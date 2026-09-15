import { useEffect, useState } from "react";
import {
  Button,
  Empty,
  Modal,
  Pagination,
  Skeleton,
  Spin,
  Tabs,
  Tag,
  message,
} from "antd";
import {
  CheckCircleFilled,
  ClockCircleOutlined,
  CloseCircleFilled,
  FileDoneOutlined,
  ReadOutlined,
  RightOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { exam } from "../../api";
import type {
  ExamRecordDetail,
  ExamRecordItem,
  FixedPaperSummary,
  PracticeAnswer,
  PracticeBank,
  PracticeQuestion,
} from "../../api/exam";
import { dateFormat } from "../../utils";
import styles from "./index.module.scss";

const PAGE_SIZE = 10;

function snapshotAnswerText(answer: PracticeAnswer | null | undefined, question: PracticeQuestion) {
  if (!answer) return "未作答";
  if (question.type === "true_false") {
    if (answer.value === undefined) return "未作答";
    return answer.value ? "正确" : "错误";
  }
  if (!answer.optionIds?.length) return "未作答";
  return answer.optionIds
    .map((id) => question.options.find((option) => option.id === id)?.text || id)
    .join("、");
}

export default function ExamCenterPage() {
  document.title = "考试中心";
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("papers");
  const [papers, setPapers] = useState<FixedPaperSummary[]>([]);
  const [banks, setBanks] = useState<PracticeBank[]>([]);
  const [records, setRecords] = useState<ExamRecordItem[]>([]);
  const [recordTotal, setRecordTotal] = useState(0);
  const [recordPage, setRecordPage] = useState(1);
  const [paperLoading, setPaperLoading] = useState(true);
  const [bankLoading, setBankLoading] = useState(true);
  const [recordLoading, setRecordLoading] = useState(false);
  const [recordDetail, setRecordDetail] = useState<ExamRecordDetail>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadBanks = async () => {
    setBankLoading(true);
    try {
      setBanks(await exam.banks());
    } catch {
      message.error("考试中心加载失败，请稍后重试");
    } finally {
      setBankLoading(false);
    }
  };

  const loadPapers = async () => {
    setPaperLoading(true);
    try {
      const data = await exam.papers();
      setPapers(data.items);
    } catch {
      message.error("正式试卷加载失败，请稍后重试");
    } finally {
      setPaperLoading(false);
    }
  };

  const loadRecords = async (page: number) => {
    setRecordLoading(true);
    try {
      const data = await exam.records(page, PAGE_SIZE);
      setRecords(data.items);
      setRecordTotal(data.total);
    } catch {
      message.error("考试记录加载失败，请稍后重试");
    } finally {
      setRecordLoading(false);
    }
  };

  const showRecordDetail = async (id: number) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setRecordDetail(undefined);
    try {
      setRecordDetail(await exam.recordDetail(id));
    } catch {
      message.error("答题快照加载失败，请稍后重试");
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    loadPapers();
    loadBanks();
  }, []);

  useEffect(() => {
    if (activeTab === "records") loadRecords(recordPage);
  }, [activeTab, recordPage]);

  const bankPanel = bankLoading ? (
    <div className={styles.grid} aria-label="题库加载中">
      {[1, 2, 3].map((item) => (
        <div className={styles.card} key={item}>
          <Skeleton active paragraph={{ rows: 3 }} />
        </div>
      ))}
    </div>
  ) : banks.length ? (
    <div className={styles.grid}>
      {banks.map((bank) => (
        <article className={styles.card} key={bank.id}>
          <div className={styles.cardTop}>
            <div className={styles.bankIcon}>
              <ReadOutlined />
            </div>
            <Tag color="success">开放练习</Tag>
          </div>
          <h2>{bank.name}</h2>
          <p>{bank.description || "管理员暂未填写题库说明"}</p>
          <div className={styles.cardFooter}>
            <span>
              <FileDoneOutlined /> {bank.questionCount} 道题
            </span>
            <Button
              type="primary"
              onClick={() =>
                navigate(`/exam/practice/${bank.id}`, { state: { bank } })
              }
            >
              开始练习 <RightOutlined />
            </Button>
          </div>
        </article>
      ))}
    </div>
  ) : (
    <div className={styles.empty}>
      <Empty description="暂无开放的考试题库" />
      <Button onClick={loadBanks}>重新加载</Button>
    </div>
  );

  const paperPanel = paperLoading ? (
    <div className={styles.grid} aria-label="试卷加载中">
      {[1, 2, 3].map((item) => (
        <div className={styles.card} key={item}>
          <Skeleton active paragraph={{ rows: 3 }} />
        </div>
      ))}
    </div>
  ) : papers.length ? (
    <div className={styles.grid}>
      {papers.map((paper) => (
        <article className={styles.card} key={paper.id}>
          <div className={styles.cardTop}>
            <div className={styles.bankIcon}><FileDoneOutlined /></div>
            <Tag color={paper.requiresManualGrading ? "warning" : "processing"}>
              {paper.requiresManualGrading ? "含主观题" : `及格 ${paper.passScore} 分`}
            </Tag>
          </div>
          <h2>{paper.name}</h2>
          <p>{paper.description || "管理员暂未填写试卷说明"}</p>
          <div className={styles.cardFooter}>
            <span>{paper.questionCount} 道题 · 满分 {paper.totalScore} 分</span>
            <Button
              type="primary"
              disabled={paper.requiresManualGrading}
              onClick={() => navigate(`/exam/paper/${paper.id}`, { state: { paper } })}
            >
              {paper.requiresManualGrading ? "暂不支持在线交卷" : "开始考试"} {!paper.requiresManualGrading && <RightOutlined />}
            </Button>
          </div>
        </article>
      ))}
    </div>
  ) : (
    <div className={styles.empty}>
      <Empty description="暂无已发布的正式试卷" />
      <Button onClick={loadPapers}>重新加载</Button>
    </div>
  );

  const recordPanel = recordLoading ? (
    <div className={styles.historyList} aria-label="考试记录加载中">
      <Skeleton active paragraph={{ rows: 5 }} />
    </div>
  ) : records.length ? (
    <>
      <div className={styles.historyList}>
        {records.map((item) => (
          <article className={styles.historyItem} key={item.id}>
            <div
              className={`${styles.resultIcon} ${
                item.passed ? styles.correct : styles.incorrect
              }`}
            >
              {item.passed ? (
                <CheckCircleFilled />
              ) : (
                <CloseCircleFilled />
              )}
            </div>
            <div className={styles.historyMain}>
              <div className={styles.historyStem}>{item.paperName}</div>
              <div className={styles.historyMeta}>
                <ClockCircleOutlined /> {dateFormat(item.examTime)} · 第 {item.version} 版 · 答对{" "}
                {item.correctCount} / {item.questionCount} 题
              </div>
            </div>
            <div className={styles.recordActions}>
              <div className={styles.score}>
                <strong>{item.score}</strong> / {item.maxScore} 分
                <div>{item.passed ? "已通过" : `未通过 · 及格 ${item.passScore} 分`}</div>
              </div>
              <Button onClick={() => showRecordDetail(item.id)}>查看答题快照</Button>
            </div>
          </article>
        ))}
      </div>
      <Pagination
        className={styles.pagination}
        current={recordPage}
        pageSize={PAGE_SIZE}
        total={recordTotal}
        showSizeChanger={false}
        onChange={setRecordPage}
      />
    </>
  ) : (
    <div className={styles.empty}>
      <Empty description="还没有考试记录，去选择一份正式试卷开始吧" />
      <Button type="primary" onClick={() => setActiveTab("papers")}>
        去考试
      </Button>
    </div>
  );

  return (
    <main className={styles.page}>
      <section className={styles.hero}>
        <div>
          <span className={styles.eyebrow}>EXAM CENTER</span>
          <h1>考试中心</h1>
          <p>参加正式试卷或按知识主题练习，交卷后查看成绩与通过结果。</p>
        </div>
        <div className={styles.heroStat}>
          <strong>{papers.length}</strong>
          <span>份正式试卷</span>
        </div>
      </section>

      <section className={styles.surface}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            { key: "papers", label: "正式试卷", children: paperPanel },
            { key: "banks", label: "开放题库", children: bankPanel },
            { key: "records", label: "考试记录", children: recordPanel },
          ]}
        />
      </section>

      <Modal
        title={recordDetail ? `${recordDetail.paperName} · 答题快照` : "答题快照"}
        open={detailOpen}
        width={900}
        footer={<Button onClick={() => setDetailOpen(false)}>关闭</Button>}
        onCancel={() => setDetailOpen(false)}
      >
        <Spin spinning={detailLoading}>
          {recordDetail && (
            <div className={styles.snapshot}>
              <div className={styles.snapshotSummary}>
                <span>考试时间：{dateFormat(recordDetail.examTime)}</span>
                <span>试卷版本：第 {recordDetail.version} 版</span>
                <span>成绩：{recordDetail.score} / {recordDetail.maxScore} 分</span>
                <Tag color={recordDetail.passed ? "success" : "error"}>
                  {recordDetail.passed ? "已通过" : "未通过"}
                </Tag>
              </div>
              {recordDetail.sections.map((section, sectionIndex) => (
                <section className={styles.snapshotSection} key={`${section.position}-${sectionIndex}`}>
                  <h3>{section.title}</h3>
                  {section.items.map((item, itemIndex) => (
                    <article className={styles.snapshotQuestion} key={item.questionId}>
                      <div className={styles.snapshotQuestionTitle}>
                        <span>{itemIndex + 1}. {item.question.stem}</span>
                        <Tag color={item.result === "correct" ? "success" : "error"}>
                          {item.result === "correct" ? "正确" : "错误"} · {item.score}/{item.maxScore} 分
                        </Tag>
                      </div>
                      {item.question.options?.length > 0 && (
                        <ol className={styles.snapshotOptions} type="A">
                          {item.question.options.map((option) => <li key={option.id}>{option.text}</li>)}
                        </ol>
                      )}
                      <div className={styles.snapshotAnswer}>
                        <div><strong>学员答案：</strong>{snapshotAnswerText(item.submittedAnswer, item.question)}</div>
                        <div><strong>正确答案：</strong>{snapshotAnswerText(item.standardAnswer, item.question)}</div>
                        <div><strong>答案解析：</strong>{item.analysis || "暂无解析"}</div>
                      </div>
                    </article>
                  ))}
                </section>
              ))}
            </div>
          )}
        </Spin>
      </Modal>
    </main>
  );
}
