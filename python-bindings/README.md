# MaxiCP Python Bindings

Python bindings for the [MaxiCP](https://github.com/aia-uclouvain/maxicp) constraint-programming solver, built via GraalVM native-image.

## Architecture

```
MaxiCP Java model  ──►  GraalVM native-image --shared  ──►  libmaxicp.{dylib,so}
                                                                     │
                                                              cffi.dlopen()
                                                                     │
                                                           maxicp Python package
```

* **`HandleRegistry.java`** – thread-safe opaque-handle registry; all Java objects (variables, constraints, search contexts) are stored here and referenced by `long` IDs across the FFI boundary.
* **`MaxiCPNativeAPI.java`** – `@CEntryPoint`-annotated static methods; these become the exported C symbols in the shared library.
* **`_lib/__init__.py`** – cffi ABI-mode loader; creates a GraalVM isolate at import time and exposes `lib`, `ffi`, `thread`.
* **`model.py`** – `Model` class (context manager); high-level entry point.
* **`variables.py`** – `IntVar`, `BoolVar`, `IntExpression`, `BoolExpression` with operator overloads.
* **`expressions.py`** – `sum_expr`, `element1d`, `element2d`.
* **`constraints.py`** – `allDifferent`, `circuit`, `table`, `cumulative`, `disjunctive`.
* **`search.py`** – `SolveResult`, `Solution`, `SearchStats`.

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| GraalVM JDK | 21+ | `brew install --cask graalvm-jdk@21` |
| Maven | 3.8+ | `brew install maven` |
| Python | 3.9+ | pre-installed on macOS / `apt install python3` |
| cffi | ≥ 1.15 | installed automatically by pip |

> **macOS quick-start for GraalVM:**
> ```bash
> brew install --cask graalvm-jdk@21
> export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-21.jdk/Contents/Home
> export PATH=$JAVA_HOME/bin:$PATH
> ```
> Add the two `export` lines to your `~/.zshrc` or `~/.bashrc` to make them permanent.

---

## Installation (one command)

Run from the **repo root**:

```bash
./python-bindings/build.sh --install
```

This script (re-runnable at any time):

1. **Detects GraalVM** — auto-discovers `/Library/Java/JavaVirtualMachines/graalvm-21.jdk` and similar locations; no manual `export` needed once installed.
2. **Builds a fat JAR** — `mvn package -Pfat-jar -DskipTests` (~30 s).
3. **Runs `native-image --shared`** — compiles `libmaxicp.dylib` / `libmaxicp.so` (~2 min first time, faster on rebuilds).
4. **Copies the library** into `python-bindings/python/maxicp/_lib/`.
5. **Installs the Python package** in editable mode into the **`myvenv`** virtual environment at the repo root.

### Skip individual steps on re-runs

```bash
# Rebuild only the native library (Java source changed):
./python-bindings/build.sh --skip-java --install

# Re-install Python package only (Python source changed):
./python-bindings/build.sh --skip-java --skip-native --install

# Build everything but don't install into myvenv:
./python-bindings/build.sh
```

### Manual build (step by step)

```bash
# 1. Build fat JAR
cd <repo_root>
mvn package -Pfat-jar -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true

# 2. Build shared library (from target/)
cd target/
native-image --shared --no-fallback -H:Name=libmaxicp \
    -H:ConfigurationFileDirectories=../src/main/resources/META-INF/native-image/org.maxicp/maxicp \
    -cp maxicp-native-jar-with-dependencies.jar

# 3. Copy library and install
cp libmaxicp.dylib ../python-bindings/python/maxicp/_lib/
../myvenv/bin/pip install -e ../python-bindings/
```

---

## Usage from the command line

```bash
# Activate the venv first
source myvenv/bin/activate

# Run examples
python -m maxicp.examples.nqueens 8
python -m maxicp.examples.magic_square 3
python -m maxicp.examples.qap
```

Or without activating:

```bash
myvenv/bin/python -m maxicp.examples.nqueens 8
```

---

## Usage from a Jupyter Notebook

### One-time kernel setup

After running `build.sh --install`, register the `myvenv` as a Jupyter kernel:

```bash
# Install Jupyter into myvenv (if not already there)
myvenv/bin/pip install jupyter ipykernel

# Register the kernel (runs once; survives across terminal sessions)
myvenv/bin/python -m ipykernel install --user \
    --name maxicp \
    --display-name "MaxiCP (Python)"
```

### Launch Jupyter

```bash
# Option A – use the myvenv's own Jupyter:
myvenv/bin/jupyter notebook python-bindings/maxicp_demo.ipynb

# Option B – use any Jupyter installation; just select the "MaxiCP (Python)" kernel
jupyter notebook python-bindings/maxicp_demo.ipynb
```

When the notebook opens, select **Kernel → Change Kernel → MaxiCP (Python)**.

### Minimal notebook example

```python
from maxicp import Model, allDifferent

with Model() as m:
    q = m.int_var_array(8, 0, 7)          # 8 queens, columns 0-7
    m.add(allDifferent(q))
    m.add(allDifferent([q[i] - i for i in range(8)]))   # no shared diagonal
    m.add(allDifferent([q[i] + i for i in range(8)]))

    result = m.solve(decision_vars=q, strategy="first_fail", max_solutions=1)
    sol = result.optimal
    cols = [sol[q[r]] for r in range(8)]

print("Solution:", cols)
for r in range(8):
    print("".join("Q" if c == cols[r] else "." for c in range(8)))
```

A fully worked demo notebook with N-Queens, Magic Square, QAP optimisation, and a scheduling example is at **`python-bindings/maxicp_demo.ipynb`**.

---

## GraalVM tracing agent (recommended for production)

To auto-generate a complete `reflect-config.json` that covers all internal MaxiCP reflection:

```bash
JAVA_OPTS="-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/org.maxicp/maxicp" \
    mvn test
```

Then re-run `build.sh` to rebuild the native library with the enriched config.

---

## Packaging as a wheel

```bash
myvenv/bin/pip install build
myvenv/bin/python -m build --wheel python-bindings/
```

The `.whl` will contain `maxicp/_lib/libmaxicp.{dylib,so}` as package data. Use platform wheel tags (e.g. `py3-none-macosx_14_0_arm64`) when publishing to PyPI.


## Architecture

```
MaxiCP Java model  ──►  GraalVM native-image --shared  ──►  libmaxicp.{dylib,so}
                                                                     │
                                                              cffi.dlopen()
                                                                     │
                                                           maxicp Python package
```

* **`HandleRegistry.java`** – thread-safe opaque-handle registry; all Java objects (variables, constraints, search contexts) are stored here and referenced by `long` IDs across the FFI boundary.
* **`MaxiCPNativeAPI.java`** – `@CEntryPoint`-annotated static methods; these become the exported C symbols in the shared library.
* **`_lib/__init__.py`** – cffi ABI-mode loader; creates a GraalVM isolate at import time and exposes `lib`, `ffi`, `thread`.
* **`model.py`** – `Model` class (context manager); high-level entry point.
* **`variables.py`** – `IntVar`, `BoolVar`, `IntExpression`, `BoolExpression` with operator overloads.
* **`expressions.py`** – `sum_expr`, `element1d`, `element2d`.
* **`constraints.py`** – `allDifferent`, `circuit`, `table`, `cumulative`, `disjunctive`.
* **`search.py`** – `SolveResult`, `Solution`, `SearchStats`.

## Building

### Prerequisites

| Tool | Version |
|---|---|
| GraalVM JDK | 21+ (with `native-image` component) |
| Maven | 3.8+ |
| Python | 3.9+ |
| cffi | ≥ 1.15 |

### Quick build

```bash
# From the repo root
./python-bindings/build.sh --install
```

This will:
1. Check / install GraalVM via SDKMAN or Homebrew.
2. Build the fat JAR (`mvn package -DskipTests`).
3. Run `native-image --shared` to produce `libmaxicp.dylib` (macOS) or `libmaxicp.so` (Linux).
4. Copy the library into `python/maxicp/_lib/`.
5. Install the Python package in editable mode (`pip install -e python-bindings/`).

### Manual build (step by step)

```bash
# 1. Build fat JAR
cd <repo_root>
mvn package assembly:single -DskipTests -DdescriptorId=jar-with-dependencies -DfinalName=maxicp-native

# 2. Build shared library
cd target/
native-image --shared --no-fallback -H:Name=libmaxicp \
    -H:ConfigurationFileDirectories=../src/main/resources/META-INF/native-image/org.maxicp/maxicp \
    -cp maxicp-native-jar-with-dependencies.jar

# 3. Install Python package
cp libmaxicp.dylib ../python-bindings/python/maxicp/_lib/
pip install ../python-bindings/
```

### GraalVM tracing agent (recommended for production)

To generate a complete `reflect-config.json` that covers all internal MaxiCP reflection:

```bash
# Run the test suite with the native-image tracing agent attached
JAVA_OPTS="-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/org.maxicp/maxicp" \
    mvn test
```

Then rebuild the native library. The agent automatically discovers all reflective accesses.

## Usage

```python
from maxicp import Model, allDifferent, sum_expr

# 8-Queens
with Model() as m:
    q = m.int_var_array(8, 0, 7)
    m.add(allDifferent(q))
    m.add(allDifferent([q[i] - i for i in range(8)]))
    m.add(allDifferent([q[i] + i for i in range(8)]))
    result = m.solve(decision_vars=q, strategy="first_fail", max_solutions=1)
    print([result.optimal[q[i]] for i in range(8)])

# Magic square (optimization via branch-and-bound)
with Model() as m:
    x = [[m.int_var(1, 9) for _ in range(3)] for _ in range(3)]
    flat = [x[i][j] for i in range(3) for j in range(3)]
    m.add(allDifferent(flat))
    magic = 15
    for i in range(3):
        m.add(sum_expr(x[i]) == magic)
    # ... column & diagonal constraints ...
    result = m.solve(decision_vars=flat, max_solutions=1)
```

## Running examples

```bash
python -m maxicp.examples.nqueens 8
python -m maxicp.examples.magic_square 3
python -m maxicp.examples.qap
```

## Packaging as a wheel

```bash
pip install build
python -m build --wheel python-bindings/
```

The `.whl` file will contain `maxicp/_lib/libmaxicp.{dylib,so}` as package data. Use platform wheel tags (e.g. `py3-none-macosx_14_0_arm64`) when publishing to PyPI.

