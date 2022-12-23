package org.matrix.chromext.utils

import java.lang.reflect.Method

typealias MethodCondition = Method.() -> Boolean

fun findMethod(clz: Class<*>, findSuper: Boolean = false, condition: MethodCondition): Method {
  return findMethodOrNull(clz, findSuper, condition) ?: throw NoSuchMethodException()
}

fun findMethodOrNull(
    clz: Class<*>,
    findSuper: Boolean = false,
    condition: MethodCondition
): Method? {
  var c = clz
  c.declaredMethods
      .firstOrNull { it.condition() }
      ?.let {
        it.isAccessible = true
        return it
      }

  if (findSuper) {
    while (c.superclass?.also { c = it } != null) {
      c.declaredMethods
          .firstOrNull { it.condition() }
          ?.let {
            it.isAccessible = true
            return it
          }
    }
  }
  return null
}

fun Any.invokeMethod(vararg args: Any?, condition: MethodCondition): Any? {
  this::class
      .java
      .declaredMethods
      .firstOrNull { it.condition() }
      ?.let {
        it.isAccessible = true
        return it(this, *args)
      }
  throw NoSuchMethodException()
}
