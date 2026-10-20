package org.matrix.chromext.hook

abstract class BaseHook {
  var isInit: Boolean = false

  abstract fun init()
}
