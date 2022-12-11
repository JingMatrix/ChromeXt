package org.matrix.chromext.hook

import com.github.kyuubiran.ezxhelper.utils.*

object ChromeHook : BaseHook() {
    override fun init() {
        findMethod("org.chromium.chrome.browser.base.SplitChromeApplication") {
            name == "attachBaseContext"
        }.hookBefore {
            Log.i("Hooked before SplitChromeApplication.attachBaseContext()")
        }
    }
}
