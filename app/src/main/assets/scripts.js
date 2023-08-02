if (typeof ChromeXt == "undefined") {
  class SyncArray extends Array {
    #name;
    #sync;
    #freeze;
    #inited = false;
    constructor(name, sync = true, freeze = false) {
      if (
        typeof name == "string" &&
        typeof sync == "boolean" &&
        typeof freeze == "boolean"
      ) {
        super();
        this.#name = name;
        this.#sync = sync;
        this.#freeze = freeze;
        this.#inited = this.#freeze;
      } else {
        super(...arguments);
        this.#inited = true;
        this.#sync = false;
        this.#freeze = false;
      }
    }

    init(value) {
      if (this.#inited || this.length > 0) {
        throw new Error(
          `Array ${this.#name} is already initialized with elements`
        );
      } else {
        if (typeof value == "string") {
          value == JSON.parse(value);
        }
        super.push(...value);
      }
    }

    sync(data) {
      if (this.#sync && typeof this.#name == "string") {
        const payload = { origin: window.location.origin, name: this.#name };
        if (typeof data == "object" && Array.isArray(data)) {
          payload.data = this.#freeze
            ? data.filter((it) => Object.isFrozen(it))
            : data;
        } else if (this.length > 0) {
          payload.data = new Array(...this);
        }
        ChromeXt.dispatch("syncData", payload);
      }
    }

    pop() {
      super.pop.apply(this, arguments);
      this.sync();
    }
    push() {
      const args = this.#freeze
        ? Array.from(arguments).filter((it) => Object.isFrozen(it))
        : arguments;
      super.push.apply(this, args);
      this.sync();
    }
    fill() {
      if (
        this.#freeze &&
        arguments.length > 1 &&
        !Object.isFrozen(arguments[0])
      )
        return;
      super.fill.apply(this, arguments);
      this.sync();
    }
    splice() {
      super.splice.apply(this, arguments);
      this.sync();
    }
  }
  class ChromeXtTarget extends EventTarget {
    #debug;
    constructor() {
      super();
      this.#debug = console.debug.bind(console);
      const self = this;
      console.debug = function () {
        let args = Array.from(arguments);
        try {
          const data = JSON.parse(args.map((it) => it.toString()).join(" "));
          if ("action" in data) {
            data.blocked = true;
            args = [JSON.stringify(data)];
            console.warn("Block access to ChromeXt APIs");
          }
        } catch {}
        self.#debug(...args);
      };
    }
    scripts = new SyncArray("scripts", false, true);
    commands = new SyncArray("commands", false, false);
    cspRules = new SyncArray("cspRules");
    filters = new SyncArray("filters");
    post(event, detail) {
      this.dispatchEvent(new CustomEvent(event, { detail }));
    }
    dispatch(action, payload) {
      this.#debug(JSON.stringify({ action, payload }));
    }
  }
  Object.defineProperty(globalThis, "ChromeXt", {
    value: new ChromeXtTarget(),
    enumerable: true,
  });
  Object.freeze(ChromeXt);
}
// Kotlin separator

try {
  if (eruda._isInit) {
    eruda.hide();
    eruda.destroy();
  } else {
    eruda.init();
    eruda._localConfig();
    eruda.show();
  }
} catch (e) {
  ChromeXt.dispatch("loadEruda");
}
// Kotlin separator

if (ChromeXt.cspRules.length > 0) {
  ChromeXt.cspRules.forEach((rule) => {
    if (rule.length == 0) return;
    // Skip empty cspRules
    const meta = document.createElement("meta");
    meta.setAttribute("http-equiv", "Content-Security-Policy");
    meta.setAttribute("content", rule);
    try {
      document.head.append(meta);
    } catch {
      setTimeout(() => {
        document.head.append(meta);
      }, 0);
    }
  });
}
// Kotlin separator

window.addEventListener("DOMContentLoaded", () => {
  function GM_addStyle(css) {
    const style = document.createElement("style");
    style.setAttribute("type", "text/css");
    style.textContent = css;
    document.head.appendChild(style);
  }

  if (ChromeXt.filters.length > 0) {
    filter = ChromeXt.filters
      .filter((item) => item.trim().length > 0)
      .join(", ");
    try {
      GM_addStyle(filter + " {display: none !important;}");
    } finally {
      window.addEventListener("load", () => {
        document.querySelectorAll(filter).forEach((node) => {
          node.hidden = true;
          node.style.display = "none";
        });
      });
    }
  }

  document
    .querySelectorAll("amp-ad,amp-embed,amp-sticky-ad")
    .forEach((node) => node.remove());
});
// Kotlin separator
