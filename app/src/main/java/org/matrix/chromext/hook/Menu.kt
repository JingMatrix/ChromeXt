package org.matrix.chromext.hook

import android.content.Context
import android.os.Build
import android.view.Menu
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import org.matrix.chromext.proxy.MenuProxy

object MenuHook : BaseHook() {

  override fun init(ctx: Context) {

    val proxy = MenuProxy(ctx)

    if (!proxy.isDeveloper || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      return
    }

    findMethod(proxy.chromeTabbedActivity!!) { name == proxy.MENU_KEYBOARD_ACTION }
        // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu)
        .hookAfter {
          val id = it.args[0] as Int
          if (ctx.getResources().getResourceName(id) ==
              "org.matrix.chromext:id/developer_tools_id") {
            UserScriptHook.proxy!!.openDevTools()
          }
        }

    findMethod(proxy.appMenuPropertiesDelegateImpl!!) { name == proxy.PREPARE_MENU }
        // public void prepareMenu(Menu menu, AppMenuHandler handler)
        .hookBefore {
          val menu = it.args[0] as Menu
          proxy.injectLocalMenu(it.thisObject, ctx, menu)
        }
  }
}
