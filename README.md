# ChromeXt

Add UserScript support to Chrome using Xposed framework

##  How it works?

We hook a `onUpdateUrl` function in [ChromeHook.kt](app/src/main/java/org/matrix/chromext/hook/ChromeHook.kt),
add URL comparison there and evaluate JavaScript using the `javascript:` scheme.

### Adapt to your Chrome version

We only need to find out some method names in its smali code.
First use `apktool` to decompile `split_chrome.apk` taken from the installation of Chrome on your phone,
then follow the hints in [BaseHook.kt](app/src/main/java/org/matrix/chromext/hook/BaseHook.kt) to get correct names
and modify them there.

## Usage

Current I hard-coded a YouTube AdBlocker as an example.
You might want to modify it by your own.


## Development Plans

- [ ] Add UserScript manager by `chrome://` URL scheme
- [ ] Add DevTool support through [Eruda](https://github.com/liriliri/eruda) project
