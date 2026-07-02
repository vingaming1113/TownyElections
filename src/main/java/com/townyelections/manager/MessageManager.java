package com.townyelections.manager;

import com.townyelections.TownyElections;
import com.townyelections.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the locale message file, resolves keys, applies placeholder
 * substitution and colourisation, and sends messages to players/console.
 */
public class MessageManager {

    private final TownyElections plugin;
    private FileConfiguration messages;
    private String prefix = "";

    public MessageManager(TownyElections plugin) {
        this.plugin = plugin;
    }

    public void load(String locale) {
        String fileName = "messages_" + locale + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);

        // Save the bundled default if the requested locale file does not exist yet.
        if (!file.exists()) {
            if (plugin.getResource(fileName) != null) {
                plugin.saveResource(fileName, false);
            } else {
                // Fall back to English if the requested locale is not bundled.
                fileName = "messages_en.yml";
                file = new File(plugin.getDataFolder(), fileName);
                if (!file.exists()) {
                    plugin.saveResource(fileName, false);
                }
            }
        }

        messages = YamlConfiguration.loadConfiguration(file);

        // Merge in bundled defaults so new keys are always available after updates.
        InputStream defStream = plugin.getResource(fileName);
        if (defStream == null) {
            defStream = plugin.getResource("messages_en.yml");
        }
        if (defStream != null) {
            YamlConfiguration def = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
            messages.setDefaults(def);
            messages.options().copyDefaults(true);
        }

        prefix = messages.getString("prefix", "");
    }

    /** Raw string for a key, or a visible warning if the key is missing. */
    public String raw(String key) {
        String value = messages.getString(key);
        return value == null ? ("<missing:" + key + ">") : value;
    }

    /** Resolve a key with placeholders and return a coloured component (no prefix). */
    public Component component(String key, Map<String, String> placeholders) {
        return TextUtil.colorize(apply(raw(key), placeholders));
    }

    /** Resolve a key with placeholders and return Bukkit legacy text. */
    public String legacy(String key, Map<String, String> placeholders) {
        return TextUtil.legacy(apply(raw(key), placeholders));
    }

    /** Raw string list for a key, falling back to defaults when present. */
    public List<String> rawList(String key) {
        return messages.getStringList(key);
    }

    /** Send a prefixed message to a sender. */
    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String body = apply(raw(key), placeholders);
        sender.sendMessage(TextUtil.colorize(prefix + body));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, null);
    }

    /** Send without the configured prefix (useful for multi-line panels). */
    public void sendNoPrefix(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(TextUtil.colorize(apply(raw(key), placeholders)));
    }

    /** Colourise arbitrary text with the prefix applied. */
    public Component prefixed(String body) {
        return TextUtil.colorize(prefix + body);
    }

    private String apply(String text, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /** Convenience builder for placeholder maps. */
    public static Map<String, String> placeholders(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
