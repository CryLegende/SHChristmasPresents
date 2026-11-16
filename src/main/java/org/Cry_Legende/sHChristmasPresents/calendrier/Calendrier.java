package org.Cry_Legende.sHChristmasPresents.calendrier;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.commun.Joueurs;
import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetConfig;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.Cry_Legende.sHChristmasPresents.joueurs.Profil;
import org.Cry_Legende.sHChristmasPresents.stockage.Base;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Calendrier de l'Avent : porte N ouvrable le jour N (premier-jour + N - 1) dès 00:00, rattrapable jusqu'à
 * N + rattrapage-jours à 23:59, jamais avant. Temps de jeu minimal et nombre de comptes par empreinte d'IP et par porte.
 */
public final class Calendrier {

    record Recompense(int flocons, List<String> commandes) {
    }

    private final SHChristmasPresents plugin;
    private final Portes portes;
    private volatile LocalDate premierJour = LocalDate.of(2026, 12, 1);
    private volatile int nombre = 25;
    private volatile int rattrapageJours = 3;
    private volatile int tempsDeJeuMin = 30;
    private volatile int comptesParIp = 2;
    private volatile int bienvenue = 100;
    private volatile int porteSpeciale = 25;
    private volatile Recompense recompenseDefaut = new Recompense(30, List.of());
    private volatile Map<Integer, Recompense> recompenses = Map.of();
    private volatile TreeMap<Integer, List<String>> paliers = new TreeMap<>();
    private volatile int lignes = 6;
    private volatile List<Integer> cases = List.of();
    private volatile int caseInfo = 4;
    private volatile Material cadre = Material.BLACK_STAINED_GLASS_PANE;
    private volatile Map<EtatPorte, ObjetConfig> objets = Map.of();
    private volatile Map<EtatPorte, ObjetConfig> objetsSpeciale = Map.of();

    public Calendrier(SHChristmasPresents plugin, Base base, int annee) {
        this.plugin = plugin;
        this.portes = new Portes(base, annee);
    }

    public void configurer(ConfigurationSection s, int annee) {
        if (s == null) {
            return;
        }
        LocalDate premier = LectureConfig.date(s.get("premier-jour"));
        premierJour = premier == null ? LocalDate.of(annee, 12, 1) : premier;
        nombre = Math.max(1, s.getInt("portes", 25));
        rattrapageJours = Math.max(0, s.getInt("rattrapage-jours", 3));
        tempsDeJeuMin = Math.max(0, s.getInt("temps-de-jeu-min", 30));
        comptesParIp = Math.max(0, s.getInt("comptes-par-ip", 2));
        bienvenue = Math.max(0, s.getInt("bienvenue", 100));
        porteSpeciale = s.getInt("porte-speciale", nombre);

        ConfigurationSection r = s.getConfigurationSection("recompenses");
        int floconsDefaut = r == null ? 30 : r.getInt("defaut.flocons", 30);
        recompenseDefaut = new Recompense(floconsDefaut, r == null ? List.of() : LectureConfig.textes(r.getConfigurationSection("defaut"), "commandes"));
        Map<Integer, Recompense> parPorte = new HashMap<>();
        if (r != null) {
            for (String cle : r.getKeys(false)) {
                try {
                    int porte = Integer.parseInt(cle.trim());
                    ConfigurationSection p = r.getConfigurationSection(cle);
                    parPorte.put(porte, new Recompense(p == null ? floconsDefaut : p.getInt("flocons", floconsDefaut),
                            LectureConfig.textes(p, "commandes")));
                } catch (NumberFormatException ignored) {
                    // « defaut » et autres clés non numériques
                }
            }
        }
        recompenses = Map.copyOf(parPorte);

        TreeMap<Integer, List<String>> lus = new TreeMap<>();
        ConfigurationSection p = s.getConfigurationSection("paliers");
        if (p != null) {
            for (String cle : p.getKeys(false)) {
                try {
                    lus.put(Integer.parseInt(cle.trim()), LectureConfig.textes(p, cle));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("calendrier.paliers." + cle + " : nombre de portes illisible");
                }
            }
        }
        paliers = lus;

        ConfigurationSection menu = s.getConfigurationSection("menu");
        lignes = menu == null ? 6 : menu.getInt("lignes", 6);
        List<Integer> lues = LectureConfig.entiers(menu, "cases");
        cases = lues.isEmpty() ? casesParDefaut() : List.copyOf(lues);
        caseInfo = menu == null ? 4 : menu.getInt("case-info", 4);
        Material vitre = menu == null ? Material.BLACK_STAINED_GLASS_PANE : Material.matchMaterial(menu.getString("cadre", "BLACK_STAINED_GLASS_PANE"));
        cadre = vitre == null ? Material.AIR : vitre;
        objets = lireObjets(menu == null ? null : menu.getConfigurationSection("objets"), defauts());
        objetsSpeciale = lireObjets(menu == null ? null : menu.getConfigurationSection("objets-speciale"), objets);
    }

    private static List<Integer> casesParDefaut() {
        List<Integer> out = new ArrayList<>();
        for (int ligne = 1; ligne <= 3; ligne++) {
            for (int colonne = 1; colonne <= 7; colonne++) {
                out.add(ligne * 9 + colonne);
            }
        }
        out.addAll(List.of(39, 40, 41, 49));
        return out;
    }

    private static Map<EtatPorte, ObjetConfig> defauts() {
        Map<EtatPorte, ObjetConfig> out = new EnumMap<>(EtatPorte.class);
        out.put(EtatPorte.A_VENIR, ObjetConfig.simple(Material.GRAY_SHULKER_BOX));
        out.put(EtatPorte.OUVRABLE, new ObjetConfig(Material.RED_SHULKER_BOX, null, true, null));
        out.put(EtatPorte.RATTRAPABLE, ObjetConfig.simple(Material.ORANGE_SHULKER_BOX));
        out.put(EtatPorte.OUVERTE, ObjetConfig.simple(Material.LIME_STAINED_GLASS_PANE));
        out.put(EtatPorte.MANQUEE, ObjetConfig.simple(Material.BLACK_STAINED_GLASS_PANE));
        return out;
    }

    private static Map<EtatPorte, ObjetConfig> lireObjets(ConfigurationSection section, Map<EtatPorte, ObjetConfig> defauts) {
        Map<EtatPorte, ObjetConfig> out = new EnumMap<>(EtatPorte.class);
        for (EtatPorte etat : EtatPorte.values()) {
            out.put(etat, ObjetConfig.lire(section == null ? null : section.getConfigurationSection(etat.cle()), defauts.get(etat)));
        }
        return out;
    }

    public void charger(Connection connexion) throws SQLException {
        portes.charger(connexion);
    }

    // ---- état

    public int nombre() {
        return nombre;
    }

    LocalDate jourDe(int porte) {
        return premierJour.plusDays(porte - 1L);
    }

    /** Numéro de la porte du jour, 0 hors des jours du calendrier. */
    public int porteDuJour(LocalDate jour) {
        long ecart = jour.toEpochDay() - premierJour.toEpochDay() + 1;
        return ecart >= 1 && ecart <= nombre ? (int) ecart : 0;
    }

    public EtatPorte etat(UUID uuid, int porte, LocalDate jour) {
        if (portes.ouverte(uuid, porte)) {
            return EtatPorte.OUVERTE;
        }
        LocalDate ouverture = jourDe(porte);
        if (jour.isBefore(ouverture)) {
            return EtatPorte.A_VENIR;
        }
        if (jour.equals(ouverture)) {
            return EtatPorte.OUVRABLE;
        }
        return jour.isAfter(ouverture.plusDays(rattrapageJours)) ? EtatPorte.MANQUEE : EtatPorte.RATTRAPABLE;
    }

    public int ouvertes(UUID uuid) {
        return portes.nombre(uuid);
    }

    /** Portes encore rattrapables (jours passés). */
    public int aRattraper(UUID uuid, LocalDate jour) {
        int n = 0;
        for (int porte = 1; porte <= nombre; porte++) {
            if (etat(uuid, porte, jour) == EtatPorte.RATTRAPABLE) {
                n++;
            }
        }
        return n;
    }

    /** Une porte peut être ouverte maintenant (calendrier ouvert, porte du jour ou à rattraper). */
    public boolean disponible(UUID uuid) {
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        if (!plugin.dates().gains(maintenant)) {
            return false;
        }
        LocalDate jour = maintenant.toLocalDate();
        for (int porte = 1; porte <= nombre; porte++) {
            EtatPorte etat = etat(uuid, porte, jour);
            if (etat == EtatPorte.OUVRABLE || etat == EtatPorte.RATTRAPABLE) {
                return true;
            }
        }
        return false;
    }

    /** « 9/14 » : portes ouvertes sur le prochain palier (ou sur le total après le dernier palier). */
    public String palierTexte(UUID uuid) {
        int n = ouvertes(uuid);
        Integer suivant = paliers.higherKey(n);
        return n + "/" + (suivant == null ? nombre : suivant);
    }

    /** Minutes de jeu qui manquent pour ouvrir une porte, 0 si assez. */
    public int minutesManquantes(Player joueur) {
        return Math.max(0, tempsDeJeuMin - Joueurs.minutesDeJeu(joueur));
    }

    /** Ligne de %shchristmaspresents_porte_ligne%. */
    public String ligne(OfflinePlayer joueur) {
        var messages = plugin.messages();
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        LocalDate jour = maintenant.toLocalDate();
        if (jour.isBefore(premierJour) || maintenant.isBefore(plugin.dates().get("avent"))) {
            return messages.brut("placeholders.porte-ligne.avant", "{date}", Textes.jour(premierJour));
        }
        if (!plugin.dates().gains(maintenant)) {
            return messages.brut("placeholders.porte-ligne.fin");
        }
        UUID uuid = joueur.getUniqueId();
        int porte = porteDuJour(jour);
        if (porte > 0 && etat(uuid, porte, jour) == EtatPorte.OUVRABLE) {
            Player enLigne = joueur.getPlayer();
            int manque = enLigne == null ? 0 : minutesManquantes(enLigne);
            return manque > 0 ? messages.brut("placeholders.porte-ligne.temps", "{porte}", porte, "{minutes}", manque)
                    : messages.brut("placeholders.porte-ligne.attend", "{porte}", porte);
        }
        int rattraper = aRattraper(uuid, jour);
        if (rattraper > 0) {
            return messages.brut(rattraper == 1 ? "placeholders.porte-ligne.rattraper-une" : "placeholders.porte-ligne.rattraper", "{n}", rattraper);
        }
        if (porte > 0 && porte < nombre) {
            return messages.brut("placeholders.porte-ligne.ouverte", "{porte}", porte);
        }
        return messages.brut("placeholders.porte-ligne.fin");
    }

    // ---- ouverture

    /** Ouvre une porte (fil principal) : conditions, enregistrement, récompenses, paliers, bienvenue. */
    public void ouvrir(Player joueur, int porte) {
        var messages = plugin.messages();
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        if (!plugin.dates().gains(maintenant)) {
            boolean avant = maintenant.isBefore(plugin.dates().get("avent"));
            messages.envoyer(joueur, avant ? "calendrier.pas-encore" : "calendrier.termine", "{date}", Textes.date(plugin.dates().get("avent")));
            plugin.son("refus", joueur);
            return;
        }
        LocalDate jour = maintenant.toLocalDate();
        UUID uuid = joueur.getUniqueId();
        EtatPorte etat = etat(uuid, porte, jour);
        switch (etat) {
            case A_VENIR -> {
                messages.envoyer(joueur, "calendrier.a-venir", "{porte}", porte, "{date}", Textes.jour(jourDe(porte)));
                plugin.son("refus", joueur);
                return;
            }
            case OUVERTE -> {
                messages.envoyer(joueur, "calendrier.deja-ouverte", "{porte}", porte);
                plugin.son("refus", joueur);
                return;
            }
            case MANQUEE -> {
                messages.envoyer(joueur, "calendrier.manquee", "{porte}", porte);
                plugin.son("refus", joueur);
                return;
            }
            default -> {
            }
        }
        int manque = minutesManquantes(joueur);
        if (manque > 0) {
            messages.envoyer(joueur, "calendrier.temps-de-jeu", "{minutes}", manque);
            plugin.son("refus", joueur);
            return;
        }
        String empreinte = plugin.empreintes().de(joueur);
        if (comptesParIp > 0 && empreinte != null && portes.comptes(porte, empreinte) >= comptesParIp) {
            messages.envoyer(joueur, "calendrier.ip", "{porte}", porte);
            plugin.son("refus", joueur);
            plugin.getLogger().info("Porte " + porte + " refusée à " + joueur.getName() + " : " + comptesParIp + " comptes de la même connexion l'ont déjà ouverte");
            return;
        }

        portes.ajouter(uuid, porte, empreinte, etat == EtatPorte.RATTRAPABLE, maintenant.toLocalDateTime());
        Recompense recompense = recompenses.getOrDefault(porte, recompenseDefaut);
        String pseudo = joueur.getName();
        int verse = recompense.flocons() > 0 ? plugin.flocons().gagner(uuid, pseudo, recompense.flocons(), "porte", false) : 0;
        Console.executer(plugin.getLogger(), recompense.commandes(), "{joueur}", pseudo, "{uuid}", uuid.toString(), "{porte}", porte);
        String contenuCle = "calendrier.contenu." + porte;
        String contenu = messages.existe(contenuCle) ? messages.brut(contenuCle) : messages.brut("calendrier.contenu.defaut");
        messages.envoyer(joueur, "calendrier.ouverte", "{porte}", porte, "{contenu}",
                contenu.replace("{flocons}", Integer.toString(verse > 0 ? verse : recompense.flocons())));
        plugin.son(porte == porteSpeciale ? "porte-speciale" : "porte", joueur);

        int total = portes.nombre(uuid);
        plugin.compteurs().ajouter(uuid, "portes", 1);
        plugin.carte().compteurChange(uuid, pseudo, "portes");
        List<String> palier = paliers.get(total);
        if (palier != null) {
            Console.executer(plugin.getLogger(), palier, "{joueur}", pseudo, "{uuid}", uuid.toString(), "{portes}", total);
            String cle = "calendrier.paliers-contenu." + total;
            messages.envoyer(joueur, "calendrier.palier", "{n}", total, "{contenu}", messages.existe(cle) ? messages.brut(cle) : "");
        }
        Profil profil = plugin.profils().obtenir(uuid, pseudo);
        if (profil.bienvenue == null) {
            profil.bienvenue = maintenant.toLocalDateTime();
            plugin.profils().sauver(profil);
            if (bienvenue > 0) {
                int cadeau = plugin.flocons().gagner(uuid, pseudo, bienvenue, "bienvenue", false);
                messages.envoyer(joueur, "calendrier.bienvenue", "{flocons}", cadeau);
            }
        }
    }

    /** À la connexion : la porte attend mais le temps de jeu manque. */
    public void rappel(Player joueur) {
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        if (!plugin.dates().gains(maintenant)) {
            return;
        }
        int manque = minutesManquantes(joueur);
        if (manque > 0 && disponible(joueur.getUniqueId())) {
            plugin.messages().barre(joueur, "calendrier.temps-de-jeu-barre", "{minutes}", manque);
        }
    }

    public void ouvrirMenu(Player joueur) {
        new MenuCalendrier(plugin, this, joueur).ouvrir();
    }

    // ---- accès du menu

    int lignes() {
        return lignes;
    }

    List<Integer> cases() {
        return cases;
    }

    int caseInfo() {
        return caseInfo;
    }

    Material cadre() {
        return cadre;
    }

    ObjetConfig objet(int porte, EtatPorte etat) {
        return (porte == porteSpeciale ? objetsSpeciale : objets).get(etat);
    }

    int rattrapageJours() {
        return rattrapageJours;
    }

    int floconsDe(int porte) {
        return recompenses.getOrDefault(porte, recompenseDefaut).flocons();
    }
}
