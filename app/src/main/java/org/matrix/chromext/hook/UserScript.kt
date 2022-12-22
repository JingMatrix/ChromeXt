package org.matrix.chromext.hook

import android.content.Context
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import org.json.JSONObject
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.promptInstallUserScript

object UserScriptHook : BaseHook() {

  var proxy: UserScriptProxy? = null

  override fun init(ctx: Context) {

    proxy = UserScriptProxy(ctx)

    findMethod(proxy!!.tabWebContentsDelegateAndroidImpl!!) { name == "onUpdateUrl" }
        // public void onUpdateUrl(GURL url)
        .hookAfter {
          proxy!!.updateTabDelegator(it.thisObject)
          val url = proxy!!.parseUrl(it.args[0])!!
          if (url.endsWith("/ChromeXt/")) {
            proxy!!.evaluateJavaScript("ChromeXt=console.debug;")
          } else if (url.endsWith(".user.js")) {
            proxy!!.evaluateJavaScript(promptInstallUserScript)
          } else {
            proxy!!.didUpdateUrl(url)
          }
        }

    findMethod(proxy!!.tabWebContentsDelegateAndroidImpl!!) { name == "addMessageToConsole" }
        // public boolean addMessageToConsole(int level, String message, int lineNumber,
        // String sourceId)
        .hookAfter {
          // This should be the way to communicate with the front-end of ChromeXt
          if (it.args[0] as Int == 0) {
            val text = it.args[1] as String
            runCatching {
                  val data = JSONObject(text)
                  val action = data.getString("action")
                  val payload = data.getString("payload")
                  val callback = proxy!!.scriptManager(action, payload)
                  if (callback != null) {
                    proxy!!.evaluateJavaScript(callback)
                  }
                }
                .onFailure { Log.w(it.toString()) }
          } else {
            Log.d("[${it.args[0]}] ${it.args[1]} @${it.args[3]}:${it.args[2]}")
          }
        }

    // findMethod(proxy!!.navigationControllerImpl!!) { name ==
    // proxy!!.NAVI_LOAD_URL }
    //     // public void loadUrl(LoadUrlParams params)
    //     .hookBefore {
    //       val url = proxy!!.parseUrl(it.args[0])!!
    //       // proxy!!.updateNavController(it.thisObject)
    //       if (url.startsWith("content://")) {
    //         proxy!!.fixCharset(it.args[0])
    //       }
    //     }

    // findMethod(proxy!!.webContentsObserverProxy!!) { name == "didStartLoading" }
    //     // public void didStartLoading(GURL url)
    //     .hookBefore {
    //       val url = proxy!!.parseUrl(it.args[0])!!
    //       proxy!!.didStartLoading(url)
    //     }

    // findMethod(proxy!!.webContentsObserverProxy!!) { name == "didStopLoading" }
    //     // public void didStopLoading(GURL url, boolean isKnownValid)
    //     .hookAfter {
    //       val url = proxy!!.parseUrl(it.args[0])!!
    //       proxy!!.didStopLoading(url)
    //     }

    // findMethod(proxy!!.interceptNavigationDelegateImpl!!) {
    //       name == "shouldIgnoreNavigation"
    //     }
    // public boolean shouldIgnoreNavigation(NavigationHandle navigationHandle, GURL
    // escapedUrl)
    // .hookAfter {
    // Not using it yet, could help AdAway in the future
    // Source code not stable yet, we should postpone relevant implements
    // }
  }
}
