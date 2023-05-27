let old_script = "";

function installScript(install = false) {
  let script = document.querySelector("body > pre").innerText;
  if (script != old_script) {
    old_script = script;
    if (!install) {
      install = confirm("Allow ChromeXt to install / update this userscript?");
    }
    if (install) {
      ChromeXt(JSON.stringify({ action: "installScript", payload: script }));
    }
  }
}

function editor() {
  const js = document.createElement("script");
  const meta = document.createElement("meta");
  const style = document.createElement("style");
  js.setAttribute("type", "module");
  js.textContent =
    "import {highlightElement} from 'https://unpkg.com/@speed-highlight/core/dist/index.js';highlightElement(document.querySelector('body > pre'), 'js', {hideLineNumbers: true});";
  style.setAttribute("type", "text/css");
  meta.setAttribute("name", "viewport");
  meta.setAttribute(
    "content",
    "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"
  );
  style.textContent = `
	@import url('https://unpkg.com/@speed-highlight/core/dist/themes/default.css');
	body {
		margin: 0;
	}
	[class*="shj-lang-"] {
		border-radius: 0;
		color: #abb2bf;
		background: #161b22;
		font: 1.2em monospace;
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
	.shj-syn-func {color: #61afef}
	`;
  document.head.appendChild(meta);
  document.head.appendChild(style);
  document.body.appendChild(js);
  document.querySelector("body > pre").setAttribute("contenteditable", true);
  setTimeout(installScript, 500);
}

if (document.readyState == "complete") {
  editor();
} else {
  window.onload = editor;
}
