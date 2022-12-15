package org.matrix.chromext.hook

import android.content.Context
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import org.json.JSONObject
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.promptInstallUserScript

object UserScriptHook : BaseHook() {
  override fun init(ctx: Context) {
    val userScriptProxy = UserScriptProxy(ctx)

    findMethod(userScriptProxy.tabWebContentsDelegateAndroidImpl!!) { name == "onUpdateUrl" }
        // public void onUpdateUrl(GURL url)
        .hookAfter {
          userScriptProxy.updateTabDelegator(it.thisObject)
          val url = userScriptProxy.parseUrl(it.args[0])!!
          if (url.endsWith("/ChromeXt/")) {
            userScriptProxy.evaluateJavaScript("ChromeXt=console.debug;")
          } else if (url.endsWith(".user.js")) {
            userScriptProxy.evaluateJavaScript(promptInstallUserScript)
          } else {
            userScriptProxy.didUpdateUrl(url)
          }
        }

    findMethod(userScriptProxy.tabWebContentsDelegateAndroidImpl!!) {
          name == "addMessageToConsole"
        }
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
                  val callback = userScriptProxy.scriptManager(action, payload)
                  if (callback != null) {
                    userScriptProxy.evaluateJavaScript(callback)
                  }
                }
                .onFailure { Log.w(it.toString()) }
          } else {
            Log.d("[${it.args[0]}] ${it.args[1]} @${it.args[3]}:${it.args[2]}")
          }
        }

    // findMethod(userScriptProxy.navigationControllerImpl!!) { name == chromeXt.NAVI_LOAD_URL }
    // public void loadUrl(LoadUrlParams params)
    // .hookBefore {
    // We might use it to reserve a page
    // val url = userScriptProxy.parseUrl(it.args[0])!!
    // userScriptProxy.updateNavController(it.thisObject)
    // if (url.startsWith("chrome://xt/")) {
    //   userScriptProxy.changeUrl(it.args[0], "javascript: ${homepageChromeXt}")
    // }
    // }

    // findMethod(userScriptProxy.webContentsObserverProxy!!) { name == "didStartLoading" }
    //     // public void didStartLoading(GURL url)
    //     .hookBefore {
    //       val url = userScriptProxy.parseUrl(it.args[0])!!
    //       userScriptProxy.didStartLoading(url)
    //     }

    // findMethod(userScriptProxy.webContentsObserverProxy!!) { name == "didStopLoading" }
    //     // public void didStopLoading(GURL url, boolean isKnownValid)
    //     .hookAfter {
    //       val url = userScriptProxy.parseUrl(it.args[0])!!
    //       userScriptProxy.didStopLoading(url)
    //     }

    // findMethod(userScriptProxy.interceptNavigationDelegateImpl!!) {
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
