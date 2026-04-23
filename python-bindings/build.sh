#!/usr/bin/env bash
# =============================================================================
# build.sh – Master build script for MaxiCP Python bindings.
#
# Usage (run from the repo root OR from python-bindings/):
#   ./python-bindings/build.sh [--skip-java] [--skip-native] [--install]
#
# Steps:
#   1. Detect GraalVM native-image (auto-discovers common install locations).
#   2. Build the fat JAR with Maven (mvn package assembly:single -DskipTests).
#   3. Run native-image --shared to produce libmaxicp.{dylib,so}.
#   4. Copy the library into python/maxicp/_lib/.
#   5. (--install) pip-install the Python package in editable mode.
#
# Requirements:
#   - GraalVM JDK 21+ with native-image  (brew install --cask graalvm-jdk@21)
#   - Maven 3.8+
#   - Python 3.9+ with pip
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GRN='\033[0;32m'; YLW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GRN}[build.sh]${NC} $*"; }
warn()  { echo -e "${YLW}[build.sh] WARN:${NC} $*"; }
error() { echo -e "${RED}[build.sh] ERROR:${NC} $*" >&2; exit 1; }

# --- Parse arguments ---------------------------------------------------------
SKIP_JAVA=false
SKIP_NATIVE=false
DO_INSTALL=false

for arg in "$@"; do
    case "$arg" in
        --skip-java)   SKIP_JAVA=true ;;
        --skip-native) SKIP_NATIVE=true ;;
        --install)     DO_INSTALL=true ;;
        *) warn "Unknown argument: $arg (ignored)" ;;
    esac
done

# --- Locate repo root (works whether called from repo root or python-bindings/) ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "$(basename "$SCRIPT_DIR")" == "python-bindings" ]]; then
    REPO_ROOT="$(dirname "$SCRIPT_DIR")"
    PB_DIR="$SCRIPT_DIR"
else
    REPO_ROOT="$SCRIPT_DIR"
    PB_DIR="$SCRIPT_DIR/python-bindings"
fi

LIB_OUT="$PB_DIR/python/maxicp/_lib"
mkdir -p "$LIB_OUT"

info "Repo root  : $REPO_ROOT"
info "Library out: $LIB_OUT"

# --- Step 1: Detect native-image ---------------------------------------------
info "Looking for native-image..."

try_graalvm_home() {
    local home="$1"
    [[ -z "$home" ]] && return 1
    if [[ -x "$home/bin/native-image" ]]; then
        export JAVA_HOME="$home"
        export PATH="$home/bin:$PATH"
        info "  Using GraalVM at: $home"
        return 0
    fi
    return 1
}

if ! command -v native-image &>/dev/null; then
    # Search well-known macOS Homebrew locations
    for candidate in \
        /Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home \
        /Library/Java/JavaVirtualMachines/graalvm-jdk-21/Contents/Home \
        "$(find /Library/Java/JavaVirtualMachines -maxdepth 4 -name native-image 2>/dev/null \
               | grep -E '/bin/' | sed 's|/bin/native-image||' | head -1 || true)" \
        "$(find "$HOME/Library/Java/JavaVirtualMachines" -maxdepth 4 -name native-image 2>/dev/null \
               | grep -E '/bin/' | sed 's|/bin/native-image||' | head -1 || true)" \
        "${JAVA_HOME:-}" \
        ; do
        try_graalvm_home "$candidate" 2>/dev/null && break || true
    done
fi

# Try SDKMAN
if ! command -v native-image &>/dev/null && [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
    # shellcheck disable=SC1090
    source "$HOME/.sdkman/bin/sdkman-init.sh" 2>/dev/null || true
fi

if ! command -v native-image &>/dev/null; then
    error "native-image not found. Install GraalVM JDK 21:\n\
  macOS:  brew install --cask graalvm-jdk@21\n\
          export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home\n\
          export PATH=\$JAVA_HOME/bin:\$PATH\n\
  Linux:  https://www.graalvm.org/downloads/"
fi

info "native-image : $(native-image --version)"

# --- Step 2: Build fat JAR ---------------------------------------------------
if [[ "$SKIP_JAVA" == false ]]; then
    info "Building MaxiCP fat JAR (approx. 30 s)..."
    cd "$REPO_ROOT"
    mvn -q package -Pfat-jar \
        -DskipTests \
        -Dmaven.javadoc.skip=true \
        -Dgpg.skip=true \
        -Dmaven.source.skip=true
    info "Fat JAR built."
fi

FAT_JAR="$REPO_ROOT/target/maxicp-native-jar-with-dependencies.jar"
if [[ ! -f "$FAT_JAR" ]]; then
    FAT_JAR="$(ls "$REPO_ROOT/target/"*"-jar-with-dependencies.jar" 2>/dev/null | head -1 || true)"
fi
[[ -n "$FAT_JAR" && -f "$FAT_JAR" ]] || \
    error "Fat JAR not found in target/. Run without --skip-java to rebuild it."
info "Fat JAR : $FAT_JAR"

# --- Step 3: native-image --shared -------------------------------------------
if [[ "$SKIP_NATIVE" == false ]]; then
    info "Running native-image --shared (1-3 min on first run)..."
    REFLECT_CFG="$REPO_ROOT/src/main/resources/META-INF/native-image/org.maxicp/maxicp"
    cd "$REPO_ROOT/target"

    native-image \
        --shared \
        --no-fallback \
        -H:Name=libmaxicp \
        -H:+ReportExceptionStackTraces \
        -H:ConfigurationFileDirectories="$REFLECT_CFG" \
        -cp "$FAT_JAR"

    info "native-image build complete."
else
    info "Skipping native-image build."
fi

# --- Step 4: Copy artefacts --------------------------------------------------
info "Copying artefacts to $LIB_OUT ..."
cd "$REPO_ROOT/target"

LIB_COPIED=false
for ext in dylib so dll; do
    if [[ -f "libmaxicp.$ext" ]]; then
        cp "libmaxicp.$ext" "$LIB_OUT/"
        info "  Copied libmaxicp.$ext"
        LIB_COPIED=true
    fi
done

for hdr in graal_isolate.h libmaxicp_dynamic.h libmaxicp.h; do
    [[ -f "$hdr" ]] && cp "$hdr" "$LIB_OUT/" && info "  Copied $hdr"
done

if [[ "$LIB_COPIED" == false && "$SKIP_NATIVE" == false ]]; then
    warn "No libmaxicp.{dylib,so,dll} found in target/ – native build may have failed."
fi

# --- Step 5: pip install -----------------------------------------------------
if [[ "$DO_INSTALL" == true ]]; then
    # Use the project-level myvenv at the repo root
    VENV="$REPO_ROOT/myvenv"
    if [[ -x "$VENV/bin/pip" ]]; then
        PIP="$VENV/bin/pip"
        PYTHON="$VENV/bin/python"
        info "Using venv at $VENV"
    else
        # Fallback: create the venv, then install
        info "Creating virtual environment at $VENV ..."
        python3 -m venv "$VENV"
        PIP="$VENV/bin/pip"
        PYTHON="$VENV/bin/python"
    fi
    info "Installing maxicp Python package (editable)..."
    "$PIP" install -q -e "$PB_DIR"
    info "  Installed."
    "$PYTHON" -c "import maxicp; print('  maxicp version:', maxicp.__version__)"
fi

echo ""
info "========================================================"
info "Build complete!"
if [[ "$DO_INSTALL" == false ]]; then
    echo ""
    info "Install the Python package into myvenv with:"
    info "  $REPO_ROOT/myvenv/bin/pip install $PB_DIR"
fi
echo ""
info "Activate the venv and try it:"
info "  source $REPO_ROOT/myvenv/bin/activate"
info "  python -m maxicp.examples.nqueens 8"
info "  python -m maxicp.examples.magic_square 3"
info "  python -m maxicp.examples.qap"
info "========================================================"
