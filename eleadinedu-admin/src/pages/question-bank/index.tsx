import { useEffect, useRef, useState } from "react";
import { useSelector } from "react-redux";
import { Alert, Button, Card, Descriptions, Drawer, Empty, Form, Input, Modal, Popconfirm, Result, Select, Space, Switch, Table, Tag, Typography, message } from "antd";
import * as api from "../../api/question-bank";
import QuestionEditor, { EditableQuestion, categoryOptions, selectOptions } from "./question-editor";
import TransferDialog, { downloadJson } from "./transfer-dialog";
import styles from "./index.module.less";

export default function QuestionBankPage() {
  const permissions = useSelector((state: any) => state.loginUser.value.permissions);
  const canView = permissions?.["question-bank-view"] !== undefined;
  const canEdit = permissions?.["question-bank-edit"] !== undefined;
  const canExport = permissions?.["question-bank-export"] !== undefined;
  const [banks, setBanks] = useState<api.Bank[]>([]);
  const [bankId, setBankId] = useState<number>();
  const bank = banks.find(b => b.id === bankId);
  const [categories, setCategories] = useState<api.Category[]>([]);
  const [query, setQuery] = useState<api.Query>({ page: 1, size: 20 });
  const [rows, setRows] = useState<api.Question[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState(false);
  const [bankModal, setBankModal] = useState<Partial<api.Bank>>();
  const [categoryModal, setCategoryModal] = useState<Partial<api.Category>>();
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [editor, setEditor] = useState<{ question?: EditableQuestion }>();
  const [transfer, setTransfer] = useState(false);
  const [preview, setPreview] = useState<api.Question>();
  const [history, setHistory] = useState<api.Version[]>([]);
  const previewRequest = useRef(0);
  const [bankForm] = Form.useForm();
  const [categoryForm] = Form.useForm();
  const [filterForm] = Form.useForm();
  const reload = () => setRefresh(n => n + 1);
  useEffect(() => {
    if (!canView) return;
    let active = true;
    api.banks().then(data => { if (active) { setBanks(data); setBankId(id => data.some(b => b.id === id) ? id : data[0]?.id); setError(false); } })
      .catch(() => { if (active) setError(true); });
    return () => { active = false; };
  }, [canView, refresh]);
  useEffect(() => {
    setCategories([]);
    if (!bankId || !canView) return;
    let active = true;
    api.categories(bankId).then(data => { if (active) setCategories(data); }).catch(() => message.error("分类加载失败，请刷新重试"));
    return () => { active = false; };
  }, [bankId, canView, refresh]);
  useEffect(() => {
    setRows([]); setTotal(0);
    if (!bankId || !canView) return;
    let active = true;
    setLoading(true);
    api.questions({ ...query, bankId }).then(data => { if (active) { setRows(data.items); setTotal(data.total); } })
      .catch(() => { if (active) message.error("试题加载失败，请刷新重试"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [bankId, query, canView, refresh]);
  const act = async (action: () => Promise<unknown>, success = "操作成功") => {
    setBusy(true);
    try { await action(); message.success(success); reload(); }
    catch (error) { api.showRequestError(error); }
    finally { setBusy(false); }
  };
  const showQuestion = async (q: api.Question, edit = false, copy = false) => {
    const request = ++previewRequest.current;
    setBusy(true);
    try {
      const detail = await api.detail(q.id);
      if (request !== previewRequest.current) return;
      if (edit || copy) setEditor({ question: copy ? { ...detail, id: undefined, code: "", status: "draft" } : detail });
      else { setPreview(detail); setHistory([]); const versions = await api.versions(q.id); if (request === previewRequest.current) setHistory(versions); }
    } catch (error) { api.showRequestError(error); }
    finally { setBusy(false); }
  };
  const viewVersion = async (version: number) => {
    if (!preview) return;
    const request = ++previewRequest.current;
    try { const data = await api.detail(preview.id, version); if (request === previewRequest.current) setPreview(data); } catch (error) { api.showRequestError(error); }
  };
  if (!canView) return <Result status="403" title="无试题库查看权限" subTitle="请联系管理员分配试题库查看权限。" />;
  return <div className={styles.page}>
    <Space className={styles.toolbar} wrap>
      <Typography.Title level={4} style={{ margin: 0 }}>试题库</Typography.Title>
      <Select aria-label="选择题库" placeholder="请选择题库" style={{ width: 260 }} value={bankId} options={banks.map(b => ({ value: b.id, label: b.name }))}
        onChange={id => { setBankId(id); setQuery({ page: 1, size: 20 }); filterForm.resetFields(); setCategoryOpen(false); setEditor(undefined); setTransfer(false); }} />
      <Button onClick={reload}>刷新</Button>
      {canEdit && <Button type="primary" onClick={() => { const values = { status: "enabled" as const, practiceEnabled: false, description: "" }; setBankModal(values); bankForm.resetFields(); bankForm.setFieldsValue(values); }}>新增题库</Button>}
    </Space>
    {error && <Alert type="error" showIcon message="题库加载失败，请点击刷新重试。" />}
    {!bank ? <Empty description="暂无题库" /> : <>
      <Card size="small" className={styles.section}>
        <Space wrap><Typography.Text strong>{bank.name}</Typography.Text><Tag color={bank.status === "enabled" ? "green" : "default"}>{bank.status === "enabled" ? "启用" : "停用"}</Tag><Tag>学员练习{bank.practiceEnabled ? "已开放" : "未开放"}</Tag><span>共 {bank.questionCount} 题</span></Space>
        <p className={styles.text}>{bank.description || "暂无题库说明"}</p>
        <Space wrap>
          {canEdit && <Button onClick={() => { setBankModal(bank); bankForm.resetFields(); bankForm.setFieldsValue({ ...bank, practiceEnabled: Boolean(bank.practiceEnabled) }); }}>编辑题库</Button>}
          <Button onClick={() => setCategoryOpen(true)}>分类管理</Button>
          {canEdit && <Popconfirm title="删除该空题库及其分类？" onConfirm={() => act(async () => { await api.deleteBank(bank.id); setQuery({ page: 1, size: 20 }); filterForm.resetFields(); })}><Button danger disabled={bank.questionCount > 0 || busy}>删除空题库</Button></Popconfirm>}
        </Space>
      </Card>
      <Card className={styles.section}>
        <Form form={filterForm} layout="inline" className={styles.filters} onFinish={values => setQuery({ ...values, page: 1, size: query.size })}>
          <Form.Item name="keyword"><Input placeholder="题干 / 编码" maxLength={100} allowClear /></Form.Item>
          <Form.Item name="categoryId"><Select placeholder="全部分类" allowClear style={{ width: 180 }} options={categoryOptions(categories)} /></Form.Item>
          <Form.Item name="type"><Select placeholder="全部题型" allowClear style={{ width: 120 }} options={selectOptions(api.questionTypes)} /></Form.Item>
          <Form.Item name="difficulty"><Select placeholder="全部难度" allowClear style={{ width: 120 }} options={selectOptions(api.difficulties)} /></Form.Item>
          <Form.Item name="status"><Select placeholder="全部状态" allowClear style={{ width: 120 }} options={selectOptions(api.statuses)} /></Form.Item>
          <Form.Item name="tag"><Input placeholder="标签" maxLength={40} allowClear style={{ width: 140 }} /></Form.Item>
          <Form.Item><Space><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { filterForm.resetFields(); setQuery({ page: 1, size: query.size }); }}>重置</Button></Space></Form.Item>
        </Form>
        <Space className={styles.toolbar} wrap>
          {canEdit && <><Button type="primary" onClick={() => setEditor({})}>新增试题</Button><Button onClick={() => setTransfer(true)}>批量导入</Button></>}
          {canExport && <Button loading={busy} onClick={() => act(async () => downloadJson(await api.exportQuestions(bank.id), `题库-${bank.id}.json`), "导出成功")}>导出全部试题</Button>}
        </Space>
        <Table rowKey="id" loading={loading} dataSource={rows} scroll={{ x: 1100 }} pagination={{ current: query.page, pageSize: query.size, total, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100], showTotal: n => `共 ${n} 题`, onChange: (page, size) => setQuery(q => ({ ...q, page: size === q.size ? page : 1, size })) }} columns={[
          { title: "编码", dataIndex: "code", width: 140 },
          { title: "题干", dataIndex: "stem", ellipsis: true, width: 260, render: (text, q) => <Button type="link" className={styles.stem} disabled={busy} onClick={() => void showQuestion(q)}>{text}</Button> },
          { title: "题型", dataIndex: "type", render: (type: api.Question["type"]) => api.questionTypes[type] },
          { title: "难度", dataIndex: "difficulty", render: (d: api.Question["difficulty"]) => api.difficulties[d] },
          { title: "状态", dataIndex: "status", render: (s: api.Question["status"]) => <Tag>{api.statuses[s]}</Tag> },
          { title: "版本", dataIndex: "version", render: v => `v${v}` },
          { title: "操作", width: 320, render: (_, q) => <Space wrap size={0}>
            <Button type="link" disabled={busy} onClick={() => void showQuestion(q)}>详情 / 版本</Button>
            {canEdit && <><Button type="link" disabled={busy || q.status === "archived"} onClick={() => void showQuestion(q, true)}>编辑</Button><Button type="link" disabled={busy} onClick={() => void showQuestion(q, false, true)}>复制</Button>
              {q.status !== "archived" && <><Popconfirm title={q.status === "enabled" ? "停用该试题？" : "启用该试题？"} onConfirm={() => act(() => api.changeStatus(q, q.status === "enabled" ? "disabled" : "enabled"))}><Button type="link" disabled={busy}>{q.status === "enabled" ? "停用" : "启用"}</Button></Popconfirm>
                <Popconfirm title="归档后不可修改，确定归档？" onConfirm={() => act(() => api.changeStatus(q, "archived"))}><Button type="link" danger disabled={busy}>归档</Button></Popconfirm></>}
              {q.status === "draft" && q.version === 1 && <Popconfirm title="永久删除该初始草稿？" onConfirm={() => act(async () => { await api.deleteDraft(q.id); if (rows.length === 1 && query.page > 1) setQuery(v => ({ ...v, page: v.page - 1 })); })}><Button type="link" danger disabled={busy}>删除</Button></Popconfirm>}
            </>}
          </Space> },
        ]} />
      </Card>
    </>}
    <Modal open={!!bankModal} title={bankModal?.id ? "编辑题库" : "新增题库"} confirmLoading={busy} onCancel={() => setBankModal(undefined)} onOk={() => { void bankForm.validateFields().then(values => act(async () => { const saved = await api.saveBank({ ...values, id: bankModal?.id, revision: bankModal?.revision }); setBankModal(undefined); setBankId(saved.id); }, "题库已保存")).catch(() => undefined); }}>
      <Form form={bankForm} layout="vertical"><Form.Item label="题库名称" name="name" rules={[{ required: true, whitespace: true }]}><Input maxLength={100} /></Form.Item><Form.Item label="说明" name="description"><Input.TextArea maxLength={1000} rows={3} /></Form.Item><Form.Item label="状态" name="status"><Select options={[{ value: "enabled", label: "启用" }, { value: "disabled", label: "停用" }]} /></Form.Item><Form.Item label="开放学员练习" name="practiceEnabled" valuePropName="checked"><Switch /></Form.Item><Alert type="info" message="启用题库并开放练习后，学员可练习其中已启用的客观题。" /></Form>
    </Modal>
    <Drawer open={categoryOpen} title="分类管理（最多三级）" width={620} onClose={() => setCategoryOpen(false)}>
      {canEdit && <Button type="primary" onClick={() => { setCategoryModal({}); categoryForm.resetFields(); categoryForm.setFieldsValue({ parentId: 0 }); }}>新增分类</Button>}
      <Table rowKey="id" dataSource={categories} pagination={false} columns={[{ title: "分类", render: (_, c) => categoryOptions(categories).find(o => o.value === c.id)?.label }, { title: "操作", render: (_, c) => canEdit && <Space><Button type="link" onClick={() => { setCategoryModal(c); categoryForm.resetFields(); categoryForm.setFieldsValue(c); }}>重命名</Button><Popconfirm title="删除分类？分类下不能有子分类或试题。" onConfirm={() => act(() => api.deleteCategory(c.id))}><Button type="link" danger disabled={busy}>删除</Button></Popconfirm></Space> }]} />
    </Drawer>
    <Modal open={!!categoryModal} title={categoryModal?.id ? "重命名分类" : "新增分类"} confirmLoading={busy} onCancel={() => setCategoryModal(undefined)} onOk={() => { void categoryForm.validateFields().then(values => act(async () => { if (categoryModal?.id) await api.renameCategory(categoryModal.id, values.name); else await api.createCategory({ ...values, bankId: bankId! }); setCategoryModal(undefined); })).catch(() => undefined); }}>
      <Form form={categoryForm} layout="vertical">{!categoryModal?.id && <Form.Item label="上级分类" name="parentId" rules={[{ required: true }]}><Select options={[{ value: 0, label: "无（一级分类）" }, ...categoryOptions(categories).filter(o => { const c = categories.find(c => c.id === o.value)!; return c.parentId === 0 || categories.find(p => p.id === c.parentId)?.parentId === 0; })]} /></Form.Item>}<Form.Item label="分类名称" name="name" rules={[{ required: true, whitespace: true }]}><Input maxLength={100} /></Form.Item></Form>
    </Modal>
    {editor && bankId && <QuestionEditor bankId={bankId} categories={categories} question={editor.question} onClose={() => setEditor(undefined)} onSaved={() => { setEditor(undefined); message.success("试题已保存"); reload(); }} />}
    {transfer && bankId && <TransferDialog bankId={bankId} categories={categories} onClose={() => setTransfer(false)} onSaved={() => { setTransfer(false); reload(); }} />}
    <Drawer open={!!preview} width={780} title="试题详情与版本历史" onClose={() => { ++previewRequest.current; setPreview(undefined); }}>
      {preview && <><Space className={styles.toolbar}><span>查看版本</span><Select value={preview.version} style={{ width: 340 }} options={history.map(v => ({ value: v.version, label: `v${v.version} · ${v.createdAt} · 管理员 ${v.createdBy}` }))} onChange={v => void viewVersion(v)} /></Space>
        <Descriptions bordered column={2}>
          <Descriptions.Item label="编码">{preview.code}</Descriptions.Item><Descriptions.Item label="版本">v{preview.version}</Descriptions.Item>
          <Descriptions.Item label="题型">{api.questionTypes[preview.type]}</Descriptions.Item><Descriptions.Item label="难度">{api.difficulties[preview.difficulty]}</Descriptions.Item>
          <Descriptions.Item label="分类">{categoryOptions(categories).find(c => c.value === preview.categoryId)?.label || "未分类 / 历史分类"}</Descriptions.Item><Descriptions.Item label="状态">{api.statuses[preview.status]}</Descriptions.Item>
          <Descriptions.Item label="建议分值">{preview.suggestedScore}</Descriptions.Item><Descriptions.Item label="评分方式">{preview.gradingRule.strategy === "manual" ? "人工评分" : "完全匹配"}</Descriptions.Item>
          <Descriptions.Item label="题干" span={2}><div className={styles.text}>{preview.stem}</div></Descriptions.Item>
          {preview.options.length > 0 && <Descriptions.Item label="选项" span={2}>{preview.options.map(o => <p key={o.id} className={styles.text}>{o.id}：{o.text}</p>)}</Descriptions.Item>}
          <Descriptions.Item label="标准答案" span={2}><div className={styles.text}>{preview.type === "true_false" ? (preview.standardAnswer.value ? "正确" : "错误") : preview.type === "short_answer" ? preview.standardAnswer.text : preview.standardAnswer.optionIds?.join("、")}</div></Descriptions.Item>
          <Descriptions.Item label="解析" span={2}><div className={styles.text}>{preview.analysis || "暂无解析"}</div></Descriptions.Item><Descriptions.Item label="标签" span={2}>{preview.tags.map(t => <Tag key={t}>{t}</Tag>)}</Descriptions.Item>
        </Descriptions></>}
    </Drawer>
  </div>;
}
