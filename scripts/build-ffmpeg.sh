#!/bin/bash
# The FFmpeg that "Convert sound for your TV" runs (SoundShare.kt): one small executable per processor, packed as
# lib/<abi>/libnebulaff.so so Android unpacks it beside the app's other native code, where an app may run a program.
# It does one thing — reads a film's DTS frames from its input (the TV reads the film and sends them; the phone never
# fetches anything), decodes them and writes Dolby Digital Plus in fragmented MP4 to its output — so everything else,
# the network included, is left out.
#
#   scripts/build-ffmpeg.sh                 → app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libnebulaff.so (needs the NDK:
#                                             $ANDROID_NDK_LATEST_HOME or $ANDROID_NDK_HOME)
#   HOST=1 scripts/build-ffmpeg.sh <dir>    → <dir>/ffmpeg for this machine, the same parts (to test the command line)
set -euo pipefail
VER=7.1.1
SHA=733984395e0dbbe5c046abda2dc49a5544e7e0e1e2366bba849222ae9e3a03b1
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=${FFWORK:-$ROOT/build/ffmpeg}
mkdir -p "$WORK"
cd "$WORK"
if [ ! -d "ffmpeg-$VER" ]; then
  [ -f "ffmpeg-$VER.tar.xz" ] || curl -sSfL -o "ffmpeg-$VER.tar.xz" "https://ffmpeg.org/releases/ffmpeg-$VER.tar.xz"
  echo "$SHA  ffmpeg-$VER.tar.xz" | sha256sum -c -
  tar -xJf "ffmpeg-$VER.tar.xz"
fi

PARTS=(
  --disable-everything --disable-autodetect --disable-doc --disable-debug --disable-ffplay --disable-ffprobe --enable-ffmpeg
  --enable-static --disable-shared --enable-small --disable-avdevice --disable-swscale --disable-postproc --disable-iconv
  --disable-network --enable-protocol=pipe
  --enable-demuxer=dts,truehd
  --enable-decoder=dca,truehd,mlp
  --enable-parser=dca,mlp
  --enable-encoder=eac3
  --enable-muxer=mp4
  --enable-filter=abuffer,abuffersink,aformat,anull,aresample,atrim,asetpts,buffer,buffersink,format,null,trim,setpts
  --enable-swresample
)

build() {   # build <out dir> [configure args…]
  local out=$1; shift
  rm -rf "b-$(basename "$out")"; cp -r "ffmpeg-$VER" "b-$(basename "$out")"
  ( cd "b-$(basename "$out")" && ./configure "${PARTS[@]}" "$@" > configure.log 2>&1 || { tail -40 configure.log; exit 1; }
    make -j"$(nproc)" ffmpeg > make.log 2>&1 || { tail -40 make.log; exit 1; } )
  mkdir -p "$out"
  cp "b-$(basename "$out")/ffmpeg" "$out/"
}

if [ "${HOST:-}" = 1 ]; then
  OUT=${1:-$WORK/host}
  build "$OUT" --disable-x86asm
  echo "host ffmpeg: $OUT/ffmpeg ($(stat -c %s "$OUT/ffmpeg") bytes)"
  exit 0
fi

NDK=${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-}}
[ -n "$NDK" ] && [ -d "$NDK" ] || { echo "no NDK (set ANDROID_NDK_HOME)"; exit 1; }
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64
API=26
for ABI in arm64-v8a armeabi-v7a; do
  case $ABI in
    arm64-v8a)   ARCH=aarch64; CPU=armv8-a; CC=aarch64-linux-android$API-clang ;;
    armeabi-v7a) ARCH=arm;     CPU=armv7-a; CC=armv7a-linux-androideabi$API-clang ;;
  esac
  build "$WORK/out-$ABI" --enable-cross-compile --target-os=android --arch=$ARCH --cpu=$CPU \
    --cc="$TC/bin/$CC" --cxx="$TC/bin/${CC}++" --ar="$TC/bin/llvm-ar" --nm="$TC/bin/llvm-nm" --ranlib="$TC/bin/llvm-ranlib" \
    --strip="$TC/bin/llvm-strip" --sysroot="$TC/sysroot" --enable-pic --extra-ldflags=-pie
  DEST=$ROOT/app/src/main/jniLibs/$ABI
  mkdir -p "$DEST"
  "$TC/bin/llvm-strip" -o "$DEST/libnebulaff.so" "$WORK/out-$ABI/ffmpeg"
  echo "$ABI: $DEST/libnebulaff.so ($(stat -c %s "$DEST/libnebulaff.so") bytes)"
done
