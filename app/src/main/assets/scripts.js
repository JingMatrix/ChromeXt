if (typeof Symbol.ChromeXt == "undefined") {
  const initKey = ChromeXtUnlockKeyForInit;
  // Used to lock and unlock ChromeXt;

  const props = {
    Array: Object.getOwnPropertyNames(Array.prototype),
    ChromeXt: ["commands", "cspRules", "filters", "scripts"],
    EventTarget: Object.getOwnPropertyNames(EventTarget.prototype),
    global: Object.keys(window),
    // Drop user-defined props in the global context
  };

  props.EventTarget.pop(); // Remove the prop `constructor`
  props.global.push(...props.EventTarget);

  const backup = {
    // Store some variables to avoid being hooked
    Error: Error,
    Event: CustomEvent,
    confirm: confirm.bind(window),
    parse: JSON.parse.bind(JSON),
    stringify: JSON.stringify.bind(JSON),
    setTimeout: setTimeout.bind(window),
  };

  const SyncMethods = ["fill", "pop", "push", "splice"];
  class SyncArray extends Array {
    #freeze;
    #ChromeXt = null; // Used to validate the sync method
    #name;
    #sync;

    constructor(name, sync = true, freeze = false) {
      super();
      this.#name = name;
      this.#sync = sync;
      this.#freeze = freeze;

      const toVerify = ["fill", "push"];
      const hook = (args, method) => {
        if (toVerify.includes(method) && this.#freeze) {
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
        const result = super[method](...args);
        ChromeXt.isLocked() && this.sync();
        return result;
      };

      this.proxy = (target, prop) => {
        // Getter for methods of super
        const value = target[prop];
        if (SyncMethods.includes(prop) && typeof value == "function") {
          return (...args) => hook(args, prop);
        } else {
          return value;
        }
      };
    }

    sync(data = this) {
      const LocalChromeXt = this.#ChromeXt || ChromeXt;
      LocalChromeXt.isLocked(true);
      this.#ChromeXt = null; // Must be re-validate each time
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
        LocalChromeXt.dispatch("syncData", payload);
      }
    }

    /** @param {ChromeXtTarget} target */
    set ChromeXt(target) {
      this.#ChromeXt = target;
      backup.setTimeout(() => (this.#ChromeXt = null));
    }
  }

  const trustedDomains = ["greasyfork.org", "raw.githubusercontent.com"];
  // Verified sources of UserScripts

  let secure = Symbol("secure"); // Secure states of ChromeXt context

  class ChromeXtTarget {
    #debug;
    #locked; // Whether ChromeXt is available
    #security; // State of ChromeXt context
    #target;

    #store = {}; // SyncArrays with names in props.ChromeXt

    constructor(security, debug, target) {
      if (typeof debug == "function" && target instanceof EventTarget) {
        this.#debug = debug;
        this.#target = target;
      } else {
        this.#target = new EventTarget();
        this.#debug = console.debug.bind(console);
      }

      this.#check(security);

      props.EventTarget.forEach((m) => {
        const method = this.#target[m].bind(this.#target);
        this[m] = (...args) => {
          if (!this.isLocked(true)) return method(...args);
        };
      });

      if (secure.description == "secure" && this.#security == secure) {
        props.ChromeXt.forEach((p) => {
          const sync = p != "scripts" && p != "commands";
          const v = new SyncArray(p, sync, p == "scripts");
          this.#store[p] = v;
          this[p] = new Proxy(v, { get: v.proxy });
          delete v.proxy;
        });
      }
    }

    get globalKeys() {
      return props.global;
    }

    /** @param {any | Error} prop */
    set security(prop) {
      if (this.#security == secure) return;
      if (prop != secure && this.#security instanceof backup.Error) return;

      if (prop == secure) {
        if (typeof backup.debug == "function") console.debug = backup.debug;
        delete backup.debug;
      } else if (prop instanceof backup.Error) {
        this.dispatch("block");
        console.warn(
          `Url ${location.href} is not verified for`,
          `ChromeXt security level ${this.#security} due to`,
          prop
        );
      } else {
        this.#patchConsole();
      }

      this.#security = prop;
    }

    #check(security) {
      if (security != secure) throw backup.Error("Invalid constructor");
      // Block access to the ChromeXtTarget constructor from outside
      if (
        secure.description != "verified" &&
        location.protocol.startsWith("http") &&
        location.pathname.endsWith(".user.js") &&
        typeof installScript == "function" &&
        !trustedDomains.includes(location.hostname)
      ) {
        this.security = "userscript";
        // Not a secure context since the page might not be a UserScript

        fetch(location.href, { cache: "only-if-cached", mode: "same-origin" })
          // Local pages are always cached before shown
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

    #patchConsole() {
      if (this.#security == secure || backup.debug !== undefined) return;
      backup.debug = console.debug;
      console.debug = new Proxy(this.#debug, {
        apply(_target, _this, args) {
          try {
            const data = backup.parse(args.join(""));
            if ("action" in data) console.warn("Attacks to ChromeXt defended");
          } catch {}
          return Reflect.apply(...arguments);
        },
      });
    }

    dispatch(action, payload) {
      this.isLocked(true);
      if (action != "block" && this.#security != secure) {
        const error = new backup.Error(
          `ChromeXt called with security level: ${this.#security}`,
          { cause: this.#security }
        );
        if (this.#security instanceof backup.Error) throw error;
        if (!this.#confirmAction(action)) throw error;
      }
      // Kotlin anchor
      this.#debug(backup.stringify({ action, payload, key: initKey }));
    }
    isLocked(throwError = false) {
      const locked = this.#locked === true && secure.description == "verified";
      if (throwError && locked) throw new backup.Error("ChromeXt locked");
      return locked;
    }
    lock(token, name) {
      if (secure.description == "verified")
        throw backup.Error("ChromeXt was already locked once before");
      if (!this.isLocked() && name.length > 16 && token === initKey) {
        this.#locked = true;
        secure = Symbol("verified"); // Context is verified by the provided token
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
      if (!this.isLocked(true))
        this.dispatchEvent(new backup.Event(event, { detail }));
    }
    unlock(token, apiOnly = true) {
      if (!this.isLocked()) {
        if (!["content://", "file://"].includes(location.origin))
          throw backup.Error("ChromeXt is not locked");
        return this;
      }
      if (token == initKey) {
        const UnLocked = new ChromeXtTarget(
          this.#security,
          this.#debug,
          this.#target
        );
        if (!apiOnly) {
          // Allow to use SyncMethods
          props.ChromeXt.forEach((k) => {
            const array = this.#store[k];
            UnLocked[k] = new Proxy(this[k], {
              get(target, prop) {
                let value = target[prop];
                if (prop == "sync" || SyncMethods.includes(prop)) {
                  array.ChromeXt = UnLocked; // Unlock sync methods of SyncArray
                  if (prop == "sync") value = value.bind(array);
                }
                return value;
              },
            });
          });
        }
        return UnLocked;
      } else {
        throw new backup.Error("Failed to unlock ChromeXtTarget");
      }
    }
  }

  Object.freeze(ChromeXtTarget.prototype);
  Object.freeze(SyncArray.prototype);
  const ChromeXt = new ChromeXtTarget(secure);
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
