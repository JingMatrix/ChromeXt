package org.matrix.chromext

import android.content.Context
import android.content.res.Resources
import android.content.res.XModuleResources
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.Log.logexIfThrow
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.matrix.chromext.hook.BaseHook
import org.matrix.chromext.hook.GestureNavHook
import org.matrix.chromext.hook.IntentHook
import org.matrix.chromext.hook.MenuHook
import org.matrix.chromext.hook.UserScriptHook

private const val PACKAGE_CHROME = "com.android.chrome"
private const val PACKAGE_BETA = "com.chrome.beta"
private const val PACKAGE_DEV = "com.chrome.dev"
// private const val PACKAGE_CANARY = "com.chrome.canary"
private const val TAG = "ChromeXt"

fun filterPackage(packageName: String): Boolean {
  return packageName == PACKAGE_BETA ||
      packageName == PACKAGE_DEV ||
      // packageName == PACKAGE_CANARY ||
      packageName == PACKAGE_CHROME
}

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {

  var MODULE_PATH: String? = null
  var DEV_ICON_RES_ID: Int? = null

  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (filterPackage(lpparam.packageName)) {
      // Init EzXHelper
      EzXHelperInit.initHandleLoadPackage(lpparam)
      EzXHelperInit.setLogTag(TAG)
      // EzXHelperInit.setToastTag(TAG)
      // Init hooks
      findMethod("org.chromium.chrome.browser.base.SplitChromeApplication") {
            name == "attachBaseContext"
          }
          .hookAfter {
            val ctx = (it.args[0] as Context).createContextForSplit("chrome")
            MenuHook.devTool_res_id = DEV_ICON_RES_ID!!
            initHooks(
                ctx, lpparam.packageName, UserScriptHook, GestureNavHook, IntentHook, MenuHook)
          }
    }
  }

  // Optional
  override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    EzXHelperInit.initZygote(startupParam)
    MODULE_PATH = startupParam.modulePath
  }

  override fun handleInitPackageResources(
      resparam: XC_InitPackageResources.InitPackageResourcesParam
  ) {
    if (filterPackage(resparam.packageName)) {
      val res: Resources = XModuleResources.createInstance(MODULE_PATH, resparam.res)
      DEV_ICON_RES_ID = resparam.res.addResource(res, R.drawable.ic_devtools)
    }
  }

  private fun initHooks(ctx: Context, packageName: String, vararg hook: BaseHook) {
    hook.forEach {
      runCatching {
            if (it.isInit) return@forEach
            it.init(ctx)
            it.isInit = true
            Log.i("Inited hook for ${packageName}: ${it.javaClass.simpleName}")
          }
          .logexIfThrow("Failed init hook for ${packageName}: ${it.javaClass.simpleName}")
    }
  }
}
