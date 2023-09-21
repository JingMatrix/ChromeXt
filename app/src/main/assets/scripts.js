if (typeof Symbol.ChromeXt == "undefined") {
  const keys = {
    Array: Object.getOwnPropertyNames(Array.prototype),
    ChromeXt: ["commands", "cspRules", "filters", "scripts"],
    EventTarget: Object.getOwnPropertyNames(EventTarget.prototype),
    global: Object.keys(window),
    // Drop user-defined keys in the global context
  };

  keys.global.push(...keys.EventTarget);

  const backup = {
    // Store some variables to avoid being hooked
    Error: Error,
    Event: CustomEvent,
    confirm: confirm.bind(window),
    parse: JSON.parse.bind(JSON),
    replace: String.prototype.replace,
    stringify: JSON.stringify.bind(JSON),
    REGEX: /\n {4}[^\n]+/,
  };

  const cachedTypes = {
    // Caching polices created by trustedTypes
    polices: new Set(),
    bypass: false,
    Error: class extends TypeError {
      constructor(name) {
        const msg =
          "Failed to execute 'createHTML' on 'TrustedTypePolicy': " +
          `Policy ${name}'s TrustedTypePolicyOptions did not specify a 'createHTML' member.`;
        super(msg);
        this.stack = backup.replace.apply(this.stack, [backup.REGEX, ""]);
      }
    },
  };

  trustedTypes.createPolicy = new Proxy(trustedTypes.createPolicy, {
    apply(target, thisArg, args) {
      const policyOptions = args[1] || {};
      const original = policyOptions.createHTML;
      function createHTML() {
        if (cachedTypes.bypass) {
          return arguments[0];
        } else {
          if (typeof original == "function") {
            return original.apply(policyOptions, arguments);
          } else {
            throw new cachedTypes.Error(args[0]);
          }
        }
      }

      args[1] = new Proxy(policyOptions, {
        get(_target, prop) {
          if (prop == "createHTML") return createHTML;
          return Reflect.get(...arguments);
        },
      });
      const result = target.apply(thisArg, args);
      cachedTypes.polices.add(result);
      return result;
    },
  });

  class SyncArray extends Array {
    #freeze;
    #key = null;
    #name;
    #sync;

    constructor(name, sync = true, freeze = false) {
      super();
      this.#name = name;
      this.#sync = sync;
      this.#freeze = freeze;

      keys.Array.forEach((m) => {
        if (typeof this[m] != "function") return;
        Object.defineProperty(this, m, {
          value: (...args) => {
            return this.#verify(args, m);
          },
        });
      });

      Object.defineProperty(this, "sync", {
        value: (data = this, ChromeXt = this.#key || ChromeXt) => {
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
            throw new backup.Error(`Element ${args[i]} is not frozen`);
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

  const trustedDomains = ["greasyfork.org", "raw.githubusercontent.com"];
  // Verified sources of UserScripts

  let unlock;
  const secure = Symbol("secure");
  // Symbols to store credentials

  class ChromeXtTarget {
    #debug;
    #key = null;
    #security;
    #target;

    #commands;
    #cspRules;
    #filters;
    #scripts;

    constructor(debug, target, security) {
      if (typeof debug == "function" && target instanceof EventTarget) {
        this.#debug = debug;
        this.#target = target;
      } else {
        this.#target = new EventTarget();
        this.#debug = console.debug.bind(console);
      }

      this.#check(security);

      keys.EventTarget.forEach((m) => {
        Object.defineProperty(this, m, {
          value: (...args) => {
            if (this.isLocked()) throw new backup.Error("ChromeXt locked");
            return this.#target[m].apply(this.#target, args);
          },
        });
      });

      keys.ChromeXt.forEach((p) => {
        const sync = p != "scripts" && p != "commands";
        const v = new SyncArray(p, sync, p == "scripts");
        this.#factory(p, v);
        Object.defineProperty(this, p, {
          set(v) {
            if (typeof unlock == "symbol" && v.ChromeXt[unlock] == ChromeXt) {
              this.#factory(p, v);
              return true;
            }
            throw backup.Error(`Illegal access to the setter of ${p}`);
          },
          get() {
            if (!this.isLocked()) return this.#factory(p);
            return [];
          },
        });
      });
    }

    get globalKeys() {
      return keys.global;
    }
    get trustedTypes() {
      return cachedTypes;
    }

    /** @param {any | Error} prop */
    set security(prop) {
      if (this.#security == secure) return;
      if (prop != secure && this.#security instanceof backup.Error) return;

      if (prop == secure) {
        if (typeof backup.debug == "function") console.debug = backup.debug;
      } else if (prop instanceof backup.Error) {
        this.dispatch("block");
        console.warn(
          `Url ${location.href} is not verified for`,
          `ChromeXt security level ${this.#security} due to`,
          prop
        );
      } else if (prop != undefined) {
        this.#patchConsole();
      }

      this.#security = prop || secure;
    }

    #check(security) {
      if (
        security != secure &&
        location.protocol.startsWith("http") &&
        location.pathname.endsWith(".user.js") &&
        typeof installScript == "function" &&
        !trustedDomains.includes(location.hostname)
      ) {
        this.security = "userscript";

        fetch(location.href, { cache: "only-if-cached", mode: "same-origin" })
          .then((res) => {
            const type = res.headers.get("Content-Type").trim();
            if (
              type.startsWith("text/javascript") ||
              type.startsWith("text/plain")
            ) {
              this.security = secure;
            } else {
              throw TypeError(`Incompatible content-type: ${type}`);
            }
          })
          .catch((e) => (this.security = e));
      } else {
        this.security = security;
      }
    }

    #confirmAction(action) {
      const msg = [
        "Current environment is not verified by ChromeXt.",
        `Please confirm (each time) to trust current page for the action: ${action}.`,
        "",
        "See details in https://github.com/JingMatrix/ChromeXt/issues/100.",
      ];
      return backup.confirm(msg.join("\n"));
    }

    #factory(p, v) {
      // Set or get private properties
      switch (p) {
        case "commands":
          if (v) this.#commands = v;
          return this.#commands;
        case "cspRules":
          if (v) this.#cspRules = v;
          return this.#cspRules;
        case "filters":
          if (v) this.#filters = v;
          return this.#filters;
        case "scripts":
          if (v) this.#scripts = v;
          return this.#scripts;
      }
      throw new backup.Error(`Invalid field #${key}`);
    }

    #patchConsole() {
      if (this.#security == secure || backup.debug !== undefined) return;
      const parse = backup.parse;
      backup.debug = console.debug;
      console.debug = new Proxy(this.#debug, {
        apply(_target, _this, argumentsList) {
          try {
            const data = parse(argumentsList.join(""));
            if ("action" in data) console.warn("Attacks to ChromeXt defended");
          } catch {}
          Reflect.apply(...arguments);
        },
      });
    }

    dispatch(action, payload, key) {
      if (action != "block" && this.#security != secure) {
        const error = new backup.Error(
          `ChromeXt called with security level: ${this.#security}`,
          { cause: this.#security }
        );
        if (this.#security instanceof backup.Error) throw error;
        if (!this.#confirmAction(action)) throw error;
      }
      if (this.isLocked() && key != this.#key)
        throw new backup.Error("ChromeXt locked");
      if (typeof unlock == "symbol") key = Number(unlock.description);
      // Kotlin anchor
      this.#debug(backup.stringify({ action, payload, key }));
    }
    isLocked() {
      return this.#key != null;
    }
    lock(key, name) {
      if (
        !this.isLocked() &&
        name.length > 16 &&
        typeof key == "number" &&
        typeof unlock != "symbol"
      ) {
        this.#key = key;
        unlock = Symbol(key);
        delete Symbol.ChromeXt;
        Symbol = new Proxy(Symbol, {
          get(_target, prop) {
            if (prop == name) {
              return ChromeXt;
            } else {
              return Reflect.get(...arguments);
            }
          },
        });
        if (typeof userDefinedChromeXt != "undefined") {
          Symbol.ChromeXt = userDefinedChromeXt;
        }
        this.security = secure;
      }
    }
    post(event, detail) {
      this.dispatchEvent(new backup.Event(event, { detail }));
    }
    unlock(key, apiOnly = true) {
      if (!this.isLocked()) {
        if (!["content://", "file://"].includes(location.origin))
          throw backup.Error("ChromeXt is not locked");
        return this;
      }
      if (this.#key == key) {
        const UnLocked = new ChromeXtTarget(
          this.#debug,
          this.#target,
          this.#security
        );
        if (!apiOnly) {
          UnLocked[unlock] = ChromeXt;
          keys.ChromeXt.forEach((k) => {
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
        throw new backup.Error("Fail to unlock ChromeXtTarget");
      }
    }
  }

  const ChromeXt = new ChromeXtTarget();
  const userDefinedChromeXt = Symbol.ChromeXt;
  Object.freeze(ChromeXt);
  Symbol.ChromeXt = ChromeXt;
} else {
  throw Error("ChromeXt is already defined, cancel initialization");
}
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
  Symbol.ChromeXt.unlock(ChromeXtUnlockKeyForEruda).dispatch("loadEruda");
}
// Kotlin separator

if (Symbol.ChromeXt.cspRules.length > 0) {
  Symbol.ChromeXt.cspRules.forEach((rule) => {
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

if (Symbol.ChromeXt.filters.length > 0) {
  const filter = Symbol.ChromeXt.filters.join(", ");
  let GM_addStyle = (css) => {
    const style = document.createElement("style");
    style.textContent = css;
    if (document.head) {
      document.head.appendChild(style);
    } else {
      setTimeout(() => document.head.appendChild(style));
    }
  };
  GM_addStyle(filter + " {display: none !important;}");
  window.addEventListener("load", () => {
    document.querySelectorAll(filter).forEach((node) => {
      node.hidden = true;
      node.style.display = "none";
    });
  });
  const amp = "amp-ad,amp-embed,amp-sticky-ad,amp-analytics,amp-auto-ads";
  document.querySelectorAll(amp).forEach((node) => node.remove());
}
