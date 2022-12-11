# EzXhepler-template
A template for [EzXHelper](https://github.com/KyuubiRan/EzXHelper)

这是一份关于[EzXHelper](https://github.com/KyuubiRan/EzXHelper)的项目模板

## Use this template to create your project / 使用模板创建项目
Before use:
- Change the `applicationId` in `build.gradle.kts`
- Change the packageName and change the HookEntry class path in `xposed_init`
- Change the `package` in `AndroidManifest.xml`
- Change the `rootProject.name` in `settings.gradle.kts`
- Sync gradle
- Change the `TAG` and `PACKAGE_NAME_HOOKED` in `MainHook.kt`
- Change the `xposedscope` in `arrays.xml`

if you don't like kts, you can switch to the groovy branch.

开始之前：
- 修改`build.gradle.kts`中的`applicationId`
- 修改包名并同时修改`xposed_init`中的Hook入口
- 修改`AndroidManifest.xml`中的`package`
- 修改`settings.gradle.kts`中的`rootProject.name`
- 执行 Sync gradle
- 修改`MainHook.kt`中的`TAG`和`PACKAGE_NAME_HOOKED`
- 修改`arrays.xml`中的`xposedscope`

如果你不喜欢kts，你可以切换到groovy分支。
