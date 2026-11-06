const PANEL_READY = true;

async function probe() {
  const out = {};
  out.id = chrome.runtime.id;
  out.name = chrome.runtime.getManifest().name;
  out.manifestVersion = chrome.runtime.getManifest().manifest_version;
  out.getURL = chrome.runtime.getURL("background.json");
  out.i18n = chrome.i18n.getMessage("hello");
  out.i18nSub = chrome.i18n.getMessage("greet", ["ChromeXt"]);

  await chrome.storage.local.set({ counter: 42, nested: { a: [1, 2, 3] } });
  out.storage = await chrome.storage.local.get(["counter", "nested"]);
  out.storageAll = (await chrome.storage.local.get(null)).counter === 42;

  const resource = await fetch(chrome.runtime.getURL("background.json"));
  out.resource = await resource.json();

  try {
    const tabs = await chrome.tabs.query({});
    out.tabs = tabs.map((tab) => (tab.url || "").slice(0, 60)).slice(0, 3);
  } catch (error) {
    out.tabs = "error: " + error.message;
  }
  return out;
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log("CXBG got " + JSON.stringify(message) + " sender=" + JSON.stringify(sender));
  if (!message || typeof message !== "object") return;
  if (message.type === "ping") {
    sendResponse({ pong: true, from: "background", at: Date.now() });
    return;
  }
  if (message.type === "probe") {
    probe().then(sendResponse, (error) => sendResponse({ error: String(error) }));
    return true;
  }
  if (message.type === "cosmetic") {
    // Hide the probe element on this origin from now on, through the ChromeXt namespace
    const origin = sender && sender.url ? new URL(sender.url).origin : "*";
    chrome.chromeXt
      .addCosmeticRules(origin, ["#cx-hide-target"])
      .then(() => sendResponse({ registered: true }), (error) => sendResponse({ error: String(error) }));
    return true;
  }
  if (message.type === "tabsMessage" && sender.tab) {
    chrome.tabs.sendMessage(
      sender.tab.id,
      { type: "fromBackground", text: "background → content 消息到达" },
      (response) => {
        if (chrome.runtime.lastError) console.warn(chrome.runtime.lastError);
      }
    );
    sendResponse({ dispatched: true });
    return;
  }
});

chrome.runtime.onConnect.addListener((port) => {
  port.onMessage.addListener((message) => {
    port.postMessage({ echo: message, from: "background" });
  });
});

// webRequest: ChromeXt only sees top level navigations, so that is what fires here
console.log("CXBG webRequest api: " + typeof chrome.webRequest);
if (chrome.webRequest && chrome.webRequest.onBeforeRequest) {
  chrome.webRequest.onBeforeRequest.addListener(
    (details) => {
      console.log("CXBG webRequest " + details.type + " " + details.url);
      if (details.url.indexOf("/wr-block") !== -1) {
        console.log("CXBG webRequest cancels " + details.url);
        return { cancel: true };
      }
      return {};
    },
    { urls: ["*://*/*"], types: ["main_frame"] },
    ["blocking"]
  );
}
