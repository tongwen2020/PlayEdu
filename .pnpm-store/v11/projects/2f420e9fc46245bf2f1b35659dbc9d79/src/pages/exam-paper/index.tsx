import { useEffect, useRef, useState } from "react";
import { useSelector } from "react-redux";
import { Alert, Button, Card, Descriptions, Drawer, Form, Input, Modal, Popconfirm, Result, Select, Space, Statistic, Table, Tag, Typography, message } from "antd";
import * as api from "../../api/exam-paper";
import * as questionApi from "../../api/question-bank";
import PaperEditor from "./paper-editor";
import TransferDialog, { downloadJson } from "./transfer-dialog";
import styles from "./index.module.less";

const statusColors: Record<api.PaperStatus, string> = { draft: "default", published: "green", disabled: "orange", archived: "red" };
const categoryLabels = (categories: api.PaperCategory[]) => {
  const label = (category: api.PaperCategory, seen: number[] = []): string => { const parent = categories.find(c => c.id === category.parentId); return parent && !seen.includes(parent.id) ? `${label(parent, [...seen, category.id])} / ${category.name}` : category.name; };
  return categories.map(category => ({ value: category.id, label: label(category) }));
};
const answer = (question?: questionApi.Question) => {
  if (!question) return "-";
  if (question.type === "true_false") return question.standardAnswer.value ? "正确" : "错误";
  if (question.type === "short_answer") return question.standardAnswer.text || "-";
  return question.standardAnswer.optionIds?.map(id => `${id}：${question.options.find(option => option.id === id)?.text || ""}`).join("；") || "-";
};

export default function ExamPaperPage() {
  const login = useSelector((state: any) => state.loginUser.value);
  const permissions = login.permissions;
  const canView = permissions?.["exam-paper-view"] !== undefined;
  const canEdit = permissions?.["exam-paper-edit"] !== undefined;
  const canPublish = permissions?.["exam-paper-publish"] !== undefined;
  const canExport = permissions?.["exam-paper-export"] !== undefined;
  const [categories, setCategories] = useState<api.PaperCategory[]>([]);
  const [rows, setRows] = useState<api.Paper[]>([]); const [total, setTotal] = useState(0);
  const [query, setQuery] = useState<api.PaperQuery>({ page: 1, size: 20 });
  const [loading, setLoading] = useState(false); const [busy, setBusy] = useState(false); const [refresh, setRefresh] = useState(0);
  const [editor, setEditor] = useState<api.Paper | "new">(); const [transfer, setTransfer] = useState(false);
  const [categoryOpen, setCategoryOpen] = useState(false); const [categoryEdit, setCategoryEdit] = useState<Partial<api.PaperCategory>>();
  const [preview, setPreview] = useState<api.Paper>(); const [history, setHistory] = useState<api.PaperVersion[]>([]); const previewRequest = useRef(0);
  const [copying, setCopying] = useState<api.Paper>(); const [validation, setValidation] = useState<{ paper: api.Paper; result: api.ValidationResult }>();
  const [filterForm] = Form.useForm(); const [categoryForm] = Form.useForm(); const [copyForm] = Form.useForm();
  const reload = () => setRefresh(value => value + 1);
  useEffect(() => { if (canView) api.categories().then(setCategories).catch(questionApi.showRequestError); }, [canView, refresh]);
  useEffect(() => {
    if (!canView) return; let active = true; setLoading(true);
    api.papers(query).then(data => { if (active) { setRows(data.items); setTotal(data.total); } }).catch(questionApi.showRequestError).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [canView, query, refresh]);
  const act = async (action: () => Promise<unknown>, success = "操作成功") => { setBusy(true); try { await action(); message.success(success); reload(); } catch (e) { questionApi.showRequestError(e); } finally { setBusy(false); } };
  const openDetail = async (paper: api.Paper, mode: "edit" | "preview") => {
    const request = ++previewRequest.current; setBusy(true);
    try { const value = await api.detail(paper.id); if (request !== previewRequest.current) return; if (mode === "edit") setEditor(value); else { setPreview(value); setHistory(await api.versions(paper.id)); } } catch (e) { questionApi.showRequestError(e); } finally { setBusy(false); }
  };
  const validate = async (paper: api.Paper) => { setBusy(true); try { setValidation({ paper, result: await api.validatePaper(paper.id) }); } catch (e) { questionApi.showRequestError(e); } finally { setBusy(false); } };
  const showVersion = async (version?: number) => { if (!preview) return; const request = ++previewRequest.current; try { const value = await api.detail(preview.id, version); if (request === previewRequest.current) setPreview(value); } catch (e) { questionApi.showRequestError(e); } };
  if (!canView) return <Result status="403" title="无试卷库查看权限" subTitle="请联系管理员分配试卷库查看权限。" />;
  const ownCategories = categories.filter(category => category.ownerId === login.user?.id);
  return <div className={styles.page}>
    <Space className={styles.toolbar} wrap><Typography.Title level={4} style={{ margin: 0 }}>试卷库</Typography.Title><Space wrap><Button onClick={reload}>刷新</Button><Button onClick={() => setCategoryOpen(true)}>分类管理</Button>{canEdit && <><Button onClick={() => setTransfer(true)}>导入试卷</Button><Button type="primary" onClick={() => setEditor("new")}>新建试卷</Button></>}</Space></Space>
    <Card>
      <Form form={filterForm} layout="inline" className={styles.filters} onFinish={values => setQuery(old => ({ ...values, page: 1, size: old.size }))}>
        <Form.Item name="keyword"><Input allowClear placeholder="编码或名称" /></Form.Item><Form.Item name="categoryId"><Select allowClear placeholder="全部分类" style={{ width: 200 }} options={categoryLabels(categories)} /></Form.Item>
        <Form.Item name="status"><Select allowClear placeholder="全部状态" style={{ width: 120 }} options={Object.entries(api.paperStatuses).map(([value, label]) => ({ value, label }))} /></Form.Item><Form.Item name="tag"><Input allowClear placeholder="标签" /></Form.Item>
        <Form.Item><Space><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { filterForm.resetFields(); setQuery(old => ({ page: 1, size: old.size })); }}>重置</Button></Space></Form.Item>
      </Form>
      <Table rowKey="id" loading={loading || busy} dataSource={rows} scroll={{ x: 1250 }} pagination={{ current: query.page, pageSize: query.size, total, showSizeChanger: true, showTotal: value => `共 ${value} 份`, onChange: (page, size) => setQuery(old => ({ ...old, page, size })) }} columns={[
        { title: "编码", dataIndex: "code", width: 150 }, { title: "试卷名称", dataIndex: "name", width: 220, ellipsis: true, render: (name, paper) => <Button type="link" style={{ padding: 0 }} onClick={() => void openDetail(paper, "preview")}>{name}</Button> },
        { title: "分类", dataIndex: "categoryId", width: 160, render: id => categoryLabels(categories).find(item => item.value === id)?.label || "未分类" }, { title: "状态", dataIndex: "status", width: 90, render: (status: api.PaperStatus) => <Tag color={statusColors[status]}>{api.paperStatuses[status]}</Tag> },
        { title: "版本/修订", width: 100, render: (_, paper) => `v${paper.currentVersion} / r${paper.revision}` }, { title: "题数", dataIndex: "questionCount", width: 70 }, { title: "总分", dataIndex: "totalScore", width: 80 },
        { title: "标签", dataIndex: "tags", width: 150, render: tags => tags.map((tag: string) => <Tag key={tag}>{tag}</Tag>) },
        { title: "操作", fixed: "right", width: 360, render: (_, paper: api.Paper) => <Space size={0} wrap>
          <Button type="link" onClick={() => void openDetail(paper, "preview")}>预览/版本</Button>{canEdit && paper.status !== "archived" && <Button type="link" onClick={() => void openDetail(paper, "edit")}>编辑</Button>}{canEdit && <Button type="link" onClick={() => void validate(paper)}>校验</Button>}
          {canPublish && paper.status === "draft" && <Button type="link" onClick={() => void validate(paper)}>发布</Button>}{canPublish && paper.status === "published" && <Popconfirm title="停用后仍可重新启用" onConfirm={() => act(() => api.changeStatus(paper.id, paper.revision, "disabled"), "试卷已停用")}><Button type="link">停用</Button></Popconfirm>}{canPublish && paper.status === "disabled" && <Button type="link" onClick={() => act(() => api.changeStatus(paper.id, paper.revision, "published"), "试卷已重新启用")}>启用</Button>}
          {canEdit && <Button type="link" onClick={() => { copyForm.resetFields(); setCopying(paper); copyForm.setFieldsValue({ code: `${paper.code}_copy`, name: `${paper.name}（副本）`, version: paper.currentVersion || undefined }); }}>复制</Button>}{canExport && paper.currentVersion > 0 && <Button type="link" onClick={() => act(async () => downloadJson(await api.exportPaper(paper.id), `${paper.code}_v${paper.currentVersion}.json`), "导出成功")}>导出</Button>}
          {canPublish && (paper.status === "published" || paper.status === "disabled") && <Popconfirm title="归档是终态，归档后不能恢复，确认继续？" onConfirm={() => act(() => api.changeStatus(paper.id, paper.revision, "archived"), "试卷已归档")}><Button type="link" danger>归档</Button></Popconfirm>}{canEdit && paper.status === "draft" && paper.currentVersion === 0 && <Popconfirm title="永久删除该未发布草稿？" onConfirm={() => act(() => api.deleteDraft(paper.id), "草稿已删除")}><Button type="link" danger>删除</Button></Popconfirm>}
        </Space> },
      ]} />
    </Card>
    {editor && <PaperEditor paper={editor === "new" ? undefined : editor} categories={categories} currentOwnerId={login.user?.id} onClose={() => setEditor(undefined)} onSaved={saved => { setEditor(saved); reload(); }} />}
    {transfer && <TransferDialog categories={ownCategories} onClose={() => setTransfer(false)} onSaved={() => { setTransfer(false); reload(); }} />}
    <Drawer open={categoryOpen} title="试卷分类" width={600} onClose={() => setCategoryOpen(false)} extra={canEdit && <Button type="primary" onClick={() => { const value = { parentId: 0 }; categoryForm.resetFields(); setCategoryEdit(value); categoryForm.setFieldsValue(value); }}>新增分类</Button>}>
      <Alert type="info" showIcon message="试卷分类独立于课程和试题分类，最多三级。" style={{ marginBottom: 16 }} />
      <Table rowKey="id" size="small" dataSource={categories} pagination={false} columns={[{ title: "分类", render: (_, category) => categoryLabels(categories).find(item => item.value === category.id)?.label }, { title: "所有者", dataIndex: "ownerId", width: 90 }, { title: "操作", width: 140, render: (_, category) => canEdit && category.ownerId === login.user?.id ? <Space><Button type="link" onClick={() => { setCategoryEdit(category); categoryForm.setFieldsValue(category); }}>重命名</Button><Popconfirm title="分类必须为空且无子分类才能删除" onConfirm={() => act(() => api.deleteCategory(category.id), "分类已删除")}><Button type="link" danger>删除</Button></Popconfirm></Space> : "-" }]} />
    </Drawer>
    <Modal open={Boolean(categoryEdit)} title={categoryEdit?.id ? "重命名分类" : "新增分类"} onCancel={() => setCategoryEdit(undefined)} onOk={() => void categoryForm.validateFields().then(values => act(async () => { categoryEdit?.id ? await api.renameCategory(categoryEdit.id, values.name) : await api.createCategory(values.parentId || 0, values.name); setCategoryEdit(undefined); }, "分类已保存"))}>
      <Form form={categoryForm} layout="vertical"><Form.Item label="上级分类" name="parentId"><Select disabled={Boolean(categoryEdit?.id)} options={[{ value: 0, label: "顶级分类" }, ...categoryLabels(ownCategories)]} /></Form.Item><Form.Item label="分类名称" name="name" rules={[{ required: true, whitespace: true }]}><Input maxLength={100} /></Form.Item></Form>
    </Modal>
    <Modal open={Boolean(copying)} title="复制为新草稿" onCancel={() => setCopying(undefined)} onOk={() => void copyForm.validateFields().then(values => act(async () => { await api.copy(copying!.id, values.version, values.code, values.name); setCopying(undefined); }, "试卷已复制"))}>
      <Form form={copyForm} layout="vertical"><Form.Item label="来源版本" name="version"><Select allowClear placeholder="当前草稿" options={Array.from({ length: copying?.currentVersion || 0 }, (_, i) => ({ value: i + 1, label: `正式版本 v${i + 1}` }))} /></Form.Item><Form.Item label="新编码" name="code" rules={[{ required: true }, { pattern: /^[a-zA-Z0-9_-]{1,64}$/ }]}><Input /></Form.Item><Form.Item label="新名称" name="name" rules={[{ required: true, whitespace: true }]}><Input maxLength={100} /></Form.Item></Form>
    </Modal>
    <Modal open={Boolean(validation)} width={680} title="发布前校验" onCancel={() => setValidation(undefined)} okText="确认发布" okButtonProps={{ disabled: !validation?.result.valid || !canPublish }} onOk={() => validation && act(async () => { await api.publish(validation.paper.id, validation.paper.revision); setValidation(undefined); }, "试卷发布成功")}>
      {validation && <><Alert showIcon type={validation.result.valid ? "success" : "error"} message={validation.result.valid ? "校验通过，可以发布" : `发现 ${validation.result.errors.length} 个问题`} /><Descriptions size="small" column={3} style={{ marginTop: 16 }}><Descriptions.Item label="题数">{validation.result.summary.questionCount}</Descriptions.Item><Descriptions.Item label="总分">{validation.result.summary.totalScore}</Descriptions.Item><Descriptions.Item label="主观题分">{validation.result.summary.subjectiveScore}</Descriptions.Item></Descriptions>{validation.result.errors.map((error, i) => <Alert key={i} type="error" message={error} style={{ marginTop: 8 }} />)}{validation.result.warnings.map((warning, i) => <Alert key={i} type="warning" message={warning} style={{ marginTop: 8 }} />)}{!canPublish && validation.result.valid && <Alert type="info" message="校验已通过，但当前账号没有发布权限。" style={{ marginTop: 8 }} />}</>}
    </Modal>
    <Drawer open={Boolean(preview)} width="min(980px, 96vw)" title={preview ? `${preview.name} · ${preview.version ? `正式版本 v${preview.version}` : "当前草稿"}` : "试卷预览"} onClose={() => { setPreview(undefined); setHistory([]); }}>
      {preview && <><Space wrap style={{ marginBottom: 16 }}><Button onClick={() => void showVersion(undefined)}>当前草稿</Button>{history.map(version => <Button key={version.version} type={preview.version === version.version ? "primary" : "default"} onClick={() => void showVersion(version.version)}>v{version.version}</Button>)}</Space>
        <Card size="small" className={styles.summary}><Space size={32} wrap><Statistic title="题数" value={preview.questionCount} /><Statistic title="总分" value={preview.totalScore} precision={2} /><Statistic title="客观题" value={preview.objectiveScore} precision={2} /><Statistic title="主观题" value={preview.subjectiveScore} precision={2} /></Space></Card>
        <Descriptions bordered size="small" column={2}><Descriptions.Item label="编码">{preview.code}</Descriptions.Item><Descriptions.Item label="状态"><Tag color={statusColors[preview.status]}>{api.paperStatuses[preview.status]}</Tag></Descriptions.Item><Descriptions.Item label="分类">{categoryLabels(categories).find(item => item.value === preview.categoryId)?.label || "未分类"}</Descriptions.Item><Descriptions.Item label="标签">{preview.tags.map(tag => <Tag key={tag}>{tag}</Tag>)}</Descriptions.Item><Descriptions.Item label="说明" span={2}><div className={styles.text}>{preview.description || "暂无说明"}</div></Descriptions.Item></Descriptions>
        {preview.sections.map((section, index) => <Card key={index} title={`${index + 1}. ${section.title}`} extra={`${section.items.reduce((sum, item) => sum + Number(item.score), 0).toFixed(2)} 分`} style={{ marginTop: 16 }}><div className={styles.text}>{section.description}</div>{section.items.map((item, itemIndex) => <div className={styles.question} key={`${item.questionId}-${itemIndex}`}><Typography.Text strong>{itemIndex + 1}. {item.question?.stem || `试题 #${item.questionId}`}</Typography.Text><Tag style={{ marginLeft: 8 }}>{item.score} 分</Tag>{item.question?.options.map(option => <div key={option.id}>{option.id}. {option.text}</div>)}<div className={styles.answer}><b>答案：</b>{answer(item.question)}<br /><b>解析：</b>{item.question?.analysis || "暂无解析"}</div></div>)}</Card>)}
      </>}
    </Drawer>
  </div>;
}
