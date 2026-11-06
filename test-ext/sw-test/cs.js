(async () => {
  try {
    const res = await chrome.runtime.sendMessage({ type: "hello" });
    console.log("CXSW content hello " + JSON.stringify(res));
  } catch (error) {
    console.log("CXSW content hello failed " + error.message);
  }
})();
