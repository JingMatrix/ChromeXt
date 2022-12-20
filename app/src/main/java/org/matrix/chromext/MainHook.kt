package org.matrix.chromext

import android.content.Context
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.Log.logexIfThrow
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.matrix.chromext.hook.BaseHook
import org.matrix.chromext.hook.GestureNavHook
import org.matrix.chromext.hook.IntentHook
import org.matrix.chromext.hook.UserScriptHook

private const val PACKAGE_CHROME = "com.android.chrome"
private const val PACKAGE_BETA = "com.chrome.beta"
private const val PACKAGE_DEV = "com.chrome.dev"
// private const val PACKAGE_CANARY = "com.chrome.canary"
private const val TAG = "ChromeXt"

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit /* Optional */ {
  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (lpparam.packageName == PACKAGE_BETA ||
        lpparam.packageName == PACKAGE_DEV ||
        // lpparam.packageName == PACKAGE_CANARY ||
        lpparam.packageName == PACKAGE_CHROME) {
      // Init EzXHelper
      EzXHelperInit.initHandleLoadPackage(lpparam)
      EzXHelperInit.setLogTag(TAG)
      EzXHelperInit.setToastTag(TAG)
      // Init hooks
      findMethod("org.chromium.chrome.browser.base.SplitChromeApplication") {
            name == "attachBaseContext"
          }
          .hookAfter {
            val ctx = (it.args[0] as Context).createContextForSplit("chrome")
            initHooks(ctx, lpparam.packageName, UserScriptHook, GestureNavHook, IntentHook)
          }
    }
  }

  // Optional
  override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    EzXHelperInit.initZygote(startupParam)
  }

  private fun initHooks(ctx: Context, pacakge: String, vararg hook: BaseHook) {
    hook.forEach {
      runCatching {
            if (it.isInit) return@forEach
            it.init(ctx)
            it.isInit = true
            Log.i("Inited hook for ${pacakge}: ${it.javaClass.simpleName}")
          }
          .logexIfThrow("Failed init hook for ${pacakge}: ${it.javaClass.simpleName}")
    }
  }
}
