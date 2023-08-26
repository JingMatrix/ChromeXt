package org.matrix.chromext.hook

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import de.robv.android.xposed.XC_MethodHook.Unhook
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.R
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore
import org.matrix.chromext.utils.isChromeXtFrontEnd

object ContextMenuHook : BaseHook() {

  override fun init() {

    val erudaMenuId = 31415926
    val clickListnerFactory = { mode: ActionMode, showConsole: Boolean ->
      MenuItem.OnMenuItemClickListener {
        if (WebViewHook.isInit) {
          val webSettings = (Chrome.getTab() as WebView).settings
          val javaScriptEnabled = webSettings.javaScriptEnabled
          if (!javaScriptEnabled) {
            webSettings.javaScriptEnabled = true
            Chrome.evaluateJavascript(listOf(Local.initChromeXt))
          }
        }
        if (showConsole) {
          Chrome.evaluateJavascript(listOf(Local.openEruda))
        } else {
          Listener.on("inspectPages")
        }
        mode.finish()
        true
      }
    }

    var actionModeFinder: Unhook? = null
    actionModeFinder =
        findMethod(View::class.java) { name == "startActionMode" && parameterTypes.size == 2 }
            // public ActionMode startActionMode (ActionMode.Callback callback, int type)
            .hookBefore {
              if (it.args[1] as Int != ActionMode.TYPE_FLOATING) return@hookBefore
              actionModeFinder?.unhook()
              findMethod(it.args[0]::class.java) { name == "onCreateActionMode" }
                  // public abstract boolean onCreateActionMode (ActionMode mode, Menu menu)
                  .hookAfter {
                    val showConsole = !isChromeXtFrontEnd(Chrome.getUrl())
                    val mode = it.args[0] as ActionMode
                    val menu = it.args[1] as Menu
                    if (menu.findItem(erudaMenuId) != null) return@hookAfter
                    val titleId =
                        if (showConsole) R.string.main_menu_eruda_console
                        else R.string.main_menu_developer_tools
                    val erudaMenu = menu.add(titleId)
                    val mId = erudaMenu::class.java.getDeclaredField("mId")
                    mId.setAccessible(true)
                    mId.set(erudaMenu, erudaMenuId)
                    erudaMenu.setOnMenuItemClickListener(clickListnerFactory(mode, showConsole))
                  }
            }
  }
}
