package org.matrix.chromext.proxy

import android.net.Uri
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findField
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.findMethodOrNull
import org.matrix.chromext.utils.firstDeclared
import org.matrix.chromext.utils.invokeMethod
import org.matrix.chromext.utils.parseOrigin
import org.matrix.chromext.utils.r8Rank

object UserScriptProxy {
  // It is possible to do a HTTP POST with LoadUrlParams Class
  // grep org/chromium/content_public/common/ResourceRequestBody to get setPostData in
  // org/chromium/content_public/browser/LoadUrlParams

  val gURL = Chrome.load("org.chromium.url.GURL")
  val loadUrlParams =
      if (Chrome.isSamsung) {
        Chrome.load("com.sec.android.app.sbrowser.tab.LoadUrlParams")
      } else {
        Chrome.load("org.chromium.content_public.browser.LoadUrlParams")
      }
  // val tabModelJniBridge = Chrome.load("org.chromium.chrome.browser.tabmodel.TabModelJniBridge")
  val tabWebContentsDelegateAndroidImpl =
      if (Chrome.isSamsung) {
        Chrome.load("com.sec.android.app.sbrowser.tab.Tab")
      } else {
        Chrome.load("org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroidImpl")
      }
  val navigationControllerImpl =
      Chrome.load("org.chromium.content.browser.framehost.NavigationControllerImpl")
  val chromeTabbedActivity =
      if (Chrome.isSamsung) {
        Chrome.load("com.sec.terrace.TerraceActivity")
      } else {
        Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
      }
  val tabImpl =
      if (Chrome.isSamsung) {
        Chrome.load("com.sec.terrace.Terrace")
      } else {
        Chrome.load("org.chromium.chrome.browser.tab.TabImpl")
      }
  private val getId = findMethodOrNull(tabImpl) { name == "getId" }
  // Only consulted when getId is missing, which is never the case on Chrome itself: mId is the
  // first int declared by TabImpl, right after the native pointer. Lazy, so that a browser we
  // cannot make sense of costs us getTabId rather than the whole module.
  private val mId: Field? by lazy {
    (if (Chrome.isSamsung) tabWebContentsDelegateAndroidImpl else tabImpl)
        .declaredFields
        .filter { !Modifier.isStatic(it.modifiers) }
        .run { find { it.name == "mId" } ?: filter { it.type == Int::class.java }.firstDeclared() }
        ?.also { it.isAccessible = true }
  }
  val mTab = findField(tabWebContentsDelegateAndroidImpl) { type == tabImpl }
  val mIsLoading: Field? by lazy {
    // mIsLoading is used in method stopLoading, before calling
    // Lorg/chromium/content_public/browser/WebContents;->stop()V, and TabImpl declares it right
    // after the WebContents and the pending LoadUrlParams, hence the two anchors below.
    tabImpl.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) }
        .run {
          find { it.name == "mIsLoading" }
              ?: run {
                val webContents = Chrome.load("org.chromium.content_public.browser.WebContents")
                val anchor =
                    maxOf(
                        filter { it.type == webContents }.firstDeclared()?.let { r8Rank(it.name) }
                            ?: -1,
                        filter { it.type == loadUrlParams }.firstDeclared()?.let { r8Rank(it.name) }
                            ?: -1)
                // Without an anchor any boolean is as good as any other, so do not guess.
                if (anchor < 0) null
                else
                    filter { it.type == Boolean::class.java && r8Rank(it.name) > anchor }
                        .firstDeclared()
              }
        }
        ?.also { it.isAccessible = true }
  }
  // Match the name first: picking by return type alone lands on getOriginalUrl, which hands back
  // the distilled URL rather than the one the tab is actually showing.
  val getUrl =
      findMethodOrNull(tabImpl) { name == "getUrl" && returnType == gURL }
          ?: findMethodOrNull(tabImpl) { returnType == gURL && parameterTypes.isEmpty() }
  val loadUrl =
      findMethod(if (Chrome.isSamsung) tabWebContentsDelegateAndroidImpl else tabImpl) {
        parameterTypes contentDeepEquals arrayOf(loadUrlParams) &&
            (Chrome.isSamsung || returnType != Void.TYPE)
      }

  // Chrome keeps none of these names, so fall back to the declaration order recovered from the
  // obfuscated ones: LoadUrlParams declares mUrl first and mVerbatimHeaders second among its
  // strings, and GURL declares mSpec as its only one.
  private fun stringField(clz: Class<*>, name: String, skip: Int = 0): Field? =
      clz.declaredFields
          .filter { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
          .run {
            find { it.name == name }
                ?: filter { r8Rank(it.name) != Int.MAX_VALUE }
                    .sortedBy { r8Rank(it.name) }
                    .getOrNull(skip)
          }
          ?.also { it.isAccessible = true }

  private val mUrl by lazy { stringField(loadUrlParams, "mUrl") }
  private val mVerbatimHeaders by lazy { stringField(loadUrlParams, "mVerbatimHeaders", 1) }
  private val mSpec by lazy { stringField(gURL, "mSpec") }

  val kMaxURLChars = 2097152

  private fun loadUrl(url: String, tab: Any? = Chrome.getTab()) {
    if (!Chrome.isSamsung && !Chrome.checkTab(tab)) return
    loadUrl.invoke(tab, newLoadUrlParams(url))
  }

  // Fallback for when mIsLoading cannot be located: UserScriptHook then hooks loadingStateChanged,
  // whose name the obfuscation preserves because it is called from the native side, and feeds the
  // state here instead.
  private val loadingTabs = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
  private var trackLoadingState = false

  fun startTrackingLoadingState() {
    trackLoadingState = true
  }

  fun setLoading(tab: Any?, loading: Boolean) {
    if (tab == null) return
    if (loading) loadingTabs.add(tab) else loadingTabs.remove(tab)
  }

  fun isLoading(tab: Any): Boolean {
    mIsLoading?.let {
      return it.get(tab) as Boolean
    }
    // With no way to tell, keep injecting: both the init script and GM.bootstrap are idempotent
    // for a given document.
    return if (trackLoadingState) loadingTabs.contains(tab) else true
  }

  fun getTabId(tab: Any): String {
    val id = getId?.invoke(tab) ?: mId?.get(tab)
    if (id == null) Log.e("Failed to read the tab id of ${tab::class.java}")
    return id.toString()
  }

  fun newLoadUrlParams(url: String): Any {
    val constructor =
        loadUrlParams.declaredConstructors.find { it.parameterTypes.contains(String::class.java) }!!
    val types = constructor.parameterTypes
    if (types contentDeepEquals arrayOf(Int::class.java, String::class.java)) {
      return constructor.newInstance(0, url)
    } else if (types contentDeepEquals arrayOf(String::class.java, Int::class.java)) {
      return constructor.newInstance(url, 0)
    } else {
      return constructor.newInstance(url)
    }
  }

  fun evaluateJavascript(script: String, tab: Any? = Chrome.getTab()): Boolean {
    if (script == "") return true
    if (Chrome.isSamsung) {
      mTab.get(tab ?: Chrome.getTab())?.invokeMethod(script, null) {
        name == "evaluateJavaScriptForTests"
      }
      return true
    }
    if (script.length > kMaxURLChars - 20000) return false
    val code = Uri.encode(script)
    if (code.length < kMaxURLChars - 200) {
      loadUrl("javascript:${code}", tab ?: Chrome.getTab())
      return true
    } else {
      return false
    }
  }

  fun getTab(delegate: Any): Any? {
    return if (Chrome.isSamsung) delegate else mTab.get(delegate)
  }

  fun parseUrl(packed: Any?): String? {
    if (packed == null) {
      return null
    } else if (packed::class.java == String::class.java) {
      return packed as String
    } else if (packed::class.java == loadUrlParams) {
      return mUrl?.get(packed) as String?
    } else if (packed::class.java == gURL) {
      return mSpec?.get(packed) as String?
    }
    Log.e("parseUrl: ${packed::class.java} is not ${loadUrlParams.name} nor ${gURL.name}")
    return null
  }

  fun userAgentHook(url: String, urlParams: Any): Boolean {
    val origin = parseOrigin(url)
    if (origin != null) {
      // Log.d("Change User-Agent header: ${origin}")
      if (ScriptDbManager.userAgents.contains(origin)) {
        val header = "user-agent: ${ScriptDbManager.userAgents.get(origin)}\r\n"
        if (Chrome.isSamsung) {
          urlParams.invokeMethod(header) { name == "setVerbatimHeaders" }
        } else {
          mVerbatimHeaders?.set(urlParams, header) ?: return false
        }
        return true
      }
    }
    return false
  }
}
