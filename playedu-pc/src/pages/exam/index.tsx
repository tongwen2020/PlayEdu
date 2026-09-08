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
import type { PracticeBank, PracticeHistoryItem } from "../../api/exam";
import { dateFormat } from "../../utils";
import styles from "./index.module.scss";

const PAGE_SIZE = 10;

export default function ExamCenterPage() {
  document.title = "考试中心";
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("banks");
  const [banks, setBanks] = useState<PracticeBank[]>([]);
  const [history, setHistory] = useState<PracticeHistoryItem[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPage, setHistoryPage] = useState(1);
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
          <p>按知识主题完成练习，提交后立即查看判分与解析。</p>
        </div>
        <div className={styles.heroStat}>
          <strong>{banks.reduce((sum, bank) => sum + bank.questionCount, 0)}</strong>
          <span>道开放题目</span>
        </div>
      </section>

      <section className={styles.surface}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            { key: "banks", label: "开放题库", children: bankPanel },
            { key: "history", label: "我的作答记录", children: historyPanel },
          ]}
        />
      </section>
    </main>
  );
}
