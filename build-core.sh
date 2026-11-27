#!/usr/bin/env bash
# Build the Rust core for all Android ABIs and copy .so into jniLibs.
set -euo pipefail
ROOT=/opt/data/apps/loom
export PATH=/opt/data/tools/rust/bin:$PATH
export CARGO_HOME=/opt/data/tools/cargo-home
export CARGO_NET_RETRY=5
NDK=/opt/data/tools/android-sdk/ndk-27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64
export NDK_HOME=/opt/data/tools/android-sdk/ndk-27.2.12479018
export ANDROID_NDK_HOME=$NDK_HOME
# NDK's `clang` is a stub that execs `clang-18` by name, so its bin dir must be on PATH.
export PATH="$NDK/bin:$PATH"

# cc-rs looks for $TARGET-ar; the NDK ships llvm-ar instead.
export AR_aarch64_linux_android=$NDK/bin/llvm-ar
export AR_armv7_linux_androideabi=$NDK/bin/llvm-ar
export AR_x86_64_linux_android=$NDK/bin/llvm-ar
export CC_aarch64_linux_android=$NDK/bin/aarch64-linux-android24-clang
export CC_armv7_linux_androideabi=$NDK/bin/armv7a-linux-androideabi24-clang
export CC_x86_64_linux_android=$NDK/bin/x86_64-linux-android24-clang
export CXX_aarch64_linux_android=$NDK/bin/aarch64-linux-android24-clang++
export CXX_armv7_linux_androideabi=$NDK/bin/armv7a-linux-androideabi24-clang++
export CXX_x86_64_linux_android=$NDK/bin/x86_64-linux-android24-clang++
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$NDK/bin/aarch64-linux-android24-clang
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER=$NDK/bin/armv7a-linux-androideabi24-clang
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER=$NDK/bin/x86_64-linux-android24-clang

cd "$ROOT/core"
cargo build --release --target aarch64-linux-android
cargo build --release --target armv7-linux-androideabi

for abi in arm64-v8a:aarch64-linux-android armeabi-v7a:armv7-linux-androideabi; do
  dir=${abi%%:*}; tgt=${abi##*:}
  mkdir -p "$ROOT/app/src/main/jniLibs/$dir"
  cp "target/$tgt/release/libloom_core.so" "$ROOT/app/src/main/jniLibs/$dir/"
done
ls -lh "$ROOT"/app/src/main/jniLibs/*/libloom_core.so
