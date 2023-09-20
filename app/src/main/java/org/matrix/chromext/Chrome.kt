package org.matrix.chromext

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.http.HttpResponseCache
import android.os.Build
import android.os.Handler
import java.io.File
import java.lang.ref.WeakReference
import java.net.CookieManager
import java.util.concurrent.Executors
import org.json.JSONObject
import org.matrix.chromext.devtools.DevSessions
import org.matrix.chromext.devtools.getInspectPages
import org.matrix.chromext.devtools.hitDevTools
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findField
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

  val IO = Executors.newCachedThreadPool()
  val channel =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel(
                "ChromeXt", "UserScript Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Notifications created by GM_notification API" }
      } else null
  val cookieStore = CookieManager().getCookieStore()

  fun init(ctx: Context, packageName: String? = null) {
    val initialized = mContext != null
    mContext = WeakReference(ctx)

    if (initialized || packageName == null) return

    isBrave = packageName.startsWith("com.brave.browser")
    isDev = packageName.endsWith("canary") || packageName.endsWith("dev")
    isEdge = packageName.startsWith("com.microsoft.emmx")
    isSamsung = packageName.startsWith("com.sec.android.app.sbrowser")
    isVivaldi = packageName == "com.vivaldi.browser"
    @Suppress("DEPRECATION") val packageInfo = ctx.packageManager?.getPackageInfo(packageName, 0)
    Log.i("Package: ${packageName}, v${packageInfo?.versionName}")
    setupHttpCache(ctx)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager =
          ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel!!)
    }
  }

  private fun setupHttpCache(context: Context) {
    val httpCacheDir = File(context.getCacheDir(), "ChromeXt")
    val httpCacheSize = 16 * 1024 * 1024L
    HttpResponseCache.install(httpCacheDir, httpCacheSize)
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
    if (Chrome.isSamsung) return mContext!!.get()!!
    val activity = getTab()?.invokeMethod { name == "getContext" } as Context?
    if (activity != null && mContext == null) init(activity, activity.packageName)
    return activity ?: mContext!!.get()!!
  }

  fun checkTab(tab: Any?): Boolean {
    if (tab == null) return false
    if (UserScriptHook.isInit) {
      return UserScriptProxy.mNativeAndroid.get(tab) != 0L
    } else {
      return tab == getTab()
    }
  }

  fun load(className: String): Class<*> {
    return getContext().classLoader.loadClass(className)
  }

  fun getTab(referTab: Any? = null): Any? {
    return referTab ?: mTab?.get()
  }

  fun getUrl(tab: Any? = null): String? {
    val url = getTab(tab)?.invokeMethod { name == "getUrl" }
    return if (UserScriptHook.isInit) {
      UserScriptProxy.parseUrl(url)
    } else {
      url as String?
    }
  }

  fun updateTab(tab: Any?) {
    if (tab != null) {
      mTab = WeakReference(tab)
      if (Chrome.isSamsung) {
        val context = findField(tab::class.java) { name == "mContext" }
        mContext = WeakReference(context.get(tab) as Context)
      }
    }
  }

  fun getTabId(tab: Any?, url: String? = null): String {
    if (WebViewHook.isInit) {
      val attached = tab == Chrome.getTab()
      val ids = filterTabs {
        val description = JSONObject(getString("description"))
        optString("type") == "page" &&
            optString("url") == url!! &&
            !description.optBoolean("never_attached") &&
            !(attached && !description.optBoolean("attached"))
      }
      if (ids.size > 1) Log.i("Multiple possible tabIds matched with url ${url}")
      return ids.first()
    } else {
      return getTab(tab)!!.invokeMethod() { name == "getId" }.toString()
    }
  }

  private fun evaluateJavascriptDevTools(codes: List<String>, tabId: String, bypassCSP: Boolean) {
    wakeUpDevTools()
    var client = DevSessions.new(tabId)
    codes.forEach { client.evaluateJavascript(it) }
    // Bypass CSP is only effective after reloading
    client.bypassCSP(bypassCSP)

    if (!bypassCSP) client.close()
  }

  fun evaluateJavascript(
      codes: List<String>,
      currentTab: Any? = null,
      forceDevTools: Boolean = false,
      bypassCSP: Boolean = false,
  ) {
    if (forceDevTools) {
      IO.submit {
        val tabId = getTabId(currentTab)
        evaluateJavascriptDevTools(codes, tabId, bypassCSP)
      }
    } else {
      if (codes.size == 0) return
      Handler(getContext().mainLooper).post {
        if (WebViewHook.isInit) {
          codes.forEach { WebViewHook.evaluateJavascript(it, currentTab) }
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
      matching: (String?) -> Boolean
  ) {
    val code = "Symbol.${Local.name}.unlock(${Local.key}).post('${event}', ${data});"
    Log.d("broadcasting ${event}")
    if (WebViewHook.isInit) {
      val tabs = WebViewHook.records.filter { matching(it.get()?.getUrl()) }
      if (tabs.size > 1 || !excludeSelf)
          tabs.forEach { WebViewHook.evaluateJavascript(code, it.get()) }
      return
    }
    IO.submit {
      val tabs = filterTabs {
        optString("type") == "page" &&
            matching(optString("url")) &&
            (optString("description") == "" ||
                !JSONObject(getString("description")).optBoolean("never_attached"))
      }

      if (tabs.size > 1 || !excludeSelf)
          tabs.forEach { evaluateJavascriptDevTools(listOf(code), it, false) }
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
