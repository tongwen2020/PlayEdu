import type { PaperCategory, PaperInput } from "../../api/exam-paper";

export function parsePaperImport(text: string, categories: PaperCategory[]): PaperInput[] {
  const parsed: any = JSON.parse(text.replace(/^\uFEFF/, ""));
  const source: any[] = Array.isArray(parsed) ? parsed : Array.isArray(parsed?.papers) ? parsed.papers : parsed?.code ? [parsed] : [];
  if (!source.length || source.length > 100) throw new Error("文件必须包含 1–100 份试卷");
  const codes = new Set<string>();
  return source.map((paper, paperIndex) => {
    const fail = (reason: string): never => { throw new Error(`第 ${paperIndex + 1} 份试卷：${reason}`); };
    if (!paper || typeof paper !== "object") return fail("格式不正确");
    if (typeof paper.code !== "string" || !/^[a-zA-Z0-9_-]{1,64}$/.test(paper.code)) fail("编码格式不正确");
    if (codes.has(paper.code)) fail(`编码重复：${paper.code}`); codes.add(paper.code);
    if (typeof paper.name !== "string" || !paper.name.trim() || paper.name.length > 100) fail("名称必填且不超过 100 字");
    if (paper.description != null && (typeof paper.description !== "string" || paper.description.length > 1000)) fail("说明不超过 1000 字");
    if (!Array.isArray(paper.tags) || paper.tags.length > 20 || paper.tags.some((tag: unknown) => typeof tag !== "string" || !tag.trim() || tag.length > 40)) fail("标签格式不正确");
    if (!Array.isArray(paper.sections) || paper.sections.length > 50) fail("大题必须为数组且最多 50 个");
    if (paper.categoryId != null && !categories.some(category => category.id === paper.categoryId)) fail("分类不存在，请将 categoryId 改为当前试卷分类 ID 或 null");
    let questionCount = 0;
    const ids = new Set<number>();
    const sections = paper.sections.map((section: any, sectionIndex: number) => {
      if (!section || typeof section.title !== "string" || !section.title.trim() || section.title.length > 100) fail(`第 ${sectionIndex + 1} 大题标题不正确`);
      if (!Array.isArray(section.items) || section.items.length > 500) fail(`第 ${sectionIndex + 1} 大题试题格式不正确`);
      questionCount += section.items.length;
      const items = section.items.map((item: any, itemIndex: number) => {
        if (!Number.isInteger(item?.questionId) || item.questionId < 1 || !Number.isInteger(item.questionVersion) || item.questionVersion < 1) fail(`第 ${sectionIndex + 1} 大题第 ${itemIndex + 1} 题引用不正确`);
        if (ids.has(item.questionId)) fail(`试题 #${item.questionId} 重复`); ids.add(item.questionId);
        if (typeof item.score !== "number" || item.score < 0.01 || item.score > 10000 || Math.abs(item.score * 100 - Math.round(item.score * 100)) > 0.000001) fail(`第 ${sectionIndex + 1} 大题第 ${itemIndex + 1} 题分值不正确`);
        return { questionId: item.questionId, questionVersion: item.questionVersion, score: item.score, position: itemIndex };
      });
      return { title: section.title, description: typeof section.description === "string" ? section.description : "", position: sectionIndex, shuffleQuestions: Boolean(section.shuffleQuestions), items };
    });
    if (questionCount > 500) fail("整卷最多 500 道题");
    return { code: paper.code, name: paper.name, description: paper.description || "", categoryId: paper.categoryId ?? null, tags: paper.tags, sections };
  });
}
