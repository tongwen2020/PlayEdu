import { useEffect, useMemo, useRef, useState } from "react";
import {
  Button,
  Checkbox,
  Dialog,
  ProgressBar,
  Radio,
  Skeleton,
  Toast,
} from "antd-mobile";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { exam } from "../../api";
import type {
  PracticeAnswer,
  PracticeBank,
  PracticePaperResult,
  PracticeQuestion,
  PracticeResult,
} from "../../api/exam";
import { generateUUID } from "../../utils";
import styles from "./practice.module.scss";

type AnswerState = Record<number, PracticeAnswer>;
type ResultState = Record<number, PracticeResult>;

export function isAnswerEmpty(
  question: PracticeQuestion,
  answer?: PracticeAnswer
) {
  if (!answer) return true;
  if (question.type === "true_false") return answer.value === undefined;
  return !answer.optionIds?.length;
}

export function answerText(
  answer: PracticeAnswer,
  question: PracticeQuestion
) {
  if (question.type === "true_false") {
    return answer.value ? "正确" : "错误";
  }
  return (answer.optionIds || [])
    .map((id) => question.options.find((option) => option.id === id)?.text || id)
    .join("、");
}

const ExamPracticePage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { bankId } = useParams();
  const bank = (location.state as { bank?: PracticeBank } | null)?.bank;
  const numericBankId = Number(bankId);
  const [questions, setQuestions] = useState<PracticeQuestion[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<AnswerState>({});
  const [results, setResults] = useState<ResultState>({});
  const [paperResult, setPaperResult] = useState<PracticePaperResult>();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const request = useRef<{ key: string; signature: string }>();

  const current = questions[currentIndex];
  const currentAnswer = current ? answers[current.id] : undefined;
  const currentResult = current ? results[current.id] : undefined;
  const answeredCount = questions.filter(
    (question) => !isAnswerEmpty(question, answers[question.id])
  ).length;
  const completed = paperResult ? questions.length : answeredCount;
  const percent = questions.length
    ? Math.round((completed / questions.length) * 100)
    : 0;
  const hasUnsubmittedAnswer = !paperResult && answeredCount > 0;

  useEffect(() => {
    document.title = bank?.name ? `${bank.name} - 考试中心` : "在线答题";
  }, [bank?.name]);

  useEffect(() => {
    if (!Number.isInteger(numericBankId) || numericBankId <= 0) {
      setLoading(false);
      return;
    }
    exam
      .questions(numericBankId)
      .then((data) => setQuestions(data.items))
      .catch(() => Toast.show({ content: "题目加载失败，请返回后重试" }))
      .finally(() => setLoading(false));
  }, [numericBankId]);

  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!hasUnsubmittedAnswer) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", beforeUnload);
    return () => window.removeEventListener("beforeunload", beforeUnload);
  }, [hasUnsubmittedAnswer]);

  const selectAnswer = (answer: PracticeAnswer) => {
    if (!current || paperResult) return;
    setAnswers((old) => ({ ...old, [current.id]: answer }));
  };

  const gradePaper = async () => {
    if (submitting || paperResult) return;
    setSubmitting(true);
    try {
      const payload = questions
        .filter((question) => !isAnswerEmpty(question, answers[question.id]))
        .map((question) => ({
          questionId: question.id,
          version: question.version,
          answer: answers[question.id],
        }));
      const signature = JSON.stringify(payload);
      if (!request.current || request.current.signature !== signature)
        request.current = { key: generateUUID(), signature };
      const result = await exam.submitPaper(
        numericBankId,
        payload,
        request.current.key
      );
      setPaperResult(result);
      setResults(
        Object.fromEntries(result.items.map((item) => [item.questionId, item]))
      );
      Toast.show({ content: `自动阅卷完成，得分 ${result.score} 分` });
    } catch {
      Toast.show({ content: "交卷或自动阅卷失败，请检查网络后重试" });
    } finally {
      setSubmitting(false);
    }
  };

  const confirmSubmit = async () => {
    const unanswered = questions.length - answeredCount;
    const confirmed = await Dialog.confirm({
      title: "确认提交试卷？",
      content: unanswered
        ? `还有 ${unanswered} 道题未作答，交卷后将按 0 分计算。`
        : "交卷后将立即自动阅卷并给出分数。",
      confirmText: "确认交卷",
      cancelText: "继续答题",
    });
    if (confirmed) await gradePaper();
  };

  const leave = async () => {
    if (hasUnsubmittedAnswer) {
      const confirmed = await Dialog.confirm({
        title: "确认退出练习？",
        content: "尚未交卷的答案不会保存。",
        confirmText: "确认退出",
        cancelText: "继续答题",
      });
      if (!confirmed) return;
    }
    navigate("/exam");
  };

  const standardAnswer = useMemo(() => {
    if (!current || !currentResult) return "";
    return answerText(currentResult.standardAnswer, current);
  }, [current, currentResult]);

  if (loading) {
    return (
      <main className={styles.loading} aria-label="题目加载中">
        <Skeleton animated style={{ height: 18, width: "45%" }} />
        <Skeleton animated style={{ height: 60, width: "100%" }} />
        {[1, 2, 3, 4].map((item) => (
          <Skeleton key={item} animated style={{ height: 54, width: "100%" }} />
        ))}
      </main>
    );
  }

  if (!current) {
    return (
      <main className={styles["empty-page"]}>
        <div className={styles["empty-icon"]}>暂无</div>
        <p>该题库暂时没有可练习的题目</p>
        <Button color="primary" onClick={() => navigate("/exam")}>
          返回考试中心
        </Button>
      </main>
    );
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div className={styles["header-main"]}>
          <button className={styles.back} onClick={leave} aria-label="退出练习">
            ‹
          </button>
          <div className={styles.title}>{bank?.name || "题库练习"}</div>
          <div className={styles.summary}>
            <strong>{completed}</strong>/{questions.length}
          </div>
        </div>
        <ProgressBar percent={percent} />
      </header>

      <section className={styles["question-card"]}>
        <div className={styles.meta}>
          <span className={styles["type-tag"]}>
            {exam.questionTypes[current.type]}
          </span>
          <span className={styles["difficulty-tag"]}>
            {exam.difficulties[current.difficulty]}
          </span>
          <span className={styles.points}>本题 {current.suggestedScore} 分</span>
        </div>

        <h1>
          <span>{currentIndex + 1}.</span> {current.stem}
        </h1>

        <div className={styles.options}>
          {current.type === "single_choice" && (
            <Radio.Group
              value={currentAnswer?.optionIds?.[0]}
              onChange={(value) => selectAnswer({ optionIds: [String(value)] })}
            >
              {current.options.map((option, index) => (
                <Radio
                  block
                  disabled={Boolean(paperResult)}
                  className={styles.option}
                  value={option.id}
                  key={option.id}
                >
                  <span className={styles.letter}>
                    {String.fromCharCode(65 + index)}
                  </span>
                  <span>{option.text}</span>
                </Radio>
              ))}
            </Radio.Group>
          )}

          {current.type === "multiple_choice" && (
            <Checkbox.Group
              value={currentAnswer?.optionIds || []}
              onChange={(value) =>
                selectAnswer({ optionIds: value.map(String) })
              }
            >
              {current.options.map((option, index) => (
                <Checkbox
                  block
                  disabled={Boolean(paperResult)}
                  className={styles.option}
                  value={option.id}
                  key={option.id}
                >
                  <span className={styles.letter}>
                    {String.fromCharCode(65 + index)}
                  </span>
                  <span>{option.text}</span>
                </Checkbox>
              ))}
            </Checkbox.Group>
          )}

          {current.type === "true_false" && (
            <Radio.Group
              value={
                currentAnswer?.value === undefined
                  ? undefined
                  : String(currentAnswer.value)
              }
              onChange={(value) => selectAnswer({ value: value === "true" })}
            >
              <Radio
                block
                disabled={Boolean(paperResult)}
                className={styles.option}
                value="true"
              >
                <span className={styles.letter}>A</span>
                <span>正确</span>
              </Radio>
              <Radio
                block
                disabled={Boolean(currentResult)}
                className={styles.option}
                value="false"
              >
                <span className={styles.letter}>B</span>
                <span>错误</span>
              </Radio>
            </Radio.Group>
          )}
        </div>

        {currentResult && (
          <div
            className={`${styles.analysis} ${
              currentResult.result === "correct" ? styles.pass : styles.fail
            }`}
          >
            <div className={styles["analysis-title"]}>
              <span>
                {currentResult.result === "correct" ? "✓" : "×"} {" "}
                {currentResult.result === "correct" ? "回答正确" : "回答错误"}
              </span>
              <span>
                {currentResult.score} / {currentResult.maxScore} 分
              </span>
            </div>
            <div className={styles["answer-line"]}>
              <strong>正确答案：</strong>
              {standardAnswer || "—"}
            </div>
            <div className={styles["answer-line"]}>
              <strong>答案解析：</strong>
              {currentResult.analysis || "暂无解析"}
            </div>
          </div>
        )}

        <div className={styles.actions}>
          <Button
            disabled={currentIndex === 0}
            onClick={() => setCurrentIndex((value) => value - 1)}
          >
            上一题
          </Button>
          {!paperResult && currentIndex < questions.length - 1 && (
            <Button
              color="primary"
              onClick={() => setCurrentIndex((value) => value + 1)}
            >
              下一题
            </Button>
          )}
          {!paperResult && currentIndex === questions.length - 1 && (
            <Button
              color="primary"
              loading={submitting}
              onClick={confirmSubmit}
            >
              提交试卷
            </Button>
          )}
          {paperResult && (
            <Button color="primary" onClick={leave}>
              完成练习
            </Button>
          )}
        </div>
      </section>

      <section className={styles["answer-card"]}>
        <div className={styles["answer-card-title"]}>
          <h2>答题卡</h2>
          <span>{paperResult ? `${paperResult.score} / ${paperResult.maxScore} 分` : "整卷自动阅卷"}</span>
        </div>
        <div className={styles.numbers}>
          {questions.map((question, index) => (
            <button
              key={question.id}
              className={`${index === currentIndex ? styles.current : ""} ${
                (paperResult ? results[question.id]?.result === "correct" : answers[question.id]) ? styles.submitted : ""
              }`}
              onClick={() => setCurrentIndex(index)}
              aria-label={`第 ${index + 1} 题${
                answers[question.id] ? "，已作答" : ""
              }`}
            >
              {index + 1}
            </button>
          ))}
        </div>
        {!paperResult && currentIndex !== questions.length - 1 && (
          <Button block color="primary" loading={submitting} onClick={confirmSubmit}>
            提交试卷
          </Button>
        )}
      </section>
    </main>
  );
};

export default ExamPracticePage;
