// Built-in UserScripts manager, injected by ChromeXt into its internal manager page.
// It talks to ChromeXt with the same actions as the hosted front end.

(() => {
  const style = `
:root { color-scheme: light dark; --accent: #0d9488; --muted: #6b7280; --line: #d1d5db;
  --danger: #dc2626; --bg: #fff; --card: #f9fafb; }
@media (prefers-color-scheme: dark) {
  :root { --accent: #2dd4bf; --muted: #9ca3af; --line: #374151; --bg: #111827; --card: #1f2937; }
}
* { box-sizing: border-box; }
body { margin: 0; padding: 0 12px 48px; font: 16px/1.4 system-ui, sans-serif; background: var(--bg); }
h1 { text-align: center; font-size: 1.5em; font-weight: 600; margin: 24px 0 4px; }
h2 { font-size: 1.1em; font-weight: 600; margin: 28px 0 8px; color: var(--accent); }
p.hint { color: var(--muted); font-size: .9em; margin: 4px 0 16px; text-align: center; }
a { color: var(--accent); }
button { font: inherit; color: inherit; background: none; border: 1px solid var(--line);
  border-radius: 8px; padding: 6px 14px; }
button.primary { border-color: var(--accent); color: var(--accent); }
button.danger { border-color: var(--danger); color: var(--danger); }
.toolbar { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; margin: 12px 0; }
.script { display: flex; align-items: center; gap: 8px; padding: 10px 12px; margin: 6px 0;
  background: var(--card); border-radius: 10px; }
.script .name { flex: 1; overflow-wrap: anywhere; }
.script .ns { color: var(--muted); font-size: .8em; overflow-wrap: anywhere; }
.script .version { color: var(--muted); font-size: .8em; white-space: nowrap; }
.empty { color: var(--muted); text-align: center; margin: 24px 0; }
.field { margin: 14px 0; }
.field > label { display: block; font-weight: 600; margin-bottom: 4px; }
.row { display: flex; gap: 6px; margin: 4px 0; }
.row input, select { flex: 1; font: inherit; color: inherit; background: var(--card);
  border: 1px solid var(--line); border-radius: 6px; padding: 6px 8px; min-width: 0; }
.meta-title { font-size: 1.3em; text-align: center; margin: 20px 0 4px; overflow-wrap: anywhere; }
.meta-sub { text-align: center; color: var(--muted); font-size: .9em; }
.desc { color: var(--muted); margin: 12px 4px; }
.actions { display: flex; justify-content: space-between; gap: 8px; margin-top: 24px; }
.page { padding: 8px 0; border-bottom: 1px solid var(--line); }
.page .title { display: flex; justify-content: space-between; gap: 8px; }
.page .url { color: var(--muted); font-size: .8em; overflow-wrap: anywhere; overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
dialog { border: 1px solid var(--line); border-radius: 12px; background: var(--bg); color: inherit;
  max-width: 90vw; }
dialog::backdrop { background: rgb(0 0 0 / .5); }
dialog .actions { margin-top: 16px; }
`;

  const el = (tag, props = {}, ...children) => {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
      if (k.startsWith("on")) node.addEventListener(k.substring(2), v);
      else if (k == "class") node.className = v;
      else node[k] = v;
    }
    node.append(...children.filter((c) => c !== null && c !== undefined));
    return node;
  };

  class Metadata {
    static fields = ["name", "namespace", "author", "description", "version", "icon",
      "updateURL", "downloadURL", "supportURL", "homepage"];
    static lists = ["match", "include", "exclude", "grant"];

    constructor(text) {
      this.lines = [];
      this.values = {};
      this.match = [];
      this.include = [];
      this.exclude = [];
      this.grant = [];
      this.runAt = "";
      text.trim().split("\n").forEach((line) => {
        const meta = line.match(/^\/\/\s+@([\w-]+)\s+(.+?)\s*$/);
        if (meta == null) return this.lines.push(line);
        const [, key, value] = meta;
        if (Metadata.lists.includes(key)) this[key].push(value);
        else if (key == "run-at") this.runAt = value;
        else if (Metadata.fields.includes(key)) this.values[key] = value;
        else this.lines.push(line);
      });
      this.isValid = this.match.length + this.include.length > 0;
    }

    get id() {
      // Same rule as Parser.kt in ChromeXt
      const name = (this.values.name || "sample").replaceAll(":", "");
      return (this.values.namespace || "ChromeXt") + ":" + name;
    }

    get link() {
      const v = this.values;
      const url = v.homepage || v.supportURL || v.updateURL || v.downloadURL || v.namespace || "";
      return url.startsWith("http") ? url : null;
    }

    toString() {
      // Keep the unknown lines, including the ==UserScript== markers, around the known ones
      const lines = [...this.lines];
      const end = lines.pop();
      for (const key of Metadata.fields) {
        if (this.values[key]) lines.push(`// @${key}\t${this.values[key]}`);
      }
      if (this.runAt) lines.push(`// @run-at\t${this.runAt}`);
      for (const key of Metadata.lists) {
        this[key].filter((v) => v.trim() != "").forEach((v) => lines.push(`// @${key}\t${v.trim()}`));
      }
      lines.push(end);
      return lines.join("\n") + "\n";
    }
  }

  const state = { ids: [], metas: {}, pages: null };
  let root;

  function show(...nodes) {
    root.replaceChildren(...nodes.filter((n) => n !== null && n !== undefined));
  }

  function nameOf(id) {
    return id.split(":").pop();
  }

  function namespaceOf(id) {
    const parts = id.split(":");
    parts.pop();
    let ns = parts.join(":");
    if (ns.startsWith("http")) ns = ns.replace(/^https?:\/\//, "");
    return ns;
  }

  function confirmDialog(message, confirmLabel) {
    return new Promise((resolve) => {
      const dialog = el("dialog", {}, el("p", { textContent: message }));
      const close = (result) => {
        dialog.close();
        dialog.remove();
        resolve(result);
      };
      dialog.append(
        el("div", { class: "actions" },
          el("button", { textContent: "Cancel", onclick: () => close(false) }),
          el("button", { class: "danger", textContent: confirmLabel, onclick: () => close(true) })));
      document.body.append(dialog);
      dialog.showModal();
    });
  }

  function refresh() {
    ChromeXt.dispatch("userscript");
  }

  function requestMetas(ids) {
    if (ids.length > 0) ChromeXt.dispatch("userscript", { ids });
  }

  function installFromFiles(files) {
    Array.from(files).forEach((f) => {
      if (!f.name.endsWith(".js")) return alert(f.name + " is not a UserScript file.");
      f.text().then((code) => {
        ChromeXt.dispatch("installScript", code);
        setTimeout(refresh, 300);
      });
    });
  }

  function renderList() {
    const picker = el("input", { type: "file", accept: ".js,text/javascript", multiple: true,
      hidden: true, onchange: (e) => installFromFiles(e.target.files) });
    const list = el("div");
    if (state.ids.length == 0) {
      list.append(el("p", { class: "empty", textContent: "No UserScript installed." }));
    }
    state.ids.forEach((id) => {
      const meta = state.metas[id];
      const version = meta?.values.version;
      list.append(
        el("div", { class: "script" },
          el("div", { class: "name", onclick: () => renderDetails(id) },
            el("div", { textContent: meta?.values.name || nameOf(id) }),
            el("div", { class: "ns", textContent: namespaceOf(id) })),
          version ? el("span", { class: "version", textContent: "v" + version }) : null,
          el("button", { class: "danger", textContent: "Delete", onclick: () => remove(id) })));
    });
    show(
      el("h1", { textContent: "ChromeXt" }),
      el("p", { class: "hint", textContent: "UserScripts installed in this browser" }),
      el("div", { class: "toolbar" },
        el("button", { class: "primary", textContent: "Install from file",
          onclick: () => picker.click() }),
        el("button", { textContent: "Refresh", onclick: refresh }),
        el("button", { textContent: "Developer tools",
          onclick: () => ChromeXt.dispatch("inspectPages") })),
      picker,
      list,
      renderPages());
  }

  function renderPages() {
    if (state.pages == null) return null;
    const section = el("div", {}, el("h2", { textContent: "Developer tools" }));
    if (state.pages.length == 0) {
      section.append(el("p", { class: "empty",
        textContent: "Open pages in other tabs, then inspect them from here." }));
    }
    state.pages.forEach((p) => {
      section.append(
        el("div", { class: "page" },
          el("div", { class: "title" },
            el("span", { textContent: p.title }),
            el("a", { href: devToolsUrl(p), target: "_blank", textContent: "Inspect" })),
          el("div", { class: "url", textContent: p.url })));
    });
    return section;
  }

  function devToolsUrl(p) {
    const url = p.devtoolsFrontendUrl || "";
    if (url.startsWith("https://chrome-devtools-frontend.appspot.com/")) return url;
    // Chromium 120.0.6060.1, as used by the hosted front end
    const commit = "71a97c345b75e0862bf81b98d06f1edbef2b0d49";
    return `https://chrome-devtools-frontend.appspot.com/serve_internal_file/@${commit}/inspector.html?ws=/devtools/page/${p.id}`;
  }

  async function remove(id) {
    const ok = await confirmDialog(`Delete the UserScript "${nameOf(id)}"?`, "Delete");
    if (!ok) return;
    ChromeXt.dispatch("userscript", { ids: [id], delete: true });
    delete state.metas[id];
    state.ids = state.ids.filter((it) => it != id);
    renderList();
  }

  function listField(meta, key) {
    const box = el("div", { class: "field" }, el("label", { textContent: key }));
    const rows = el("div");
    const draw = () => {
      rows.replaceChildren(...meta[key].map((value, i) =>
        el("div", { class: "row" },
          el("input", { value, placeholder: "Add new item",
            oninput: (e) => (meta[key][i] = e.target.value) }),
          el("button", { textContent: "✕", onclick: () => { meta[key].splice(i, 1); draw(); } }))));
    };
    draw();
    box.append(rows, el("button", { textContent: "+ Add", onclick: () => { meta[key].push(""); draw(); } }));
    return box;
  }

  function renderDetails(id) {
    const meta = state.metas[id];
    if (meta == null || !meta.isValid) {
      show(
        el("p", { class: "empty",
          textContent: "No valid metadata found. Updating ChromeXt and reinstalling the script may help." }),
        el("div", { class: "toolbar" }, el("button", { textContent: "Back", onclick: renderList })));
      return;
    }
    const v = meta.values;
    const link = meta.link;
    const title = el("div", { class: "meta-title" },
      v.icon ? el("img", { src: v.icon, style: "width: 24px; height: 24px; object-fit: contain; vertical-align: middle; margin-right: 6px" }) : null,
      link ? el("a", { href: link, target: "_blank", textContent: v.name }) : v.name);
    const runAt = el("select", { onchange: (e) => (meta.runAt = e.target.value) },
      ...["document-start", "document-end", "document-idle"].map((it) =>
        el("option", { value: it, textContent: it, selected: (meta.runAt || "document-idle") == it })));
    show(
      title,
      el("div", { class: "meta-sub", textContent:
        [v.version && "v" + v.version, v.author && "by " + v.author].filter(Boolean).join(" · ") }),
      v.description ? el("p", { class: "desc", textContent: v.description }) : null,
      listField(meta, "match"),
      listField(meta, "include"),
      listField(meta, "exclude"),
      el("div", { class: "field" }, el("label", { textContent: "run-at" }), el("div", { class: "row" }, runAt)),
      el("div", { class: "actions" },
        el("button", { textContent: "Back", onclick: renderList }),
        el("button", { class: "danger", textContent: "Delete", onclick: () => remove(id) }),
        el("button", { class: "primary", textContent: "Save", onclick: () => save(id, meta) })));
  }

  function save(id, meta) {
    ChromeXt.dispatch("userscript", { id, meta: meta.toString() });
    // Re-read what ChromeXt stored, so the page never shows unsaved values
    setTimeout(() => {
      requestMetas([id]);
      renderList();
    }, 300);
  }

  function start() {
    document.head.append(el("style", { textContent: style }));
    root = el("main");
    document.body.append(root);

    ChromeXt.addEventListener("userscript", (e) => {
      if (e.detail.type != "init") return;
      state.ids = e.detail.ids.filter((id) => id.includes(":")).sort((a, b) =>
        nameOf(a).localeCompare(nameOf(b)));
      renderList();
      requestMetas(state.ids);
    });
    ChromeXt.addEventListener("script_meta", (e) => {
      e.detail.forEach((text) => {
        const meta = new Metadata(text);
        if (state.ids.includes(meta.id)) state.metas[meta.id] = meta;
      });
      if (root.querySelector(".meta-title") == null) renderList();
    });
    ChromeXt.addEventListener("inspect_pages", (e) => {
      state.pages = e.detail.filter((it) =>
        it.type == "page" &&
        !it.url.startsWith("https://chrome-devtools-frontend.appspot.com") &&
        !it.url.startsWith("data:text/html;chromext=manager") &&
        (it.description == "" || !JSON.parse(it.description).never_attached));
      renderList();
    });
    renderList();
    refresh();
  }

  if (document.readyState == "loading") document.addEventListener("DOMContentLoaded", start);
  else start();
})();
