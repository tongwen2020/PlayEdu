import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import ts from "typescript";

const source = readFileSync(new URL("../src/pages/exam-paper/import-format.ts", import.meta.url), "utf8");
const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ESNext, module: ts.ModuleKind.ESNext } });
const { parsePaperImport } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString("base64")}`);
const paper = { id: 9, revision: 4, currentVersion: 2, code: "P1", name: "试卷一", description: "", categoryId: null, tags: [], sections: [{ id: 8, title: "单选题", description: "", position: 9, shuffleQuestions: false, items: [{ id: 7, questionId: 11, questionVersion: 2, score: 5, position: 8, question: { stem: "快照" } }] }] };

test("accepts single export and strips paper, section, item and question snapshot identifiers", () => {
  const [value] = parsePaperImport(JSON.stringify(paper), []);
  assert.equal(value.id, undefined); assert.equal(value.revision, undefined); assert.equal(value.sections[0].items[0].question, undefined);
  assert.deepEqual(value.sections[0].items[0], { questionId: 11, questionVersion: 2, score: 5, position: 0 });
});
test("accepts arrays, envelopes and UTF-8 BOM", () => {
  assert.equal(parsePaperImport(JSON.stringify([paper]), []).length, 1);
  assert.equal(parsePaperImport("\uFEFF" + JSON.stringify({ papers: [paper] }), []).length, 1);
});
test("checks category and duplicate codes or questions before submission", () => {
  assert.throws(() => parsePaperImport(JSON.stringify({ ...paper, categoryId: 4 }), []), /分类/);
  assert.equal(parsePaperImport(JSON.stringify({ ...paper, categoryId: 4 }), [{ id: 4 }])[0].categoryId, 4);
  assert.throws(() => parsePaperImport(JSON.stringify([paper, paper]), []), /编码重复/);
  assert.throws(() => parsePaperImport(JSON.stringify({ ...paper, sections: [{ ...paper.sections[0], items: [paper.sections[0].items[0], paper.sections[0].items[0]] }] }), []), /重复/);
});
test("rejects invalid scores, references and oversized batches", () => {
  const item = paper.sections[0].items[0];
  for (const patch of [{ score: 0 }, { score: 1.001 }, { questionVersion: 0 }, { questionId: -1 }]) assert.throws(() => parsePaperImport(JSON.stringify({ ...paper, sections: [{ ...paper.sections[0], items: [{ ...item, ...patch }] }] }), []), /第 1 份试卷/);
  assert.throws(() => parsePaperImport(JSON.stringify([]), []), /1–100/);
  assert.throws(() => parsePaperImport(JSON.stringify(Array(101).fill(paper)), []), /1–100/);
});
