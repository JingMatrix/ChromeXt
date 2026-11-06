"use strict";

const list = document.getElementById("list");
const dir = document.getElementById("dir");
const message = document.getElementById("msg");

let toastTimer = null;
function toast(text) {
  message.textContent = text;
  message.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => message.classList.remove("show"), 2600);
}

async function api(action, params = {}, options = {}) {
  const query = new URLSearchParams(Object.assign({ token: TOKEN }, params));
  const response = await fetch("/_api/" + action + "?" + query, options);
  const text = await response.text();
  let data;
  try {
    data = JSON.parse(text);
  } catch (ignored) {
    data = { error: text };
  }
  if (data && data.error) throw new Error(data.error);
  return data;
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function icon(extension) {
  const sizes = Object.keys(extension.icons || {});
  if (sizes.length === 0) return el("div", "icon");
  sizes.sort((a, b) => Number(b) - Number(a));
  const image = el("img", "icon");
  image.src =
    "http://127.0.0.1:" + extension.port + "/" + String(extension.icons[sizes[0]]).replace(/^\/+/, "");
  image.onerror = () => image.replaceWith(el("div", "icon"));
  return image;
}

function render(extensions) {
  list.textContent = "";
  if (!extensions || extensions.length === 0) {
    list.append(el("p", "empty", "还没有安装扩展"));
    return;
  }
  extensions.forEach((extension) => {
    const card = el("div", "card");
    card.append(icon(extension));

    const body = el("div", "body");
    const title = el("div", "title");
    title.append(el("b", null, extension.name));
    title.append(el("span", "version", "v" + extension.version));
    title.append(el("span", "tag", extension.manifestVersion === 3 ? "MV3" : "MV2"));
    if (extension.hasBackground) title.append(el("span", "tag", "后台页"));
    if (extension.contentScripts > 0)
      title.append(el("span", "tag", extension.contentScripts + " 内容脚本"));
    body.append(title);

    if (extension.description) body.append(el("p", "desc", extension.description));
    body.append(el("div", "meta", extension.id));

    const actions = el("div", "actions");
    const open = el("button", "ghost", "打开后台页");
    open.onclick = () => window.open("http://127.0.0.1:" + extension.port + "/", "_blank");
    actions.append(open);

    const remove = el("button", "danger", "卸载");
    remove.onclick = async () => {
      if (!confirm("卸载 " + extension.name + "？")) return;
      try {
        await api("uninstall", { id: extension.id });
        toast("已卸载 " + extension.name);
        refresh();
      } catch (error) {
        toast("卸载失败：" + error.message);
      }
    };
    actions.append(remove);

    const toggle = el("div", "switch");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = extension.enabled;
    checkbox.onchange = async () => {
      try {
        await api("toggle", { id: extension.id, enabled: checkbox.checked ? "1" : "0" });
        toast((checkbox.checked ? "已启用 " : "已禁用 ") + extension.name);
        refresh();
      } catch (error) {
        toast("切换失败：" + error.message);
      }
    };
    toggle.append(checkbox);
    actions.append(toggle);

    body.append(actions);
    card.append(body);
    list.append(card);
  });
}

async function refresh() {
  try {
    const data = await api("list");
    render(data.extensions);
    let host = "";
    try {
      const state = await api("state");
      const names = { running: "运行中", starting: "启动中", stopped: "未启动" };
      host = " ｜ 宿主进程：" + (names[state.background] || state.background);
    } catch (error) {
      // the status is a nicety, never block the list on it
    }
    dir.textContent = "扩展目录：" + data.directory + host;
  } catch (error) {
    toast("读取失败：" + error.message);
  }
}

document.getElementById("reload").onclick = async () => {
  try {
    const data = await api("reload");
    render(data.extensions);
    toast("已重新扫描");
  } catch (error) {
    toast("刷新失败：" + error.message);
  }
};

document.getElementById("file").onchange = async (event) => {
  const files = Array.from(event.target.files || []);
  event.target.value = "";
  for (const file of files) {
    toast("正在安装 " + file.name + " …");
    try {
      const data = await api("install", { name: file.name }, { method: "POST", body: file });
      toast("已安装 " + (data.id || file.name));
    } catch (error) {
      toast("安装失败：" + error.message);
    }
  }
  refresh();
};

document.getElementById("fetch").onclick = async () => {
  const input = document.getElementById("store");
  const text = input.value.trim();
  if (!text) return;
  const match = text.match(/[a-p]{32}/);
  if (!match) {
    toast("没找到扩展 ID（32 位字母 a-p）");
    return;
  }
  toast("正在从应用店下载 " + match[0] + " …");
  try {
    const data = await api("store", { id: match[0] });
    toast("已安装 " + (data.id || match[0]));
    input.value = "";
  } catch (error) {
    toast("下载失败：" + error.message);
  }
  refresh();
};

refresh();
