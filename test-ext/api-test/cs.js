(function () {
  const rows = [];
  function add(name, ok, detail) {
    const text = detail === undefined ? "" : String(detail);
    rows.push({ name: name, ok: ok, detail: text });
    console.log("CXTEST " + (ok ? "PASS" : "FAIL") + " [" + name + "] " + text.slice(0, 160));
  }

  function panel() {
    const root = document.createElement("div");
    root.id = "cx-test-panel";
    const header = document.createElement("header");
    const title = document.createElement("span");
    title.textContent = "ChromeXt 扩展自检";
    const close = document.createElement("button");
    close.textContent = "×";
    close.onclick = () => root.remove();
    header.append(title, close);
    root.append(header);
    const body = document.createElement("div");
    rows.forEach((row) => {
      const line = document.createElement("div");
      line.className = "row";
      const mark = document.createElement("b");
      mark.className = row.ok ? "ok" : "fail";
      mark.textContent = row.ok ? "✅" : "❌";
      const text = document.createElement("div");
      const name = document.createElement("div");
      name.textContent = row.name;
      text.append(name);
      if (row.detail) {
        const detail = document.createElement("div");
        detail.className = "detail";
        detail.textContent = row.detail;
        text.append(detail);
      }
      line.append(mark, text);
      body.append(line);
    });
    root.append(body);
    document.documentElement.append(root);
    return root;
  }

  async function run() {
    add("runtime.id", !!chrome.runtime.id, chrome.runtime.id);
    add(
      "runtime.getManifest()",
      chrome.runtime.getManifest().name === "ChromeXt API Test",
      chrome.runtime.getManifest().name
    );
    const url = chrome.runtime.getURL("background.json");
    add("runtime.getURL()", url.startsWith("http://127.0.0.1:"), url);

    add("i18n.getMessage", chrome.i18n.getMessage("hello") === "你好，来自 i18n 的消息",
        chrome.i18n.getMessage("hello"));
    add("i18n 占位符", chrome.i18n.getMessage("greet", ["扩展"]) === "你好 扩展",
        chrome.i18n.getMessage("greet", ["扩展"]));

    try {
      await chrome.storage.local.set({ fromContent: "hello", n: 7 });
      const got = await chrome.storage.local.get(["fromContent", "n"]);
      add("storage 读写", got.fromContent === "hello" && got.n === 7, JSON.stringify(got));
    } catch (error) {
      add("storage 读写", false, error.message);
    }

    try {
      const res = await chrome.runtime.sendMessage({ type: "ping" });
      add("sendMessage → 后台", res && res.pong === true, JSON.stringify(res));
    } catch (error) {
      add("sendMessage → 后台", false, error.message);
    }

    try {
      const res = await chrome.runtime.sendMessage({ type: "probe" });
      const ok = res && !res.error && res.id && res.storage && res.resource;
      add("后台探针", !!ok, res && res.error ? res.error : JSON.stringify(res).slice(0, 220));
    } catch (error) {
      add("后台探针", false, error.message);
    }

    await new Promise((resolve) => {
      const timer = setTimeout(() => {
        add("port 双向通信", false, "超时");
        resolve();
        // the first message of a session pays for opening the host tab
      }, 15000);
      try {
        const port = chrome.runtime.connect({ name: "self-test" });
        port.onMessage.addListener((message) => {
          clearTimeout(timer);
          add("port 双向通信", message && message.echo && message.echo.n === 1,
              JSON.stringify(message));
          resolve();
        });
        port.postMessage({ n: 1, text: "port 测试" });
      } catch (error) {
        clearTimeout(timer);
        add("port 双向通信", false, error.message);
        resolve();
      }
    });

    try {
      const tabs = await chrome.tabs.query({});
      add("tabs.query", Array.isArray(tabs) && tabs.length > 0,
          tabs.map((t) => (t.url || "").slice(0, 40)).join(" | "));
    } catch (error) {
      add("tabs.query", false, error.message);
    }

    const root = panel();

    chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
      console.log("CXCS onMessage: " + JSON.stringify(message));
      if (message && message.type === "fromBackground") {
        add("后台 → 内容脚本", true, message.text);
        root.remove();
        panel();
        sendResponse({ received: true });
      }
    });
    chrome.runtime.sendMessage({ type: "tabsMessage" }, (response) => {
      console.log("CXTEST tabsMessage response: " + JSON.stringify(response) +
        " error: " + (chrome.runtime.lastError && chrome.runtime.lastError.message));
    });
  }

  console.log("CXTEST content script loaded on " + location.href);
  const start = () =>
      run().catch((error) => {
        add("自检异常", false, String(error));
        panel();
          });
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start, { once: true });
  } else {
    start();
  }
})();
