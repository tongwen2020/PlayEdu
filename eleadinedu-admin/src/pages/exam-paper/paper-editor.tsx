import { useMemo, useState } from "react";
import { Alert, Button, Card, Col, Drawer, Form, Input, InputNumber, Popconfirm, Row, Select, Space, Switch, Table, Tag, Typography, message } from "antd";
import * as paperApi from "../../api/exam-paper";
import * as questionApi from "../../api/question-bank";
import QuestionPicker from "./question-picker";

type EditablePaper = Partial<paperApi.Paper> & Pick<paperApi.PaperInput, "code" | "name" | "description" | "tags" | "sections">;
const categoryOptions = (categories: paperApi.PaperCategory[]) => {
  const label = (category: paperApi.PaperCategory, seen: number[] = []): string => {
    if (seen.includes(category.id)) return category.name;
    const parent = categories.find(item => item.id === category.parentId);
    return parent ? `${label(parent, [...seen, category.id])} / ${category.name}` : category.name;
  };
  return categories.map(category => ({ value: category.id, label: label(category) }));
};

export default function PaperEditor({ paper, categories, currentOwnerId, onClose, onSaved }: {
  paper?: EditablePaper; categories: paperApi.PaperCategory[]; currentOwnerId?: number; onClose: () => void; onSaved: (paper: paperApi.Paper) => void;
}) {
  const [form] = Form.useForm();
  const [sections, setSections] = useState<paperApi.PaperSection[]>(paper?.sections || []);
  const [pickerSection, setPickerSection] = useState<number>();
  const [saving, setSaving] = useState(false);
  const readOnly = paper?.status === "archived";
  const ownerId = paper?.ownerId || currentOwnerId;
  const ownCategories = useMemo(() => categories.filter(c => c.ownerId === ownerId), [categories, ownerId]);
  const updateSection = (index: number, changes: Partial<paperApi.PaperSection>) => setSections(old => old.map((section, i) => i === index ? { ...section, ...changes } : section));
  const moveSection = (index: number, offset: number) => setSections(old => { const next = [...old]; const target = index + offset; if (target < 0 || target >= next.length) return old; [next[index], next[target]] = [next[target], next[index]]; return next; });
  const moveItem = (sectionIndex: number, itemIndex: number, offset: number) => setSections(old => old.map((section, i) => {
    if (i !== sectionIndex) return section; const items = [...section.items]; const target = itemIndex + offset; if (target < 0 || target >= items.length) return section; [items[itemIndex], items[target]] = [items[target], items[itemIndex]]; return { ...section, items };
  }));
  const save = async () => {
    const values = await form.validateFields(); setSaving(true);
    try {
      const saved = await paperApi.savePaper({ id: paper?.id, revision: paper?.id ? paper.revision : undefined, code: values.code, name: values.name, description: values.description || "", categoryId: values.categoryId ?? null, tags: values.tags || [], sections: sections.map((section, position) => ({ title: section.title.trim(), description: section.description || "", shuffleQuestions: section.shuffleQuestions, position, items: section.items.map((item, itemPosition) => ({ questionId: item.questionId, questionVersion: item.questionVersion, score: Number(item.score), position: itemPosition })) })) });
      message.success("草稿已保存"); onSaved(saved);
    } catch (error) { questionApi.showRequestError(error); } finally { setSaving(false); }
  };
  const excluded = sections.flatMap(section => section.items.map(item => item.questionId));
  return <Drawer open width="min(1180px, 96vw)" title={paper?.id ? `编辑试卷：${paper.name}` : "新建试卷"} onClose={onClose} destroyOnClose maskClosable={false}
    extra={<Space><Button onClick={onClose}>关闭</Button><Button type="primary" loading={saving} disabled={readOnly} onClick={() => void save()}>保存草稿</Button></Space>}>
    {paper?.id && <Alert showIcon type={paper.status === "archived" ? "warning" : "info"} style={{ marginBottom: 16 }} message={paper.status === "archived" ? "归档试卷仅供查看，不能再编辑。" : `当前修订 r${paper.revision}；编辑已发布或已停用试卷并保存后，试卷会回到草稿状态。`} />}
    <Form form={form} layout="vertical" disabled={readOnly} initialValues={{ code: paper?.code, name: paper?.name, description: paper?.description || "", categoryId: paper?.categoryId || undefined, tags: paper?.tags || [] }}>
      <Row gutter={16}>
        <Col span={8}><Form.Item label="试卷编码" name="code" rules={[{ required: true }, { pattern: /^[a-zA-Z0-9_-]{1,64}$/, message: "限 1–64 位字母、数字、下划线或连字符" }]}><Input maxLength={64} /></Form.Item></Col>
        <Col span={8}><Form.Item label="试卷名称" name="name" rules={[{ required: true, whitespace: true }]}><Input maxLength={100} /></Form.Item></Col>
        <Col span={8}><Form.Item label="试卷分类" name="categoryId"><Select allowClear placeholder="未分类" options={categoryOptions(ownCategories)} /></Form.Item></Col>
      </Row>
      <Form.Item label="试卷说明" name="description"><Input.TextArea rows={2} maxLength={1000} showCount /></Form.Item>
      <Form.Item label="标签" name="tags" rules={[{ validator: async (_, tags: string[]) => { if (tags?.length > 20 || tags?.some(t => !t.trim() || t.length > 40)) throw new Error("最多 20 个标签，每个标签 1–40 字"); } }]}><Select mode="tags" tokenSeparators={[","]} /></Form.Item>
    </Form>
    <Space style={{ marginBottom: 12 }}><Typography.Title level={5} style={{ margin: 0 }}>试卷结构</Typography.Title><Tag>{sections.length} 个大题</Tag><Tag>{excluded.length} 道题</Tag><Tag color="blue">{sections.reduce((sum, section) => sum + section.items.reduce((v, item) => v + Number(item.score), 0), 0).toFixed(2)} 分</Tag><Button disabled={readOnly || sections.length >= 50} onClick={() => setSections(old => [...old, { title: `第 ${old.length + 1} 大题`, description: "", position: old.length, shuffleQuestions: false, items: [] }])}>添加大题</Button></Space>
    {!sections.length && <Alert showIcon type="info" message="草稿可以暂时不添加大题；发布前至少需要一个大题和一道启用的试题。" />}
    {sections.map((section, sectionIndex) => <Card key={sectionIndex} size="small" style={{ marginBottom: 16 }} title={<Space><Input value={section.title} disabled={readOnly} maxLength={100} status={!section.title.trim() ? "error" : undefined} onChange={e => updateSection(sectionIndex, { title: e.target.value })} style={{ width: 260 }} /><span style={{ fontWeight: 400 }}>共 {section.items.reduce((sum, item) => sum + Number(item.score), 0).toFixed(2)} 分</span></Space>}
      extra={<Space><Button size="small" disabled={readOnly || sectionIndex === 0} onClick={() => moveSection(sectionIndex, -1)}>上移</Button><Button size="small" disabled={readOnly || sectionIndex === sections.length - 1} onClick={() => moveSection(sectionIndex, 1)}>下移</Button><Popconfirm title="移除该大题及其中试题？" onConfirm={() => setSections(old => old.filter((_, i) => i !== sectionIndex))}><Button size="small" danger disabled={readOnly}>移除</Button></Popconfirm></Space>}>
      <Row gutter={16} align="middle"><Col flex="auto"><Input.TextArea value={section.description} disabled={readOnly} rows={1} maxLength={1000} placeholder="大题说明（可选）" onChange={e => updateSection(sectionIndex, { description: e.target.value })} /></Col><Col><Space><span>随机题序</span><Switch checked={section.shuffleQuestions} disabled={readOnly} onChange={checked => updateSection(sectionIndex, { shuffleQuestions: checked })} /><Button type="primary" ghost disabled={readOnly || excluded.length >= 500} onClick={() => setPickerSection(sectionIndex)}>添加试题</Button></Space></Col></Row>
      <Table rowKey={row => `${row.questionId}-${row.questionVersion}`} size="small" pagination={false} dataSource={section.items} style={{ marginTop: 12 }} columns={[
        { title: "序号", width: 60, render: (_, __, index) => index + 1 }, { title: "编码", width: 140, render: (_, item) => item.question?.code || `#${item.questionId}` },
        { title: "题干", ellipsis: true, render: (_, item) => item.question?.stem || "题目详情将在重新打开时加载" },
        { title: "题型", width: 90, render: (_, item) => item.question ? questionApi.questionTypes[item.question.type] : "-" }, { title: "版本", width: 70, render: (_, item) => `v${item.questionVersion}` },
        { title: "分值", width: 120, render: (_, item, itemIndex) => <InputNumber min={0.01} max={10000} precision={2} disabled={readOnly} value={item.score} onChange={score => updateSection(sectionIndex, { items: section.items.map((row, i) => i === itemIndex ? { ...row, score: Number(score || 0) } : row) })} /> },
        { title: "操作", width: 190, render: (_, __, itemIndex) => <Space><Button type="link" size="small" disabled={readOnly || itemIndex === 0} onClick={() => moveItem(sectionIndex, itemIndex, -1)}>上移</Button><Button type="link" size="small" disabled={readOnly || itemIndex === section.items.length - 1} onClick={() => moveItem(sectionIndex, itemIndex, 1)}>下移</Button><Button type="link" danger size="small" disabled={readOnly} onClick={() => updateSection(sectionIndex, { items: section.items.filter((_, i) => i !== itemIndex) })}>移除</Button></Space> },
      ]} />
    </Card>)}
    <QuestionPicker open={pickerSection !== undefined} excluded={excluded} onClose={() => setPickerSection(undefined)} onPick={questions => { if (pickerSection === undefined) return; const additions: paperApi.PaperItem[] = questions.map((question, index) => ({ questionId: question.id, questionVersion: question.version, score: question.suggestedScore, position: sections[pickerSection].items.length + index, question })); updateSection(pickerSection, { items: [...sections[pickerSection].items, ...additions] }); }} />
  </Drawer>;
}
