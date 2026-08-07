package org.matrix.chromext.utils

import java.lang.reflect.Field
import java.lang.reflect.Method

typealias MethodCondition = Method.() -> Boolean

typealias FieldCondition = Field.() -> Boolean

private const val R8_HEAD = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val R8_TAIL = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

// R8 hands out obfuscated member names in declaration order, whereas getDeclaredFields returns them
// sorted by name, so mapping a name back to its index in that sequence recovers the declaration
// order our heuristics are really after. The first character comes from a 52 letter pool and every
// further character from a 62 character pool, least significant first, which is why the names run
// a, b, ... z, A, ... Z, a0, b0, ... Z0, a1, ... Z9, aa, ba, ... - big classes really do reach the
// letter suffixed part, so stopping at digits would silently mis-rank half of Whale's members.
// Names that R8 did not generate, i.e. everything on a non obfuscated build, rank last so that they
// are never picked by a lowest rank search.
fun r8Rank(name: String): Int {
  val head = if (name.isEmpty()) -1 else R8_HEAD.indexOf(name[0])
  if (head < 0) return Int.MAX_VALUE
  if (name.length == 1) return head
  var value = 0L
  for (i in name.length - 1 downTo 1) {
    val digit = R8_TAIL.indexOf(name[i])
    if (digit < 0) return Int.MAX_VALUE
    value = value * R8_TAIL.length + digit
    if (value > Int.MAX_VALUE / R8_HEAD.length) return Int.MAX_VALUE
  }
  return ((value + 1) * R8_HEAD.length + head).toInt()
}

// The member declared first in the original source, among the obfuscated ones.
fun <T : java.lang.reflect.Member> Iterable<T>.firstDeclared(): T? =
    filter { r8Rank(it.name) != Int.MAX_VALUE }.minByOrNull { r8Rank(it.name) }

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
  findMethodOrNull(this::class.java, true, condition)?.let {
    return it(this, *args)
  }
  throw NoSuchMethodException()
}

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
