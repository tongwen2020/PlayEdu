import { useState } from "react";
import { Alert, Button, Col, Form, Input, InputNumber, Modal, Radio, Row, Select, Space } from "antd";
import { Category, Question, QuestionInput, difficulties, questionTypes, statuses, saveQuestion, showRequestError } from "../../api/question-bank";

export const selectOptions = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));
export type EditableQuestion = Omit<Question, "id"> & { id?: number };
export function categoryOptions(categories: Category[]) {
  const label = (c: Category): string => {
    const parent = categories.find(p => p.id === c.parentId);
    return parent ? `${label(parent)} / ${c.name}` : c.name;
  };
  return categories.map(c => ({ value: c.id, label: label(c) }));
}
export default function QuestionEditor({ bankId, categories, question, onClose, onSaved }: {
  bankId: number; categories: Category[]; question?: EditableQuestion; onClose: () => void; onSaved: () => void;
}) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const type = Form.useWatch("type", form) || question?.type || "single_choice";
  const options: QuestionInput["options"] = Form.useWatch("options", form) || [];
  const choice = type === "single_choice" || type === "multiple_choice";
  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await saveQuestion({
        id: question?.id, expectedVersion: question?.id ? question.version : undefined, bankId,
        code: values.code, type: values.type, difficulty: values.difficulty, stem: values.stem,
        suggestedScore: values.suggestedScore, status: values.status,
        categoryId: values.categoryId ?? null, options: choice ? values.options : [],
        standardAnswer: choice ? { optionIds: type === "single_choice" ? [values.singleAnswer] : values.multipleAnswer }
          : type === "true_false" ? { value: values.booleanAnswer } : { text: values.textAnswer },
        gradingRule: { strategy: type === "short_answer" ? "manual" : "exact_match" },
        analysis: values.analysis || "", tags: values.tags || [],
      });
      onSaved();
    } catch (error) { showRequestError(error); }
    finally { setSaving(false); }
  };
  return <Modal open title={question?.id ? "编辑试题" : "新增试题"} width={820} onCancel={onClose}
    confirmLoading={saving} onOk={() => { void submit().catch(() => undefined); }} maskClosable={false}>
    <Form form={form} layout="vertical" initialValues={question ? {
      ...question, singleAnswer: question.standardAnswer.optionIds?.[0], multipleAnswer: question.standardAnswer.optionIds,
      booleanAnswer: question.standardAnswer.value, textAnswer: question.standardAnswer.text,
    } : { type: "single_choice", difficulty: "medium", status: "draft", suggestedScore: 1, options: [{ id: "A", text: "" }, { id: "B", text: "" }], tags: [] }}>
      {question?.id && <Alert type="info" showIcon message={`当前版本 v${question.version}，保存将生成新版本。`} style={{ marginBottom: 16 }} />}
      <Row gutter={16}>
        <Col span={12}><Form.Item label="试题编码" name="code" rules={[{ required: true }, { pattern: /^[a-zA-Z0-9_-]{1,64}$/, message: "限 1–64 位字母、数字、下划线或连字符" }]}><Input maxLength={64} /></Form.Item></Col>
        <Col span={12}><Form.Item label="分类" name="categoryId"><Select allowClear options={categoryOptions(categories)} placeholder="未分类" /></Form.Item></Col>
        <Col span={8}><Form.Item label="题型" name="type" rules={[{ required: true }]}><Select options={selectOptions(questionTypes)} onChange={() => form.setFieldsValue({ singleAnswer: undefined, multipleAnswer: [], booleanAnswer: undefined, textAnswer: undefined })} /></Form.Item></Col>
        <Col span={8}><Form.Item label="难度" name="difficulty" rules={[{ required: true }]}><Select options={selectOptions(difficulties)} /></Form.Item></Col>
        <Col span={8}><Form.Item label="建议分值" name="suggestedScore" rules={[{ required: true }]}><InputNumber min={0.01} max={10000} precision={2} style={{ width: "100%" }} /></Form.Item></Col>
      </Row>
      <Form.Item label="题干" name="stem" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={4} maxLength={10000} showCount /></Form.Item>
      {choice && <>
        <Form.List name="options" rules={[{ validator: async (_, rows) => {
          if (!rows || rows.length < (type === "single_choice" ? 2 : 3) || rows.length > 8) throw new Error(type === "single_choice" ? "单选题需要 2–8 个选项" : "多选题需要 3–8 个选项");
          if (new Set(rows.map((o: { id: string }) => o.id)).size !== rows.length) throw new Error("选项标识不能重复");
        } }]}>{(fields, { add, remove }, { errors }) => <>
          {fields.map(field => <Space key={field.key} align="baseline" style={{ display: "flex" }}>
            <Form.Item name={[field.name, "id"]} label="选项标识" rules={[{ required: true }, { pattern: /^[a-zA-Z0-9_-]{1,64}$/, message: "标识格式不正确" }]}><Input style={{ width: 110 }} maxLength={64} /></Form.Item>
            <Form.Item name={[field.name, "text"]} label="选项内容" rules={[{ required: true, whitespace: true }]}><Input.TextArea style={{ width: 450 }} maxLength={2000} autoSize /></Form.Item>
            <Button danger onClick={() => remove(field.name)}>移除</Button>
          </Space>)}
          <Button onClick={() => add({ id: "ABCDEFGH".split("").find(id => !options.some(o => o?.id === id)), text: "" })} disabled={fields.length >= 8}>添加选项</Button>
          <Form.ErrorList errors={errors} />
        </>}</Form.List>
        <Form.Item label="正确答案" name={type === "single_choice" ? "singleAnswer" : "multipleAnswer"} style={{ marginTop: 16 }} rules={[{ required: true, message: "请选择正确答案" }, { validator: async (_, value) => {
          const ids: string[] = type === "single_choice" ? (value ? [value] : []) : value || [];
          if (type === "multiple_choice" && ids.length < 2) throw new Error("多选题至少选择两个正确答案");
          if (ids.some(id => !options.some(o => o?.id === id))) throw new Error("答案选项已变更，请重新选择");
        } }]}><Select key={type} mode={type === "multiple_choice" ? "multiple" : undefined} options={options.filter(o => o?.id).map(o => ({ value: o.id, label: `${o.id}：${o.text || "（未填写）"}` }))} /></Form.Item>
      </>}
      {type === "true_false" && <Form.Item label="正确答案" name="booleanAnswer" rules={[{ required: true, message: "请选择正确或错误" }]}><Radio.Group options={[{ label: "正确", value: true }, { label: "错误", value: false }]} /></Form.Item>}
      {type === "short_answer" && <Form.Item label="参考答案与评分要点（人工评分）" name="textAnswer" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={4} maxLength={10000} /></Form.Item>}
      <Form.Item label="答案解析" name="analysis"><Input.TextArea rows={3} maxLength={10000} /></Form.Item>
      <Form.Item label="标签" name="tags" rules={[{ validator: async (_, tags: string[]) => {
        if (tags?.length > 20 || tags?.some(t => !t.trim() || t.length > 40)) throw new Error("最多 20 个标签，每个标签 1–40 字");
      } }]}><Select mode="tags" tokenSeparators={[","]} /></Form.Item>
      <Form.Item label="状态" name="status" rules={[{ required: true }]}><Select options={selectOptions(statuses)} /></Form.Item>
      <Alert type="info" message="客观题按答案完全匹配评分；简答题需人工评分。归档后不能再编辑，可复制为新题。" />
    </Form>
  </Modal>;
}
