import { useState } from "react";
import { Alert, Button, Modal, Space, Table, Upload, message } from "antd";
import * as api from "../../api/exam-paper";
import { showRequestError } from "../../api/question-bank";
import { parsePaperImport } from "./import-format";

export function downloadJson(value: unknown, filename: string) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: "application/json;charset=utf-8" }));
  const link = document.createElement("a"); link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export default function TransferDialog({ categories, onClose, onSaved }: { categories: api.PaperCategory[]; onClose: () => void; onSaved: () => void }) {
  const [papers, setPapers] = useState<api.PaperInput[]>([]); const [error, setError] = useState(""); const [filename, setFilename] = useState(""); const [busy, setBusy] = useState(false);
  const template = () => downloadJson([{ code: "EXAM_SAMPLE_001", name: "示例试卷", description: "可先导入空草稿，再进入编辑器选题", categoryId: null, tags: ["示例"], sections: [] }], "试卷导入模板.json");
  return <Modal open title="批量导入试卷" width={820} onCancel={onClose} confirmLoading={busy} okText={`确认导入 ${papers.length} 份`} okButtonProps={{ disabled: !papers.length }} onOk={async () => {
    setBusy(true); try { const result = await api.importPapers(papers); message.success(`成功导入 ${result.count} 份试卷`); onSaved(); } catch (e) { showRequestError(e); setError("导入失败，请检查试卷编码、试题版本和数据归属；本批次不会部分写入。"); } finally { setBusy(false); }
  }}>
    <Alert showIcon type="info" message="仅新增草稿，每批最多 100 份。" description="支持单份导出文件、JSON 数组和 { papers: [...] }。导入会移除原试卷 ID、版本、修订和题目快照，保留固定的试题 ID、版本与试卷分值。" />
    <Space style={{ margin: "16px 0" }}><Button onClick={template}>下载模板</Button><Upload accept=".json,application/json" showUploadList={false} beforeUpload={async file => { setPapers([]); setError(""); setFilename(file.name); try { if (file.size > 20 * 1024 * 1024) throw new Error("文件不能超过 20 MB"); setPapers(parsePaperImport(await file.text(), categories)); } catch (e) { setError(e instanceof Error ? e.message : "文件读取失败"); } return false; }}><Button>选择 JSON 文件</Button></Upload></Space>
    {error && <Alert type="error" showIcon message={error} />}{filename && <p>{filename} · 已校验 {papers.length} 份试卷</p>}
    <Table rowKey="code" size="small" dataSource={papers} pagination={{ pageSize: 5 }} columns={[{ title: "编码", dataIndex: "code" }, { title: "名称", dataIndex: "name" }, { title: "大题", render: (_, p) => p.sections.length }, { title: "试题", render: (_, p) => p.sections.reduce((sum, section) => sum + section.items.length, 0) }]} />
  </Modal>;
}
