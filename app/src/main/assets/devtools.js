window.addEventListener("load", () => {
  const meta = document.createElement("meta");
  meta.setAttribute("name", "viewport");
  meta.setAttribute("content", "width=device-width, initial-scale=1");
  document.head.prepend(meta);
  const style = document.createElement("style");
  style.innerText = ".filter-bitset-filter { overflow-x: scroll !important; }";
  document.body.prepend(style);
});

class WebSocket {
  constructor() {
    this.url = arguments[0];
    this.targetTabId = this.url.split("/").pop();
    this.detail = {
      action: "websocket",
      payload: { targetTabId: this.targetTabId },
    };
    const detail = this.detail;
    ChromeXt(JSON.stringify(detail));
    this.sessions = new Map();

    window.addEventListener("inspect_pages", (e) => {
      detail.payload.tabId = e.detail.find(
        (info) => info.url == window.location.href
      ).id;
      ChromeXt(JSON.stringify(detail));
    });

    window.addEventListener("websocket", (e) => {
      const type = Object.keys(e.detail)[0];
      const data = e.detail[type];
      if (
        type == "message" &&
        !("id" in data) &&
        "method" in data &&
        "params" in data
      ) {
        const targetInfo = data.params.targetInfo;
        if (typeof targetInfo != "undefined" && targetInfo.type != "page") {
          console.info("Ignore inspecting", targetInfo.type, targetInfo.url);
          // To inspect them, we may need to change the targetTabId
          return;
        }
      }
      this["on" + type](new MessageEvent(type, { data }));
    });
  }

  send() {
    this.detail.payload.message = arguments[0];
    ChromeXt(JSON.stringify(this.detail));
  }
}
