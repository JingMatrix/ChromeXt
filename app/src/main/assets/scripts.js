if (typeof ChromeXt == "undefined") {
  const ArrayKeys = Object.getOwnPropertyNames(Array.prototype);
  const EventTargetKeys = Object.getOwnPropertyNames(EventTarget.prototype);
  const ChromeXtTargetKeys = ["scripts", "commands", "cspRules", "filters"];
  let unlock;

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
              data = [...new Set(data)];
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

    #verify(args, method) {
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
      if (
        this.#key instanceof ChromeXtTarget &&
        !this.#key.isLocked() &&
        ["pop", "push", "fill", "splice"].includes(method)
      )
        this.sync();
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
        this.#debug = console.debug.bind(console);
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
            if (typeof unlock == "symbol" && v.ChromeXt[unlock] == ChromeXt) {
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
    dispatch(action, payload, key) {
      if (this.isLocked() && key != this.#key)
        throw new Error("ChromeXt locked");
      if (typeof unlock == "symbol") key = Number(unlock.description);
      // Kotlin anchor
      this.#debug(JSON.stringify({ action, payload, key }));
    }
    isLocked() {
      return this.#key != null;
    }
    lock(key) {
      if (
        !this.isLocked() &&
        typeof key == "number" &&
        typeof unlock != "symbol"
      ) {
        this.#key = key;
        unlock = Symbol(key);
      }
    }
    unlock(key, apiOnly = true) {
      if (!this.isLocked()) {
        if (window.location.origin !== "content://")
          throw Error("ChromeXt is not locked");
        return this;
      }
      if (this.#key == key) {
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
} else {
  throw Error("ChromeXt is already defined");
}

trustedTypes.polices = new Set();
trustedTypes.createPolicy = new Proxy(trustedTypes.createPolicy, {
  apply(target, thisArg, args) {
    const createHTML = args[1].createHTML;
    args[1].createHTML = function () {
      if (this.bypass == true) {
        return arguments[0];
      } else {
        return createHTML.apply(this, arguments);
      }
    };
    const result = target.apply(thisArg, args);
    trustedTypes.polices.add(result);
    return result;
  },
});
// Kotlin separator

try {
  if (eruda._isInit) {
    eruda.hide();
    eruda.destroy();
  } else {
    eruda.init();
    eruda.show();
  }
} catch (e) {
  if (typeof define == "function") define.amd = false;
  ChromeXt.unlock(ChromeXtUnlockKeyForEruda).dispatch("loadEruda");
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

if (ChromeXt.filters.length > 0) {
  const filter = ChromeXt.filters.join(", ");
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
      .querySelectorAll(
        "amp-ad,amp-embed,amp-sticky-ad,amp-analytics,amp-auto-ads"
      )
      .forEach((node) => node.remove());
  });
}
