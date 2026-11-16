package org.Cry_Legende.sHChristmasPresents.stockage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Valeurs globales de shxmas_etat (sapin, multiplicateur, médianes des fenêtres…) : en mémoire, écrites en file.
 * Les valeurs « globales » (annee = 0) ne dépendent pas de l'année : sel des empreintes, horloge de test.
 */
public final class Etat {

    private final Base base;
    private final int annee;
    private final Map<String, String> valeurs = new ConcurrentHashMap<>();
    private final Map<String, String> globales = new ConcurrentHashMap<>();

    public Etat(Base base, int annee) {
        this.base = base;
        this.annee = annee;
    }

    public void charger(Connection connexion) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT annee, cle, valeur FROM shxmas_etat WHERE annee IN (0, ?)")) {
            requete.setInt(1, annee);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    (lignes.getInt(1) == 0 ? globales : valeurs).put(lignes.getString(2), lignes.getString(3));
                }
            }
        }
    }

    public String get(String cle) {
        return valeurs.get(cle);
    }

    public void set(String cle, String valeur) {
        ecrire(annee, cle, valeur, valeurs);
    }

    public String globale(String cle) {
        return globales.get(cle);
    }

    public void setGlobale(String cle, String valeur) {
        ecrire(0, cle, valeur, globales);
    }

    private void ecrire(int an, String cle, String valeur, Map<String, String> carte) {
        if (valeur == null) {
            carte.remove(cle);
            base.ecrire("etat " + cle, c -> {
                try (PreparedStatement requete = c.prepareStatement("DELETE FROM shxmas_etat WHERE annee = ? AND cle = ?")) {
                    requete.setInt(1, an);
                    requete.setString(2, cle);
                    requete.executeUpdate();
                }
            });
            return;
        }
        carte.put(cle, valeur);
        base.ecrire("etat " + cle, c -> {
            try (PreparedStatement requete = c.prepareStatement("INSERT INTO shxmas_etat (annee, cle, valeur) VALUES (?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE valeur = VALUES(valeur)")) {
                requete.setInt(1, an);
                requete.setString(2, cle);
                requete.setString(3, valeur);
                requete.executeUpdate();
            }
        });
    }
}
