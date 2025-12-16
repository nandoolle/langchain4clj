# 🔧 Scripts Directory

This directory contains utility scripts used during development and testing of langchain4clj.

## 📁 Directory Structure

```
scripts/
├── dev/          # Development and API discovery scripts
└── test/         # Testing utilities and runners
```

---

## 🔬 Development Scripts (`dev/`)

Scripts for exploring the LangChain4j Java API and discovering functionality.

### API Discovery
- `discover-api.clj` - General API exploration
- `discover-jsonschema-api.clj` - JSON Schema API discovery

### Component Finders
- `find-responseformat.clj` - Locate ResponseFormat classes
- `find-chatrequest.clj` - Locate ChatRequest classes
- `find-jsonschema.clj` - Locate JSON Schema classes

### Inspection Scripts
- `inspect-toolspec.clj` - Inspect ToolSpecification API
- `inspect-parameters.clj` - Inspect parameter handling
- `inspect-toolexecrequest.clj` - Inspect ToolExecutionRequest
- `inspect-toolexecrequest-builder.clj` - Inspect ToolExecutionRequest builder
- `inspect-chatmodel.clj` - Inspect ChatModel interface
- `inspect-chatmodel-methods.clj` - Inspect ChatModel methods

### Testing & Syntax
- `test-imports.clj` - Test Java imports
- `test-syntax.clj` - Test Clojure syntax patterns
- `test-syntax2.clj` - Additional syntax tests

### Shell Scripts
- `check-jar-classes.sh` - Verify classes in JAR files

---

## 🧪 Test Scripts (`test/`)

Scripts for running tests and analyzing results.

### Test Runners
- `test-runner.clj` - Main test runner
- `minimal-test.clj` - Minimal test configuration
- `run-tests.sh` - Shell script for running tests
- `run-all-tests-manual.sh` - Manual test execution
- `quick-test.sh` - Fast test subset

### Analysis
- `analyze-test-results.clj` - Parse and analyze test output

### Documentation
- `scripts-README.md` - Original scripts documentation (legacy)

---

## 🚀 Usage

### Running Development Scripts

```bash
# From project root
clojure -M:dev scripts/dev/discover-api.clj

# Or with specific Clojure options
clojure -Sforce -M:dev scripts/dev/inspect-toolspec.clj
```

### Running Tests

```bash
# Using test runners
./scripts/test/run-tests.sh

# Quick test subset
./scripts/test/quick-test.sh

# Full manual test run
./scripts/test/run-all-tests-manual.sh
```

---

## 📝 Notes

- **Development scripts** are typically run interactively in a REPL
- **Test scripts** can be run from the command line
- All scripts assume you're running from the project root directory
- Some scripts may require specific dependencies from `deps.edn` aliases

---

**Last Updated**: December 2025
