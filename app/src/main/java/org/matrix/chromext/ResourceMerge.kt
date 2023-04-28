package org.matrix.chromext

import android.content.Context
import org.matrix.chromext.utils.invokeMethod

object ResourceMerge {
  private var module_path: String? = null

  fun init(packagePath: String) {
    module_path = packagePath
  }

  fun enrich(ctx: Context) {
    ctx.assets.invokeMethod(module_path!!) { name == "addAssetPath" }
  }
}
