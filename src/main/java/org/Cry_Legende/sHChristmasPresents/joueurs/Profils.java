package org.Cry_Legende.sHChristmasPresents.joueurs;

import org.Cry_Legende.sHChristmasPresents.stockage.Base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Profils de l'année, tous en mémoire (quelques centaines de lignes), écrits en file à chaque changement. */
public final class Profils {

    private final Base base;
    private final int annee;
    private final Map<UUID, Profil> profils = new ConcurrentHashMap<>();

    public Profils(Base base, int annee) {
        this.base = base;
        this.annee = annee;
    }

    public void charger(Connection connexion) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("SELECT uuid, pseudo, empreinte, temps_jeu, bienvenue,"
                + " carte_complete, retour, multi_perso, multi_perso_fin, recap, premiere_visite FROM shxmas_joueurs WHERE annee = ?")) {
            requete.setInt(1, annee);
            try (ResultSet l = requete.executeQuery()) {
                while (l.next()) {
                    Profil p = new Profil(UUID.fromString(l.getString(1)), l.getString(2));
                    p.empreinte = l.getString(3);
                    p.tempsJeu = l.getInt(4);
                    p.bienvenue = date(l.getTimestamp(5));
                    p.carteComplete = date(l.getTimestamp(6));
                    p.retour = date(l.getTimestamp(7));
                    p.multiPerso = l.getDouble(8);
                    p.multiPersoFin = date(l.getTimestamp(9));
                    p.recap = date(l.getTimestamp(10));
                    p.premiereVisite = date(l.getTimestamp(11));
                    profils.put(p.uuid, p);
                }
            }
        }
    }

    private static LocalDateTime date(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static Timestamp ts(LocalDateTime date) {
        return date == null ? null : Timestamp.valueOf(date);
    }

    public Profil get(UUID uuid) {
        return profils.get(uuid);
    }

    /** Profil du joueur, créé s'il n'existe pas (fil principal). */
    public Profil obtenir(UUID uuid, String pseudo) {
        Profil profil = profils.computeIfAbsent(uuid, id -> new Profil(id, pseudo));
        if (pseudo != null && !pseudo.equals(profil.pseudo)) {
            profil.pseudo = pseudo;
        }
        return profil;
    }

    public Collection<Profil> tous() {
        return profils.values();
    }

    public int nombre() {
        return profils.size();
    }

    /** Écrit la ligne entière (valeurs du moment). */
    public void sauver(Profil p) {
        String pseudo = p.pseudo == null ? "?" : p.pseudo;
        String empreinte = p.empreinte;
        int temps = p.tempsJeu;
        Timestamp bienvenue = ts(p.bienvenue), carte = ts(p.carteComplete), retour = ts(p.retour), fin = ts(p.multiPersoFin),
                recap = ts(p.recap), visite = ts(p.premiereVisite);
        double multi = p.multiPerso;
        base.ecrire("profil " + pseudo, c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT INTO shxmas_joueurs (annee, uuid, pseudo, empreinte, temps_jeu,"
                    + " bienvenue, carte_complete, retour, multi_perso, multi_perso_fin, recap, premiere_visite)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE pseudo = VALUES(pseudo),"
                    + " empreinte = VALUES(empreinte), temps_jeu = VALUES(temps_jeu), bienvenue = VALUES(bienvenue),"
                    + " carte_complete = VALUES(carte_complete), retour = VALUES(retour), multi_perso = VALUES(multi_perso),"
                    + " multi_perso_fin = VALUES(multi_perso_fin), recap = VALUES(recap), premiere_visite = VALUES(premiere_visite)")) {
                r.setInt(1, annee);
                r.setString(2, p.uuid.toString());
                r.setString(3, pseudo);
                r.setString(4, empreinte);
                r.setInt(5, temps);
                r.setTimestamp(6, bienvenue);
                r.setTimestamp(7, carte);
                r.setTimestamp(8, retour);
                r.setDouble(9, multi);
                r.setTimestamp(10, fin);
                r.setTimestamp(11, recap);
                r.setTimestamp(12, visite);
                r.executeUpdate();
            }
        });
    }
}
