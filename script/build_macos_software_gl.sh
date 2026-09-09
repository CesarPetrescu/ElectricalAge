#!/usr/bin/env bash
# QA-only GLFW build for hosted M1 VMs. The production ELN JAR is untouched.
# Accept Apple's actual software OpenGL renderer instead of requiring a GPU.
set -euo pipefail
[[ "$(uname -s)" == Darwin && "$(uname -m)" == arm64 ]]
prefix="${RUNNER_TEMP:?}/eln-glfw-3.4-apple-software"
work="${RUNNER_TEMP}/eln-glfw-source"
mkdir -p build/native-graphics "$prefix"
if [[ ! -f "$prefix/lib/libglfw.3.dylib" ]]; then
  git init "$work"
  git -C "$work" remote add origin https://github.com/glfw/glfw.git
  git -C "$work" fetch --depth 1 origin 7b6aead9fb88b3623e3b3725ebb42670cbe4c579
  git -C "$work" checkout --detach FETCH_HEAD
  test "$(git -C "$work" rev-parse HEAD)" = 7b6aead9fb88b3623e3b3725ebb42670cbe4c579
  python3 - "$work/src/nsgl_context.m" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1]);s=p.read_text();old='    ADD_ATTRIB(NSOpenGLPFAAccelerated);'
assert s.count(old)==1
p.write_text(s.replace(old,'    if (!getenv("ELN_QA_APPLE_SOFTWARE_GL"))\n        ADD_ATTRIB(NSOpenGLPFAAccelerated);'))
PY
  git -C "$work" diff -- src/nsgl_context.m > "$prefix/glfw-allow-software.patch"
  cmake -S "$work" -B "$work/build" -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
    -DGLFW_BUILD_EXAMPLES=OFF -DGLFW_BUILD_TESTS=OFF -DGLFW_BUILD_DOCS=OFF -DCMAKE_INSTALL_PREFIX="$prefix"
  cmake --build "$work/build" --parallel 3
  cmake --install "$work/build"
fi
cp "$prefix/glfw-allow-software.patch" build/native-graphics/
file "$prefix/lib/libglfw.3.dylib" | tee build/native-graphics/library-architecture.txt
otool -L "$prefix/lib/libglfw.3.dylib" > build/native-graphics/library-dependencies.txt
shasum -a 256 "$prefix/lib/libglfw.3.dylib" > build/native-graphics/library-sha256.txt
cat > build/native-graphics/backend.txt <<'TEXT'
Hosted M1 ARM64, actual macOS NSGL/Cocoa context and Apple Software Renderer.
NOT GPU acceleration. Original Minecraft framebuffer, shaders and ELN JAR.
Pinned GLFW 3.4, sole patch makes the accelerated-renderer constraint optional
under ELN_QA_APPLE_SOFTWARE_GL; all window/input/render context code is upstream.
TEXT
printf 'ELN_APPLE_SOFTWARE_PREFIX=%s\nELN_QA_APPLE_SOFTWARE_GL=1\nJAVA_TOOL_OPTIONS=-Dorg.lwjgl.glfw.libname=%s/lib/libglfw.3.dylib\n' "$prefix" "$prefix" >> "${GITHUB_ENV:?}"
