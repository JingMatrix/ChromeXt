# ChromeXt

Add UserScript support to Chrome using Xposed framework

##  How it works?

We hook a `onUpdateUrl` function in [ChromeHook.kt](app/src/main/java/org/matrix/chromext/hook/ChromeHook.kt),
add URL comparison there and evaluate JavaScript using the `javascript:` scheme.

### Adapt to your Chrome version

The author has tested `ChromeXt` with the latest `Android Chrome 108.0.5359.79`, and it works well.
Please consider update your Android Chrome first.

For other vesions, it might not work.
To adapt to those versions, one only need to find out one method name in its smali code.
First use `apktool` to decompile the `split_chrome.apk` file pulled from the installation of Chrome on your phone,
then follow the hints in [UserScript.kt](app/src/main/java/org/matrix/chromext/proxy/UserScript.kt) to get the correct name
and modify it in the `SharedPreferences` of Chrome at `/data/data/com.android.chrome/shared_prefs/ChromeXt.xml`.

## Usage

Pick up the latest built APK from [Action](https://github.com/JingMatrix/ChromeXt/actions/workflows/android.yml) and install it.
You can then install UserScripts from popular sources: any URL that ends with `.user.js`.
Currently, ChromeXt supports `@match`, `@run-at` and `@grant GM_addStyle` since they are everything the author needs to perform all sort of tasks.

To manage scripts installed by `ChromeXt`, here is a simple [front end](https://jingmatrix.github.io/ChromeXt/).

## Bonus

### Solution of system gesture conflicts

To enable forward gesture in chrome, with the help of this module,
one only needs to disable the right back gesture by
```sh
adb shell settings put secure back_gesture_inset_scale_right -1
```

## Development Plans

- [ ] Improve front end
