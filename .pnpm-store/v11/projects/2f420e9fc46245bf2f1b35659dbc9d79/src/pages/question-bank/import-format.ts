import type { Category, QuestionInput } from "../../api/question-bank";
export function parseImport(text: string, bankId: number, categories: Category[]): QuestionInput[] {
  const parsed: unknown = JSON.parse(text.replace(/^\uFEFF/, ""));
  if (!Array.isArray(parsed) || !parsed.length || parsed.length > 1000) throw new Error("文件必须是包含 1–1000 道试题的 JSON 数组");
  const codes = new Set<string>();
  return parsed.map((q, index) => {
    const fail = (reason: string): never => { throw new Error(`第 ${index + 1} 题：${reason}`); };
    if (!q || typeof q !== "object") return fail("格式不正确");
    if (typeof q.code !== "string" || !/^[a-zA-Z0-9_-]{1,64}$/.test(q.code)) fail("编码格式不正确");
    if (codes.has(q.code)) fail(`编码重复：${q.code}`); codes.add(q.code);
    if (!["single_choice", "multiple_choice", "true_false", "short_answer"].includes(q.type)) fail("不支持的题型");
    if (!["easy", "medium", "hard"].includes(q.difficulty)) fail("难度不正确");
    if (!["draft", "enabled", "disabled", "archived"].includes(q.status)) fail("状态不正确");
    if (typeof q.stem !== "string" || !q.stem.trim() || q.stem.length > 10000) fail("题干必填且不超过 10000 字");
    if (typeof q.suggestedScore !== "number" || !Number.isFinite(q.suggestedScore) || q.suggestedScore < 0.01 || q.suggestedScore > 10000 || Math.abs(q.suggestedScore * 100 - Math.round(q.suggestedScore * 100)) > 0.000001) fail("分值须为 0.01–10000，最多两位小数");
    if (typeof q.analysis !== "string" || q.analysis.length > 10000) fail("解析必须为不超过 10000 字的文本");
    if (!Array.isArray(q.tags) || q.tags.length > 20 || q.tags.some((t: unknown) => typeof t !== "string" || !t.trim() || t.length > 40) || new Set(q.tags).size !== q.tags.length) fail("标签格式不正确或重复");
    if (q.categoryId != null && !categories.some(c => c.id === q.categoryId)) fail("分类不属于当前题库；请修改 categoryId，或设为 null（未分类）");
    if (!Array.isArray(q.options) || q.options.length > 8 || q.options.some((o: any) => !o || typeof o.id !== "string" || !/^[a-zA-Z0-9_-]{1,64}$/.test(o.id) || typeof o.text !== "string" || !o.text.trim() || o.text.length > 2000)) fail("选项格式不正确");
    const ids = q.options.map((o: { id: string }) => o.id);
    if (new Set(ids).size !== ids.length) fail("选项标识重复");
    const a = q.standardAnswer;
    if (!a || typeof a !== "object") fail("缺少标准答案");
    const choice = q.type === "single_choice" || q.type === "multiple_choice";
    if (choice) {
      if (ids.length < (q.type === "single_choice" ? 2 : 3)) fail("选项数量不足");
      if (!Array.isArray(a.optionIds) || (q.type === "single_choice" ? a.optionIds.length !== 1 : a.optionIds.length < 2) || a.optionIds.length > 8 || new Set(a.optionIds).size !== a.optionIds.length || a.optionIds.some((id: unknown) => !ids.includes(id)) || a.value != null || a.text != null) fail("选择题标准答案不正确");
    } else {
      if (ids.length || (a.optionIds != null && (!Array.isArray(a.optionIds) || a.optionIds.length))) fail("该题型不允许选项");
      if (q.type === "true_false" && (typeof a.value !== "boolean" || a.text != null)) fail("判断题答案必须为布尔值");
      if (q.type === "short_answer" && (a.value != null || typeof a.text !== "string" || !a.text.trim() || a.text.length > 10000)) fail("简答题必须填写参考答案（最多 10000 字）");
    }
    if (q.gradingRule?.strategy !== (q.type === "short_answer" ? "manual" : "exact_match")) fail("评分规则与题型不符");
    // Exported identifiers and version numbers must never turn imports into updates.
    return { bankId, categoryId: q.categoryId ?? null, code: q.code, type: q.type, difficulty: q.difficulty, stem: q.stem, options: q.options,
      standardAnswer: q.standardAnswer, gradingRule: q.gradingRule, suggestedScore: q.suggestedScore, analysis: q.analysis, tags: q.tags, status: q.status };
  });
}

