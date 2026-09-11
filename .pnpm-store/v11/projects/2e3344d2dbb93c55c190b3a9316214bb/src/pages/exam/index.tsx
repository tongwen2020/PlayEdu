import { useEffect, useMemo, useState } from "react";
import { Button, PullToRefresh, Skeleton, Tabs, Toast } from "antd-mobile";
import { useNavigate } from "react-router-dom";
import { exam } from "../../api";
import type { PracticeBank, PracticeHistoryItem } from "../../api/exam";
import { dateFormat } from "../../utils";
import styles from "./index.module.scss";

const PAGE_SIZE = 10;

const ExamCenterPage = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("banks");
  const [banks, setBanks] = useState<PracticeBank[]>([]);
  const [history, setHistory] = useState<PracticeHistoryItem[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPage, setHistoryPage] = useState(1);
  const [bankLoading, setBankLoading] = useState(true);
  const [historyLoading, setHistoryLoading] = useState(false);

  const totalQuestions = useMemo(
    () => banks.reduce((sum, bank) => sum + bank.questionCount, 0),
    [banks]
  );
  const totalHistoryPages = Math.max(1, Math.ceil(historyTotal / PAGE_SIZE));

  const loadBanks = async () => {
    setBankLoading(true);
    try {
      setBanks(await exam.banks());
    } catch {
      Toast.show({ content: "考试中心加载失败，请稍后重试" });
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
      Toast.show({ content: "作答记录加载失败，请稍后重试" });
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    document.title = "考试中心";
    loadBanks();
  }, []);

  useEffect(() => {
    if (activeTab === "history") {
      loadHistory(historyPage);
    }
  }, [activeTab, historyPage]);

  const renderLoading = () => (
    <div className={styles["loading-list"]} aria-label="内容加载中">
      {[1, 2, 3].map((item) => (
        <div className={styles["loading-card"]} key={item}>
          <Skeleton animated style={{ height: 20, width: "55%" }} />
          <Skeleton animated style={{ height: 16, width: "100%" }} />
          <Skeleton animated style={{ height: 36, width: "100%" }} />
        </div>
      ))}
    </div>
  );

  const bankPanel = bankLoading ? (
    renderLoading()
  ) : banks.length > 0 ? (
    <div className={styles["bank-list"]}>
      {banks.map((bank) => (
        <article className={styles["bank-card"]} key={bank.id}>
          <div className={styles["bank-top"]}>
            <div className={styles["bank-icon"]}>题</div>
            <span className={styles["open-tag"]}>开放练习</span>
          </div>
          <h2>{bank.name}</h2>
          <p>{bank.description || "管理员暂未填写题库说明"}</p>
          <div className={styles["bank-footer"]}>
            <span>{bank.questionCount} 道题</span>
            <Button
              color="primary"
              size="small"
              onClick={() =>
                navigate(`/exam/practice/${bank.id}`, { state: { bank } })
              }
            >
              开始练习
            </Button>
          </div>
        </article>
      ))}
    </div>
  ) : (
    <div className={styles.empty}>
      <div className={styles["empty-icon"]}>暂无</div>
      <p>暂无开放的考试题库</p>
      <Button onClick={loadBanks}>重新加载</Button>
    </div>
  );

  const historyPanel = historyLoading ? (
    renderLoading()
  ) : history.length > 0 ? (
    <>
      <div className={styles["history-list"]}>
        {history.map((item) => (
          <article className={styles["history-item"]} key={item.id}>
            <div
              className={`${styles["result-icon"]} ${
                item.result === "correct" ? styles.correct : styles.incorrect
              }`}
              aria-label={item.result === "correct" ? "回答正确" : "回答错误"}
            >
              {item.result === "correct" ? "✓" : "×"}
            </div>
            <div className={styles["history-main"]}>
              <div className={styles["history-stem"]}>{item.stem}</div>
              <div className={styles["history-meta"]}>
                {dateFormat(item.createdAt)}
              </div>
            </div>
            <div className={styles.score}>
              <strong>{item.score}</strong>
              <span>/ {item.maxScore} 分</span>
            </div>
          </article>
        ))}
      </div>
      {historyTotal > PAGE_SIZE && (
        <div className={styles.pagination}>
          <Button
            size="small"
            disabled={historyPage === 1}
            onClick={() => setHistoryPage((page) => page - 1)}
          >
            上一页
          </Button>
          <span>
            {historyPage} / {totalHistoryPages}
          </span>
          <Button
            size="small"
            disabled={historyPage >= totalHistoryPages}
            onClick={() => setHistoryPage((page) => page + 1)}
          >
            下一页
          </Button>
        </div>
      )}
    </>
  ) : (
    <div className={styles.empty}>
      <div className={styles["empty-icon"]}>暂无</div>
      <p>还没有作答记录，去选择一个题库开始吧</p>
      <Button color="primary" onClick={() => setActiveTab("banks")}>
        去练习
      </Button>
    </div>
  );

  return (
    <main className={styles.page}>
      <section className={styles.hero}>
        <div>
          <span>EXAM CENTER</span>
          <h1>考试中心</h1>
          <p>逐题练习，提交后立即查看判分与解析。</p>
        </div>
        <div className={styles["hero-stat"]}>
          <strong>{totalQuestions}</strong>
          <span>道开放题目</span>
        </div>
      </section>

      <section className={styles.surface}>
        <Tabs activeKey={activeTab} onChange={setActiveTab}>
          <Tabs.Tab title="开放题库" key="banks">
            <PullToRefresh onRefresh={loadBanks}>{bankPanel}</PullToRefresh>
          </Tabs.Tab>
          <Tabs.Tab title="我的作答" key="history">
            <PullToRefresh onRefresh={() => loadHistory(historyPage)}>
              {historyPanel}
            </PullToRefresh>
          </Tabs.Tab>
        </Tabs>
      </section>
    </main>
  );
};

export default ExamCenterPage;
