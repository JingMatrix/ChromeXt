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
    this.targetTabId = Number(this.url.split("/").pop());
    this.detail = {
      action: "websocket",
      payload: { tabId: ChromeXt.tabId, targetTabId: this.targetTabId },
    };
    ChromeXt(JSON.stringify(this.detail));

    window.addEventListener("websocket", (e) => {
      const type = Object.keys(e.detail)[0];
      this["on" + type]({ data: e.detail[type] });
    });
  }

  send() {
    this.detail.payload.message = arguments[0];
    ChromeXt(JSON.stringify(this.detail));
  }

  close() {
    delete this.detail.payload.message;
    this.detail.payload.close = true;
    ChromeXt(JSON.stringify(this.detail));
  }
}
