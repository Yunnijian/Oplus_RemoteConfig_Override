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
esac

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
if [[ ! -x "$TOOLCHAIN/aarch64-linux-android24-clang" ]]; then
    echo "错误: 未找到 $TOOLCHAIN/aarch64-linux-android24-clang" >&2
    exit 1
fi

export PATH="$TOOLCHAIN:$PATH"
export CC_aarch64_linux_android="$TOOLCHAIN/aarch64-linux-android24-clang"

cargo build --release --target aarch64-linux-android

BINARY="target/aarch64-linux-android/release/cosa"
DEST="../android/app/src/main/jniLibs/arm64-v8a/libcosa.so"

# 产物自检:必须包含逐库失败上报与删除恢复的关键字符串，
# 否则说明源码改动未进入本次构建(陈旧二进制)。
for marker in "write failed on" "delete failed on" "protect_local_pkg_delete" "已忽略未知字段"; do
    if ! grep -qaF "$marker" "$BINARY"; then
        echo "错误: 产物缺少功能字符串 '$marker'，构建可能使用了陈旧缓存，请清理后重试" >&2
        exit 1
    fi
done

cp "$BINARY" "$DEST"
echo "已更新 $DEST"
echo "产物自检通过:包含双库失败上报与删除保护恢复逻辑"
