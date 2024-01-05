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
delete GM.name;
Object.freeze(GM_info.script);
const ChromeXt = GM.ChromeXt;
if (typeof GM_xmlhttpRequest == "function" && !GM_xmlhttpRequest.strict) {
  GM_xmlhttpRequest.addCookie = GM_info.script.grants.includes("GM_cookie");
  Object.defineProperty(GM_xmlhttpRequest, "strict", { value: true });
}
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

const GM_log = console.info.bind(
  console,
  GM_info.script.id.split(":").at(-1) + ":"
);
// Kotlin separator

function GM_notification(details, ondone) {
  let payload = {};
  if (typeof details == "object") {
    payload = { ...details, ondone };
  } else {
    const props = ["text", "title", "image", "onclick", "timeout", "ondone"];
    props.splice(arguments.length, props.length);
    props.forEach((prop, index) => (payload[prop] = arguments[index]));
  }
  const ChromeXt = LockedChromeXt.unlock(key);
  if (Number.isInteger(payload.timeout)) {
    setTimeout(() => {
      if (typeof payload.ondone == "function") payload.ondone("timeout");
      if (typeof listener == "function")
        ChromeXt.removeEventListener("notification", listener);
    }, payload.timeout);
  } else {
    payload.timeout = 10000;
  }
  if (!("text" in payload || payload.highlight))
    throw TypeError("Parameter text not given");
  let onclick;
  if (typeof payload.onclick == "function") {
    onclick = payload.onclick.bind(
      typeof details == "object" ? details : payload
    );
    payload.onclick = true;
  } else {
    delete payload.onclick;
  }
  payload.id = GM_info.script.id;
  payload.uuid = Math.floor(Math.random() * 2 ** 16);
  if (payload.onclick || typeof payload.ondone == "function") {
    function listener(e) {
      const data = e.detail;
      if (!(e.type == "notification" && data.id == payload.id)) return;
      if (data.uuid == payload.uuid) {
        e.stopImmediatePropagation();
        ChromeXt.removeEventListener("notification", listener);
        if (payload.onclick) onclick();
        if (typeof payload.ondone == "function") {
          payload.ondone("click");
          delete payload.ondone;
        }
      }
    }
    ChromeXt.addEventListener("notification", listener);
  }
  ChromeXt.dispatch("notification", payload);
}
// Kotlin separator

const GM_cookie = new (class CookieManager {
  #cache = [];
  get store() {
    return this.#cache;
  }
  #command(method, params) {
    const uuid = Math.random();
    const payload = { method, params, uuid, id: GM_info.script.id };
    const ChromeXt = LockedChromeXt.unlock(key);
    const self = this;
    return new Promise((resolve, reject) => {
      function listener(e) {
        const data = e.detail;
        if (!(e.type == "cookie" && data.id == payload.id)) return;
        const response = data.response.find((r) => r.id === 2);
        if (data.method == "Network.getCookies" && "result" in response)
          self.#cache = response.result.cookies.map(
            (it) => new CookieParam(it)
          );
        if (data.uuid == uuid) {
          if (typeof response != "object")
            reject(new TypeError(`Response not found for ${data.method}`));
          if ("error" in response)
            reject(new TypeError("CDP Error: " + response.error.message));
          ChromeXt.removeEventListener("cookie", listener);
          resolve(response.result);
        }
      }
      ChromeXt.addEventListener("cookie", listener);
      ChromeXt.dispatch("cookie", payload);
    });
  }
  export(url = location.origin, store, httpOnly = false) {
    const cookies = store || this.store;
    if (!Array.isArray(cookies)) return;
    if (typeof url == "string") {
      url = new URL(url);
    } else if (!(url instanceof URL)) {
      return;
    }
    if (cookies == this.store && url.origin != location.origin) return;
    return cookies
      .map((it) => (it instanceof CookieParam ? it : new CookieParam(it)))
      .filter((it) => it.match(url, httpOnly))
      .map((cookie) => cookie.toHeader());
  }
  async list(details = { url: window.origin }, callback) {
    let cookies, error;
    try {
      if (typeof details != "object") throw TypeError("Invalid parameters");
      await this.#command("Network.getCookies", [
        details.url || location.origin,
      ]);
      const props = ["domain", "name", "path"].filter((key) => key in details);
      if (props.length == 0) {
        cookies = this.#cache;
      } else {
        cookies = this.#cache.filter((item) => {
          for (const prop of props) {
            if (item[prop] !== details[prop]) return false;
          }
          return true;
        });
      }
    } catch (e) {
      error = e;
    }
    if (typeof callback == "function") callback(cookies, error?.message);
    if (error instanceof Error) throw error;
    return cookies;
  }
  async #dispatch(method, details, callback) {
    if (typeof callback == "function") {
      let error, result;
      try {
        result = await this.#command(method, details);
      } catch (e) {
        error = e;
      }
      callback(error?.message);
      if (error instanceof Error) throw error;
      return result;
    } else {
      return this.#command(method, details);
    }
  }
  set(details, callback) {
    let cookies = details;
    if (!Array.isArray(cookies)) cookies = [details];
    for (const cookie of cookies) {
      if (typeof cookie.expirationDate == "number") {
        cookie.expires = cookie.expirationDate;
        delete cookie.expirationDate;
      }
      if (cookie.domain == undefined) cookie.domain = window.location.hostname;
    }
    return this.#dispatch("Network.setCookies", { cookies }, callback);
  }
  delete(details, callback) {
    if (details.domain == undefined && details.url == undefined)
      details.domain = window.location.hostname;
    return this.#dispatch("Network.deleteCookies", details, callback);
  }
})();

class CookieParam {
  #header;
  constructor(data) {
    if (
      typeof data == "object" &&
      typeof data.name == "string" &&
      "value" in data
    ) {
      Object.assign(this, data);
      if (typeof this.header == "string") {
        this.#header = this.header;
        delete this.header;
      }
    } else {
      throw TypeError("Invalid parameters for cookie");
    }
  }
  static fromHeader(str, url) {
    const props = str
      .split(";")
      .map((it) => it.trim())
      .filter((it) => it.length > 0);
    const defn = props.shift().split("=");
    if (defn.length < 2) return;
    const cookie = new CookieParam({
      name: defn.shift(),
      value: defn.join("="),
      header: str,
    });
    props.forEach((prop) => {
      const parts = prop.split("=");
      const key = parts.shift().toLowerCase();
      var value = parts.join("=");
      if (key === "expires") {
        cookie.expires = new Date(value).getTime() / 1000;
      } else if (key === "max-age") {
        cookie.maxAge = Number(value);
        cookie.expires = cookie.maxAge + new Date().getTime() / 1000;
      } else if (key === "secure") {
        cookie.secure = true;
      } else if (key === "httponly") {
        cookie.httpOnly = true;
      } else {
        cookie[key] = value;
      }
    });
    cookie.url = url;
    cookie.session = cookie.expires == -1;
    return cookie;
  }
  /** @param {URL} url */
  set url(url) {
    if (!(url instanceof URL)) url = new URL(url || location.origin);
    this.domain = this.domain || url.hostname;
    if (url.port.length != 0) {
      this.sourcePort = Number(url.port);
    } else if (url.protocol == "https:") {
      this.sourcePort = 443;
    } else if (url.protocol == "http:") {
      this.sourcePort = 80;
    }
    if (url.protocol.endsWith("s:")) this.sourceScheme = "Secure";
  }
  httpOnly = false;
  path = "/";
  secure = false;
  expires = -1;
  priority = "Medium";
  sourceScheme = "NonSecure";
  capitalize(s) {
    return s && s[0].toUpperCase() + s.slice(1);
  }
  toHeader() {
    if (typeof this.#header == "string") return this.#header;
    let header = [this.name + "=" + this.value];
    header.push(`Domain=${this.domain}`);
    if (Number.isFinite(this.maxAge) && this.maxAge > 0) {
      header.push(`Max-Age=${this.maxAge}`);
    }
    if (Number.isFinite(this.expires) && this.expires != -1) {
      const date = new Date();
      date.setTime(this.expires * 1000);
      header.push(`expires=${date.toUTCString()}`);
    }
    const props = ["path", "sameSite", "httpOnly", "secure"];
    for (const prop of props) {
      if (!(prop in this)) continue;
      const val = this[prop];
      if (typeof val == "string" && val.length != 0) {
        header.push(this.capitalize(prop) + `=${this.capitalize(val)}`);
      } else if (val === true) {
        header.push(this.capitalize(prop));
      }
    }
    return header.join("; ");
  }
  match(url, httpOnly) {
    if (!(url instanceof URL)) url = new URL(url);
    if (httpOnly && this.httpOnly !== true) return false;
    if ("path" in this && !url.pathname.startsWith(this.path)) return false;
    if ("domain" in this) {
      let domain = this.domain;
      if (domain.startsWith(".")) domain = domain.slice(1);
      if (!url.hostname.endsWith(domain)) return false;
    }
    const expires = this.expirationDate || this.expires;
    if (expires > 0) return expires * 1000 > new Date().getTime();
    return true;
  }
}
// Kotlin separator

function GM_setClipboard(text, info = { type: "text" }) {
  const type = info?.mimetype || info?.type || info;
  if (typeof info != "object" && typeof type == "string") info = { type };
  info.type = info.type || info.mimetype;
  info.text = text;
  info.label = `GM_setClipboard by ${GM_info.script.id}`;
  if (info.type == "html" && !("htmlText" in info)) info.htmlText = info.text;
  LockedChromeXt.unlock(key).dispatch("copy", info);
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
  if (!gm_window) return window;
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
  return key in GM_info.storage ? GM_info.storage[key] : default_value;
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
  const fallback_method = typeof details.data == "undefined" ? "GET" : "POST";
  details.method = details.method
    ? details.method.toUpperCase()
    : fallback_method;
  if (["GET", "HEAD"].includes(details.method)) {
    delete details.data;
    delete details.body;
  }

  let useJSFetch = true;
  if (typeof details.forceCORS == "boolean") useJSFetch = !details.forceCORS;
  details.headers = new Headers(details.headers || {});

  async function prepare(details) {
    if (details instanceof Request && details.method != "GET") {
      details.data = details.data || (await details.blob());
    }

    if (details.data instanceof FormData) {
      if (details.binary !== true)
        for (const value of details.data.values())
          if (value instanceof Blob) {
            details.binary = true;
            break;
          }
      if (!details.binary) {
        const form = Array.from(details.data.entries())
          .map(([k, v]) => `${k}=${v}`)
          .join("&");
        if (details.method == "GET") {
          if (!details.url.includes("?"))
            details.url += "?" + encodeURIComponent(form);
        } else {
          details.headers.set(
            "Content-Type",
            "application/x-www-form-urlencoded"
          );
          details.data = form;
        }
      } else {
        const parts = [];
        const formboundary =
          "------WebKitFormBoundary" +
          Math.random().toString(36).slice(2, 10).toUpperCase() +
          Math.random().toString(36).slice(2, 10).toUpperCase();
        details.headers.set(
          "Content-Type",
          "multipart/form-data; boundary=" + formboundary.slice(2)
        );
        for (const [k, v] of details.data.entries()) {
          parts.push(formboundary);
          let disposition = `Content-Disposition: form-data; name="${k}"`;
          if (v instanceof Blob) {
            details.binary = true;
            const name = v.name || "blob";
            const type = v.type || "unknown";
            disposition += `; filename="${name}"`;
            parts.push(disposition);
            parts.push(`Content-Type: ${type}`);
            parts.push("");
            const binary = new Uint8Array(await v.arrayBuffer());
            parts.push(binary);
          } else {
            parts.push(disposition);
            parts.push("");
            parts.push(v);
          }
        }
        parts.push(formboundary + "--");
        const blob = [];
        parts.forEach((d) => blob.push(d, "\r\n"));
        details.data = new Blob(blob);
      }
    }

    if (
      details.data &&
      typeof details.data != "string" &&
      details.method != "GET" &&
      details.binary !== true
    ) {
      details.binary = true;
    }

    if ("data" in details && details.binary === true) {
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
      }
    }

    if (
      details.headers instanceof Headers &&
      !details.headers.has("User-Agent")
    ) {
      details.headers.set("User-Agent", window.navigator.userAgent);
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
    get() {
      const prop = arguments[1];
      if (prop in Promise.prototype && this.promise instanceof Promise) {
        return this.promise[prop].bind(this.promise);
      } else if (prop in EventTarget.prototype) {
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
      } else {
        return Reflect.get(...arguments);
      }
    },
    set(target, prop, value) {
      if (
        prop == "responseHandler" &&
        !(this.promise instanceof Promise) &&
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

  xhr.responseHandler = async (resolve, reject) => {
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
      } else if (type == "redirect" && xhr.redirect == "error") {
        xhr.error = new Error("Redirection not allowed");
        sink.dispatch("error");
        xhr.abort("redirect");
      } else if (type == "load") {
        sink.writer
          .close()
          .then(() => resolve(xhr.response))
          .catch((error) => {
            // error might be caused by abort
            if (error instanceof Error) {
              reject(
                new TypeError("Fail to parse response data: " + error.message, {
                  cause: error,
                })
              );
            }
          });
      } else if (["timeout", "error"].includes(type)) {
        const writer = sink.writer; // To dispatch loadstart event
        const cause = new Error(data.message);
        cause.stack = data.stack;
        const error = new Error(
          [data.status, data.statusText].filter((e) => e).join(", "),
          {
            cause,
          }
        );
        error.name = data.type;
        xhr.error = error;
        xhr.response = data.error;
        delete xhr.stack;
        delete xhr.type;
        if (xhr.response != undefined) {
          writer
            .close()
            .then(() => xhr.abort(type))
            .catch((e) => reject(e));
        } else {
          xhr.abort(type);
        }
      }
    }

    xhr.abort = (type = "abort") => {
      ChromeXt.dispatch("xmlhttpRequest", {
        uuid,
        abort: true,
      });
      revoke(listener);
      if (xhr.error instanceof Error) reject(xhr.error);
      sink.writer.abort(type);
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
    } else if (useJSFetch) {
      request = new Request(details.url, {
        cache: "force-cache",
        body: details.data,
        ...details,
        credentials: details.anonymous == true ? "omit" : "include",
        headers: {},
      });
      for (const p of details.headers) {
        request.headers.set(...p);
        if (request.headers.get(p[0]) !== p[1]) {
          useJSFetch = false;
          break;
        }
      }
    }

    xhr.binaryType = ["arraybuffer", "blob", "stream"].includes(
      xhr.responseType
    );
    xhr.readyState = 1;

    if (useJSFetch) {
      request.signal.addEventListener("abort", xhr.abort);
      await fetch(request)
        .then(async (response) => {
          const teedOff = response.body.tee();
          const res = new Response(teedOff[0], response);
          const localRes = new Response(teedOff[1], response);
          if (xhr.fetch) {
            sink.dispatch("loadstart", res);
            sink.dispatch("progress", res);
            let type = xhr.responseType || "text";
            if (type == "arraybuffer") type = "arrayBuffer";
            if (type in localRes) {
              resolve(await localRes[type]());
            } else {
              resolve(res);
            }
            sink.dispatch("load", res);
            sink.dispatch("loadend", res);
            return;
          }
          res.binary = xhr.binaryType;
          if (!res.binary) {
            res.chunk = await localRes.text();
            listener(res, "progress");
          } else {
            const reader = localRes.body.getReader();
            while (true) {
              const result = await reader.read();
              if (result.done) break;
              res.chunk = result.value;
              listener(res, "progress");
            }
            reader.cancel();
          }
          listener(res, "load");
        })
        .catch((e) => {
          if (!(e instanceof TypeError)) {
            sink.writer.abort("error");
            reject(e);
          } else {
            useJSFetch = false;
          }
        });
    }

    if (!useJSFetch) {
      await prepare(details);
      if (details instanceof Request) {
        request = {};
        for (const key in details) request[key] = details[key];
      } else {
        request = details;
      }
      if (details.headers instanceof Headers)
        request.headers = Object.fromEntries(details.headers);

      const origin = new URL(details.url).origin;
      if (
        location.origin == origin &&
        GM_xmlhttpRequest.addCookie &&
        !("cookie" in details) &&
        details.anonymous !== true
      ) {
        if (GM_cookie.store.length == 0) {
          await GM_cookie.list();
        }
        request.cookie = GM_cookie.export(details.url);
        GM_xmlhttpRequest.addCookie = false;
      }
      if (typeof request.cookie == "string") {
        request.cookie = request.cookie.split("; ");
      }

      if (!Array.isArray(request.cookie)) delete request.cookie;
      ChromeXt.dispatch("xmlhttpRequest", {
        id: GM_info.script.id,
        request,
        uuid,
      });
      ChromeXt.addEventListener("xmlhttpRequest", listener);
    }
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
      switch (data.responseType) {
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
        let charset = type.split(";").filter((it) => it.includes("charset="));
        if (charset.length != 0) {
          charset = charset[0].trim().substring(8).toLowerCase();
        } else {
          charset = "utf-8";
        }
        const decoder = new TextDecoder(charset);
        const blob = new Blob(data.response);
        data.response = decoder.decode(await blob.arrayBuffer());
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
    if (data.fetch) data.response = new Response(data.response, data);
  }
  parse(data) {
    if (typeof data != "object") return;
    for (const prop in data) {
      if (prop == "headers" || prop == "status") continue;
      const val = data[prop];
      if (typeof val == "function") continue;
      this.xhr[prop] = val;
    }
    if (this.xhr.status == data.status) return;
    // status change if there are redirections

    this.xhr.status = data.status;

    let headers = data.headers;
    if (!(headers instanceof Headers)) {
      const entries = Object.entries(headers);
      headers = new Headers();
      entries.forEach(([k, vs]) => {
        for (const v of vs) headers.append(k, v);
      });
    }
    this.xhr.headers = headers;
    this.xhr.readyState = 2;
    const responseHeaders = Object.entries(Object.fromEntries(headers))
      .map(([k, v]) => k.toLowerCase() + ": " + v)
      .join("\r\n");
    this.xhr.responseHeaders = responseHeaders;
    this.xhr.getAllResponseHeaders = () => responseHeaders;
    this.xhr.getResponseHeader = (headerName) => headers.get(headerName);
    this.xhr.finalUrl = headers.get("Location") || this.xhr.url;
    this.xhr.responseURL = this.xhr.finalUrl;
    this.xhr.total = headers.get("Content-Length");
    if (this.xhr.total !== null) {
      this.xhr.lengthComputable = true;
      this.xhr.total = Number(this.xhr.total);
    }
    if (this.xhr.finalUrl != this.xhr.url && this.xhr.redirect != "error")
      this.dispatch("redirect", { ...this.xhr });
    if (data instanceof Response) return;
    const encoding = headers.get("Content-Encoding");
    if (encoding != null) {
      try {
        this.ds = new DecompressionStream(encoding.toLowerCase());
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
    if (chunk == undefined) return;
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
    if (this.#writer == undefined) return;
    const type =
      this.xhr.overrideMimeType ||
      this.xhr.headers.get("Content-Type") ||
      "text/xml";
    if (
      Array.isArray(this.xhr.response) &&
      this.xhr.response.length != 0 &&
      this.ds instanceof DecompressionStream
    ) {
      const stream = new Blob(this.xhr.response, { type })
        .stream()
        .pipeThrough(this.ds);
      this.xhr.response = [];
      const reader = stream.getReader();
      while (true) {
        const result = await reader.read();
        if (result.done) break;
        this.xhr.response.push(result.value);
      }
      reader.cancel();
    }
    this.xhr.readyState = 4;
    let parseError;
    try {
      await ResponseSink.prepare(type, this.xhr);
    } catch (e) {
      this.xhr.error = e;
      parseError = e;
    }
    this.dispatch("load");
    this.dispatch("loadend");
    if (parseError instanceof Error) throw parseError;
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
  if (ChromeXt.scripts.findIndex((e) => e.script.id == GM_info.script.id) != -1)
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
    grants.includes("unsafeWindow")
  ) {
    GM.globalThis = window;
  } else {
    const handler = {
      // A handler to block access to globalThis
      window: { GM },
      libLoading: meta.requires.length > 0,
      keys: Array.from(ChromeXt.globalKeys),
      deleteProperty(_target, prop) {
        if (this.libLoading && prop == "__loading__") {
          this.libLoading = false;
          if (prop in this.window) return;
        }
        return delete this.window[prop];
      },
      set(target, prop, value) {
        if (target[prop] != value || target.propertyIsEnumerable(prop)) {
          // Avoid redefining global non-enumerable classes, though they are accessible to the getter
          this.window[prop] = value;
        }
        if (this.libLoading) Reflect.set(...arguments);
        return true;
      },
      get(target, prop, receiver) {
        if (target[prop] == target) return receiver;
        // Block possible jail break
        if (this.keys.includes(prop)) {
          const val = target[prop];
          return typeof val == "function" ? val.bind(target) : val;
        } else if (
          typeof target[prop] != "undefined" &&
          !target.propertyIsEnumerable(prop)
        ) {
          // Should never change the binding property of global non-enumerable classes
          return Reflect.get(...arguments);
        } else {
          return this.window[prop];
        }
      },
    };
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
      const result = Reflect.deleteProperty(target, key);
      this.sync({ key });
      return result;
    },
    set(target, key, value) {
      target[key] = value;
      this.sync({ key, value });
      return true;
    },
    async_get(key, default_value) {
      if (!this.cache.has(key)) {
        this.cache.add(key);
        const value = this.storage[key];
        return new Promise((resolve) =>
          resolve(value != undefined ? value : default_value)
        );
      }
      const id = Math.random();
      this.sync({ key, id, broadcast: false });
      return promiseListenerFactory(
        "scriptSyncValue",
        GM_info.uuid,
        meta.id,
        (data, resolve, _reject) => {
          if (data.value == undefined) data.value = default_value;
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
    if (typeof GM_xmlhttpRequest == "function" && meta.resources.length > 0) {
      meta.sync_code = meta.code;
      meta.code = async () => {
        for (const data of meta.resources) {
          data.content = await GM_xmlhttpRequest({ url: data.url });
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
    GM_info.version = "3.8.0";
    Object.freeze(GM_info);
    ChromeXt.scripts.push(GM_info);
  }
};

const key = Symbol("key");
GM.ChromeXtLock = class {
  #key = key;
  #ChromeXt;
  constructor(GM) {
    if (
      typeof GM.key == "number" &&
      typeof GM.name == "string" &&
      Symbol[GM.name].isLocked()
    ) {
      this.#ChromeXt = Symbol[GM.name].unlock(GM.key, false);
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
