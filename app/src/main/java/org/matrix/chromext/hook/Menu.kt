package org.matrix.chromext.hook

import android.content.Context
import android.view.Menu
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import org.matrix.chromext.proxy.MenuProxy

object MenuHook : BaseHook() {

  var devTool_icon_res_id: Int? = null
  var devTool_text_res_id: Int? = null
  val devTool_id = 31415926

  override fun init(ctx: Context) {

    val proxy = MenuProxy(ctx)

    if (!proxy.isDeveloper) {
      return
    }

    findMethod(proxy.chromeTabbedActivity!!) { name == proxy.MENU_KEYBOARD_ACTION }
        // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
        .hookAfter {
          val id = it.args[0] as Int
          if (id == devTool_id) {
            UserScriptHook.proxy!!.openDevTools()
          }
        }

    findMethod(proxy.appMenuPropertiesDelegateImpl!!) { name == proxy.PREPARE_MENU }
        // public void prepareMenu(Menu menu, AppMenuHandler handler)
        .hookBefore {
          val menu = it.args[0] as Menu
          val devMenuItem = menu.add(Menu.NONE, devTool_id, Menu.NONE, "")
          devMenuItem.setTitle(devTool_text_res_id!!)
          devMenuItem.setIcon(devTool_icon_res_id!!)
          devMenuItem.setVisible(true)
        }
  }
}
