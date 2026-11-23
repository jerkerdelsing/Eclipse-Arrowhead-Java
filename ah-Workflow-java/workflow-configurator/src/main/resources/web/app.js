/*
 * Copyright (c) 2026 Lulea University of Technology
 * SPDX-License-Identifier: EPL-2.0
 *
 * WorkflowConfigurator console: graphical editor of workflow recipes. The diagram model is in model.js; this file holds
 * the DOM, the two diagram views (state machine, sequence), the inspector and the calls to workflowConfiguration.
 */
(function () {
  "use strict";

  const M = window.WfModel;
  const API = "/workflowconfigurator/api";
  const SVGNS = "http://www.w3.org/2000/svg";
  const DRAFT_KEY = "wfc.draft";
  const CLOUD_KEY = "wfc.cloud";
  const LAYOUT_PREFIX = "wfc.layout.";
  const W = M.STEP_W, H = M.STEP_H;

  const state = {
    doc: M.emptyDocument(),
    selection: null, // {kind: "step"|"transition"|"action", name | from,to}
    view: "state",
    clouds: [],
    sources: [],
    cloudId: null,
    services: { entries: [], systems: [], live: true },
    executors: [],
    serverFindings: [],
    undo: [],
    redo: [],
    vb: { x: 0, y: 0, k: 1 },
    seqVb: { x: 0, y: 0, k: 1 },
    stored: null, // "name@version" when the document equals what is stored
    viewMoved: false // the user panned or zoomed: no automatic fit any more
  };

  const $ = id => document.getElementById(id);

  // =========================================================================================== utilities

  function el(tag, attrs, ...children) {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v == null || v === false) continue;
      if (k === "class") e.className = v;
      else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
      else if (k === "text") e.textContent = v;
      else e.setAttribute(k, v === true ? "" : v);
    }
    for (const c of children.flat()) if (c != null) e.append(c.nodeType ? c : document.createTextNode(String(c)));
    return e;
  }

  function sv(tag, attrs, ...children) {
    const e = document.createElementNS(SVGNS, tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v == null) continue;
      if (k === "text") e.textContent = v;
      else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
      else e.setAttribute(k, v);
    }
    for (const c of children.flat()) if (c != null) e.append(c);
    return e;
  }

  function clip(text, max) {
    text = String(text == null ? "" : text);
    return text.length > max ? text.slice(0, max - 1) + "…" : text;
  }

  function toast(message, kind) {
    const t = el("div", { class: "toast " + (kind || ""), role: kind === "error" ? "alert" : "status" }, message);
    $("toasts").append(t);
    setTimeout(() => t.remove(), kind === "error" ? 9000 : 4500);
  }

  function storage(fn, fallback) {
    try { return fn(window.localStorage); } catch (e) { return fallback; }
  }

  async function api(method, path, body) {
    const res = await fetch(API + path, {
      method,
      credentials: "same-origin",
      headers: body === undefined ? {} : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    let data = null;
    const text = await res.text();
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = { error: text }; }
    if (res.status === 401 && path !== "/session") {
      showLogin();
      throw Object.assign(new Error("Sign in first"), { status: 401, data });
    }
    if (!res.ok && res.status !== 422) {
      throw Object.assign(new Error((data && (data.error || data.errorMessage)) || res.statusText), { status: res.status, data });
    }
    return { status: res.status, data };
  }

  // =========================================================================================== document changes

  function snapshot() {
    return JSON.stringify(state.doc);
  }

  function mutate(fn, keepSelection) {
    state.undo.push(snapshot());
    if (state.undo.length > 150) state.undo.shift();
    state.redo = [];
    fn(state.doc);
    changed(keepSelection);
  }

  function changed(keepSelection) {
    state.stored = null;
    if (!keepSelection && state.selection && !selectionExists()) state.selection = null;
    state.serverFindings = []; // the WorkflowManager's findings belong to the previous version of the drawing
    state.serverVerdict = null;
    saveDraft();
    render();
  }

  function selectionExists() {
    const s = state.selection;
    if (!s) return true;
    if (s.kind === "step") return !!M.step(state.doc, s.name);
    if (s.kind === "action") return state.doc.actions.some(a => a.name === s.name);
    if (s.kind === "transition") return state.doc.transitions.some(t => t.from === s.from && t.to === s.to);
    return false;
  }

  function undo() {
    if (!state.undo.length) return;
    state.redo.push(snapshot());
    state.doc = JSON.parse(state.undo.pop());
    changed();
  }

  function redo() {
    if (!state.redo.length) return;
    state.undo.push(snapshot());
    state.doc = JSON.parse(state.redo.pop());
    changed();
  }

  let draftTimer = null;
  function saveDraft() {
    clearTimeout(draftTimer);
    draftTimer = setTimeout(() => storage(s => s.setItem(DRAFT_KEY, JSON.stringify(state.doc))), 300);
  }

  function loadDocument(doc, stored) {
    state.doc = doc;
    state.undo = [];
    state.redo = [];
    state.selection = null;
    state.serverFindings = [];
    state.stored = stored || null;
    fit();
    saveDraft();
    render();
  }

  function select(sel) {
    state.selection = sel;
    if (sel && sel.kind === "step") ensureVisible(sel.name);
    render();
  }

  /** Pans the state-machine view so that a step is inside it (new steps may land outside). */
  function ensureVisible(name) {
    const s = M.step(state.doc, name);
    const r = $("canvas").getBoundingClientRect();
    if (!s || !r.width || state.view !== "state") return;
    const vb = state.vb, w = r.width / vb.k, h = r.height / vb.k, m = 30;
    if (s.x < vb.x + m) vb.x = s.x - m;
    else if (s.x + W > vb.x + w - m) vb.x = s.x + W - w + m;
    if (s.y < vb.y + m) vb.y = s.y - m;
    else if (s.y + H > vb.y + h - m) vb.y = s.y + H - h + m;
  }

  // =========================================================================================== login and data

  function showLogin() {
    $("login").classList.remove("hidden");
    $("login-password").focus();
  }

  async function login(ev) {
    ev.preventDefault();
    $("login-error").textContent = "";
    try {
      const res = await fetch(API + "/session", {
        method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password: $("login-password").value })
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        $("login-error").textContent = body.error || res.statusText;
        return;
      }
      $("login-password").value = "";
      $("login").classList.add("hidden");
      if (!state.viewMoved) fit();
      render();
      loadData();
    } catch (e) {
      $("login-error").textContent = e.message;
    }
  }

  async function logout() {
    await fetch(API + "/session", { method: "DELETE", credentials: "same-origin" }).catch(() => null);
    showLogin();
  }

  async function loadData() {
    try {
      const { data } = await api("GET", "/clouds");
      state.clouds = data.entries || [];
      state.sources = data.sources || [];
      const remembered = storage(s => s.getItem(CLOUD_KEY), null);
      state.cloudId = state.clouds.some(c => c.id === remembered) ? remembered : (state.clouds[0] && state.clouds[0].id);
      renderClouds();
      await loadServices();
    } catch (e) {
      if (e.status !== 401) toast("Clouds could not be loaded: " + e.message, "error");
    }
    try {
      const { data } = await api("GET", "/executors");
      state.executors = data.entries || [];
    } catch (e) {
      if (e.status !== 401) toast("Executors could not be loaded: " + e.message, "error");
    }
    render();
  }

  async function loadServices() {
    if (!state.cloudId) return;
    try {
      const { data } = await api("GET", "/clouds/" + encodeURIComponent(state.cloudId) + "/services");
      state.services = data;
    } catch (e) {
      state.services = { entries: [], systems: [], live: false, note: e.message };
      if (e.status !== 401) toast("Services could not be loaded: " + e.message, "error");
    }
    renderPalette();
  }

  // =========================================================================================== top bar and side panels

  function renderClouds() {
    const sel = $("cloud-select");
    sel.innerHTML = "";
    const groups = {};
    for (const c of state.clouds) (groups[c.source] = groups[c.source] || []).push(c);
    const label = { ServiceRegistry: "Running cloud", LocalCloudConfigurator: "LocalCloudConfigurator designs", SoLocalCloudConfigurator: "Systems of LocalClouds" };
    for (const [source, list] of Object.entries(groups)) {
      const g = el("optgroup", { label: label[source] || source });
      list.forEach(c => g.append(el("option", { value: c.id, selected: c.id === state.cloudId }, c.name + (c.source === "SoLocalCloudConfigurator" ? "  (" + c.description.replace(/^Member cloud of the System of LocalClouds /, "SoLC ").split(",")[0] + ")" : ""))));
      sel.append(g);
    }
    const src = $("sources");
    src.innerHTML = "";
    const bySystem = {};
    state.sources.forEach(s => { bySystem[s.system] = (bySystem[s.system] !== false) && s.available; });
    state.sources.forEach(s => {
      if (s.system in bySystem && bySystem[s.system] !== null) {
        const ok = bySystem[s.system];
        const errors = state.sources.filter(x => x.system === s.system && !x.available).map(x => x.operation + ": " + x.error).join("\n");
        src.append(el("span", { class: "source", title: ok ? s.system + " available" : errors }, el("span", { class: "dot" + (ok ? "" : " off") }), s.system));
        bySystem[s.system] = null;
      }
    });
  }

  function renderPalette() {
    const filter = $("service-filter").value.trim().toLowerCase();
    const box = $("palette");
    box.innerHTML = "";
    const byProvider = {};
    for (const s of state.services.entries || []) (byProvider[s.provider] = byProvider[s.provider] || []).push(s);
    const systems = (state.services.systems || []).map(s => s.name);
    Object.keys(byProvider).forEach(p => { if (!systems.includes(p)) systems.push(p); });
    if (state.services.note) box.append(el("p", { class: "hint" }, state.services.note));
    let shown = 0;
    for (const name of systems.sort((a, b) => a.localeCompare(b))) {
      const services = (byProvider[name] || []).filter(s => !filter || name.toLowerCase().includes(filter) || s.serviceDefinition.toLowerCase().includes(filter));
      if (filter && !services.length && !name.toLowerCase().includes(filter)) continue;
      const info = (state.services.systems || []).find(s => s.name === name) || {};
      const details = el("details", { class: "system" + (services.length ? "" : " idle"), open: filter || services.length <= 6 ? true : null },
        el("summary", {}, name, info.kind ? el("span", { class: "badge grey" }, info.kind) : null,
          info.running === false ? el("span", { class: "badge grey" }, "not running") : null,
          el("span", { class: "count" }, services.length ? services.length + "" : "")));
      const chips = el("div", { class: "chips" });
      services.forEach(s => {
        const chip = el("div", { class: "chip", draggable: "true", title: "Drag into the diagram" },
          s.serviceDefinition, " ", el("span", { class: "badge" }, s.version),
          el("span", { class: "ops" }, (s.operations || []).join(", ") || "—"));
        chip.addEventListener("dragstart", ev => {
          ev.dataTransfer.setData("application/x-wfc-service", JSON.stringify({ serviceDefinition: s.serviceDefinition, provider: s.provider }));
          ev.dataTransfer.effectAllowed = "copy";
        });
        chip.addEventListener("dblclick", () => addStepFromService({ serviceDefinition: s.serviceDefinition, provider: s.provider }));
        chips.append(chip);
        shown++;
      });
      if (services.length) details.append(chips);
      box.append(details);
    }
    if (!shown && !state.services.note) box.append(el("p", { class: "hint" }, filter ? "No service matches." : "No services listed for this cloud."));
  }

  function renderWorkflowPanel() {
    const d = state.doc;
    const bind = (id, get, set) => {
      const e = $(id);
      if (document.activeElement !== e) e.value = get() || "";
      e.onchange = () => mutate(doc => set(doc, e.value.trim()), true);
    };
    bind("meta-name", () => d.meta.name, (doc, v) => { doc.meta.name = v; });
    bind("meta-version", () => d.meta.version, (doc, v) => { doc.meta.version = v; });
    bind("meta-description", () => d.meta.description, (doc, v) => { doc.meta.description = v; });
    bind("meta-dispatch", () => d.meta.dispatch, (doc, v) => { doc.meta.dispatch = v || null; });

    const params = $("param-list");
    params.innerHTML = "";
    d.parameters.forEach((p, i) => {
      const name = el("input", { class: "name", value: p.name, "aria-label": "Parameter name", spellcheck: "false" });
      name.onchange = () => mutate(doc => { doc.parameters[i].name = name.value.trim(); }, true);
      const type = el("select", { "aria-label": "Parameter type" }, ["string", "number", "integer", "boolean", "object"].map(t => el("option", { value: t, selected: t === p.type }, t)));
      type.style.width = "92px";
      type.onchange = () => mutate(doc => { doc.parameters[i].type = type.value; }, true);
      const req = el("input", { type: "checkbox", checked: p.required !== false, title: "Required" });
      req.style.width = "auto";
      req.onchange = () => mutate(doc => { doc.parameters[i].required = req.checked; }, true);
      params.append(el("div", { class: "row" }, name, type, req, el("button", { class: "small danger", title: "Remove", onclick: () => mutate(doc => doc.parameters.splice(i, 1), true) }, "✕")));
    });
    if (!d.parameters.length) params.append(el("p", { class: "hint" }, "Values given when a session starts; steps read them as ${param.name}."));

    const actions = $("action-list");
    actions.innerHTML = "";
    d.actions.forEach((a, i) => {
      const name = el("input", { class: "name", value: a.name, "aria-label": "Action name", spellcheck: "false" });
      name.onchange = () => renameActionUi(a.name, name.value.trim());
      actions.append(el("div", { class: "row" },
        el("span", { class: "muted" }, (i + 1) + "."), name,
        el("button", { class: "small", title: "Earlier", disabled: i === 0, onclick: () => moveAction(i, -1) }, "↑"),
        el("button", { class: "small", title: "Later", disabled: i === d.actions.length - 1, onclick: () => moveAction(i, 1) }, "↓"),
        el("button", { class: "small danger", title: "Delete action and its steps", disabled: d.actions.length <= 1, onclick: () => deleteAction(a.name) }, "✕")));
    });
  }

  function renameActionUi(from, to) {
    if (from === to) return;
    if (!M.IDENT.test(to)) return toast("An action name starts with a letter and has letters, digits, - and _", "error"), render();
    if (state.doc.actions.some(a => a.name === to) || M.step(state.doc, to)) return toast("The name " + to + " is taken", "error"), render();
    mutate(doc => M.renameAction(doc, from, to), true);
    if (state.selection && state.selection.kind === "action" && state.selection.name === from) select({ kind: "action", name: to });
  }

  function moveAction(i, delta) {
    mutate(doc => {
      const [a] = doc.actions.splice(i, 1);
      doc.actions.splice(i + delta, 0, a);
      M.autoLayout(doc);
    }, true);
  }

  function deleteAction(name) {
    const n = M.stepsOf(state.doc, name).length;
    if (n && !confirm("Delete action " + name + " and its " + n + " step(s)?")) return;
    mutate(doc => M.removeAction(doc, name));
  }

  function addAction() {
    mutate(doc => {
      const last = doc.actions[doc.actions.length - 1];
      const f = last ? M.frame(doc, last.name) : { x: 120, y: 70, w: 0 };
      doc.actions.push({ name: M.uniqueActionName(doc, "action"), x: f.x + f.w + 90, y: f.y });
    });
  }

  // =========================================================================================== steps

  function newStep(service, action, x, y) {
    return {
      name: M.uniqueName(state.doc, service.serviceDefinition),
      action,
      service: { serviceDefinition: service.serviceDefinition },
      provider: service.provider || null,
      executor: null, params: {}, input: null, dep: [], policy: {},
      x: Math.round(x / 10) * 10, y: Math.round(y / 10) * 10
    };
  }

  function addStepFromService(service, point) {
    const doc = state.doc;
    let action = null;
    if (point) {
      action = doc.actions.find(a => { const f = M.frame(doc, a.name); return point.x >= f.x && point.x <= f.x + f.w && point.y >= f.y && point.y <= f.y + f.h; });
    }
    if (!action) {
      const sel = state.selection;
      const selected = sel && sel.kind === "step" ? M.step(doc, sel.name) : null;
      action = doc.actions.find(a => (sel && sel.kind === "action" && a.name === sel.name) || (selected && a.name === selected.action)) || doc.actions[doc.actions.length - 1];
    }
    let x, y;
    if (point && pointInFrame(point, action.name)) {
      x = point.x - W / 2; y = point.y - H / 2;
    } else {
      const ss = M.stepsOf(doc, action.name);
      const f = M.frame(doc, action.name);
      x = ss.length ? Math.max(...ss.map(s => s.x)) + W + 70 : f.x + M.FRAME_PAD;
      y = ss.length ? ss.find(s => s.x === Math.max(...ss.map(q => q.x))).y : f.y + M.FRAME_PAD + 20;
    }
    let created = null;
    mutate(d => {
      created = newStep(service, action.name, x, y);
      const sel = state.selection;
      d.steps.push(created);
      // dropping next to the selected step of the same action continues the flow from it
      if (!point && sel && sel.kind === "step" && M.step(d, sel.name) && M.step(d, sel.name).action === action.name) M.connect(d, sel.name, created.name);
    });
    select({ kind: "step", name: created.name });
  }

  function pointInFrame(p, action) {
    const f = M.frame(state.doc, action);
    return p.x >= f.x && p.x <= f.x + f.w && p.y >= f.y && p.y <= f.y + f.h;
  }

  function customService() {
    openModal("Step with another service", body => {
      const sd = el("input", { placeholder: "e.g. temperature-read", spellcheck: "false", list: "sd-list" });
      const list = el("datalist", { id: "sd-list" }, [...new Set((state.services.entries || []).map(s => s.serviceDefinition))].map(n => el("option", { value: n })));
      body.append(el("p", { class: "muted" }, "Name a service definition that is not in the palette, e.g. one of another cloud. Orchestration finds its provider when the step runs."),
        el("label", {}, "Service definition", sd, list));
      sd.focus();
      return () => {
        const v = sd.value.trim();
        if (!v) return false;
        addStepFromService({ serviceDefinition: v });
        return true;
      };
    }, "Add step");
  }

  // =========================================================================================== state-machine view

  let drag = null;

  function svgPoint(svg, ev, vb) {
    const r = svg.getBoundingClientRect();
    return { x: vb.x + (ev.clientX - r.left) / vb.k, y: vb.y + (ev.clientY - r.top) / vb.k };
  }

  function issueMap() {
    const map = {};
    const add = (where, sev) => {
      if (!map[where] || sev === "error") map[where] = sev;
    };
    M.localIssues(state.doc).forEach(i => add(i.where, "error"));
    state.serverFindings.forEach(f => {
      if (f.outcome === "REVISE" || f.outcome === "REJECT") add(f.location, "error");
      else if (f.outcome === "CLARIFY") add(f.location, "warn");
    });
    return map;
  }

  function defs() {
    return sv("defs", {},
      sv("marker", { id: "arrow", viewBox: "0 0 10 10", refX: "9", refY: "5", markerWidth: "8", markerHeight: "8", orient: "auto-start-reverse" }, sv("path", { d: "M0,0 L10,5 L0,10 z", class: "marker" })),
      sv("marker", { id: "arrow-sel", viewBox: "0 0 10 10", refX: "9", refY: "5", markerWidth: "8", markerHeight: "8", orient: "auto-start-reverse" }, sv("path", { d: "M0,0 L10,5 L0,10 z", class: "marker sel" })),
      sv("marker", { id: "arrow-open", viewBox: "0 0 10 10", refX: "9", refY: "5", markerWidth: "9", markerHeight: "9", orient: "auto" }, sv("path", { d: "M0,0 L10,5 L0,10", fill: "none", stroke: "currentColor", class: "marker" })));
  }

  function renderStateView() {
    const svg = $("canvas");
    const doc = state.doc;
    const vb = state.vb;
    svg.innerHTML = "";
    svg.append(defs());
    const g = sv("g", { class: "viewport", transform: viewTransform(vb) });
    svg.append(g);
    const issues = issueMap();
    const sel = state.selection || {};

    // action frames, initial and final pseudo-states, next_A flow
    const frames = doc.actions.map(a => ({ a, f: M.frame(doc, a.name) }));
    frames.forEach(({ a, f }) => {
      const selected = sel.kind === "action" && sel.name === a.name;
      const fg = sv("g", { class: "frame" + (selected ? " selected" : ""), "data-action": a.name });
      fg.append(sv("rect", { class: "body", x: f.x, y: f.y, width: f.w, height: f.h, rx: 14 }));
      fg.append(sv("text", { class: "title", x: f.x + 12, y: f.y + 18, text: "action " + a.name }));
      if (f.empty) fg.append(sv("text", { class: "placeholder", x: f.x + f.w / 2, y: f.y + f.h / 2 + 8, "text-anchor": "middle", text: "drop services here" }));
      fg.addEventListener("pointerdown", ev => {
        if (ev.button !== 0) return;
        ev.stopPropagation();
        select({ kind: "action", name: a.name });
        if (f.empty) {
          const p = svgPoint(svg, ev, vb);
          drag = { kind: "action", name: a.name, dx: p.x - a.x, dy: p.y - a.y, sx: ev.clientX, sy: ev.clientY, before: snapshot(), moved: false };
        }
      });
      g.append(fg);
    });
    if (frames.length) {
      const first = frames[0].f;
      const cy = first.y + first.h / 2;
      g.append(sv("circle", { class: "pseudo", cx: first.x - 50, cy, r: 9 }));
      g.append(sv("g", { class: "flow" }, sv("path", { d: "M" + (first.x - 41) + "," + cy + " L" + (first.x - 2) + "," + cy, "marker-end": "url(#arrow)" })));
      for (let i = 0; i + 1 < frames.length; i++) {
        const a = frames[i].f, b = frames[i + 1].f;
        const y1 = a.y + a.h / 2, y2 = b.y + b.h / 2;
        const mx = (a.x + a.w + b.x) / 2;
        g.append(sv("g", { class: "flow" }, sv("path", { d: "M" + (a.x + a.w) + "," + y1 + " C" + mx + "," + y1 + " " + mx + "," + y2 + " " + (b.x - 2) + "," + y2, "marker-end": "url(#arrow)" })));
      }
      const last = frames[frames.length - 1].f;
      const ly = last.y + last.h / 2;
      g.append(sv("g", { class: "flow" }, sv("path", { d: "M" + (last.x + last.w) + "," + ly + " L" + (last.x + last.w + 38) + "," + ly, "marker-end": "url(#arrow)" })));
      g.append(sv("circle", { class: "pseudo-ring", cx: last.x + last.w + 50, cy: ly, r: 10 }));
      g.append(sv("circle", { class: "pseudo", cx: last.x + last.w + 50, cy: ly, r: 6 }));
    }

    // transitions; their condition labels go to a layer above the steps, placed clear of steps and of each other
    const labels = sv("g", { class: "labels" });
    const placed = M.labelLayout(doc);
    for (const t of doc.transitions) {
      const e = M.edgeGeometry(doc, t);
      if (!e) continue;
      const key = "transition " + t.from + " → " + t.to;
      const selected = sel.kind === "transition" && sel.from === t.from && sel.to === t.to;
      const eg = sv("g", { class: "edge" + (t.guard ? " guarded" : "") + (selected ? " selected" : "") + (issues[key] === "error" ? " issue-error" : "") });
      eg.append(sv("path", { class: "line", d: e.d, "marker-end": selected ? "url(#arrow-sel)" : "url(#arrow)" }));
      eg.append(sv("path", { class: "hit", d: e.d }));
      const pick = ev => { ev.stopPropagation(); select({ kind: "transition", from: t.from, to: t.to }); };
      const lb = placed[t.from + "→" + t.to];
      if (lb) {
        const lg = sv("g", { class: "edge" + (selected ? " selected" : "") });
        // a label moved off its curve keeps a thin leader to the point on the curve it belongs to
        if (Math.abs(lb.y - lb.anchorY) > 2) lg.append(sv("path", { class: "leader", d: "M" + lb.x + "," + lb.anchorY + " L" + lb.x + "," + (lb.y + (lb.anchorY > lb.y ? lb.h / 2 : -lb.h / 2)) }));
        lg.append(sv("rect", { class: "label", x: lb.x - lb.w / 2, y: lb.y - lb.h / 2, width: lb.w, height: lb.h, rx: 4 }));
        lg.append(sv("text", { class: "label", x: lb.x, y: lb.y + 4, "text-anchor": "middle", text: lb.text }));
        lg.append(sv("title", { text: t.guard }));
        lg.addEventListener("pointerdown", pick);
        labels.append(lg);
      }
      eg.addEventListener("pointerdown", pick);
      g.append(eg);
    }

    // steps
    for (const s of doc.steps) {
      const key = "step " + s.name;
      const selected = sel.kind === "step" && sel.name === s.name;
      const issue = issues[key];
      const sg = sv("g", { class: "step" + (selected ? " selected" : "") + (issue ? " issue-" + issue : ""), transform: "translate(" + s.x + "," + s.y + ")", "data-step": s.name });
      sg.append(sv("rect", { class: "body", width: W, height: H, rx: 10 }));
      if (s.executor && s.executor.capability) sg.append(sv("rect", { class: "exec", width: 5, height: H - 12, x: 0, y: 6, rx: 2 }));
      sg.append(sv("text", { class: "name", x: 12, y: 20, text: clip(s.name, 22) }));
      sg.append(sv("text", { x: 12, y: 37, text: clip(s.service.serviceDefinition || "— choose a service —", 26) }));
      const sub = [s.provider ? "@" + s.provider : null, s.executor && s.executor.capability ? "η " + s.executor.capability : null].filter(Boolean).join("  ");
      sg.append(sv("text", { class: "sub", x: 12, y: 53, text: clip(sub || "orchestrated per session", 30) }));
      // the visible handle is small; an invisible ring around it makes it easy to grab
      const port = sv("g", { class: "port-hit" },
        sv("circle", { cx: W, cy: H / 2, r: 16, fill: "transparent" }),
        sv("circle", { class: "port", cx: W, cy: H / 2, r: 7 }),
        sv("title", { text: "Drag to another step to add a transition" }));
      sg.append(port);
      port.addEventListener("pointerdown", ev => {
        if (ev.button !== 0) return;
        ev.stopPropagation();
        drag = { kind: "connect", from: s.name, line: sv("path", { class: "temp-edge" }) };
        g.append(drag.line);
        svg.setPointerCapture(ev.pointerId);
      });
      sg.addEventListener("pointerdown", ev => {
        if (ev.button !== 0) return;
        ev.stopPropagation();
        const p = svgPoint(svg, ev, vb);
        drag = { kind: "step", name: s.name, dx: p.x - s.x, dy: p.y - s.y, sx: ev.clientX, sy: ev.clientY, before: snapshot(), moved: false };
        svg.setPointerCapture(ev.pointerId);
        if (!selected) select({ kind: "step", name: s.name });
      });
      g.append(sg);
    }
    g.append(labels);
    $("empty-hint").classList.toggle("hidden", doc.steps.length > 0 || state.view !== "state");
  }

  function stepAt(p) {
    return state.doc.steps.slice().reverse().find(s => p.x >= s.x && p.x <= s.x + W && p.y >= s.y && p.y <= s.y + H);
  }

  function onPointerMove(ev) {
    const svg = $("canvas");
    if (!drag) return;
    const vb = state.view === "sequence" ? state.seqVb : state.vb;
    if (drag.kind === "pan") {
      if (!drag.moved && Math.hypot(ev.clientX - drag.sx, ev.clientY - drag.sy) < DRAG_START_PX) return;
      drag.moved = true;
      state.viewMoved = true;
      $("canvas").classList.add("panning");
      vb.x = drag.vx - (ev.clientX - drag.sx) / vb.k;
      vb.y = drag.vy - (ev.clientY - drag.sy) / vb.k;
      moveViewport();
      return;
    }
    // a click on a step or frame must not nudge it: moving starts after a few pixels
    if ((drag.kind === "step" || drag.kind === "action") && !drag.moved && Math.hypot(ev.clientX - drag.sx, ev.clientY - drag.sy) < DRAG_START_PX) return;
    const p = svgPoint(svg, ev, vb);
    if (drag.kind === "step") {
      const s = M.step(state.doc, drag.name);
      const nx = Math.round((p.x - drag.dx) / 10) * 10, ny = Math.round((p.y - drag.dy) / 10) * 10;
      if (nx !== s.x || ny !== s.y) {
        s.x = nx; s.y = ny; drag.moved = true;
        renderStateViewThrottled();
      }
    } else if (drag.kind === "action") {
      const a = state.doc.actions.find(x => x.name === drag.name);
      a.x = Math.round((p.x - drag.dx) / 10) * 10; a.y = Math.round((p.y - drag.dy) / 10) * 10;
      drag.moved = true;
      renderStateViewThrottled();
    } else if (drag.kind === "connect") {
      const a = M.step(state.doc, drag.from);
      const x1 = a.x + W, y1 = a.y + H / 2;
      drag.line.setAttribute("d", "M" + x1 + "," + y1 + " C" + (x1 + 60) + "," + y1 + " " + (p.x - 60) + "," + p.y + " " + p.x + "," + p.y);
    }
  }

  const DRAG_START_PX = 4;

  function viewTransform(vb) {
    return "translate(" + (-vb.x * vb.k) + "," + (-vb.y * vb.k) + ") scale(" + vb.k + ")";
  }

  // panning only moves the drawing: one transform update per frame instead of rebuilding the diagram on every event
  let movePending = false;
  function moveViewport() {
    if (movePending) return;
    movePending = true;
    requestAnimationFrame(() => {
      movePending = false;
      const vb = state.view === "sequence" ? state.seqVb : state.vb;
      const g = $("canvas").querySelector("g.viewport");
      if (g) g.setAttribute("transform", viewTransform(vb)); else renderCanvas();
    });
  }

  let canvasPending = false;
  function renderCanvasThrottled() {
    if (canvasPending) return;
    canvasPending = true;
    requestAnimationFrame(() => { canvasPending = false; renderCanvas(); });
  }

  let rafPending = false;
  function renderStateViewThrottled() {
    if (rafPending) return;
    rafPending = true;
    requestAnimationFrame(() => { rafPending = false; renderStateView(); });
  }

  function onPointerUp(ev) {
    const svg = $("canvas");
    if (!drag) return;
    const d = drag;
    drag = null;
    svg.classList.remove("panning");
    if ((d.kind === "step" || d.kind === "action") && d.moved) {
      state.undo.push(d.before);
      state.redo = [];
      // a step dropped into another action's frame moves there; transitions that would cross actions are removed
      if (d.kind === "step") {
        const s = M.step(state.doc, d.name);
        const center = { x: s.x + W / 2, y: s.y + H / 2 };
        const target = state.doc.actions.find(a => a.name !== s.action && pointInFrame(center, a.name));
        if (target) moveStepToAction(s.name, target.name, true);
      }
      changed(true);
    } else if (d.kind === "connect") {
      const p = svgPoint(svg, ev, state.vb);
      const target = stepAt(p);
      if (target && target.name !== d.from) {
        const err = M.connect(M.clone(state.doc), d.from, target.name);
        if (err) toast(err, "error");
        else {
          mutate(doc => M.connect(doc, d.from, target.name));
          select({ kind: "transition", from: d.from, to: target.name });
          return;
        }
      }
      render();
    }
  }

  function moveStepToAction(name, action, silent) {
    const s = M.step(state.doc, name);
    const dropped = state.doc.transitions.filter(t => t.from === name || t.to === name).length;
    s.action = action;
    state.doc.transitions = state.doc.transitions.filter(t => t.from !== name && t.to !== name);
    if (dropped) toast("Moved " + name + " to action " + action + "; its " + dropped + " transition(s) were removed (transitions stay inside an action)");
    else if (!silent) toast("Moved " + name + " to action " + action);
  }

  function onPointerDownBackground(ev) {
    if (ev.button !== 0 && ev.button !== 1) return;
    const vb = state.view === "sequence" ? state.seqVb : state.vb;
    drag = { kind: "pan", sx: ev.clientX, sy: ev.clientY, vx: vb.x, vy: vb.y, moved: false };
    $("canvas").setPointerCapture(ev.pointerId);
    if (state.selection) select(null);
  }


  function onWheel(ev) {
    ev.preventDefault();
    state.viewMoved = true;
    const vb = state.view === "sequence" ? state.seqVb : state.vb;
    // deltaMode 1 = lines (a mouse wheel), 0 = pixels (a trackpad)
    const unit = ev.deltaMode === 1 ? 16 : ev.deltaMode === 2 ? 400 : 1;
    const dx = ev.deltaX * unit, dy = ev.deltaY * unit;
    if (ev.ctrlKey || ev.metaKey || ev.altKey) {
      // pinch arrives as ctrl + wheel with small deltas; the factor follows the delta and is capped per event
      const p = svgPoint($("canvas"), ev, vb);
      const factor = Math.exp(-Math.max(-60, Math.min(60, dy)) * 0.004);
      const k = Math.min(2.5, Math.max(0.25, vb.k * factor));
      vb.x = p.x - (p.x - vb.x) * vb.k / k;
      vb.y = p.y - (p.y - vb.y) * vb.k / k;
      vb.k = k;
      renderCanvasThrottled();
      return;
    }
    vb.x += (ev.shiftKey && !dx ? dy : dx) / vb.k;
    vb.y += (ev.shiftKey && !dx ? 0 : dy) / vb.k;
    moveViewport();
  }

  function zoom(factor) {
    const vb = state.view === "sequence" ? state.seqVb : state.vb;
    const r = $("canvas").getBoundingClientRect();
    const cx = vb.x + r.width / 2 / vb.k, cy = vb.y + r.height / 2 / vb.k;
    vb.k = Math.min(2.5, Math.max(0.25, vb.k * factor));
    vb.x = cx - r.width / 2 / vb.k;
    vb.y = cy - r.height / 2 / vb.k;
    renderCanvas();
  }

  function fit() {
    const r = $("canvas").getBoundingClientRect();
    if (!r.width) return;
    const frames = state.doc.actions.map(a => M.frame(state.doc, a.name));
    if (!frames.length) return;
    const minX = Math.min(...frames.map(f => f.x)) - 80, minY = Math.min(...frames.map(f => f.y)) - 40;
    const maxX = Math.max(...frames.map(f => f.x + f.w)) + 90, maxY = Math.max(...frames.map(f => f.y + f.h)) + 40;
    const k = Math.min(1.4, Math.max(0.3, Math.min(r.width / (maxX - minX), r.height / (maxY - minY))));
    state.vb = { k, x: minX - (r.width / k - (maxX - minX)) / 2, y: minY - (r.height / k - (maxY - minY)) / 2 };
    // sequence view: lifelines are 200 apart from x = 110; fit the width, never enlarge
    const lifelines = M.sequence(state.doc).lifelines.length;
    const width = 110 + (lifelines - 1) * 200 + 120;
    const sk = Math.min(1, Math.max(0.3, r.width / width));
    state.seqVb = { x: 0, y: 0, k: sk };
  }

  function onDrop(ev) {
    const data = ev.dataTransfer.getData("application/x-wfc-service");
    if (!data) return;
    ev.preventDefault();
    const service = JSON.parse(data);
    if (state.view === "sequence") {
      const after = state.selection && state.selection.kind === "step" ? state.selection.name : null;
      let created = null;
      mutate(doc => { created = M.appendAfter(doc, after, newStep(service, null, 0, 0)); });
      select({ kind: "step", name: created.name });
      return;
    }
    addStepFromService(service, svgPoint($("canvas"), ev, state.vb));
  }

  // =========================================================================================== sequence view

  function renderSequenceView() {
    const svg = $("canvas");
    const vb = state.seqVb;
    const seq = M.sequence(state.doc);
    const issues = issueMap();
    const sel = state.selection || {};
    svg.innerHTML = "";
    svg.append(defs());
    const g = sv("g", { class: "viewport", transform: viewTransform(vb) });
    svg.append(g);
    const X0 = 110, DX = 200, TOP = 30;
    const xs = {};
    seq.lifelines.forEach((l, i) => { xs[l.id] = X0 + i * DX; });
    const right = X0 + (seq.lifelines.length - 1) * DX;
    const left = X0 - 90;
    let y = TOP + 60;
    const body = sv("g", {});
    const message = (m, yy) => {
      const selected = sel.kind === "step" && sel.name === m.step;
      const mg = sv("g", { class: "msg" + (selected ? " selected" : "") + (issues["step " + m.step] === "error" ? " issue-error" : "") });
      const x1 = xs[m.from], x2 = xs[m.to];
      const dir = x2 >= x1 ? 1 : -1;
      mg.append(sv("rect", { class: "activation", x: x2 - 5, y: yy - 4, width: 10, height: 26 }));
      mg.append(sv("line", { class: "call", x1, y1: yy, x2: x2 - dir * 6, y2: yy, "marker-end": selected ? "url(#arrow-sel)" : "url(#arrow)" }));
      mg.append(sv("line", { class: "reply", x1: x2 - dir * 6, y1: yy + 18, x2: x1 + dir * 2, y2: yy + 18, "marker-end": "url(#arrow)" }));
      mg.append(sv("text", { x: (x1 + x2) / 2, y: yy - 6, "text-anchor": "middle", text: clip(m.label, 42) }));
      mg.append(sv("rect", { class: "hit", x: Math.min(x1, x2), y: yy - 20, width: Math.max(40, Math.abs(x2 - x1)), height: 44 }));
      mg.addEventListener("pointerdown", ev => { ev.stopPropagation(); select({ kind: "step", name: m.step }); });
      body.append(mg);
    };
    for (const row of seq.rows) {
      if (row.kind === "action") {
        const ag = sv("g", { class: "band" });
        ag.append(sv("rect", { x: left, y, width: right - left + 90, height: 24, rx: 6 }));
        ag.append(sv("text", { x: left + 10, y: y + 16, text: "action " + row.name }));
        ag.addEventListener("pointerdown", ev => { ev.stopPropagation(); select({ kind: "action", name: row.name }); });
        body.append(ag);
        y += 46;
      } else if (row.kind === "message") {
        y += 12;
        message(row, y);
        y += 46;
      } else {
        const fg = sv("g", { class: "fragment " + row.kind });
        const top = y;
        y += 30;
        const seps = [];
        row.operands.forEach((op, i) => {
          if (i > 0) { seps.push(y); y += 18; }
          if (op.guard) fg.append(sv("text", { class: "guard", x: left + 54, y: y - (i === 0 ? 12 : 4), text: "[" + clip(op.guard, 60) + "]" }));
          op.messages.forEach(m => { y += 8; message(m, y); y += 40; });
        });
        y += 6;
        fg.prepend(sv("rect", { class: "box", x: left + 4, y: top, width: right - left + 82, height: y - top, rx: 2 }));
        fg.append(sv("path", { class: "tag", d: "M" + (left + 4) + "," + top + " h44 v12 l-8,8 h-36 z" }));
        fg.append(sv("text", { class: "tag", x: left + 12, y: top + 14, text: row.kind }));
        seps.forEach(sy => fg.append(sv("line", { class: "sep", x1: left + 4, y1: sy, x2: right + 86, y2: sy })));
        body.prepend(fg);
        y += 14;
      }
    }
    const bottom = Math.max(y + 20, TOP + 160);
    seq.lifelines.forEach(l => {
      const lg = sv("g", { class: "lifeline " + l.kind });
      const w = Math.min(180, Math.max(110, l.label.length * 7.2 + 20));
      lg.append(sv("line", { x1: xs[l.id], y1: TOP + 36, x2: xs[l.id], y2: bottom }));
      lg.append(sv("rect", { x: xs[l.id] - w / 2, y: TOP, width: w, height: 36, rx: 6 }));
      lg.append(sv("text", { x: xs[l.id], y: TOP + 23, "text-anchor": "middle", text: clip(l.label, 24) }));
      g.append(lg);
    });
    g.append(body);
    if (!state.doc.steps.length) {
      g.append(sv("text", { x: X0, y: TOP + 110, class: "hint", fill: "currentColor", text: "Drag services here: each becomes the next message after the selected step." }));
    }
  }

  // =========================================================================================== json view

  function renderJsonView() {
    $("json-text").textContent = JSON.stringify(M.toRecipe(state.doc), null, 2);
  }

  // =========================================================================================== inspector

  function field(label, input, note) {
    return el("label", {}, label, input, note ? el("div", { class: "note" }, note) : null);
  }

  function jsonArea(value, onValid, rows) {
    const area = el("textarea", { rows: rows || 4, spellcheck: "false" });
    area.value = value == null ? "" : JSON.stringify(value, null, 2);
    const err = el("div", { class: "field-error" });
    area.onchange = () => {
      const text = area.value.trim();
      if (!text) { err.textContent = ""; onValid(null); return; }
      try { const v = JSON.parse(text); err.textContent = ""; onValid(v); } catch (e) { err.textContent = "Not JSON: " + e.message; }
    };
    return [area, err];
  }

  function renderInspector() {
    const box = $("inspector");
    box.innerHTML = "";
    const sel = state.selection;
    if (sel && sel.kind === "step" && M.step(state.doc, sel.name)) return inspectStep(box, M.step(state.doc, sel.name));
    if (sel && sel.kind === "transition") {
      const t = state.doc.transitions.find(x => x.from === sel.from && x.to === sel.to);
      if (t) return inspectTransition(box, t);
    }
    if (sel && sel.kind === "action" && state.doc.actions.some(a => a.name === sel.name)) return inspectAction(box, state.doc.actions.find(a => a.name === sel.name));
    inspectRecipe(box);
  }

  function inspectRecipe(box) {
    const d = state.doc;
    const cloud = state.clouds.find(c => c.id === state.cloudId);
    box.append(el("div", { class: "kind" }, "Workflow"), el("h2", {}, d.meta.name + "@" + d.meta.version),
      el("dl", { class: "kv" },
        el("dt", {}, "Actions"), el("dd", {}, d.actions.map(a => a.name).join(" → ")),
        el("dt", {}, "Steps"), el("dd", {}, String(d.steps.length)),
        el("dt", {}, "Transitions"), el("dd", {}, d.transitions.length + " (" + d.transitions.filter(t => t.guard).length + " with a condition)"),
        el("dt", {}, "Parameters"), el("dd", {}, d.parameters.map(p => p.name).join(", ") || "—"),
        el("dt", {}, "Cloud"), el("dd", {}, cloud ? cloud.name + " (" + cloud.source + ")" : "—"),
        el("dt", {}, "Stored"), el("dd", {}, state.stored ? "yes, " + state.stored : "not since the last change")));
    box.append(el("div", { class: "section" },
      el("p", { class: "hint" }, "Select a step, a transition or an action frame to edit it. Drag a step's right handle to another step for a transition; a transition with a condition is taken only when the condition holds, and a step none of whose incoming transitions is taken is skipped."),
      el("p", { class: "hint" }, "Conditions read ${param.<name>} and ${result.<step>.<field>} and use == != < <= > >= && || ! and parentheses.")));
  }

  function inspectStep(box, s) {
    const doc = state.doc;
    const name = el("input", { value: s.name, spellcheck: "false" });
    name.onchange = () => {
      const v = name.value.trim();
      if (v === s.name) return;
      if (!M.IDENT.test(v)) return toast("A step name starts with a letter and has letters, digits, - and _", "error");
      if (M.step(doc, v) || doc.actions.some(a => a.name === v)) return toast("The name " + v + " is taken", "error");
      mutate(d => M.renameStep(d, s.name, v));
      state.selection = { kind: "step", name: v };
      render();
    };
    const action = el("select", {}, doc.actions.map(a => el("option", { value: a.name, selected: a.name === s.action }, a.name)));
    action.onchange = () => mutate(() => {
      moveStepToAction(s.name, action.value);
      const f = M.frame(state.doc, action.value);
      const st = M.step(state.doc, s.name);
      st.x = f.x + f.w - W - M.FRAME_PAD; st.y = f.y + f.h + 10;
    }, true);

    const sds = [...new Set((state.services.entries || []).map(x => x.serviceDefinition))].sort();
    const sd = el("input", { value: s.service.serviceDefinition || "", list: "sd-options", spellcheck: "false" });
    const sdList = el("datalist", { id: "sd-options" }, sds.map(n => el("option", { value: n })));
    sd.onchange = () => mutate(d => {
      const st = M.step(d, s.name);
      st.service.serviceDefinition = sd.value.trim();
      const offers = (state.services.entries || []).filter(x => x.serviceDefinition === st.service.serviceDefinition).map(x => x.provider);
      if (st.provider && !offers.includes(st.provider)) st.provider = offers[0] || null;
    }, true);
    const offers = (state.services.entries || []).filter(x => x.serviceDefinition === s.service.serviceDefinition);
    const provider = el("select", {}, el("option", { value: "" }, "(no hint)"), offers.map(o => el("option", { value: o.provider, selected: o.provider === s.provider }, o.provider + " " + o.version)));
    if (s.provider && !offers.some(o => o.provider === s.provider)) provider.append(el("option", { value: s.provider, selected: true }, s.provider + " (not listed)"));
    provider.onchange = () => mutate(d => { M.step(d, s.name).provider = provider.value || null; }, true);
    const offer = offers.find(o => o.provider === s.provider) || offers[0];

    const caps = [...new Set(state.executors.flatMap(e => e.capabilities || []))].sort();
    const current = s.executor && s.executor.capability || "";
    const exec = el("select", {}, el("option", { value: "" }, "WorkflowManager (no executor capability)"),
      caps.map(c => el("option", { value: c, selected: c === current }, "executor with capability " + c + " (" + state.executors.filter(e => (e.capabilities || []).includes(c)).map(e => e.name).join(", ") + ")")),
      current && !caps.includes(current) ? el("option", { value: current, selected: true }, "capability " + current + " (no executor offers it now)") : null,
      el("option", { value: "__other" }, "Other capability…"));
    exec.onchange = () => {
      let v = exec.value;
      if (v === "__other") { v = (prompt("Executor capability η(s)") || "").trim(); if (!v) return render(); }
      mutate(d => { M.step(d, s.name).executor = v ? Object.assign({}, M.step(d, s.name).executor || {}, { capability: v }) : null; }, true);
    };

    // input template κ_s with a reference helper
    const [input, inputErr] = jsonArea(s.input, v => mutate(d => { M.step(d, s.name).input = v; }, true), 5);
    const refs = el("select", {}, el("option", { value: "" }, "Insert a reference…"),
      doc.parameters.map(p => el("option", { value: "${param." + p.name + "}" }, "parameter " + p.name)),
      M.precedingSteps(doc, s.name).map(n => el("option", { value: "${result." + n + "}" }, "result of " + n)));
    refs.onchange = () => {
      if (!refs.value) return;
      const pos = input.selectionStart || input.value.length;
      input.value = input.value.slice(0, pos) + '"' + refs.value + '"' + input.value.slice(pos);
      refs.value = "";
      input.focus();
    };
    const [params, paramsErr] = jsonArea(Object.keys(s.params || {}).length ? s.params : null, v => mutate(d => { M.step(d, s.name).params = v || {}; }, true), 3);

    const timeout = el("input", { type: "number", min: "0", step: "1", value: s.policy && s.policy.timeoutSeconds || "" });
    timeout.onchange = () => mutate(d => { const p = M.step(d, s.name).policy; if (timeout.value) p.timeoutSeconds = Number(timeout.value); else delete p.timeoutSeconds; }, true);
    const attempts = el("input", { type: "number", min: "1", step: "1", value: s.policy && s.policy.retry && s.policy.retry.maxAttempts || "" });
    attempts.onchange = () => mutate(d => { const p = M.step(d, s.name).policy; if (attempts.value && Number(attempts.value) > 1) p.retry = { maxAttempts: Number(attempts.value), onError: "rebind" }; else delete p.retry; }, true);
    const otherPolicy = Object.assign({}, s.policy || {});
    delete otherPolicy.timeoutSeconds; delete otherPolicy.retry;
    const [policy, policyErr] = jsonArea(Object.keys(otherPolicy).length ? otherPolicy : null, v => mutate(d => {
      const st = M.step(d, s.name);
      st.policy = Object.assign({}, v || {}, st.policy.timeoutSeconds ? { timeoutSeconds: st.policy.timeoutSeconds } : {}, st.policy.retry ? { retry: st.policy.retry } : {});
    }, true), 3);

    const outgoing = doc.transitions.filter(t => t.from === s.name);
    const incoming = doc.transitions.filter(t => t.to === s.name);
    const targets = M.stepsOf(doc, s.action).filter(x => x.name !== s.name && !outgoing.some(t => t.to === x.name) && !M.reaches(doc, x.name, s.name));
    const addNext = el("select", {}, el("option", { value: "" }, "+ transition to…"), targets.map(x => el("option", { value: x.name }, x.name)));
    addNext.onchange = () => { if (addNext.value) { const to = addNext.value; mutate(d => M.connect(d, s.name, to)); } };

    box.append(el("div", { class: "kind" }, "Step"), el("h2", {}, s.name),
      field("Name", name),
      field("Action", action),
      field("Service definition θ(s)", sd, offer ? "Operations: " + (offer.operations || []).join(", ") : (state.services.live ? "No provider of this service is registered now" : null)), sdList,
      field("Provider (diagram hint)", provider, "Orchestration selects the provider for each session; the hint labels the diagram and the sequence lifeline."),
      field("Consumer of the service", exec, "With a capability, an executor offering it runs the step (η(s)); otherwise the WorkflowManager has the provider orchestrated and an executor runs it."),
      el("div", { class: "section" },
        field("Input κ(s) (JSON template)", input), inputErr, refs,
        field("Static parameters (JSON object)", params), paramsErr),
      el("div", { class: "section" },
        el("h3", {}, "Transitions"),
        incoming.length ? el("div", { class: "note" }, "From: " + incoming.map(t => t.from + (t.guard ? " [" + t.guard + "]" : "")).join(", ")) : el("div", { class: "note" }, "First step of action " + s.action),
        outgoing.map(t => el("div", { class: "transition-row" }, el("code", {}, "→ " + t.to),
          el("button", { class: "small", onclick: () => select({ kind: "transition", from: t.from, to: t.to }) }, t.guard ? "[" + clip(t.guard, 26) + "]" : "add condition"),
          el("button", { class: "small danger", title: "Remove", onclick: () => mutate(d => { d.transitions = d.transitions.filter(x => !(x.from === t.from && x.to === t.to)); }) }, "✕"))),
        targets.length ? addNext : null),
      el("div", { class: "section" },
        el("h3", {}, "Policy (extensions)"),
        field("Timeout (s)", timeout), field("Attempts on ERROR (re-bind)", attempts),
        field("Other policy fields (JSON: lease, nominalDurationSeconds, …)", policy), policyErr),
      el("div", { class: "buttons" },
        el("button", { onclick: () => duplicateStep(s.name) }, "Duplicate"),
        el("button", { class: "danger", onclick: () => mutate(d => M.removeStep(d, s.name)) }, "Delete step")));
  }

  function duplicateStep(name) {
    let created = null;
    mutate(d => {
      const s = M.clone(M.step(d, name));
      s.name = M.uniqueName(d, name);
      s.y += H + 34;
      created = s;
      d.steps.push(s);
    });
    select({ kind: "step", name: created.name });
  }

  function inspectTransition(box, t) {
    const doc = state.doc;
    const guard = el("textarea", { rows: 3, spellcheck: "false", placeholder: "always taken; e.g. ${result." + t.from + ".ok} == true" });
    guard.value = t.guard || "";
    const status = el("div", { class: "field-error" });
    const check = () => {
      const v = guard.value.trim();
      if (!v) { status.textContent = ""; status.style.color = ""; return; }
      const r = M.checkGuard(v);
      status.textContent = r.error ? r.error : "Condition is well-formed";
      status.style.color = r.error ? "" : "var(--ok)";
    };
    guard.oninput = check;
    guard.onchange = () => {
      const v = guard.value.trim();
      mutate(d => { const x = d.transitions.find(y => y.from === t.from && y.to === t.to); if (v) x.guard = v; else delete x.guard; }, true);
    };
    check();
    // builder: operand, operator, value
    const operands = [].concat(
      [t.from].concat(M.precedingSteps(doc, t.from)).map(n => "${result." + n + ".}"),
      doc.parameters.map(p => "${param." + p.name + "}"));
    const left = el("input", { list: "operand-list", placeholder: "${result." + t.from + ".field}", spellcheck: "false" });
    const leftList = el("datalist", { id: "operand-list" }, operands.map(o => el("option", { value: o })));
    const op = el("select", {}, ["==", "!=", "<", "<=", ">", ">=", "is true", "is false"].map(o => el("option", { value: o }, o)));
    const right = el("input", { placeholder: "value: 21.5, 'text', true", spellcheck: "false" });
    const apply = combine => () => {
      if (!left.value.trim()) return toast("Pick what the condition reads", "error");
      let expr;
      if (op.value === "is true") expr = left.value.trim();
      else if (op.value === "is false") expr = "!" + left.value.trim();
      else {
        let r = right.value.trim();
        if (!r) return toast("Give a value to compare with", "error");
        if (!/^(-?\d+(\.\d+)?|true|false|null|'.*'|".*"|\$\{.*\})$/.test(r)) r = "'" + r.replace(/'/g, "\\'") + "'";
        expr = left.value.trim() + " " + op.value + " " + r;
      }
      const current = guard.value.trim();
      guard.value = current && combine ? "(" + current + ") " + combine + " " + expr : expr;
      guard.onchange();
      check();
    };
    const siblings = doc.transitions.filter(x => x.from === t.from && x.to !== t.to && x.guard);
    box.append(el("div", { class: "kind" }, "Transition"), el("h2", {}, t.from + " → " + t.to),
      field("Condition", guard, "Taken when " + t.from + " has completed and the condition holds. Without a condition it is always taken."), status,
      el("div", { class: "section" },
        el("h3", {}, "Build a condition"),
        el("div", { class: "builder" }, left, op, right), leftList,
        el("div", { class: "buttons" },
          el("button", { onclick: apply(null) }, "Set"),
          el("button", { onclick: apply("&&") }, "And"),
          el("button", { onclick: apply("||") }, "Or"),
          siblings.length ? el("button", { title: "Taken exactly when the other branch is not", onclick: () => {
            guard.value = siblings.length === 1 ? "!(" + siblings[0].guard + ")" : "!(" + siblings.map(x => "(" + x.guard + ")").join(" || ") + ")";
            guard.onchange(); check();
          } }, "Otherwise (else branch)") : null)),
      el("div", { class: "buttons" },
        el("button", { onclick: () => select({ kind: "step", name: t.from }) }, "Go to " + t.from),
        el("button", { class: "danger", onclick: () => mutate(d => { d.transitions = d.transitions.filter(x => !(x.from === t.from && x.to === t.to)); }) }, "Delete transition")));
  }

  function inspectAction(box, a) {
    const doc = state.doc;
    const i = doc.actions.indexOf(a);
    const name = el("input", { value: a.name, spellcheck: "false" });
    name.onchange = () => renameActionUi(a.name, name.value.trim());
    box.append(el("div", { class: "kind" }, "Action " + (i + 1) + " of " + doc.actions.length), el("h2", {}, a.name),
      field("Name", name),
      el("p", { class: "hint" }, "Actions run one after the other; the steps inside an action follow its transitions and run in parallel where the graph allows."),
      el("div", { class: "buttons" },
        el("button", { disabled: i === 0, onclick: () => moveAction(i, -1) }, "Run earlier"),
        el("button", { disabled: i === doc.actions.length - 1, onclick: () => moveAction(i, 1) }, "Run later"),
        el("button", { class: "danger", disabled: doc.actions.length <= 1, onclick: () => deleteAction(a.name) }, "Delete action")));
  }

  // =========================================================================================== findings

  function renderFindings() {
    const list = $("findings-list");
    list.innerHTML = "";
    const local = M.localIssues(state.doc).map(i => ({ code: i.code || "local", outcome: "local", location: i.where, message: i.message }));
    const all = local.concat(state.serverFindings);
    const errors = all.filter(f => ["local", "REVISE", "REJECT", "CLARIFY"].includes(f.outcome)).length;
    $("findings-summary").textContent = all.length
      ? errors + " to resolve" + (state.serverFindings.length ? " · WorkflowManager: " + state.serverVerdict : " · not validated with the WorkflowManager yet")
      : (state.serverVerdict ? "WorkflowManager: " + state.serverVerdict : "no issues found while drawing");
    for (const f of all) {
      const help = M.FINDING_HELP[f.code];
      const key = f.code + "|" + f.location + "|" + f.message;
      const open = state.openHelp === key;
      const helpButton = el("button", { class: "small help-toggle", title: "How to fix this", "aria-expanded": open ? "true" : "false",
        onclick: ev => { ev.stopPropagation(); state.openHelp = open ? null : key; renderFindings(); } }, open ? "Hide help" : f.outcome === "ACCEPT" ? "What does this mean?" : "How to fix?");
      const li = el("li", { class: "sev-" + f.outcome, onclick: () => selectLocation(f.location), title: "Select " + (f.location || "the workflow") },
        el("span", { class: "code" }, f.code === "local" ? "draw" : f.code),
        el("span", { class: "msg" }, (f.location ? f.location + ": " : "") + f.message, f.outcome === "local" ? el("span", { class: "muted" }, " (while drawing)") : null),
        helpButton);
      list.append(li);
      if (open) {
        list.append(el("li", { class: "help" },
          el("div", {},
            el("strong", {}, help ? help.title : "No specific help for " + f.code),
            el("p", {}, help ? help.fix : "Select the item to see it in the diagram, and compare with the message. The README describes the validator rules."),
            el("p", { class: "muted" }, M.OUTCOME_HELP[f.outcome] || ""))));
      }
    }
    if (all.length) {
      list.append(el("li", { class: "legend muted" }, "Click a line to select the item it is about. Save stores the workflow only when the WorkflowManager answers ACCEPT; REVISE and CLARIFY must be resolved first, information (ACCEPT) needs nothing."));
    }
  }

  function selectLocation(loc) {
    let m;
    if ((m = /^step (.+)$/.exec(loc || ""))) select({ kind: "step", name: m[1] });
    else if ((m = /^transition (.+) → (.+)$/.exec(loc || ""))) select({ kind: "transition", from: m[1], to: m[2] });
    else if ((m = /^action (.+)$/.exec(loc || ""))) select({ kind: "action", name: m[1] });
    else select(null);
  }

  // =========================================================================================== WorkflowManager

  async function validate() {
    try {
      const { data } = await api("POST", "/recipes/validate", M.toRecipe(state.doc));
      state.serverFindings = data.findings || [];
      state.serverVerdict = data.verdict;
      render();
      toast("WorkflowManager: " + data.verdict + (data.findings && data.findings.length ? ", " + data.findings.length + " finding(s)" : ""), data.verdict === "ACCEPT" ? "ok" : "error");
      return data;
    } catch (e) {
      if (e.status !== 401) toast("Validation failed: " + e.message, "error");
      return null;
    }
  }

  async function save() {
    const local = M.localIssues(state.doc);
    if (local.length) {
      selectLocation(local[0].where);
      return toast("Fix the " + local.length + " issue(s) listed under Checks first", "error");
    }
    const recipe = M.toRecipe(state.doc);
    try {
      const { status, data } = await api("POST", "/recipes", recipe);
      state.serverFindings = (data.report && data.report.findings) || [];
      state.serverVerdict = data.report && data.report.verdict;
      if (status === 422) {
        render();
        return toast("Not stored: the WorkflowManager's verdict is " + state.serverVerdict + "; see Checks", "error");
      }
      const key = recipe.name + "@" + recipe.version;
      storage(s => s.setItem(LAYOUT_PREFIX + key, JSON.stringify(M.layoutOf(state.doc))));
      state.stored = key;
      render();
      toast(data.created ? "Stored " + key + " in the WorkflowManager" : key + " was already stored with this content", "ok");
    } catch (e) {
      if (e.status === 409) {
        openModal("Version exists", body => {
          body.append(el("p", {}, "The WorkflowManager already holds " + recipe.name + "@" + recipe.version + " with different content. Recipes are immutable: store this workflow as a new version."));
          return () => { mutate(d => { d.meta.version = bumpVersion(d.meta.version); }, true); save(); return true; };
        }, "Store as " + recipe.name + "@" + bumpVersion(recipe.version));
      } else if (e.status !== 401) {
        toast("Not stored: " + e.message, "error");
      }
    }
  }

  function bumpVersion(v) {
    const m = /^(\d+)\.(\d+)\.(\d+)$/.exec(v || "");
    return m ? m[1] + "." + m[2] + "." + (Number(m[3]) + 1) : "1.0.0";
  }

  async function openStored() {
    let entries = [];
    try {
      const { data } = await api("GET", "/recipes");
      entries = (data.entries || []).slice().reverse();
    } catch (e) {
      if (e.status !== 401) toast("Recipes could not be listed: " + e.message, "error");
      return;
    }
    openModal("Open a workflow from the WorkflowManager", body => {
      if (!entries.length) body.append(el("p", { class: "muted" }, "The WorkflowManager holds no recipes yet."));
      else body.append(el("table", {}, el("tr", {}, el("th", {}, "Name"), el("th", {}, "Version"), el("th", {}, "Status"), el("th", {}, "By"), el("th", {}, "Created")),
        entries.map(r => el("tr", { class: "pick", onclick: () => { closeModal(); loadStored(r); } },
          el("td", {}, r.name), el("td", {}, r.version), el("td", {}, r.status), el("td", {}, r.registeredBy || ""), el("td", {}, (r.createdAt || "").replace("T", " ").slice(0, 16))))));
      return null;
    });
  }

  function loadStored(entry) {
    if (!confirmDiscard()) return;
    const key = entry.name + "@" + entry.version;
    const layout = storage(s => JSON.parse(s.getItem(LAYOUT_PREFIX + key) || "null"), null);
    loadDocument(M.fromRecipe(entry.recipe, layout), key);
    toast("Opened " + key);
  }

  function confirmDiscard() {
    return state.stored || !state.undo.length || confirm("Discard the changes to the current workflow?");
  }

  // =========================================================================================== import / export

  function exportJson() {
    const recipe = M.toRecipe(state.doc);
    const blob = new Blob([JSON.stringify(recipe, null, 2) + "\n"], { type: "application/json" });
    const a = el("a", { href: URL.createObjectURL(blob), download: recipe.name + "-" + recipe.version + ".recipe.json" });
    document.body.append(a);
    a.click();
    setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
  }

  function importJson(file) {
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const recipe = JSON.parse(reader.result);
        if (!recipe || !Array.isArray(recipe.actions)) throw new Error("not a recipe (no actions)");
        if (!confirmDiscard()) return;
        loadDocument(M.fromRecipe(recipe));
        toast("Imported " + recipe.name + "@" + recipe.version);
      } catch (e) {
        toast("Import failed: " + e.message, "error");
      }
    };
    reader.readAsText(file);
  }

  // =========================================================================================== modal

  function openModal(title, build, okLabel) {
    const body = $("modal-body");
    body.innerHTML = "";
    body.append(el("h2", {}, title));
    const content = el("div");
    body.append(content);
    const onOk = build(content);
    body.append(el("div", { class: "buttons" },
      el("button", { onclick: closeModal }, onOk ? "Cancel" : "Close"),
      onOk ? el("button", { class: "primary", onclick: () => { if (onOk() !== false) closeModal(); } }, okLabel || "OK") : null));
    $("modal").classList.remove("hidden");
  }

  function closeModal() {
    $("modal").classList.add("hidden");
  }

  // =========================================================================================== render

  function renderCanvas() {
    const svg = $("canvas");
    const json = state.view === "json";
    svg.classList.toggle("hidden", json);
    $("json-view").classList.toggle("hidden", !json);
    if (state.view === "state") renderStateView();
    else if (state.view === "sequence") { renderSequenceView(); $("empty-hint").classList.add("hidden"); }
    else { renderJsonView(); $("empty-hint").classList.add("hidden"); }
  }

  function render() {
    renderCanvas();
    renderWorkflowPanel();
    renderInspector();
    renderFindings();
    $("doc-title").textContent = state.doc.meta.name + "@" + state.doc.meta.version + (state.stored ? " · stored" : "");
    $("btn-undo").disabled = !state.undo.length;
    $("btn-redo").disabled = !state.redo.length;
  }

  // =========================================================================================== wiring

  function wire() {
    $("login-form").addEventListener("submit", login);
    $("btn-logout").onclick = logout;
    $("cloud-select").onchange = () => {
      state.cloudId = $("cloud-select").value;
      storage(s => s.setItem(CLOUD_KEY, state.cloudId));
      loadServices().then(render);
    };
    $("service-filter").oninput = renderPalette;
    $("btn-custom-service").onclick = customService;
    $("btn-add-param").onclick = () => mutate(d => d.parameters.push({ name: "p" + (d.parameters.length + 1), type: "string", required: true }), true);
    $("btn-add-action").onclick = addAction;
    $("btn-new").onclick = () => { if (confirmDiscard()) loadDocument(M.emptyDocument()); };
    $("btn-open").onclick = openStored;
    $("btn-import").onclick = () => $("file-input").click();
    $("file-input").onchange = ev => { if (ev.target.files[0]) importJson(ev.target.files[0]); ev.target.value = ""; };
    $("btn-export").onclick = exportJson;
    $("btn-undo").onclick = undo;
    $("btn-redo").onclick = redo;
    $("btn-layout").onclick = () => { mutate(d => M.autoLayout(d), true); fit(); render(); };
    $("btn-validate").onclick = validate;
    $("btn-save").onclick = save;
    $("btn-zoom-in").onclick = () => zoom(1.2);
    $("btn-zoom-out").onclick = () => zoom(1 / 1.2);
    $("btn-zoom-reset").onclick = () => { fit(); state.viewMoved = false; renderCanvas(); };
    $("btn-copy-json").onclick = () => navigator.clipboard.writeText($("json-text").textContent).then(() => toast("Copied", "ok"), () => toast("Copy failed", "error"));
    document.querySelectorAll(".sidebar .tabs button").forEach(b => b.onclick = () => {
      document.querySelectorAll(".sidebar .tabs button").forEach(x => x.classList.toggle("active", x === b));
      document.querySelectorAll(".sidebar .panel").forEach(p => p.classList.toggle("hidden", p.id !== b.dataset.panel));
    });
    document.querySelectorAll(".views button[data-view]").forEach(b => b.onclick = () => {
      state.view = b.dataset.view;
      document.querySelectorAll(".views button[data-view]").forEach(x => x.classList.toggle("active", x === b));
      renderCanvas();
    });
    const svg = $("canvas");
    svg.addEventListener("pointerdown", onPointerDownBackground);
    svg.addEventListener("pointermove", onPointerMove);
    svg.addEventListener("pointerup", onPointerUp);
    svg.addEventListener("pointercancel", () => { drag = null; render(); });
    svg.addEventListener("wheel", onWheel, { passive: false });
    const wrap = $("canvas-wrap");
    wrap.addEventListener("dragover", ev => { if (ev.dataTransfer.types.includes("application/x-wfc-service")) { ev.preventDefault(); ev.dataTransfer.dropEffect = "copy"; } });
    wrap.addEventListener("drop", onDrop);
    document.addEventListener("keydown", ev => {
      const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName);
      if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === "z" && !typing) { ev.preventDefault(); ev.shiftKey ? redo() : undo(); }
      else if ((ev.key === "Delete" || ev.key === "Backspace") && !typing && state.selection) {
        ev.preventDefault();
        const s = state.selection;
        if (s.kind === "step") mutate(d => M.removeStep(d, s.name));
        else if (s.kind === "transition") mutate(d => { d.transitions = d.transitions.filter(x => !(x.from === s.from && x.to === s.to)); });
        else if (s.kind === "action") deleteAction(s.name);
      } else if (ev.key === "Escape") { closeModal(); select(null); }
    });
    window.addEventListener("resize", () => { if (!state.viewMoved) fit(); renderCanvas(); });
    $("modal").addEventListener("pointerdown", ev => { if (ev.target === $("modal")) closeModal(); });
  }

  function start() {
    wire();
    const draft = storage(s => JSON.parse(s.getItem(DRAFT_KEY) || "null"), null);
    if (draft && draft.meta && Array.isArray(draft.actions) && Array.isArray(draft.steps)) state.doc = draft;
    requestAnimationFrame(() => { fit(); render(); });
    loadData();
  }

  start();
})();
