#!/usr/bin/env bash
# QA-only off-screen renderer for hosted M1 VMs without a CGL core context.
# This does NOT alter the ELN JAR, Minecraft classes, shaders, or simulation.
set -euo pipefail
[[ "$(uname -s)" == Darwin && "$(uname -m)" == arm64 ]]
prefix="${RUNNER_TEMP:?}/eln-osmesa-24.3.4"
mkdir -p build/native-graphics
export HOMEBREW_NO_AUTO_UPDATE=1
brew install llvm@19 meson ninja pkgconf bison
export PATH="$(brew --prefix llvm@19)/bin:$(brew --prefix bison)/bin:$PATH"
export CC=/usr/bin/clang CXX=/usr/bin/clang++
export CMAKE_PREFIX_PATH="$(brew --prefix llvm@19)"
export PKG_CONFIG_PATH="$(brew --prefix llvm@19)/lib/pkgconfig:$(brew --prefix)/lib/pkgconfig"
python3 -m pip install Mako==1.3.8 PyYAML==6.0.2 packaging==24.2
work="${RUNNER_TEMP}/eln-graphics-source"
mkdir -p "$work" "$prefix"
if [[ ! -f "$prefix/lib/libOSMesa.8.dylib" ]]; then
  curl --fail --location --retry 3 https://archive.mesa3d.org/older-versions/24.x/mesa-24.3.4.tar.xz -o "$work/mesa.tar.xz"
  echo "e641ae27191d387599219694560d221b7feaa91c900bcec46bf444218ed66025  $work/mesa.tar.xz" | shasum -a 256 -c -
  tar -xf "$work/mesa.tar.xz" -C "$work"
  meson setup "$work/mesa-build" "$work/mesa-24.3.4" --prefix="$prefix" --buildtype=release \
    -Dplatforms= -Dgallium-drivers=swrast -Dvulkan-drivers= -Dosmesa=true \
    -Dglx=disabled -Degl=disabled -Dgbm=disabled -Dgles1=disabled -Dgles2=disabled \
    -Dllvm=enabled -Dshared-llvm=enabled -Dbuild-tests=false
  meson compile -C "$work/mesa-build" -j 3
  meson install -C "$work/mesa-build"
fi
if [[ ! -f "$prefix/lib/libglfw.3.dylib" ]]; then
  git init "$work/glfw"
  git -C "$work/glfw" remote add origin https://github.com/glfw/glfw.git
  git -C "$work/glfw" fetch --depth 1 origin 7b6aead9fb88b3623e3b3725ebb42670cbe4c579
  git -C "$work/glfw" checkout --detach FETCH_HEAD
  test "$(git -C "$work/glfw" rev-parse HEAD)" = 7b6aead9fb88b3623e3b3725ebb42670cbe4c579
  # GLFW already supports OSMesa on Cocoa. Select that existing backend via
  # an opt-in environment variable because the game's default-window-hints
  # calls otherwise request NSGL. Input/window functions remain upstream.
  python3 - "$work/glfw/src/window.c" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1]);s=p.read_text();old='_glfw.hints.context.source = GLFW_NATIVE_CONTEXT_API;'
assert s.count(old)==1
p.write_text(s.replace(old,'_glfw.hints.context.source = getenv("ELN_QA_OSMESA") ? GLFW_OSMESA_CONTEXT_API : GLFW_NATIVE_CONTEXT_API;'))
PY
  git -C "$work/glfw" diff -- src/window.c > build/native-graphics/glfw-osmesa-selection.patch
  cmake -S "$work/glfw" -B "$work/glfw-build" -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
    -DGLFW_BUILD_EXAMPLES=OFF -DGLFW_BUILD_TESTS=OFF -DGLFW_BUILD_DOCS=OFF -DCMAKE_INSTALL_PREFIX="$prefix"
  cmake --build "$work/glfw-build" --parallel 3
  cmake --install "$work/glfw-build"
fi
file "$prefix/lib/libOSMesa.8.dylib" "$prefix/lib/libglfw.3.dylib" | tee build/native-graphics/library-architectures.txt
otool -L "$prefix/lib/libOSMesa.8.dylib" > build/native-graphics/library-dependencies.txt
shasum -a 256 "$prefix/lib/libOSMesa.8.dylib" "$prefix/lib/libglfw.3.dylib" > build/native-graphics/library-sha256.txt
cat > build/native-graphics/backend.txt <<'EOF'
Hosted M1 ARM64, Mesa 24.3.4 OSMesa/llvmpipe, GLFW 3.4 Cocoa window/input.
Actual Minecraft framebuffer screenshots, off-screen software OpenGL.
NOT Apple GPU acceleration and NOT validation of the Apple NSGL driver.
The sole GLFW patch chooses its existing OSMesa backend by an opt-in flag.
EOF
printf 'ELN_OSMESA_PREFIX=%s\nELN_QA_OSMESA=1\nGALLIUM_DRIVER=llvmpipe\nLP_NUM_THREADS=2\nDYLD_FALLBACK_LIBRARY_PATH=%s/lib:/usr/local/lib:/usr/lib\nJAVA_TOOL_OPTIONS=-Dorg.lwjgl.opengl.libname=%s/lib/libOSMesa.8.dylib -Dorg.lwjgl.glfw.libname=%s/lib/libglfw.3.dylib\n' "$prefix" "$prefix" "$prefix" "$prefix" >> "${GITHUB_ENV:?}"
