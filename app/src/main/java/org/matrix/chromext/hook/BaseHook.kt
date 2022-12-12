package org.matrix.chromext.hook

abstract class BaseHook {
	var isInit: Boolean = false
	abstract fun init()
	companion object {
		// Use frida-trace to find common method
		const val SHOW_URL = "j"
		// grep smali code with Tab.loadUrl to get the loadUrl function
		const val LOAD_URL = "h"
		// get TabImpl field Lorg/chromium/chrome/browser/tab/TabWebContentsDelegateAndroidImpl
		const val TAB_FIELD = "a"
	}
}
