if (typeof ChromeXt == "undefined") {
  const ArrayKeys = Object.getOwnPropertyNames(Array.prototype);
  const EventTargetKeys = Object.getOwnPropertyNames(EventTarget.prototype);
  const ChromeXtTargetKeys = ["scripts", "commands", "cspRules", "filters"];
  const unlock = Symbol("unlock");

  class SyncArray extends Array {
    #name;
    #sync;
    #freeze;
    #key = null;

    constructor(name, sync = true, freeze = false) {
      super();
      this.#name = name;
      this.#sync = sync;
      this.#freeze = freeze;

      ArrayKeys.forEach((m) => {
        if (typeof this[m] != "function") return;
        Object.defineProperty(this, m, {
          value: (...args) => {
            return this.#verify(args, m);
          },
        });
      });

      Object.defineProperty(this, "sync", {
        value: (data = this, ChromeXt = this.#key || globalThis.ChromeXt) => {
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

    /** @param {ChromeXtTarget} key */
    set ChromeXt(key) {
      this.#key = key;
      setTimeout(() => {
        this.#key = null;
      });
    }

    #checkChromeXt() {
      if (this.#key instanceof ChromeXtTarget && !this.#key.isLocked()) {
        return true;
      } else if (globalThis.ChromeXt.isLocked()) {
        throw new Error(`ChromeXt locked`);
      } else {
        return true;
      }
    }

    #verify(args, method) {
      this.#checkChromeXt(args);
      if (this.#freeze) {
        let n = 0;
        if (method == "push") {
          n = args.length;
        } else if (method == "fill") {
          n = 1;
        }
        for (let i = 0; i < n; i++) {
          if (!Object.isFrozen(args[i]))
            throw new Error(`Element ${args[i]} is not frozen`);
        }
      }
      const result = super[method].apply(this, args);
      if (["pop", "push", "fill", "splice"].includes(method)) this.sync();
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
        this.#target = new EventTarget();
        this.#debug = console.debug.bind(debug);
      }
      EventTargetKeys.forEach((m) => {
        Object.defineProperty(this, m, {
          value: (...args) => {
            if (this.isLocked()) throw new Error("ChromeXt locked");
            return this.#target[m].apply(this.#target, args);
          },
        });
      });
      ChromeXtTargetKeys.forEach((p) => {
        const v = new SyncArray(
          p,
          p != "scripts" && p != "commands",
          p == "scripts"
        );
        this.#factory(p, v);
        Object.defineProperty(this, p, {
          set(v) {
            if (v.ChromeXt[unlock] == ChromeXt) {
              this.#factory(p, v);
              return true;
            } else {
              throw Error(`Illegal access to the setter of ${p}`);
            }
          },
          get() {
            if (!this.isLocked()) {
              return this.#factory(p);
            } else {
              return [];
            }
          },
        });
      });
    }
    #factory(p, v) {
      // Set or get private properties
      switch (p) {
        case "scripts":
          if (v) this.#scripts = v;
          return this.#scripts;
        case "commands":
          if (v) this.#commands = v;
          return this.#commands;
        case "cspRules":
          if (v) this.#cspRules = v;
          return this.#cspRules;
        case "filters":
          if (v) this.#filters = v;
          return this.#filters;
      }
      throw new Error(`Invalid field #${key}`);
    }
    post(event, detail) {
      this.dispatchEvent(new CustomEvent(event, { detail }));
    }
    dispatch(action, payload, key = null) {
      if (key != this.#key) throw new Error("ChromeXt locked");
      // Kotlin anchor
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
      if (this.#key == key || !this.isLocked()) {
        const UnLocked = new ChromeXtTarget(this.#debug, this.#target);
        if (!apiOnly) {
          UnLocked[unlock] = ChromeXt;
          ChromeXtTargetKeys.forEach((k) => {
            UnLocked[k] = new Proxy(this.#factory(k), {
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
