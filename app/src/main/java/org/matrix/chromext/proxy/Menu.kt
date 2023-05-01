package org.matrix.chromext.proxy

import android.content.Context
import android.os.Build
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.lang.ref.WeakReference
import kotlin.text.Regex
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookBefore

const val ERUD_URL = "https://cdn.jsdelivr.net/npm/eruda@latest/eruda.min.js"

object MenuProxy {
  private var clickHooked: Boolean = false

  val isDeveloper =
      Chrome.getContext()
          .getSharedPreferences(
              Chrome.getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)!!
          .getBoolean("developer", false) || Chrome.isDev

  val developerSettings =
      Chrome.load("org.chromium.chrome.browser.tracing.settings.DeveloperSettings")
  val chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
  val windowAndroid = Chrome.load("org.chromium.ui.base.WindowAndroid")
  val propertyModel = Chrome.load("org.chromium.ui.modelutil.PropertyModel")

  private val pageInfoController =
      Chrome.load("org.chromium.components.page_info.PageInfoController")
  val pageInfoRowView = Chrome.load("org.chromium.components.page_info.PageInfoRowView")
  val mIcon = pageInfoRowView.getDeclaredFields()[0]
  val mTitle = pageInfoRowView.getDeclaredFields()[1]
  val mSubtitle = pageInfoRowView.getDeclaredFields()[2]
  val pageInfoView =
      if (Chrome.isEdge) {
        Chrome.load("org.chromium.components.page_info.PageInfoView")
      } else {
        pageInfoController
            .getDeclaredFields()
            .find { it.type.getSuperclass() == FrameLayout::class.java }!!
            .type
      }
  val mRowWrapper = pageInfoView.getDeclaredFields().find { it.type == LinearLayout::class.java }!!
  val pageInfoControllerRef =
      // A particular WebContentsObserver designed for PageInfoController
      pageInfoController
          .getDeclaredFields()
          .find {
            it.type.getDeclaredFields().size == 1 &&
                (it.type.getDeclaredFields()[0].type == pageInfoController ||
                    it.type.getDeclaredFields()[0].type == WeakReference::class.java)
          }!!
          .type

  val emptyTabObserver =
      Chrome.load("org.chromium.chrome.browser.login.ChromeHttpAuthHandler").getSuperclass()
          as Class<*>

  private val preference = Chrome.load("androidx.preference.Preference")
  private val mClickListener =
      preference.getDeclaredFields().find {
        it.getType() == OnClickListener::class.java ||
            it.getType().getInterfaces().contains(OnClickListener::class.java)
      }!!
  private val setSummary =
      findMethod(preference) {
        getParameterCount() == 1 &&
            getParameterTypes().first() == CharSequence::class.java &&
            getReturnType() == Void.TYPE
      }
  private val twoStatePreference =
      Chrome.load("org.chromium.components.browser_ui.settings.ChromeSwitchPreference")
          .getSuperclass() as Class<*>
  private val setChecked =
      findMethod(twoStatePreference, true) {
        getParameterCount() == 1 && getParameterTypes().first() == Boolean::class.java
      }

  val preferenceFragmentCompat = developerSettings.getSuperclass() as Class<*>
  val findPreference =
      findMethod(preferenceFragmentCompat, true) {
        getParameterCount() == 1 &&
            getParameterTypes().first() == CharSequence::class.java &&
            getReturnType() == preference
      }
  val addPreferencesFromResource =
      findMethod(preferenceFragmentCompat, true) {
        getParameterCount() == 1 &&
            getParameterTypes().first() == Int::class.java &&
            getReturnType() == Void.TYPE
      }

  fun setClickListener(preferences: Map<String, Any>) {
    val ctx = Chrome.getContext()
    val sharedPref =
        ctx.getSharedPreferences(
            Chrome.getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)

    var summary = "Click to install Eruda, size around 0.5 MiB"
    val version = sharedPref.getString("eruda_version", "unknown")
    if (version != "unknown") {
      summary = "Current version: v" + version
    }
    setSummary.invoke(preferences["eruda"], summary)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      setChecked.invoke(preferences["gesture_mod"], sharedPref.getBoolean("gesture_mod", true))
    } else {
      setChecked.invoke(preferences["gesture_mod"], false)
    }

    val listeners =
        mapOf(
            "exit" to
                fun(_: Any) {
                  if (Chrome.isDev) {
                    Log.toast(ctx, "This function is not available for your Chrome build")
                    return
                  }
                  with(sharedPref.edit()) {
                    putBoolean("developer", false)
                    apply()
                  }
                  Log.toast(ctx, "Please restart Chrome to apply the changes")
                },
            "eruda" to
                fun(obj: Any) {
                  Download.start(ERUD_URL, "Download/Eruda.js", true) {
                    val old_version = sharedPref.getString("eruda_version", "unknown")
                    var new_version = old_version
                    val verisonReg = Regex("""/npm/eruda@(?<version>[\d\.]+)/eruda""")
                    val vMatchGroup =
                        verisonReg.find(it.take(150))?.groups as? MatchNamedGroupCollection
                    if (vMatchGroup != null) {
                      new_version = vMatchGroup.get("version")?.value as String
                    }
                    if (old_version != new_version) {
                      with(sharedPref.edit()) {
                        putString("eruda_version", new_version)
                        apply()
                      }
                      Log.toast(ctx, "Updated to eruda v" + new_version)
                      setSummary.invoke(obj, "Current version: v" + new_version)
                    } else {
                      Log.toast(ctx, "Eruda is already the lastest")
                    }
                  }
                },
            "gesture_mod" to
                fun(obj: Any) {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val enabled = sharedPref.getBoolean("gesture_mod", true)
                    setChecked.invoke(obj, !enabled)
                    with(sharedPref.edit()) {
                      putBoolean("gesture_mod", !enabled)
                      apply()
                    }
                  } else {
                    setChecked.invoke(obj, false)
                    Log.toast(ctx, "Feature unavaible for your Chrome or Android versions")
                  }
                })

    mClickListener.setAccessible(true)
    if (mClickListener.getType() == OnClickListener::class.java) {
      preferences.forEach { (name, pref) ->
        mClickListener.set(
            pref,
            object : OnClickListener {
              override fun onClick(v: View) {
                listeners[name]?.invoke(pref)
              }
            })
      }
    } else if (!clickHooked) {
      clickHooked = true
      val mPreference = mClickListener.getType().getDeclaredFields().first()
      val prefTitles =
          preferences.entries.map { (name, pref) ->
            Pair(name, pref.toString().split(" ").take(3).joinToString(" "))
          }
      findMethod(mClickListener.getType()) { name == "onClick" }
          .hookBefore {
            prefTitles.forEach(
                fun(p: Pair<String, String>) {
                  if (mPreference.get(it.thisObject)!!.toString().startsWith(p.second)) {
                    listeners[p.first]?.invoke(mPreference.get(it.thisObject)!!)
                    it.result = true
                  }
                })
          }
    }
    mClickListener.setAccessible(false)
  }
}
