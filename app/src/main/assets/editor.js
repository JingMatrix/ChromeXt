async function installScript(force = false) {
  if (!force) {
    force = confirm("Confirm ChromeXt to install this UserScript?");
  }
  if (force) {
    let script;
    if (
      document.characterSet != "UTF-8" &&
      window.location.href.startsWith("http")
    ) {
      const response = await fetch(window.location.href);
      script = await response.text();
    } else {
      script = document.body.innerText;
    }
    ChromeXt.dispatch("installScript", script);
  }
}

function editor() {
  const separator = "==/UserScript==\n";
  const code = document.querySelector("body > pre");
  const script = code.innerHTML.split(separator);

  const scriptMeta = document.createElement("pre");
  const meta = document.createElement("meta");
  const style = document.createElement("style");
  const js = document.createElement("script");
  scriptMeta.innerHTML = script.shift() + separator;
  scriptMeta.id = "meta";
  code.innerHTML = script.join(separator);
  code.id = "code";
  code.removeAttribute("style");
  style.setAttribute("type", "text/css");
  meta.setAttribute("name", "viewport");
  meta.setAttribute(
    "content",
    "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"
  );
  style.textContent = `@import url('https://unpkg.com/@speed-highlight/core/dist/themes/default.css');
	html {overflow-x: hidden}
	pre {overflow-x: scroll}
	body { margin: 0}
	[class*="shj-lang-"] {
		border-radius: 0;
		color: #abb2bf;
		background: #161b22;
		font: 1em monospace;
		padding: 8px 5px;
		margin: 0;
		width: 100vw;
		word-break: break-word;
	}
	[class*="shj-lang-"]:before {color: #6f9aff}
	.shj-syn-deleted,
	.shj-syn-err,
	.shj-syn-var {color: #e06c75}
	.shj-syn-section,
	.shj-syn-oper,
	.shj-syn-kwd {color: #c678dd}
	.shj-syn-class {color: #e5c07b}
	.shj-numbers,
	.shj-syn-cmnt {color: #76839a}
	.shj-syn-insert {color: #98c379}
	.shj-syn-type {color: #56b6c2}
	.shj-syn-num,
	.shj-syn-bool {color: #d19a66}
	.shj-syn-str,
	.shj-syn-func {color: #61afef}`;
  js.setAttribute("type", "module");
  js.textContent =
    "import { highlightElement } from 'https://unpkg.com/@speed-highlight/core/dist/index.js'; highlightElement(document.querySelector('#code'), 'js', { hideLineNumbers: true });";

  document.body.prepend(scriptMeta);
  document.head.appendChild(meta);
  document.head.appendChild(style);
  document.head.appendChild(js);
  if (document.characterSet == "UTF-8") {
    scriptMeta.setAttribute("contenteditable", true);
    code.setAttribute("contenteditable", true);
  }
  setTimeout(installScript, 500);
}

if (document.readyState == "complete") {
  editor();
} else {
  window.onload = editor;
}
