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

  /** The label shown on a guarded transition, and its width in canvas units (the canvas font is ~6.4 units per character). */
  function guardLabel(guard) {
    const g = String(guard || "");
    return "[" + (g.length > 30 ? g.slice(0, 29) + "…" : g) + "]";
  }

  function labelWidth(text) {
    return text.length * 6.4 + 10;
  }

  const LABEL_H = 19, LABEL_MARGIN = 14;

  /**
   * Places actions left to right, steps in columns by depth inside their action frame. The gap before a column is wide enough
   * for the condition labels of the transitions that end there, since those labels sit just in front of their target step.
   */
  function autoLayout(doc) {
    let x = ORIGIN_X;
    for (const a of doc.actions) {
      const lv = levels(doc, a.name);
      const colOf = {};
      lv.forEach((names, col) => names.forEach(n => { colOf[n] = col; }));
      const gap = lv.map(() => COL_GAP);
      for (const t of doc.transitions) {
        if (!t.guard || colOf[t.to] == null || colOf[t.from] == null || colOf[t.to] <= colOf[t.from]) continue;
        gap[colOf[t.to]] = Math.max(gap[colOf[t.to]], labelWidth(guardLabel(t.guard)) + 2 * LABEL_MARGIN + 20);
      }
      a.x = x;
      a.y = ORIGIN_Y;
      let colX = x + FRAME_PAD;
      lv.forEach((names, col) => {
        if (col > 0) colX += STEP_W + gap[col];
        names.forEach((n, row) => {
          const s = step(doc, n);
          s.x = colX;
          s.y = ORIGIN_Y + FRAME_PAD + 20 + row * (STEP_H + ROW_GAP);
        });
      });
      x = colX + STEP_W + FRAME_PAD + FRAME_GAP;
    }
    return doc;
  }

  /** The drawn curve of a transition: from the right edge of its source to the left edge of its target (cubic Bézier). */
  function edgeGeometry(doc, t) {
    const a = step(doc, t.from), b = step(doc, t.to);
    if (!a || !b) return null;
    const x1 = a.x + STEP_W, y1 = a.y + STEP_H / 2, x2 = b.x, y2 = b.y + STEP_H / 2;
    const back = x2 < x1 + 20;
    let pts;
    if (back) {
      const c = Math.max(60, Math.abs(y2 - y1) / 2), dy = y2 >= y1 ? 70 : -70;
      pts = [[x1, y1], [x1 + c, y1 + dy], [x2 - c, y2 - dy], [x2 - 2, y2]];
    } else {
      const c = Math.max(30, (x2 - x1) / 2);
      pts = [[x1, y1], [x1 + c, y1], [x2 - c, y2], [x2 - 2, y2]];
    }
    const d = "M" + pts[0] + " C" + pts[1] + " " + pts[2] + " " + pts[3];
    return { x1, y1, x2, y2, back, pts, d };
  }

  function bezier(pts, t) {
    const u = 1 - t;
    const f = i => u * u * u * pts[0][i] + 3 * u * u * t * pts[1][i] + 3 * u * t * t * pts[2][i] + t * t * t * pts[3][i];
    return [f(0), f(1)];
  }

  /** y of a forward curve at x (x grows with t on a forward edge). */
  function yAt(pts, x) {
    let lo = 0, hi = 1;
    for (let i = 0; i < 30; i++) {
      const mid = (lo + hi) / 2;
      if (bezier(pts, mid)[0] < x) lo = mid; else hi = mid;
    }
    return bezier(pts, (lo + hi) / 2)[1];
  }

  function overlaps(r, q) {
    return r.x < q.x + q.w && q.x < r.x + r.w && r.y < q.y + q.h && q.y < r.y + r.h;
  }

  /**
   * Positions of the condition labels: on the curve just in front of the target step (where the curves of different targets are
   * apart), moved up or down along the gap when that would cover a step or another label. Keyed "from→to"; x, y are the centre.
   */
  function labelLayout(doc) {
    const out = {};
    const blocked = doc.steps.map(s => ({ x: s.x - 6, y: s.y - 6, w: STEP_W + 12, h: STEP_H + 12 }));
    for (const t of doc.transitions) {
      if (!t.guard) continue;
      const e = edgeGeometry(doc, t);
      if (!e) continue;
      const text = guardLabel(t.guard), w = labelWidth(text);
      let cx, cy;
      if (e.back) {
        [cx, cy] = bezier(e.pts, 0.5);
      } else {
        cx = Math.max((e.x1 + e.x2) / 2, e.x2 - LABEL_MARGIN - w / 2);
        cy = yAt(e.pts, cx);
      }
      let rect = null;
      for (let i = 0; i < 17; i++) {
        const dy = (i % 2 ? 1 : -1) * Math.ceil(i / 2) * (LABEL_H + 4);
        const r = { x: cx - w / 2, y: cy + dy - LABEL_H / 2, w, h: LABEL_H };
        if (!blocked.some(q => overlaps(r, q))) { rect = r; break; }
      }
      rect = rect || { x: cx - w / 2, y: cy - LABEL_H / 2, w, h: LABEL_H };
      blocked.push({ x: rect.x - 3, y: rect.y - 2, w: rect.w + 6, h: rect.h + 4 });
      out[t.from + "→" + t.to] = { text, w, h: LABEL_H, x: rect.x + w / 2, y: rect.y + LABEL_H / 2, anchorY: cy };
    }
    return out;
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
    const add = (where, message, code) => issues.push({ where, message, code });
    if (!IDENT.test(doc.meta.name || "")) add("recipe", "Name must start with a letter: letters, digits, - and _ (up to 63)", "S00");
    if (!VERSION.test(doc.meta.version || "")) add("recipe", "Version must be MAJOR.MINOR.PATCH", "S00");
    if (!doc.actions.length) add("recipe", "Add at least one action", "S08");
    const names = new Set();
    for (const a of doc.actions) {
      if (!IDENT.test(a.name)) add("action " + a.name, "Action name is not an identifier", "S00");
      if (names.has(a.name)) add("action " + a.name, "Duplicate name", "S01");
      names.add(a.name);
      if (!stepsOf(doc, a.name).length) add("action " + a.name, "Action has no steps: drag a service into it", "S08");
    }
    for (const s of doc.steps) {
      if (!IDENT.test(s.name)) add("step " + s.name, "Step name is not an identifier", "S00");
      if (names.has(s.name)) add("step " + s.name, "Duplicate name", "S02");
      names.add(s.name);
      if (!s.service || !s.service.serviceDefinition) add("step " + s.name, "Choose a service", "S09");
      const preceding = new Set(precedingSteps(doc, s.name));
      if (s.input != null) {
        templateReferences(s.input).steps.forEach(r => {
          if (!preceding.has(r)) add("step " + s.name, "Input reads the result of '" + r + "', which does not run before this step", "D03");
        });
        templateReferences(s.input).params.forEach(p => {
          if (!doc.parameters.some(x => x.name === p)) add("step " + s.name, "Input reads undeclared parameter '" + p + "'", "D05");
        });
      }
    }
    for (const t of doc.transitions) {
      if (!t.guard) continue;
      const where = "transition " + t.from + " → " + t.to;
      const r = checkGuard(t.guard);
      if (r.error) {
        add(where, "Condition: " + r.error, "G02");
        continue;
      }
      const allowed = new Set(precedingSteps(doc, t.from).concat([t.from]));
      r.steps.forEach(n => { if (!allowed.has(n)) add(where, "Condition reads '" + n + "', whose result does not exist yet", "G03"); });
      r.params.forEach(p => { if (!doc.parameters.some(x => x.name === p)) add(where, "Condition reads undeclared parameter '" + p + "'", "G05"); });
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

  // ------------------------------------------------------------------------------------------- validation help

  /** What each outcome means for saving. Save stores a workflow only when the WorkflowManager's verdict is ACCEPT. */
  const OUTCOME_HELP = {
    REJECT: "Must be fixed: the WorkflowManager refuses the workflow.",
    REVISE: "Must be fixed before the workflow can be saved.",
    CLARIFY: "Must be resolved before saving: the WorkflowManager cannot confirm this part against the running cloud.",
    ACCEPT: "Information only: nothing to fix.",
    local: "Found while drawing. The WorkflowManager would report the same problem; fix it before you validate or save."
  };

  /** Help per validator code: what it means and how to fix it in this editor. */
  const FINDING_HELP = {
    S00: { title: "Name or version not valid",
      fix: "Names of the workflow, its actions and steps start with a letter and use only letters, digits, - and _ (at most 63). The version is MAJOR.MINOR.PATCH, for example 1.0.0. Click the item in the list to select it and rename it in the side panel; the workflow's name and version are in the Workflow panel (click an empty part of the canvas)." },
    S01: { title: "Two actions have the same name",
      fix: "Select one of the actions (click its frame title) and rename it." },
    S02: { title: "Two steps have the same name",
      fix: "Step names must be unique across the whole workflow, also between actions. Select one of the steps and rename it." },
    S03: { title: "The first action does not exist",
      fix: "The editor normally sets the first action itself. Delete and re-add the first action, or import the recipe again; if the problem stays, check firstAction in the Recipe JSON view." },
    S04: { title: "The next action does not exist",
      fix: "Actions run in the order shown. Select an action and use Run earlier / Run later to reorder; the editor sets each action's next action from that order." },
    S05: { title: "Action cannot be reached, or actions form a loop",
      fix: "Actions run one after the other from the first. Reorder the actions with Run earlier / Run later, or delete the action that is not reached." },
    S06: { title: "Transition to a step in another action",
      fix: "Transitions stay inside one action. Delete the transition, or drag the target step into the same action frame. Work that continues in another action follows the action order instead." },
    S07: { title: "The steps form a loop",
      fix: "A workflow cannot repeat steps. Remove one transition of the loop. To repeat work, start a new session of the workflow." },
    S08: { title: "Workflow or action is empty",
      fix: "Drag a service from the Services panel into the action frame (or double-click it), or delete the empty action." },
    S09: { title: "Step has no service",
      fix: "Select the step and choose a service in the side panel, or drop a service from the Services panel onto the canvas." },
    P01: { title: "Two session parameters have the same name",
      fix: "Open the Workflow panel (click an empty part of the canvas) and rename or remove one of the parameters." },
    D01: { title: "Dependency on an unknown step",
      fix: "The step lists a dependency on a step that does not exist (usually from an imported recipe). Remove it from the step's dep list in the Recipe JSON view and import again, or add a step of that name." },
    D02: { title: "Dependency does not run first",
      fix: "A step may only depend on steps that run before it in the same action, following the transitions. Draw transitions so that the dependency comes first, or stop reading its result in the step's Input." },
    D03: { title: "Input reads a result the step cannot see",
      fix: "An input like ${result.read.temperature} may only read a step that runs before this one in the same action. Draw transitions so that the step it reads comes first, or change the step's Input κ(s). The editor adds the step it reads to the dependencies itself." },
    D04: { title: "Input needs a service result no dependency provides",
      fix: "Read the result of a step that calls the needed service in the step's Input κ(s) (and make that step run first), or change the input." },
    D05: { title: "Input reads an undeclared session parameter",
      fix: "An input like ${param.threshold} needs a parameter of that name. Declare it in the Workflow panel's Parameters, or correct the spelling." },
    G01: { title: "Condition on a transition that does not exist",
      fix: "The condition belongs to a transition that was removed. Select the step and remove the condition, or draw the transition again." },
    G02: { title: "Condition cannot be read",
      fix: "Conditions use ${param.name} and ${result.step.field}, numbers, 'text', true, false, null, the comparisons == != < <= > >=, and && || ! with parentheses. Example: ${result.evaluate.temperature} < 23 && ${param.mode} == 'auto'. Select the transition and correct the condition, or use the condition builder." },
    G03: { title: "Condition reads a result that may not exist yet",
      fix: "A condition on a transition from step A may read the result of A itself or of steps that run before A. Change the condition to read such a step, or move it to a later transition." },
    G04: { title: "Workflow uses conditions (information)",
      fix: "Nothing to fix. A session can skip steps whose conditions are false; such runs are outside the proven model (Theorems 1–2), which the WorkflowManager notes for every workflow with conditions." },
    G05: { title: "Condition reads an undeclared session parameter",
      fix: "Declare the parameter in the Workflow panel's Parameters, or correct the spelling in the condition." },
    L01: { title: "Lease of an undeclared resource",
      fix: "Declare the resource (for example the shared tool) in the recipe's resources in the Recipe JSON view, or remove the lease from the step's Other policy fields." },
    L02: { title: "No reserve step before the tool use",
      fix: "A step that uses a leased resource needs a step that reserves it earlier. Add a reserve step before it and connect them with transitions." },
    L03: { title: "Tool use does not depend on its reserve step",
      fix: "Read the lease token from the reserve step in this step's Input κ(s), for example ${result.reserve.lease}; that makes the reserve step a dependency." },
    L04: { title: "Lease duration missing or too short",
      fix: "Declare a lease duration for the resource (Recipe JSON view) that is longer than the nominal duration of the steps that use it (nominalDurationSeconds in the step's Other policy fields)." },
    L05: { title: "No release step after the tool use",
      fix: "Add a step that releases the resource after its last use and connect it with a transition." },
    M01: { title: "Quantity above 1",
      fix: "A step runs once per session. Draw the step several times, or start several sessions." },
    M02: { title: "Executor choice without capability",
      fix: "The step names a workstation or system for its executor but no capability. Choose an executor capability under Consumer of the service in the step panel, or remove the workstation/system in the Recipe JSON view." },
    C01: { title: "Service not found in the running cloud",
      fix: "The WorkflowManager cannot find a provider of this service in the ServiceRegistry. Check the spelling, pick the service from the Services panel, or start and register the provider; then validate again." },
    C02: { title: "No executor can run the step",
      fix: "No running executor offers the step's capability η. Choose another capability under Consumer of the service in the step panel (the list shows which executors offer each), or start an executor that has it." }
  };

  const api = {
    STEP_W, STEP_H, FRAME_PAD, IDENT, VERSION,
    emptyDocument, clone, step, stepsOf, successors, predecessors, uniqueName, uniqueActionName,
    templateReferences, precedingSteps, renameStep, removeStep, removeAction, renameAction, connect, reaches, levels,
    autoLayout, edgeGeometry, labelLayout, guardLabel, labelWidth, FINDING_HELP, OUTCOME_HELP, frame, toRecipe, fromRecipe, layoutOf, applyLayout, localIssues, checkGuard, sequence, appendAfter
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.WfModel = api;
})(typeof self !== "undefined" ? self : this);
