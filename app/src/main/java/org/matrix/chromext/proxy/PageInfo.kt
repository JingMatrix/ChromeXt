package org.matrix.chromext.proxy

import android.widget.FrameLayout
import android.widget.LinearLayout
import java.lang.ref.WeakReference
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.findField
import org.matrix.chromext.utils.findFieldOrNull

object PageInfoProxy {

  private val pageInfoController =
      Chrome.load("org.chromium.components.page_info.PageInfoController")
  val pageInfoRowView = Chrome.load("org.chromium.components.page_info.PageInfoRowView")
  val mIcon = pageInfoRowView.declaredFields[0]
  val mTitle = pageInfoRowView.declaredFields[1]
  val mSubtitle = pageInfoRowView.declaredFields[2]
  val pageInfoView =
      if (Chrome.isEdge) {
        Chrome.load("org.chromium.components.page_info.PageInfoView")
      } else {
        findField(pageInfoController) { type.superclass == FrameLayout::class.java }.type
      }
  val mRowWrapper = findFieldOrNull(pageInfoView) { type == LinearLayout::class.java }
  val pageInfoControllerRef =
      // A particular WebContentsObserver designed for PageInfoController
      findField(pageInfoController) {
            type.declaredFields.size == 1 &&
                (type.declaredFields[0].type == pageInfoController ||
                    type.declaredFields[0].type == WeakReference::class.java)
          }
          .type
}
