const globalThis = GM.globalThis;
const window = GM.globalThis;
const self = GM.globalThis;
const parent = GM.globalThis;
const frames = GM.globalThis;
const top = GM.globalThis;
delete GM.globalThis;
// Override possible references to the original window object.
// Note that from the DevTools console, these objects are undefined if they are not used in the script debugging context.
delete GM_info.script.code;
Object.freeze(GM_info.script);
const ChromeXt = GM.ChromeXt;
// Kotlin separator

function GM_addStyle(css) {
  const style = document.createElement("style");
  style.setAttribute("type", "text/css");
  style.textContent = css;
  try {
    (document.head || document.documentElement).appendChild(style);
  } catch {
    setTimeout(() => {
      document.head.appendChild(style);
    }, 0);
  }
  return style;
}
// Kotlin separator

const GM_log = console.log.bind(console);
// Kotlin separator

function GM_removeValueChangeListener(index) {
  GM_info.valueListener[index].enabled = false;
}
// Kotlin separator

function GM_unregisterMenuCommand(index) {
  LockedChromeXt.unlock(key).commands[index].enabled = false;
}
// Kotlin separator

function GM_addElement() {
  // arguments: parent_node, tag_name, attributes
  if (arguments.length == 2) {
    arguments = [document.head, arguments[0], arguments[1]];
  }
  if (arguments.length != 3) {
    return;
  }
  const element = document.createElement(arguments[1]);
  for (const [key, value] of Object.entries(arguments[2])) {
    if (key != "textContent") {
      element.setAttribute(key, value);
    } else {
      element.textContent = value;
    }
  }
  try {
    arguments[0].appendChild(element);
  } catch {
    setTimeout(() => {
      document.head.appendChild(element);
    }, 0);
  }
  return element;
}
// Kotlin separator

function GM_download(details) {
  if (arguments.length == 2) {
    details = { url: arguments[0], name: arguments[1] };
  }
  return GM_xmlhttpRequest({
    ...details,
    responseType: "blob",
    onload: (res) => {
      if (res.status !== 200)
        return console.error("Error loading: ", details.url, res);
      const link = document.createElement("a");
      link.href = URL.createObjectURL(res.response);
      link.download =
        details.name ||
        details.url.split("#").shift().split("?").shift().split("/").pop();
      link.dispatchEvent(new MouseEvent("click"));
      setTimeout(() => URL.revokeObjectURL(link.href), 1000);
    },
  });
}
// Kotlin separator

function GM_openInTab(url, options) {
  const gm_window = window.open(url, "_blank");
  if (typeof options == "boolean") {
    options = { active: !options };
  }
  if ("active" in options && options.active) {
    gm_window.focus();
  }
  return gm_window;
}
// Kotlin separator

function GM_registerMenuCommand(title, listener, _accessKey = "Dummy") {
  const index = LockedChromeXt.unlock(key).commands.findIndex(
    (e) => e.id == GM_info.script.id && e.title == title
  );
  if (index != -1) {
    LockedChromeXt.unlock(key).commands[index].listener = listener;
    return index;
  }
  LockedChromeXt.unlock(key).commands.push({
    id: GM_info.script.id,
    title,
    listener,
    enabled: true,
  });
  return LockedChromeXt.unlock(key).commands.length - 1;
}
// Kotlin separator

function GM_addValueChangeListener(key, listener) {
  const index = GM_info.valueListener.findIndex(
    (e) => e.key == key && e.listener == listener
  );
  if (index != -1) {
    GM_info.valueListener[index].enabled = true;
    return index;
  }
  GM_info.valueListener.push({ key, listener, enabled: true });
  return GM_info.valueListener.length - 1;
}
// Kotlin separator

function GM_setValue(key, value) {
  GM_info.storage[key] = value;
  GM.scriptStorage({ key, value });
}
// Kotlin separator

function GM_deleteValue(key) {
  if (key in GM_info.storage) {
    delete GM_info.storage[key];
    GM.scriptStorage({ key });
  }
}
// Kotlin separator

function GM_getValue(key, default_value) {
  return GM_info.storage[key] || default_value;
}
// Kotlin separator

function GM_listValues() {
  return Object.keys(GM_info.storage);
}
// Kotlin separator

function GM_getResourceText(name) {
  return (
    GM_info.script.resources.find((it) => it.name == name).content ||
    "ChromeXt failed to find resource " + name
  );
}
// Kotlin separator

function GM_getResourceURL(name) {
  return (
    GM_info.script.resources.find((it) => it.name == name).url ||
    "ChromeXt failed to find resource " + name
  );
}
// Kotlin separator

function GM_xmlhttpRequest(details) {
  if (!details.url) {
    throw new Error("GM_xmlhttpRequest requires a URL.");
  } else {
    const domain = new URL(details.url).hostname;
    const connects = GM_info.script.connects;
    let allowed = false;
    if (location.hostname.endsWith(domain) && connects.includes("self"))
      allowed = true;
    if (connects.includes("*")) allowed = true;
    if (!allowed) {
      connects.forEach((it) => {
        if (it.endsWith(domain)) allowed = true;
      });
    }
    if (!allowed) {
      console.error("Connection to", url, "is not declared using @connect");
      return;
    }
  }
  const uuid = Math.random();
  details.method = details.method ? details.method.toUpperCase() : "GET";

  if (
    "data" in details &&
    details.data != null &&
    typeof details.data != "string"
  ) {
    details.binary = true;
  }

  if ("binary" in details && "data" in details && details.binary) {
    switch (details.data.constructor) {
      case File:
      case Blob:
        details.data = details.data.arrayBuffer();
      case ArrayBuffer:
        details.data = new Uint8Array(details.data);
      case Uint8Array:
        data = btoa(
          (binString = Array.from(data, (x) => String.fromCodePoint(x)).join(
            ""
          ))
        );
        break;
      default:
        details.binary = false;
    }
  }

  if (
    !(
      "headers" in details &&
      ("User-Agent" in details.headers || "user-agent" in details.headers)
    )
  ) {
    details.headers = details.headers || {};
    details.headers["User-Agent"] = window.navigator.userAgent;
  }

  LockedChromeXt.unlock(key).dispatch("xmlhttpRequest", {
    id: GM_info.script.id,
    request: details,
    uuid,
  });

  const tmpListener = (e) => {
    if (e.detail.id == GM_info.script.id && e.detail.uuid == uuid) {
      e.stopImmediatePropagation();
      let data = e.detail.data;
      data.context = details.context;
      if (data.responseHeaders) {
        data.finalUrl = data.responseHeaders.Location || details.url;
        data.total = data.responseHeaders["Content-Length"];
      }
      switch (e.detail.type) {
        case "load":
          data.readyState = 4;
          if ("overrideMimeType" in details)
            data.responseHeaders["Content-Type"] = details.overrideMimeType;
          details.responseType = details.responseType || "";
          if ([101, 204, 205, 304].includes(data.status)) {
            data.response = null;
          } else if (
            ["arraybuffer", "blob", "stream"].includes(details.responseType)
          ) {
            const arraybuffer = Uint8Array.from(atob(data.response), (m) =>
              m.codePointAt(0)
            ).buffer;
            const type = data.responseHeaders["Content-Type"] || "";
            const blob = new Blob([arraybuffer], { type });
            switch (details.responseType) {
              case "arraybuffer":
                data.response = arraybuffer;
                break;
              case "blob":
                data.response = blob;
                break;
              case "stream":
                data.response = blob.stream();
                break;
            }
          } else {
            data.responseText = data.response;
            switch (details.responseType) {
              case "json":
                data.response = JSON.parse(data.responseText);
                break;
              case "document":
                const parser = new DOMParser();
                data.response = parser.parseFromString(
                  data.responseText,
                  data.responseHeaders["Content-Type"] || "text/html"
                );
                break;
            }
          }
          details.onload(data);
          e.detail.type = "loadend";
        default:
          if (typeof details["on" + e.detail.type] == "function") {
            details["on" + e.detail.type](data);
          }
          if (["loadend", "error"].includes(e.detail.type)) {
            LockedChromeXt.unlock(key).removeEventListener(
              "xmlhttpRequest",
              tmpListener
            );
          }
      }
    }
  };
  LockedChromeXt.unlock(key).addEventListener("xmlhttpRequest", tmpListener);

  return {
    abort: () => {
      LockedChromeXt.unlock(key).dispatch("xmlhttpRequest", {
        uuid,
        abort: true,
      });
    },
  };
}
// Kotlin separator

GM.bootstrap = () => {
  delete GM.bootstrap;
  const row = /\/\/\s+@(\S+)\s+(.+)/g;
  const meta = GM_info.script;
  if (typeof meta.code != "function" && typeof ChromeXt != "undefined") {
    return;
  }
  let match;
  while ((match = row.exec(GM_info.scriptMetaStr.trim())) !== null) {
    if (meta[match[1]]) {
      if (typeof meta[match[1]] == "string") meta[match[1]] = [meta[match[1]]];
      meta[match[1]].push(match[2]);
    } else meta[match[1]] = match[2];
  }
  for (const it of [
    "include",
    "match",
    "exlcude",
    "require",
    "grant",
    "connect",
  ]) {
    const plural = it.endsWith("h") ? it + "es" : it + "s";
    meta[plural] = typeof meta[it] == "string" ? [meta[it]] : meta[it] || [];
    Object.freeze(meta[plural]);
    delete meta[it];
  }
  delete meta.resource;

  meta["run-at"] = Array.isArray(meta["run-at"])
    ? meta["run-at"][0]
    : meta["run-at"] || "document-idle";

  if (
    meta.grants.includes("unsafeWindow") ||
    meta["inject-into"] == "page" ||
    meta.requires.length > 0
  ) {
    GM.globalThis = window;
  } else {
    const handler = {
      // A handler to block access to globalThis
      window: {},
      keys: Object.keys(window),
      // These keys will be accessible to the getter but not to the setter
      set(target, prop, value) {
        if (target[prop] != value || target.propertyIsEnumerable(prop)) {
          // Avoid redefining global non-enumerable classes, such as Object and Proxy, which are accessible to the getter
          this.window[prop] = value;
        }
        return true;
      },
      get(target, prop, receiver) {
        if (target[prop] == target) return receiver;
        // Block possible jail break
        if (
          this.keys.includes(prop) ||
          (typeof target[prop] != "undefined" &&
            !target.propertyIsEnumerable(prop))
          // Simulate an isolated window object, where common functions are available
        ) {
          const v = target[prop];
          return prop != "ChromeXt" &&
            this.keys.includes(prop) &&
            typeof v == "function"
            ? v.bind(window)
            : v;
          // Avoid changing the binding property of global non-enumerable classes
          // ChromeXt is non-configurable, and thus should not be bound
        } else {
          return this.window[prop];
        }
      },
    };
    handler.keys.splice(handler.keys.findIndex((e) => e == "ChromeXt") + 1);
    // Drop user-defined keys in the global context
    handler.keys.push(...Object.keys(EventTarget.prototype));
    GM.globalThis = new Proxy(window, handler);
  }

  if (
    meta.grants.includes("GM.xmlHttpRequest") &&
    meta.grants.includes("GM_xmlhttpRequest")
  ) {
    GM.xmlHttpRequest = async (details) => {
      return await new Promise((resolve, reject) => {
        const onload = details.onload;
        const onerror = details.onerror;
        details.onload = (d) => {
          if (typeof onload == "function") onload(d);
          resolve(d);
        };
        details.onerror = (e) => {
          if (typeof onerror == "function") onerror(e);
          reject(e);
        };
        GM_xmlhttpRequest(details);
      });
    };
  }

  if (meta.grants.includes("GM.ChromeXt")) {
    GM.ChromeXt = LockedChromeXt.unlock(key);
  }
  if (
    meta.grants.includes("GM_setValue") ||
    meta.grants.includes("GM_listValues")
  ) {
    GM_info.storage = {};
  }

  if (
    meta.grants.includes("GM_getValue") ||
    meta.grants.includes("GM.getValue")
  ) {
    GM.scriptStorage = function (data) {
      const grants = GM_info.script.grants;
      let broadcast = grants.includes("GM_addValueChangeListener");
      if ("broadcast" in data && !data.broadcast) {
        broadcast = false;
        delete data.broadcast;
      }
      let payload = {
        id: GM_info.script.id,
        data,
        uuid: GM_info.uuid,
        broadcast,
      };
      if (broadcast) {
        LockedChromeXt.unlock(key).post("scriptStorage", payload);
      }
      LockedChromeXt.unlock(key).dispatch("scriptStorage", payload);
    };
    Object.freeze(GM.scriptStorage);

    LockedChromeXt.unlock(key).addEventListener("scriptStorage", (e) => {
      if (e.detail.id != GM_info.script.id) {
        return;
      }
      e.stopImmediatePropagation();
      const data = e.detail.data;
      if ("init" in data) {
        GM_info.storage = data.init;
        runScript(meta);
        return;
      }
      if ("key" in data && data.key in GM_info.storage) {
        if (e.detail.uuid == GM_info.uuid && e.detail.broadcast != true) return;
        GM_info.valueListener.forEach((v) => {
          if (v.enabled == true && v.key == data.key) {
            v.listener(
              GM_info.storage[data.key] || null,
              data.value,
              e.detail.uuid != GM_info.uuid
            );
          }
        });
      }
      GM_info.storage[data.key] = data.value;
    });
    GM_info.valueListener = [];

    const valueAsked = [];
    GM.getValue = async function (key) {
      const id = Math.random();
      if (!valueAsked.includes(key)) {
        valueAsked.push(key);
        return GM_info.storage[key];
      }
      GM.scriptStorage({ key, id, broadcast: false });
      return await promiseListenerFactory(
        "scriptSyncValue",
        GM_info.uuid,
        GM_info.script.id,
        (data, resolve, _reject) => {
          GM_info.storage[key] = data.value;
          resolve(data.value);
        },
        (data) => data.id == id && data.key == key
      );
    };
  } else {
    runScript(meta);
  }

  function promiseListenerFactory(
    event,
    uuid,
    id = GM_info.script.id,
    listener = (_data, resolve, _reject) => resolve(true),
    closeCondition = () => true
  ) {
    return new Promise((resolve, reject) => {
      const tmpListener = (e) => {
        if (e.detail.id == id && e.detail.uuid == uuid) {
          e.stopImmediatePropagation();
          const data = e.detail.data || null;
          if (closeCondition(data)) {
            LockedChromeXt.unlock(key).removeEventListener(event, tmpListener);
            listener(data, resolve, reject);
          }
        }
      };
      LockedChromeXt.unlock(key).addEventListener(event, tmpListener);
    });
  }

  function runScript(meta) {
    if (meta.requires.length > 0) {
      meta.sync_code = meta.code;
      meta.code = async () => {
        for (const url of meta.requires) {
          let script;
          try {
            script = await (await fetch(url)).text();
          } catch {
            script = await new Promise((resolve, reject) => {
              GM_xmlhttpRequest({
                url,
                onload: (res) => resolve(res.responseText),
                onerror: (e) => reject(e),
                ontimeout: (e) => reject(e),
              });
            });
          }
          try {
            new Function(script)();
          } catch {
            const uuid = Math.random();
            const detail = JSON.stringify({
              uuid,
              id: GM_info.script.id,
            });
            script =
              script.replace("ChromeXt", "LockedChromeXt") +
              `\nChromeXt.post('unsafe_eval', ${detail});`;
            LockedChromeXt.unlock(key).dispatch("unsafeEval", script);
            await promiseListenerFactory("unsafe_eval", uuid);
          }
        }
        meta.sync_code();
      };
    }

    switch (meta["run-at"]) {
      case "document-start":
        meta.code();
        break;
      case "document-end":
        if (document.readyState != "loading") {
          meta.code();
        } else {
          window.addEventListener("DOMContentLoaded", meta.code);
        }
        break;
      default:
        if (document.readyState == "complete") {
          meta.code();
        } else {
          window.addEventListener("load", meta.code);
        }
    }

    GM_info.uuid = Math.random();
    GM_info.scriptHandler = "ChromeXt";
    GM_info.version = "3.5.0";
    Object.freeze(GM_info);
    LockedChromeXt.unlock(key).scripts.push(GM_info);
  }
};

const key = Symbol("key");
GM.ChromeXtLock = class {
  #key = key;
  #ChromeXt;
  constructor(GM) {
    if (typeof GM.key == "number" && ChromeXt.isLocked()) {
      this.#ChromeXt = ChromeXt.unlock(GM.key, false);
      GM.key = null; // Revoke key
    } else {
      throw new Error("Invalid key to construct a lock");
    }
    Object.defineProperty(this, "unlock", {
      configurable: false,
      value: (key) => {
        if (key == this.#key) {
          return this.#ChromeXt;
        } else {
          throw new Error("Fail to unlock ChromeXtLock");
        }
      },
    });
  }
};
const LockedChromeXt = new GM.ChromeXtLock(GM);
delete GM.key;
delete GM.ChromeXtLock;
// Delete references
