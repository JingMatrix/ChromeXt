if (typeof ChromeXt == "undefined") {
  const ChromeXt = new EventTarget();
  const hidden = Symbol("debug");
  Object.defineProperty(ChromeXt, hidden, {
    value: console.debug.bind(console),
  });
  Object.defineProperties(ChromeXt, {
    scripts: { value: [] },
    commands: { value: [] },
    post: {
      value: function (event, detail) {
        ChromeXt.dispatchEvent(new CustomEvent(event, { detail }));
      },
    },
    dispatch: {
      value: function (action, payload) {
        this[hidden](JSON.stringify({ action, payload }));
      },
    },
  });
  Object.defineProperty(globalThis, "ChromeXt", { value: ChromeXt });
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
  ChromeXt.dispatch("loadEruda");
}
// Kotlin separator

if (ChromeXt.cspRules) {
  // Skip empty cspRules
  const meta = document.createElement("meta");
  meta.setAttribute("http-equiv", "Content-Security-Policy");
  meta.setAttribute("content", ChromeXt.cspRules);
  try {
    document.head.append(meta);
  } catch {
    setTimeout(() => {
      document.head.append(meta);
    }, 0);
  }
}
// Kotlin separator
