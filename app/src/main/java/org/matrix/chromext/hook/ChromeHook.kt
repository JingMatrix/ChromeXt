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
              // public void onUpdateUrl(GURL url)
              .hookAfter {
                val url = chromeXt.parseUrl(it.args[0])!!
                Log.i("onUpdateUrl: ${url}")
                chromeXt.updateTabDelegator(it.thisObject)
                chromeXt.didUpdateUrl(url)
              }

          findMethod(chromeXt.tabWebContentsDelegateAndroidImpl!!) { name == "addMessageToConsole" }
              // public boolean addMessageToConsole(int level, String message, int lineNumber,
              // String sourceId)
              .hookAfter {
                // This should be the way to communicate with the front-end of ChromeXt
                Log.d("[${it.args[0]}] ${it.args[1]} @${it.args[3]}:${it.args[2]}")
              }

          findMethod(chromeXt.navigationControllerImpl!!) { name == chromeXt.NAVI_LOAD_URL }
              // public void loadUrl(LoadUrlParams params)
              .hookBefore {
                // Currently we only use it to reserve a page
                val url = chromeXt.parseUrl(it.args[0])!!
                chromeXt.updateNavController(it.thisObject)
                if (url.startsWith("chrome://xt/")) {
                  chromeXt.changeUrl(it.args[0], "javascript: 'Page reserved for ChromeXt'")
                }
              }

          findAllMethods(chromeXt.webContentsObserverProxy!!) { name == "didStartLoading" }
              // public void didStartLoading(GURL url)
              .hookBefore {
                val url = chromeXt.parseUrl(it.args[0])!!
                chromeXt.didStartLoading(url)
              }

          findAllMethods(chromeXt.webContentsObserverProxy!!) { name == "didStopLoading" }
              // public void didStopLoading(GURL url, boolean isKnownValid)
              .hookAfter {
                val url = chromeXt.parseUrl(it.args[0])!!
                chromeXt.didStopLoading(url)
              }

          findMethod(chromeXt.interceptNavigationDelegateImpl!!) {
                name == "shouldIgnoreNavigation"
              }
              // public boolean shouldIgnoreNavigation(NavigationHandle navigationHandle, GURL
              // escapedUrl)
              .hookAfter {
                // Not using it yet, could help AdAway in the future
                // Source code not stable yet, we should postpone relevant implements
              }
        }
  }
}
