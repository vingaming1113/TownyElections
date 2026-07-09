# 🗳️ TownyElections

[![Build](https://github.com/vingaming1113/TownyElections/actions/workflows/build.yml/badge.svg)](https://github.com/vingaming1113/TownyElections/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/vingaming1113/TownyElections?include_prereleases&label=release&logo=github)](https://github.com/vingaming1113/TownyElections/releases)
[![Downloads](https://img.shields.io/github/downloads/vingaming1113/TownyElections/total?label=downloads&logo=github)](https://github.com/vingaming1113/TownyElections/releases)
[![Contributors](https://img.shields.io/github/contributors/vingaming1113/TownyElections?label=contributors&logo=github)](https://github.com/vingaming1113/TownyElections/graphs/contributors)
[![Last commit](https://img.shields.io/github/last-commit/vingaming1113/TownyElections?logo=git)](https://github.com/vingaming1113/TownyElections/commits/main)
[![Issues](https://img.shields.io/github/issues/vingaming1113/TownyElections?logo=github)](https://github.com/vingaming1113/TownyElections/issues)
[![bStats servers](https://img.shields.io/bstats/servers/32328?label=servers&logo=googleanalytics&logoColor=white)](https://bstats.org/plugin/bukkit/TownyElection/32328)
[![Java 21](https://img.shields.io/badge/Java-21-f89820?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Paper 1.21.4+](https://img.shields.io/badge/Paper-1.21.4%2B-34d399)](https://papermc.io/)
[![Towny](https://img.shields.io/badge/Towny-required-8b5cf6)](https://github.com/TownyAdvanced/Towny)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A **Paper 1.21.4+** plugin that adds a formal, fully-configurable **election system** 🏛️
to towns managed by the [Towny](https://github.com/TownyAdvanced/Towny) plugin.

Residents run for office 👥, publish a campaign message 📢, and their fellow townsfolk vote 🗳️. When the election concludes, the winner is automatically granted the Towny town ranks (plot management, etc.) and/or mayorship you configure 👑.

**Links:** [Releases](https://github.com/vingaming1113/TownyElections/releases)
| [Issues](https://github.com/vingaming1113/TownyElections/issues)
| [bStats](https://bstats.org/plugin/bukkit/TownyElection/32328)
| [Towny](https://github.com/TownyAdvanced/Towny)

---

## ✨ Features

- **🔄 Structured election lifecycle** — Nomination → Voting → (optional Runoff) → Concluded.
- **👤 Candidacy** — residents register with `/election run` and set a custom campaign message shown to voters.
- **🎭 Political parties** — candidates can organize under configurable party labels, voters can inspect party standings with `/election parties`, and results include party-level vote totals alongside individual winners.
- **📦 Inventory GUI** — players can run `/election` with no arguments to open a click-driven election desk, inspect candidates, and vote from player-head icons.
- **🗳️ Voting** — one command to cast (and optionally change) a vote, with eligibility restricted to town residents. Supports secret ballots (hidden tallies).
- **📊 Three electoral systems** — classic **plurality**, **ranked-choice** (instant-runoff with automatic elimination rounds and round-by-round results), and **approval voting**, selected with `election.voting-system`. Ballots, commands, tab completion, the GUI, results, and persistence all adapt to the chosen system.
- **🏆 Automatic winner rewards** — grants configurable Towny **town ranks**, optionally transfers **mayorship**, runs custom console commands, and can revoke the previous holder's ranks.
- **⚙️ Highly configurable** — durations, minimum/maximum candidates, tie-breaking strategy, campaign rules, notifications, and **renamable sub-commands**.
- **🎲 Tie-breakers** — `RANDOM`, `EARLIEST`, `INCUMBENT`, `RUNOFF`, or `NONE`.
- **⏰ Auto-scheduling** — optionally run recurring elections in every eligible town.
- **💾 Persistence** — active elections and results survive restarts (`data.yml`).
- **🔌 Integrations** — optional **PlaceholderAPI** placeholders and **bStats** metrics.
- **🌍 Localised, colourised messages** — legacy `&` and hex `&#RRGGBB` colours.

---

## 📊 Project stats

These images update automatically from GitHub and bStats.

<p align="center">
  <a href="https://github.com/vingaming1113/TownyElections/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=vingaming1113/TownyElections&max=1000" alt="TownyElections contributors" />
  </a>
</p>

<p align="center">
  <a href="https://www.star-history.com/#vingaming1113/TownyElections&Date">
    <img src="https://api.star-history.com/svg?repos=vingaming1113/TownyElections&type=Date" alt="TownyElections star history chart" />
  </a>
</p>

![bStats](https://bstats.org/signatures/bukkit/TownyElection.svg)

---

## 📋 Requirements

| Dependency      | Version              | Required |
|-----------------|----------------------|----------|
| 📦 Paper           | 1.21.4 or newer      | Yes      |
| ☕ Java            | 21+                  | Yes      |
| 🏘️ Towny           | 0.100.0.0+ (0.103 recommended) | Yes |
| 🔌 PlaceholderAPI  | any recent           | Optional |

---

## 🚀 Installation

1. 📥 Download the latest jar from the [releases page](https://github.com/vingaming1113/TownyElections/releases).
2. 📂 Place it in the server `plugins/` folder.
3. ✅ Make sure [Towny](https://github.com/TownyAdvanced/Towny) is installed.
4. 🔄 Restart the server, then edit `plugins/TownyElections/config.yml`.
5. ⚙️ Run `/election reload` after config or message changes.

🔌 PlaceholderAPI is optional. If it is present, TownyElections registers the placeholders listed below.

---

## 🛠️ Building

```bash
mvn clean package
```

The shaded jar is produced at `target/TownyElections-1.1.0.jar`. Drop it into your server's `plugins/` folder alongside Towny.

> 📚 The build depends on the Paper API, Towny (via JitPack), and PlaceholderAPI repositories, all declared in `pom.xml`.

---

## 🎮 Commands

The root command is `/election` (aliases: `/elec`, `/te`). Every sub-command literal below is **configurable** in `config.yml` under the `commands:` section.

| Command | Permission | Description |
|---------|------------|-------------|
| `/election` 🎪 | Town resident | Open the election desk GUI with all options |
| `/election run` 🏃 | `townyelections.candidate`| Register as a candidate in the election |
| `/election withdraw` 🚪 | `townyelections.candidate`| Leave the race and stop being a candidate |
| `/election campaign <message>` 📢 | `townyelections.candidate`| Set your campaign message for voters to read |
| `/election profile <profile>` 📝 | `townyelections.candidate`| Write your candidate profile or bio |
| `/election party <name>` 🎭 | `townyelections.candidate`| Join or create a political party |
| `/election parties` 🏛️ | `townyelections.info` | View all parties and their vote standings |
| `/election vote <candidate...>` ✅ | `townyelections.vote` | Cast your vote for a candidate |
| `/election status` 📋 | `townyelections.info` | Check current election phase and time remaining |
| `/election candidates` 👥 | `townyelections.info` | List all candidates and their campaigns |
| `/election results` 🏆 | `townyelections.info` | View the final results and winner |
| `/election start [town]` ▶️ | `townyelections.admin` | Start a new election |
| `/election stop [town]` ⏸️ | `townyelections.admin` | End voting early and count the votes |
| `/election cancel [town]` ❌ | `townyelections.admin` | Cancel the election with no winner |
| `/election reload` 🔄 | `townyelections.admin` | Reload configuration and messages |

👤 Admins may target another town by name; otherwise the sender's own town is used.

### 📦 Inventory GUI

Running `/election` as a player opens the election desk for your town. The menu shows:
- 📊 Current election phase and time remaining
- 👥 Number of candidates and votes cast
- 🗳️ Your current vote choice
- 🎯 Quick access to candidate ballot, party standings, and results
- 🛠️ Candidacy tools and admin controls (if you have permission)

💡 The candidate roster uses player heads as buttons:
- **Plurality:** Click to vote for a candidate
- **Ranked Choice:** Each click adds a candidate to your ranked ballot
- **Approval:** Click to approve or disapprove a candidate

🪣 A clear-ballot button lets you reset your choices. Hovering over a candidate head shows their party affiliation, campaign message, profile, and live vote counts (if enabled).

✏️ You can set campaign messages, candidate profiles, and party names from the menu by clicking the item and typing in chat.

🔒 The GUI enforces the same permissions, residency checks, voting phases, and campaign limits as chat commands. Menu interactions are protected so items stay in place.

### 🎭 Political parties

Parties are active-election labels that candidates can join, create, leave, or admin-rename without changing the core election rules.

```text
/election party Reform Coalition      # join or create a party
/election party leave                 # return to the default party
/election parties                     # list current parties and standings
/election party rename Reform Unity   # admin: rename a party in this election
```

📋 Candidate lists and final results show each candidate's party. Party standings show candidate counts, and show vote totals whenever live results are public or after results are recorded.

📊 Example result output:

```text
Party Results - Oakvale
 - Reform Coalition (18 votes, 2 candidates) Alex, Mira
 - Independent (7 votes, 1 candidate) Rowan
```

### 📊 Voting systems

`election.voting-system` in `config.yml` selects how ballots are collected and counted. The system locks in when an election starts, so a config change never re-interprets running ballots.

| System          | Ballot                                   | Count                                   |
|-----------------|------------------------------------------|-----------------------------------------|
| `PLURALITY`     | Choose one candidate                     | Most votes wins (default, classic)       |
| `RANKED_CHOICE` | Rank candidates by preference            | Instant-runoff elimination rounds       |
| `APPROVAL`      | Approve multiple candidates              | Most approvals wins                     |

#### 🔄 Ranked Choice Voting

`/election vote Alex Mira Rowan` ranks Alex first, Mira second, Rowan third. Counting eliminates the weakest candidate each round and transfers their ballots to each voter's next surviving preference until a candidate holds a majority.

📊 Every round (tallies, eliminations, exhausted ballots) is recorded and shown in `/election results`:

```text
Runoff Round 1
   - Alex: 4 votes
   - Mira: 3 votes
   - Rowan: 2 votes
   Eliminated: Rowan
Runoff Round 2
   - Mira: 5 votes
   - Alex: 4 votes
```

#### ✅ Approval Voting

`/election vote Alex Mira` approves both candidates. List as many as you support.

#### 🎮 GUI Interaction

In the GUI, plurality clicks vote directly. Ranked-choice and approval clicks build the ballot:
- Each click adds/approves a candidate
- Clicking again removes them
- 🪣 A bucket button clears the whole ballot
- ✅ Adding is always allowed
- ⚙️ Removing/clearing requires `allow-vote-changes: true`

📈 Live first-preference and approval tallies follow your `public-live-results` setting.

#### 🎲 Tie Resolution

If the final count ends in a dead heat, your configured `tie-breaker` (`RANDOM`, `EARLIEST`, `INCUMBENT`, `RUNOFF`, or `NONE`) resolves it under every system.

### 🔐 Permissions

| Node                        | Default | Grants                               |
|-----------------------------|---------|--------------------------------------|
| `townyelections.candidate`  | true    | Running & managing a campaign        |
| `townyelections.vote`       | true    | Casting votes                        |
| `townyelections.info`       | true    | Viewing status/candidates/results    |
| `townyelections.admin`      | op      | Start/stop/cancel/reload             |
| `townyelections.*`          | op      | Everything                          |

---

## 🏆 Winner rewards

When an election concludes, the winner receives whatever you configure under `winner:` in `config.yml`:

```yaml
winner:
  set-as-mayor: false          # 👑 transfer mayorship to the winner
  grant-town-ranks:            # 📋 Towny ranks from your townyperms.yml
    - "councillor"
    - "helper"
  revoke-previous-winner-ranks: true
  commands-on-win:
    - "lp user {winner} parent addtemp mayor 30d"
  commands-on-loss: []
```

📝 Ranks map to Towny permission nodes defined in **`townyperms.yml`** (where plot-management and role permissions live). Invalid ranks are skipped with a console warning.

🔤 Command placeholders: `{winner}`, `{winner_uuid}`, `{town}`, `{votes}`, `{total_votes}`, `{winner_party}` (and `{loser}`, `{loser_uuid}`, `{loser_party}` for loss commands).

---

## ⚙️ Configuration highlights

⏱️ Durations accept friendly strings like `30s`, `10m`, `2h`, `3d`, `1w`, or combinations such as `1w3d12h`.

📢 Candidates can set:
- 💬 A short campaign message for voters
- 📖 A longer candidate profile/bio
- 🎭 A political party affiliation

```yaml
election:
  nomination-duration: "2d"           # ⏱️ How long candidates can register
  voting-duration: "3d"               # 🗳️ How long voting lasts
  min-candidates: 2                   # 👥 Minimum candidates to start voting
  max-candidates: 0                   # 0 = unlimited
  auto-win-single-candidate: true     # ⚡ Skip voting if only 1 candidate
  allow-vote-changes: true            # 🔄 Let voters change their vote
  public-live-results: false          # 🔒 false = secret ballot
  voting-system: "PLURALITY"          # PLURALITY | RANKED_CHOICE | APPROVAL
  tie-breaker: "INCUMBENT"            # RANDOM | EARLIEST | INCUMBENT | RUNOFF | NONE
  auto-schedule:
    enabled: false                    # 🔄 Automatically run elections
    interval: "30d"                   # ⏰ How often to run them
```

📚 See the generated `config.yml` for the full, commented set of options, and `messages_en.yml` for every editable message.

---

## 🔌 PlaceholderAPI

If PlaceholderAPI is installed, these placeholders are available (identifier `townyelections`), resolved for the viewing player's town:

| Placeholder                         | Description                          |
|-------------------------------------|--------------------------------------|
| `%townyelections_phase%` 📋         | Current phase, or `none`            |
| `%townyelections_voting_system%` 📊 | Active election's system, or `none` |
| `%townyelections_time_left%` ⏱️      | Time until the current phase ends   |
| `%townyelections_candidates%` 👥    | Number of candidates                |
| `%townyelections_votes%` 🗳️         | Number of votes cast                |
| `%townyelections_has_voted%` ✅      | `true`/`false` for the player       |
| `%townyelections_my_party%` 🎭       | Your candidate party, if running    |
| `%townyelections_leading_party%` 🥇 | Leading current-election party      |
| `%townyelections_last_winner%` 🏆   | Name of the last winner in the town |
| `%townyelections_last_winner_party%`🎭| Party of the last winner in town  |

---

## 📖 How it works

1. 👨‍⚖️ An admin (or the auto-scheduler) starts an election in a town.
2. 📢 **Nomination phase**: residents `/election run`, set campaign messages, and choose parties.
3. 🗳️ When nominations end, if there are enough candidates the **voting phase** opens (otherwise the election is cancelled, or a lone candidate may auto-win).
4. 👥 Residents `/election vote <candidate...>` (one name under plurality, several to rank or approve). A reminder is sent to non-voters before voting closes.
5. 🏆 When voting ends:
   - Ballots are counted under the election's voting system (including instant-runoff rounds for ranked choice)
   - Ties are resolved per your strategy
   - The result is recorded
   - Winner's rewards are applied through Towny

💾 State is checked once per second and persisted to `data.yml`, so elections resume correctly across restarts.

---

## 🤝 Contributing

🐛 Bug reports, 💡 feature ideas, and 🔧 pull requests are welcome. Please use the [issue tracker](https://github.com/vingaming1113/TownyElections/issues) and keep changes focused on the existing Paper/Towny workflow.

Before submitting a code change, run:

```bash
mvn clean package
```

---

## 📄 License

Licensed under the [MIT License](LICENSE). Adapt freely for your server.
