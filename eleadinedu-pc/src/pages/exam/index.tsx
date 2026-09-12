import { useEffect, useState } from "react";
import {
  Button,
  Empty,
  Pagination,
  Skeleton,
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
import type { FixedPaperSummary, PracticeBank, PracticeHistoryItem } from "../../api/exam";
import { dateFormat } from "../../utils";
import styles from "./index.module.scss";

const PAGE_SIZE = 10;

export default function ExamCenterPage() {
  document.title = "考试中心";
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("papers");
  const [papers, setPapers] = useState<FixedPaperSummary[]>([]);
  const [banks, setBanks] = useState<PracticeBank[]>([]);
  const [history, setHistory] = useState<PracticeHistoryItem[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPage, setHistoryPage] = useState(1);
  const [paperLoading, setPaperLoading] = useState(true);
  const [bankLoading, setBankLoading] = useState(true);
  const [historyLoading, setHistoryLoading] = useState(false);

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

  const loadHistory = async (page: number) => {
    setHistoryLoading(true);
    try {
      const data = await exam.history(page, PAGE_SIZE);
      setHistory(data.items);
      setHistoryTotal(data.total);
    } catch {
      message.error("作答记录加载失败，请稍后重试");
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    loadPapers();
    loadBanks();
  }, []);

  useEffect(() => {
    if (activeTab === "history") loadHistory(historyPage);
  }, [activeTab, historyPage]);

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

  const historyPanel = historyLoading ? (
    <div className={styles.historyList} aria-label="作答记录加载中">
      <Skeleton active paragraph={{ rows: 5 }} />
    </div>
  ) : history.length ? (
    <>
      <div className={styles.historyList}>
        {history.map((item) => (
          <article className={styles.historyItem} key={item.id}>
            <div
              className={`${styles.resultIcon} ${
                item.result === "correct" ? styles.correct : styles.incorrect
              }`}
            >
              {item.result === "correct" ? (
                <CheckCircleFilled />
              ) : (
                <CloseCircleFilled />
              )}
            </div>
            <div className={styles.historyMain}>
              <div className={styles.historyStem}>{item.stem}</div>
              <div className={styles.historyMeta}>
                <ClockCircleOutlined /> {dateFormat(item.createdAt)}
              </div>
            </div>
            <div className={styles.score}>
              <strong>{item.score}</strong> / {item.maxScore} 分
            </div>
          </article>
        ))}
      </div>
      <Pagination
        className={styles.pagination}
        current={historyPage}
        pageSize={PAGE_SIZE}
        total={historyTotal}
        showSizeChanger={false}
        onChange={setHistoryPage}
      />
    </>
  ) : (
    <div className={styles.empty}>
      <Empty description="还没有作答记录，去选择一个题库开始吧" />
      <Button type="primary" onClick={() => setActiveTab("banks")}>
        去练习
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
            { key: "history", label: "我的作答记录", children: historyPanel },
          ]}
        />
      </section>
    </main>
  );
}
