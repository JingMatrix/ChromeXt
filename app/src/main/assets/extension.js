class StubEvent {
  addListener() {
    return stub(this, arguments);
  }
  removeListener() {
    return stub(this, arguments);
  }
  hasListener() {
    return stub(this, arguments);
  }
  hasListeners() {
    return stub(this, arguments);
  }
  dispatch() {
    return stub(this, arguments);
  }
}

class StubState {
  get() {
    return stub(this, arguments);
  }
  set() {
    return stub(this, arguments);
  }
  clear() {
    return stub(this, arguments);
  }
  onChange = new StubEvent();
}

class StubStorage extends StubState {
  remove() {
    return stub(this, arguments);
  }
  getBytesInUse() {
    return stub(this, arguments);
  }
}

chrome = {
  loadTimes() {
    return stub(this, arguments);
  },
  csi() {
    return stub(this, arguments);
  },
  app: {
    isInstalled: false,
    getDetails() {
      return stub(this, arguments);
    },
    getIsInstalled() {
      return stub(this, arguments);
    },
    installState() {
      return stub(this, arguments);
    },
    runningState() {
      return stub(this, arguments);
    },
    InstallState: {
      DISABLED: "disabled",
      INSTALLED: "installed",
      NOT_INSTALLED: "not_installed",
    },
    RunningState: {
      CANNOT_RUN: "cannot_run",
      READY_TO_RUN: "ready_to_run",
      RUNNING: "running",
    },
  },
  browserAction: {
    onClicked: new StubEvent(),
    disable() {
      return stub(this, arguments);
    },
    enable() {
      return stub(this, arguments);
    },
    getBadgeBackgroundColor() {
      return stub(this, arguments);
    },
    getBadgeText() {
      return stub(this, arguments);
    },
    getPopup() {
      return stub(this, arguments);
    },
    getTitle() {
      return stub(this, arguments);
    },
    setBadgeBackgroundColor() {
      return stub(this, arguments);
    },
    setBadgeText() {
      return stub(this, arguments);
    },
    setIcon() {
      return stub(this, arguments);
    },
    setPopup() {
      return stub(this, arguments);
    },
    setTitle() {
      return stub(this, arguments);
    },
  },
  commands: {
    onCommand: new StubEvent(),
    getAll() {
      return stub(this, arguments);
    },
  },
  contextMenus: {
    onClicked: new StubEvent(),
    create() {
      return stub(this, arguments);
    },
    remove() {
      return stub(this, arguments);
    },
    removeAll() {
      return stub(this, arguments);
    },
    update() {
      return stub(this, arguments);
    },
    ContextType: {
      ACTION: "action",
      ALL: "all",
      AUDIO: "audio",
      BROWSER_ACTION: "browser_action",
      EDITABLE: "editable",
      FRAME: "frame",
      IMAGE: "image",
      LAUNCHER: "launcher",
      LINK: "link",
      PAGE: "page",
      PAGE_ACTION: "page_action",
      SELECTION: "selection",
      VIDEO: "video",
    },
    ItemType: {
      CHECKBOX: "checkbox",
      NORMAL: "normal",
      RADIO: "radio",
      SEPARATOR: "separator",
    },
    ACTION_MENU_TOP_LEVEL_LIMIT: 6,
  },
  dom: {
    openOrClosedShadowRoot() {
      return stub(this, arguments);
    },
  },
  extension: {
    onRequestExternal: new StubEvent(),
    onRequest: new StubEvent(),
    getBackgroundPage() {
      return stub(this, arguments);
    },
    getExtensionTabs() {
      return stub(this, arguments);
    },
    getURL() {
      return stub(this, arguments);
    },
    getViews() {
      return stub(this, arguments);
    },
    isAllowedFileSchemeAccess() {
      return stub(this, arguments);
    },
    isAllowedIncognitoAccess() {
      return stub(this, arguments);
    },
    sendRequest() {
      return stub(this, arguments);
    },
    setUpdateUrlData() {
      return stub(this, arguments);
    },
    ViewType: { POPUP: "popup", TAB: "tab" },
    inIncognitoContext: false,
    connect() {
      return stub(this, arguments);
    },
    sendMessage() {
      return stub(this, arguments);
    },
    onConnect: new StubEvent(),
    onConnectExternal: new StubEvent(),
    onMessage: new StubEvent(),
    onMessageExternal: new StubEvent(),
  },
  i18n: {
    detectLanguage() {
      return stub(this, arguments);
    },
    getAcceptLanguages() {
      return stub(this, arguments);
    },
    getMessage() {
      return stub(this, arguments);
    },
    getUILanguage() {
      return stub(this, arguments);
    },
  },
  management: {
    getPermissionWarningsByManifest() {
      return stub(this, arguments);
    },
    getSelf() {
      return stub(this, arguments);
    },
    uninstallSelf() {
      return stub(this, arguments);
    },
    ExtensionDisabledReason: {
      PERMISSIONS_INCREASE: "permissions_increase",
      UNKNOWN: "unknown",
    },
    ExtensionInstallType: {
      ADMIN: "admin",
      DEVELOPMENT: "development",
      NORMAL: "normal",
      OTHER: "other",
      SIDELOAD: "sideload",
    },
    ExtensionType: {
      EXTENSION: "extension",
      HOSTED_APP: "hosted_app",
      LEGACY_PACKAGED_APP: "legacy_packaged_app",
      LOGIN_SCREEN_EXTENSION: "login_screen_extension",
      PACKAGED_APP: "packaged_app",
      THEME: "theme",
    },
    LaunchType: {
      OPEN_AS_PINNED_TAB: "OPEN_AS_PINNED_TAB",
      OPEN_AS_REGULAR_TAB: "OPEN_AS_REGULAR_TAB",
      OPEN_AS_WINDOW: "OPEN_AS_WINDOW",
      OPEN_FULL_SCREEN: "OPEN_FULL_SCREEN",
    },
  },
  permissions: {
    onRemoved: new StubEvent(),
    onAdded: new StubEvent(),
    contains() {
      return stub(this, arguments);
    },
    getAll() {
      return stub(this, arguments);
    },
    remove() {
      return stub(this, arguments);
    },
    request() {
      return stub(this, arguments);
    },
  },
  privacy: {
    IPHandlingPolicy: {
      DEFAULT: "default",
      DEFAULT_PUBLIC_AND_PRIVATE_INTERFACES:
        "default_public_and_private_interfaces",
      DEFAULT_PUBLIC_INTERFACE_ONLY: "default_public_interface_only",
      DISABLE_NON_PROXIED_UDP: "disable_non_proxied_udp",
    },
    network: {
      webRTCIPHandlingPolicy: new StubState(),
      networkPredictionEnabled: new StubState(),
    },
    services: {
      translationServiceEnabled: new StubState(),
      spellingServiceEnabled: new StubState(),
      searchSuggestEnabled: new StubState(),
      safeBrowsingExtendedReportingEnabled: new StubState(),
      safeBrowsingEnabled: new StubState(),
      passwordSavingEnabled: new StubState(),
      autofillEnabled: new StubState(),
      autofillCreditCardEnabled: new StubState(),
      autofillAddressEnabled: new StubState(),
      alternateErrorPagesEnabled: new StubState(),
    },
    websites: {
      topicsEnabled: new StubState(),
      thirdPartyCookiesAllowed: new StubState(),
      referrersEnabled: new StubState(),
      privacySandboxEnabled: new StubState(),
      hyperlinkAuditingEnabled: new StubState(),
      fledgeEnabled: new StubState(),
      doNotTrackEnabled: new StubState(),
      adMeasurementEnabled: new StubState(),
    },
  },
  runtime: {
    onRestartRequired: new StubEvent(),
    onMessageExternal: new StubEvent(),
    onMessage: new StubEvent(),
    onConnectExternal: new StubEvent(),
    onConnect: new StubEvent(),
    onBrowserUpdateAvailable: new StubEvent(),
    onUpdateAvailable: new StubEvent(),
    onSuspendCanceled: new StubEvent(),
    onSuspend: new StubEvent(),
    onInstalled: new StubEvent(),
    onStartup: new StubEvent(),
    connect() {
      return stub(this, arguments);
    },
    getBackgroundPage() {
      return stub(this, arguments);
    },
    getManifest() {
      return stub(this, arguments);
    },
    getPackageDirectoryEntry() {
      return stub(this, arguments);
    },
    getPlatformInfo() {
      return stub(this, arguments);
    },
    getURL() {
      return stub(this, arguments);
    },
    openOptionsPage() {
      return stub(this, arguments);
    },
    reload() {
      return stub(this, arguments);
    },
    requestUpdateCheck() {
      return stub(this, arguments);
    },
    restart() {
      return stub(this, arguments);
    },
    restartAfterDelay() {
      return stub(this, arguments);
    },
    sendMessage() {
      return stub(this, arguments);
    },
    setUninstallURL() {
      return stub(this, arguments);
    },
    ContextType: {
      BACKGROUND: "BACKGROUND",
      OFFSCREEN_DOCUMENT: "OFFSCREEN_DOCUMENT",
      POPUP: "POPUP",
      TAB: "TAB",
    },
    OnInstalledReason: {
      CHROME_UPDATE: "chrome_update",
      INSTALL: "install",
      SHARED_MODULE_UPDATE: "shared_module_update",
      UPDATE: "update",
    },
    OnRestartRequiredReason: {
      APP_UPDATE: "app_update",
      OS_UPDATE: "os_update",
      PERIODIC: "periodic",
    },
    PlatformArch: {
      ARM: "arm",
      ARM64: "arm64",
      MIPS: "mips",
      MIPS64: "mips64",
      X86_32: "x86-32",
      X86_64: "x86-64",
    },
    PlatformNaclArch: {
      ARM: "arm",
      MIPS: "mips",
      MIPS64: "mips64",
      X86_32: "x86-32",
      X86_64: "x86-64",
    },
    PlatformOs: {
      ANDROID: "android",
      CROS: "cros",
      FUCHSIA: "fuchsia",
      LINUX: "linux",
      MAC: "mac",
      OPENBSD: "openbsd",
      WIN: "win",
    },
    RequestUpdateCheckStatus: {
      NO_UPDATE: "no_update",
      THROTTLED: "throttled",
      UPDATE_AVAILABLE: "update_available",
    },
  },
  storage: {
    sync: new StubStorage(),
    managed: new StubStorage(),
    local: new StubStorage(),
    onChanged: new StubEvent(),
    AccessLevel: {
      TRUSTED_AND_UNTRUSTED_CONTEXTS: "TRUSTED_AND_UNTRUSTED_CONTEXTS",
      TRUSTED_CONTEXTS: "TRUSTED_CONTEXTS",
    },
  },
  tabs: {
    onZoomChange: new StubEvent(),
    onReplaced: new StubEvent(),
    onRemoved: new StubEvent(),
    onAttached: new StubEvent(),
    onDetached: new StubEvent(),
    onHighlighted: new StubEvent(),
    onHighlightChanged: new StubEvent(),
    onActivated: new StubEvent(),
    onActiveChanged: new StubEvent(),
    onSelectionChanged: new StubEvent(),
    onMoved: new StubEvent(),
    onUpdated: new StubEvent(),
    onCreated: new StubEvent(),
    captureVisibleTab() {
      return stub(this, arguments);
    },
    connect() {
      return stub(this, arguments);
    },
    create() {
      return stub(this, arguments);
    },
    detectLanguage() {
      return stub(this, arguments);
    },
    discard() {
      return stub(this, arguments);
    },
    duplicate() {
      return stub(this, arguments);
    },
    executeScript() {
      return stub(this, arguments);
    },
    get() {
      return stub(this, arguments);
    },
    getAllInWindow() {
      return stub(this, arguments);
    },
    getCurrent() {
      return stub(this, arguments);
    },
    getSelected() {
      return stub(this, arguments);
    },
    getZoom() {
      return stub(this, arguments);
    },
    getZoomSettings() {
      return stub(this, arguments);
    },
    goBack() {
      return stub(this, arguments);
    },
    goForward() {
      return stub(this, arguments);
    },
    group() {
      return stub(this, arguments);
    },
    highlight() {
      return stub(this, arguments);
    },
    insertCSS() {
      return stub(this, arguments);
    },
    move() {
      return stub(this, arguments);
    },
    query() {
      return stub(this, arguments);
    },
    reload() {
      return stub(this, arguments);
    },
    remove() {
      return stub(this, arguments);
    },
    removeCSS() {
      return stub(this, arguments);
    },
    sendMessage() {
      return stub(this, arguments);
    },
    sendRequest() {
      return stub(this, arguments);
    },
    setZoom() {
      return stub(this, arguments);
    },
    setZoomSettings() {
      return stub(this, arguments);
    },
    ungroup() {
      return stub(this, arguments);
    },
    update() {
      return stub(this, arguments);
    },
    MutedInfoReason: {
      CAPTURE: "capture",
      EXTENSION: "extension",
      USER: "user",
    },
    TabStatus: {
      COMPLETE: "complete",
      LOADING: "loading",
      UNLOADED: "unloaded",
    },
    WindowType: {
      APP: "app",
      DEVTOOLS: "devtools",
      NORMAL: "normal",
      PANEL: "panel",
      POPUP: "popup",
    },
    ZoomSettingsMode: {
      AUTOMATIC: "automatic",
      DISABLED: "disabled",
      MANUAL: "manual",
    },
    ZoomSettingsScope: { PER_ORIGIN: "per-origin", PER_TAB: "per-tab" },
    MAX_CAPTURE_VISIBLE_TAB_CALLS_PER_SECOND: 2,
    TAB_ID_NONE: -1,
  },
  webNavigation: {
    onHistoryStateUpdated: new StubEvent(),
    onTabReplaced: new StubEvent(),
    onReferenceFragmentUpdated: new StubEvent(),
    onCreatedNavigationTarget: new StubEvent(),
    onErrorOccurred: new StubEvent(),
    onCompleted: new StubEvent(),
    onDOMContentLoaded: new StubEvent(),
    onCommitted: new StubEvent(),
    onBeforeNavigate: new StubEvent(),
    getAllFrames() {
      return stub(this, arguments);
    },
    getFrame() {
      return stub(this, arguments);
    },
    TransitionQualifier: {
      CLIENT_REDIRECT: "client_redirect",
      FORWARD_BACK: "forward_back",
      FROM_ADDRESS_BAR: "from_address_bar",
      SERVER_REDIRECT: "server_redirect",
    },
    TransitionType: {
      AUTO_BOOKMARK: "auto_bookmark",
      AUTO_SUBFRAME: "auto_subframe",
      FORM_SUBMIT: "form_submit",
      GENERATED: "generated",
      KEYWORD: "keyword",
      KEYWORD_GENERATED: "keyword_generated",
      LINK: "link",
      MANUAL_SUBFRAME: "manual_subframe",
      RELOAD: "reload",
      START_PAGE: "start_page",
      TYPED: "typed",
    },
  },
  webRequest: {
    onActionIgnored: new StubEvent(),
    onErrorOccurred: new StubEvent(),
    onCompleted: new StubEvent(),
    onBeforeRedirect: new StubEvent(),
    onResponseStarted: new StubEvent(),
    onAuthRequired: new StubEvent(),
    onHeadersReceived: new StubEvent(),
    onSendHeaders: new StubEvent(),
    onBeforeSendHeaders: new StubEvent(),
    onBeforeRequest: new StubEvent(),
    handlerBehaviorChanged() {
      return stub(this, arguments);
    },
    IgnoredActionType: {
      AUTH_CREDENTIALS: "auth_credentials",
      REDIRECT: "redirect",
      REQUEST_HEADERS: "request_headers",
      RESPONSE_HEADERS: "response_headers",
    },
    OnAuthRequiredOptions: {
      ASYNC_BLOCKING: "asyncBlocking",
      BLOCKING: "blocking",
      EXTRA_HEADERS: "extraHeaders",
      RESPONSE_HEADERS: "responseHeaders",
    },
    OnBeforeRedirectOptions: {
      EXTRA_HEADERS: "extraHeaders",
      RESPONSE_HEADERS: "responseHeaders",
    },
    OnBeforeRequestOptions: {
      BLOCKING: "blocking",
      EXTRA_HEADERS: "extraHeaders",
      REQUEST_BODY: "requestBody",
    },
    OnBeforeSendHeadersOptions: {
      BLOCKING: "blocking",
      EXTRA_HEADERS: "extraHeaders",
      REQUEST_HEADERS: "requestHeaders",
    },
    OnCompletedOptions: {
      EXTRA_HEADERS: "extraHeaders",
      RESPONSE_HEADERS: "responseHeaders",
    },
    OnErrorOccurredOptions: { EXTRA_HEADERS: "extraHeaders" },
    OnHeadersReceivedOptions: {
      BLOCKING: "blocking",
      EXTRA_HEADERS: "extraHeaders",
      RESPONSE_HEADERS: "responseHeaders",
    },
    OnResponseStartedOptions: {
      EXTRA_HEADERS: "extraHeaders",
      RESPONSE_HEADERS: "responseHeaders",
    },
    OnSendHeadersOptions: {
      EXTRA_HEADERS: "extraHeaders",
      REQUEST_HEADERS: "requestHeaders",
    },
    ResourceType: {
      CSP_REPORT: "csp_report",
      FONT: "font",
      IMAGE: "image",
      MAIN_FRAME: "main_frame",
      MEDIA: "media",
      OBJECT: "object",
      OTHER: "other",
      PING: "ping",
      SCRIPT: "script",
      STYLESHEET: "stylesheet",
      SUB_FRAME: "sub_frame",
      WEBBUNDLE: "webbundle",
      WEBSOCKET: "websocket",
      XMLHTTPREQUEST: "xmlhttprequest",
    },
    MAX_HANDLER_BEHAVIOR_CHANGED_CALLS_PER_10_MINUTES: 20,
  },
  windows: {
    onBoundsChanged: new StubEvent(),
    onFocusChanged: new StubEvent(),
    onRemoved: new StubEvent(),
    onCreated: new StubEvent(),
    create() {
      return stub(this, arguments);
    },
    get() {
      return stub(this, arguments);
    },
    getAll() {
      return stub(this, arguments);
    },
    getCurrent() {
      return stub(this, arguments);
    },
    getLastFocused() {
      return stub(this, arguments);
    },
    remove() {
      return stub(this, arguments);
    },
    update() {
      return stub(this, arguments);
    },
    CreateType: { NORMAL: "normal", PANEL: "panel", POPUP: "popup" },
    WindowState: {
      FULLSCREEN: "fullscreen",
      LOCKED_FULLSCREEN: "locked-fullscreen",
      MAXIMIZED: "maximized",
      MINIMIZED: "minimized",
      NORMAL: "normal",
    },
    WindowType: {
      APP: "app",
      DEVTOOLS: "devtools",
      NORMAL: "normal",
      PANEL: "panel",
      POPUP: "popup",
    },
    WINDOW_ID_CURRENT: -2,
    WINDOW_ID_NONE: -1,
  },
};

const inFrame = typeof globalThis.ChromeXt == "undefined";
chrome.runtime.getManifest = () => extension;
chrome.runtime.id = extension.id;
chrome.runtime.getURL = (path) => location.origin + "/" + path;

function stub(_self, args) {
  try {
    console.debug(
      new Error(args.callee.name + " is not implement yet by ChromeXt")
    );
  } catch {
    console.debug(new Error("Stub class is not implement yet by ChromeXt"));
  }
}

// Use the following code to get the stub definitions of chrome
// JSON.stringify(chrome, replacer)
function replacer(_key, value) {
  if (typeof value === "function") {
    return "{return stub(this,arguments)}";
  } else if (typeof value == "object") {
    const name = value.__proto__.constructor.name;
    if (name != "Object" && name != "") {
      return `new ${name}()`;
    }
  }
  return value;
}
