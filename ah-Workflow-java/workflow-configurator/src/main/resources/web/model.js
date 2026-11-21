/*
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *
 * WorkflowConfigurator editor model, without DOM access (unit-tested with Node, src/test/js).
 *
 * The editor document is a diagram of the recipe:
 *   { meta: {name, version, description, dispatch}, parameters: [{name, type, required}], resources: [...],
 *     actions: [{name, x, y}], steps: [{name, action, service: {serviceDefinition, ...}, provider, executor, params,
 *     input, dep, policy, x, y}], transitions: [{from, to, guard}] }
 * Transitions connect steps of one action (next_alpha); the action order is next_A. A transition may carry a guard
 * (extension x-guard of the WorkflowManager). The recipe JSON (model/recipe.schema.json) is derived with toRecipe and
 * read back with fromRecipe; positions and provider hints are diagram-only and kept by the browser.
 */
(function (root) {
  "use strict";

  const IDENT = /^[A-Za-z][A-Za-z0-9_-]{0,62}$/;
  const VERSION = /^\d+\.\d+\.\d+$/;
  const PLACEHOLDER = /\$\{(param|result|params)\.([A-Za-z][A-Za-z0-9_-]*)((?:\.[A-Za-z0-9_-]+)*)\}/g;
  const STEP_W = 180, STEP_H = 62, COL_GAP = 130, ROW_GAP = 34, FRAME_PAD = 34, FRAME_GAP = 90, ORIGIN_X = 120, ORIGIN_Y = 70;

  // ------------------------------------------------------------------------------------------- document basics

  function emptyDocument() {
    return {
      meta: { name: "new-workflow", version: "1.0.0", description: "", dispatch: null },
      parameters: [],
      resources: [],
      actions: [{ name: "main", x: ORIGIN_X, y: ORIGIN_Y }],
      steps: [],
      transitions: []
    };
  }

  function clone(v) {
    return JSON.parse(JSON.stringify(v));
  }

  function step(doc, name) {
    return doc.steps.find(s => s.name === name);
  }

  function stepsOf(doc, action) {
    return doc.steps.filter(s => s.action === action);
  }

  function successors(doc, name) {
    return doc.transitions.filter(t => t.from === name).map(t => t.to);
  }

  function predecessors(doc, name) {
    return doc.transitions.filter(t => t.to === name).map(t => t.from);
  }

  /** A free step name from a service definition: temperature-read gives temperatureRead, then temperatureRead2, ... */
  function uniqueName(doc, base) {
    let stem = String(base || "step").replace(/[^A-Za-z0-9_-]+/g, "-").replace(/-([a-zA-Z0-9])/g, (m, c) => c.toUpperCase()).replace(/^[^A-Za-z]+/, "");
    if (!stem) stem = "step";
    stem = stem.charAt(0).toLowerCase() + stem.slice(1);
    stem = stem.slice(0, 56);
    const taken = new Set(doc.steps.map(s => s.name).concat(doc.actions.map(a => a.name)));
    if (!taken.has(stem)) return stem;
    for (let i = 2; ; i++) {
      if (!taken.has(stem + i)) return stem + i;
    }
  }

  function uniqueActionName(doc, base) {
    const taken = new Set(doc.actions.map(a => a.name).concat(doc.steps.map(s => s.name)));
    const stem = base || "action";
    if (!taken.has(stem)) return stem;
    for (let i = 2; ; i++) {
      if (!taken.has(stem + i)) return stem + i;
    }
  }

  // ------------------------------------------------------------------------------------------- references

  /** ${param.x} and ${result.step} references in a JSON template (κ_s). */
  function templateReferences(template) {
    const out = { steps: new Set(), params: new Set() };
    (function walk(node) {
      if (node == null) return;
      if (typeof node === "string") {
        for (const m of node.matchAll(PLACEHOLDER)) {
          if (m[1] === "result") out.steps.add(m[2]);
          if (m[1] === "param") out.params.add(m[2]);
        }
      } else if (Array.isArray(node)) {
        node.forEach(walk);
      } else if (typeof node === "object") {
        Object.values(node).forEach(walk);
      }
    })(template);
    return out;
  }

  /** Transitive predecessors of a step: steps of the same action before it, and every step of earlier actions (p ≺ s). */
  function precedingSteps(doc, name) {
    const s = step(doc, name);
    if (!s) return [];
    const order = doc.actions.map(a => a.name);
    const idx = order.indexOf(s.action);
    const out = new Set(doc.steps.filter(x => order.indexOf(x.action) < idx).map(x => x.name));
    const work = predecessors(doc, name);
    while (work.length) {
      const p = work.pop();
      if (!out.has(p)) {
        out.add(p);
        work.push(...predecessors(doc, p));
      }
    }
    return [...out];
  }

  /** Renames a step everywhere: transitions, dep, guards and input templates. */
  function renameStep(doc, from, to) {
    const s = step(doc, from);
    if (!s || from === to) return;
    s.name = to;
    const re = new RegExp("\\$\\{result\\." + from.replace(/[-]/g, "\\-") + "(?=[.}])", "g");
    const fix = v => typeof v === "string" ? v.replace(re, "${result." + to) : v;
    const deep = node => {
      if (Array.isArray(node)) return node.map(deep);
      if (node && typeof node === "object") {
        const o = {};
        for (const [k, v] of Object.entries(node)) o[k] = deep(v);
        return o;
      }
      return fix(node);
    };
    for (const t of doc.transitions) {
      if (t.from === from) t.from = to;
      if (t.to === from) t.to = to;
      if (t.guard) t.guard = fix(t.guard);
    }
    for (const x of doc.steps) {
      x.dep = (x.dep || []).map(d => d === from ? to : d);
      if (x.input != null) x.input = deep(x.input);
    }
  }

  function removeStep(doc, name) {
    doc.steps = doc.steps.filter(s => s.name !== name);
    doc.transitions = doc.transitions.filter(t => t.from !== name && t.to !== name);
    for (const s of doc.steps) s.dep = (s.dep || []).filter(d => d !== name);
  }

  function removeAction(doc, name) {
    if (doc.actions.length <= 1) return false;
    for (const s of stepsOf(doc, name)) removeStep(doc, s.name);
    doc.actions = doc.actions.filter(a => a.name !== name);
    return true;
  }

  function renameAction(doc, from, to) {
    const a = doc.actions.find(x => x.name === from);
    if (!a) return;
    a.name = to;
    for (const s of doc.steps) if (s.action === from) s.action = to;
  }

  /** Adds s -> t when both are in one action and no cycle results; returns an error text or null. */
  function connect(doc, from, to, guard) {
    const a = step(doc, from), b = step(doc, to);
    if (!a || !b) return "unknown step";
    if (from === to) return "a step cannot follow itself";
    if (a.action !== b.action) return "transitions connect steps of the same action; the order of actions is set in the action list";
    if (doc.transitions.some(t => t.from === from && t.to === to)) return "the transition exists already";
    if (reaches(doc, to, from)) return "this transition would make a cycle (recipes are acyclic)";
    doc.transitions.push(guard ? { from, to, guard } : { from, to });
    return null;
  }

  function reaches(doc, from, to) {
    const seen = new Set();
    const work = [from];
    while (work.length) {
      const n = work.pop();
      if (n === to) return true;
      if (seen.has(n)) continue;
      seen.add(n);
      work.push(...successors(doc, n));
    }
    return false;
  }

  /** Steps of an action grouped by longest-path depth from its first steps (Kahn layering). */
  function levels(doc, action) {
    const names = stepsOf(doc, action).map(s => s.name);
    const inAction = new Set(names);
    const depth = {};
    const indeg = {};
    names.forEach(n => { indeg[n] = predecessors(doc, n).filter(p => inAction.has(p)).length; depth[n] = 0; });
    const queue = names.filter(n => indeg[n] === 0);
    const order = [];
    while (queue.length) {
      const n = queue.shift();
      order.push(n);
      for (const m of successors(doc, n).filter(x => inAction.has(x))) {
        depth[m] = Math.max(depth[m], depth[n] + 1);
        if (--indeg[m] === 0) queue.push(m);
      }
    }
    names.filter(n => !order.includes(n)).forEach(n => order.push(n)); // cycles: keep them visible
    const out = [];
    for (const n of order) {
      (out[depth[n]] = out[depth[n]] || []).push(n);
    }
    return out.filter(Boolean);
  }

  // ------------------------------------------------------------------------------------------- layout

  /** Places actions left to right, steps in columns by depth inside their action frame. */
  function autoLayout(doc) {
    let x = ORIGIN_X;
    for (const a of doc.actions) {
      const lv = levels(doc, a.name);
      a.x = x;
      a.y = ORIGIN_Y;
      lv.forEach((names, col) => {
        names.forEach((n, row) => {
          const s = step(doc, n);
          s.x = x + FRAME_PAD + col * (STEP_W + COL_GAP);
          s.y = ORIGIN_Y + FRAME_PAD + 20 + row * (STEP_H + ROW_GAP);
        });
      });
      const cols = Math.max(1, lv.length);
      x += FRAME_PAD * 2 + cols * STEP_W + (cols - 1) * COL_GAP + FRAME_GAP;
    }
    return doc;
  }

  /** Bounding frame of an action: its steps plus padding, or a placeholder at its own position. */
  function frame(doc, action) {
    const a = doc.actions.find(x => x.name === action);
    const ss = stepsOf(doc, action);
    if (!ss.length) {
      return { x: a.x, y: a.y, w: STEP_W + FRAME_PAD * 2, h: STEP_H + FRAME_PAD * 2 + 20, empty: true };
    }
    const minX = Math.min(...ss.map(s => s.x)) - FRAME_PAD;
    const minY = Math.min(...ss.map(s => s.y)) - FRAME_PAD - 20;
    const maxX = Math.max(...ss.map(s => s.x + STEP_W)) + FRAME_PAD;
    const maxY = Math.max(...ss.map(s => s.y + STEP_H)) + FRAME_PAD;
    return { x: minX, y: minY, w: maxX - minX, h: maxY - minY, empty: false };
  }

  // ------------------------------------------------------------------------------------------- recipe conversion

  function toRecipe(doc) {
    const recipe = { name: doc.meta.name, version: doc.meta.version };
    if (doc.meta.description) recipe.description = doc.meta.description;
    recipe.firstAction = doc.actions.length ? doc.actions[0].name : null;
    if (doc.parameters.length) {
      recipe.parameters = doc.parameters.map(p => {
        const o = { name: p.name, type: p.type || "string" };
        if (p.required === false) o.required = false;
        return o;
      });
    }
    if (doc.resources && doc.resources.length) recipe.resources = clone(doc.resources);
    if (doc.meta.dispatch) recipe.dispatch = doc.meta.dispatch;
    recipe.actions = doc.actions.map((a, i) => {
      const action = { name: a.name, next: i + 1 < doc.actions.length ? doc.actions[i + 1].name : null };
      if (a.workCentre) action.workCentre = a.workCentre;
      if (a.dispatch) action.dispatch = a.dispatch;
      // recipe order: topological inside the action, so readers see the flow
      const ordered = [].concat(...levels(doc, a.name));
      action.steps = ordered.map(n => stepToRecipe(doc, step(doc, n)));
      return action;
    });
    return recipe;
  }

  function stepToRecipe(doc, s) {
    const out = { name: s.name, service: Object.assign({}, s.service) };
    if (!out.service.serviceDefinition) out.service.serviceDefinition = "";
    for (const k of Object.keys(out.service)) {
      const v = out.service[k];
      if (v == null || (Array.isArray(v) && !v.length) || (typeof v === "object" && !Array.isArray(v) && !Object.keys(v).length)) delete out.service[k];
    }
    out.service.serviceDefinition = out.service.serviceDefinition || "";
    const next = doc.transitions.filter(t => t.from === s.name);
    if (next.length) out.next = next.map(t => t.to);
    // dep(s): declared, plus the results the input template reads (the WorkflowManager requires them in dep)
    const dep = new Set(s.dep || []);
    if (s.input != null) templateReferences(s.input).steps.forEach(n => dep.add(n));
    if (dep.size) out.dep = [...dep];
    if (s.executor && s.executor.capability) {
      out.executor = { capability: s.executor.capability };
      if (s.executor.workstation) out.executor.workstation = s.executor.workstation;
      if (s.executor.system) out.executor.system = s.executor.system;
    }
    if (s.params && Object.keys(s.params).length) out.params = clone(s.params);
    if (s.policy && Object.keys(s.policy).length) out.policy = clone(s.policy);
    if (s.input != null) out.input = clone(s.input);
    const guards = {};
    next.filter(t => t.guard && t.guard.trim()).forEach(t => { guards[t.to] = t.guard.trim(); });
    if (Object.keys(guards).length) out.guards = guards;
    return out;
  }

  /** Reads a recipe; {@code layout} = {steps: {name: {x, y, provider}}, actions: {name: {x, y}}} from an earlier session. */
  function fromRecipe(recipe, layout) {
    const doc = {
      meta: { name: recipe.name || "workflow", version: recipe.version || "1.0.0", description: recipe.description || "", dispatch: recipe.dispatch || null },
      parameters: (recipe.parameters || []).map(p => ({ name: p.name, type: p.type || "string", required: p.required !== false })),
      resources: clone(recipe.resources || []),
      actions: [],
      steps: [],
      transitions: []
    };
    // action order follows next_A from firstAction; unreachable actions are appended
    const byName = {};
    (recipe.actions || []).forEach(a => { byName[a.name] = a; });
    const order = [];
    for (let a = recipe.firstAction; a && byName[a] && !order.includes(a); a = byName[a].next) order.push(a);
    (recipe.actions || []).forEach(a => { if (!order.includes(a.name)) order.push(a.name); });
    for (const name of order) {
      const a = byName[name];
      const entry = { name, x: 0, y: 0 };
      if (a.workCentre) entry.workCentre = a.workCentre;
      if (a.dispatch) entry.dispatch = a.dispatch;
      doc.actions.push(entry);
      for (const s of a.steps || []) {
        const inputRefs = s.input != null ? templateReferences(s.input).steps : new Set();
        doc.steps.push({
          name: s.name,
          action: name,
          service: clone(s.service || { serviceDefinition: "" }),
          provider: null,
          executor: s.executor ? clone(s.executor) : null,
          params: clone(s.params || {}),
          input: s.input === undefined ? null : clone(s.input),
          dep: (s.dep || []).filter(d => !inputRefs.has(d)),
          policy: clone(s.policy || {}),
          x: 0, y: 0
        });
        for (const n of s.next || []) {
          const g = s.guards && s.guards[n];
          doc.transitions.push(g ? { from: s.name, to: n, guard: g } : { from: s.name, to: n });
        }
      }
    }
    autoLayout(doc);
    if (layout) applyLayout(doc, layout);
    return doc;
  }

  function layoutOf(doc) {
    const out = { steps: {}, actions: {} };
    doc.steps.forEach(s => { out.steps[s.name] = { x: s.x, y: s.y, provider: s.provider || null }; });
    doc.actions.forEach(a => { out.actions[a.name] = { x: a.x, y: a.y }; });
    return out;
  }

  function applyLayout(doc, layout) {
    for (const s of doc.steps) {
      const p = layout.steps && layout.steps[s.name];
      if (p && isFinite(p.x) && isFinite(p.y)) { s.x = p.x; s.y = p.y; }
      if (p && p.provider) s.provider = p.provider;
    }
    for (const a of doc.actions) {
      const p = layout.actions && layout.actions[a.name];
      if (p && isFinite(p.x) && isFinite(p.y)) { a.x = p.x; a.y = p.y; }
    }
    return doc;
  }

  // ------------------------------------------------------------------------------------------- local checks

  /** Quick checks while drawing; the WorkflowManager's validator is the authority (Validate / Save). */
  function localIssues(doc) {
    const issues = [];
    const add = (where, message) => issues.push({ where, message });
    if (!IDENT.test(doc.meta.name || "")) add("recipe", "Name must start with a letter: letters, digits, - and _ (up to 63)");
    if (!VERSION.test(doc.meta.version || "")) add("recipe", "Version must be MAJOR.MINOR.PATCH");
    if (!doc.actions.length) add("recipe", "Add at least one action");
    const names = new Set();
    for (const a of doc.actions) {
      if (!IDENT.test(a.name)) add("action " + a.name, "Action name is not an identifier");
      if (names.has(a.name)) add("action " + a.name, "Duplicate name");
      names.add(a.name);
      if (!stepsOf(doc, a.name).length) add("action " + a.name, "Action has no steps: drag a service into it");
    }
    for (const s of doc.steps) {
      if (!IDENT.test(s.name)) add("step " + s.name, "Step name is not an identifier");
      if (names.has(s.name)) add("step " + s.name, "Duplicate name");
      names.add(s.name);
      if (!s.service || !s.service.serviceDefinition) add("step " + s.name, "Choose a service");
      const preceding = new Set(precedingSteps(doc, s.name));
      if (s.input != null) {
        templateReferences(s.input).steps.forEach(r => {
          if (!preceding.has(r)) add("step " + s.name, "Input reads the result of '" + r + "', which does not run before this step");
        });
        templateReferences(s.input).params.forEach(p => {
          if (!doc.parameters.some(x => x.name === p)) add("step " + s.name, "Input reads undeclared parameter '" + p + "'");
        });
      }
    }
    for (const t of doc.transitions) {
      if (!t.guard) continue;
      const where = "transition " + t.from + " → " + t.to;
      const r = checkGuard(t.guard);
      if (r.error) {
        add(where, "Condition: " + r.error);
        continue;
      }
      const allowed = new Set(precedingSteps(doc, t.from).concat([t.from]));
      r.steps.forEach(n => { if (!allowed.has(n)) add(where, "Condition reads '" + n + "', whose result does not exist yet"); });
      r.params.forEach(p => { if (!doc.parameters.some(x => x.name === p)) add(where, "Condition reads undeclared parameter '" + p + "'"); });
    }
    return issues;
  }

  // ------------------------------------------------------------------------------------------- guard syntax

  /**
   * Syntax check of a guard (the grammar of GuardExpression in workflow-model): {error} or {steps, params}.
   * Evaluation is the WorkflowManager's.
   */
  function checkGuard(src) {
    const steps = new Set(), params = new Set();
    if (!src || !src.trim()) return { error: "empty condition" };
    if (src.length > 2000) return { error: "longer than 2000 characters" };
    const tokens = [];
    let i = 0;
    try {
      while (i < src.length) {
        const c = src[i];
        if (/\s/.test(c)) { i++; continue; }
        if (c === "$" && src[i + 1] === "{") {
          const end = src.indexOf("}", i);
          if (end < 0) throw new Error("unterminated reference at " + i);
          const ref = src.slice(i + 2, end).trim();
          const parts = ref.split(".");
          if (parts.length < 2 || !["param", "result"].includes(parts[0]) || !IDENT.test(parts[1])) {
            throw new Error("${" + ref + "} must be ${param.<name>…} or ${result.<step>…}");
          }
          (parts[0] === "param" ? params : steps).add(parts[1]);
          tokens.push({ k: "operand", p: i });
          i = end + 1;
        } else if (c === "'" || c === '"') {
          let j = i + 1;
          while (j < src.length && src[j] !== c) { if (src[j] === "\\") j++; j++; }
          if (j >= src.length) throw new Error("unterminated string at " + i);
          tokens.push({ k: "operand", p: i });
          i = j + 1;
        } else if (/[0-9]/.test(c) || (c === "-" && /[0-9]/.test(src[i + 1] || "") && (!tokens.length || ["op", "("].includes(tokens[tokens.length - 1].k)))) {
          const m = /^-?[0-9][0-9.eE+-]*/.exec(src.slice(i));
          if (!/^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(m[0])) throw new Error("bad number '" + m[0] + "' at " + i);
          tokens.push({ k: "operand", p: i });
          i += m[0].length;
        } else if (/[A-Za-z]/.test(c)) {
          const m = /^[A-Za-z]+/.exec(src.slice(i));
          if (!["true", "false", "null"].includes(m[0])) throw new Error("unknown word '" + m[0] + "' at " + i + " (use ${param.x} or ${result.step.x})");
          tokens.push({ k: "operand", p: i });
          i += m[0].length;
        } else if (c === "(" || c === ")") {
          tokens.push({ k: c, p: i++ });
        } else {
          const two = src.slice(i, i + 2);
          if (["&&", "||", "==", "!=", "<=", ">="].includes(two)) { tokens.push({ k: "op", v: two, p: i }); i += 2; }
          else if ("<>!".includes(c)) { tokens.push({ k: "op", v: c, p: i++ }); }
          else throw new Error("unexpected character '" + c + "' at " + i);
        }
      }
      // recursive descent over the token kinds
      let at = 0;
      const peek = () => tokens[at];
      const isOp = (...v) => peek() && peek().k === "op" && v.includes(peek().v);
      const operand = () => {
        const t = tokens[at++];
        if (!t) throw new Error("condition ends where an operand is expected");
        if (t.k === "(") { expr(); if (!peek() || peek().k !== ")") throw new Error("missing ')' for '(' at " + t.p); at++; return; }
        if (t.k !== "operand") throw new Error("unexpected '" + (t.v || t.k) + "' at " + t.p);
      };
      const compare = () => { operand(); if (isOp("==", "!=", "<", "<=", ">", ">=")) { at++; operand(); } };
      const unary = () => { if (isOp("!")) { at++; unary(); } else compare(); };
      const and = () => { unary(); while (isOp("&&")) { at++; unary(); } };
      const expr = () => { and(); while (isOp("||")) { at++; and(); } };
      expr();
      if (at < tokens.length) throw new Error("unexpected '" + (tokens[at].v || tokens[at].k) + "' at " + tokens[at].p);
    } catch (e) {
      return { error: e.message };
    }
    return { steps: [...steps], params: [...params] };
  }

  // ------------------------------------------------------------------------------------------- sequence view

  /**
   * Rows of the sequence diagram: for every action a band, then per depth level either one message, a par fragment of
   * parallel messages, an alt fragment (guarded alternatives that leave one step) or opt fragments (other guarded steps).
   * Lifelines: the WorkflowManager, one per executor capability, and one per provider (hint) or service definition.
   */
  function sequence(doc) {
    const lifelines = [{ id: "wfm", label: "WorkflowManager", kind: "manager" }];
    const ensure = (id, label, kind) => {
      if (!lifelines.some(l => l.id === id)) lifelines.push({ id, label, kind });
      return id;
    };
    const caller = s => s.executor && s.executor.capability ? ensure("cap:" + s.executor.capability, "executor [" + s.executor.capability + "]", "executor") : "wfm";
    const callee = s => s.provider ? ensure("sys:" + s.provider, s.provider, "provider")
      : ensure("sd:" + (s.service.serviceDefinition || "?"), "«" + (s.service.serviceDefinition || "service?") + "»", "service");
    // callers first so executors sit next to the manager
    doc.steps.forEach(caller);
    doc.steps.forEach(callee);
    const message = name => {
      const s = step(doc, name);
      return { step: name, from: caller(s), to: callee(s), label: name + ": " + (s.service.serviceDefinition || "?") + "()" };
    };
    const guardOf = name => {
      const incoming = doc.transitions.filter(t => t.to === name);
      const guarded = incoming.filter(t => t.guard);
      if (!guarded.length || guarded.length < incoming.length) return null; // an unguarded way in: always runs
      return guarded.map(t => t.guard).join("  or  ");
    };
    const rows = [];
    for (const a of doc.actions) {
      rows.push({ kind: "action", name: a.name });
      for (const level of levels(doc, a.name)) {
        const plain = level.filter(n => !guardOf(n));
        const guarded = level.filter(n => guardOf(n));
        // alternatives: guarded steps whose only predecessor is the same step
        const bySource = {};
        guarded.forEach(n => {
          const pre = predecessors(doc, n);
          const key = pre.length === 1 ? pre[0] : null;
          (bySource[key] = bySource[key] || []).push(n);
        });
        if (plain.length === 1) rows.push(Object.assign({ kind: "message" }, message(plain[0])));
        else if (plain.length > 1) rows.push({ kind: "par", operands: plain.map(n => ({ guard: null, messages: [message(n)] })) });
        for (const [source, names] of Object.entries(bySource)) {
          if (source !== "null" && names.length > 1) {
            rows.push({ kind: "alt", operands: names.map(n => ({ guard: guardOf(n), messages: [message(n)] })) });
          } else {
            names.forEach(n => rows.push({ kind: "opt", operands: [{ guard: guardOf(n), messages: [message(n)] }] }));
          }
        }
      }
    }
    return { lifelines, rows };
  }

  /** Appends a step after {@code after} (or after the sinks of the last action): drawing in the sequence view. */
  function appendAfter(doc, after, newStep) {
    const target = after ? step(doc, after) : null;
    const action = target ? target.action : doc.actions[doc.actions.length - 1].name;
    newStep.action = action;
    const sinks = target ? [target.name] : stepsOf(doc, action).filter(s => !successors(doc, s.name).length).map(s => s.name);
    const ref = target || step(doc, sinks[0]);
    newStep.x = ref ? ref.x + STEP_W + COL_GAP : (doc.actions.find(a => a.name === action).x + FRAME_PAD);
    newStep.y = ref ? ref.y : ORIGIN_Y + FRAME_PAD + 20;
    while (doc.steps.some(s => Math.abs(s.x - newStep.x) < 20 && Math.abs(s.y - newStep.y) < 20)) newStep.y += STEP_H + ROW_GAP;
    doc.steps.push(newStep);
    sinks.forEach(n => connect(doc, n, newStep.name));
    return newStep;
  }

  const api = {
    STEP_W, STEP_H, FRAME_PAD, IDENT, VERSION,
    emptyDocument, clone, step, stepsOf, successors, predecessors, uniqueName, uniqueActionName,
    templateReferences, precedingSteps, renameStep, removeStep, removeAction, renameAction, connect, reaches, levels,
    autoLayout, frame, toRecipe, fromRecipe, layoutOf, applyLayout, localIssues, checkGuard, sequence, appendAfter
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.WfModel = api;
})(typeof self !== "undefined" ? self : this);
