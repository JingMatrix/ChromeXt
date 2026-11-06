(async () => {
  const out = [];
  try {
    out.push("id=" + chrome.runtime.id);
    out.push("manifest=" + chrome.runtime.getManifest().name);
    await chrome.storage.local.set({ popupRan: true });
    out.push("storage=" + JSON.stringify(await chrome.storage.local.get(["popupRan"])));
    const tabs = await chrome.tabs.query({});
    out.push("tabs=" + tabs.length);
  } catch (error) {
    out.push("ERROR " + error.message);
  }
  const text = out.join(" | ");
  document.getElementById("out").textContent = text;
  console.log("CXPOPUP " + text);
})();
