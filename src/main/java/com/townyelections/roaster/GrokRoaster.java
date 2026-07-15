package com.townyelections.roaster;

import com.townyelections.TownyElections;
import com.townyelections.model.Candidate;
import com.townyelections.model.Election;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Calls the <b>xAI Grok API</b> to roast election candidates with genuine AI
 * wit. Candidate data (name, campaign message, party, profile, vote count,
 * rank) is sent as context and Grok returns a savage, Minecraft-themed roast
 * formatted with {@code §} colour codes.
 *
 * <p>If the API key is absent or the call fails, the roaster falls back to
 * procedural template roasts so the command never breaks.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   GrokRoaster roaster = new GrokRoaster(plugin);
 *   roaster.roastAsync(candidate, election, votes, rank, sender,
 *       roast -> sender.sendMessage(roast));
 * }</pre>
 */
public class GrokRoaster {

    /** Roast intensity sent to the model. */
    public enum Intensity { MILD, SPICY, SCORCHED_EARTH }

    // ---------- configurable fields (read from config.yml) ------------------

    private String apiKey;
    private String model;
    private URI endpoint;
    private Duration timeout;
    private Intensity intensity;
    private int maxTokens;

    // ---------- internal ----------------------------------------------------

    private final TownyElections plugin;
    private final HttpClient httpClient;
    private final ProceduralRoaster fallback;

    /** Whether an API key has been provided (set after loadConfig). */
    private boolean apiConfigured;

    // ========================================================================
    //  Construction
    // ========================================================================

    public GrokRoaster(TownyElections plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.fallback = new ProceduralRoaster();
        loadConfig();
    }

    /**
     * Reload API settings from {@code config.yml → roaster:} section.
     * Safe to call multiple times (e.g. on /election reload).
     */
    public void loadConfig() {
        var cfg = plugin.getConfig();
        apiKey = cfg.getString("roaster.api-key", "");
        model  = cfg.getString("roaster.model",  "grok-2-latest");
        String url = cfg.getString("roaster.endpoint", "https://api.x.ai/v1/chat/completions");
        try {
            endpoint = URI.create(url);
        } catch (Exception e) {
            plugin.getLogger().warning("roaster.endpoint is not a valid URL: " + url);
            endpoint = URI.create("https://api.x.ai/v1/chat/completions");
        }
        timeout = Duration.ofSeconds(
                Math.max(5, cfg.getInt("roaster.timeout-seconds", 20)));
        maxTokens = Math.max(128, cfg.getInt("roaster.max-tokens", 500));
        String rawIntensity = cfg.getString("roaster.intensity", "SPICY").toUpperCase();
        try {
            intensity = Intensity.valueOf(rawIntensity);
        } catch (IllegalArgumentException e) {
            intensity = Intensity.SPICY;
        }
        apiConfigured = apiKey != null && !apiKey.isBlank() && !apiKey.equals("YOUR_API_KEY_HERE");
    }

    /** @return true if the xAI API is configured and ready. */
    public boolean isApiConfigured() {
        return apiConfigured;
    }

    // ========================================================================
    //  Async roast (primary path — calls Grok API)
    // ========================================================================

    /**
     * Roast a single candidate asynchronously via the Grok API. The callback
     * fires on the <b>main server thread</b>. If the API is unavailable the
     * fallback procedural roast is returned instead (still via callback).
     *
     * @param candidate the candidate
     * @param election  the election they're in
     * @param voteCount their current votes
     * @param rank      1‑based rank among candidates
     * @param player    the requesting player (for context logging)
     * @param callback  invoked on the main thread with the formatted roast
     */
    public void roastAsync(Candidate candidate, Election election, int voteCount,
                           int rank, Player player, Consumer<String> callback) {
        if (!apiConfigured) {
            callback.accept(fallback.roast(candidate, election, voteCount, rank));
            return;
        }

        String prompt = buildSinglePrompt(candidate, election, voteCount, rank);

        callApi(prompt).thenAccept(rawResponse -> {
            // Back on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                String formatted = formatApiResponse(rawResponse, candidate);
                callback.accept(formatted);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Grok API call failed, falling back to procedural roast", ex);
            Bukkit.getScheduler().runTask(plugin, () ->
                    callback.accept(fallback.roast(candidate, election, voteCount, rank)));
            return null;
        });
    }

    /**
     * Roast all candidates in an election via a single API call. Returns a
     * combined roast string via callback on the main thread.
     */
    public void roastAllAsync(Election election, Player player, Consumer<String> callback) {
        if (!apiConfigured) {
            callback.accept(fallback.roastAll(election));
            return;
        }

        Map<UUID, Integer> tally = election.tally();
        List<UUID> ranked = election.rankedCandidates();
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are Grok, a brutally honest AI election commentator for a Minecraft server. ")
              .append("Roast EVERY candidate in the election below. ")
              .append("For each candidate, include a savage opener, roast their campaign message, party, profile, ")
              .append("vote count, and deliver a brutal closing verdict. ")
              .append("Use Minecraft colour codes with § symbol. ")
              .append("Keep the tone ").append(intensity.name().toLowerCase()).append(". ")
              .append("Format each candidate as a separate block. ")
              .append("The town is \"").append(election.getTownName()).append("\".\n\n");

        int rank = 1;
        for (UUID uuid : ranked) {
            Candidate c = election.getCandidate(uuid);
            if (c == null) continue;
            int votes = tally.getOrDefault(uuid, 0);
            prompt.append("CANDIDATE #").append(rank).append(":\n")
                  .append("  Name: ").append(c.getName()).append("\n")
                  .append("  Party: ").append(c.getPartyName()).append("\n")
                  .append("  Campaign Message: ").append(c.getCampaignMessage()).append("\n")
                  .append("  Profile: ").append(c.getProfile() != null ? c.getProfile() : "(none)").append("\n")
                  .append("  Votes: ").append(votes).append("\n")
                  .append("  Rank: #").append(rank).append(" of ").append(ranked.size()).append("\n\n");
            rank++;
        }
        prompt.append("Roast them all. Be savage. Be funny. Use § colour codes. Go.");

        String finalPrompt = prompt.toString();

        callApi(finalPrompt).thenAccept(rawResponse -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String formatted = "§d§l🤖 GROK ROAST — ALL CANDIDATES 🤖\n"
                        + "§8§m                                               §r\n\n"
                        + rawResponse.trim()
                        + "\n\n§8§m                                               §r\n"
                        + "§dGrok has spoken.\n";
                callback.accept(formatted);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Grok API call failed, falling back to procedural roast-all", ex);
            Bukkit.getScheduler().runTask(plugin, () ->
                    callback.accept(fallback.roastAll(election)));
            return null;
        });
    }

    // ========================================================================
    //  Sync convenience (falls back to procedural immediately)
    // ========================================================================

    /**
     * Synchronous roast using the procedural fallback. Use this when you need
     * an immediate result (e.g. console, broadcasts). For players, prefer
     * {@link #roastAsync}.
     */
    public String roastSync(Candidate candidate, Election election, int voteCount, int rank) {
        return fallback.roast(candidate, election, voteCount, rank);
    }

    /** Synchronous roast-all using procedural fallback. */
    public String roastAllSync(Election election) {
        return fallback.roastAll(election);
    }

    // ========================================================================
    //  Internal — API call
    // ========================================================================

    private CompletableFuture<String> callApi(String prompt) {
        String requestBody = buildRequestBody(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseApiResponse);
    }

    private String buildRequestBody(String prompt) {
        // Escape prompt for JSON embedding
        String escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        return String.format("""
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "system",
                      "content": "You are Grok, a brutally honest AI commentator for a Minecraft server called TownyElections. You roast election candidates with savage humour. Use Minecraft colour codes (\\u00a7 followed by a letter or number) in your responses. Keep roasts witty, short, and deeply personal based on the candidate data provided. Never break character. Never apologise."
                    },
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "temperature": 0.9,
                  "max_tokens": %d
                }""",
                model, escapedPrompt, maxTokens);
    }

    private String parseApiResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body();

        if (status != 200) {
            throw new RuntimeException("Grok API returned HTTP " + status + ": "
                    + (body != null ? body.substring(0, Math.min(200, body.length())) : "(no body)"));
        }

        // Extract content from OpenAI-compatible JSON: choices[0].message.content
        String content = extractContent(body);
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Grok API returned empty content. Body: "
                    + (body != null ? body.substring(0, Math.min(200, body.length())) : "(null)"));
        }
        return content;
    }

    /** Quick JSON extraction without a dependency. */
    private String extractContent(String json) {
        // Find "content": " ... " within message object
        int msgIdx = json.indexOf("\"message\"");
        if (msgIdx < 0) return null;
        int contentIdx = json.indexOf("\"content\"", msgIdx);
        if (contentIdx < 0) return null;
        int colonIdx = json.indexOf(':', contentIdx);
        if (colonIdx < 0) return null;
        int openQuote = json.indexOf('"', colonIdx + 1);
        if (openQuote < 0) return null;

        // Walk the string to find the closing unescaped quote
        StringBuilder sb = new StringBuilder();
        for (int i = openQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    default   -> sb.append(c).append(next);
                }
                i++; // skip escaped char
            } else if (c == '"') {
                break; // closing quote
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ========================================================================
    //  Formatting
    // ========================================================================

    private String formatApiResponse(String raw, Candidate candidate) {
        StringBuilder sb = new StringBuilder();
        sb.append("§d§l🤖 GROK ROAST — ").append(candidate.getName()).append(" §d§l🤖\n");
        sb.append("§8§m                                          §r\n\n");
        sb.append(raw.trim());
        sb.append("\n\n§8§m                                          §r");
        return sb.toString();
    }

    // ========================================================================
    //  Prompt builder
    // ========================================================================

    private String buildSinglePrompt(Candidate candidate, Election election, int voteCount, int rank) {
        int total = election.getCandidateCount();
        String phase = election.getPhase().name();

        return String.format("""
                Roast this Minecraft election candidate. Be brutally funny. Use § colour codes. Be %s.

                CANDIDATE DATA:
                - Name: %s
                - Party: %s
                - Campaign Message: "%s"
                - Profile: %s
                - Votes: %d
                - Rank: #%d of %d candidates
                - Election Phase: %s
                - Town: %s

                Start with a savage opener, then roast their campaign message, their party choice,
                their profile, and their vote count. End with a brutal closing verdict.
                Keep it under 200 words. Use § codes for Minecraft colours. Go.""",
                intensity.name().toLowerCase(),
                candidate.getName(),
                candidate.getPartyName(),
                candidate.getCampaignMessage() != null ? candidate.getCampaignMessage() : "(none)",
                candidate.getProfile() != null && !candidate.getProfile().isBlank()
                        ? candidate.getProfile() : "(no profile — zero effort)",
                voteCount,
                rank,
                total,
                phase,
                election.getTownName()
        );
    }

    // ========================================================================
    //  Accessors
    // ========================================================================

    public Intensity getIntensity() { return intensity; }
    public void setIntensity(Intensity intensity) { this.intensity = intensity; }

    // ========================================================================
    //  Procedural fallback — offline roasts when the API is unavailable
    // ========================================================================

    /**
     * Generates witty roasts from template pools — no network needed. Used as
     * the fallback when the xAI API key isn't configured or the call fails.
     */
    private static class ProceduralRoaster {

        private final java.util.Random random = new java.util.Random();

        String roast(Candidate candidate, Election election, int voteCount, int rank) {
            random.setSeed(candidate.getUuid().getLeastSignificantBits()
                    ^ (election != null ? election.getTownUuid().getLeastSignificantBits() : 0));

            StringBuilder sb = new StringBuilder();
            sb.append("§d§l🤖 GROK ROAST — ").append(candidate.getName()).append(" §d§l🤖\n");
            sb.append("§8§m                                          §r\n\n");
            sb.append(pickOpener(candidate, rank)).append("\n\n");
            sb.append("§7On their campaign message: §f")
                    .append(roastCampaignMessage(candidate)).append("\n");
            sb.append("§7On their party affiliation: §f")
                    .append(roastParty(candidate)).append("\n");
            sb.append("§7On their candidate profile: §f")
                    .append(roastProfile(candidate)).append("\n");
            if (election != null && election.getPhase() != election.getPhase().NOMINATION) {
                sb.append("§7On their electoral performance: §f")
                        .append(roastVoteCount(voteCount, rank, election.getCandidateCount())).append("\n");
            }
            sb.append("\n").append(pickCloser(candidate, voteCount)).append("\n");
            sb.append("\n§8§m                                          §r");
            return sb.toString();
        }

        String roastAll(Election election) {
            Map<UUID, Integer> tally = election.tally();
            List<UUID> ranked = election.rankedCandidates();
            StringBuilder sb = new StringBuilder();
            sb.append("§d§l🤖 GROK ROAST — ALL CANDIDATES 🤖\n");
            sb.append("§8§m                                               §r\n\n");
            sb.append("§7Grok has analysed the candidates of §e")
                    .append(election.getTownName()).append("§7. Here's the brutal truth:\n\n");
            int rank = 1;
            for (UUID uuid : ranked) {
                Candidate c = election.getCandidate(uuid);
                if (c == null) continue;
                String r = roast(c, election, tally.getOrDefault(uuid, 0), rank);
                String stripped = r
                        .replaceAll("§d§l🤖 GROK ROAST — .+? §d§l🤖\n", "")
                        .replaceAll("§8§m.+?§r\n", "").trim();
                sb.append("§e#").append(rank).append(" ").append(stripped).append("\n\n");
                rank++;
            }
            sb.append("§8§m                                               §r\n");
            sb.append("§dGrok has spoken. §7(offline mode — API not configured)\n");
            return sb.toString();
        }

        // --- opener / closer pools ---

        private String pickOpener(Candidate candidate, int rank) {
            java.util.List<String> options = new java.util.ArrayList<>(java.util.List.of(
                    "§e" + candidate.getName() + "§7. Where do I even begin?",
                    "§7Let's talk about §e" + candidate.getName() + "§7. Or rather, let's not.",
                    "§7Ah yes, §e" + candidate.getName() + "§7. The algorithm has... detected a candidate.",
                    "§7Grok has analysed §e" + candidate.getName() + "§7. The results were... unfortunate.",
                    "§e" + candidate.getName() + "§7. The embodiment of \"participation trophy\" energy."
            ));
            if (rank == 1) {
                options.add("§e" + candidate.getName() + "§7 is currently winning. Democracy is in crisis.");
            } else if (rank == 2) {
                options.add("§e" + candidate.getName() + " §7is in second place. In a town election. Let that sink in.");
            }
            return options.get(random.nextInt(options.size()));
        }

        private String pickCloser(Candidate candidate, int voteCount) {
            if (voteCount == 0) {
                return pickOne("§c§lVerdict: §7Not even their own mother voted for them.",
                        "§c§lVerdict: §7A masterclass in irrelevance.",
                        "§c§lVerdict: §7Grok recommends a career change. Perhaps farming?");
            }
            return pickOne("§c§lVerdict: §7" + voteCount + " votes. " + voteCount + " people need to be studied.",
                    "§c§lVerdict: §7Grok is not angry. Just disappointed.",
                    "§c§lVerdict: §7They showed up. That was their first mistake.");
        }

        // --- trait roasts ---

        private String roastCampaignMessage(Candidate candidate) {
            String msg = candidate.getCampaignMessage();
            if (msg == null || msg.isBlank() || msg.equalsIgnoreCase("I would be honored to serve this town."))
                return "§7Default message. Zero effort. Grok rates this campaign 0/10 emeralds.";
            if (msg.length() < 15)
                return "\"" + msg + "\" §7— That's it? That's the whole pitch? §8Embarrassing.";
            if (msg.length() > 80)
                return "§7A " + msg.length() + "-character manifesto. Nobody is reading all that.";
            if (msg.toLowerCase().contains("honor") || msg.toLowerCase().contains("honour"))
                return "\"" + msg + "\" §7— \"Honor\"? This isn't a medieval joust.";
            return "\"" + msg + "\" §7— " + pickOne(
                    "Grok has seen better campaigns on a discarded piece of cobblestone.",
                    "If words were diamonds, this would still be dirt.",
                    "The enthusiasm of a zombie villager. The charisma of gravel.");
        }

        private String roastParty(Candidate candidate) {
            String party = candidate.getPartyName();
            if (party == null || party.isBlank() || party.equalsIgnoreCase("Independent"))
                return "§7\"Independent.\" Translation: nobody wanted to run with them.";
            if (party.length() > 20)
                return "§7\"" + party + "\" — That's not a party name, that's a paragraph.";
            return "§7\"" + party + "\" — " + pickOne(
                    "Sounds made up. Grok checked. It is.",
                    "A party of one. Literally. Nobody else joined.",
                    "Grok searched the political spectrum. This party wasn't on it.");
        }

        private String roastProfile(Candidate candidate) {
            String profile = candidate.getProfile();
            if (profile == null || profile.isBlank())
                return "§7No profile written. Grok assumes they have no qualifications, personality, or pulse.";
            if (profile.length() < 30)
                return "§7" + profile.length() + " characters. That's not a profile, that's a tweet.";
            return "§7Grok read the profile. Grok regrets learning to read.";
        }

        private String roastVoteCount(int votes, int rank, int totalCandidates) {
            if (totalCandidates <= 1)
                return "§7Running unopposed. Impressive in the worst possible way.";
            if (votes == 0) return "§7Zero votes. The tumbleweeds voted for someone else.";
            if (votes == 1) return "§7One vote. And we all know who cast it. §8(It was them.)";
            if (rank == 1) return "§7Leading with " + votes + " votes. \"Least bad option\" energy.";
            if (rank == totalCandidates) return "§7Dead last with " + votes + " votes. Performance art.";
            return "§7" + votes + " votes. Ranked #" + rank + ". The definition of \"mid.\"";
        }

        private String pickOne(String... options) {
            return options[random.nextInt(options.length)];
        }
    }
}
