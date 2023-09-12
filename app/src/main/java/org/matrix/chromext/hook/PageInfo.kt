package org.matrix.chromext.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.proxy.PageInfoProxy
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.utils.*

object PageInfoHook : BaseHook() {

  override fun init() {

    val proxy = PageInfoProxy

    // Add eruda menu to page_info dialog
    var pageInfoController: Any? = null
    proxy.pageInfoControllerRef.declaredConstructors[0].hookAfter {
      pageInfoController = it.thisObject
    }
    proxy.pageInfoView.declaredConstructors[0].hookAfter {
      val ctx = it.args[0] as Context
      Resource.enrich(ctx)
      val url = Chrome.getUrl()!!
      if (!url.startsWith("edge://")) {
        val erudaRow =
            proxy.pageInfoRowView.declaredConstructors[0].newInstance(ctx, null) as ViewGroup
        erudaRow.setVisibility(View.VISIBLE)
        val icon = proxy.mIcon.get(erudaRow) as ImageView
        icon.setImageResource(R.drawable.ic_devtools)
        val subTitle = proxy.mSubtitle.get(erudaRow) as TextView
        (subTitle.getParent() as? ViewGroup)?.removeView(subTitle)
        val title = proxy.mTitle.get(erudaRow) as TextView
        if (isChromeXtFrontEnd(url)) {
          title.setText(R.string.main_menu_developer_tools)
          erudaRow.setOnClickListener {
            Listener.on("inspectPages")
            pageInfoController!!.invokeMethod() { name == "destroy" }
          }
        } else if (isUserScript(url)) {
          title.setText(R.string.main_menu_install_script)
          erudaRow.setOnClickListener {
            val sandBoxed = shouldBypassSandbox(url)
            Chrome.evaluateJavascript(listOf("installScript(true);"), null, sandBoxed)
            pageInfoController!!.invokeMethod() { name == "destroy" }
          }
        } else {
          title.setText(R.string.main_menu_eruda_console)
          erudaRow.setOnClickListener {
            UserScriptProxy.evaluateJavascript(Local.openEruda)
            pageInfoController!!.invokeMethod() { name == "destroy" }
          }
        }
        (proxy.mRowWrapper!!.get(it.thisObject) as LinearLayout).addView(erudaRow)
      }
    }
    // readerMode.init(Chrome.load("org.chromium.chrome.browser.dom_distiller.ReaderModeManager"))
  }
}
