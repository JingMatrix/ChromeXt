"use strict";

// Runs inside ChromeXt's own process (see HostService.kt). It embeds one iframe per extension,
// each served from its own loopback port, and relays messages between them and the browser over the
// loopback server: commands come from /_api/poll, reports go to /_api/hostmsg.

const status = document.getElementById("status");
const frames = {};
const readyFrames = new Set();
const pendingByExtension = {};
const buffered = [];
let ready = false;

function line(text) {
  const node = document.createElement("p");
  node.textContent = text;
  status.append(node);
}

function report(payload) {
  fetch("/_api/hostmsg?token=" + encodeURIComponent(TOKEN), {
    method: "POST",
    body: JSON.stringify(payload),
  }).catch((error) => console.error("host: cannot report to ChromeXt", error));
}

function runCommand(code) {
  try {
    (0, eval)(code);
  } catch (error) {
    console.error("host eval failed", error, code);
  }
}

EXTENSIONS.forEach((extension) => {
  const frame = document.createElement("iframe");
  frame.src = "http://127.0.0.1:" + extension.port + "/";
  frame.dataset.ext = extension.id;
  frame.style.cssText = "width:1px;height:1px;border:0;position:absolute;left:-20px;top:-20px;";
  document.body.append(frame);
  frames[extension.id] = frame;
  line(extension.id + " → http://127.0.0.1:" + extension.port + "/");
});

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
    (pendingByExtension[extension] || (pendingByExtension[extension] = [])).push(payload);
    return true;
  }
  return sendToFrame(extension, payload);
}

window.addEventListener("message", (event) => {
  const data = event.data && event.data.__chromext;
  if (!data) return;
  if (data.type === "ready") {
    line("后台页就绪：" + data.ext);
    readyFrames.add(data.ext);
    const waiting = pendingByExtension[data.ext] || [];
    delete pendingByExtension[data.ext];
    waiting.forEach((payload) => sendToFrame(data.ext, payload));
    return;
  }
  data.host = true;
  report(data);
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

async function poll() {
  try {
    const response = await fetch("/_api/poll?token=" + encodeURIComponent(TOKEN));
    const data = await response.json();
    (data.commands || []).forEach((code) => {
      if (ready) runCommand(code);
      else buffered.push(code);
    });
  } catch (error) {
    await new Promise((resolve) => setTimeout(resolve, 1500));
  }
  poll();
}

line("宿主已启动，正在等待后台页…");
ready = true;
report({ host: true, action: "host.ready", ext: "", args: [], uuid: "" });
buffered.splice(0).forEach(runCommand);
poll();
