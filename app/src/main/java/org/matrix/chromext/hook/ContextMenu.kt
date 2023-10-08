package org.matrix.chromext.hook

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.lang.Class
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore
import org.matrix.chromext.utils.isChromeXtFrontEnd
import org.matrix.chromext.utils.isUserScript
import org.matrix.chromext.utils.shouldBypassSandbox

object ContextMenuHook : BaseHook() {

  override fun init() {

    val erudaMenuId = 31415926
    val clickListnerFactory = { mode: ActionMode, url: String ->
      MenuItem.OnMenuItemClickListener {
        if (WebViewHook.isInit) {
          val webSettings = (Chrome.getTab() as WebView).settings
          val javaScriptEnabled = webSettings.javaScriptEnabled
          if (!javaScriptEnabled) {
            webSettings.javaScriptEnabled = true
            Chrome.evaluateJavascript(listOf(Local.initChromeXt))
          }
        }
        if (isChromeXtFrontEnd(url)) {
          Listener.on("inspectPages")
        } else if (isUserScript(url)) {
          val sandBoxed = shouldBypassSandbox(url)
          Chrome.evaluateJavascript(listOf("Symbol.installScript(true);"), null, sandBoxed)
        } else {
          Chrome.evaluateJavascript(listOf(Local.openEruda))
        }
        mode.finish()
        true
      }
    }

    var actionModeFinder: Unhook? = null
    fun hookActionMode(cls: Class<*>) {
      actionModeFinder?.unhook()
      findMethod(cls) { name == "onCreateActionMode" }
          // public abstract boolean onCreateActionMode (ActionMode mode, Menu menu)
          .hookAfter {
            val url = Chrome.getUrl()
            val mode = it.args[0] as ActionMode
            val menu = it.args[1] as Menu
            if (menu.findItem(erudaMenuId) != null) return@hookAfter
            Resource.enrich(Chrome.getContext())
            val titleId =
                if (isChromeXtFrontEnd(url)) R.string.main_menu_developer_tools
                else if (isUserScript(url)) R.string.main_menu_install_script
                else R.string.main_menu_eruda_console
            val erudaMenu = menu.add(titleId)
            val mId = erudaMenu::class.java.getDeclaredField("mId")
            mId.setAccessible(true)
            mId.set(erudaMenu, erudaMenuId)
            erudaMenu.setOnMenuItemClickListener(clickListnerFactory(mode, url!!))
          }
    }

    if (Chrome.isSamsung) {
      hookActionMode(Chrome.load("com.sec.terrace.content.browser.TinActionModeCallback"))
    } else {
      actionModeFinder =
          findMethod(View::class.java) { name == "startActionMode" && parameterTypes.size == 2 }
              // public ActionMode startActionMode (ActionMode.Callback callback, int type)
              .hookBefore {
                if (it.args[1] as Int != ActionMode.TYPE_FLOATING) return@hookBefore
                hookActionMode(it.args[0]::class.java)
              }
    }
  }
}
