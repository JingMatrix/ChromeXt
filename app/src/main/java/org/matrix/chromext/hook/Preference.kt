package org.matrix.chromext.hook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Insets
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import java.lang.reflect.Modifier
import kotlin.math.roundToInt
import org.matrix.chromext.Chrome
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.proxy.PreferenceProxy
import org.matrix.chromext.utils.*

object PreferenceHook : BaseHook() {

  private fun getUrl(): String {
    return Chrome.getUrl()!!
  }

  override fun init() {

    val proxy = PreferenceProxy

    proxy.addPreferencesFromResource
        // public void addPreferencesFromResource(Int preferencesResId)
        .hookMethod {
          before {
            if (it.thisObject::class.java == proxy.developerSettings) {
              it.args[0] = R.xml.developer_preferences
            }
          }

          after {
            if (it.thisObject::class.java == proxy.developerSettings) {
              val refThis = it
              val preferences = mutableMapOf<String, Any>()
              arrayOf("eruda", "gesture_mod", "keep_storage", "bookmark", "reset", "exit").forEach {
                preferences[it] = proxy.findPreference.invoke(refThis.thisObject, it)!!
              }
              proxy.setClickListener(preferences.toMap())
            }
          }
        }

    findMethod(proxy.developerSettings, true) {
          Modifier.isStatic(modifiers) &&
              parameterTypes contentDeepEquals
                  arrayOf(Context::class.java, String::class.java, Bundle::class.java)
          // public static Fragment instantiate(Context context,
          // String fname, @Nullable Bundle args)
        }
        .hookAfter {
          if (it.result::class.java == proxy.developerSettings) {
            Resource.enrich(it.args[0] as Context)
          }
        }

    findMethod(proxy.chromeTabbedActivity) { name == "onNewIntent" || name == "onMAMNewIntent" }
        .hookBefore {
          val intent = it.args[0] as Intent
          if (intent.hasExtra("ChromeXt")) {
            intent.setAction(Intent.ACTION_VIEW)
            var url = intent.getStringExtra("ChromeXt") as String
            intent.setData(Uri.parse(url))
          }
        }

    findMethod(WindowInsets::class.java) { name == "getSystemGestureInsets" }
        .hookBefore {
          val ctx = Chrome.getContext()
          val sharedPref = ctx.getSharedPreferences("ChromeXt", Context.MODE_PRIVATE)
          if (sharedPref.getBoolean("gesture_mod", true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              it.result = Insets.of(0, 0, 0, 0)
            }
            toggleGestureConflict(true)
          } else {
            toggleGestureConflict(false)
          }
        }
  }

  private fun toggleGestureConflict(excludeSystemGesture: Boolean) {
    val activity = Chrome.getContext()
    if (activity is Activity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val decoView = activity.window.decorView
      if (excludeSystemGesture) {
        val width = decoView.width
        val height = decoView.height
        val excludeHeight: Int = (activity.resources.displayMetrics.density * 100).roundToInt()
        decoView.setSystemGestureExclusionRects(
            // public Rect (int left, int top, int right, int bottom)
            listOf(Rect(width / 2, height / 2 - excludeHeight, width, height / 2 + excludeHeight)))
      } else {
        decoView.setSystemGestureExclusionRects(listOf(Rect(0, 0, 0, 0)))
      }
    }
  }
}
