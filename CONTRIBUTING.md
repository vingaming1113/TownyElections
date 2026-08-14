# Contributing to TownyElections

Contributions to **TownyElections** whether bug fixes, localized translations, documentation improvements, or new features—are welcome. Every merged pull request adds you to the repository's contributor list on the main README.

---

## Ways to Contribute

You do not need to write Java to contribute to the project. Here are the primary ways to get involved:

- **Translations & Localization:** Translate `messages_en.yml` into another language or refine existing message strings to help localized servers.
- **Documentation:** Improve the `README.md`, clarify command usage, detail configuration options, or fix errors.
- **Bug Reports & Issue Verification:** Submit detailed reports on the [Issue Tracker](https://github.com/vingaming1113/TownyElections/issues) with server logs, Paper/Towny versions, and reproduction steps, or help verify existing open issues.
- **Code & Features:** Fix open bugs, optimize performance, or implement new electoral mechanics in Java.

---

## Build Requirements

Before contributing code, ensure your development environment meets the build requirements:

| Tool | Required Version |
|---|---|
| Java Development Kit (JDK) | 21 or newer |
| Build Tool | Maven 3.8+ |
| Target Server | Paper 1.21.4+ |

Build and verify the shaded jar locally using:

```bash
mvn clean package

```

---

## Pull Request Guidelines & Standards

When opening a Pull Request (PR), adhere to the following guidelines:

* **Atomic PRs:** Keep PRs concise and focused on a single feature, translation, or bug fix. Avoid bundling unrelated changes together.
* **Discuss Major Changes:** Open a [Discussion](https://github.com/vingaming1113/TownyElections/discussions) before implementing major new features (such as new electoral mechanics, storage alterations, or major GUI rewrites) to discuss design before writing code.
* **State & Data Preservation:** Ensure all state changes maintain backwards compatibility with existing YAML storage (`data.yml`, `config.yml`, `messages_en.yml`). Never break active election persistence or reload safety.
* **Java 21 & Paper API Standards:** Write clean Java utilizing modern features where applicable. Keep heavy operations asynchronous (e.g., update checks) and run all Bukkit/Towny state modifications safely on the main thread.
* **Testing:** Test your built `.jar` thoroughly on a local Paper server running Towny before submitting. Verify that commands, GUI interactions, tab completion, and permissions work as expected.

---

## Step-by-Step Guide to Submitting a Pull Request

### 1. Fork the Repository

Navigate to [TownyElections on GitHub](https://www.google.com/search?q=https://github.com/vingaming1113/TownyElections) and click **Fork** in the top-right corner.

### 2. Clone Your Fork

Clone your fork locally:

```bash
git clone [https://github.com/YOUR_USERNAME/TownyElections.git](https://github.com/YOUR_USERNAME/TownyElections.git)
cd TownyElections

```

### 3. Create a Topic Branch

Create a new branch specific to your contribution:

```bash
git checkout -b feature/short-description
# or for translations/docs:
git checkout -b docs/add-danish-translation

```

### 4. Implement and Test

Make your changes. For code contributions, verify the build finishes cleanly:

```bash
mvn clean package

```

Test the compiled output (`target/TownyElections-<version>.jar`) on a local test server.

### 5. Commit and Push

Stage and commit your changes with a clear, descriptive commit message:

```bash
git add .
git commit -m "Add Danish translation for messages_en.yml"
git push origin docs/add-danish-translation

```

### 6. Open the Pull Request

1. Go to the original [TownyElections Repository](https://www.google.com/search?q=https://github.com/vingaming1113/TownyElections).
2. Click **Pull requests** > **New pull request**.
3. Select your fork and topic branch in the comparison dropdown.
4. Fill out the PR title and description explaining what was changed, then submit.
