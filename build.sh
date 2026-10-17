#!/usr/bin/env bash
# 大肥鱼账本 —— 不依赖 Gradle 的直编脚本
# aapt2(资源 + R.java) -> javac -> d8 -> 打包 -> zipalign -> apksigner
#
# 环境变量（都可选）：
#   ANDROID_SDK_ROOT / ANDROID_HOME   指向 Android SDK 根目录
#   ANDROID_PLATFORM                  平台目录名，默认 android-35
#   BUILD_TOOLS_VERSION               build-tools 版本目录名，默认 35.0.0
#   QEMU_TOOLS                        arm64 主机上转发 x86_64 工具用，默认 /root/tools/bin
#   KS_PASS / KS_ALIAS                签名用，默认 dafeiyu123 / dafeiyu
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
OUT="$PROJ/build"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
    for guess in "$PROJ/sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" \
                 "$HOME/tools/android-sdk" "/usr/local/lib/android/sdk"; do
        if [ -d "$guess" ]; then SDK="$guess"; break; fi
    done
fi

PLATFORM="${ANDROID_PLATFORM:-android-35}"
BT_VER="${BUILD_TOOLS_VERSION:-35.0.0}"
BT="$SDK/build-tools/$BT_VER"
ANDROID_JAR="$SDK/platforms/$PLATFORM/android.jar"

die() { echo "错误：$*" >&2; exit 1; }

[ -d "$SDK" ] || die "没找到 Android SDK，请设置 ANDROID_SDK_ROOT 或 ANDROID_HOME"
[ -f "$ANDROID_JAR" ] || die "缺少 $ANDROID_JAR（用 sdkmanager 安装 platforms;$PLATFORM）"
[ -x "$BT/d8" ] || die "缺少 $BT/d8（用 sdkmanager 安装 build-tools;$BT_VER）"

# 官方 aapt2 / zipalign 只有 x86_64 版；arm64 主机上跑不起来时，
# 自动退回到 qemu 包装脚本（参见 README 的「在 arm64 主机上构建」）。
QEMU_TOOLS="${QEMU_TOOLS:-/root/tools/bin}"
AAPT2="$BT/aapt2"
ZIPALIGN="$BT/zipalign"
if ! "$AAPT2" version >/dev/null 2>&1; then
    [ -x "$QEMU_TOOLS/aapt2" ] || die "aapt2 无法执行；arm64 主机需要先配好 qemu 包装（见 README）"
    AAPT2="$QEMU_TOOLS/aapt2"
fi
if ! "$ZIPALIGN" 2>/dev/null | head -1 >/dev/null 2>&1; then
    [ -x "$QEMU_TOOLS/zipalign" ] && ZIPALIGN="$QEMU_TOOLS/zipalign"
fi

KS="${KS_PATH:-$PROJ/keystore/dafeiyu.jks}"
KS_PASS="${KS_PASS:-dafeiyu123}"
ALIAS="${KS_ALIAS:-dafeiyu}"

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/apk"

echo "== 1/6 编译资源 =="
"$AAPT2" compile --dir "$PROJ/res" -o "$OUT/compiled/res.zip" --no-crunch

echo "== 2/6 链接资源并生成 R.java =="
"$AAPT2" link \
    -o "$OUT/apk/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$PROJ/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --auto-add-overlay \
    "$OUT/compiled/res.zip"

echo "== 3/6 编译 Java =="
find "$PROJ/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -nowarn \
    -bootclasspath "$ANDROID_JAR" \
    -d "$OUT/classes" \
    @"$OUT/sources.txt"

echo "== 4/6 dex =="
find "$OUT/classes" -name '*.class' -print0 \
    | xargs -0 "$BT/d8" --release --min-api 24 --lib "$ANDROID_JAR" --output "$OUT/dex"

echo "== 5/6 打包 + 对齐 =="
python3 "$PROJ/packapk.py" "$OUT/apk/base.apk" "$OUT/apk/unsigned.apk" "$OUT/dex/classes.dex"
"$ZIPALIGN" -f -p 4 "$OUT/apk/unsigned.apk" "$OUT/apk/aligned.apk"

echo "== 6/6 签名 =="
if [ ! -f "$KS" ]; then
    mkdir -p "$(dirname "$KS")"
    echo "  未找到密钥库，生成一个自签名密钥（仅用于本地安装）"
    keytool -genkeypair -keystore "$KS" -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -dname "CN=DaFeiYu Ledger, OU=Dev, O=DaFeiYu, L=Beijing, ST=Beijing, C=CN"
fi
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$ALIAS" \
    --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$PROJ/DaFeiYuLedger.apk" "$OUT/apk/aligned.apk"

"$BT/apksigner" verify --print-certs "$PROJ/DaFeiYuLedger.apk" | head -3
ls -l "$PROJ/DaFeiYuLedger.apk" | awk '{print "APK 大小:", $5, "字节"}'
echo "BUILD OK"
