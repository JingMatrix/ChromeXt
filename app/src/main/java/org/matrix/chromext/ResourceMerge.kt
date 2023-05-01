package org.matrix.chromext

// import org.matrix.chromext.utils.Log
import android.content.Context
import org.matrix.chromext.utils.invokeMethod

object ResourceMerge {
  private var module_path: String? = null

  fun init(packagePath: String) {
    module_path = packagePath
  }

  fun enrich(ctx: Context) {
    // Log.d("Enriching context for " + ctx.toString())
    ctx.assets.invokeMethod(module_path!!) { name == "addAssetPath" }
  }
}
