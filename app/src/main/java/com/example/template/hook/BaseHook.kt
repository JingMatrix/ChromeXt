package com.example.template.hook

abstract class BaseHook {
    var isInit: Boolean = false
    abstract fun init()
}