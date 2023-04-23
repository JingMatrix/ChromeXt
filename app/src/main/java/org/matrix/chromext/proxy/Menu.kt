package org.matrix.chromext.proxy

import android.content.Context
import android.os.Build
import android.view.View
import android.view.View.OnClickListener
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.text.Regex
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookBefore

const val ERUD_URL = "https://cdn.jsdelivr.net/npm/eruda@latest/eruda.min.js"

class MenuProxy() {

  val chromeTabbedActivity: Class<*>
  val customTabActivity: Class<*>
  val developerSettings: Class<*>
  val preferenceFragmentCompat: Class<*>
  val emptyTabObserver: Class<*>
  val twoStatePreference: Class<*>
  val gURL: Class<*>
  val windowAndroid: Class<*>

  private val preference: Class<*>
  private val mClickListener: Field

  val setChecked: Method
  val setSummary: Method

  val findPreference: Method

  val addPreferencesFromResource: Method

  var isDeveloper: Boolean = false
  var clickHooked: Boolean = false

  init {
    val sharedPref =
        Chrome.getContext()
            .getSharedPreferences(
                Chrome.getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)
    isDeveloper = sharedPref!!.getBoolean("developer", false) || Chrome.isDev

    preference = Chrome.load("androidx.preference.Preference")
    developerSettings =
        Chrome.load("org.chromium.chrome.browser.tracing.settings.DeveloperSettings")
    preferenceFragmentCompat = developerSettings.getSuperclass() as Class<*>
    chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
    windowAndroid = Chrome.load("org.chromium.ui.base.WindowAndroid")
    customTabActivity = Chrome.load("org.chromium.chrome.browser.customtabs.CustomTabActivity")
    twoStatePreference =
        Chrome.load("org.chromium.components.browser_ui.settings.ChromeSwitchPreference")
            .getSuperclass() as Class<*>
    gURL = Chrome.load("org.chromium.url.GURL")
    emptyTabObserver =
        Chrome.load("org.chromium.chrome.browser.login.ChromeHttpAuthHandler").getSuperclass()
            as Class<*>
    mClickListener =
        preference.getDeclaredFields().find {
          it.getType() == OnClickListener::class.java ||
              it.getType().getInterfaces().contains(OnClickListener::class.java)
        }!!
    mClickListener.setAccessible(true)

    setSummary =
        findMethod(preference) {
          getParameterCount() == 1 &&
              getParameterTypes().first() == CharSequence::class.java &&
              getReturnType() == Void.TYPE
        }
    setChecked =
        findMethod(twoStatePreference, true) {
          getParameterCount() == 1 && getParameterTypes().first() == Boolean::class.java
        }
    findPreference =
        findMethod(preferenceFragmentCompat, true) {
          getParameterCount() == 1 &&
              getParameterTypes().first() == CharSequence::class.java &&
              getReturnType() == preference
        }
    addPreferencesFromResource =
        findMethod(preferenceFragmentCompat, true) {
          getParameterCount() == 1 &&
              getParameterTypes().first() == Int::class.java &&
              getReturnType() == Void.TYPE
        }
  }

  fun setClickListener(preferences: Map<String, Any>) {
    val ctx = Chrome.getContext()
    val sharedPref =
        ctx.getSharedPreferences(
            Chrome.getContext().getPackageName() + "_preferences", Context.MODE_PRIVATE)

    var summary = "Click to install Eruda, size around 0.5 MiB"
    if (!Chrome.isEdge) {
      val version = sharedPref.getString("eruda_version", "unknown")
      if (version != "unknown") {
        summary = "Current version: v" + version
      }
    } else {
      summary = "Eruda and DevTools are not available for Egde yet"
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
                  if (Chrome.isEdge) {
                    Log.toast(ctx, "We may support Edge soon!")
                    return
                  }
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
  }
}
