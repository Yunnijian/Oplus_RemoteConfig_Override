#!/usr/bin/env bash
# 重建 libcosa.so（arm64-v8a）并自检产物，避免再次发布陈旧二进制。
#
# 用法:
#   NDK=/path/to/android-ndk ./build-android.sh
#
# 前置条件:
#   - Rust 已安装 aarch64-linux-android target (rustup target add aarch64-linux-android)
#   - NDK 内包含 aarch64-linux-android24-clang 工具链
#   - SQLite 使用 bundled 特性内嵌编译，无需设备系统库

set -euo pipefail

if [[ -z "${NDK:-}" ]]; then
    echo "错误: 请通过环境变量 NDK 指定 Android NDK 路径，例如:" >&2
    echo "  NDK=~/Library/Android/sdk/ndk/26.3.11579264 ./build-android.sh" >&2
    exit 1
fi

HOST_TAG=darwin-x86_64
case "$(uname -s)-$(uname -m)" in
    Darwin-arm64) HOST_TAG=darwin-arm64 ;;
    Linux-x86_64) HOST_TAG=linux-x86_64 ;;
    Linux-aarch64) HOST_TAG=linux-aarch64 ;;
esac

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
if [[ ! -x "$TOOLCHAIN/aarch64-linux-android24-clang" ]]; then
    echo "错误: 未找到 $TOOLCHAIN/aarch64-linux-android24-clang" >&2
    exit 1
fi

READELF="$TOOLCHAIN/llvm-readelf"

export PATH="$TOOLCHAIN:$PATH"
export CC_aarch64_linux_android="$TOOLCHAIN/aarch64-linux-android24-clang"

cargo build --release --target aarch64-linux-android

BINARY="target/aarch64-linux-android/release/cosa"
DEST="../android/app/src/main/jniLibs/arm64-v8a/libcosa.so"

# 产物自检:必须包含逐库失败上报与删除恢复的关键字符串，
# 否则说明源码改动未进入本次构建(陈旧二进制)。
# 注意:必须用 LC_ALL=C + grep -o, 否则 BSD grep 在 UTF-8 locale 下会因二进制中的
# 非法字节序列跳过匹配(中文功能串会漏报)。
for marker in "write failed on" "delete failed on" "protect_local_pkg_delete" "已忽略未知字段" "joyose-app"; do
    if ! LC_ALL=C grep -aoF "$marker" "$BINARY" | head -1 | grep -q .; then
        echo "错误: 产物缺少功能字符串 '$marker'，构建可能使用了陈旧缓存，请清理后重试" >&2
        exit 1
    fi
done

# SQLite 必须是内嵌编译:动态依赖里不得出现设备系统 libsqlite.so(≥3.38 符号在 Android 13 上缺失)
if [[ -x "$READELF" ]]; then
    NEEDED="$("$READELF" -d "$BINARY" 2>/dev/null | tr -d '\r')"
    if printf '%s\n' "$NEEDED" | grep -q 'libsqlite[.]so'; then
        echo "错误: 产物仍动态链接设备系统 libsqlite.so，rusqlite 的 bundled 特性未生效" >&2
        exit 1
    fi
    printf '%s\n' "$NEEDED" | grep -o 'Shared library: \[.*\]' | sed 's/^/  DT_NEEDED: /'
fi

cp "$BINARY" "$DEST"
echo "已更新 $DEST"
echo "产物自检通过:包含双库失败上报与删除保护恢复逻辑，SQLite 已内嵌"
