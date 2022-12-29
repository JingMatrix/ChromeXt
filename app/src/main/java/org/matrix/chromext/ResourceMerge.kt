package org.matrix.chromext

import android.content.Context
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import java.net.URI
import org.matrix.chromext.utils.invokeMethod

object ResourceMerge {
  var module_path: String? = null

  fun init(packagePath: String) {
    module_path = packagePath
  }

  fun enrich(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val resLoader = ResourcesLoader()
      resLoader.addProvider(
          ResourcesProvider.loadFromApk(
              ParcelFileDescriptor.open(
                  File(URI.create("file://" + module_path!!)),
                  ParcelFileDescriptor.MODE_READ_ONLY)))

      ctx.getResources().addLoaders(resLoader)
    } else {
      ctx.assets.invokeMethod(module_path!!) { name == "addAssetPath" }
    }
  }
}
