// ==UserScript==
// @name	Content-Security-Policy Blocker
// @namespace	JingMatrix
// @match	https://*
// @run-at	document-start
// @grant GM_registerMenuCommand
// @downloadURL https://raw.githubusercontent.com/JingMatrix/ChromeXt/master/CSP.user.js
// ==/UserScript==

const cspRule = localStorage.getItem("CSPBlocker");
if (cspRule) {
	const meta = document.createElement("meta");
	meta.setAttribute("http-equiv", "Content-Security-Policy");
	meta.setAttribute("content", cspRule);
	try {
		document.head.append(meta);
	} catch {
		setTimeout(() => {
			document.head.append(meta);
		}, 0);
	}
}

GM_registerMenuCommand("No JavaScript", () => { localStorage.setItem("CSPBlocker", "script-src 'none'") });
GM_registerMenuCommand("No Third-Party", () => { localStorage.setItem("CSPBlocker", "default-src 'unsafe-inline' 'self'") });
GM_registerMenuCommand("Edit CSP rules", () => {
	const newrule = prompt("Editing CSP rules", localStorage.getItem("CSPBlocker") || "");
	if (newrule && newrule != "") {
		localStorage.setItem("CSPBlocker", newrule)
	}
});
GM_registerMenuCommand("Clear CSP rules", () => { localStorage.removeItem("CSPBlocker") });
