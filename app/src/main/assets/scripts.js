if (typeof ChromeXt == "undefined") {
  const ArrayKeys = Object.getOwnPropertyNames(Array.prototype);
  const EventTargetKeys = Object.getOwnPropertyNames(EventTarget.prototype);
  class SyncArray extends Array {
    #name;
    #sync;
    #freeze;
    #ChromeXt;
    #inited = false;
    constructor(ChromeXt, name, sync = true, freeze = false) {
      if (
        ChromeXt instanceof ChromeXtTarget &&
        typeof name == "string" &&
        typeof sync == "boolean" &&
        typeof freeze == "boolean"
      ) {
        super();
        this.#ChromeXt = ChromeXt;
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
      ArrayKeys.forEach((m) => {
        if (typeof this[m] != "function") return;
        const self = this;
        Object.defineProperty(self, m, {
          configurable: false,
          value: function () {
            return self.#verify(arguments, m);
          },
        });
      });

      Object.defineProperty(this, "sync", {
        configurable: false,
        value: (data, ChromeXt = this.#ChromeXt) => {
          if (this.#sync && typeof this.#name == "string") {
            const payload = {
              origin: window.location.origin,
              name: this.#name,
            };
            if (typeof data == "object" && Array.isArray(data)) {
              if (this.#freeze) data = data.filter((it) => Object.isFrozen(it));
              if (this.length > 0) payload.data = data;
            }
            ChromeXt.dispatch("syncData", payload);
          }
        },
      });
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
        this.#inited = true;
      }
    }

    #getChromeXt() {
      let ChromeXt;
      if (
        this.ChromeXt instanceof ChromeXtTarget &&
        !this.ChromeXt.isLocked()
      ) {
        // Temporarily unlock using proxy, might leak the ChromeXt object by listening at the value of this.ChromeXt
        ChromeXt = this.ChromeXt;
        delete this.ChromeXt;
      } else if (this.#ChromeXt.isLocked()) {
        throw new Error(`ChromeXt is locked`);
      } else {
        ChromeXt = this.#ChromeXt;
      }
      return ChromeXt;
    }

    #verify(args, method) {
      args = Array.from(args);
      const ChromeXt = this.#getChromeXt(args);
      if (this.#freeze) {
        let n = 0;
        if (method == "push") {
          n = args.length;
        } else if (method == "fill") {
          n = 1;
        }
        for (let i = 0; i < n; i++) {
          if (!Object.isFrozen(args[i]))
            throw new Error(`Argument ${args[i]} is not frozen`);
        }
      }
      const result = super[method].apply(this, args);
      if (["pop", "push", "fill", "splice"].includes(method))
        this.sync(this, ChromeXt);
      return result;
    }
  }
  class ChromeXtTarget {
    #debug;
    #key = null;
    #target;
    constructor(debug, target) {
      if (typeof debug == "function" && target instanceof EventTarget) {
        this.#debug = debug;
        this.#target = target;
      } else {
        this.#target = new EventTarget();
        this.#debug = console.debug.bind(debug);
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
      EventTargetKeys.forEach((m) => {
        const self = this;
        Object.defineProperty(self, m, {
          configurable: false,
          value: function () {
            return this.#target[m].apply(this.#target, Array.from(arguments));
          },
        });
      });
    }
    scripts = new SyncArray(this, "scripts", false, true);
    commands = new SyncArray(this, "commands", false, false);
    cspRules = new SyncArray(this, "cspRules");
    filters = new SyncArray(this, "filters");
    post(event, detail, key = null) {
      if (key != this.#key) throw new Error("ChromeXt locked");
      this.dispatchEvent(new CustomEvent(event, { detail }));
    }
    dispatch(action, payload, key = null) {
      if (key != this.#key) throw new Error("ChromeXt locked");
      this.#debug(JSON.stringify({ action, payload }));
    }
    isLocked() {
      return this.#key != null;
    }
    lock(key) {
      if (!this.isLocked() && typeof key == "number") {
        this.#key = key;
      }
    }
    unlock(key, eventOnly = true) {
      if (!this.isLocked()) {
        return this;
      } else if (this.#key == key) {
        const UnLocked = new ChromeXtTarget(this.#debug, this.#target);
        if (!eventOnly) {
          Object.keys(UnLocked).forEach((k) => {
            if (UnLocked[k] instanceof SyncArray)
              UnLocked[k] = new Proxy(this[k], {
                get(target, prop) {
                  const value = target[prop];
                  if (typeof value == "function") {
                    target.ChromeXt = UnLocked;
                    setTimeout(() => {
                      delete target.ChromeXt;
                    }, 0);
                  }
                  return Reflect.get(...arguments);
                },
              });
          });
        }
        return UnLocked;
      } else {
        throw new Error("Fail to unlock ChromeXtTarget");
      }
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
  ChromeXt.unlock(ChromeXtUnlockKeyForEruda).dispatch("loadEruda");
}
// Kotlin separator

(() => {
  const cspRules = ChromeXt.cspRules;
  if (cspRules.length > 0) {
    cspRules.forEach((rule) => {
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
})();
// Kotlin separator

(() => {
  const tmp_filter = ChromeXt.filters
    .filter((item) => item.trim().length > 0)
    .join(", ");
  window.addEventListener("DOMContentLoaded", () => {
    function GM_addStyle(css) {
      const style = document.createElement("style");
      style.setAttribute("type", "text/css");
      style.textContent = css;
      document.head.appendChild(style);
    }
    try {
      GM_addStyle(tmp_filter + " {display: none !important;}");
    } finally {
      window.addEventListener("load", () => {
        document.querySelectorAll(tmp_filter).forEach((node) => {
          node.hidden = true;
          node.style.display = "none";
        });
      });
    }
    document
      .querySelectorAll("amp-ad,amp-embed,amp-sticky-ad")
      .forEach((node) => node.remove());
  });
})();
