import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import ts from "typescript";

// Compile the pure import boundary with the project's existing TypeScript runtime.
const source = readFileSync(new URL("../src/pages/question-bank/import-format.ts", import.meta.url), "utf8");
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ESNext, module: ts.ModuleKind.ESNext } });
const { parseImport } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString("base64")}`);
const single = { bankId: 1, code: "Q1", categoryId: null, type: "single_choice", difficulty: "easy", stem: "1 + 1 = ?", options: [{ id: "A", text: "2" }, { id: "B", text: "3" }], standardAnswer: { optionIds: ["A"] }, gradingRule: { strategy: "exact_match" }, suggestedScore: 1, analysis: "", tags: [], status: "draft" };
const parse = (rows, categories = []) => parseImport(JSON.stringify(rows), 2, categories);

test("imports all four types and preserves a false answer", () => {
  const rows = [single,
    { ...single, code: "Q2", type: "multiple_choice", options: [...single.options, { id: "C", text: "4" }], standardAnswer: { optionIds: ["A", "C"] } },
    { ...single, code: "Q3", type: "true_false", options: [], standardAnswer: { value: false } },
    { ...single, code: "Q4", type: "short_answer", options: [], standardAnswer: { text: "评分要点" }, gradingRule: { strategy: "manual" } },
  ];
  assert.equal(parse(rows).length, 4);
  assert.equal(parse(rows)[2].standardAnswer.value, false);
});
test("exported records become new questions in the selected bank", () => {
  const [q] = parse([{ ...single, id: 42, version: 7, expectedVersion: 7 }]);
  assert.equal(q.bankId, 2);
  assert.equal(q.id, undefined);
  assert.equal(q.expectedVersion, undefined);
  assert.equal(q.version, undefined);
});
test("rejects foreign categories instead of silently discarding them", () => {
  assert.throws(() => parse([{ ...single, categoryId: 99 }]), /第 1 题.*分类/);
  assert.equal(parse([{ ...single, categoryId: 99 }], [{ id: 99, bankId: 2, parentId: 0, name: "分类" }])[0].categoryId, 99);
});
test("rejects duplicate codes and invalid choice answers with row numbers", () => {
  assert.throws(() => parse([single, single]), /第 2 题.*编码重复/);
  assert.throws(() => parse([{ ...single, standardAnswer: { optionIds: ["C"] } }]), /第 1 题.*标准答案/);
  assert.throws(() => parse([{ ...single, type: "multiple_choice" }]), /选项数量/);
});
test("validates grading, scores, tags and required answers before submitting", () => {
  for (const patch of [
    { gradingRule: { strategy: "manual" } }, { suggestedScore: 0 }, { suggestedScore: 1.001 },
    { tags: ["重复", "重复"] }, { stem: " " }, { type: "true_false", options: [], standardAnswer: { value: null } },
    { type: "short_answer", options: [], standardAnswer: { text: " " }, gradingRule: { strategy: "manual" } },
  ]) assert.throws(() => parse([{ ...single, ...patch }]), /第 1 题/);
});
test("rejects malformed or oversized batches and supports UTF-8 BOM", () => {
  assert.throws(() => parse([]), /1–1000/);
  assert.throws(() => parse(Array(1001).fill(single)), /1–1000/);
  assert.throws(() => parseImport("{}", 2, []), /JSON 数组/);
  assert.throws(() => parseImport("not json", 2, []));
  assert.equal(parseImport("\uFEFF" + JSON.stringify([single]), 2, []).length, 1);
});
