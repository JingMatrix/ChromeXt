package com.example.template.hook

import com.github.kyuubiran.ezxhelper.utils.*

// Example hook
object ExampleHook : BaseHook() {
    override fun init() {
        // Example for findMethod
        findMethod("android.widget.Toast") {
            name == "show"
        }.hookBefore {
            Log.i("Hooked before Toast.show()")
        }

        // Example for getMethodByDesc
        getMethodByDesc("Landroid/widget/Toast;->show()V").hookAfter {
            Log.i("Hooked after Toast.show()")
        }
    }
}