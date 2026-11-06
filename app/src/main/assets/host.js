"use strict";

const status = document.getElementById("status");
const frames = {};
const readyFrames = new Set();
const pendingByExtension = {};
const buffered = [];
let ChromeXt = null;
let ready = false;

function runCommand(code) {
  try {
    (0, eval)(code);
  } catch (error) {
    console.error("host eval failed", error, code);
  }
}

function line(text) {
  const node = document.createElement("p");
  node.textContent = text;
  status.append(node);
}

// The ChromeXt init script is injected right after the navigation starts, so the symbol may not
// exist yet when this file runs.
function whenReady(callback) {
  let attempts = 0;
  const timer = setInterval(() => {
    const target = Symbol[CHROMEXT_NAME];
    if (target) {
      clearInterval(timer);
      try {
        callback(target.unlock(CHROMEXT_KEY) || target);
      } catch (error) {
        callback(target);
      }
    } else if (++attempts > 200) {
      clearInterval(timer);
      line("ChromeXt 没就绪，这个页面可能没有注入初始化脚本");
    }
  }, 50);
}

whenReady((api) => {
  ChromeXt = api;
  console.log("host: bridge ready, extensions=" + EXTENSIONS.map((e) => e.id).join(","));
  EXTENSIONS.forEach((extension) => {
    const frame = document.createElement("iframe");
    frame.src = "http://127.0.0.1:" + extension.port + "/";
    frame.dataset.ext = extension.id;
    frame.style.cssText = "width:1px;height:1px;border:0;position:absolute;left:-20px;top:-20px;";
    document.body.append(frame);
    frames[extension.id] = frame;
    line(extension.id + " → http://127.0.0.1:" + extension.port + "/");
  });
  line("宿主页已启动，正在等待后台页…");
  ready = true;
  const backlog = buffered.splice(0);
  if (backlog.length > 0) console.log("host: flushing " + backlog.length + " buffered command(s)");
  backlog.forEach(runCommand);
  ChromeXt.dispatch("ext", { host: true, action: "host.ready", ext: "", args: [], uuid: "" });
});

async function poll() {
  try {
    const response = await fetch("/_api/poll?token=" + encodeURIComponent(TOKEN));
    const data = await response.json();
    const commands = data.commands || [];
    if (commands.length > 0) console.log("host: received " + commands.length + " command(s)");
    commands.forEach((code) => {
      if (ready) runCommand(code);
      else buffered.push(code);
    });
  } catch (error) {
    await new Promise((resolve) => setTimeout(resolve, 1500));
  }
  poll();
}

function sendToFrame(extension, payload) {
  const frame = frames[extension];
  if (!frame || !frame.contentWindow) {
    line("没有找到 " + extension + " 的 iframe");
    return false;
  }
  frame.contentWindow.postMessage({ __chromext: payload }, "*");
  return true;
}

function forward(extension, payload) {
  if (!readyFrames.has(extension)) {
    console.log("host: queue " + payload.type + " until " + extension + " is ready");
    (pendingByExtension[extension] || (pendingByExtension[extension] = [])).push(payload);
    return true;
  }
  console.log("host: forward " + payload.type + " to " + extension);
  return sendToFrame(extension, payload);
}

window.addEventListener("message", (event) => {
  const data = event.data && event.data.__chromext;
  if (!data) return;
  if (data.type === "ready") {
    line("后台页就绪：" + data.ext);
    console.log("host: background ready " + data.ext);
    readyFrames.add(data.ext);
    const waiting = pendingByExtension[data.ext] || [];
    delete pendingByExtension[data.ext];
    if (waiting.length > 0)
      console.log("host: replaying " + waiting.length + " message(s) to " + data.ext);
    waiting.forEach((payload) => sendToFrame(data.ext, payload));
    return;
  }
  if (!ChromeXt) return;
  data.host = true;
  try {
    ChromeXt.dispatch("ext", data);
  } catch (error) {
    console.error(error);
  }
});

window.__chromextHostDeliver = function (extension, message, sender, uuid) {
  return forward(extension, {
    type: "deliver",
    ext: extension,
    message: message,
    sender: sender,
    uuid: uuid,
  });
};

window.__chromextHostResolve = function (extension, uuid, ok, value) {
  return forward(extension, { type: "resolve", ext: extension, uuid: uuid, ok: ok, value: value });
};

window.__chromextHostPortOpen = function (extension, portId, name, sender) {
  return forward(extension, {
    type: "portOpen",
    ext: extension,
    portId: portId,
    name: name,
    sender: sender,
  });
};

window.__chromextHostPort = function (extension, portId, type, message) {
  return forward(extension, { type: type, ext: extension, portId: portId, message: message });
};

poll();
