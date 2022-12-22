package org.matrix.chromext.hook

import android.content.Context
import android.view.Menu
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import org.matrix.chromext.R
import org.matrix.chromext.proxy.MenuProxy

object MenuHook : BaseHook() {

  var devTool_res_id: Int? = null

  override fun init(ctx: Context) {

    val menuProxy = MenuProxy(ctx)

    if (!menuProxy.isDeveloper) {
      return
    }

    findMethod(menuProxy.chromeTabbedActivity!!) { name == menuProxy.MENU_KEYBOARD_ACTION }
        // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
        .hookAfter {
          val id = it.args[0] as Int
          if (id == R.id.developer_tools_id) {
            Log.i("Dev Tools icon clicked!")
          }
        }

    findMethod(menuProxy.appMenuPropertiesDelegateImpl!!) { name == menuProxy.PREPARE_MENU }
        // public void prepareMenu(Menu menu, AppMenuHandler handler)
        .hookBefore {
          val menu = it.args[0] as Menu
          val devMenuItem = menu.add(Menu.NONE, R.id.developer_tools_id, Menu.NONE, "Dev Tools")
          devMenuItem.setIcon(ctx.getResources().getDrawable(devTool_res_id!!, null))
          devMenuItem.setVisible(true)
        }
  }
}
