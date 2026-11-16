package org.Cry_Legende.sHChristmasPresents.horloge;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.stockage.Base;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Actions datées (actions:) et quotidiennes (chaque-jour:). Chaque id ne part qu'une fois, même après un
 * redémarrage : il est réservé en base (shxmas_actions) avant de lancer ses commandes. Une action manquée pendant un
 * arrêt part au démarrage si son retard reste dans sa marge (rattrapage-minutes), sinon elle est notée « sautée ».
 * Fil principal seulement.
 */
public final class Actions {

    record Action(String id, LocalDateTime quand, int marge, List<String> commandes) {
    }

    record Quotidienne(String id, LocalDate du, LocalDate au, LocalTime heure, int marge, List<String> commandes) {
    }

    private final SHChristmasPresents plugin;
    private final Base base;
    private final int annee;
    private final Set<String> traitees = ConcurrentHashMap.newKeySet();
    private final Set<String> enCours = new HashSet<>();
    private volatile List<Action> ponctuelles = List.of();
    private volatile List<Quotidienne> quotidiennes = List.of();

    public Actions(SHChristmasPresents plugin, Base base, int annee) {
        this.plugin = plugin;
        this.base = base;
        this.annee = annee;
    }

    public void configurer(ConfigurationSection actions, ConfigurationSection chaqueJour, int margeDefaut) {
        List<Action> lues = new ArrayList<>();
        if (actions != null) {
            for (String id : actions.getKeys(false)) {
                ConfigurationSection section = actions.getConfigurationSection(id);
                LocalDateTime quand = section == null ? null : LectureConfig.dateHeure(section.get("quand"));
                if (quand == null) {
                    plugin.getLogger().warning("actions." + id + " : quand illisible, action ignorée");
                    continue;
                }
                lues.add(new Action(id, quand, section.getInt("rattrapage-minutes", margeDefaut),
                        LectureConfig.textes(section, "commandes")));
            }
        }
        List<Quotidienne> jours = new ArrayList<>();
        if (chaqueJour != null) {
            for (String id : chaqueJour.getKeys(false)) {
                ConfigurationSection section = chaqueJour.getConfigurationSection(id);
                LocalDate du = section == null ? null : LectureConfig.date(section.get("du"));
                LocalDate au = section == null ? null : LectureConfig.date(section.get("au"));
                LocalTime heure = section == null ? null : LectureConfig.heure(section.get("heure"));
                if (du == null || au == null || heure == null) {
                    plugin.getLogger().warning("chaque-jour." + id + " : du, au ou heure illisible, action ignorée");
                    continue;
                }
                jours.add(new Quotidienne(id, du, au, heure, section.getInt("rattrapage-minutes", margeDefaut),
                        LectureConfig.textes(section, "commandes")));
            }
        }
        ponctuelles = List.copyOf(lues);
        quotidiennes = List.copyOf(jours);
    }

    public void charger(Connection connexion) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("SELECT id FROM shxmas_actions WHERE annee = ?")) {
            requete.setInt(1, annee);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    traitees.add(lignes.getString(1));
                }
            }
        }
    }

    public int nombrePonctuelles() {
        return ponctuelles.size();
    }

    public int nombreQuotidiennes() {
        return quotidiennes.size();
    }

    public boolean traitee(String id) {
        return traitees.contains(id);
    }

    /** Lance ce qui est dû (toutes les quelques secondes et au démarrage). */
    public void verifier() {
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        for (Action action : ponctuelles) {
            ZonedDateTime prevue = action.quand().atZone(maintenant.getZone());
            if (!prevue.isAfter(maintenant)) {
                traiter(action.id(), prevue, action.marge(), action.commandes(), action.quand().toLocalDate());
            }
        }
        LocalDate aujourdhui = maintenant.toLocalDate();
        for (Quotidienne quotidienne : quotidiennes) {
            LocalDate fin = quotidienne.au().isBefore(aujourdhui) ? quotidienne.au() : aujourdhui;
            for (LocalDate jour = quotidienne.du(); !jour.isAfter(fin); jour = jour.plusDays(1)) {
                ZonedDateTime prevue = jour.atTime(quotidienne.heure()).atZone(maintenant.getZone());
                if (!prevue.isAfter(maintenant)) {
                    traiter(quotidienne.id() + "-" + jour, prevue, quotidienne.marge(), quotidienne.commandes(), jour);
                }
            }
        }
    }

    private void traiter(String id, ZonedDateTime prevue, int marge, List<String> commandes, LocalDate date) {
        if (traitees.contains(id) || enCours.contains(id)) {
            return;
        }
        long retard = Duration.between(prevue, plugin.horloge().maintenant()).toMinutes();
        boolean lancer = retard <= marge;
        reclamer(id, lancer ? "fait" : "saute", prevue.toLocalDateTime(), reservee -> {
            if (!reservee) {
                return;
            }
            if (lancer) {
                int n = Console.executer(plugin.getLogger(), commandes, "{date}", date.toString(), "{annee}",
                        Integer.toString(annee), "{id}", id);
                plugin.getLogger().info("Action " + id + " lancée (" + n + " commande(s)" + (retard > 0 ? ", " + retard + " min de retard" : "") + ")");
            } else {
                plugin.getLogger().warning("Action " + id + " sautée : " + retard + " min de retard, marge " + marge + " min");
            }
        });
    }

    /**
     * Réserve un id une seule fois pour l'année (shxmas_actions). La suite reçoit vrai si cet appel l'a réservé ;
     * en cas d'erreur de base, rien n'est réservé et l'id pourra être retenté.
     */
    public void reclamer(String id, String statut, LocalDateTime prevue, Consumer<Boolean> suite) {
        if (traitees.contains(id) || !enCours.add(id)) {
            suite.accept(false);
            return;
        }
        LocalDateTime maintenant = plugin.horloge().local();
        base.lire(connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "INSERT IGNORE INTO shxmas_actions (annee, id, statut, prevue, traitee) VALUES (?, ?, ?, ?, ?)")) {
                requete.setInt(1, annee);
                requete.setString(2, id);
                requete.setString(3, statut);
                requete.setTimestamp(4, prevue == null ? null : Timestamp.valueOf(prevue));
                requete.setTimestamp(5, Timestamp.valueOf(maintenant));
                return requete.executeUpdate() == 1;
            }
        }).whenComplete((reservee, erreur) -> plugin.sync(() -> {
            enCours.remove(id);
            if (erreur != null) {
                plugin.getLogger().severe("Réservation de " + id + " impossible : " + erreur.getMessage());
                return;
            }
            traitees.add(id);
            suite.accept(reservee);
        }));
    }
}
