import { useEffect, useMemo, useState } from "react";
import { Button, Form, Input, Modal, Select, Space, Table, Tag, message } from "antd";
import * as paperApi from "../../api/exam-paper";
import * as questionApi from "../../api/question-bank";
import { categoryOptions, selectOptions } from "../question-bank/question-editor";

export default function QuestionPicker({ open, excluded, onClose, onPick }: {
  open: boolean; excluded: number[]; onClose: () => void; onPick: (questions: questionApi.Question[]) => void;
}) {
  const [banks, setBanks] = useState<questionApi.Bank[]>([]);
  const [categories, setCategories] = useState<questionApi.Category[]>([]);
  const [query, setQuery] = useState<questionApi.Query>({ page: 1, size: 10, status: "enabled" });
  const [rows, setRows] = useState<questionApi.Question[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<Map<number, questionApi.Question>>(new Map());
  const [form] = Form.useForm();
  useEffect(() => { if (open) questionApi.banks().then(setBanks).catch(questionApi.showRequestError); }, [open]);
  useEffect(() => {
    setCategories([]); setRows([]); setSelected(new Map());
    if (query.bankId) questionApi.categories(query.bankId).then(setCategories).catch(questionApi.showRequestError);
  }, [query.bankId]);
  useEffect(() => {
    if (!open || !query.bankId) return;
    let active = true; setLoading(true);
    paperApi.searchQuestions(query).then(data => { if (active) { setRows(data.items); setTotal(data.total); } })
      .catch(questionApi.showRequestError).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, query]);
  const availableRows = useMemo(() => rows.filter(row => !excluded.includes(row.id)), [rows, excluded]);
  return <Modal open={open} title="从试题库选题" width={1000} destroyOnClose maskClosable={false} onCancel={onClose}
    okText={`加入 ${selected.size} 道题`} okButtonProps={{ disabled: selected.size === 0 }} onOk={() => { onPick([...selected.values()]); onClose(); }}>
    <Form form={form} layout="inline" style={{ marginBottom: 16 }} onFinish={values => setQuery(q => ({ ...q, ...values, page: 1 }))}>
      <Form.Item name="bankId" label="题库" rules={[{ required: true }]}><Select placeholder="请选择题库" style={{ width: 180 }} options={banks.map(b => ({ value: b.id, label: `${b.name}${b.status === "disabled" ? "（题库已停用）" : ""}` }))} onChange={bankId => { form.setFieldValue("categoryId", undefined); setQuery(q => ({ ...q, bankId, categoryId: undefined, page: 1 })); }} /></Form.Item>
      <Form.Item name="categoryId"><Select allowClear placeholder="全部分类" style={{ width: 180 }} options={categoryOptions(categories)} /></Form.Item>
      <Form.Item name="type"><Select allowClear placeholder="全部题型" style={{ width: 120 }} options={selectOptions(questionApi.questionTypes)} /></Form.Item>
      <Form.Item name="difficulty"><Select allowClear placeholder="全部难度" style={{ width: 110 }} options={selectOptions(questionApi.difficulties)} /></Form.Item>
      <Form.Item name="keyword"><Input allowClear placeholder="编码或题干" style={{ width: 180 }} /></Form.Item>
      <Form.Item><Space><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { form.resetFields(["categoryId", "type", "difficulty", "keyword"]); setQuery(q => ({ bankId: q.bankId, page: 1, size: q.size, status: "enabled" })); }}>重置</Button></Space></Form.Item>
    </Form>
    {!query.bankId ? <div style={{ padding: 60, textAlign: "center", color: "#999" }}>请先选择题库</div> : <Table rowKey="id" size="small" loading={loading} dataSource={availableRows}
      rowSelection={{ preserveSelectedRowKeys: true, selectedRowKeys: [...selected.keys()], onSelect: (row, checked) => setSelected(old => { const next = new Map(old); checked ? next.set(row.id, row) : next.delete(row.id); return next; }), onSelectAll: (checked, changed) => setSelected(old => { const next = new Map(old); changed.forEach(row => checked ? next.set(row.id, row) : next.delete(row.id)); return next; }) }}
      pagination={{ current: query.page, pageSize: query.size, total, showSizeChanger: false, onChange: page => setQuery(q => ({ ...q, page })) }}
      columns={[
        { title: "编码", dataIndex: "code", width: 140 },
        { title: "题干", dataIndex: "stem", ellipsis: true },
        { title: "题型", dataIndex: "type", width: 90, render: (v: keyof typeof questionApi.questionTypes) => questionApi.questionTypes[v] },
        { title: "难度", dataIndex: "difficulty", width: 80, render: (v: keyof typeof questionApi.difficulties) => <Tag>{questionApi.difficulties[v]}</Tag> },
        { title: "版本", dataIndex: "version", width: 70, render: v => `v${v}` },
        { title: "建议分", dataIndex: "suggestedScore", width: 80 },
      ]} />}
  </Modal>;
}
