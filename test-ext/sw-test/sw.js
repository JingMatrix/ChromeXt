// Deliberately quiet: no timers, no polling. ChromeXt should stop this worker when idle and start
// it again when a message arrives.
console.log("CXSW boot " + Date.now());

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log("CXSW message " + JSON.stringify(message));
  if (message && message.type === "ping") {
    sendResponse({ pong: true, at: Date.now() });
    return;
  }
  if (message && message.type === "hello") {
    sendResponse({ hello: true, url: sender && sender.url });
    return;
  }
});
