package org.matrix.chromext.utils

import java.lang.reflect.Field

typealias FieldCondition = Field.() -> Boolean

fun findField(clz: Class<*>, findSuper: Boolean = false, condition: FieldCondition): Field {
  return findFieldOrNull(clz, findSuper, condition) ?: throw NoSuchFieldException()
}

fun findFieldOrNull(clz: Class<*>, findSuper: Boolean = false, condition: FieldCondition): Field? {
  var c = clz
  c.declaredFields
      .firstOrNull { it.condition() }
      ?.let {
        it.isAccessible = true
        return it
      }

  if (findSuper) {
    while (c.superclass?.also { c = it } != null) {
      c.declaredFields
          .firstOrNull { it.condition() }
          ?.let {
            it.isAccessible = true
            return it
          }
    }
  }
  return null
}
