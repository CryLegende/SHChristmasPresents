package org.Cry_Legende.sHChristmasPresents.livraison;

import org.Cry_Legende.sHChristmasPresents.stockage.Base;
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
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Récompenses gardées pour un joueur hors ligne (shxmas_livraisons), livrées une fois à sa connexion suivante.
 * Chaque type a son traitement (ex. sapin-palier : commandes de contributeur d'un palier).
 */
public final class Livraisons {

    public record Livraison(String type, String ref) {
    }

    private final Base base;
    private final int annee;
    private final Logger log;
    private final Supplier<LocalDateTime> horloge;
    private final Map<UUID, Set<Livraison>> enAttente = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<Player, String>> types = new ConcurrentHashMap<>();

    public Livraisons(Base base, int annee, Logger log, Supplier<LocalDateTime> horloge) {
        this.base = base;
        this.annee = annee;
        this.log = log;
        this.horloge = horloge;
    }

    public void charger(Connection connexion) throws SQLException {
        try (PreparedStatement r = connexion.prepareStatement(
                "SELECT uuid, type, ref FROM shxmas_livraisons WHERE annee = ? AND livree IS NULL")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    enAttente.computeIfAbsent(UUID.fromString(l.getString(1)), k -> ConcurrentHashMap.newKeySet())
                            .add(new Livraison(l.getString(2), l.getString(3)));
                }
            }
        }
    }

    public void type(String type, BiConsumer<Player, String> traitement) {
        types.put(type, traitement);
    }

    public int nombre(UUID uuid) {
        Set<Livraison> attente = enAttente.get(uuid);
        return attente == null ? 0 : attente.size();
    }

    public int total() {
        int n = 0;
        for (Set<Livraison> attente : enAttente.values()) {
            n += attente.size();
        }
        return n;
    }

    /** Garde une livraison (une seule par joueur, type et référence). */
    public void ajouter(UUID uuid, String type, String ref) {
        if (!enAttente.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(new Livraison(type, ref))) {
            return;
        }
        LocalDateTime maintenant = horloge.get();
        base.ecrire("livraison " + type, c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT IGNORE INTO shxmas_livraisons (annee, uuid, type, ref, cree)"
                    + " VALUES (?, ?, ?, ?, ?)")) {
                r.setInt(1, annee);
                r.setString(2, uuid.toString());
                r.setString(3, type);
                r.setString(4, ref);
                r.setTimestamp(5, Timestamp.valueOf(maintenant));
                r.executeUpdate();
            }
        });
    }

    /** Livre ce qui attend le joueur (fil principal, à la connexion). */
    public void livrer(Player joueur) {
        Set<Livraison> attente = enAttente.remove(joueur.getUniqueId());
        if (attente == null || attente.isEmpty()) {
            return;
        }
        LocalDateTime maintenant = horloge.get();
        List<Livraison> liste = new ArrayList<>(attente);
        for (Livraison livraison : liste) {
            base.ecrire("livraison faite", c -> {
                try (PreparedStatement r = c.prepareStatement("UPDATE shxmas_livraisons SET livree = ? WHERE annee = ? AND uuid = ?"
                        + " AND type = ? AND ref = ?")) {
                    r.setTimestamp(1, Timestamp.valueOf(maintenant));
                    r.setInt(2, annee);
                    r.setString(3, joueur.getUniqueId().toString());
                    r.setString(4, livraison.type());
                    r.setString(5, livraison.ref());
                    r.executeUpdate();
                }
            });
            BiConsumer<Player, String> traitement = types.get(livraison.type());
            if (traitement == null) {
                log.warning("Livraison de type inconnu pour " + joueur.getName() + " : " + livraison.type());
                continue;
            }
            traitement.accept(joueur, livraison.ref());
            log.info("Livraison " + livraison.type() + " " + livraison.ref() + " faite à " + joueur.getName());
        }
    }
}
