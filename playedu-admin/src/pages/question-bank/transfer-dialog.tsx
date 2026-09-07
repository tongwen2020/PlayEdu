import { parseImport } from "./import-format";
import { useState } from "react";
import { Alert, Button, Modal, Space, Table, Upload, message } from "antd";
import { Category, QuestionInput, importQuestions, questionTypes } from "../../api/question-bank";

export function downloadJson(value: unknown, name: string) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: "application/json;charset=utf-8" }));
  const link = document.createElement("a"); link.href = url; link.download = name;
  document.body.appendChild(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export default function TransferDialog({ bankId, categories, onClose, onSaved }: { bankId: number; categories: Category[]; onClose: () => void; onSaved: () => void }) {
  const [questions, setQuestions] = useState<QuestionInput[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [reading, setReading] = useState(false);
  const [filename, setFilename] = useState("");
  const template = () => {
    const base = { bankId, categoryId: null, difficulty: "medium", suggestedScore: 1, analysis: "答案解析", tags: [], status: "draft" };
    downloadJson([
      { ...base, code: "sample_single", type: "single_choice", stem: "1 + 1 等于多少？", options: [{ id: "A", text: "2" }, { id: "B", text: "3" }], standardAnswer: { optionIds: ["A"] }, gradingRule: { strategy: "exact_match" } },
      { ...base, code: "sample_multiple", type: "multiple_choice", stem: "请选择偶数", options: [{ id: "A", text: "2" }, { id: "B", text: "4" }, { id: "C", text: "3" }], standardAnswer: { optionIds: ["A", "B"] }, gradingRule: { strategy: "exact_match" } },
      { ...base, code: "sample_boolean", type: "true_false", stem: "1 + 1 = 3", options: [], standardAnswer: { value: false }, gradingRule: { strategy: "exact_match" } },
      { ...base, code: "sample_short", type: "short_answer", stem: "简述学习计划的作用", options: [], standardAnswer: { text: "明确目标、安排时间；按要点给分。" }, gradingRule: { strategy: "manual" } },
    ], "试题导入模板.json");
  };
  return <Modal open title="批量导入试题" width={800} onCancel={onClose} confirmLoading={busy} okText={`确认导入 ${questions.length} 题`} okButtonProps={{ disabled: !questions.length || reading }} onOk={async () => {
    setBusy(true); try { const result = await importQuestions(bankId, questions); message.success(`成功导入 ${result.count} 题`); onSaved(); }
    catch { setError("导入失败，请根据接口提示检查编码是否已存在或数据是否有效；本次整批不会部分写入。"); } finally { setBusy(false); }
  }}>
    <Alert type="info" showIcon message="仅新增试题，每批最多 1000 题；编码须在题库内唯一。" description="支持 JSON 数组，可使用模板或导出文件。导入时会移除原 ID 和版本，并使用当前题库；跨题库导入请将 categoryId 改为当前分类 ID 或 null。状态按文件保留。" />
    <Space style={{ margin: "16px 0" }}><Button onClick={template}>下载四种题型模板</Button><Upload accept=".json,application/json" showUploadList={false} disabled={busy || reading} beforeUpload={async file => {
      setQuestions([]); setError(""); setFilename(file.name); setReading(true);
      try { if (file.size > 20 * 1024 * 1024) throw new Error("文件不能超过 20 MB"); setQuestions(parseImport(await file.text(), bankId, categories)); }
      catch (e) { setError(e instanceof Error ? e.message : "文件读取失败"); }
      finally { setReading(false); }
      return false;
    }}><Button loading={reading}>选择 JSON 文件</Button></Upload></Space>
    {error && <Alert type="error" showIcon message={error} />}
    {filename && <p>{filename} · 已校验 {questions.length} 题</p>}
    <Table rowKey="code" size="small" dataSource={questions} pagination={{ pageSize: 5 }} columns={[{ title: "编码", dataIndex: "code" }, { title: "题型", dataIndex: "type", render: (t: QuestionInput["type"]) => questionTypes[t] }, { title: "题干", dataIndex: "stem", ellipsis: true }]} />
  </Modal>;
}
