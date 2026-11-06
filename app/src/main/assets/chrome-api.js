// WebExtensions API shim shared by content scripts and background pages.
// __CHROMEXT_CFG__ must be defined by the injector, see ChromeApi.kt.
function __chromextCreateApi(cfg) {
  "use strict";

  const ID = cfg.id;
  const ORIGIN = cfg.origin;
  const MANIFEST = cfg.manifest;
  const CONTENT = cfg.transport === "content";
  const MESSAGES = (cfg.i18n && cfg.i18n.messages) || {};
  const LOCALE = (cfg.i18n && cfg.i18n.locale) || "en";
  const TIMEOUT = 30000;

  const registry =
    globalThis.__chromextPending || (globalThis.__chromextPending = new Map());
  const hooks =
    globalThis.__chromextHooks || (globalThis.__chromextHooks = { deliver: {} });

  function raw(payload) {
    // __chromextBridge is injected by ChromeApi.bootstrap for content scripts,
    // background pages talk to the host page that embeds them
    if (CONTENT) __chromextBridge(payload);
    else parent.postMessage({ __chromext: payload }, "*");
  }

  function post(action, args, uuid) {
    return new Promise((resolve, reject) => {
      const id =
        uuid || ID + ":" + Math.random().toString(36).slice(2) + ":" + Date.now().toString(36);
      const timer = setTimeout(() => {
        registry.delete(id);
        reject(new Error("ChromeXt: " + action + " timed out"));
      }, TIMEOUT);
      registry.set(id, {
        resolve: (v) => {
          clearTimeout(timer);
          resolve(v);
        },
        reject: (e) => {
          clearTimeout(timer);
          reject(e);
        },
      });
      try {
        raw({ ext: ID, action: action, args: args, uuid: id });
      } catch (e) {
        clearTimeout(timer);
        registry.delete(id);
        reject(e);
      }
    });
  }

  function respond(uuid, ok, value) {
    raw({ ext: ID, action: "runtime.respond", args: [], uuid: uuid, ok: ok, value: value });
  }

  globalThis.__chromextResolve = function (uuid, ok, value) {
    const entry = registry.get(uuid);
    if (!entry) return;
    registry.delete(uuid);
    let parsed;
    try {
      parsed = JSON.parse(value);
    } catch (e) {
      parsed = value;
    }
    if (ok) entry.resolve(parsed);
    else entry.reject(new Error(parsed && parsed.message ? parsed.message : parsed));
  };

  let lastError;
  function wrap(promise, callback) {
    if (typeof callback !== "function") return promise;
    promise.then(
      (value) => {
        lastError = undefined;
        callback(value);
      },
      (error) => {
        lastError = { message: error && error.message ? error.message : String(error) };
        callback(undefined);
      }
    );
    return undefined;
  }

  function event() {
    const listeners = [];
    return {
      addListener: (fn) => {
        if (typeof fn === "function" && !listeners.includes(fn)) listeners.push(fn);
      },
      removeListener: (fn) => {
        const index = listeners.indexOf(fn);
        if (index >= 0) listeners.splice(index, 1);
      },
      hasListener: (fn) => listeners.includes(fn),
      hasListeners: () => listeners.length > 0,
      _listeners: listeners,
    };
  }

  const onMessage = event();
  const onConnect = event();
  const onInstalled = event();
  const storageChanged = event();

  // ---- storage --------------------------------------------------------------

  const storageCache = { local: null, sync: null };
  const areaWatchers = [];

  function readArea(area) {
    return post("storage.get", [area, null]).then((data) => {
      storageCache[area] = data || {};
      return storageCache[area];
    });
  }

  function notifyChanged(changes, area) {
    const detail = { [area]: changes };
    storageChanged._listeners.slice().forEach((fn) => {
      try {
        fn(detail, area);
      } catch (e) {
        console.error(e);
      }
    });
  }

  function selectKeys(area, keys) {
    const source = storageCache[area] || {};
    if (keys === null || keys === undefined) return Object.assign({}, source);
    if (typeof keys === "string") return keys in source ? { [keys]: source[keys] } : {};
    if (Array.isArray(keys)) {
      const result = {};
      keys.forEach((key) => {
        if (key in source) result[key] = source[key];
      });
      return result;
    }
    const result = {};
    Object.keys(keys).forEach((key) => {
      result[key] = key in source ? source[key] : keys[key];
    });
    return result;
  }

  function storageArea(area) {
    return {
      get(keys, callback) {
        const ready = storageCache[area]
          ? Promise.resolve(storageCache[area])
          : readArea(area);
        return wrap(
          ready.then(() => selectKeys(area, keys)),
          callback
        );
      },
      set(items, callback) {
        const changes = {};
        Object.keys(items).forEach((key) => {
          changes[key] = { newValue: items[key] };
          if (storageCache[area] && key in storageCache[area])
            changes[key].oldValue = storageCache[area][key];
        });
        return wrap(
          post("storage.set", [area, items]).then(() => {
            storageCache[area] = Object.assign(storageCache[area] || {}, items);
            notifyChanged(changes, area);
          }),
          callback
        );
      },
      remove(keys, callback) {
        const list = typeof keys === "string" ? [keys] : keys;
        const changes = {};
        list.forEach((key) => {
          if (storageCache[area] && key in storageCache[area])
            changes[key] = { oldValue: storageCache[area][key] };
        });
        return wrap(
          post("storage.remove", [area, list]).then(() => {
            if (storageCache[area]) list.forEach((key) => delete storageCache[area][key]);
            notifyChanged(changes, area);
          }),
          callback
        );
      },
      clear(callback) {
        return wrap(
          post("storage.clear", [area]).then(() => {
            const changes = {};
            Object.keys(storageCache[area] || {}).forEach((key) => {
              changes[key] = { oldValue: storageCache[area][key] };
            });
            storageCache[area] = {};
            notifyChanged(changes, area);
          }),
          callback
        );
      },
      getBytesInUse(keys, callback) {
        return wrap(
          storageArea(area)
            .get(keys)
            .then((data) => JSON.stringify(data).length),
          typeof keys === "function" ? keys : callback
        );
      },
      onChanged: storageChanged,
    };
  }

  // ---- i18n -----------------------------------------------------------------

  function getMessage(name, substitutions) {
    const entry = MESSAGES[name];
    if (!entry || typeof entry.message !== "string") return "";
    const subs = substitutions === undefined ? [] : [].concat(substitutions);
    let text = entry.message;
    const placeholders = entry.placeholders || {};
    Object.keys(placeholders).forEach((key) => {
      const placeholder = placeholders[key];
      let content = placeholder && placeholder.content !== undefined ? placeholder.content : "";
      content = String(content).replace(/\$(\d)/g, (match, index) => {
        const value = subs[Number(index) - 1];
        return value === undefined ? "" : String(value);
      });
      text = text.split("$" + key + "$").join(content);
    });
    subs.forEach((value, index) => {
      text = text.split("$" + (index + 1)).join(String(value));
    });
    return text.replace(/\$\$/g, "$");
  }

  // ---- runtime messaging ----------------------------------------------------

  hooks.deliver[ID] = function (message, sender, uuid) {
    const listeners = onMessage._listeners.slice();
    let responded = false;
    let async = false;
    const sendResponse = (value) => {
      if (responded) return;
      responded = true;
      respond(uuid, true, value === undefined ? null : value);
    };
    for (const listener of listeners) {
      try {
        const result = listener(message, sender, sendResponse);
        if (result === true) async = true;
        else if (result && typeof result.then === "function") {
          async = true;
          result.then(
            (value) => {
              if (value !== undefined) sendResponse(value);
            },
            (error) => respond(uuid, false, String(error))
          );
        }
      } catch (e) {
        respond(uuid, false, String(e));
        return;
      }
    }
    if (!responded && !async) respond(uuid, true, null);
    return async;
  };

  const runtime = {
    id: ID,
    getManifest: () => MANIFEST,
    getURL: (path) => ORIGIN + "/" + String(path === undefined ? "" : path).replace(/^\/+/, ""),
    getPlatformInfo: (callback) =>
      wrap(
        Promise.resolve({
          os: "android",
          arch: "arm64",
          nacl_arch: "arm",
        }),
        callback
      ),
    sendMessage: (...args) => {
      const callback = typeof args[args.length - 1] === "function" ? args.pop() : null;
      if (args.length > 1 && typeof args[0] !== "undefined") {
        // sendMessage(extensionId, message, options, callback) is not supported
      }
      const message = args.length > 1 ? args[1] : args[0];
      const target = args.length > 1 ? args[0] : null;
      return wrap(post("runtime.sendMessage", [message, target]), callback);
    },
    connect: (info) => makePort(info),
    onMessage: onMessage,
    onMessageExternal: event(),
    onConnect: onConnect,
    onConnectExternal: event(),
    onInstalled: onInstalled,
    onStartup: event(),
    onSuspend: event(),
    get lastError() {
      return lastError;
    },
    setUninstallURL: () => {},
    reload: () => {},
  };

  // ---- ports ----------------------------------------------------------------

  let portSeq = 0;
  const openPorts = globalThis.__chromextPorts || (globalThis.__chromextPorts = {});

  function newPort(portId, name, sender) {
    const messageListeners = [];
    const disconnectListeners = [];
    const port = {
      name: name || "",
      sender: sender,
      postMessage: (message) => raw({ ext: ID, action: "port.post", args: [portId, message] }),
      disconnect: () => {
        raw({ ext: ID, action: "port.disconnect", args: [portId] });
        port._close();
      },
      onMessage: {
        addListener: (fn) => messageListeners.push(fn),
        removeListener: (fn) => {
          const index = messageListeners.indexOf(fn);
          if (index >= 0) messageListeners.splice(index, 1);
        },
        hasListener: (fn) => messageListeners.includes(fn),
      },
      onDisconnect: {
        addListener: (fn) => disconnectListeners.push(fn),
        removeListener: (fn) => {
          const index = disconnectListeners.indexOf(fn);
          if (index >= 0) disconnectListeners.splice(index, 1);
        },
        hasListener: (fn) => disconnectListeners.includes(fn),
      },
      _deliver: (message) => {
        messageListeners.slice().forEach((fn) => {
          try {
            fn(message, port);
          } catch (e) {
            console.error(e);
          }
        });
      },
      _close: () => {
        delete openPorts[portId];
        disconnectListeners.slice().forEach((fn) => {
          try {
            fn(port);
          } catch (e) {
            console.error(e);
          }
        });
      },
    };
    openPorts[portId] = port;
    return port;
  }

  function makePort(info) {
    const portId = ID + ":port:" + ++portSeq + ":" + Date.now().toString(36);
    const port = newPort(portId, info && info.name, undefined);
    raw({ ext: ID, action: "port.connect", args: [portId, port.name] });
    return port;
  }

  // Incoming ports, created when a content script opens a connection
  hooks.portOpen = hooks.portOpen || {};
  hooks.portOpen[ID] = function (portId, name, sender) {
    const port = newPort(portId, name, sender);
    onConnect._listeners.slice().forEach((fn) => {
      try {
        fn(port);
      } catch (e) {
        console.error(e);
      }
    });
  };

  globalThis.__chromextPortMessage = function (portId, message) {
    const port = openPorts[portId];
    if (port && port._deliver) port._deliver(message);
  };

  globalThis.__chromextPortDisconnect = function (portId) {
    const port = openPorts[portId];
    if (port && port._close) port._close();
  };

  // ---- tabs -----------------------------------------------------------------

  const tabs = {
    TAB_ID_NONE: -1,
    query: (info, callback) => wrap(post("tabs.query", [info || {}]), callback),
    get: (tabId, callback) => wrap(post("tabs.get", [tabId]), callback),
    getCurrent: (callback) => wrap(post("tabs.getCurrent", []), callback),
    create: (info, callback) => wrap(post("tabs.create", [info || {}]), callback),
    update: (tabId, info, callback) => {
      if (typeof tabId === "object") {
        callback = info;
        info = tabId;
        tabId = undefined;
      }
      return wrap(post("tabs.update", [tabId, info || {}]), callback);
    },
    remove: (tabId, callback) => wrap(post("tabs.remove", [tabId]), callback),
    reload: (tabId, info, callback) => wrap(post("tabs.reload", [tabId]), callback),
    sendMessage: (...args) => {
      const callback = typeof args[args.length - 1] === "function" ? args.pop() : null;
      const tabId = args[0];
      const message = args[args.length - 1];
      return wrap(post("tabs.sendMessage", [tabId, message]), callback);
    },
    executeScript: (tabId, details, callback) => {
      if (typeof tabId === "object") {
        callback = details;
        details = tabId;
        tabId = undefined;
      }
      return wrap(post("tabs.executeScript", [tabId, details || {}]), callback);
    },
    insertCSS: (tabId, details, callback) => {
      if (typeof tabId === "object") {
        callback = details;
        details = tabId;
        tabId = undefined;
      }
      return wrap(post("tabs.insertCSS", [tabId, details || {}]), callback);
    },
    onUpdated: event(),
    onCreated: event(),
    onRemoved: event(),
    onActivated: event(),
  };

  // ---- action / contextMenus ------------------------------------------------

  function actionApi() {
    return {
      setBadgeText: (details, callback) => wrap(post("action.setBadgeText", [details]), callback),
      setBadgeBackgroundColor: (details, callback) =>
        wrap(post("action.setBadgeBackgroundColor", [details]), callback),
      setTitle: (details, callback) => wrap(post("action.setTitle", [details]), callback),
      setIcon: (details, callback) => wrap(post("action.setIcon", [details]), callback),
      setPopup: (details, callback) => wrap(post("action.setPopup", [details]), callback),
      enable: (tabId) => post("action.enable", [tabId]),
      disable: (tabId) => post("action.disable", [tabId]),
      onClicked: event(),
    };
  }

  const contextMenus = {
    create: (details, callback) => wrap(post("contextMenus.create", [details]), callback),
    update: (id, details, callback) => wrap(post("contextMenus.update", [id, details]), callback),
    remove: (id, callback) => wrap(post("contextMenus.remove", [id]), callback),
    removeAll: (callback) => wrap(post("contextMenus.removeAll", []), callback),
    onClicked: event(),
    ACTION_MENU_TOP_LEVEL_LIMIT: 6,
  };

  const extension = {
    getURL: runtime.getURL,
    getBackgroundPage: (callback) => wrap(post("extension.getBackgroundPage", []), callback),
    sendMessage: runtime.sendMessage,
    isAllowedIncognitoAccess: (callback) => wrap(Promise.resolve(false), callback),
    isAllowedFileSchemeAccess: (callback) => wrap(Promise.resolve(false), callback),
    inIncognitoContext: false,
  };

  const permissions = {
    contains: (perms, callback) => wrap(post("permissions.contains", [perms]), callback),
    getAll: (callback) => wrap(post("permissions.getAll", []), callback),
    request: (perms, callback) => wrap(post("permissions.request", [perms]), callback),
    remove: (perms, callback) => wrap(post("permissions.remove", [perms]), callback),
    onAdded: event(),
    onRemoved: event(),
  };

  const webNavigation = {
    getFrame: (details, callback) => wrap(post("webNavigation.getFrame", [details]), callback),
    getAllFrames: (details, callback) =>
      wrap(post("webNavigation.getAllFrames", [details]), callback),
    onBeforeNavigate: event(),
    onCommitted: event(),
    onCompleted: event(),
    onDOMContentLoaded: event(),
    onErrorOccurred: event(),
  };

  const api = {
    runtime: runtime,
    storage: {
      local: storageArea("local"),
      sync: storageArea("sync"),
      managed: storageArea("managed"),
      session: storageArea("session"),
      onChanged: storageChanged,
    },
    i18n: {
      getMessage: getMessage,
      getUILanguage: () => LOCALE,
      getAcceptLanguages: (callback) =>
        wrap(Promise.resolve([LOCALE, "en"]), callback),
      detectLanguage: (text, callback) => wrap(Promise.resolve({ isReliable: true, languages: [{ language: LOCALE, percentage: 100 }] }), callback),
    },
    tabs: tabs,
    extension: extension,
    action: actionApi(),
    browserAction: actionApi(),
    pageAction: actionApi(),
    contextMenus: contextMenus,
    permissions: permissions,
    webNavigation: webNavigation,
    management: {
      getSelf: (callback) =>
        wrap(
          Promise.resolve({
            id: ID,
            name: MANIFEST.name,
            version: MANIFEST.version,
            enabled: true,
            mayDisable: false,
            type: "extension",
            installType: "development",
          }),
          callback
        ),
      uninstallSelf: (options, callback) => wrap(post("management.uninstallSelf", [options]), callback),
    },
    alarms: {
      create: (name, info) => raw({ ext: ID, action: "alarms.create", args: [name, info] }),
      clear: (name, callback) => wrap(post("alarms.clear", [name]), callback),
      onAlarm: event(),
    },
    idle: {
      onStateChanged: event(),
      queryState: (threshold, callback) => wrap(Promise.resolve("active"), callback),
      setDetectionInterval: () => {},
    },
    notifications: {
      create: (id, options, callback) => wrap(post("notifications.create", [id, options]), callback),
      clear: (id, callback) => wrap(post("notifications.clear", [id]), callback),
      onClicked: event(),
    },
    scripting: {
      executeScript: (details, callback) =>
        wrap(post("tabs.executeScript", [details && details.target && details.target.tabId, details]), callback),
      insertCSS: (details, callback) =>
        wrap(post("tabs.insertCSS", [details && details.target && details.target.tabId, details]), callback),
    },
  };

  // Commands pushed by Kotlin for this tab: replies, messages, port traffic and raw code
  function maybeParse(value) {
    if (typeof value !== "string") return value;
    if (value === "") return null;
    try {
      return JSON.parse(value);
    } catch (error) {
      return value;
    }
  }

  function handleCommand(item) {
    if (!item) return;
    try {
      if (item.type === "resolve") {
        globalThis.__chromextResolve(item.uuid, item.ok, item.value);
      } else if (item.type === "deliver") {
        const deliver = hooks.deliver[ID];
        if (deliver) deliver(maybeParse(item.message), item.sender, item.uuid);
      } else if (item.type === "portMessage") {
        if (globalThis.__chromextPortMessage)
          globalThis.__chromextPortMessage(item.portId, maybeParse(item.message));
      } else if (item.type === "portDisconnect") {
        if (globalThis.__chromextPortDisconnect) globalThis.__chromextPortDisconnect(item.portId);
      } else if (item.type === "webRequest") {
        const hook = (globalThis.__chromextWebRequest || {})[item.ext];
        console.log("CX webRequest " + item.kind + " hook=" + !!hook);
        if (hook) hook(item.kind, item.details, item.requestId);
      } else if (item.type === "eval") {
        (0, eval)(item.code);
      }
    } catch (error) {
      console.error("chromext command failed", error, item);
    }
  }

  // Each extension polls for itself: the loop lives in this closure, so the page can host several
  // extensions without one of them draining the commands of another.
  if (CONTENT) {
    let busy = false;
    const tick = () => {
      if (busy) return;
      busy = true;
      const timeout = new Promise((resolve, reject) =>
        setTimeout(() => reject(new Error("poll timeout")), 4000)
      );
      Promise.race([post("runtime.poll", []), timeout])
        .then((items) => {
          (items || []).forEach(handleCommand);
        })
        .catch(() => {})
        .then(() => {
          busy = false;
          const delay = document.visibilityState === "visible" ? 400 : 1500;
          setTimeout(tick, delay);
        });
    };
    setTimeout(tick, 300 + Math.random() * 200);
  }

  // ---- webRequest -----------------------------------------------------------
  // Only top level navigations are visible to ChromeXt, so the events fire for those and the
  // blocking listener can answer {cancel: true} (applied with a short grace period).

  const webRequestEvents = {};
  function webRequestEvent(kind) {
    if (!webRequestEvents[kind]) {
      const base = event();
      const addListener = base.addListener;
      base.addListener = (fn, filter, extra) => {
        addListener(fn);
        raw({
          ext: ID,
          action: "webRequest.register",
          args: [kind, filter || {}, extra || []],
          uuid: "",
        });
      };
      webRequestEvents[kind] = base;
    }
    return webRequestEvents[kind];
  }

  function onWebRequest(kind, details, requestId) {
    const listeners = (webRequestEvents[kind] && webRequestEvents[kind]._listeners) || [];
    if (listeners.length === 0) {
      raw({ ext: ID, action: "webRequest.respond", args: [requestId, false], uuid: "" });
      return;
    }
    let cancel = false;
    for (const listener of listeners) {
      try {
        const result = listener(details);
        if (kind === "onBeforeRequest" && result && result.cancel) cancel = true;
        if (kind === "onBeforeRequest" && result && typeof result.redirectUrl === "string") {
          raw({ ext: ID, action: "webRequest.redirect", args: [requestId, result.redirectUrl], uuid: "" });
          return;
        }
      } catch (error) {
        console.error(error);
      }
    }
    raw({ ext: ID, action: "webRequest.respond", args: [requestId, cancel], uuid: "" });
  }
  api.__onWebRequest = onWebRequest;

  const webRequest = {
    onBeforeRequest: webRequestEvent("onBeforeRequest"),
    onBeforeSendHeaders: webRequestEvent("onBeforeSendHeaders"),
    onSendHeaders: webRequestEvent("onSendHeaders"),
    onHeadersReceived: webRequestEvent("onHeadersReceived"),
    onResponseStarted: webRequestEvent("onResponseStarted"),
    onBeforeRedirect: webRequestEvent("onBeforeRedirect"),
    onCompleted: webRequestEvent("onCompleted"),
    onErrorOccurred: webRequestEvent("onErrorOccurred"),
    onAuthRequired: webRequestEvent("onAuthRequired"),
    handlerBehaviorChanged: (callback) => wrap(Promise.resolve(), callback),
    MAX_HANDLER_BEHAVIOR_CHANGED_CALLS_PER_10_MINUTES: 20,
    ResourceType: {
      MAIN_FRAME: "main_frame",
      SUB_FRAME: "sub_frame",
      STYLESHEET: "stylesheet",
      SCRIPT: "script",
      IMAGE: "image",
      FONT: "font",
      OBJECT: "object",
      XMLHTTPREQUEST: "xmlhttprequest",
      PING: "ping",
      CSP_REPORT: "csp_report",
      MEDIA: "media",
      WEBSOCKET: "websocket",
      OTHER: "other",
    },
  };

  // ---- declarativeNetRequest ------------------------------------------------
  // Static rulesets live in the manifest and are matched by ChromeXt on the top level navigation;
  // sub-resources are out of reach, see Blocking.kt.

  const dnr = {
    onRuleMatchedDebug: event(),
    getDynamicRules: (callback) => wrap(post("dnr.getDynamicRules", []), callback),
    updateDynamicRules: (options, callback) => wrap(post("dnr.updateDynamicRules", [options || {}]), callback),
    getSessionRules: (callback) => wrap(Promise.resolve([]), callback),
    updateSessionRules: (options, callback) => wrap(Promise.resolve(), callback),
    getEnabledRulesets: (callback) =>
      wrap(
        Promise.resolve(
          Array.isArray(MANIFEST.declarative_net_request)
            ? (MANIFEST.declarative_net_request.rule_resources || []).map((r) => r.id)
            : []
        ),
        callback
      ),
    updateEnabledRulesets: (options, callback) => wrap(Promise.resolve(), callback),
    getAvailableStaticRuleCount: (callback) => wrap(Promise.resolve(30000), callback),
    isRegexSupported: (regexOptions, callback) => {
      let supported = true;
      try {
        new RegExp(regexOptions.regex);
      } catch (error) {
        supported = false;
      }
      return wrap(Promise.resolve({ isSupported: supported }), callback);
    },
    getMatchedRules: (filter, callback) => wrap(Promise.resolve({ rulesMatchedInfo: [] }), callback),
    RuleActionType: { BLOCK: "block", REDIRECT: "redirect", ALLOW: "allow", UPGRADE_SCHEME: "upgradeScheme", MODIFY_HEADERS: "modifyHeaders", ALLOW_ALL_REQUESTS: "allowAllRequests" },
    RequestMethod: { CONNECT: "connect", DELETE: "delete", GET: "get", HEAD: "head", OPTIONS: "options", PATCH: "patch", POST: "post", PUT: "put" },
    ResourceType: webRequest.ResourceType,
    RuleCondition: {},
    MAX_NUMBER_OF_DYNAMIC_AND_SESSION_RULES: 5000,
  };

  api.webRequest = webRequest;
  api.declarativeNetRequest = dnr;

  // ChromeXt specific escape hatch, namespaced so it cannot collide with a WebExtension API
  api.chromeXt = {
    addCosmeticRules: (origin, selectors, callback) =>
      wrap(post("cosmetic.add", [{ origin: origin || "*", selectors: selectors || [] }]), callback),
    version: () => MANIFEST.version,
  };

  api.browser = api;
  api.__handleCommand = handleCommand;
  (globalThis.__chromextWebRequest || (globalThis.__chromextWebRequest = {}))[ID] = onWebRequest;
  return api;
}
