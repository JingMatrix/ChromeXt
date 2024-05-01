package org.matrix.chromext

import android.app.AndroidAppHelper
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.matrix.chromext.hook.BaseHook
import org.matrix.chromext.hook.ContextMenuHook
import org.matrix.chromext.hook.PageInfoHook
import org.matrix.chromext.hook.PageMenuHook
import org.matrix.chromext.hook.PreferenceHook
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.hookAfter

val supportedPackages =
    arrayOf(
        "com.android.chrome",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.brave.browser_nightly",
        "com.chrome.beta",
        "com.chrome.canary",
        "com.chrome.dev",
        "com.coccoc.trinhduyet",
        "com.coccoc.trinhduyet_beta",
        "com.kiwibrowser.browser",
        "com.microsoft.emmx",
        "com.microsoft.emmx.beta",
        "com.microsoft.emmx.canary",
        "com.microsoft.emmx.dev",
        "com.naver.whale",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.vivaldi.browser",
        "com.vivaldi.browser.snapshot",
        "org.bromite.bromite",
        "org.chromium.thorium",
        "org.cromite.cromite",
        "org.greatfire.freebrowser",
        "org.triple.banana",
        "us.spotco.mulch")

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    Log.d(lpparam.processName + " started")
    if (lpparam.packageName == "org.matrix.chromext") return
    if (supportedPackages.contains(lpparam.packageName)) {
      lpparam.classLoader
          .loadClass("org.chromium.ui.base.WindowAndroid")
          .declaredConstructors[1]
          .hookAfter {
            Chrome.init(it.args[0] as Context, lpparam.packageName)
            initHooks(UserScriptHook)
            if (ContextMenuHook.isInit) return@hookAfter
            runCatching {
                  initHooks(
                      PreferenceHook,
                      if (Chrome.isEdge || Chrome.isCocCoc) PageInfoHook else PageMenuHook)
                }
                .onFailure {
                  initHooks(ContextMenuHook)
                  if (BuildConfig.DEBUG) Log.ex(it)
                }
          }
    } else {
      val ctx = AndroidAppHelper.currentApplication()

      Chrome.isMi =
          lpparam.packageName == "com.mi.globalbrowser" ||
              lpparam.packageName == "com.android.browser"
      Chrome.isQihoo = lpparam.packageName == "com.qihoo.contents"

      if (ctx == null && Chrome.isMi) return
      // Wait to get the browser context of Mi Browser

      if (ctx != null && lpparam.packageName != "android") Chrome.init(ctx, ctx.packageName)

      if (Chrome.isMi) {
        WebViewHook.WebView = Chrome.load("com.miui.webkit.WebView")
        WebViewHook.ViewClient = Chrome.load("com.android.browser.tab.TabWebViewClient")
        WebViewHook.ChromeClient = Chrome.load("com.android.browser.tab.TabWebChromeClient")
        hookWebView()
        return
      }

      if (Chrome.isQihoo) {
        WebViewHook.WebView = Chrome.load("com.qihoo.webkit.WebView")
        WebViewHook.ViewClient = Chrome.load("com.qihoo.webkit.WebViewClient")
        WebViewHook.ChromeClient = Chrome.load("com.qihoo.webkit.WebChromeClient")
        hookWebView()
        return
      }

      WebViewClient::class.java.declaredConstructors[0].hookAfter {
        if (it.thisObject::class != WebViewClient::class) {
          WebViewHook.ViewClient = it.thisObject::class.java
          hookWebView()
        }
      }

      WebChromeClient::class.java.declaredConstructors[0].hookAfter {
        if (it.thisObject::class != WebChromeClient::class) {
          WebViewHook.ChromeClient = it.thisObject::class.java
          hookWebView()
        }
      }
    }
  }

  private fun hookWebView() {
    if (WebViewHook.ChromeClient == null || WebViewHook.ViewClient == null) return
    if (WebViewHook.WebView == null) {
      runCatching {
            WebViewHook.WebView = WebView::class.java
            WebView.setWebContentsDebuggingEnabled(true)
          }
          .onFailure { if (BuildConfig.DEBUG) Log.ex(it) }
    }
    initHooks(WebViewHook, ContextMenuHook)
  }

  override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    Resource.init(startupParam.modulePath)
  }

  private fun initHooks(vararg hook: BaseHook) {
    hook.forEach {
      if (it.isInit) return@forEach
      it.init()
      if (it.isInit) Log.d("${it.javaClass.simpleName} hooked")
    }
  }
}
