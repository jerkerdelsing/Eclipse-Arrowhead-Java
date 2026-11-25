// Unit tests of the editor model (no browser): node --test workflow-configurator/src/test/js/model.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const M = require("../../main/resources/web/model.js");
const example = name => JSON.parse(readFileSync(new URL("../../../../examples/" + name, import.meta.url)));

/** Same recipe up to key order and defaults the editor drops (empty dep/next). */
function normalize(recipe) {
  const r = structuredClone(recipe);
  for (const a of r.actions) {
    a.next = a.next ?? null;
    a.steps.sort((x, y) => x.name.localeCompare(y.name));
    for (const s of a.steps) {
      if (s.next && !s.next.length) delete s.next;
      if (s.dep && !s.dep.length) delete s.dep;
      if (s.dep) s.dep.sort();
      if (s.next) s.next.sort();
    }
  }
  return JSON.parse(JSON.stringify(r, Object.keys(flatKeys(r)).sort()));
}
function flatKeys(o, acc = {}) {
  if (o && typeof o === "object") for (const [k, v] of Object.entries(o)) { acc[k] = 1; flatKeys(v, acc); }
  return acc;
}

for (const file of ["temperature-control.recipe.json", "temperature-branching.recipe.json", "shared-tool-tightening.recipe.json"]) {
  test("round trip " + file, () => {
    const recipe = example(file);
    const doc = M.fromRecipe(recipe);
    assert.deepEqual(normalize(M.toRecipe(doc)), normalize(recipe));
    assert.deepEqual(M.localIssues(doc), [], "no local issues for a valid example");
  });
}

test("drawing a guarded branch produces the guards and dep", () => {
  const doc = M.emptyDocument();
  doc.meta.name = "drawn";
  doc.parameters.push({ name: "threshold", type: "number", required: true });
  const add = (sd, name) => { const s = { name, action: "main", service: { serviceDefinition: sd }, provider: "WfEquipment", executor: null, params: {}, input: null, dep: [], policy: {}, x: 0, y: 0 }; doc.steps.push(s); return s; };
  add("temperature-read", "read");
  const ev = add("temperature-evaluate", "evaluate");
  ev.input = { temperature: "${result.read.temperature}", threshold: "${param.threshold}" };
  add("heating-switch", "on");
  add("heating-switch", "off");
  assert.equal(M.connect(doc, "read", "evaluate"), null);
  assert.equal(M.connect(doc, "evaluate", "on", "${result.evaluate.heat} == true"), null);
  assert.equal(M.connect(doc, "evaluate", "off", "!${result.evaluate.heat}"), null);
  assert.match(M.connect(doc, "on", "read"), /cycle/);
  assert.match(M.connect(doc, "evaluate", "on"), /exists/);
  const r = M.toRecipe(doc);
  const evaluate = r.actions[0].steps.find(s => s.name === "evaluate");
  assert.deepEqual(evaluate.dep, ["read"], "dep inferred from the input template");
  assert.deepEqual(evaluate.guards, { on: "${result.evaluate.heat} == true", off: "!${result.evaluate.heat}" });
  assert.deepEqual(r.actions[0].steps.map(s => s.name), ["read", "evaluate", "on", "off"], "topological order");
  const seq = M.sequence(doc);
  assert.deepEqual(seq.rows.map(x => x.kind), ["action", "message", "message", "alt"]);
  assert.equal(seq.rows[3].operands.length, 2);
  assert.deepEqual(seq.lifelines.map(l => l.label), ["WorkflowManager", "WfEquipment"]);
});

test("guard syntax matches the Java grammar", () => {
  for (const ok of ["${result.read.temperature} < ${param.threshold}", "${param.mode} == 'auto' && !(${param.t} >= 30)",
    "${result.read.tags.0} == \"a\" || false", "${result.x.y} == null", "${result.r.t} > -5", "(true)"]) {
    assert.ok(!M.checkGuard(ok).error, ok + ": " + M.checkGuard(ok).error);
  }
  for (const bad of ["", "${result.read.x} <", "(true", "foo == 1", "${other.x} == 1", "1 = 1", "'open"]) {
    assert.ok(M.checkGuard(bad).error, bad);
  }
  assert.deepEqual(M.checkGuard("${result.a.b} && ${param.p}"), { steps: ["a"], params: ["p"] });
});

test("local issues point at the element", () => {
  const doc = M.fromRecipe(example("temperature-branching.recipe.json"));
  doc.transitions.find(t => t.to === "heatOff").guard = "${result.confirm.x} ==";
  const issues = M.localIssues(doc);
  assert.ok(issues.some(i => i.where === "transition evaluate → heatOff" && /Condition/.test(i.message)));
});

test("rename updates transitions, dep, input and guards", () => {
  const doc = M.fromRecipe(example("temperature-branching.recipe.json"));
  M.renameStep(doc, "evaluate", "decide");
  const r = M.toRecipe(doc);
  const steps = Object.fromEntries(r.actions[0].steps.map(s => [s.name, s]));
  assert.deepEqual(steps.read.next, ["decide"]);
  assert.deepEqual(steps.decide.guards, { heatOn: "${result.decide.heat} == true", heatOff: "!${result.decide.heat}" });
  assert.equal(steps.decide.input.temperature, "${result.read.temperature}");
});

test("appendAfter connects the new step after the selection", () => {
  const doc = M.fromRecipe(example("temperature-control.recipe.json"));
  M.appendAfter(doc, "switch", { name: "log", service: { serviceDefinition: "log" }, params: {}, input: null, dep: [], policy: {} });
  assert.ok(doc.transitions.some(t => t.from === "switch" && t.to === "log"));
  assert.equal(M.step(doc, "log").action, "control");
});

test("layout survives a reload", () => {
  const doc = M.fromRecipe(example("temperature-control.recipe.json"));
  M.step(doc, "read").x = 999;
  M.step(doc, "read").provider = "WfEquipment";
  const again = M.fromRecipe(M.toRecipe(doc), M.layoutOf(doc));
  assert.equal(M.step(again, "read").x, 999);
  assert.equal(M.step(again, "read").provider, "WfEquipment");
});

const box = s => ({ x: s.x, y: s.y, w: M.STEP_W, h: M.STEP_H });
const hit = (r, q) => r.x < q.x + q.w && q.x < r.x + r.w && r.y < q.y + q.h && q.y < r.y + r.h;
const labelRects = doc => Object.values(M.labelLayout(doc)).map(l => ({ x: l.x - l.w / 2, y: l.y - l.h / 2, w: l.w, h: l.h }));

test("after Arrange, condition labels cover no step and no other label", () => {
  // fan-out with long conditions, a join, and a transition that skips a column
  const doc = M.emptyDocument();
  const action = doc.actions[0].name;
  for (const n of ["read", "decide", "a", "b", "c", "join"]) doc.steps.push({ name: n, action, service: { serviceDefinition: "svc-" + n }, params: {}, input: null, dep: [], policy: {}, x: 0, y: 0 });
  const t = (from, to, guard) => doc.transitions.push(guard ? { from, to, guard } : { from, to });
  t("read", "decide");
  t("decide", "a", "${result.decide.temperature} < ${param.lowThreshold}");
  t("decide", "b", "${result.decide.temperature} >= ${param.lowThreshold} && ${result.decide.temperature} < 30");
  t("decide", "c", "${result.decide.temperature} >= 30");
  t("a", "join"); t("b", "join"); t("c", "join");
  t("read", "join", "${result.read.ok} == false");
  M.autoLayout(doc);
  const labels = labelRects(doc);
  assert.equal(labels.length, 4);
  for (const l of labels) for (const s of doc.steps) assert.ok(!hit(l, box(s)), "label over step " + s.name);
  for (let i = 0; i < labels.length; i++) for (let j = i + 1; j < labels.length; j++) assert.ok(!hit(labels[i], labels[j]), "labels overlap");
});

test("the branching example keeps its labels clear after Arrange", () => {
  const doc = M.autoLayout(M.fromRecipe(example("temperature-branching.recipe.json")));
  for (const l of labelRects(doc)) for (const s of doc.steps) assert.ok(!hit(l, box(s)), "label over step " + s.name);
});

test("every validator code has help", () => {
  const dir = new URL("../../../../workflow-model/src/main/java/eu/arrowhead/workflow/model/validation/", import.meta.url);
  const wfm = new URL("../../../../workflow-manager/src/main/java/eu/arrowhead/workflowmanager/service/CatalogueCheck.java", import.meta.url);
  const src = readFileSync(new URL("RecipeValidator.java", dir), "utf8") + readFileSync(wfm, "utf8");
  const codes = new Set([...src.matchAll(/new Finding\("([A-Z]\d\d)"/g)].map(m => m[1]));
  assert.ok(codes.size >= 25);
  for (const c of codes) assert.ok(M.FINDING_HELP[c] && M.FINDING_HELP[c].fix, "help for " + c);
  for (const i of M.localIssues(M.emptyDocument())) assert.ok(M.FINDING_HELP[i.code], "drawing check " + i.message + " has a code with help");
});
