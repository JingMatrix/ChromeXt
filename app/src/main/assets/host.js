"use strict";

// Runs inside ChromeXt's own process (see HostService.kt). It embeds one iframe per extension,
// each served from its own loopback port, and relays messages between them and the browser over the
// loopback server: commands come from /_api/poll, reports go to /_api/hostmsg.

const status = document.getElementById("status");
const frames = {};
const readyFrames = new Set();
// MV3 semantics: a worker is stopped after half a minute without events and restarted on demand
const IDLE_MS = 30000;
const workers = new Set(EXTENSIONS.filter((e) => e.worker).map((e) => e.id));
const suspended = new Set();
const lastActivity = {};
const pendingByExtension = {};
const buffered = [];
let ready = false;

function line(text) {
  const node = document.createElement("p");
  node.textContent = text;
  status.append(node);
}

function report(payload) {
  fetch("/_api/hostmsg?token=" + encodeURIComponent(TOKEN) + "&p=" + encodeURIComponent(HOST_TOKEN), {
    method: "POST",
    body: JSON.stringify(payload),
  }).catch((error) => console.error("host: cannot report to ChromeXt", error));
}

function touch(extension) {
  lastActivity[extension] = Date.now();
}

function frameUrl(extension) {
  return "http://127.0.0.1:" + extension.port + "/?v=" + encodeURIComponent(HOST_TOKEN);
}

function suspend(extension) {
  const frame = frames[extension];
  if (!frame) return;
  console.log("host: suspend " + extension);
  suspended.add(extension);
  readyFrames.delete(extension);
  // Removing the frame is what really ends the worker: pointing it elsewhere leaves the old
  // document running in some Chromium versions
  frame.remove();
  delete frames[extension];
}

function wake(extension) {
  if (!suspended.has(extension)) return;
  const entry = EXTENSIONS.find((e) => e.id === extension);
  if (!entry) return;
  console.log("host: wake " + extension);
  suspended.delete(extension);
  touch(extension);
  createFrame(entry);
}

function sweepIdleWorkers() {
  const now = Date.now();
  workers.forEach((extension) => {
    if (suspended.has(extension) || !readyFrames.has(extension)) return;
    if (now - (lastActivity[extension] || 0) > IDLE_MS) suspend(extension);
  });
}

setInterval(sweepIdleWorkers, 5000);

function runCommand(code) {
  try {
    (0, eval)(code);
  } catch (error) {
    console.error("host eval failed", error, code);
  }
}

function createFrame(extension) {
  const frame = document.createElement("iframe");
  frame.src = frameUrl(extension);
  frame.dataset.ext = extension.id;
  frame.style.cssText = "width:1px;height:1px;border:0;position:absolute;left:-20px;top:-20px;";
  document.body.append(frame);
  frames[extension.id] = frame;
  touch(extension.id);
  return frame;
}

EXTENSIONS.forEach((extension) => {
  createFrame(extension);
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
  touch(extension);
  if (!readyFrames.has(extension)) {
    (pendingByExtension[extension] || (pendingByExtension[extension] = [])).push(payload);
    // A stopped worker has to be started before it can receive anything
    wake(extension);
    return true;
  }
  return sendToFrame(extension, payload);
}

window.addEventListener("message", (event) => {
  const data = event.data && event.data.__chromext;
  if (!data) return;
  if (data.type === "ready") {
    line("后台页就绪：" + data.ext);
    touch(data.ext);
    readyFrames.add(data.ext);
    const waiting = pendingByExtension[data.ext] || [];
    delete pendingByExtension[data.ext];
    waiting.forEach((payload) => sendToFrame(data.ext, payload));
    return;
  }
  data.host = true;
  touch(data.ext);
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

window.__chromextHostContextMenu = function (extension, info, tab) {
  return forward(extension, { type: "contextMenu", ext: extension, info: info, tab: tab });
};

window.__chromextHostAction = function (extension, tab) {
  return forward(extension, { type: "action", ext: extension, tab: tab });
};

window.__chromextHostWebRequest = function (extension, kind, details, requestId) {
  return forward(extension, {
    type: "webRequest",
    ext: extension,
    kind: kind,
    details: details,
    requestId: requestId,
  });
};

window.__chromextHostPort = function (extension, portId, type, message) {
  return forward(extension, { type: type, ext: extension, portId: portId, message: message });
};

async function poll() {
  try {
    const response = await fetch(
      "/_api/poll?token=" + encodeURIComponent(TOKEN) + "&p=" + encodeURIComponent(HOST_TOKEN)
    );
    if (response.status === 403) {
      // Another browser process owns the host now
      line("这个宿主页已失效，可以关闭");
      return;
    }
    const data = await response.json();
    sweepIdleWorkers();
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
