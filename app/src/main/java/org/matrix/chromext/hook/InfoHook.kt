package org.matrix.chromext.hook

import com.github.kyuubiran.ezxhelper.utils.*

object InfoHook : BaseHook() {
  override fun init() {
    findAllMethods("J.N") { paramCount == 3 }
        .hookBefore {
          if (it.args[0]::class.simpleName == "Long" && it.args[1]::class.simpleName == "String") {
            Log.d(
                "${Thread.currentThread().getStackTrace()[9].getClassName()} > ${Thread.currentThread().getStackTrace()[8].getClassName()} > ${Thread.currentThread().getStackTrace()[7].getClassName()} call with ${it.args[0]} ${it.args[1]} ${it.args[2]}")
            if (it.args[2]::class.simpleName == "JavaScriptCallback") {
              Log.d("Call ${it.method.getName()} with ${it.args[2]::class.simpleName}")
            }
          }
        }

    // One can use signature to identify JNI, for example
    // Mi3V1mlO(JLjava/lang/Object;ZIZLjava/lang/Object;)I must be
    // int downloadImage(long nativeWebContentsAndroid, GURL url, boolean isFavicon, int
    // maxBitmapSize, boolean bypassCache, ImageDownloadCallback callback);

    // We want to test out which one is
    //  void evaluateJavaScriptForTests(long nativeWebContentsAndroid, String script,
    // JavaScriptCallback callback);
    // Currently, there are 11 options to test with: (JLjava/lang/String;Ljava/lang/Object;)V

    findMethod("J.N") { name == "Mi3V1mlO" }
        .hookBefore {
          Log.d("Call downloadImage with url: ${it.args[1].invokeMethod() {name == SHOW_URL}}")
        }

    findMethod("java.lang.Runtime") { name == "loadLibrary0" }
        .hookAfter {
          if ("${it.args[1]}".startsWith("class org.chromium.base.library_loader", false)) {
            Log.i("${it.args[0]} load ${it.args[1]}")
          }
        }

    findAllMethods("org.chromium.chrome.browser.base.SplitChromeApplication") { true }
        .forEach {
          var info: String = it.getName() + ": "
          it.getParameterTypes().forEach { info = info + it.getName() + " " }
          Log.i(info)
        }
  }
}
