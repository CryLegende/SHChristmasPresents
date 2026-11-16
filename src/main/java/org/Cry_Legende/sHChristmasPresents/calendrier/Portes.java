package org.Cry_Legende.sHChristmasPresents.calendrier;

import org.Cry_Legende.sHChristmasPresents.stockage.Base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Portes ouvertes (shxmas_portes) : par joueur, et par porte le nombre de comptes de chaque empreinte d'IP.
 * Écrit sur le fil principal ; l'index des empreintes n'est lu que là.
 */
final class Portes {

    private final Base base;
    private final int annee;
    private final Map<UUID, Set<Integer>> parJoueur = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, Integer>> parEmpreinte = new HashMap<>();

    Portes(Base base, int annee) {
        this.base = base;
        this.annee = annee;
    }

    void charger(Connection connexion) throws SQLException {
        try (PreparedStatement r = connexion.prepareStatement("SELECT uuid, porte, empreinte FROM shxmas_portes WHERE annee = ?")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    int porte = l.getInt(2);
                    parJoueur.computeIfAbsent(UUID.fromString(l.getString(1)), k -> ConcurrentHashMap.newKeySet()).add(porte);
                    String empreinte = l.getString(3);
                    if (empreinte != null) {
                        parEmpreinte.computeIfAbsent(porte, k -> new HashMap<>()).merge(empreinte, 1, Integer::sum);
                    }
                }
            }
        }
    }

    boolean ouverte(UUID uuid, int porte) {
        Set<Integer> portes = parJoueur.get(uuid);
        return portes != null && portes.contains(porte);
    }

    int nombre(UUID uuid) {
        Set<Integer> portes = parJoueur.get(uuid);
        return portes == null ? 0 : portes.size();
    }

    int joueurs() {
        return parJoueur.size();
    }

    /** Comptes qui ont déjà ouvert cette porte avec cette empreinte. */
    int comptes(int porte, String empreinte) {
        Map<String, Integer> index = parEmpreinte.get(porte);
        return index == null || empreinte == null ? 0 : index.getOrDefault(empreinte, 0);
    }

    void ajouter(UUID uuid, int porte, String empreinte, boolean rattrapage, LocalDateTime quand) {
        parJoueur.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(porte);
        if (empreinte != null) {
            parEmpreinte.computeIfAbsent(porte, k -> new HashMap<>()).merge(empreinte, 1, Integer::sum);
        }
        base.ecrire("porte " + porte, c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT IGNORE INTO shxmas_portes (annee, uuid, porte, empreinte,"
                    + " rattrapage, ouverte) VALUES (?, ?, ?, ?, ?, ?)")) {
                r.setInt(1, annee);
                r.setString(2, uuid.toString());
                r.setInt(3, porte);
                r.setString(4, empreinte);
                r.setBoolean(5, rattrapage);
                r.setTimestamp(6, Timestamp.valueOf(quand));
                r.executeUpdate();
            }
        });
    }
}
