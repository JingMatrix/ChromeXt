if (typeof ChromeXt == "undefined") {
  const ArrayKeys = Object.getOwnPropertyNames(Array.prototype);
  const EventTargetKeys = Object.getOwnPropertyNames(EventTarget.prototype);
  const ChromeXtTargetKeys = ["script", "command", "cspRule", "filter"];
  const unlock = Symbol("unlock");
  class SyncArray extends Array {
    #name;
    #sync;
    #freeze;
    #ChromeXt;
    #inited = false;
    #key = null;
    /** @param {ChromeXtTarget} key */
    set ChromeXt(key) {
      this.#key = key;
      setTimeout(() => {
        this.#key = null;
      }, 0);
    }

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
        Object.defineProperty(this, m, {
          configurable: false,
          value: (...args) => {
            return this.#verify(Array.from(args), m);
          },
        });
      });

      Object.defineProperty(this, "sync", {
        configurable: false,
        value: (data, ChromeXt = this.#key || this.#ChromeXt) => {
          this.#key = null;
          if (this.#sync && typeof this.#name == "string") {
            const payload = {
              origin: window.location.origin,
              name: this.#name,
            };
            if (typeof data == "object" && Array.isArray(data)) {
              if (this.#freeze) data = data.filter((it) => Object.isFrozen(it));
              if (data.length > 0) payload.data = data;
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
      if (this.#key instanceof ChromeXtTarget && !this.#key.isLocked()) {
        ChromeXt = this.#key;
      } else if (this.#ChromeXt.isLocked()) {
        throw new Error(`ChromeXt locked`);
      } else {
        ChromeXt = this.#ChromeXt;
      }
      this.#key = null;
      return ChromeXt;
    }

    #verify(args, method) {
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
    #scripts;
    #commands;
    #cspRules;
    #filters;
    constructor(debug, target) {
      if (typeof debug == "function" && target instanceof EventTarget) {
        this.#debug = debug;
        this.#target = target;
      } else {
        this.#scripts = new SyncArray(this, "scripts", false, true);
        this.#commands = new SyncArray(this, "commands", false, false);
        this.#cspRules = new SyncArray(this, "cspRules");
        this.#filters = new SyncArray(this, "filters");
        this.#target = new EventTarget();
        this.#debug = console.debug.bind(debug);
      }
      EventTargetKeys.forEach((m) => {
        Object.defineProperty(this, m, {
          configurable: false,
          value: (...args) => {
            return this.#target[m].apply(this.#target, Array.from(args));
          },
        });
      });
      ChromeXtTargetKeys.forEach((p) => {
        Object.defineProperty(this, p + "s", {
          configurable: false,
          set(v) {
            if (v.ChromeXt[unlock] == ChromeXt) {
              this.#factory(p, v);
              return true;
            } else {
              throw Error(`Illegal access to the setter of ${p}s`);
            }
          },
          get() {
            if (!this.isLocked()) {
              return this.#factory(p);
            } else {
              throw new Error("ChromeXt locked");
            }
          },
        });
      });
    }
    #factory(key, v) {
      let result;
      switch (key) {
        case "script":
          if (v) this.#scripts = v;
          result = this.#scripts;
          break;
        case "command":
          if (v) this.#commands = v;
          result = this.#commands;
          break;
        case "cspRule":
          if (v) this.#cspRules = v;
          result = this.#cspRules;
          break;
        case "filter":
          if (v) this.#filters = v;
          result = this.#filters;
          break;
      }
      return v || result instanceof SyncArray
        ? result
        : new Error(`Invalid field #${key}s`);
    }
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
        console.debug = (...args) => {
          args = Array.from(args);
          try {
            const data = JSON.parse(args.map((it) => it.toString()).join(" "));
            if ("action" in data) {
              data.blocked = true;
              args = [JSON.stringify(data)];
              console.warn("Block access to ChromeXt APIs");
            }
          } catch {}
          this.#debug(...args);
        };
      }
    }
    unlock(key, apiOnly = true) {
      if (!this.isLocked()) {
        return this;
      } else if (this.#key == key) {
        const UnLocked = new ChromeXtTarget(this.#debug, this.#target);
        if (!apiOnly) {
          UnLocked[unlock] = ChromeXt;
          ChromeXtTargetKeys.forEach((k) => {
            UnLocked[k + "s"] = new Proxy(this.#factory(k), {
              get(target, prop) {
                if (prop == "ChromeXt") return UnLocked;
                const value = target[prop];
                if (typeof value == "function") {
                  target.ChromeXt = UnLocked;
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
  if (cspRules.length == 0) return;
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
})();
// Kotlin separator

(() => {
  let filter = ChromeXt.filters.filter((item) => item.trim().length > 0);
  if (filter.length == 0) return;
  filter = filter.join(", ");
  window.addEventListener("DOMContentLoaded", () => {
    function GM_addStyle(css) {
      const style = document.createElement("style");
      style.setAttribute("type", "text/css");
      style.textContent = css;
      document.head.appendChild(style);
    }
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
    document
      .querySelectorAll("amp-ad,amp-embed,amp-sticky-ad")
      .forEach((node) => node.remove());
  });
})();
