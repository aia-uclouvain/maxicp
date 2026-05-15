#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT_DIR}/mini-xcsp3"
LIB_DIR="${OUT_DIR}/lib"

rm -rf "${OUT_DIR}"
mkdir -p "${LIB_DIR}"

# Build project and gather runtime dependencies in a competition-ready folder.
mvn -f "${ROOT_DIR}/pom.xml" -Dmaven.test.skip=true package dependency:copy-dependencies \
  -DincludeScope=runtime \
  -DoutputDirectory="${LIB_DIR}"

VERSION="$(mvn -q -f "${ROOT_DIR}/pom.xml" -DforceStdout help:evaluate -Dexpression=project.version | tail -n 1)"
MAIN_JAR="${ROOT_DIR}/target/maxicp-${VERSION}.jar"

cp "${MAIN_JAR}" "${LIB_DIR}/"

cat > "${OUT_DIR}/maxicp-mini" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java ${JAVA_OPTS:-} -cp "${DIR}/lib/*" org.maxicp.modeling.xcsp3.XCSP3Cli "$@"
EOF

chmod +x "${OUT_DIR}/maxicp-mini"

cat > "${OUT_DIR}/README.txt" <<'EOF'
maxicp mini-xcsp3 bundle

Build/runtime requirements:
- Java 21+
- Maven (for rebuilding this bundle)

Suggested command line for XCSP3 competition placeholders (Section 7.1):
DIR/maxicp-mini BENCHNAME RANDOMSEED
or
DIR/maxicp-mini --mem-limit=MEMLIMIT --time-limit=TIMELIMIT --tmpdir=TMPDIR BENCHNAME RANDOMSEED

Build command used:
./scripts/package-mini-xcsp3.sh

Notes:
- Runtime jars are in ./lib
- The launcher accepts extra options and forwards all arguments to the Java runner
- The Java entrypoint is org.maxicp.modeling.xcsp3.XCSP3Cli
EOF

{
  echo
  echo "Runtime libraries (name + version):"
  ls -1 "${LIB_DIR}"/*.jar | xargs -n 1 basename
} >> "${OUT_DIR}/README.txt"

echo "Created ${OUT_DIR}"


