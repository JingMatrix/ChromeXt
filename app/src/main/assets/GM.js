const globalThis = GM.globalThis;
const window = GM.globalThis;
const self = GM.globalThis;
const parent = GM.globalThis;
const frames = GM.globalThis;
const top = GM.globalThis;
delete GM.globalThis;
// Override possible references to the original window object.
// Note that from the DevTools console, these objects are undefined if they are not used in the script debugging context.
// However, one can break this jail using setTimeout or Function.
delete GM_info.script.code;
delete GM_info.script.sync_code;
delete GM.key;
Object.freeze(GM_info.script);
const ChromeXt = GM.ChromeXt;
if (typeof GM_xmlhttpRequest == "function" && !GM_xmlhttpRequest.strict)
  Object.defineProperty(GM_xmlhttpRequest, "strict", { value: true });
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
    });
  }
  return style;
}
// Kotlin separator

const unsafeWindow = window;
// Kotlin separator

const GM_log = console.log.bind(console);
// Kotlin separator

function GM_setClipboard(data, info = "text/plain") {
  let type = info.mimetype || info;
  if (!type.includes("/")) type = type == "html" ? "text/html" : "text/plain";
  const blob = new Blob([data], { type });
  data = [new ClipboardItem({ [type]: blob })];
  return new Promise((resolve) => {
    window.addEventListener(
      "focus",
      () => navigator.clipboard.write(data).then(() => resolve()),
      { once: true }
    );
    LockedChromeXt.unlock(key).dispatch("focus");
  });
}
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

function GM_openInTab(url, options = true) {
  let target = "GM_openInTab";
  if (typeof options == "boolean") {
    options = { active: !options };
  }
  if (options.setParent) target = "_self";
  const gm_window = window.open(url, target);
  if (options.active) {
    gm_window.focus();
  } else {
    gm_window.blur();
    window.focus();
  }
  return gm_window;
}
// Kotlin separator

function GM_registerMenuCommand(title, listener, _accessKey = "Dummy") {
  const ChromeXt = LockedChromeXt.unlock(key);
  const index = ChromeXt.commands.findIndex(
    (e) => e.id == GM_info.script.id && e.title == title
  );
  if (index != -1) {
    ChromeXt.commands[index].listener = listener;
    return index;
  }
  ChromeXt.commands.push({
    id: GM_info.script.id,
    title,
    listener,
    enabled: true,
  });
  return ChromeXt.commands.length - 1;
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
}
// Kotlin separator

function GM_deleteValue(key) {
  delete GM_info.storage[key];
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
  if (typeof details == "string") {
    details = new Request(...arguments);
    if (arguments.length == 2) details.data = arguments[1].body;
  }
  if (details instanceof Request) {
    details.fetch = true;
  }

  if (!details.url) {
    throw new Error("GM_xmlhttpRequest requires a URL.");
  } else if (GM_xmlhttpRequest.strict) {
    const domain = new URL(details.url).hostname;
    const connects = GM_info.script.connects;
    let allowed = location.hostname == domain && connects.includes("self");
    if (!allowed && connects.includes("*")) allowed = true;
    if (!allowed) {
      connects.forEach((it) => {
        if (domain.endsWith(it)) allowed = true;
      });
    }
    if (!allowed) {
      console.error("Connection to", domain, "is not declared using @connect");
      return;
    }
  }
  const uuid = Math.random();
  details.method = details.method ? details.method.toUpperCase() : "GET";

  async function prepare(details) {
    if (details instanceof Request && details.method != "GET") {
      details.data = details.data || (await details.blob());
    }
    if (
      "data" in details &&
      details.data != null &&
      typeof details.data != "string"
    ) {
      details.binary = true;
    }
    if ("binary" in details && "data" in details && details.binary) {
      if (Array.isArray(details.data)) details.data = new Blob(details.data);
      switch (details.data.constructor) {
        case DataView:
          details.data = new Blob([details.data]);
        case File:
        case Blob:
          details.data = await details.data.arrayBuffer();
        case ArrayBuffer:
          details.data = new Uint8Array(details.data);
        case Uint8Array:
          details.data = btoa(
            Array.from(details.data, (x) => String.fromCodePoint(x)).join("")
          );
          break;
        default:
          details.binary = false;
      }
    }
    details.headers = details.headers || {};
    if (details.headers instanceof Headers)
      Object.defineProperty(details, "headers", {
        value: Object.fromEntries(details.headers),
      });
    const hasUserAgent =
      "User-Agent" in details.headers || "user-agent" in details.headers;
    if (!hasUserAgent) {
      details.headers["User-Agent"] = window.navigator.userAgent;
    }
    const buffersize = details.buffersize;
    if (Number.isInteger(buffersize) && buffersize > 0 && buffersize < 256) {
      details.buffersize = buffersize;
    } else {
      details.buffersize = 8;
    }
  }

  const ChromeXt = LockedChromeXt.unlock(key);
  function revoke(listener) {
    ChromeXt.removeEventListener("xmlhttpRequest", listener);
  }

  const xhrHandler = {
    target: new EventTarget(),
    promise: null,
    get() {
      const prop = arguments[1];
      if (prop in EventTarget.prototype) {
        return this.target[prop].bind(this.target);
      } else if (
        prop.startsWith("on") ||
        [
          "responseType",
          "overrideMimeType",
          "url",
          "timeout",
          "fetch",
        ].includes(prop)
      ) {
        return details[prop];
      } else if (prop in Promise.prototype) {
        return this.promise[prop].bind(this.promise);
      } else {
        return Reflect.get(...arguments);
      }
    },
    set(target, prop, value) {
      if (
        prop == "responseHandler" &&
        this.promise == null &&
        typeof value == "function"
      ) {
        this.promise = target;
        value(this.resolve, this.reject);
        return true;
      }
      if (prop in Promise.prototype || prop in EventTarget.prototype)
        return false;
      target[prop] = value;
      if (prop == "readyState")
        this.target.dispatchEvent(new Event("readystatechange"));
      return true;
    },
  };
  const xhr = new Proxy(
    new Promise((resolve, reject) => {
      xhrHandler.resolve = resolve;
      xhrHandler.reject = reject;
    }),
    xhrHandler
  );

  xhr.responseHandler = (resolve, reject) => {
    const sink = new ResponseSink(xhr);
    function listener(e) {
      let data, type;
      if (arguments.length > 1) {
        data = arguments[0];
        type = arguments[1];
      } else if (e.detail.id == GM_info.script.id && e.detail.uuid == uuid) {
        e.stopImmediatePropagation();
        data = e.detail.data;
        type = e.detail.type;
      } else {
        return;
      }
      sink.parse(data);
      if (type == "progress") {
        sink.writer.write(data);
      } else if (type == "load") {
        sink.writer.close().then(() => resolve(xhr.response));
      } else if (["timeout", "error"].includes(type)) {
        sink.writer.abort(type);
        const error = new Error(data.message, {
          cause: data.error || type.toUpperCase(),
          fileName: xhr.url,
        });
        error.name = data.type;
        reject(error);
        revoke(listener);
      }
    }
    xhr.abort = () => {
      ChromeXt.dispatch("xmlhttpRequest", {
        uuid,
        abort: true,
      });
      revoke(listener);
      sink.writer.abort("abort");
    };
    let request = details;
    if (
      !details.signal &&
      Number.isInteger(details.timeout) &&
      details.timeout > 0
    ) {
      details.signal = AbortSignal.timeout(details.timeout);
    }
    if (details instanceof Request) {
      request = details;
    } else {
      request = new Request(details.url, {
        ...details,
        cache: "force-cache",
        body: details.data,
      });
    }
    request.signal.addEventListener("abort", xhr.abort);
    const responseType = details.responseType || "";
    xhr.binaryType = ["arraybuffer", "blob", "stream"].includes(responseType);
    xhr.readyState = 1;
    fetch(request)
      .then(async (response) => {
        const teedOff = response.body.tee();
        const res = new Response(teedOff[0], response);
        const stream = teedOff[1];
        if (xhr.fetch) {
          sink.dispatch("loadstart", res);
          sink.dispatch("progress", res);
          if (!xhr.binaryType) {
            resolve(await new Response(stream, response).text());
          } else {
            resolve(res);
          }
          sink.dispatch("load", res);
          sink.dispatch("loadend", res);
          return;
        }
        if (!xhr.binaryType) {
          res.chunk = await new Response(stream, response).text();
          listener(res, "progress");
        } else {
          const reader = stream.getReader();
          let result = { done: false };
          while (!result.done) {
            result = await reader.read();
            res.chunk = result.value;
            res.binary = true;
            listener(res, "progress");
          }
          reader.cancel();
        }
        listener(res, "load");
      })
      .catch(async (e) => {
        if (!(e instanceof TypeError)) {
          sink.writer.abort("error");
          reject(e);
          return;
        }
        await prepare(details);
        if (details instanceof Request) {
          request = {};
          for (const key in details) request[key] = details[key];
        } else {
          request = details;
        }
        ChromeXt.dispatch("xmlhttpRequest", {
          id: GM_info.script.id,
          request,
          uuid,
        });
        ChromeXt.addEventListener("xmlhttpRequest", listener);
      });
  };

  return xhr;
}

class ResponseSink {
  #writer;
  xhr;
  get writer() {
    if (!this.#writer) this.#writer = new WritableStream(this).getWriter();
    return this.#writer;
  }
  constructor(xhr) {
    this.xhr = xhr;
    // this.xhr.readyState = 0;
    this.xhr.status = 0;
  }
  dispatch(type, data) {
    const event = new ProgressEvent(type, this.xhr);
    this.xhr.dispatchEvent(event);
    if (typeof this.xhr["on" + type] == "function") {
      this.xhr["on" + type](data || this.xhr);
    }
  }
  static async prepare(type, data) {
    if ([101, 204, 205, 304].includes(data.status)) {
      data.response = null;
    }
    if (data.binaryType) {
      if (typeof data.response == "string") data.response = [data.response];
      const blob = new Blob(data.response, { type });
      switch (responseType) {
        case "arraybuffer":
          data.response = await blob.arrayBuffer();
          break;
        case "blob":
          data.response = blob;
          break;
        case "stream":
          data.response = blob.stream();
          break;
      }
    } else {
      if (Array.isArray(data.response)) {
        const utf8 = new TextDecoder();
        const blob = new Blob(data.response);
        data.response = utf8.decode(await blob.arrayBuffer());
      }
      data.responseText = data.response;
      if (data.responseType == "json") {
        data.response = JSON.parse(data.responseText);
      } else if (data.responseType == "document") {
        const parser = new DOMParser();
        data.response = parser.parseFromString(
          data.responseText,
          type == "text/xml" ? "text/xml" : "text/html"
        );
        data.responseXML = data.response;
      }
    }
    if (data.fetch) data = new Response(data.response, data);
  }
  parse(data) {
    if (typeof data != "object") return;
    for (const prop in data) {
      if (prop == "header" && this.xhr.headers instanceof Headers) continue;
      let val = data[prop];
      if (typeof val == "function") continue;
      this.xhr[prop] = val;
    }
    if (this.xhr.readyState != 1) return;
    this.xhr.readyState = 2;
    const headers = data.headers;
    this.xhr.headers = new Headers(headers);
    this.xhr.responseHeaders = Object.entries(
      Object.fromEntries(this.xhr.headers)
    )
      .map(([k, v]) => k.toLowerCase() + ": " + v)
      .join("\r\n");
    this.xhr.getAllResponseHeaders = () => this.xhr.responseHeaders;
    this.xhr.getResponseHeader = (headerName) =>
      this.xhr.headers.get(headerName);
    this.xhr.finalUrl = this.xhr.headers.get("Location") || this.xhr.url;
    this.xhr.responseURL = this.xhr.finalUrl;
    this.xhr.total = this.xhr.headers.get("Content-Length");
    if (this.xhr.total !== null) {
      this.xhr.lengthComputable = true;
      this.xhr.total = Number(this.xhr.total);
    }
    if (data instanceof Response) return;
    this.xhr.encoding = this.xhr.headers.get("Content-Encoding");
    if (this.xhr.encoding != null) {
      try {
        this.ds = new DecompressionStream(this.xhr.encoding.toLowerCase());
      } catch {
        this.xhr.abort();
      }
    }
  }
  start(_controller) {
    this.dispatch("loadstart");
    if (this.xhr.readyState == 2) this.xhr.readyState = 3;
    this.xhr.response = this.xhr.binary ? [] : "";
    this.xhr.loaded = 0;
  }
  write(data, _controller) {
    let chunk = data.chunk;
    if (this.xhr.binary) {
      if (!(data instanceof Response) && typeof chunk == "string")
        chunk = Uint8Array.from(atob(chunk), (m) => m.codePointAt(0));
      this.xhr.response.push(chunk);
    } else {
      this.xhr.response += chunk;
    }
    this.xhr.loaded += data.bytes || chunk.length;
    this.dispatch("progress");
  }
  async close(_controller) {
    const type =
      this.xhr.overrideMimeType || this.xhr.headers.get("Content-Type") || "";
    if (
      Array.isArray(this.xhr.response) &&
      this.ds instanceof DecompressionStream
    ) {
      const stream = new Blob(this.xhr.response, { type })
        .stream()
        .pipeThrough(this.ds);
      this.xhr.response = [];
      const reader = stream.getReader();
      let result = { done: true };
      while (result.done) {
        result = await reader.read();
        this.xhr.response.push(result.value);
      }
      reader.cancel();
    }
    this.xhr.readyState = 4;
    await ResponseSink.prepare(type, this.xhr);
    this.dispatch("load");
    this.dispatch("loadend");
  }
  abort(reason) {
    this.dispatch(reason);
    this.dispatch("loadend");
  }
}
// Kotlin separator

GM.bootstrap = () => {
  delete GM.bootstrap;
  const ChromeXt = LockedChromeXt.unlock(key);
  if (ChromeXt.scripts.findIndex((e) => e.id == GM_info.script.id) != -1)
    return;

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
    "resource",
  ]) {
    const plural = it.endsWith("h") ? it + "es" : it + "s";
    meta[plural] = typeof meta[it] == "string" ? [meta[it]] : meta[it] || [];
    if (it != "resource") Object.freeze(meta[plural]);
    delete meta[it];
  }
  meta.resources = meta.resources.map((res) => {
    const split = res.split(/\s+/).filter((it) => it.length > 0);
    const data = { name: split[0], url: split[1] };
    const integrity = data.url.split("#");
    if (integrity.length > 1) data.url = integrity[0];
    return data;
  });

  meta["run-at"] = Array.isArray(meta["run-at"])
    ? meta["run-at"][0]
    : meta["run-at"] || "document-idle";

  const grants = meta.grants;

  if (
    meta["inject-into"] == "page" ||
    grants.includes("none") ||
    meta.requires.length > 0
  ) {
    GM.globalThis = window;
  } else {
    const handler = {
      // A handler to block access to globalThis
      window: { GM },
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
            ? v.bind(target)
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
    if (grants.includes("unsafeWindow"))
      handler.window.unsafeWindow = unsafeWindow;
    GM.globalThis = new Proxy(window, handler);
  }

  GM_info.uuid = Math.random();
  const storageHandler = {
    storage: GM_info.storage || {},
    broadcast: grants.includes("GM_addValueChangeListener"),
    payload: {
      id: meta.id,
      uuid: GM_info.uuid,
    },
    cache: new Set(),
    sync(data) {
      let broadcast = this.broadcast;
      if ("broadcast" in data && !data.broadcast) {
        broadcast = false;
        delete data.broadcast;
      }
      const payload = { data, broadcast, ...this.payload };
      if (broadcast) {
        ChromeXt.post("scriptStorage", payload);
      }
      ChromeXt.dispatch("scriptStorage", payload);
    },
    deleteProperty(target, key) {
      delete target.key;
      this.sync({ key });
    },
    set(target, key, value) {
      target[key] = value;
      this.sync({ key, value });
      return true;
    },
    async_get(key) {
      if (!this.cache.has(key)) {
        this.cache.add(key);
        return this.storage[key];
      }
      const id = Math.random();
      this.sync({ key, id, broadcast: false });
      return promiseListenerFactory(
        "scriptSyncValue",
        GM_info.uuid,
        meta.id,
        (data, resolve, _reject) => {
          this.storage[key] = data.value;
          resolve(data.value);
        },
        (data) => data.id == id && data.key == key
      );
    },
  };

  if (grants.includes("GM.getValue") || grants.includes("GM_getValue")) {
    GM.getValue = storageHandler.async_get.bind(storageHandler);
    ChromeXt.addEventListener("scriptStorage", (e) => {
      if (e.detail.id != GM_info.script.id) return;
      e.stopImmediatePropagation();
      const data = e.detail.data;
      if ("init" in data && !storageHandler.inited) {
        storageHandler.inited = true;
        storageHandler.storage = data.init;
        runScript(meta);
        return;
      }

      if ("key" in data && data.key in GM_info.storage) {
        if (e.detail.uuid == GM_info.uuid && e.detail.broadcast !== true)
          return;
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
      storageHandler.storage[data.key] = data.value;
    });
    GM_info.valueListener = [];
  }

  grants.forEach((p) => {
    if (!p.startsWith("GM.")) return;
    const name = p.substring(3);
    if (typeof GM[name] != "object") return;
    const sync = GM[name].sync;
    if (typeof sync == "function") {
      GM[name] = function () {
        const result = sync.apply(null, arguments);
        if (result instanceof Promise) return result;
        return new Promise(async (resolve) => {
          resolve(await result);
        });
      };
    } else if (typeof sync == "undefined") {
      delete GM[name];
    } else {
      GM[name] = sync;
    }
  });

  if (grants.includes("GM.ChromeXt")) {
    GM.ChromeXt = ChromeXt;
  }

  runScript(meta);

  function promiseListenerFactory(
    event,
    uuid,
    id = meta.id,
    listener = (_data, resolve, _reject) => resolve(true),
    closeCondition = () => true
  ) {
    return new Promise((resolve, reject) => {
      const tmpListener = (e) => {
        if (e.detail.id == id && e.detail.uuid == uuid) {
          e.stopImmediatePropagation();
          const data = e.detail.data || null;
          if (closeCondition(data)) {
            ChromeXt.removeEventListener(event, tmpListener);
            listener(data, resolve, reject);
          }
        }
      };
      ChromeXt.addEventListener(event, tmpListener);
    });
  }

  function runScript(meta) {
    Object.freeze(storageHandler);
    GM_info.storage = new Proxy(storageHandler.storage, storageHandler);
    if (
      typeof GM_xmlhttpRequest == "function" &&
      (meta.requires.length > 0 || meta.resources.length > 0)
    ) {
      meta.sync_code = meta.code;
      const forceCache = (url) =>
        fetch(url, { cache: "force-cache" }).then(
          (res) => res.text(),
          (_err) => GM_xmlhttpRequest({ url })
        );
      meta.code = async () => {
        for (const data of meta.resources) {
          data.content = await forceCache(data.url);
        }
        return meta.sync_code();
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

    GM_info.scriptHandler = "ChromeXt";
    GM_info.version = "3.7.0";
    Object.freeze(GM_info);
    ChromeXt.scripts.push(GM_info);
  }
};

const key = Symbol("key");
GM.ChromeXtLock = class {
  #key = key;
  #ChromeXt;
  constructor(GM) {
    if (typeof GM.key == "number" && ChromeXt.isLocked()) {
      this.#ChromeXt = ChromeXt.unlock(GM.key, false);
    } else {
      throw new Error("Invalid key to construct a lock");
    }
    Object.defineProperty(this, "unlock", {
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
delete GM.ChromeXtLock;
