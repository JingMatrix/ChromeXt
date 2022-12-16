# ChromeXt

Add UserScript support to Chrome using Xposed framework

##  How it works?

We hook a `onUpdateUrl` function in [ChromeHook.kt](app/src/main/java/org/matrix/chromext/hook/ChromeHook.kt),
add URL comparison there and evaluate JavaScript using the `javascript:` scheme.

### Adapt to your Chrome version

We pay our main efforts to support the latest stable of version of Android Chrome.
And usually the `beta` or `dev` versions are supported as well, but not guaranteed.

Recently, the author has tested `ChromeXt` with the latest `Android Chrome 108.0.5359.128`, and it works well.
Please consider update your Android Chrome first before proceeding.

For other versions, it might not work.
To adapt to those versions, one only need to find out one method name in its [smali](https://github.com/JesusFreke/smali/wiki) code.
Here is how to do that.
First use `apktool` to decompile the `split_chrome.apk` file pulled from the installation of Chrome on your phone,
then follow the hints in [UserScript.kt](app/src/main/java/org/matrix/chromext/proxy/UserScript.kt) to get the correct name
and modify it in the [SharedPreferences](https://developer.android.com/reference/android/content/SharedPreferences) of Chrome at `/data/data/com.android.chrome/shared_prefs/ChromeXt.xml`.

## Usage

This project requires **Xposed framework** installed.
You can try the following implements of it, depending on your Android version or whether having root enabled:
[LSPosed](https://github.com/LSPosed/LSPosed), [LSPatch](https://github.com/LSPosed/LSPatch),
[EdXposed](https://github.com/ElderDrivers/EdXposed), [TaiChi](https://github.com/taichi-framework/TaiChi),
[VirtualXposed](https://github.com/android-hacker/VirtualXposed), [Dreamland](https://github.com/canyie/Dreamland).

Pick up the latest built APK from my repo's [GitHub Action](https://github.com/JingMatrix/ChromeXt/actions/workflows/android.yml) and install it.
The author upload releases to [Xposed-Modules-Repo](https://github.com/Xposed-Modules-Repo/org.matrix.chromext/releases) when needed, but not that frequently.
You can then install UserScripts from popular sources: any URL that ends with `.user.js`.

### Supported API

Currently, ChromeXt supports only the following APIs since they are everything the author needs to perform all sort of tasks.

1. @name, @namespace
2. @match (must present, otherwise the script is considered as invalid)
3. @include = @match, @exclude
4. @run-at: document-start, document-end, document-idle (the default and fallback value)
5. @grant GM_addStyle, GM_addElement
6. @require

Honestly, users can implement most other APIs in their UserScripts.

### UserScripts manager front end

To manage scripts installed by `ChromeXt`, here is a simple [front end](https://jingmatrix.github.io/ChromeXt/).

### Limitations

A valid UserScript would fail if the following two conditions hold _at the same time_:

1. The matched website has disabled `script: 'unsafe-eval';` by [Content Security Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP),
2. The script size is nearly 2M, or it escapes \` with backslash.

Please note that one can, of course, use \` normally. 
To deal with this extremely rare case, one should
```
use multiple scripts of normal sizes instead of a giant script, and avoid using \` in the code.
```

## Bonus

### Solution of system gesture conflicts

To enable forward gesture in chrome, with the help of this module,
one only needs to disable the right back gesture by
```sh
adb shell settings put secure back_gesture_inset_scale_right -1
```

## Contribute to this project

Before you submit your pull-requests, please ensure that the command
`./gradlew build` or `gradlew.bat build` produces no warnings and no errors.

Here are corresponding files you might want / need to change:
1. Front end: [index.html](index.html)
2. Tampermonkey API: [LocalScripts.kt](app/src/main/java/org/matrix/chromext/script/LocalScripts.kt)

## Development plans

- [ ] Improve front end
- [ ] Support more [Tampermonkey API](https://www.tampermonkey.net/documentation.php)s
