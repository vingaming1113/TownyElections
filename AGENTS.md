# AGENTS.md

Guidance for AI coding agents and humans working in this repository. Treat this
file as the local operating manual: read it before making changes, keep it up to
date when workflows change, and prefer these project-specific rules over generic
Java or Bukkit advice.

## Project overview

**TownyElections** is a Paper 1.21.4+ Minecraft plugin that adds a formal,
configurable election system to towns managed by the **Towny** plugin. Residents
run for office, publish a campaign message, choose a party label, and vote. When
an election concludes, the winner is automatically granted configurable Towny
town ranks, optional mayorship, and optional command-based rewards.

- Language: **Java 21** (`maven.compiler.release = 21`).
- Build tool: **Maven**.
- Output: shaded jar at `target/TownyElections-1.0.2.jar`.
- Target server: **Paper 1.21.4+**.
- Required runtime dependency: **Towny**.
- Optional runtime dependency: **PlaceholderAPI**.
- Metrics dependency: **bStats**, shaded and relocated into the plugin jar.

The plugin is intentionally small and direct. Prefer focused, readable changes
over broad abstractions.

## First steps for every task

Before editing code:

1. Read the user request carefully and identify the behavior being changed.
2. Check `git status --short` so you know what is already modified.
3. Do not revert or overwrite user changes unless explicitly asked.
4. Inspect the relevant classes and resource files before assuming how the
   feature works.
5. If the task touches Towny, PlaceholderAPI, Paper, Maven dependencies, commands,
   config, messages, persistence, permissions, or release metadata, read the
   corresponding sections below before editing.

Prefer `rg`/`rg --files` for searching. Use targeted file reads rather than
loading unrelated code.

## Build and verify

Primary local verification:

```bash
mvn clean package
```

This must compile the plugin and produce the shaded jar in `target/`. There is no
dedicated local unit test suite, so compilation is the primary local gate.

CI verification:

- GitHub Actions runs the **Build** workflow at `.github/workflows/build.yml`.
- A pushed change should be expected to pass that workflow.

Important verification rules:

- Always run `mvn clean package` before committing or pushing when Maven is
  available.
- If Maven cannot be run locally, say that explicitly in the final response.
  Never claim the build passes without running it.
- If the build fails, fix the issue or report the exact blocker.
- Do not commit `target/` or any generated build output.

## Bug-checking before commit or push

Before any commit or push, do a deliberate bug pass. This is not optional just
because the project compiles.

Use this checklist:

1. Run `git diff --check` to catch whitespace and patch formatting issues.
2. Review `git diff` yourself, file by file, before committing.
3. Run `mvn clean package`.
4. Search for accidental debug output, TODOs introduced by the change, temporary
   code, and hardcoded player-facing strings.
5. Confirm all changed commands, permissions, config keys, messages, placeholders,
   and serialized fields are wired end to end.
6. Check likely null paths and missing-Towny-data paths. Towny lookups can fail
   when a player has no town, a town was deleted, a resident left, or an API call
   returns null.
7. Check restart behavior for stateful changes. Anything that must survive a
   reload or restart must be saved through `ElectionManager.save()` and restored
   by the matching deserialize path.
8. Check async behavior. Do not mutate election state off the main thread.
9. Check permission behavior. User commands should enforce the correct
   `townyelections.*` permission and admin commands should not become available
   to normal players by accident.
10. Check config reload behavior. If a setting can change in `config.yml`, make
    sure `/election reload` refreshes it through the existing managers.
11. Check message placeholders. Every placeholder used in a message should be
    supplied by the sender, and every new message should have a default in
    `messages_en.yml`.
12. Check tab completion for command changes.
13. Check README documentation when public commands, permissions, placeholders,
    configuration, or behavior changes.
14. Check `plugin.yml` when commands, aliases, permissions, plugin dependencies,
    version metadata, or API version expectations change.

When reviewing the diff, ask: "What would break on a real server at startup,
during an election, after a restart, or when a resident/town disappears?"

## Dependency notes

Towny dependency handling is fragile enough to deserve special care.

- **Towny is pulled from JitPack.** JitPack builds Towny as a multi-module
  project, so the resolvable coordinate is the submodule form:
  `com.github.TownyAdvanced.Towny:towny:<version>`.
- The JitPack groupId is `com.github.<User>.<Repo>`, and the artifactId is the
  module name, currently `towny`.
- Not every Towny version is built on JitPack. Before bumping `towny.version` in
  `pom.xml`, confirm the target version resolves.
- Check JitPack for a known good build before changing the version:
  `https://jitpack.io/api/builds/com.github.TownyAdvanced/Towny/latestOk`.
- Towny API signatures change between minor versions. Do not guess method names,
  parameters, exceptions, or nullability.
- Verify against the Towny javadocs for the exact version in `pom.xml`:
  `https://townyadvanced.github.io/Towny/javadoc/release/`.
- Common gotcha: in Towny 0.102.x, `Town.setMayor(Resident)` does **not** declare
  a checked `TownyException`; do not add a checked catch around it.

Dependency scopes matter:

- Paper API is `provided`.
- Towny is `provided`.
- PlaceholderAPI is `provided` and optional at runtime.
- bStats is compiled, shaded, and relocated.

Do not add new dependencies casually. For a plugin, every dependency affects jar
size, classloading, shading, and server compatibility.

## Architecture

All Java code lives under `src/main/java/com/townyelections/`.

| Package | Responsibility |
| --- | --- |
| root | `TownyElections` plugin entry point; wires managers, registers commands/listeners, registers PlaceholderAPI, starts the tick task. |
| `model` | Plain data and serialization: `Election`, `Candidate`, `ElectionResult`, `ElectionPhase`, `TieBreaker`. |
| `manager` | `ElectionManager` state machine, persistence, rewards, plus `ConfigManager`, `CommandConfig`, `MessageManager`, and `OperationResult`. |
| `commands` | `ElectionCommand`, the single dispatcher and tab completer for `/election`. |
| `listeners` | `PlayerListener`, join notifications and resident-leave cleanup. |
| `integration` | `TownyHook` for all Towny API access and `ElectionsPlaceholderExpansion` for PlaceholderAPI. |
| `util` | `DurationUtil` and `TextUtil` helper logic. |

Resources live under `src/main/resources/`.

| File | Purpose |
| --- | --- |
| `plugin.yml` | Bukkit/Paper plugin metadata, commands, aliases, permissions, dependencies. |
| `config.yml` | Default plugin configuration and command literals. |
| `messages_en.yml` | Default player-facing messages. |

Runtime persistence is written to `data.yml` by `ElectionManager`.

## Core design rules

- **All Towny API calls go through `integration/TownyHook`.** Do not call Towny
  APIs directly from managers, commands, listeners, models, or placeholders.
- **All player-facing strings come from `messages_*.yml`.** Do not hardcode
  chat text, command feedback, errors, or status output in Java.
- **Sub-command literals are configurable.** New subcommands need an action key
  in `CommandConfig`, a default literal in `config.yml`, help/usage messages in
  `messages_en.yml`, command dispatch handling, and tab completion.
- **State is mutated on the main thread.** `TownRemoveResidentEvent` may fire
  async; hop back to the main thread before touching election state.
- **Persistence must be explicit.** Call `ElectionManager.save()` after state
  mutations that must survive restarts.
- **Serialization must be symmetric.** New model fields must be added to both
  serialize and deserialize logic with backward-compatible defaults.
- **The state machine ticks once per second.** `ElectionManager.tick()` is driven
  by the repeating scheduler task in the main class.
- **Keep integrations defensive.** Towny and PlaceholderAPI may be absent,
  partially loaded, or return missing data.

## Election state and persistence

Be especially careful with these flows:

- Starting an election.
- Transitioning from nomination to voting.
- Cancelling due to too few candidates.
- Auto-winning with a single candidate.
- Casting or changing a vote.
- Withdrawing a candidate.
- Resident leaving a town.
- Ending voting early.
- Resolving ties.
- Applying winner rewards.
- Recording and displaying the last result.
- Server reload/restart while an election is active.

For every state mutation, decide whether it must survive restart. If yes, save.
For every new persisted field, ensure old `data.yml` files still load safely.
Use sensible defaults when fields are absent.

## Commands

`ElectionCommand` owns dispatch and tab completion for `/election`. When adding
or changing a command:

- Add or update the configurable literal in `config.yml`.
- Add or update the corresponding action key in `CommandConfig`.
- Add help, usage, success, and failure messages to `messages_en.yml`.
- Use `MessageManager` for all output.
- Enforce permissions before doing work.
- Validate sender type. Some commands require a player; others may allow console.
- Resolve the target town consistently. Admin commands may target another town;
  normal player commands should operate on the sender's town.
- Keep tab completion aligned with configured command literals, not hardcoded
  English names.
- Update README command documentation for user-visible behavior.

Avoid duplicating command logic. Prefer small helper methods inside
`ElectionCommand` when a validation path is shared.

## Config

When adding a config option:

1. Add it to `src/main/resources/config.yml` with a useful comment.
2. Load it in `ConfigManager.load()` with a safe default.
3. Expose a getter rather than reading raw config throughout the codebase.
4. Use the getter where behavior is implemented.
5. Confirm `/election reload` updates the value.
6. Mention it in README if server owners need to know about it.

Config must be forgiving. Invalid values should not crash the server where a
clear warning and default can be used instead.

Durations use friendly strings such as `30s`, `10m`, `2h`, `3d`, `1w`, or
`1w3d12h`, parsed by `DurationUtil`.

## Messages and text

When adding or changing user-facing output:

- Add a key to `messages_en.yml`.
- Send it through `MessageManager`.
- Supply all placeholders explicitly.
- Keep placeholder names stable and descriptive.
- Use `TextUtil.colorize` behavior for legacy `&` and hex `&#RRGGBB` colors.
- Do not send raw Java exception messages to players.
- Console logs may be plain English, but player chat must be configurable.

If a message is used in more than one path, make sure the placeholder set works
for all callers.

## Towny integration

`TownyHook` is the integration boundary. Keep it defensive and boring.

Towny-specific work should:

- Live in `TownyHook` unless it is pure plugin-domain logic.
- Treat missing towns, missing residents, deleted towns, offline players, invalid
  ranks, and null returns as expected cases.
- Log useful warnings for server owners when configured Towny ranks or towns are
  invalid.
- Avoid throwing raw Towny exceptions through command or manager layers.
- Be verified against the exact Towny API version in `pom.xml`.

Do not optimize by bypassing `TownyHook`. One file should absorb upstream Towny
breakage.

## PlaceholderAPI integration

PlaceholderAPI is optional. Changes to placeholders should:

- Keep the plugin functional when PlaceholderAPI is not installed.
- Be implemented in `ElectionsPlaceholderExpansion`.
- Resolve values for the viewing player's town unless clearly documented
  otherwise.
- Return stable fallback strings such as `none`, `0`, or `false`.
- Be documented in `README.md`.

## Winner rewards

Winner reward code affects real server permissions and ranks, so be conservative.

Check these cases when changing reward behavior:

- Winner is offline.
- Winner no longer belongs to the town.
- Previous winner no longer belongs to the town.
- Configured rank does not exist in Towny.
- Mayorship transfer is disabled.
- Mayorship transfer is enabled.
- Previous winner rank revocation is enabled and disabled.
- Commands on win/loss contain every documented placeholder.
- A tie or cancelled election should not apply rewards.

Do not silently grant extra power. Permission/rank changes should be exactly what
the config requests.

## Conventions

- Java 21 features are allowed, including records, switch expressions, and
  pattern matching.
- Use 4-space indentation.
- No wildcard imports.
- Remove unused imports.
- Prefer small, focused methods.
- Prefer clear names over comments explaining obvious code.
- Add comments only when they clarify a non-obvious decision or external API
  quirk.
- Keep managers responsible for plugin behavior and models responsible for data.
- Keep resource YAML readable for server owners.
- Preserve existing style unless there is a strong reason to change it.

## Git and PR etiquette

- Only commit or push when explicitly asked.
- Keep commits focused.
- Use commit messages that explain the reason for the change, not just the files
  touched.
- Check `git status --short` before committing.
- Check `git diff --check` before committing.
- Run `mvn clean package` before committing or pushing.
- Mention any verification that could not be run.
- Do not commit build output, local server files, logs, or generated data.
- Do not rewrite history, reset, force-push, or discard changes unless explicitly
  asked.
- The GitHub token in CI may lack the `workflow` scope. Edits to
  `.github/workflows/**` can be rejected on push. Mention this if a push fails
  for that reason.

## Pre-push checklist

Run this checklist immediately before pushing:

```bash
git status --short
git diff --check
mvn clean package
```

Then manually confirm:

- The diff only contains intended files.
- `target/` is not staged.
- No player-facing strings are hardcoded.
- New config keys have defaults and reload behavior.
- New messages exist in `messages_en.yml`.
- New commands are represented in `CommandConfig`, `config.yml`, messages,
  permissions, tab completion, and README.
- New placeholders are documented in README.
- New persisted fields serialize and deserialize safely.
- Towny API usage remains isolated to `TownyHook`.
- Public behavior is documented.

If any item is skipped, call it out clearly.

## Common tasks

### Add a config option

1. Add the default and comment to `config.yml`.
2. Read it in `ConfigManager.load()` with a sensible fallback.
3. Add a getter.
4. Use the getter in the relevant manager/command/listener.
5. Verify reload behavior.
6. Document it if server owners need to configure it.

### Add a message

1. Add a key to `messages_en.yml`.
2. Reference it through `MessageManager`.
3. Pass every placeholder used by the message.
4. Run through success and failure paths so missing placeholders are caught.

### Add a command

1. Add an action key to `CommandConfig`.
2. Add the default literal to `config.yml`.
3. Add permission and usage/help messages.
4. Implement dispatch in `ElectionCommand`.
5. Implement tab completion.
6. Add or update `plugin.yml` permissions if needed.
7. Document the command in README.

### Add a placeholder

1. Extend `ElectionsPlaceholderExpansion.onRequest`.
2. Keep fallback output stable when no town/election/result exists.
3. Document the placeholder in README.

### Add persisted data

1. Add the field to the relevant model.
2. Update serialization.
3. Update deserialization with backward-compatible defaults.
4. Save after mutations.
5. Consider how old `data.yml` files behave.

### Bump Towny

1. Check whether the version resolves on JitPack.
2. Update `towny.version` in `pom.xml`.
3. Verify changed API signatures against the javadocs.
4. Rebuild with `mvn clean package`.
5. Pay special attention to `TownyHook`.

### Update plugin metadata

When changing plugin identity, commands, permissions, dependencies, aliases, or
API expectations, update `plugin.yml` and verify the README still matches.

## Review stance

When asked to review code, prioritize:

1. Real bugs and regressions.
2. Server startup failures.
3. Broken command/config/message wiring.
4. Persistence or reload bugs.
5. Permission mistakes.
6. Async state mutation.
7. Towny API misuse.
8. Missing documentation for public behavior.
9. Missing verification.

Lead with findings and file/line references. If no issues are found, say so and
mention any residual test gaps.

## Final response expectations

When finishing work, report:

- What changed.
- What verification ran, especially `mvn clean package`.
- Any verification that could not be run.
- Any follow-up risk the user should know about.

Keep the final response concise and honest. Do not claim a push, commit, build,
or test happened unless it actually did.
