package org.matrix.chromext.hook

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import de.robv.android.xposed.XC_MethodHook.Unhook
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore
import org.matrix.chromext.utils.isChromeXtFrontEnd

object ContextMenuHook : BaseHook() {

  override fun init() {

    var contextMenuHook: Unhook? = null
    findMethod(View::class.java) { name == "startActionMode" && parameterTypes.size == 2 }
        // public ActionMode startActionMode (ActionMode.Callback callback,
        //         int type)
        .hookBefore {
          if (it.args[1] as Int == ActionMode.TYPE_FLOATING) {
            val isChromeXt = isChromeXtFrontEnd(Chrome.getUrl())
            contextMenuHook?.unhook()
            contextMenuHook =
                findMethod(it.args[0]::class.java) { name == "onCreateActionMode" }
                    // public abstract boolean onCreateActionMode (ActionMode mode, Menu menu)
                    .hookAfter {
                      val mode = it.args[0] as ActionMode
                      val menu = it.args[1] as Menu
                      val erudaMenu = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Eruda console")
                      if (isChromeXt) {
                        erudaMenu.setTitle("Developer tools")
                      }
                      erudaMenu.setOnMenuItemClickListener(
                          MenuItem.OnMenuItemClickListener {
                            if (WebViewHook.isInit) {
                              val webSettings = (Chrome.getTab() as WebView).settings
                              val javaScriptEnabled = webSettings.javaScriptEnabled
                              if (!javaScriptEnabled) {
                                webSettings.javaScriptEnabled = true
                                Chrome.evaluateJavascript(listOf(Local.initChromeXt))
                              }
                            }
                            if (isChromeXt) {
                              Listener.on("inspectPages")
                            } else {
                              Chrome.evaluateJavascript(listOf(Local.openEruda))
                            }
                            mode.finish()
                            true
                          })
                    }
          }
        }
  }
}
