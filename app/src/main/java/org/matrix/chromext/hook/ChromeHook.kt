package org.matrix.chromext.hook

import android.content.Context
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findAllMethods
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import org.matrix.chromext.ChromeXt

object ChromeHook : BaseHook() {
  override fun init() {
    findMethod("org.chromium.chrome.browser.base.SplitChromeApplication") {
          name == "attachBaseContext"
        }
        .hookAfter {
          val ctx: Context = (it.args[0] as Context).createContextForSplit("chrome")
          val chromeXt = ChromeXt(ctx)

          findMethod(chromeXt.tabWebContentsDelegateAndroidImpl!!) { name == "onUpdateUrl" }
              .hookAfter {
                val url = chromeXt.parseUrl(it.args[0])!!
                Log.i("onUpdateUrl: ${url}")
                chromeXt.updateTabDelegator(it.thisObject)
                chromeXt.didUpdateUrl(url)
              }

          findMethod(chromeXt.tabWebContentsDelegateAndroidImpl!!) { name == "addMessageToConsole" }
              .hookAfter {
                // addMessageToConsole(int level, String message, int lineNumber,
                // String sourceId)

                // This should be the way to communicate with the front-end of ChromeXt
                Log.d("[${it.args[0]}] ${it.args[1]} @${it.args[3]}:${it.args[2]}")
              }

          findMethod(chromeXt.navigationControllerImpl!!) { name == chromeXt.NAVI_LOAD_URL }
              .hookBefore {
                // Currently we only use it to reserve a page
                val url = chromeXt.parseUrl(it.args[0])!!
                if (url.startsWith("chrome://xt/")) {
                  chromeXt.changeUrl(it.args[0], "javascript: 'Page reserved for ChromeXt'")
                }
              }

          findAllMethods(chromeXt.webContentsObserverProxy!!) { name == "didStartLoading" }
              .hookBefore {
                val url = chromeXt.parseUrl(it.args[0])!!
                chromeXt.didStartLoading(url)
              }

          findAllMethods(chromeXt.webContentsObserverProxy!!) { name == "didStopLoading" }
              .hookAfter {
                val url = chromeXt.parseUrl(it.args[0])!!
                chromeXt.didStopLoading(url)
              }

          // findMethod(chromeXt.interceptNavigationDelegateImpl!!) {
          //       name == "shouldIgnoreNavigation"
          //     }
          //     .hookAfter {
          //       // Not using it yet, could help AdAway in the future
          //     }
        }
  }
}
