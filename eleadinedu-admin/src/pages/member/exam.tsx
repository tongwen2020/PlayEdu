import { useEffect, useState } from "react";
import { Button, Empty, Input, Modal, Select, Space, Spin, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useSearchParams } from "react-router-dom";
import { BackBartment } from "../../compenents";
import { examPaper } from "../../api";
import type { ExamAnswer, ExamRecordDetail, ExamRecordItem } from "../../api/exam-paper";
import { showRequestError, type Question } from "../../api/question-bank";
import { dateFormat } from "../../utils";

const PAGE_SIZE = 10;

function answerText(answer: ExamAnswer | null | undefined, question: Question) {
  if (!answer) return "未作答";
  if (question.type === "true_false") {
    if (answer.value === undefined) return "未作答";
    return answer.value ? "正确" : "错误";
  }
  if (question.type === "short_answer") return answer.text || "未作答";
  if (!answer.optionIds?.length) return "未作答";
  return answer.optionIds
    .map((id) => question.options.find((option) => option.id === id)?.text || id)
    .join("、");
}

export default function MemberExamPage() {
  const [searchParams] = useSearchParams();
  const userId = Number(searchParams.get("id"));
  const userName = searchParams.get("name") || "学员";
  const [keyword, setKeyword] = useState("");
  const [queryKeyword, setQueryKeyword] = useState("");
  const [passed, setPassed] = useState<boolean | undefined>();
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<ExamRecordItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<ExamRecordDetail>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadRecords = async () => {
    if (!Number.isInteger(userId) || userId <= 0) return;
    setLoading(true);
    try {
      const data = await examPaper.memberRecords(userId, {
        keyword: queryKeyword || undefined,
        passed,
        page,
        size: PAGE_SIZE,
      });
      setItems(data.items);
      setTotal(data.total);
    } catch (error) {
      showRequestError(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRecords();
  }, [userId, queryKeyword, passed, page]);

  const showDetail = async (recordId: number) => {
    setDetailOpen(true);
    setDetail(undefined);
    setDetailLoading(true);
    try {
      setDetail(await examPaper.memberRecordDetail(userId, recordId));
    } catch (error) {
      setDetailOpen(false);
      showRequestError(error);
    } finally {
      setDetailLoading(false);
    }
  };

  const columns: ColumnsType<ExamRecordItem> = [
    { title: "试卷名称", dataIndex: "paperName" },
    { title: "试卷编码", dataIndex: "paperCode", width: 160 },
    { title: "版本", dataIndex: "version", width: 80, render: (value) => `第 ${value} 版` },
    { title: "考试时间", dataIndex: "examTime", width: 170, render: dateFormat },
    { title: "成绩", width: 130, render: (_, record) => `${record.score} / ${record.maxScore}` },
    { title: "答对题数", width: 110, render: (_, record) => `${record.correctCount} / ${record.questionCount}` },
    {
      title: "考试结果",
      dataIndex: "passed",
      width: 100,
      render: (value: boolean) => (
        <Tag color={value ? "success" : "error"}>{value ? "通过" : "未通过"}</Tag>
      ),
    },
    {
      title: "操作",
      key: "action",
      fixed: "right",
      width: 100,
      render: (_, record) => (
        <Button type="link" className="b-link c-red" onClick={() => showDetail(record.id)}>
          明细
        </Button>
      ),
    },
  ];

  if (!Number.isInteger(userId) || userId <= 0) {
    return <Empty description="学员参数无效" />;
  }

  return (
    <div className="eleadinedu-main-top">
      <div className="float-left mb-24">
        <BackBartment title={`${userName}的考试记录`} />
      </div>
      <div className="float-left j-b-flex mb-24">
        <Space wrap>
          <Input
            value={keyword}
            allowClear
            placeholder="请输入试卷名称或编码"
            style={{ width: 240 }}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={() => {
              setPage(1);
              setQueryKeyword(keyword.trim());
            }}
          />
          <Select
            allowClear
            placeholder="全部结果"
            style={{ width: 140 }}
            value={passed}
            options={[
              { label: "通过", value: true },
              { label: "未通过", value: false },
            ]}
            onChange={(value) => {
              setPage(1);
              setPassed(value);
            }}
          />
          <Button
            onClick={() => {
              setKeyword("");
              setQueryKeyword("");
              setPassed(undefined);
              setPage(1);
            }}
          >
            重 置
          </Button>
          <Button
            type="primary"
            onClick={() => {
              setPage(1);
              setQueryKeyword(keyword.trim());
            }}
          >
            查 询
          </Button>
        </Space>
      </div>
      <div className="float-left">
        <Table
          columns={columns}
          dataSource={items}
          loading={loading}
          rowKey="id"
          scroll={{ x: 1100 }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            onChange: setPage,
          }}
        />
      </div>

      <Modal
        title={detail ? `${detail.paperName} · 答题明细` : "答题明细"}
        open={detailOpen}
        width={900}
        footer={<Button onClick={() => setDetailOpen(false)}>关闭</Button>}
        onCancel={() => setDetailOpen(false)}
      >
        <Spin spinning={detailLoading}>
          {detail && (
            <div>
              <Space wrap className="mb-24">
                <span>考试时间：{dateFormat(detail.examTime)}</span>
                <span>成绩：{detail.score} / {detail.maxScore} 分</span>
                <span>及格分：{detail.passScore} 分</span>
                <Tag color={detail.passed ? "success" : "error"}>
                  {detail.passed ? "通过" : "未通过"}
                </Tag>
              </Space>
              {detail.sections.map((section, sectionIndex) => (
                <section key={`${section.position}-${sectionIndex}`} className="mb-24">
                  <h3>{section.title}</h3>
                  {section.items.map((item, itemIndex) => (
                    <div
                      key={item.questionId}
                      style={{ padding: "16px 0", borderBottom: "1px solid #f0f0f0" }}
                    >
                      <div className="j-b-flex">
                        <strong>{itemIndex + 1}. {item.question.stem}</strong>
                        <Tag color={item.result === "correct" ? "success" : "error"}>
                          {item.result === "correct" ? "正确" : "错误"} · {item.score}/
                          {item.maxScore} 分
                        </Tag>
                      </div>
                      {!!item.question.options?.length && (
                        <ol type="A">
                          {item.question.options.map((option) => (
                            <li key={option.id}>{option.text}</li>
                          ))}
                        </ol>
                      )}
                      <div>学员答案：{answerText(item.submittedAnswer, item.question)}</div>
                      <div>正确答案：{answerText(item.standardAnswer, item.question)}</div>
                      <div>答案解析：{item.analysis || "暂无解析"}</div>
                    </div>
                  ))}
                </section>
              ))}
            </div>
          )}
        </Spin>
      </Modal>
    </div>
  );
}
