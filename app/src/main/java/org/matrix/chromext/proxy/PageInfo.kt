package org.matrix.chromext.proxy

import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.lang.ref.WeakReference
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.findField

object PageInfoProxy {

  val pageInfoRowView = Chrome.load("org.chromium.components.page_info.PageInfoRowView")
  val mIcon = pageInfoRowView.declaredFields[0]
  val mTitle = pageInfoRowView.declaredFields[1]
  val mSubtitle = pageInfoRowView.declaredFields[2]

  val pageInfoController = Chrome.load("org.chromium.components.page_info.PageInfoController")
  val mView =
      findField(pageInfoController) {
        (Chrome.isEdge && type == FrameLayout::class.java) ||
            (type.superclass == FrameLayout::class.java &&
                type.interfaces.contains(OnClickListener::class.java))
      }

  private val pageInfoView =
      if (Chrome.isEdge) Chrome.load("org.chromium.components.page_info.PageInfoView")
      else mView.type
  val mRowWrapper = findField(pageInfoView) { type == LinearLayout::class.java }

  val pageInfoControllerRef =
      // A particular WebContentsObserver designed for PageInfoController
      findField(pageInfoController) {
            type.declaredFields.size == 1 &&
                (type.declaredFields[0].type == pageInfoController ||
                    type.declaredFields[0].type == WeakReference::class.java)
          }
          .type
}
