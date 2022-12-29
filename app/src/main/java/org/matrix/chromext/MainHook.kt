package org.matrix.chromext

import android.app.Activity
import android.content.Context
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.net.URI
import org.matrix.chromext.hook.GestureNavHook
import org.matrix.chromext.hook.IntentHook
import org.matrix.chromext.hook.MenuHook
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.invokeMethod

private const val PACKAGE_CHROME = "com.android.chrome"
private const val PACKAGE_BETA = "com.chrome.beta"
private const val PACKAGE_DEV = "com.chrome.dev"
// private const val PACKAGE_CANARY = "com.chrome.canary"

fun filterPackage(packageName: String): Boolean {
  return packageName == PACKAGE_BETA ||
      packageName == PACKAGE_DEV ||
      // packageName == PACKAGE_CANARY ||
      packageName == PACKAGE_CHROME
}

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {

  var MODULE_PATH: String? = null
  var RESOURCE_INJECTED = false

  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (filterPackage(lpparam.packageName)) {
      var split = true
      runCatching {
            findMethod(
                    lpparam.classLoader.loadClass(
                        "org.chromium.chrome.browser.base.SplitChromeApplication")) {
                      name == "attachBaseContext"
                    }
                .hookAfter {
                  val ctx = (it.args[0] as Context).createContextForSplit("chrome")
                  initHooks(ctx, split, lpparam.packageName)
                  ctx.getClassLoader()
                      .loadClass(TAB_MODEL_IMPL_SPLIT)
                      .getDeclaredConstructors()[0]
                      .hookAfter { TabModel.update(it.thisObject, split) }
                }
          }
          .onFailure {
            split = false
            lpparam.classLoader.loadClass(TAB_MODEL_IMPL).getDeclaredConstructors()[0].hookAfter {
              TabModel.update(it.thisObject, split)
            }
            findMethod(
                    lpparam.classLoader.loadClass(
                        "org.chromium.chrome.browser.ChromeTabbedActivity")) {
                      name == "onStart"
                    }
                .hookAfter {
                  val ctx = it.thisObject as Activity
                  GestureConflict.hookActivity(ctx)
                  initHooks(ctx, split, lpparam.packageName)
                }
          }
    }
  }

  fun injectModuleResource(ctx: Context) {
    if (RESOURCE_INJECTED) {
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val resLoader = ResourcesLoader()
      resLoader.addProvider(
          ResourcesProvider.loadFromApk(
              ParcelFileDescriptor.open(
                  File(URI.create("file://" + MODULE_PATH)), ParcelFileDescriptor.MODE_READ_ONLY)))

      ctx.getResources().addLoaders(resLoader)
    } else {
      ctx.assets.invokeMethod(MODULE_PATH) { name == "addAssetPath" }
    }
    RESOURCE_INJECTED = true
  }

  override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    MODULE_PATH = startupParam.modulePath
  }

  override fun handleInitPackageResources(
      resparam: XC_InitPackageResources.InitPackageResourcesParam
  ) {
    // Black magic to inject local resources
  }

  private fun initHooks(ctx: Context, split: Boolean, packageName: String) {
    injectModuleResource(ctx)
    arrayOf(UserScriptHook, GestureNavHook, MenuHook, IntentHook).forEach {
      runCatching {
            if (it.isInit) return@forEach
            it.init(ctx, split)
            it.isInit = true
            Log.i("Inited hook for ${packageName}: ${it.javaClass.simpleName}")
          }
          .onFailure { Log.ex(it) }
    }
  }
}
