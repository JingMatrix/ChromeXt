package org.matrix.chromext.hook

import com.github.kyuubiran.ezxhelper.utils.*
import com.github.kyuubiran.ezxhelper.utils.Log.logexIfThrow
import android.content.Context

object ChromeHook : BaseHook() {
	override fun init() {
		findMethod("org.chromium.url.GURL") {
			name == "init"
		}.hookBefore {
			// Log.d(it.args[0] as String)
		}

		findMethod("org.chromium.chrome.browser.base.SplitChromeApplication") {
			name == "attachBaseContext"
		}.hookAfter{
			val ctx: Context = (it.args[0] as Context).createContextForSplit("chrome") 
				runCatching {
					findAllMethods(
							ctx.getClassLoader().loadClass(
								"org.chromium.chrome.browser.tab.TabImpl")
							) {
						paramCount == 1 && isNotAbstract && isPublic && isFinal && isNotNative
					}.hookBefore {
						if (it.args[0] != null && it.args[0].javaClass.name == "org.chromium.url.GURL" ) {
							Log.d("TabImple call GURL ${it.args[0]::class.toString()}")
						}
					}
				}.logexIfThrow("Failed to load TabImple")

		}
	}   
}
