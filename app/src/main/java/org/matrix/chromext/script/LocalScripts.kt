package org.matrix.chromext.script

const val promptInstallUserScript: String =
    """
let install = confirm("Confirm ChromeXt to intall this userscript?");
if (install) {
	let script = document.querySelector("body > pre").innerHTML;
	console.debug(JSON.stringify({"action": "installScript", "payload": script}));
}
"""

val homepageChromeXt: String = "'Page reserved for ChromeXt'"
