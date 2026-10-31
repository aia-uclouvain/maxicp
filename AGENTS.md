# AGENTS.md

Guidance for coding agents contributing to this repository.

## Project Overview

This is a Java-based constraint programming solver and modeling framework.
The solver is trail-based, supports backtracking search, and is designed to be extensible for various constraint types and search strategies.
The modeling API allows users to define variables, constraints, and objectives in a high-level way, while the raw API provides lower-level access for performance-critical code or custom constraints.
The solver also includes integration with the XCSP3 format for benchmarking and competition purposes.

## Scope and goals

- Optimize for correctness first, then code readability and maintainability, then performance.

## Repository map

- `src/main/java/org/maxicp/`: core solver, modeling API, search, XCSP3 integration.
- `src/main/java/org/maxicp/cp/examples/modeling/`: contains the examples using the modeling API.
- `src/main/java/org/maxicp/cp/examples/raw/`: contains the examples using the raw API.
- `src/test/`: JUnit test suite. It is important to test that all feasible solutions are well discovered by the search, that the constraint does not remove any feasible solution, that it achieves the expected level of consistency.
- `data/`: benchmark and example instances (including XCSP3 MiniCSP/MiniCOP datasets).
- `scripts/`: utility scripts, including competition packaging.
- `userguide/`: Sphinx documentation.
- `tech-report/`: Latex-pdf documentation, less details than in the userguide, more formal, showing the scientific innovation and key functionnalities of the solver.

## Environment requirements

- Java 21 (see `pom.xml` and workflow matrix).
- Maven 3.8+.
- Python 3.8+ only when working on `userguide/`.

## Build and test commands

Run from repository root.

```bash
mvn compile
mvn test
```

For a faster package build (skip tests):

```bash
mvn -Dmaven.test.skip=true package
```

For XCSP3 mini bundle packaging:

```bash
./scripts/package-mini-xcsp3.sh
```

## XCSP3 / competition workflow

- Main XCSP3 implementation is in `src/main/java/org/maxicp/modeling/xcsp3/`.
- Mini-track CLI entrypoint is `org.maxicp.modeling.xcsp3.XCSP3Cli`.
- Competition bundle is produced in `mini-xcsp3/` by `scripts/package-mini-xcsp3.sh`.
- Keep output protocol compatibility (`s`, `o`, `v` lines) when editing the CLI.

## Coding guidelines

- Follow existing Java style and naming conventions in nearby files.
- Prefer small methods and explicit exceptions over broad catch blocks.
- Keep comments short and only for non-obvious logic.
- Update or add tests for behavior changes in solver/modeling code.
- Keep unit tests fast (each test should take on the order of seconds at most) so that the full test suite runs quickly.
- Do not produce console output (`System.out` / `System.err`) or logs from source code or tests when running the test suite.
- Do not introduce new dependencies unless required by the task.
- Avoid cyclic dependencies between packages; maintain a clear, acyclic package dependency order.
- For the same problem, keep the raw (`src/main/java/org/maxicp/cp/examples/raw/`) and modeling (`src/main/java/org/maxicp/cp/examples/modeling/`) examples as similar as possible across the two paradigms (data loading, variables, constraints, search strategy, and output).
- When adding, moving, or removing example models in `src/main/java/org/maxicp/cp/examples/`, always update the list in `userguide/source/learning_maxicp/allmodels.rst` and run `python3 userguide/check_source_links.py` to ensure no links are broken.

## Files and paths to treat as generated/artifacts

Avoid direct edits unless the task explicitly asks for it:

- `target/**`
- `mini-xcsp3/**` (except when validating packaging output)
- `userguide/build/**`
- `userguide/myvenv/**`

## Change safety checklist

Before finishing, ensure:

- The project still compiles (`mvn compile`).
- Always run the full test suite (`mvn test`) at the end of a task and make sure the entire test suite passes quickly and without console output/logs from source or test code.
- Documentation/script updates are included when behavior or developer workflow changes. In particular, update `README.md`, `tech-report/maxicp.tex`, and `userguide/` when necessary to reflect new features, API changes, or updated examples.
- If `agents.local.md` exists in the repository root, read and follow its local instructions.

## CI alignment

- CI runs Java 21 test/coverage workflow on pushes to `main`.
- User guide build workflow publishes docs from `userguide/`.
- Tech report workflow publishes PDF from `tech-report/`.
- Keep local changes consistent with these workflows when touching related areas.

