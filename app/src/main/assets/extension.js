extension.tabUrl == extension.tabUrl ||
  "https://jingmatrix.github.io/ChromeXt/";
const StubChrome = extension.tabUrl + "StubChrome.js";

const imports = await import(StubChrome);
globalThis.chrome = imports.chrome;

class ChromeEvent extends imports.StubEvent {
  #listeners = [];
  constructor(target, event) {
    super();
    this.target = target;
    this.event = event;
  }
  addListener(f) {
    const l = (e) => f(e.detail);
    this.#listeners.push([f, l]);
    this.target.addEventListener(this.event, l);
  }
  removeListener(f) {
    const index = this.#listeners.findIndex((i) => i[0] == f);
    if (index != -1) {
      const l = this.#listeners[index][1];
      this.#listeners.splice(index, 1);
      this.target.removeEventListener(this.event, l);
    }
  }
  hasListener(f) {
    const index = this.#listeners.findIndex((i) => i[0] == f);
    return index != -1;
  }
  dispatch(detail) {
    this.target.dispatchEvent(new CustomEvent(this.event, { detail }));
  }
}

Object.keys(chrome).forEach((key) => {
  const domain = chrome[key];
  const keys = Object.keys(domain);
  const events = keys
    .filter((k) => domain[k].__proto__.constructor == imports.StubEvent)
    .map((k) => k.substring(2));
  const properties = keys
    .filter((k) => k.startsWith("set"))
    .map((k) => k.substring(3));
  if (events.length > 0) {
    chrome[key] = new EventTarget();
    const t = chrome[key];
    Object.assign(t, domain);
    events.forEach((e) => {
      t["on" + e] = new ChromeEvent(t, e);
    });
  }
  if (properties.length > 0) {
    chrome[key]._props = new Map();
    const props = chrome[key]._props;
    properties.forEach((k) => {
      chrome[key]["set" + k] = (v) => props.set(k, v);
      chrome[key]["get" + k] = () => props.get(k);
    });
  }
});

chrome.runtime.getManifest = () => extension;
chrome.runtime.id = extension.id;
chrome.runtime.getURL = (path) => location.origin + "/" + path;

async function fetch_locale(locales) {
  while (locales.length > 0) {
    const locale = locales.pop();
    try {
      const res = await fetch("/_locales/" + locale + "/messages.json");
      chrome.i18n.locale = locale;
      chrome.i18n.messages = await res.json();
      break;
    } catch {}
  }
}
await fetch_locale([
  extension.default_locale,
  navigator.language.substring(0, 2),
  navigator.language,
]);

chrome.i18n.getMessage = (name) => chrome.i18n.messages[name] || "";

// Restore the original HTML elements
const parser = new DOMParser();
const doc = parser.parseFromString(extension.html, "text/html");
const inFrame = typeof Object.ChromeXt == "undefined";
document.head.remove();
document.documentElement.prepend(doc.head);
doc.querySelectorAll("script").forEach((node) => {
  const script = document.createElement("script");
  script.src = node.src;
  if (typeof node.type == "string") {
    script.type = node.type;
  }
  document.body.append(script);
});
