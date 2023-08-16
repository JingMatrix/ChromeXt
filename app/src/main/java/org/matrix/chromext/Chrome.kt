package org.matrix.chromext

import android.app.Application
import android.content.Context
import android.os.Handler
import java.lang.ref.WeakReference
import kotlin.concurrent.thread
import org.json.JSONObject
import org.matrix.chromext.devtools.DevToolClient
import org.matrix.chromext.devtools.getInspectPages
import org.matrix.chromext.devtools.hitDevTools
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod

object Chrome {
  private var mContext: WeakReference<Context>? = null
  private var mTab: WeakReference<Any>? = null
  private var devToolsReady = false

  var isBrave = false
  var isDev = false
  var isEdge = false
  var isSamsung = false
  var isVivaldi = false

  fun init(ctx: Context, packageName: String? = null) {
    val initialized = mContext != null
    mContext = WeakReference(ctx)

    if (initialized || packageName == null) return

    if (ctx is Application) {
      Log.d("Started a WebView based browser")
    }
    isBrave = packageName.startsWith("com.brave.browser")
    isDev = packageName.endsWith("canary") || packageName.endsWith("dev")
    isEdge = packageName.startsWith("com.microsoft.emmx")
    isSamsung = packageName.startsWith("com.sec.android.app.sbrowser")
    isVivaldi = packageName == "com.vivaldi.browser"
    @Suppress("DEPRECATION") val packageInfo = ctx.packageManager?.getPackageInfo(packageName, 0)
    Log.i("Package: ${packageName}, v${packageInfo?.versionName}")
  }

  fun wakeUpDevTools(limit: Int = 10) {
    var waited = 0
    while (!devToolsReady && waited < limit) {
      runCatching {
            hitDevTools().close()
            devToolsReady = true
            Log.i("DevTools woke up")
          }
          .onFailure { Log.d("Waking up DevTools") }
      if (!devToolsReady) Thread.sleep(500)
      waited += 1
    }
  }

  fun getContext(): Context {
    val activity = getTab()?.invokeMethod { name == "getContext" } as Context?
    return activity ?: mContext!!.get()!!
  }

  fun load(className: String): Class<*> {
    return getContext().classLoader.loadClass(className)
  }

  fun getTab(referTab: Any? = null): Any? {
    return referTab ?: mTab?.get()
  }

  fun getUrl(currentTab: Any? = null): String? {
    val url = getTab(currentTab)?.invokeMethod { name == "getUrl" }
    return if (UserScriptHook.isInit) {
      UserScriptProxy.parseUrl(url)
    } else {
      url as String?
    }
  }

  fun updateTab(tab: Any?) {
    if (tab != null) mTab = WeakReference(tab)
  }

  private fun evaluateJavascript(codes: List<String>, tabId: String) {
    wakeUpDevTools()
    var client = DevToolClient(tabId)
    if (client.isClosed()) {
      hitDevTools().close()
      client = DevToolClient(tabId)
    }
    // client.command(null, "Page.setBypassCSP", JSONObject().put("enabled", true))
    codes.forEach { client.evaluateJavascript(it) }
    client.close()
  }

  fun evaluateJavascript(
      codes: List<String>,
      currentTab: Any? = null,
      forceDevTools: Boolean = false
  ) {
    if (codes.size == 0) return
    if (forceDevTools) {
      thread {
        val tabId =
            if (WebViewHook.isInit) {
              val url = getUrl(currentTab)
              filterTabs {
                    optString("type") == "page" &&
                        optString("url") == url &&
                        !JSONObject(getString("description")).optBoolean("never_attached")
                  }
                  .first()
            } else {
              getTab(currentTab)!!.invokeMethod() { name == "getId" }.toString()
            }
        evaluateJavascript(codes, tabId)
      }
    } else {
      Handler(getContext().mainLooper).post {
        if (WebViewHook.isInit) {
          codes.forEach { WebViewHook.evaluateJavascript(it) }
        } else if (UserScriptHook.isInit) {
          val failed = codes.filter { !UserScriptProxy.evaluateJavascript(it, currentTab) }
          if (failed.size > 0) evaluateJavascript(failed, currentTab, true)
        }
      }
    }
  }

  fun broadcast(
      event: String,
      data: JSONObject,
      excludeSelf: Boolean = true,
      matching: (String) -> Boolean
  ) {
    val code = "ChromeXt.unlock(${Local.key}).post('${event}', ${data});"
    Log.d("broadcasting ${event}")

    val tabs = filterTabs {
      optString("type") == "page" &&
          matching(optString("url")) &&
          (optString("description") == "" ||
              !JSONObject(getString("description")).optBoolean("never_attached"))
    }

    if (tabs.size > 1 || !excludeSelf) {
      tabs.forEach { evaluateJavascript(listOf(code), it) }
    }
  }

  private fun filterTabs(condition: JSONObject.() -> Boolean): List<String> {
    wakeUpDevTools()
    val pages = getInspectPages()!!
    val tabs = mutableListOf<String>()
    for (i in 0 until pages.length()) {
      val tab = pages.getJSONObject(i)
      if (condition.invoke(tab)) tabs.add(tab.getString("id"))
    }
    return tabs
  }
}

object Resource {
  private var module_path: String? = null

  fun init(packagePath: String) {
    module_path = packagePath
  }

  fun enrich(ctx: Context) {
    // Log.d("Enriching context for " + ctx.toString())
    ctx.assets.invokeMethod(module_path!!) { name == "addAssetPath" }
  }
}
