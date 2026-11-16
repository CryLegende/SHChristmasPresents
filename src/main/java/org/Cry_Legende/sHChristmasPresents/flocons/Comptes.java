package org.Cry_Legende.sHChristmasPresents.flocons;

import org.Cry_Legende.sHChristmasPresents.stockage.Base;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gains de chaque joueur : total de l'évènement, et pour le jour en cours, base et versé par source (relus de
 * shxmas_gains au démarrage). Le journal shxmas_gains garde chaque gain. Écrit sur le fil principal, lu de partout.
 */
public final class Comptes {

    /** Compte d'un joueur. */
    public static final class Compte {
        final UUID uuid;
        volatile String pseudo;
        volatile LocalDate jour;
        final Map<String, long[]> parSource = new ConcurrentHashMap<>();
        final Map<String, Double> restes = new ConcurrentHashMap<>();
        volatile long total;

        Compte(UUID uuid, String pseudo) {
            this.uuid = uuid;
            this.pseudo = pseudo;
        }

        public String pseudo() {
            return pseudo;
        }

        public long total() {
            return total;
        }
    }

    private final Base base;
    private final int annee;
    private final Map<UUID, Compte> comptes = new ConcurrentHashMap<>();

    public Comptes(Base base, int annee) {
        this.base = base;
        this.annee = annee;
    }

    public void charger(Connection connexion, LocalDate aujourdhui) throws SQLException {
        try (PreparedStatement r = connexion.prepareStatement(
                "SELECT uuid, MAX(pseudo), SUM(verse) FROM shxmas_gains WHERE annee = ? GROUP BY uuid")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    Compte c = new Compte(UUID.fromString(l.getString(1)), l.getString(2));
                    c.total = l.getLong(3);
                    c.jour = aujourdhui;
                    comptes.put(c.uuid, c);
                }
            }
        }
        try (PreparedStatement r = connexion.prepareStatement("SELECT uuid, source, SUM(base), SUM(verse) FROM shxmas_gains"
                + " WHERE annee = ? AND jour = ? GROUP BY uuid, source")) {
            r.setInt(1, annee);
            r.setDate(2, Date.valueOf(aujourdhui));
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    Compte c = comptes.get(UUID.fromString(l.getString(1)));
                    if (c != null) {
                        c.parSource.put(l.getString(2), new long[]{l.getLong(3), l.getLong(4)});
                    }
                }
            }
        }
    }

    /** Compte du joueur pour aujourd'hui, créé ou remis au jour (fil principal). */
    Compte compte(UUID uuid, String pseudo, LocalDate aujourdhui) {
        Compte c = comptes.computeIfAbsent(uuid, id -> new Compte(id, pseudo));
        if (pseudo != null) {
            c.pseudo = pseudo;
        }
        if (!aujourdhui.equals(c.jour)) {
            c.parSource.clear();
            c.restes.clear();
            c.jour = aujourdhui;
        }
        return c;
    }

    private long[] jour(UUID uuid, String source, LocalDate aujourdhui) {
        Compte c = comptes.get(uuid);
        if (c == null || !aujourdhui.equals(c.jour)) {
            return null;
        }
        return c.parSource.get(source);
    }

    /** Flocons de base de cette source comptés aujourd'hui. */
    public long baseJour(UUID uuid, String source, LocalDate aujourdhui) {
        long[] v = jour(uuid, source, aujourdhui);
        return v == null ? 0 : v[0];
    }

    /** Flocons versés aujourd'hui par cette source. */
    public long verseJour(UUID uuid, String source, LocalDate aujourdhui) {
        long[] v = jour(uuid, source, aujourdhui);
        return v == null ? 0 : v[1];
    }

    /** Flocons versés aujourd'hui, toutes sources. */
    public long verseJour(UUID uuid, LocalDate aujourdhui) {
        Compte c = comptes.get(uuid);
        if (c == null || !aujourdhui.equals(c.jour)) {
            return 0;
        }
        long somme = 0;
        for (long[] v : c.parSource.values()) {
            somme += v[1];
        }
        return somme;
    }

    /** Sources qui ont rapporté quelque chose aujourd'hui (versé par source). */
    public Map<String, long[]> jourParSource(UUID uuid, LocalDate aujourdhui) {
        Compte c = comptes.get(uuid);
        Map<String, long[]> out = new HashMap<>();
        if (c != null && aujourdhui.equals(c.jour)) {
            c.parSource.forEach((source, v) -> out.put(source, v.clone()));
        }
        return out;
    }

    public long total(UUID uuid) {
        Compte c = comptes.get(uuid);
        return c == null ? 0 : c.total;
    }

    public Compte get(UUID uuid) {
        return comptes.get(uuid);
    }

    /** Totaux de l'évènement par joueur (copie). */
    public Map<UUID, Long> totaux() {
        Map<UUID, Long> out = new HashMap<>();
        comptes.forEach((uuid, c) -> out.put(uuid, c.total));
        return out;
    }

    public Map<UUID, Compte> tous() {
        return comptes;
    }

    void journal(UUID uuid, String pseudo, String source, int base_, int verse, double facteur, LocalDate jour, LocalDateTime ts) {
        base.ecrire("gain " + pseudo, c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT INTO shxmas_gains (annee, uuid, pseudo, source, base, verse,"
                    + " facteur, jour, ts) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                r.setInt(1, annee);
                r.setString(2, uuid.toString());
                r.setString(3, pseudo);
                r.setString(4, source);
                r.setInt(5, base_);
                r.setInt(6, verse);
                r.setDouble(7, facteur);
                r.setDate(8, Date.valueOf(jour));
                r.setTimestamp(9, Timestamp.valueOf(ts));
                r.executeUpdate();
            }
        });
    }
}
