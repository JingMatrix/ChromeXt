package org.matrix.chromext

import android.content.Context
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
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter

val supportedPackages =
    arrayOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.bromite.bromite",
        "com.brave.browser",
        "com.brave.browser_beta")

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (supportedPackages.contains(lpparam.packageName)) {
      runCatching {
            Chrome.split = true
            var entryPoint = "org.chromium.chrome.browser.base.SplitChromeApplication"
            runCatching { lpparam.classLoader.loadClass(entryPoint) }
                .onFailure {
                  entryPoint = "org.chromium.chrome.browser.base.SplitMonochromeApplication"
                }
            findMethod(lpparam.classLoader.loadClass(entryPoint)) { name == "attachBaseContext" }
                .hookAfter {
                  val ctx = (it.args[0] as Context).createContextForSplit("chrome")
                  Chrome.init(ctx)
                  initHooks(UserScriptHook, GestureNavHook, MenuHook, IntentHook)
                }
          }
          .onFailure {
            Chrome.split = false
            findMethod(
                    lpparam.classLoader.loadClass(
                        "org.chromium.chrome.browser.ChromeTabbedActivity"),
                    true) {
                      name == "onCreate"
                    }
                .hookAfter {
                  Chrome.init(it.thisObject as Context)
                  initHooks(UserScriptHook, GestureNavHook, MenuHook, IntentHook)
                }
          }
    }
  }

  override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    ResourceMerge.init(startupParam.modulePath)
  }

  override fun handleInitPackageResources(
      resparam: XC_InitPackageResources.InitPackageResourcesParam
  ) {}

  private fun initHooks(vararg hook: BaseHook) {
    ResourceMerge.enrich(Chrome.getContext())
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
