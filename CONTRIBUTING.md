# Contributing to gkit-java

Thank you for considering a contribution to **gkit-java**!

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Running Tests](#running-tests)
- [Commit Messages](#commit-messages)
- [Pull Request Process](#pull-request-process)
- [Package Design Guidelines](#package-design-guidelines)

---

## Code of Conduct

Be kind and respectful. We follow the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).

---

## Getting Started

1. **Fork** the repo and clone your fork:
   ```bash
   git clone https://github.com/<your-handle>/gkit-java.git
   cd gkit-java
   ```
2. Add the upstream remote:
   ```bash
   git remote add upstream https://github.com/milad-ahmd/gkit-java.git
   ```
3. Create a feature branch:
   ```bash
   git checkout -b feat/my-feature
   ```

---

## Development Setup

**Prerequisites:**

| Tool | Version |
|---|---|
| JDK | 21 (Temurin recommended) |
| Maven | ≥ 3.9 |
| Docker | ≥ 24 (for integration tests) |

```bash
mvn dependency:resolve
```

---

## Running Tests

```bash
# Unit tests
mvn test

# Unit tests + integration tests (requires Docker)
mvn verify -P integration

# Single class
mvn test -Dtest=RetryTest

# Skip tests (build only)
mvn package -DskipTests

# Checkstyle
mvn checkstyle:check
```

---

## Commit Messages

We use **Conventional Commits**:

```
feat(retry): add jitter backoff strategy
fix(cache): fix TTL not applied on replace
test(pool): add drain under-load test
docs(readme): update auth usage example
chore(pom): bump spring-boot to 3.2.6
```

Rules:
- Use the class/package name as scope: `feat(ratelimit):`, `fix(saga):`
- Imperative mood: "add", "fix", "remove"
- Subject line ≤ 72 characters

---

## Pull Request Process

1. `mvn verify` must pass (unit + integration)
2. `mvn checkstyle:check` must pass
3. Add or update tests for changed behaviour
4. Update Javadoc if the public API changes
5. Fill in the PR template
6. Request review from `@milad-ahmd`

New packages must include:
- Class with full Javadoc
- Unit tests with ≥ 80% line coverage
- Entry in `README.md`

---

## Package Design Guidelines

- **Immutable config** — use builder pattern; no public setters
- **Virtual threads** — prefer `Executors.newVirtualThreadPerTaskExecutor()` for blocking work
- **Records** — use Java records for value types (config, results, events)
- **No Spring required** — packages must work without a Spring application context
- **Error wrapping** — always wrap with meaningful context: `throw new XException("pkg: ...", cause)`
- **Null safety** — `Objects.requireNonNull` on every public method parameter
