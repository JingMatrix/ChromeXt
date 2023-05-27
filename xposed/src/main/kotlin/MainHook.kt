package org.matrix.chromext

import android.app.AndroidAppHelper
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.matrix.chromext.hook.BaseHook
import org.matrix.chromext.hook.GestureNavHook
import org.matrix.chromext.hook.IntentHook
import org.matrix.chromext.hook.MenuHook
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ResourceMerge
import org.matrix.chromext.utils.hookAfter

const val TAG = "ChromeXt"

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    Log.d(lpparam.processName + " started")
    if (lpparam.packageName == "org.matrix.chromext") {
      return
    }
    runCatching {
          lpparam.classLoader
              .loadClass("org.chromium.ui.base.WindowAndroid")
              .getDeclaredConstructors()[1]
              .hookAfter {
                Chrome.init(it.args[0] as Context)
                initHooks(UserScriptHook, GestureNavHook, MenuHook, IntentHook)
              }
        }
        .onFailure {
          val ctx = AndroidAppHelper.currentApplication()
          if (ctx != null) {
            Chrome.init(ctx)
          } else {
            return
          }

          WebViewClient::class.java.getDeclaredConstructors()[0].hookAfter {
            if (it.thisObject::class != WebViewClient::class) {
              WebViewHook.ViewClient = it.thisObject::class.java
              if (WebViewHook.ChromeClient != null) {
                initHooks(WebViewHook)
              }
            }
          }

          WebChromeClient::class.java.getDeclaredConstructors()[0].hookAfter {
            if (it.thisObject::class != WebViewClient::class) {
              WebViewHook.ChromeClient = it.thisObject::class.java
              if (WebViewHook.ViewClient != null) {
                initHooks(WebViewHook)
              }
            }
          }
        }
  }

  override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    ResourceMerge.init(startupParam.modulePath)
  }

  private fun initHooks(vararg hook: BaseHook) {
    hook.forEach {
      runCatching {
            if (it.isInit) return@forEach
            it.init()
            it.isInit = true
            Log.i("Inited hook: ${it.javaClass.simpleName}")
          }
          .onFailure { Log.ex(it) }
    }
  }
}
