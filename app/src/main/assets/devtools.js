window.addEventListener("load", () => {
  const meta = document.createElement("meta");
  meta.setAttribute("name", "viewport");
  meta.setAttribute("content", "width=device-width, initial-scale=1");
  document.head.prepend(meta);
  const style = document.createElement("style");
  style.innerText = ".filter-bitset-filter { overflow-x: scroll !important; }";
  document.body.prepend(style);
});

const _websocket = window.WebSocket;
class WebSocket {
  constructor() {
    globalThis.WebSocket = _websocket;
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
      detail.payload.uuid = Math.random();
      ChromeXt(JSON.stringify(detail));
    });

    window.addEventListener("websocket", (e) => {
      const type = Object.keys(e.detail)[0];
      const data = e.detail[type];
      if (type == "message" && "id" in data && !("sessionId" in data)) {
        this.fixSession(data);
      }
      this["on" + type](new MessageEvent(type, { data }));
    });
  }

  fixSession(res) {
    for (let [key, value] of this.sessions.entries()) {
      const request = value.find((d) => d.id == res.id);
      if (request != undefined && key != "") {
        console.warn("SessionId workaround applied to message: ", request, res);
        res.sessionId = key;
        return key;
      }
    }
  }

  send() {
    this.detail.payload.message = arguments[0];
    const data = JSON.parse(arguments[0]);
    const sessionId = data.sessionId || "";
    if (this.sessions.has(sessionId)) {
      this.sessions.get(sessionId).push(data);
    } else {
      this.sessions.set(sessionId, [data]);
    }
    ChromeXt(JSON.stringify(this.detail));
  }
}
