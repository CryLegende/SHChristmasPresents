package org.Cry_Legende.sHChristmasPresents.commun;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/** Recherche de joueurs par pseudo et petites aides autour du joueur. */
public final class Joueurs {

    /** Joueur trouvé : UUID du réseau, pseudo, et le joueur s'il est en ligne. */
    public record Cible(UUID uuid, String pseudo, Player enLigne) {
    }

    private Joueurs() {
    }

    /**
     * Joueur en ligne (pseudo exact, sans casse), sinon connu du cache du serveur, sinon UUID écrit en entier.
     * Jamais getOfflinePlayer(pseudo) : il interroge Mojang et rend l'UUID premium, pas celui du réseau.
     */
    public static Cible trouver(String nom) {
        if (nom == null || nom.isBlank()) {
            return null;
        }
        Player enLigne = Bukkit.getPlayerExact(nom);
        if (enLigne != null) {
            return new Cible(enLigne.getUniqueId(), enLigne.getName(), enLigne);
        }
        OfflinePlayer connu = Bukkit.getOfflinePlayerIfCached(nom);
        if (connu != null) {
            return new Cible(connu.getUniqueId(), connu.getName() != null ? connu.getName() : nom, null);
        }
        try {
            UUID uuid = UUID.fromString(nom);
            OfflinePlayer parUuid = Bukkit.getOfflinePlayer(uuid);
            Player present = parUuid.getPlayer();
            return new Cible(uuid, parUuid.getName() != null ? parUuid.getName() : nom, present);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Minutes de jeu (statistique PLAY_ONE_MINUTE, comptée en ticks). */
    public static int minutesDeJeu(Player joueur) {
        return joueur.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200;
    }

    /** Minutes de jeu d'un joueur hors ligne (lecture de son fichier de statistiques), -1 si inconnu. */
    public static int minutesDeJeuHorsLigne(UUID uuid) {
        try {
            OfflinePlayer joueur = Bukkit.getOfflinePlayer(uuid);
            if (!joueur.hasPlayedBefore()) {
                return -1;
            }
            return joueur.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Donne l'objet, le reste tombe aux pieds du joueur. */
    public static void donner(Player joueur, ItemStack objet) {
        Map<Integer, ItemStack> reste = joueur.getInventory().addItem(objet);
        for (ItemStack tombe : reste.values()) {
            joueur.getWorld().dropItem(joueur.getLocation(), tombe);
        }
    }
}
