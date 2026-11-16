package org.Cry_Legende.sHChristmasPresents.commun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Textes de messages.yml. Les clés absentes du fichier du serveur prennent le texte du jar ; une clé laissée vide
 * exprès ne s'affiche pas. Les textes de l'évènement sont en codes &amp; (et &amp;#RRGGBB) et portent leur préfixe ;
 * cooldown_message et snowball_received (1.1) sont en MiniMessage, les autres textes 1.1 prennent la clé prefix.
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private final JavaPlugin plugin;
    private volatile YamlConfiguration yaml = new YamlConfiguration();

    public Messages(JavaPlugin plugin) {
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

    /** Lit messages.yml en mode strict (créé depuis le jar s'il manque) et remplace les textes d'un bloc. */
    public void charger() throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(file);
        loaded.setDefaults(jar());
        yaml = loaded;
    }

    /** Textes du jar seulement, quand messages.yml est illisible au démarrage. */
    public void textesDuJar() {
        yaml = jar();
    }

    /** Vrai si le fichier du serveur définit lui-même la clé (sans compter le jar). */
    public boolean definie(String cle) {
        return yaml.contains(cle, true);
    }

    /** Vrai si la clé existe dans le fichier du serveur ou dans celui du jar. */
    public boolean existe(String cle) {
        return yaml.contains(cle);
    }

    /** Texte brut d'une clé, remplacements faits ({cle}, valeur, ...). La clé elle-même si elle n'existe nulle part. */
    public String brut(String cle, Object... remplacements) {
        String texte = yaml.getString(cle);
        if (texte == null) {
            texte = cle;
        }
        return remplacer(texte, remplacements);
    }

    public static String remplacer(String texte, Object... remplacements) {
        String out = texte;
        for (int i = 0; i + 1 < remplacements.length; i += 2) {
            out = out.replace(String.valueOf(remplacements[i]), String.valueOf(remplacements[i + 1]));
        }
        return out;
    }

    /** Lignes d'une clé : liste YAML, ou texte coupé aux retours à la ligne. Vide si la clé est vide. */
    public List<String> lignes(String cle, Object... remplacements) {
        List<String> out = new ArrayList<>();
        if (yaml.isList(cle)) {
            for (String ligne : yaml.getStringList(cle)) {
                out.add(remplacer(ligne, remplacements));
            }
            return out;
        }
        String texte = yaml.getString(cle);
        if (texte == null || texte.isEmpty()) {
            return out;
        }
        out.addAll(Arrays.asList(remplacer(texte, remplacements).split("\n", -1)));
        return out;
    }

    public Component texte(String cle, Object... remplacements) {
        return legacy(brut(cle, remplacements));
    }

    /** Envoie le texte s'il n'est pas vide. */
    public void envoyer(CommandSender cible, String cle, Object... remplacements) {
        if (cible == null) {
            return;
        }
        String texte = brut(cle, remplacements);
        if (!texte.isEmpty()) {
            cible.sendMessage(legacy(texte));
        }
    }

    /** Barre d'action, si le texte n'est pas vide. */
    public void barre(Player joueur, String cle, Object... remplacements) {
        String texte = brut(cle, remplacements);
        if (!texte.isEmpty()) {
            joueur.sendActionBar(legacy(texte));
        }
    }

    /** Nom d'objet ou de menu : sans l'italique que Minecraft met par défaut. */
    public Component objet(String cle, Object... remplacements) {
        return objetTexte(brut(cle, remplacements));
    }

    /** Lignes de description d'un objet. */
    public List<Component> lore(String cle, Object... remplacements) {
        List<Component> out = new ArrayList<>();
        for (String ligne : lignes(cle, remplacements)) {
            out.add(objetTexte(ligne));
        }
        return out;
    }

    public static Component legacy(String texte) {
        return LEGACY.deserialize(texte.replace('§', '&'));
    }

    public static Component objetTexte(String texte) {
        return legacy(texte).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // ---- textes 1.1

    /** Texte au format MiniMessage, sans préfixe (le texte porte le sien). */
    public Component mini(String cle, Object... remplacements) {
        return MiniMessage.miniMessage().deserialize(brut(cle, remplacements));
    }

    /** Texte avec codes &amp;, précédé de la clé prefix (textes de la commande 1.1). */
    public Component prefixe(String cle, Object... remplacements) {
        return legacy(brut("prefix") + brut(cle, remplacements));
    }
}
