package org.Cry_Legende.sHChristmasPresents.recap;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.Cry_Legende.sHChristmasPresents.horloge.Fenetres;
import org.Cry_Legende.sHChristmasPresents.sapin.Sapin;
import org.Cry_Legende.sHChristmasPresents.stockage.Base;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /shchristmaspresents bilan : participants, Flocons gagnés et dépensés par source, médiane, top 5, jours de présence,
 * portes, cartes, sapin. Lu en base hors du fil principal, écrit dans bilan-&lt;date&gt;.yml et en console.
 */
public final class Bilan {

    private record Joueur(String pseudo, long flocons, int jours) {
    }

    private record Donnees(Map<String, long[]> parSource, List<Joueur> joueurs, Map<String, long[]> achats,
                           long[] cadeaux, long offert, Map<Integer, Integer> portes, int cartes) {
    }

    private final SHChristmasPresents plugin;
    private final Base base;
    private final int annee;

    public Bilan(SHChristmasPresents plugin, Base base, int annee) {
        this.plugin = plugin;
        this.base = base;
        this.annee = annee;
    }

    public void lancer(CommandSender auteur) {
        LocalDate date = plugin.horloge().aujourdhui();
        plugin.messages().envoyer(auteur, "admin.bilan-calcul");
        base.lire(this::lire).whenComplete((donnees, erreur) -> plugin.sync(() -> {
            if (erreur != null) {
                plugin.getLogger().severe("Bilan impossible : " + erreur.getMessage());
                plugin.messages().envoyer(auteur, "erreur");
                return;
            }
            File fichier = new File(plugin.getDataFolder(), "bilan-" + date + ".yml");
            try {
                YamlConfiguration yaml = ecrire(donnees);
                yaml.save(fichier);
                plugin.messages().envoyer(auteur, "admin.bilan-fait", "{fichier}", fichier.getName());
            } catch (IOException e) {
                plugin.getLogger().severe("Bilan non écrit : " + e.getMessage());
                plugin.messages().envoyer(auteur, "erreur");
            }
        }));
    }

    private Donnees lire(Connection c) throws SQLException {
        Map<String, long[]> parSource = new LinkedHashMap<>();
        try (PreparedStatement r = c.prepareStatement("SELECT source, SUM(verse), SUM(base), COUNT(*), COUNT(DISTINCT uuid)"
                + " FROM shxmas_gains WHERE annee = ? GROUP BY source ORDER BY SUM(verse) DESC")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    parSource.put(l.getString(1), new long[]{l.getLong(2), l.getLong(3), l.getLong(4), l.getLong(5)});
                }
            }
        }
        List<Joueur> joueurs = new ArrayList<>();
        try (PreparedStatement r = c.prepareStatement("SELECT MAX(pseudo), SUM(verse), COUNT(DISTINCT jour) FROM shxmas_gains"
                + " WHERE annee = ? GROUP BY uuid HAVING SUM(verse) > 0 ORDER BY SUM(verse) DESC")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    joueurs.add(new Joueur(l.getString(1), l.getLong(2), l.getInt(3)));
                }
            }
        }
        Map<String, long[]> achats = new LinkedHashMap<>();
        try (PreparedStatement r = c.prepareStatement("SELECT article, COUNT(*), SUM(prix), COUNT(DISTINCT uuid) FROM shxmas_achats"
                + " WHERE annee = ? GROUP BY article ORDER BY SUM(prix) DESC")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    achats.put(l.getString(1), new long[]{l.getLong(2), l.getLong(3), l.getLong(4)});
                }
            }
        }
        long[] cadeaux = new long[3];
        try (PreparedStatement r = c.prepareStatement("SELECT COUNT(*), COALESCE(SUM(montant), 0), COALESCE(SUM(statut = 'ouvert'), 0)"
                + " FROM shxmas_cadeaux WHERE annee = ?")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                if (l.next()) {
                    cadeaux = new long[]{l.getLong(1), l.getLong(2), l.getLong(3)};
                }
            }
        }
        long offert = 0;
        try (PreparedStatement r = c.prepareStatement("SELECT COALESCE(SUM(valeur), 0) FROM shxmas_compteurs WHERE annee = ?"
                + " AND compteur = 'offert'")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                if (l.next()) {
                    offert = l.getLong(1);
                }
            }
        }
        Map<Integer, Integer> portes = new LinkedHashMap<>();
        try (PreparedStatement r = c.prepareStatement("SELECT porte, COUNT(*) FROM shxmas_portes WHERE annee = ? GROUP BY porte ORDER BY porte")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    portes.put(l.getInt(1), l.getInt(2));
                }
            }
        }
        int cartes = 0;
        try (PreparedStatement r = c.prepareStatement("SELECT COUNT(*) FROM shxmas_joueurs WHERE annee = ? AND carte_complete IS NOT NULL")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                if (l.next()) {
                    cartes = l.getInt(1);
                }
            }
        }
        return new Donnees(parSource, joueurs, achats, cadeaux, offert, portes, cartes);
    }

    private YamlConfiguration ecrire(Donnees d) {
        var log = plugin.getLogger();
        YamlConfiguration y = new YamlConfiguration();
        y.set("annee", annee);
        y.set("date", plugin.horloge().local().withNano(0).toString());
        y.set("participants", d.joueurs().size());
        long gagnes = 0;
        for (Map.Entry<String, long[]> e : d.parSource().entrySet()) {
            long[] v = e.getValue();
            gagnes += v[0];
            y.set("gagnes.par-source." + e.getKey() + ".verses", v[0]);
            y.set("gagnes.par-source." + e.getKey() + ".base", v[1]);
            y.set("gagnes.par-source." + e.getKey() + ".gains", v[2]);
            y.set("gagnes.par-source." + e.getKey() + ".joueurs", v[3]);
        }
        y.set("gagnes.total", gagnes);
        long boutique = 0;
        for (Map.Entry<String, long[]> e : d.achats().entrySet()) {
            boutique += e.getValue()[1];
            y.set("depenses.boutique.articles." + e.getKey() + ".achats", e.getValue()[0]);
            y.set("depenses.boutique.articles." + e.getKey() + ".flocons", e.getValue()[1]);
            y.set("depenses.boutique.articles." + e.getKey() + ".joueurs", e.getValue()[2]);
        }
        y.set("depenses.boutique.total", boutique);
        y.set("depenses.sapin", d.offert());
        y.set("depenses.cadeaux", d.cadeaux()[1]);
        y.set("depenses.total", boutique + d.offert() + d.cadeaux()[1]);
        y.set("cadeaux.envoyes", d.cadeaux()[0]);
        y.set("cadeaux.ouverts", d.cadeaux()[2]);
        List<Long> totaux = new ArrayList<>();
        int[] presence = new int[4];
        for (Joueur j : d.joueurs()) {
            totaux.add(j.flocons());
            presence[j.jours() >= 10 ? 3 : j.jours() >= 5 ? 2 : j.jours() >= 2 ? 1 : 0]++;
        }
        double mediane = Fenetres.mediane(totaux);
        y.set("mediane", mediane);
        for (int i = 0; i < Math.min(5, d.joueurs().size()); i++) {
            y.set("top." + (i + 1) + ".joueur", d.joueurs().get(i).pseudo());
            y.set("top." + (i + 1) + ".flocons", d.joueurs().get(i).flocons());
        }
        long partTop = 0;
        for (int i = 0; i < Math.min(5, d.joueurs().size()); i++) {
            partTop += d.joueurs().get(i).flocons();
        }
        y.set("part-du-top-5", gagnes == 0 ? 0 : Math.round(1000.0 * partTop / gagnes) / 10.0);
        y.set("jours-de-presence.1", presence[0]);
        y.set("jours-de-presence.2-4", presence[1]);
        y.set("jours-de-presence.5-9", presence[2]);
        y.set("jours-de-presence.10-et-plus", presence[3]);
        for (Map.Entry<Integer, Integer> e : d.portes().entrySet()) {
            y.set("portes." + e.getKey(), e.getValue());
        }
        y.set("cartes-completes", d.cartes());
        Sapin sapin = plugin.sapin();
        y.set("sapin.pourcent", Double.parseDouble(Textes.pourcent(sapin.pourcent())));
        y.set("sapin.points", sapin.points());
        y.set("sapin.cible", sapin.cible());
        y.set("sapin.contributeurs", sapin.contributeurs());
        List<Integer> paliers = new ArrayList<>();
        for (Sapin.Palier p : sapin.paliers()) {
            if (sapin.palierFait(p)) {
                paliers.add(p.pourcent());
            }
        }
        y.set("sapin.paliers-atteints", paliers);

        log.info("Bilan " + annee + " : " + d.joueurs().size() + " participants, " + Textes.nombre(gagnes) + " Flocons gagnés, "
                + Textes.nombre(boutique + d.offert() + d.cadeaux()[1]) + " dépensés, médiane " + mediane);
        for (Map.Entry<String, long[]> e : d.parSource().entrySet()) {
            log.info("  " + e.getKey() + " : " + e.getValue()[0] + " versés (" + e.getValue()[3] + " joueurs)");
        }
        for (int i = 0; i < Math.min(5, d.joueurs().size()); i++) {
            log.info("  top " + (i + 1) + " : " + d.joueurs().get(i).pseudo() + " " + d.joueurs().get(i).flocons());
        }
        log.info("  présence : 1 j " + presence[0] + ", 2-4 j " + presence[1] + ", 5-9 j " + presence[2] + ", 10 j et + " + presence[3]);
        log.info("  sapin : " + Textes.pourcent(sapin.pourcent()) + " %, " + sapin.contributeurs() + " contributeurs, paliers " + paliers);
        return y;
    }
}
