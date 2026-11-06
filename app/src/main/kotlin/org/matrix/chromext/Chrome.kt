package org.matrix.chromext

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.net.http.HttpResponseCache
import android.os.Build
import android.os.Handler
import java.io.File
import java.io.FileReader
import java.lang.ref.WeakReference
import java.net.CookieManager
import java.net.HttpCookie
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.devtools.DevSessions
import org.matrix.chromext.devtools.getInspectPages
import org.matrix.chromext.devtools.hitDevTools
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.XMLHttpRequest
import org.matrix.chromext.utils.findField
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.invokeMethod

object Chrome {
  private var mContext: WeakReference<Context>? = null
  private var mActivity: WeakReference<android.app.Activity>? = null
  private var mTab: WeakReference<Any>? = null
  private var devToolsReady = false

  var isBrave = false
  var isDev = false
  var isHerond = false
  var isMi = false
  var isQihoo = false
  var isVivaldi = false
  var isWhale = false
  var isCocCoc = false

  var version: String? = null
  var packageName: String? = null

  val IO = Executors.newCachedThreadPool()
  // Tabs seen by the hooks, since Chrome 156 no longer hands out its tab model by name
  private val knownTabs = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Any, Boolean>())
  val cookieStore = CookieManager().getCookieStore()

  fun init(ctx: Context, packageName: String? = null) {
    val initialized = mContext != null
    mContext = WeakReference(ctx)
    if (ctx is android.app.Activity) mActivity = WeakReference(ctx)

    if (initialized || packageName == null) return
    this.packageName = packageName

    isBrave =
        packageName.startsWith("com.brave.browser") || packageName == "com.herond.android.browser"
    isCocCoc = packageName.startsWith("com.coccoc.trinhduyet")
    isDev = packageName.endsWith("canary") || packageName.endsWith("dev")
    isHerond = packageName == "com.herond.android.browser"
    isMi = packageName == "com.mi.globalbrowser" || packageName == "com.android.browser"
    isQihoo = packageName == "com.qihoo.contents"
    // com.vivaldi.browser.snapshot is supported too, and it needs the same special cases as stable
    isVivaldi = packageName.startsWith("com.vivaldi.browser")
    isWhale = packageName == "com.naver.whale"
    @Suppress("DEPRECATION") val packageInfo = ctx.packageManager?.getPackageInfo(packageName, 0)
    version = packageInfo?.versionName
    version = (if (version?.startsWith("v") == true) "" else "v") + version
    Log.i("Package: ${packageName}, ${version}")

    setupHttpCache(ctx)
    saveRedirectCookie()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val groupId = "org.matrix.chromext"
      val group = NotificationChannelGroup(groupId, "ChromeXt")
      val notificationManager =
          ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannelGroup(group)
      val id = "xposed_notification"
      val name = "UserScript Notifications"
      val desc = "Notifications created by the GM_notification API"
      val default_channel =
          NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = desc
            setGroup(groupId)
          }
      val silent_channel =
          NotificationChannel(id + "_slient", "Silent " + name, NotificationManager.IMPORTANCE_LOW)
              .apply {
                description = desc
                setGroup(groupId)
              }
      notificationManager.createNotificationChannel(default_channel)
      notificationManager.createNotificationChannel(silent_channel)
    }
  }

  private fun setupHttpCache(context: Context) {
    val httpCacheDir = File(context.getCacheDir(), "ChromeXt")
    val httpCacheSize = 16 * 1024 * 1024L
    HttpResponseCache.install(httpCacheDir, httpCacheSize)
  }

  private fun saveRedirectCookie() {
    val httpEngine = load("com.android.okhttp.internal.http.HttpEngine")
    val userRequest = findField(httpEngine) { name == "userRequest" }
    val userResponse = findField(httpEngine) { name == "userResponse" }
    val urlString = findMethod(userRequest.type) { name == "urlString" }
    val headers = findField(userResponse.type) { name == "headers" }
    val code = findField(userResponse.type) { name == "code" }
    val message = findField(userResponse.type) { name == "message" }
    val toMultimap = findMethod(headers.type) { name == "toMultimap" }
    findMethod(httpEngine) { name == "followUpRequest" }
        .hookAfter {
          if (it.result != null) {
            val url = urlString.invoke(userRequest.get(it.thisObject)) as String
            val request = Listener.xmlhttpRequests.values.find { it.url.toString() == url }
            if (request == null || request.anonymous) return@hookAfter
            val res = userResponse.get(it.thisObject)
            @Suppress("UNCHECKED_CAST")
            val headerFields = toMultimap.invoke(headers.get(res)) as Map<String?, List<String>>
            storeCoookies(request, headerFields)
            val data = JSONObject()
            data.put("status", code.get(res) as Int)
            data.put("statusText", message.get(res) as String)
            data.put("headers", JSONObject(headerFields.mapValues { JSONArray(it.value) }))
            request.response("redirect", data, false)
          }
        }
  }

  fun storeCoookies(
      request: XMLHttpRequest,
      headerFields: Map<String?, List<String>>,
  ) {
    headerFields
        .filter { it.key != null && it.key!!.lowercase().startsWith("set-cookie") }
        .forEach {
          it.value.forEach { HttpCookie.parse(it).forEach { cookieStore.add(request.uri, it) } }
        }
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
    val activity =
        getTab()?.invokeMethod { name == "getContext" || returnType == Context::class.java }
            as Context?
    if (activity != null && mContext == null) init(activity, activity.packageName)
    return activity ?: mContext!!.get()!!
  }

  fun checkTab(tab: Any?): Boolean {
    if (tab == null) return false
    if (UserScriptHook.isInit) {
      return tab.invokeMethod { name == "isInitialized" } as Boolean
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
    registerTab(tab)
    if (tab != null && tab != getTab()) {
      mTab = WeakReference(tab)
    }
  }

  /** Remember a tab so that extension APIs can enumerate tabs without the tab model. */
  fun registerTab(tab: Any?) {
    if (tab == null) return
    synchronized(knownTabs) { knownTabs.add(tab) }
  }

  fun getTabId(tab: Any?, url: String? = null): String {
    if (WebViewHook.isInit) {
      if (url == null && getContext().mainLooper.getThread() != Thread.currentThread())
          Log.w("Url parameter is missing in a non-UI thread")
      val attached = !WebViewHook.isInit || tab == Chrome.getTab()
      val ids = filterTabs {
        if (getString("description") == "") {
          optString("type") == "page" && optString("url") == url!!
        } else {
          val description = JSONObject(getString("description"))
          optString("type") == "page" &&
              optString("url") == url!! &&
              !description.optBoolean("never_attached") &&
              !(attached && !description.optBoolean("attached"))
        }
      }
      if (ids.size > 1) Log.i("Multiple possible tabIds matched with url ${url}")
      return ids.first()
    } else {
      return UserScriptProxy.getTabId(getTab(tab)!!)
    }
  }

  fun injectFrames(tab: Any? = null) {
    val url = getUrl(tab)!!
    IO.submit {
      val tabId = getTabId(tab, url)
      wakeUpDevTools()
      var client = DevSessions.new(tabId, "page")
      DevSessions.add(client)
      val duplicated = client.isPageEnabled(true)
      if (duplicated) return@submit
      var frames: MutableMap<String, String> = mutableMapOf<String, String>()
      client.listen {
        if (it.has("method")) {
          val method = it.getString("method")
          val params = it.getJSONObject("params")
          val frameId =
              if (method == "Page.frameDetached") {
                params.getString("frameId")
              } else if (method == "Page.frameNavigated") {
                params.getJSONObject("frame").getString("id")
              } else null
          if (method == "Page.frameScheduledNavigation" && params.getString("url") != url) {
            frames.put(params.getString("frameId"), params.getString("url"))
          } else if (frameId != null && frames.containsKey(frameId)) {
            val frameUrl = frames.get(frameId)
            Handler(getContext().mainLooper).post {
              ScriptDbManager.invokeScript(frameUrl!!, tab, frameId)
            }
            frames.remove(frameId)
          }
        }
      }
    }
  }

  private fun evaluateJavascriptDevTools(codes: List<String>, tabId: String, bypassCSP: Boolean) {
    wakeUpDevTools()
    var client = DevSessions.new(tabId, "page")
    codes.forEach { client.evaluateJavascript(it) }
    // Bypass CSP is only effective after reloading
    client.bypassCSP(bypassCSP)

    if (!bypassCSP) client.close()
  }

  fun evaluateJavascript(
      codes: List<String>,
      tab: Any? = null,
      frameId: String? = null,
      forceDevTools: Boolean = false,
      bypassCSP: Boolean = false,
  ) {
    if (codes.size == 0) return
    if (frameId != null || forceDevTools) {
      val url = getUrl(tab)
      IO.submit {
        val tabId = getTabId(tab, url)
        if (frameId != null) {
          var client = DevSessions.new(tabId, "page")
          val params = JSONObject().put("frameId", frameId)
          codes.forEach {
            client.command(
                null, "Page.navigate", params.put("url", "javascript: ${Uri.encode(it)}"))
          }
        } else {
          evaluateJavascriptDevTools(codes, tabId, bypassCSP)
        }
      }
    } else {
      Handler(getContext().mainLooper).post {
        if (WebViewHook.isInit) {
          codes.forEach { WebViewHook.evaluateJavascript(it, tab) }
        } else if (UserScriptHook.isInit) {
          val failed = codes.filter { !UserScriptProxy.evaluateJavascript(it, tab) }
          if (failed.size > 0) evaluateJavascript(failed, tab, frameId, true)
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
      val tabs =
          WebViewHook.records.filter {
            matching(it.get()?.invokeMethod() { name == "getUrl" } as String?)
          }
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

  /** The activity hosting the browser UI, unwrapping context wrappers. */
  fun activity(): android.app.Activity? {
    mActivity?.get()?.let {
      return it
    }
    var ctx: android.content.Context? = runCatching { getContext() }.getOrNull()
    while (ctx is android.content.ContextWrapper) {
      if (ctx is android.app.Activity) {
        mActivity = WeakReference(ctx)
        return ctx
      }
      ctx = ctx.baseContext
    }
    return ctx as? android.app.Activity
  }

  /** Whether a type looks like Chromium's tab model, whatever the obfuscation calls it. */
  private fun isTabModel(type: Class<*>?): Boolean {
    if (type == null || type.isPrimitive || type.isArray || type == String::class.java) return false
    val methods = runCatching { type.methods }.getOrNull() ?: return false
    val count = methods.any {
      it.name == "getCount" && it.parameterTypes.isEmpty() && it.returnType == Int::class.java
    }
    val tabAt = methods.any {
      it.name == "getTabAt" &&
          it.parameterTypes.size == 1 &&
          it.parameterTypes[0] == Int::class.java
    }
    return count && tabAt
  }

  private val tabAccessorNames =
      listOf("getTabModelSelector", "getCurrentTabModel", "getTabModel", "getTabModelSupplier")

  /** A type exposing a no-argument method that hands out a tab model, i.e. a model selector. */
  private fun selectorGetter(type: Class<*>?): java.lang.reflect.Method? {
    if (type == null || type.isPrimitive || type.isArray) return null
    val methods = runCatching { type.methods }.getOrNull() ?: return null
    return methods.firstOrNull { it.parameterTypes.isEmpty() && isTabModel(it.returnType) }
  }

  /**
   * Find the tab model of the activity.
   *
   * Chrome 156 keeps the accessor under an obfuscated name, but the signature is still readable, so
   * the model is recognised by shape: an object offering `getCount()` and `getTabAt(int)`, or a
   * selector offering a no-argument method that returns one.
   */
  private fun findTabModel(activity: android.app.Activity): Any? {
    var clazz: Class<*>? = activity::class.java
    while (clazz != null) {
      val methods = runCatching { clazz!!.declaredMethods.toList() }.getOrElse { emptyList() }
      for (method in methods) {
        if (method.parameterTypes.isNotEmpty()) continue
        val type = method.returnType ?: continue
        if (isTabModel(type)) {
          runCatching {
            method.isAccessible = true
            method.invoke(activity)
          }
              .getOrNull()
              ?.let {
                return it
              }
        } else {
          val getter = selectorGetter(type) ?: continue
          val selector =
              runCatching {
                method.isAccessible = true
                method.invoke(activity)
              }
                  .getOrNull() ?: continue
          runCatching {
            getter.isAccessible = true
            getter.invoke(selector)
          }
              .getOrNull()
              ?.let {
                return it
              }
        }
      }
      clazz = clazz.superclass
    }
    // The activity may hold the selector in a field instead of handing it out
    clazz = activity::class.java
    while (clazz != null) {
      val fields = runCatching { clazz!!.declaredFields.toList() }.getOrElse { emptyList() }
      for (field in fields) {
        if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
        val getter = selectorGetter(field.type) ?: continue
        val selector =
            runCatching {
              field.isAccessible = true
              field.get(activity)
            }
                .getOrNull() ?: continue
        runCatching {
          getter.isAccessible = true
          getter.invoke(selector)
        }
            .getOrNull()
            ?.let {
              return it
            }
      }
      clazz = clazz.superclass
    }
    // Fall back to names, should a future build hide the model behind an unusual signature
    clazz = activity::class.java
    while (clazz != null) {
      for (name in tabAccessorNames) {
        val value =
            runCatching {
              clazz!!.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
            }
                .getOrNull() ?: continue
        if (isTabModel(value.javaClass)) return value
        val getter = selectorGetter(value.javaClass) ?: continue
        runCatching { getter.invoke(value) }
            .getOrNull()
            ?.let {
              return it
            }
      }
      clazz = clazz.superclass
    }
    return null
  }

  /** Every tab of the current tab model, as real Tab objects (no DevTools needed). */
  fun tabList(): List<Any> {
    fromModel()?.let {
      return it
    }
    return synchronized(knownTabs) { knownTabs.toList() }
  }

  /** Every tab known to the model, restored ones included. */
  private fun fromModel(): List<Any>? {
    val activity = activity() ?: return null
    val model =
        findTabModel(activity)
            ?: run {
              Log.d("tabList: no tab model on ${activity::class.java.name}")
              return null
            }
    val count = runCatching {
      model.javaClass.getMethod("getCount").invoke(model) as Int
    }
        .getOrElse {
          Log.d("tabList: no count: ${it.message}")
          return emptyList()
        }
    val tabAt = runCatching {
      model.javaClass.getMethod("getTabAt", Int::class.java)
    }
        .getOrElse {
          Log.d("tabList: no getTabAt: ${it.message}")
          return emptyList()
        }
    return (0 until count).mapNotNull { runCatching { tabAt.invoke(model, it) }.getOrNull() }
  }

  /** Every inspectable page, as reported by the DevTools endpoint. */
  fun pages(): List<JSONObject> {
    wakeUpDevTools()
    val pages = runCatching { getInspectPages() }.getOrNull() ?: return emptyList()
    return (0 until pages.length()).mapNotNull { pages.optJSONObject(it) }
  }

  /** Evaluate code in a page designated by its DevTools id, without needing its tab object. */
  fun evaluateJavascriptInTabId(tabId: String, code: String): Boolean {
    if (code.isEmpty()) return false
    IO.submit {
      runCatching { evaluateJavascriptDevTools(listOf(code), tabId, false) }
          .onFailure { Log.ex(it, "Fail to evaluate in tab $tabId") }
    }
    return true
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

  private fun setFlag(name: String, value: Int): Boolean {
    val ctx = getContext()
    val localState = File(ctx.filesDir, "../app_chrome/Local State")
    if (!localState.exists()) return false
    val config = JSONObject(FileReader(localState).use { it.readText() })
    val labs = config.getJSONObject("browser").getJSONArray("enabled_labs_experiments")
    val newFlag = name + "@" + value
    var flagFound = false
    for (i in 0 until labs.length()) {
      val flag = labs.getString(i)
      if (flag == newFlag) return false
      if (flag.startsWith(name + "@")) {
        labs.put(i, newFlag)
        flagFound = true
        break
      }
    }
    if (!flagFound) labs.put(newFlag)
    localState.outputStream().write(config.toString().toByteArray())
    return true
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
