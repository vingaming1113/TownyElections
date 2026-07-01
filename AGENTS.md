# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository.

## Project overview

**TownyElections** is a Paper 1.21.4+ Minecraft plugin that adds a formal,
configurable election system to towns managed by the **Towny** plugin. Residents
run for office, publish a campaign message, and townsfolk vote. On conclusion the
winner is automatically granted configurable Towny town ranks and/or mayorship.

- Language: **Java 21** (`maven.compiler.release = 21`).
- Build tool: **Maven** (shade plugin produces a single jar).
- Target: **Paper 1.21.4+**, **Towny** (hard dependency), **PlaceholderAPI** (optional).

## Build & verify

```bash
mvn clean package          # produces target/TownyElections-1.0.0.jar
```

There is **no local test suite**; correctness is verified by:
1. A successful `mvn clean package` (compilation is the primary gate).
2. The GitHub Actions **Build** workflow (`.github/workflows/build.yml`) on every push.

Always ensure the project compiles before committing. If you cannot run Maven
locally, say so explicitly rather than claiming the build passes.

## Dependency notes (important)

- **Towny is pulled from JitPack.** JitPack builds Towny as a *multi-module*
  project, so the resolvable coordinate is the submodule form:
  `com.github.TownyAdvanced.Towny:towny:<version>` (groupId is `com.github.<User>.<Repo>`,
  artifactId is the module `towny`).
- Not every Towny version is built on JitPack. Before bumping `towny.version` in
  `pom.xml`, confirm the version resolves — check
  `https://jitpack.io/api/builds/com.github.TownyAdvanced/Towny/latestOk`.
- The Towny API changes between minor versions. **Do not assume method signatures.**
  Verify against the Towny javadocs (`https://townyadvanced.github.io/Towny/javadoc/release/`)
  for the exact version in `pom.xml` before using an API. A common gotcha:
  `Town.setMayor(Resident)` does **not** declare a checked exception in 0.102.x,
  so do not `catch` a checked `TownyException` around it.

## Architecture

All code lives under `src/main/java/com/townyelections/`:

| Package        | Responsibility |
|----------------|----------------|
| (root)         | `TownyElections` — plugin entry point; wires managers, registers command/listeners, runs the tick task. |
| `model`        | Plain data + serialization: `Election`, `Candidate`, `ElectionResult`, `ElectionPhase`, `TieBreaker`. |
| `manager`      | `ElectionManager` (state machine, persistence, rewards), `ConfigManager`, `CommandConfig`, `MessageManager`, `OperationResult`. |
| `commands`     | `ElectionCommand` — single dispatcher + tab completer for `/election`. |
| `listeners`    | `PlayerListener` — join notifications, resident-leave cleanup. |
| `integration`  | `TownyHook` (all Towny API access is isolated here), `ElectionsPlaceholderExpansion`. |
| `util`         | `DurationUtil`, `TextUtil` (colour/component helpers). |

Resources: `src/main/resources/` holds `plugin.yml`, `config.yml`, `messages_en.yml`.

### Key design rules

- **All Towny API calls go through `integration/TownyHook`.** Do not call Towny
  APIs directly from managers/commands/listeners. This isolates upstream breakage
  to one file.
- **All player-facing strings come from `messages_*.yml`** via `MessageManager`.
  Never hardcode user-facing text; add a key and reference it.
- **Sub-command literals are configurable** (`config.yml` `commands:` section →
  `CommandConfig`). When adding a command, add its action key to `CommandConfig`,
  a default literal in `config.yml`, and help/usage strings in the messages file.
- **State is mutated on the main thread.** `TownRemoveResidentEvent` may fire async;
  hop back to the main thread (see `PlayerListener`) before touching election state.
- **Persistence:** active elections and results are saved to `data.yml` by
  `ElectionManager.save()`; call it after any state mutation that must survive
  restarts. New model fields must be added to the `serialize`/`deserialize` methods.
- **The state machine ticks once per second** (`ElectionManager.tick()`), driven by
  a repeating scheduler task in the main class.

## Conventions

- Java 21 features are fine (records, switch expressions, pattern matching).
- 4-space indentation, no wildcard imports, keep imports tidy (unused imports are
  a warning and should be removed).
- Prefer small, focused methods; keep `TownyHook` defensive (null-checks, logs).
- Durations in config/messages use friendly strings (`2d`, `10m`, `1w3d12h`) parsed
  by `DurationUtil`.
- Colour codes: legacy `&` and hex `&#RRGGBB`, rendered via `TextUtil.colorize`.

## Git / PR etiquette

- Only commit/push when explicitly asked.
- Keep commits focused with clear messages describing the *why*.
- The GitHub token in CI may lack the `workflow` scope; edits to
  `.github/workflows/**` can be rejected on push. Mention this if a push fails for
  that reason.
- Do not commit build output (`target/`) — it is gitignored.

## Common tasks

- **Add a config option:** add it to `config.yml` (with a comment), read it in
  `ConfigManager.load()` with a sensible default, expose a getter, and use it.
- **Add a message:** add the key to `messages_en.yml`, reference via
  `MessageManager` (`send`/`sendNoPrefix`/`raw`), with placeholder substitution.
- **Add a placeholder:** extend `ElectionsPlaceholderExpansion.onRequest` and
  document it in `README.md`.
- **Bump Towny:** update `towny.version`, confirm it builds on JitPack, and
  re-verify any changed API signatures.
