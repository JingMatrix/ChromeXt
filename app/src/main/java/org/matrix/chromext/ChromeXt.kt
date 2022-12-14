package org.matrix.chromext

import android.content.Context
import android.content.SharedPreferences
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import java.lang.reflect.Field
import org.matrix.chromext.script.youtubeScript

class ChromeXt(ctx: Context) {
  // These smali code names are possible to change when Chrome updates
  // User should be able to change them by their own if needed
  // If a field is read-only, i.e., initilized with `val`, meaning that we are not using it yet

  // Grep Android.Omnibox.InputToNavigationControllerStart to get loadUrl in
  // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
  var NAVI_LOAD_URL = "h"
  // ! Note: loadUrl is only called for browser-Initiated navigations

  // Grep smali code with Tab.loadUrl to get the loadUrl function in
  // org/chromium/chrome/browser/tab/TabImpl.smali
  var LOAD_URL = "h"

  // It is possible to a HTTP POST with LoadUrlParams Class
  // grep org/chromium/content_public/common/ResourceRequestBody to get setPostData in
  // org/chromium/content_public/browser/LoadUrlParams
  val POST_DATA = "b"
  // ! Note: this is very POWERFUL

  // Grep ()I to get or goToNavigationIndex in
  // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
  // Current tab has the biggest index, a new tab has index 0, index is stored with tab
  val NAVI_GOTO = "z"

  // Grep (I)V to get or getLastCommittedEntryIndex in
  // org/chromium/content/browser/framehost/NavigationControllerImpl.smali
  // Current tab has the biggest index, a new tab has index 0, index is stored with tab
  val NAVI_LAST_INDEX = "e"

  // Grep Android.Intent.IntentUriNavigationResult to get class
  // org/chromium/components/external_intents/ExternalNavigationHandler.java
  val EXTERNALNAVIGATIONHANDLER = "GC0"

  // Grep .super Lorg/chromium/components/navigation_interception/InterceptNavigationDelegate
  // to get class org.chromium.components.external_intents.InterceptNavigationDelegateImpl
  val INTERCEPTNAVIGATIONDELEGATEIMPL = "yi1"

  // Grep (Lorg/chromium/content_public/browser/WebContents;)V
  // in INTERCEPTNAVIGATIONDELEGATEIMPL to get associateWithWebContents
  val ASSOCIATE_CONTENTS = "a"

  companion object {
    // These samli code are considered stable for further releases of Chrome
    // We give two examplar fields.

    // The only field with type Ljava/lang/String in
    // org/chromium/url/GURL.smali is for URL
    private const val SPEC_FIELD = "a"
    // ! Note: GURL has limited length:
    // const size_t kMaxURLChars = 2 * 1024 * 1024; in chromium/src/ur/url_constants.cc
    // const uint32 kMaxURLChars = 2097152; in chromium/src/url/mojom/url.mojom

    // Get TabImpl field in
    // org/chromium/chrome/browser/tab/TabWebContentsDelegateAndroidImpl.smali
    private const val TAB_FIELD = "a"

    // Fields of org/chromium/content_public/browser/LoadUrlParams
    // are too many to list here
    // They are in the same order as the source code
  }

  var tabWebContentsDelegateAndroidImpl: Class<*>? = null
  private var mTab: Field? = null
  private var tabDelegator: Any? = null

  var interceptNavigationDelegateImpl: Class<*>? = null

  var navigationControllerImpl: Class<*>? = null
  private var navController: Any? = null

  var webContentsObserverProxy: Class<*>? = null

  private val navigationHandle: Class<*>? = null

  private var gURL: Class<*>? = null
  private var mSpec: Field? = null

  private var loadUrlParams: Class<*>? = null
  private var mUrl: Field? = null
  private val mInitiatorOrigin: Field? = null
  private val mLoadUrlType: Field? = null
  private val mTransitionType: Field? = null
  private val mReferrer: Field? = null
  private val mExtraHeaders: Field? = null
  private val mNavigationHandleUserDataHost: Field? = null
  private val mVerbatimHeaders: Field? = null
  private val mUaOverrideOption: Field? = null
  private val mPostData: Field? = null
  private val mBaseUrlForDataUrl: Field? = null
  private val mVirtualUrlForDataUrl: Field? = null
  private val mDataUrlAsString: Field? = null
  private val mCanLoadLocalResources: Field? = null
  private val mIsRendererInitiated: Field? = null
  private val mShouldReplaceCurrentEntry: Field? = null
  private val mIntentReceivedTimestamp: Field? = null
  private val mInputStartTimestamp: Field? = null
  private val mHasUserGesture: Field? = null
  private val mShouldClearHistoryList: Field? = null
  private val mNavigationUIDataSupplier: Field? = null

  init {
    val sharedPref: SharedPreferences = ctx.getSharedPreferences("ChromeXt", Context.MODE_PRIVATE)
    updateSmali(sharedPref)
    gURL = ctx.getClassLoader().loadClass("org.chromium.url.GURL")
    loadUrlParams =
        ctx.getClassLoader().loadClass("org.chromium.content_public.browser.LoadUrlParams")
    tabWebContentsDelegateAndroidImpl =
        ctx.getClassLoader()
            .loadClass("org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroidImpl")
    interceptNavigationDelegateImpl =
        ctx.getClassLoader().loadClass(INTERCEPTNAVIGATIONDELEGATEIMPL)
    navigationControllerImpl =
        ctx.getClassLoader()
            .loadClass("org.chromium.content.browser.framehost.NavigationControllerImpl")
    webContentsObserverProxy =
        ctx.getClassLoader()
            .loadClass("org.chromium.content.browser.webcontents.WebContentsObserverProxy")
    mUrl = loadUrlParams!!.getDeclaredField("a")
    mTab = tabWebContentsDelegateAndroidImpl!!.getDeclaredField(TAB_FIELD)
    mSpec = gURL!!.getDeclaredField(SPEC_FIELD)
  }
  private fun updateSmali(sharedPref: SharedPreferences) {
    if (sharedPref.contains("NAVI_LOAD_URL")) {
      NAVI_LOAD_URL = sharedPref.getString("NAVI_LOAD_URL", NAVI_LOAD_URL)!!
    } else {
      writeSmali(sharedPref)
      return
    }
    if (sharedPref.contains("LOAD_URL")) {
      LOAD_URL = sharedPref.getString("LOAD_URL", LOAD_URL)!!
    } else {
      writeSmali(sharedPref)
      return
    }
  }

  private fun writeSmali(sharedPref: SharedPreferences) {
    with(sharedPref.edit()) {
      putString("NAVI_LOAD_URL", NAVI_LOAD_URL)
      putString("LOAD_URL", LOAD_URL)
      apply()
    }
  }

  private fun loadUrl(url: String) {
    mTab!!.get(tabDelegator)?.invokeMethod(
        loadUrlParams!!.getDeclaredConstructor(String::class.java).newInstance(url)) {
          name == LOAD_URL
        }
  }

  private fun evaluateJavaScript(script: String) {
    val alphabet: List<Char> = ('a'..'z') + ('A'..'Z')
    val randomString: String = List(14) { alphabet.random() }.joinToString("")
    loadUrl("javascript: void(globalThis.${randomString} = '')")
    script.chunked(2090000).forEach {
      loadUrl("javascript: void(globalThis.${randomString} += '${it.replace("'", "\\'")}')")
    }
    loadUrl("javascript: Function(globalThis.${randomString})();")
  }

  private fun naviUrl(url: String) {
    navController!!.invokeMethod(
        loadUrlParams!!.getDeclaredConstructor(String::class.java).newInstance(url)) {
          name == NAVI_LOAD_URL
        }
  }

  fun parseUrl(packed: Any): String? {
    if (packed::class.qualifiedName == loadUrlParams!!.getName()) {
      return mUrl!!.get(packed) as String
    } else if (packed::class.qualifiedName == gURL!!.getName()) {
      return mSpec!!.get(packed) as String
    }
    Log.e(
        "parseUrl: ${packed::class.qualifiedName} is not ${loadUrlParams!!.getName()} nor ${gURL!!.getName()}")
    return null
  }

  fun changeUrl(packed: Any, url: String): Boolean {
    if (packed::class.qualifiedName == loadUrlParams!!.getName()) {
      mUrl!!.set(packed, url)
      return true
    }
    Log.e("changeUrl: ${packed::class.qualifiedName} is not ${loadUrlParams!!.getName()}")
    return false
  }

  fun updateTabDelegator(delegator: Any): Boolean {
    if (delegator::class.qualifiedName == tabWebContentsDelegateAndroidImpl!!.getName()) {
      if (tabDelegator != delegator) {
        Log.i("tabDelegator updated")
        tabDelegator = delegator
      }
      return true
    }
    Log.e(
        "updateTabDelegator: ${delegator::class.qualifiedName} is not ${tabWebContentsDelegateAndroidImpl!!.getName()}")
    return false
  }

  fun updateNavController(controller: Any): Boolean {
    if (controller::class.qualifiedName == navigationControllerImpl!!.getName()) {
      if (navController != controller) {
        Log.i("navController updated")
        navController = controller
      }
      return true
    }
    Log.e(
        "updateNavController: ${controller::class.qualifiedName} is not ${navigationControllerImpl!!.getName()}")
    return false
  }

  fun didUpdateUrl(url: String) {}

  fun didStartLoading(url: String) {}

  fun didStopLoading(url: String) {
    if (url.startsWith("https://m.youtube.com")) {
      evaluateJavaScript(youtubeScript)
    }
  }
}
