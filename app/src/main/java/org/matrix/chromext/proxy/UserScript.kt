package org.matrix.chromext.proxy

import android.content.Context
import android.content.SharedPreferences
import java.lang.reflect.Field
import java.net.URLEncoder
import org.matrix.chromext.BuildConfig
import org.matrix.chromext.TabModel
import org.matrix.chromext.script.Script
import org.matrix.chromext.script.ScriptDbManger
import org.matrix.chromext.script.encodeScript
import org.matrix.chromext.script.urlMatch
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod

class UserScriptProxy(ctx: Context, split: Boolean) {
  // These smali code names are possible to change when Chrome updates
  // User should be able to change them by their own if needed
  // If a field is read-only, i.e., initilized with `val`, meaning that we are not using it yet

  // Grep smali code with Tab.loadUrl to get the loadUrl function in
  // org/chromium/chrome/browser/tab/TabImpl.smali
  var LOAD_URL = "h"

  // Grep TabModelImpl to get the class TabModelImpl
  var TAB_MODEL_IMPL = "be3"

  // Grep Android.Omnibox.InputToNavigationControllerStart to get loadUrl in
  // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
  // val NAVI_LOAD_URL = "h"
  // ! Note: loadUrl is only called for browser-Initiated navigations

  // It is possible to a HTTP POST with LoadUrlParams Class
  // grep org/chromium/content_public/common/ResourceRequestBody to get setPostData in
  // org/chromium/content_public/browser/LoadUrlParams
  // val POST_DATA = "b"
  // ! Note: this is very POWERFUL

  // Grep ()I to get or goToNavigationIndex in
  // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
  // Current tab has the biggest index, a new tab has index 0, index is stored with tab
  // val NAVI_GOTO = "z"

  // Grep (I)V to get or getLastCommittedEntryIndex in
  // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
  // Current tab has the biggest index, a new tab has index 0, index is stored with tab
  // val NAVI_LAST_INDEX = "e"

  // Grep Android.Intent.IntentUriNavigationResult to get class
  // org/chromium/components/external_intents/ExternalNavigationHandler.java
  // val EXTERNAL_NAVIGATION_HANDLER = "GC0"

  // Grep .super Lorg/chromium/components/navigation_interception/InterceptNavigationDelegate
  // to get class org.chromium.components.external_intents.InterceptNavigationDelegateImpl
  // val INTERCEPT_NAVIGATION_DELEGATE_IMPL = "yi1"

  // Grep (Lorg/chromium/content_public/browser/WebContents;)V
  // in INTERCEPT_NAVIGATION_DELEGATE_IMPL to get associateWithWebContents
  // val ASSOCIATE_CONTENTS = "a"

  companion object {
    // These samli code are considered stable for further releases of Chrome
    // We give two examplar fields.

    // The only field with type Ljava/lang/String in
    // org/chromium/url/GURL.smali is for URL
    private const val SPEC_FIELD = "a"
    // ! Note: GURL has limited length:
    // const size_t kMaxURLChars = 2 * 1024 * 1024; in chromium/src/ur/url_constants.cc
    // const uint32 kMaxURLChars = 2097152; in chromium/src/url/mojom/url.mojom
    const val kMaxURLChars = 2097152

    // Get TabImpl field in
    // org/chromium/chrome/browser/tab/TabWebContentsDelegateAndroidImpl.smali
    // private const val TAB_FIELD = "a"

    // Fields of org/chromium/content_public/browser/LoadUrlParams
    // are too many to list here
    // They are in the same order as the source code

  }

  var tabWebContentsDelegateAndroidImpl: Class<*>

  var tabModelImpl: Class<*>

  // val interceptNavigationDelegateImpl: Class<*>? = null

  // val navigationControllerImpl: Class<*>? = null
  // private val navController: Any? = null

  // val webContentsObserverProxy: Class<*>? = null

  // private val navigationHandle: Class<*>? = null

  private var gURL: Class<*>
  private var mSpec: Field

  private var loadUrlParams: Class<*>
  private var mUrl: Field
  // private val mInitiatorOrigin: Field? = null
  // private val mLoadUrlType: Field? = null
  // private val mTransitionType: Field? = null
  // private val mReferrer: Field? = null
  // private val mExtraHeaders: Field? = null
  // private val mNavigationHandleUserDataHost: Field? = null
  // private val mVerbatimHeaders: Field? = null
  // private val mUaOverrideOption: Field? = null
  // private val mPostData: Field? = null
  // private val mBaseUrlForDataUrl: Field? = null
  // private val mVirtualUrlForDataUrl: Field? = null
  // private val mDataUrlAsString: Field? = null
  // private val mCanLoadLocalResources: Field? = null
  // private val mIsRendererInitiated: Field? = null
  // private val mShouldReplaceCurrentEntry: Field? = null
  // private val mIntentReceivedTimestamp: Field? = null
  // private val mInputStartTimestamp: Field? = null
  // private val mHasUserGesture: Field? = null
  // private val mShouldClearHistoryList: Field? = null
  // private val mNavigationUIDataSupplier: Field? = null

  var scriptManager: ScriptDbManger

  init {
    if (!split) {
      LOAD_URL = "f"

      // Attention, TAB_MODEL_IMPL for non-split version should be loaded at MainHook
      // TAB_MODEL_IMPL = "pw3"
    }

    if (!BuildConfig.DEBUG) {
      val sharedPref: SharedPreferences = ctx.getSharedPreferences("ChromeXt", Context.MODE_PRIVATE)
      updateSmali(sharedPref)
    }

    scriptManager = ScriptDbManger(ctx)
    gURL = ctx.getClassLoader().loadClass("org.chromium.url.GURL")
    loadUrlParams =
        ctx.getClassLoader().loadClass("org.chromium.content_public.browser.LoadUrlParams")
    tabWebContentsDelegateAndroidImpl =
        ctx.getClassLoader()
            .loadClass("org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroidImpl")
    tabModelImpl = ctx.getClassLoader().loadClass(TAB_MODEL_IMPL)
    // interceptNavigationDelegateImpl =
    //     ctx.getClassLoader().loadClass(INTERCEPT_NAVIGATION_DELEGATE_IMPL)
    // navigationControllerImpl =
    //     ctx.getClassLoader()
    //         .loadClass("org.chromium.content.browser.framehost.NavigationControllerImpl")
    // webContentsObserverProxy =
    //     ctx.getClassLoader()
    //         .loadClass("org.chromium.content.browser.webcontents.WebContentsObserverProxy")
    mUrl = loadUrlParams.getDeclaredField("a")
    // mVerbatimHeaders = loadUrlParams!!.getDeclaredField("h")
    // mTab = tabWebContentsDelegateAndroidImpl!!.getDeclaredField(TAB_FIELD)
    mSpec = gURL.getDeclaredField(SPEC_FIELD)
  }

  private fun updateSmali(sharedPref: SharedPreferences) {
    if (sharedPref.contains("LOAD_URL")) {
      LOAD_URL = sharedPref.getString("LOAD_URL", LOAD_URL)!!
      TAB_MODEL_IMPL = sharedPref.getString("TAB_MODEL_IMPL", TAB_MODEL_IMPL)!!
    }
    with(sharedPref.edit()) {
      clear()
      putString("LOAD_URL", LOAD_URL)
      putString("TAB_MODEL_IMPL", TAB_MODEL_IMPL)
      apply()
    }
  }

  private fun loadUrl(url: String) {
    TabModel.getTab().invokeMethod(newUrl(url)) { name == LOAD_URL }
    Log.d("loadUrl: ${url}")
  }

  fun newUrl(url: String): Any {
    return loadUrlParams
        .getDeclaredConstructor(String::class.java, Int::class.java)
        .newInstance(url, 0)
  }

  private fun invokeScript(url: String) {
    scriptManager.getAll().forEach {
      val script = it
      var run = false

      script.exclude.forEach {
        if (it != "" && urlMatch(it, url)) {
          return
        }
      }

      script.match.forEach {
        if (!run && urlMatch(it, url)) {
          run = true
        }
      }

      if (run) {
        evaluateJavaScript(script)
        Log.i("${script.id} injected")
      }
    }
  }

  private fun evaluateJavaScript(script: Script) {
    val code = encodeScript(script)
    if (code != null) {
      evaluateJavaScript(code)
    }
  }

  fun evaluateJavaScript(script: String) {
    // Encode as Url makes it easier to copy and paste for debugging
    if (script == "") return
    if (script.length > kMaxURLChars - 1000) {
      val alphabet: List<Char> = ('a'..'z') + ('A'..'Z')
      val randomString: String = List(14) { alphabet.random() }.joinToString("")
      loadUrl("javascript: void(globalThis.${randomString} = '');")
      script.chunked(2000000).forEach {
        val code =
            URLEncoder.encode(
                    """void(globalThis.${randomString} += `${it.replace("`", "\\`")}`);""", "UTF-8")
                .replace("+", "%20")
        loadUrl("javascript: ${code}")
      }
      loadUrl("javascript: Function(globalThis.${randomString})();")
    } else {
      val code = URLEncoder.encode(script, "UTF-8").replace("+", "%20")
      loadUrl("javascript: ${code}")
    }
  }

  fun parseUrl(packed: Any): String? {
    if (packed::class.qualifiedName == loadUrlParams.name) {
      return mUrl.get(packed) as String
    } else if (packed::class.qualifiedName == gURL.name) {
      return mSpec.get(packed) as String
    }
    Log.e(
        "parseUrl: ${packed::class.qualifiedName} is not ${loadUrlParams.name} nor ${gURL.getName()}")
    return null
  }

  fun updateTabModel(model: Any) {
    TabModel.update(model, TAB_MODEL_IMPL)
  }

  // fun updateNavController(controller: Any): Boolean {
  //   if (controller::class.qualifiedName == navigationControllerImpl!!.name) {
  //     if (navController != controller) {
  //       Log.i("navController updated")
  //       // navController = controller
  //     }
  //     return true
  //   }
  //   Log.e(
  //       "updateNavController: ${controller::class.qualifiedName} is not
  // ${navigationControllerImpl!!.name}")
  //   return false
  // }

  fun didUpdateUrl(url: String) {
    if (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("file://")) {
      invokeScript(url)
    }
    TabModel.refresh()
  }

  // fun didStartLoading(url: String) {
  //   invokeScriptAt(RunAt.START, url)
  // }

  // fun didStopLoading(url: String) {
  //   invokeScriptAt(RunAt.END, url)
  // }

  fun openDevTools(ctx: Context) {
    evaluateJavaScript(TabModel.openEruda(ctx))
  }

  fun fixErudaFont(ctx: Context) {
    evaluateJavaScript(TabModel.getEurdaFontFix(ctx))
  }
}
