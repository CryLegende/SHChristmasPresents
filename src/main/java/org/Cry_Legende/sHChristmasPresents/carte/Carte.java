package org.Cry_Legende.sHChristmasPresents.carte;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetConfig;
import org.Cry_Legende.sHChristmasPresents.horloge.Phase;
import org.Cry_Legende.sHChristmasPresents.joueurs.Profil;
import org.Cry_Legende.sHChristmasPresents.stockage.Base;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carte de Noël : tampons accordés par une condition (première visite du village, compteur, niveau du clicker,
 * commande). Carte complète = commandes-complete, une fois. Fil principal pour tout ce qui écrit.
 */
public final class Carte {

    public record Tampon(String id, int case_, Condition condition, ObjetConfig objet, ObjetConfig objetObtenu) {
    }

    private final SHChristmasPresents plugin;
    private final Base base;
    private final int annee;
    private final Map<UUID, Set<String>> obtenus = new ConcurrentHashMap<>();
    private volatile List<Tampon> tampons = List.of();
    private volatile List<String> commandesComplete = List.of();
    private volatile String mondeVillage = "Christmas";
    private volatile int lignes = 4;
    private volatile int caseInfo = 4;
    private volatile Material cadre = Material.BLACK_STAINED_GLASS_PANE;

    public Carte(SHChristmasPresents plugin, Base base, int annee) {
        this.plugin = plugin;
        this.base = base;
        this.annee = annee;
    }

    public void configurer(ConfigurationSection section) {
        List<Tampon> lus = new ArrayList<>();
        if (section != null) {
            ConfigurationSection liste = section.getConfigurationSection("tampons");
            ObjetConfig defaut = ObjetConfig.lire(section.getConfigurationSection("objet-manquant"), ObjetConfig.simple(Material.GRAY_DYE));
            ObjetConfig defautObtenu = ObjetConfig.lire(section.getConfigurationSection("objet-obtenu"), ObjetConfig.simple(Material.LIME_DYE));
            if (liste != null) {
                int i = 0;
                for (String id : liste.getKeys(false)) {
                    ConfigurationSection t = liste.getConfigurationSection(id);
                    Condition condition = t == null ? null : Condition.lire(t.getString("condition"));
                    if (condition == null) {
                        plugin.getLogger().warning("carte.tampons." + id + " : condition illisible, tampon ignoré");
                        continue;
                    }
                    lus.add(new Tampon(id, t.getInt("case", 10 + i), condition,
                            ObjetConfig.lire(t.getConfigurationSection("objet"), defaut),
                            ObjetConfig.lire(t.getConfigurationSection("objet-obtenu"), defautObtenu)));
                    i++;
                }
            }
            commandesComplete = LectureConfig.textes(section, "commandes-complete");
            mondeVillage = section.getString("monde-village", "Christmas");
            lignes = section.getInt("menu.lignes", 4);
            caseInfo = section.getInt("menu.case-info", 4);
            Material vitre = Material.matchMaterial(section.getString("menu.cadre", "BLACK_STAINED_GLASS_PANE"));
            cadre = vitre == null ? Material.AIR : vitre;
        }
        tampons = List.copyOf(lus);
    }

    public void charger(Connection connexion) throws SQLException {
        try (PreparedStatement r = connexion.prepareStatement("SELECT uuid, tampon FROM shxmas_tampons WHERE annee = ?")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    obtenus.computeIfAbsent(UUID.fromString(l.getString(1)), k -> ConcurrentHashMap.newKeySet()).add(l.getString(2));
                }
            }
        }
    }

    public List<Tampon> tampons() {
        return tampons;
    }

    public Tampon tampon(String id) {
        for (Tampon t : tampons) {
            if (t.id().equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    public boolean a(UUID uuid, String id) {
        Set<String> joueur = obtenus.get(uuid);
        return joueur != null && joueur.contains(id);
    }

    /** Tampons obtenus parmi ceux de la config. */
    public int nombre(UUID uuid) {
        Set<String> joueur = obtenus.get(uuid);
        if (joueur == null) {
            return 0;
        }
        int n = 0;
        for (Tampon t : tampons) {
            if (joueur.contains(t.id())) {
                n++;
            }
        }
        return n;
    }

    public boolean complete(UUID uuid) {
        Profil profil = plugin.profils().get(uuid);
        return profil != null && profil.carteComplete != null;
    }

    int lignes() {
        return lignes;
    }

    int caseInfo() {
        return caseInfo;
    }

    Material cadre() {
        return cadre;
    }

    /** Accorde un tampon s'il manque (fil principal). Rend vrai s'il vient d'être accordé. */
    public boolean accorder(UUID uuid, String pseudo, String id) {
        Tampon tampon = tampon(id);
        if (tampon == null) {
            return false;
        }
        Set<String> joueur = obtenus.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        if (!joueur.add(tampon.id())) {
            return false;
        }
        LocalDateTime maintenant = plugin.horloge().local();
        base.ecrire("tampon " + tampon.id(), c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT IGNORE INTO shxmas_tampons (annee, uuid, tampon, obtenu) VALUES (?, ?, ?, ?)")) {
                r.setInt(1, annee);
                r.setString(2, uuid.toString());
                r.setString(3, tampon.id());
                r.setTimestamp(4, Timestamp.valueOf(maintenant));
                r.executeUpdate();
            }
        });
        Player enLigne = Bukkit.getPlayer(uuid);
        if (enLigne != null) {
            plugin.messages().envoyer(enLigne, "carte.tampon-obtenu", "{tampon}", plugin.messages().brut("carte.tampons." + tampon.id() + ".nom"),
                    "{n}", nombre(uuid), "{total}", tampons.size());
            plugin.son("tampon", enLigne);
        }
        verifierComplete(uuid, pseudo);
        return true;
    }

    private void verifierComplete(UUID uuid, String pseudo) {
        if (tampons.isEmpty() || nombre(uuid) < tampons.size()) {
            return;
        }
        Profil profil = plugin.profils().obtenir(uuid, pseudo);
        if (profil.carteComplete != null) {
            return;
        }
        profil.carteComplete = plugin.horloge().local();
        plugin.profils().sauver(profil);
        Console.executer(plugin.getLogger(), commandesComplete, "{joueur}", profil.pseudo, "{uuid}", uuid.toString());
        plugin.getLogger().info("Carte de Noël complète : " + profil.pseudo);
        Player enLigne = Bukkit.getPlayer(uuid);
        if (enLigne != null) {
            plugin.messages().envoyer(enLigne, "carte.complete");
            plugin.son("carte-complete", enLigne);
        }
    }

    /** Un compteur a changé : tampons qui en dépendent. */
    public void compteurChange(UUID uuid, String pseudo, String compteur) {
        int valeur = plugin.compteurs().valeur(uuid, compteur);
        for (Tampon t : tampons) {
            if (t.condition() instanceof Condition.Compteur c && c.id().equalsIgnoreCase(compteur) && valeur >= c.minimum()) {
                accorder(uuid, pseudo, t.id());
            }
        }
    }

    /** Le niveau du clicker a changé. */
    public void niveauClicker(UUID uuid, String pseudo, int niveau) {
        for (Tampon t : tampons) {
            if (t.condition() instanceof Condition.NiveauClicker c && niveau >= c.minimum()) {
                accorder(uuid, pseudo, t.id());
            }
        }
    }

    /** Entrée dans le monde du village pendant la phase village : tampons de première visite. */
    public void visite(Player joueur) {
        if (plugin.phase() != Phase.VILLAGE || !joueur.getWorld().getName().equalsIgnoreCase(mondeVillage)) {
            return;
        }
        Profil profil = plugin.profils().obtenir(joueur.getUniqueId(), joueur.getName());
        if (profil.premiereVisite != null) {
            return;
        }
        profil.premiereVisite = plugin.horloge().local();
        plugin.profils().sauver(profil);
        for (Tampon t : tampons) {
            if (t.condition() instanceof Condition.PremiereVisite) {
                accorder(joueur.getUniqueId(), joueur.getName(), t.id());
            }
        }
    }

    /** À la connexion : conditions déjà remplies (seuils changés, niveau du clicker…). */
    public void verifierTout(Player joueur) {
        UUID uuid = joueur.getUniqueId();
        for (Tampon t : tampons) {
            if (a(uuid, t.id())) {
                continue;
            }
            switch (t.condition()) {
                case Condition.Compteur c when plugin.compteurs().valeur(uuid, c.id()) >= c.minimum() ->
                        accorder(uuid, joueur.getName(), t.id());
                case Condition.NiveauClicker c when plugin.clicker().niveau(uuid) >= c.minimum() ->
                        accorder(uuid, joueur.getName(), t.id());
                default -> {
                }
            }
        }
        verifierComplete(uuid, joueur.getName());
        visite(joueur);
    }

    public void ouvrirMenu(Player joueur) {
        new MenuCarte(plugin, this, joueur).ouvrir();
    }
}
