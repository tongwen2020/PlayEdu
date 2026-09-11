import { useEffect, useMemo, useRef, useState } from "react";
import {
  Button,
  Checkbox,
  Empty,
  Modal,
  Progress,
  Radio,
  Skeleton,
  Tag,
  message,
} from "antd";
import {
  ArrowLeftOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  LeftOutlined,
  RightOutlined,
} from "@ant-design/icons";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { exam } from "../../api";
import type {
  PracticeAnswer,
  PracticeBank,
  PracticeQuestion,
  PracticePaperResult,
  PracticeResult,
} from "../../api/exam";
import { generateUUID } from "../../utils";
import styles from "./practice.module.scss";

type AnswerState = Record<number, PracticeAnswer>;
type ResultState = Record<number, PracticeResult>;

export function isAnswerEmpty(question: PracticeQuestion, answer?: PracticeAnswer) {
  if (!answer) return true;
  if (question.type === "true_false") return answer.value === undefined;
  return !answer.optionIds?.length;
}

export function answerText(
  answer: PracticeAnswer,
  question: PracticeQuestion
) {
  if (question.type === "true_false") return answer.value ? "正确" : "错误";
  return (answer.optionIds || [])
    .map((id) => question.options.find((option) => option.id === id)?.text || id)
    .join("、");
}

export default function ExamPracticePage() {
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
  const percent = questions.length ? Math.round((completed / questions.length) * 100) : 0;

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
      .catch(() => message.error("题目加载失败，请返回后重试"))
      .finally(() => setLoading(false));
  }, [numericBankId]);

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
      message.success(`自动阅卷完成，得分 ${result.score} 分`);
    } catch {
      message.error("交卷或自动阅卷失败，请检查网络后重试");
    } finally {
      setSubmitting(false);
    }
  };

  const confirmSubmit = () => {
    const unanswered = questions.length - answeredCount;
    Modal.confirm({
      title: "确认提交试卷？",
      content: unanswered ? `还有 ${unanswered} 道题未作答，交卷后将按 0 分计算。` : "交卷后将立即自动阅卷并给出分数。",
      okText: "确认交卷",
      cancelText: "继续答题",
      centered: true,
      onOk: gradePaper,
    });
  };

  const leave = () => {
    if (!paperResult && answeredCount > 0) {
      Modal.confirm({
        title: "确认退出练习？",
        content: "尚未交卷的答案不会保存。",
        okText: "确认退出",
        cancelText: "继续答题",
        centered: true,
        onOk: () => navigate("/exam"),
      });
    } else {
      navigate("/exam");
    }
  };

  const standardAnswer = useMemo(() => {
    if (!current || !currentResult) return "";
    return answerText(currentResult.standardAnswer, current);
  }, [current, currentResult]);

  if (loading) {
    return (
      <main className={styles.loading} aria-label="题目加载中">
        <Skeleton active paragraph={{ rows: 8 }} />
      </main>
    );
  }

  if (!current) {
    return (
      <main className={styles.emptyPage}>
        <Empty description="该题库暂时没有可练习的题目" />
        <Button type="primary" onClick={() => navigate("/exam")}>
          返回考试中心
        </Button>
      </main>
    );
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={leave}>
            退出练习
          </Button>
          <div className={styles.title}>{bank?.name || "题库练习"}</div>
          <div className={styles.progressSummary}>
            已完成 <strong>{completed}</strong> / {questions.length}
          </div>
        </div>
        <Progress percent={percent} showInfo={false} strokeColor="#ff4d4f" />
      </header>

      <div className={styles.workspace}>
        <section className={styles.questionPanel}>
          <div className={styles.questionMeta}>
            <Tag color="red">{exam.questionTypes[current.type]}</Tag>
            <Tag>{exam.difficulties[current.difficulty]}</Tag>
            <span>本题 {current.suggestedScore} 分</span>
          </div>
          <h1>
            <span>{currentIndex + 1}.</span> {current.stem}
          </h1>

          <div className={styles.options}>
            {current.type === "single_choice" && (
              <Radio.Group
                value={currentAnswer?.optionIds?.[0]}
                disabled={Boolean(paperResult)}
                onChange={(event) => selectAnswer({ optionIds: [event.target.value] })}
              >
                {current.options.map((option, index) => (
                  <Radio className={styles.option} value={option.id} key={option.id}>
                    <span className={styles.optionLetter}>
                      {String.fromCharCode(65 + index)}
                    </span>
                    {option.text}
                  </Radio>
                ))}
              </Radio.Group>
            )}
            {current.type === "multiple_choice" && (
              <Checkbox.Group
                value={currentAnswer?.optionIds || []}
                disabled={Boolean(paperResult)}
                onChange={(value) => selectAnswer({ optionIds: value as string[] })}
              >
                {current.options.map((option, index) => (
                  <Checkbox className={styles.option} value={option.id} key={option.id}>
                    <span className={styles.optionLetter}>
                      {String.fromCharCode(65 + index)}
                    </span>
                    {option.text}
                  </Checkbox>
                ))}
              </Checkbox.Group>
            )}
            {current.type === "true_false" && (
              <Radio.Group
                value={currentAnswer?.value}
                disabled={Boolean(paperResult)}
                onChange={(event) => selectAnswer({ value: event.target.value })}
              >
                <Radio className={styles.option} value={true}>
                  <span className={styles.optionLetter}>A</span>正确
                </Radio>
                <Radio className={styles.option} value={false}>
                  <span className={styles.optionLetter}>B</span>错误
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
              <div className={styles.analysisTitle}>
                {currentResult.result === "correct" ? (
                  <CheckCircleFilled />
                ) : (
                  <CloseCircleFilled />
                )}
                {currentResult.result === "correct" ? "回答正确" : "回答错误"}
                <span>
                  得分 {currentResult.score} / {currentResult.maxScore}
                </span>
              </div>
              <div className={styles.answerLine}>
                <strong>正确答案：</strong>{standardAnswer || "—"}
              </div>
              <div className={styles.answerLine}>
                <strong>答案解析：</strong>{currentResult.analysis || "暂无解析"}
              </div>
            </div>
          )}

          <div className={styles.actions}>
            <Button
              icon={<LeftOutlined />}
              disabled={currentIndex === 0}
              onClick={() => setCurrentIndex((value) => value - 1)}
            >
              上一题
            </Button>
            <div>
              {!paperResult && currentIndex < questions.length - 1 && (
                <Button
                  type="primary"
                  onClick={() => setCurrentIndex((value) => value + 1)}
                >
                  下一题 <RightOutlined />
                </Button>
              )}
              {!paperResult && currentIndex === questions.length - 1 && (
                <Button
                  type="primary"
                  loading={submitting}
                  onClick={confirmSubmit}
                >
                  提交试卷
                </Button>
              )}
              {paperResult && (
                <Button type="primary" onClick={() => navigate("/exam")}>
                  完成练习
                </Button>
              )}
            </div>
          </div>
        </section>

        <aside className={styles.answerCard}>
          <h2>答题卡</h2>
          {paperResult && (
            <div className={styles.cardTip}>
              自动阅卷得分：{paperResult.score} / {paperResult.maxScore}，答对 {paperResult.correctCount} / {paperResult.questionCount} 题
            </div>
          )}
          <div className={styles.legend}>
            <span><i className={styles.done} />{paperResult ? "正确" : "已作答"}</span>
            <span><i />{paperResult ? "错误/未答" : "未作答"}</span>
          </div>
          <div className={styles.numbers}>
            {questions.map((question, index) => (
              <button
                key={question.id}
                className={`${index === currentIndex ? styles.current : ""} ${
                  (paperResult ? results[question.id]?.result === "correct" : answers[question.id]) ? styles.submitted : ""
                }`}
                onClick={() => setCurrentIndex(index)}
                aria-label={`第 ${index + 1} 题${answers[question.id] ? "，已作答" : ""}`}
              >
                {index + 1}
              </button>
            ))}
          </div>
          {!paperResult && <div className={styles.cardTip}>整卷提交后将立即自动阅卷，答案不可修改。</div>}
          {!paperResult && currentIndex !== questions.length - 1 && (
            <Button type="primary" block loading={submitting} onClick={confirmSubmit}>
              提交试卷
            </Button>
          )}
        </aside>
      </div>
    </main>
  );
}
