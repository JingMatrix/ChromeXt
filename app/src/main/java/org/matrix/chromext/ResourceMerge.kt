package org.matrix.chromext

import android.content.Context
import org.matrix.chromext.utils.invokeMethod

object ResourceMerge {
  private var module_path: String? = null
  private var isChromeEnriched = false

  fun init(packagePath: String) {
    module_path = packagePath
  }

  fun enrich(ctx: Context) {
    if (ctx == Chrome.getContext()) {
      if (isChromeEnriched) {
        return
      } else {
        isChromeEnriched = true
      }
    }

    ctx.assets.invokeMethod(module_path!!) { name == "addAssetPath" }
  }
}
