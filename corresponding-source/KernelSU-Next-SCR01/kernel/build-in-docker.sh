#!/usr/bin/env bash

set -euo pipefail

source_root=$(cd "$(dirname "$0")/.." && pwd)
output_dir=${1:-"$source_root/out"}
mkdir -p "$output_dir"
for output_name in ksu_glue.unpatched.ko ksu_glue.ko; do
  if [[ -e "$output_dir/$output_name" ]]; then
    echo "output already exists: $output_dir/$output_name" >&2
    exit 2
  fi
done

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required" >&2
  exit 2
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but its daemon is not running" >&2
  exit 2
fi

docker run --rm --platform linux/amd64 \
  -v "$source_root:/source:ro" \
  -v "$output_dir:/out" \
  ubuntu:20.04@sha256:c664f8f86ed5a386b0a340d981b8f81714e21a8b9c73f658c4bea56aa179d54a \
  bash -euo pipefail -c '
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq bc bison curl flex gcc-aarch64-linux-gnu libelf-dev libssl-dev make python3 python3-pyelftools xz-utils
    mkdir -p /ksrc /work
    curl -fsSL https://cdn.kernel.org/pub/linux/kernel/v4.x/linux-4.14.186.tar.xz -o /ksrc/linux-4.14.186.tar.xz
    echo "445b426181005b157a0cd33d663ed61a73c32c669772e066bb30291c6775a260  /ksrc/linux-4.14.186.tar.xz" | sha256sum -c -
    tar -C /ksrc -xf /ksrc/linux-4.14.186.tar.xz
    cp /source/kernel/device.config /ksrc/linux-4.14.186/.config
    printf "%s\n" "-24165939" > /ksrc/linux-4.14.186/localversion-scr01
    make -C /ksrc/linux-4.14.186 ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- olddefconfig
    make -C /ksrc/linux-4.14.186 ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- modules_prepare
    cp /source/kernel/Kbuild /source/kernel/ksu_glue.c /source/kernel/adb_root_toggle.c /work/
    make -C /ksrc/linux-4.14.186 M=/work ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- modules
    cp /work/ksu_glue.ko /out/ksu_glue.unpatched.ko
    python3 /source/kernel/patch_init_offset.py /out/ksu_glue.unpatched.ko /out/ksu_glue.ko
    echo "f60a5bf12e127e7d666009fa019e5341204ef9aaf59c0f043166506a1e011ce6  /out/ksu_glue.unpatched.ko" | sha256sum -c -
    echo "e0fc60e4f5f8abc1a027f1867147b3de88a402e537ebd259e1217364fe604145  /out/ksu_glue.ko" | sha256sum -c -
  '
