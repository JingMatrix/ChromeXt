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

    window.addEventListener("inspect_pages", (e) => {
      detail.payload.tabId = e.detail.find(
        (info) => info.url == window.location.href
      ).id;
      detail.payload.uuid = Math.random();
      ChromeXt(JSON.stringify(detail));
    });

    window.addEventListener("websocket", (e) => {
      const type = Object.keys(e.detail)[0];
      this["on" + type]({ data: e.detail[type] });
      console.log(e.detail[type]);
    });
  }

  send() {
    this.detail.payload.message = arguments[0];
    ChromeXt(JSON.stringify(this.detail));
  }
}
