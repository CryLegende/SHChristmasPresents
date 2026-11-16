package org.Cry_Legende.sHChristmasPresents.carte;

import org.Cry_Legende.sHChristmasPresents.stockage.Base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compteurs de l'évènement par joueur (portes, maisons, soirees, defis, offert, cadeaux-envoyes…), shxmas_compteurs.
 * Écrits sur le fil principal, lus de partout.
 */
public final class Compteurs {

    private final Base base;
    private final int annee;
    private final Map<UUID, Map<String, Integer>> valeurs = new ConcurrentHashMap<>();

    public Compteurs(Base base, int annee) {
        this.base = base;
        this.annee = annee;
    }

    public void charger(Connection connexion) throws SQLException {
        try (PreparedStatement r = connexion.prepareStatement("SELECT uuid, compteur, valeur FROM shxmas_compteurs WHERE annee = ?")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    valeurs.computeIfAbsent(UUID.fromString(l.getString(1)), k -> new ConcurrentHashMap<>())
                            .put(l.getString(2), l.getInt(3));
                }
            }
        }
    }

    public int valeur(UUID uuid, String id) {
        Map<String, Integer> joueur = valeurs.get(uuid);
        return joueur == null ? 0 : joueur.getOrDefault(id, 0);
    }

    /** Ajoute n (fil principal) et rend la nouvelle valeur. */
    public int ajouter(UUID uuid, String id, int n) {
        Map<String, Integer> joueur = valeurs.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        int nouvelle = Math.max(0, joueur.getOrDefault(id, 0) + n);
        joueur.put(id, nouvelle);
        base.ecrire("compteur " + id, c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT INTO shxmas_compteurs (annee, uuid, compteur, valeur)"
                    + " VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE valeur = VALUES(valeur)")) {
                r.setInt(1, annee);
                r.setString(2, uuid.toString());
                r.setString(3, id);
                r.setInt(4, nouvelle);
                r.executeUpdate();
            }
        });
        return nouvelle;
    }

    /** Somme d'un compteur sur tous les joueurs (bilan). */
    public long somme(String id) {
        long total = 0;
        for (Map<String, Integer> joueur : valeurs.values()) {
            total += joueur.getOrDefault(id, 0);
        }
        return total;
    }
}
