package com.townyelections.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.townyelections.TownyElections;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Checks Modrinth for a newer <em>release</em> version of the plugin.
 *
 * <p>Only versions with a {@code release} version type are considered; beta and
 * alpha releases are ignored. The check runs asynchronously and never blocks the
 * main thread. Results are cached so join notifications and console logging can
 * read them cheaply.
 */
public class UpdateChecker {

    private static final String API_BASE = "https://api.modrinth.com/v2/project/";

    private final TownyElections plugin;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = null;
    private volatile String downloadUrl = null;

    public UpdateChecker(TownyElections plugin) {
        this.plugin = plugin;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getCurrentVersion() {
        return plugin.getDescription().getVersion();
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    /** Run the Modrinth check off the main thread. */
    public void checkAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::check);
    }

    private void check() {
        String project = plugin.getConfigManager().getUpdateProject();
        if (project == null || project.isBlank()) {
            return;
        }
        try {
            String url = API_BASE + project.trim() + "/version";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent",
                            "vingaming1113/TownyElections/" + getCurrentVersion()
                                    + " (Minecraft update checker)")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                debug("Modrinth update check returned HTTP " + response.statusCode()
                        + " for project '" + project + "'.");
                return;
            }

            String newest = newestReleaseVersion(response.body());
            if (newest == null) {
                debug("No published release versions found on Modrinth for project '" + project + "'.");
                return;
            }

            String current = getCurrentVersion();
            if (compareVersions(newest, current) > 0) {
                updateAvailable = true;
                latestVersion = newest;
                downloadUrl = "https://modrinth.com/project/" + project.trim();
                plugin.getLogger().info("A new release is available: v" + newest
                        + " (you have v" + current + "). Download: " + downloadUrl);
            } else {
                updateAvailable = false;
                latestVersion = current;
                debug("TownyElections is up to date (v" + current + ").");
            }
        } catch (Exception ex) {
            // Never let a failed check disrupt the server; log only in debug.
            debug("Update check failed: " + ex.getMessage());
        }
    }

    /** Parse the Modrinth version list and return the highest release version number, or null. */
    private String newestReleaseVersion(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray()) {
            return null;
        }
        JsonArray versions = root.getAsJsonArray();
        String best = null;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject version = element.getAsJsonObject();
            if (!version.has("version_type") || !version.has("version_number")) {
                continue;
            }
            String type = version.get("version_type").getAsString();
            if (!"release".equalsIgnoreCase(type)) {
                continue;
            }
            String number = version.get("version_number").getAsString();
            if (number == null || number.isBlank()) {
                continue;
            }
            if (best == null || compareVersions(number, best) > 0) {
                best = number;
            }
        }
        return best;
    }

    /**
     * Compare two version strings numerically component by component. Leading
     * {@code v} and any non-numeric suffix on a component are ignored. Returns a
     * positive number if {@code a} is newer than {@code b}, negative if older,
     * and zero if equal.
     */
    static int compareVersions(String a, String b) {
        int[] pa = parse(a);
        int[] pb = parse(b);
        int length = Math.max(pa.length, pb.length);
        for (int i = 0; i < length; i++) {
            int va = i < pa.length ? pa[i] : 0;
            int vb = i < pb.length ? pb[i] : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int[] parse(String version) {
        if (version == null) {
            return new int[0];
        }
        String cleaned = version.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("v")) {
            cleaned = cleaned.substring(1);
        }
        // Drop any pre-release/build suffix such as -SNAPSHOT or +build.
        int dash = cleaned.indexOf('-');
        if (dash >= 0) {
            cleaned = cleaned.substring(0, dash);
        }
        int plus = cleaned.indexOf('+');
        if (plus >= 0) {
            cleaned = cleaned.substring(0, plus);
        }
        String[] parts = cleaned.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = leadingInt(parts[i]);
        }
        return out;
    }

    private static int leadingInt(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(part.substring(0, end));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void debug(String message) {
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[UpdateChecker] " + message);
        }
    }
}
