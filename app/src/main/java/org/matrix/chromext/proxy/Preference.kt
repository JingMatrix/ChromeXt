package org.matrix.chromext.proxy

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Pair
import android.view.View
import android.view.View.OnClickListener
import java.io.File
import java.io.FileReader
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findField
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookBefore

object PreferenceProxy {
  private var clickHooked: Boolean = false

  val developerSettings =
      Chrome.load("org.chromium.chrome.browser.tracing.settings.DeveloperSettings")
  val chromeTabbedActivity = UserScriptProxy.chromeTabbedActivity
  val emptyTabObserver =
      Chrome.load("org.chromium.chrome.browser.login.ChromeHttpAuthHandler").superclass as Class<*>

  private val preference = Chrome.load("androidx.preference.Preference")
  private val mClickListener =
      findField(preference) {
        type == OnClickListener::class.java || type.interfaces.contains(OnClickListener::class.java)
      }
  private val setSummary =
      findMethod(preference) {
        parameterTypes contentDeepEquals arrayOf(CharSequence::class.java) &&
            returnType == Void.TYPE
      }
  private val twoStatePreference =
      Chrome.load("org.chromium.components.browser_ui.settings.ChromeSwitchPreference").superclass
          as Class<*>
  private val setChecked =
      findMethod(twoStatePreference, true) {
        parameterTypes contentDeepEquals arrayOf(Boolean::class.java)
      }

  private val preferenceFragmentCompat =
      if (Chrome.isBrave) developerSettings.superclass.superclass
      else developerSettings.superclass as Class<*>
  val findPreference =
      findMethod(preferenceFragmentCompat) {
        parameterTypes contentDeepEquals arrayOf(CharSequence::class.java) &&
            returnType == preference
      }
  val addPreferencesFromResource =
      preferenceFragmentCompat.declaredMethods
          .filter {
            it.parameterTypes contentDeepEquals arrayOf(Int::class.java) &&
                it.returnType == Void.TYPE
            // There exist other methods with the same signatures
          }
          .first()

  fun setClickListener(preferences: Map<String, Any>) {
    val ctx = Chrome.getContext()
    val sharedPref = ctx.getSharedPreferences("ChromeXt", Context.MODE_PRIVATE)

    var summary = "Click to install eruda, size around 0.5 MiB"
    if (Local.eruda_version != null) {
      summary = "Current version: v" + Local.eruda_version
    }
    setSummary.invoke(preferences["eruda"], summary)

    setChecked.invoke(
        preferences["gesture_mod"],
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            sharedPref.getBoolean("gesture_mod", true))
    setChecked.invoke(preferences["keep_storage"], ScriptDbManager.keepStorage)

    var reset_confirming = 1
    val listeners =
        mapOf(
            "bookmark" to
                fun(_: Any) {
                  val bookmark = File(ctx.filesDir, "../app_chrome/Default/Bookmarks")
                  if (bookmark.exists()) {
                    var html =
                        "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<!-- This is an automatically generated file. It will be read and overwritten. DO NOT EDIT! -->\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n"
                    runCatching {
                          val data =
                              JSONObject(FileReader(bookmark).use { it.readText() })
                                  .getJSONObject("roots")

                          val DIV = 10000000
                          fun loopChildren(folder: JSONObject, indent: String = "\t") {
                            if (folder.optString("type") == "folder" && folder.has("children")) {
                              val children = folder.getJSONArray("children")
                              html +=
                                  indent +
                                      "<DT><H3 ADD_DATE=\"${folder.getLong("date_added") / DIV }\" LAST_MODIFIED=\"${folder.getLong("date_modified") / DIV}\" PERSONAL_TOOLBAR_FOLDER=\"true\">${folder.getString("name")}</H3>\n"
                              html += indent + "<DL><p>\n"
                              for (i in 0 until children.length()) {
                                val item = children.getJSONObject(i)
                                if (item.getString("type") == "url") {
                                  html +=
                                      indent +
                                          "\t<DT><A HREF=\"${item.getString("url")}\" ADD_DATE=\"${item.getLong("date_added") / DIV }\">${item.getString("name")}</A>\n"
                                } else if (item.getString("type") == "folder") {
                                  loopChildren(item, indent + "\t")
                                }
                              }
                              html += indent + "</DL><p>\n"
                            }
                          }

                          val bookmarks = data.names()!!
                          for (i in 0 until bookmarks.length()) {
                            loopChildren(data.getJSONObject(bookmarks.getString(i)))
                          }

                          html += "</DL><p>"
                          File(
                                  Environment.getExternalStoragePublicDirectory(
                                      Environment.DIRECTORY_DOWNLOADS),
                                  "Bookmarks.html")
                              .writeText(html)
                          Log.toast(
                              ctx,
                              "Bookmarks exported to " +
                                  Environment.DIRECTORY_DOWNLOADS +
                                  "/Bookmarks.html")
                        }
                        .onFailure { Log.ex(it) }
                  } else {
                    Log.toast(ctx, "Bookmarks data not found")
                  }
                },
            "eruda" to
                fun(_: Any) {
                  Listener.on("updateEruda")
                },
            "exit" to
                fun(_: Any) {
                  if (Chrome.isDev || Chrome.isVivaldi) {
                    Log.toast(ctx, "Feature unavailable")
                    return
                  }
                  with(
                      ctx.getSharedPreferences(
                              Chrome.getContext().packageName + "_preferences",
                              Context.MODE_PRIVATE)
                          .edit()) {
                        putBoolean("developer", false)
                        apply()
                      }
                  Log.toast(ctx, "Please restart Chrome to apply the changes")
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
                    Log.toast(ctx, "Feature unavailable")
                  }
                },
            "keep_storage" to
                fun(obj: Any) {
                  val enabled = sharedPref.getBoolean("keep_storage", true)
                  setChecked.invoke(obj, !enabled)
                  ScriptDbManager.keepStorage = !enabled
                  with(sharedPref.edit()) {
                    putBoolean("keep_storage", !enabled)
                    apply()
                  }
                },
            "reset" to
                fun(_: Any) {
                  if (reset_confirming < 3) {
                    Log.toast(
                        ctx,
                        "Clik ${3 - reset_confirming} more times if you confirm to reset ChromeXt")
                    reset_confirming += 1
                    return
                  }
                  arrayOf("ChromeXt", "CosmeticFilter", "UserAgent", "CSPRule").forEach {
                    with(ctx.getSharedPreferences(it, Context.MODE_PRIVATE).edit()) {
                      clear()
                      apply()
                    }
                  }
                  val file = File(ctx.filesDir, "Eruda.js")
                  if (file.exists()) {
                    file.delete()
                    Local.eruda_version = null
                  }
                  ctx.deleteDatabase("userscript")
                  Log.toast(ctx, "ChromeXt data are reset")
                })

    if (mClickListener.type == OnClickListener::class.java) {
      mClickListener.setAccessible(true)
      preferences.forEach { (name, pref) ->
        mClickListener.set(
            pref,
            object : OnClickListener {
              override fun onClick(v: View) {
                listeners[name]?.invoke(pref)
              }
            })
      }
      mClickListener.setAccessible(false)
    } else if (!clickHooked) {
      clickHooked = true
      val mPreference = mClickListener.type.declaredFields.first()
      val prefTitles =
          preferences.entries.map { (name, pref) ->
            Pair(name, pref.toString().split(" ").take(3).joinToString(" "))
          }
      findMethod(mClickListener.type) { name == "onClick" }
          .hookBefore {
            prefTitles.forEach(
                fun(p: Pair<String, String>) {
                  val pref = mPreference.get(it.thisObject)!!
                  if (pref.toString().startsWith(p.second)) {
                    listeners[p.first]?.invoke(pref)
                    it.result = true
                  }
                })
          }
    }
  }
}
