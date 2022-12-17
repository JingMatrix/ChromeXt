#! /bin/sh
./gradlew ktfmtFormat && ./gradlew build
apk-singer -a app/build/outputs/apk/release/app-release-unsigned.apk --ks ~/.ssh/apk-release.keystore --ksPass $(cat ~/.ssh/apk.key) --ksKeyPass $(cat ~/.ssh/apk.key) --ksAlias $USER -o .
mv ./app-release-aligned-signed.apk /tmp/ChromeXt.apk
adb install /tmp/ChromeXt.apk
