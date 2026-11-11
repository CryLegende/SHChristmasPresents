package org.Cry_Legende.sHChristmasPresents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Textes de messages.yml. S'il n'existe pas encore, il est créé à partir de l'ancienne section « messages » de
 * config.yml (textes déjà personnalisés gardés), complétée par ceux du jar. Les clés absentes du fichier prennent
 * la valeur du jar.
 */
final class Messages {

    private final JavaPlugin plugin;
    private volatile YamlConfiguration yaml = new YamlConfiguration();

    Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration jar() {
        YamlConfiguration jar = new YamlConfiguration();
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                jar.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "messages.yml du jar illisible : " + e.getMessage());
        }
        return jar;
    }

    /** Lit messages.yml en mode strict et remplace les textes en mémoire d'un bloc. */
    void load() throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            YamlConfiguration created = jar();
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (configFile.exists()) {
                var legacy = YamlConfiguration.loadConfiguration(configFile).getConfigurationSection("messages");
                if (legacy != null) {
                    boolean migrated = false;
                    for (String key : legacy.getKeys(false)) {
                        if (legacy.isString(key)) {
                            created.set(key, legacy.getString(key));
                            migrated = true;
                        }
                    }
                    if (migrated) {
                        plugin.getLogger().info("Messages repris de config.yml dans messages.yml.");
                    }
                }
            }
            plugin.getDataFolder().mkdirs();
            created.save(file);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(file);
        loaded.setDefaults(jar());
        yaml = loaded;
    }

    /** Textes du jar seulement, quand messages.yml est illisible au démarrage. */
    void useJar() {
        yaml = jar();
    }

    private String raw(String key, String... replacements) {
        String text = yaml.getString(key);
        if (text == null) {
            text = key;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    /** Texte au format MiniMessage, sans préfixe (le texte porte le sien). */
    Component mini(String key, String... replacements) {
        return MiniMessage.miniMessage().deserialize(raw(key, replacements));
    }

    /** Texte avec codes &, précédé du préfixe SH. */
    Component legacy(String key, String... replacements) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw("prefix") + raw(key, replacements));
    }
}
