package org.Cry_Legende.sHChristmasPresents.retour;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.Cry_Legende.sHChristmasPresents.joueurs.Profil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retour des absents : un joueur qui se connecte pendant les gains après absent-jours sans venir (dernière connexion
 * lue avant la connexion) reçoit les commandes de retour, une fois par évènement, et un multiplicateur personnel.
 */
public final class Retour implements Listener {

    private final SHChristmasPresents plugin;
    private final Map<UUID, Long> derniereConnexion = new ConcurrentHashMap<>();
    private volatile boolean actif = true;
    private volatile int absentJours = 14;
    private volatile double multiplicateur = 2;
    private volatile int heures = 72;
    private volatile List<String> commandes = List.of();

    public Retour(SHChristmasPresents plugin) {
        this.plugin = plugin;
    }

    public void configurer(ConfigurationSection s) {
        if (s == null) {
            return;
        }
        actif = s.getBoolean("actif", true);
        absentJours = Math.max(1, s.getInt("absent-jours", 14));
        multiplicateur = Math.max(1, s.getDouble("multiplicateur", 2));
        heures = Math.max(0, s.getInt("multiplicateur-heures", 72));
        commandes = LectureConfig.textes(s, "commandes");
    }

    /** Dernière connexion connue, relevée avant que le joueur n'entre (fil de connexion). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreConnexion(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            OfflinePlayer joueur = Bukkit.getOfflinePlayer(event.getUniqueId());
            derniereConnexion.put(event.getUniqueId(), joueur.hasPlayedBefore() ? joueur.getLastSeen() : 0L);
        } catch (RuntimeException e) {
            derniereConnexion.put(event.getUniqueId(), 0L);
        }
    }

    /** À la connexion (fil principal). */
    public void connexion(Player joueur) {
        Long vu = derniereConnexion.remove(joueur.getUniqueId());
        if (!actif || vu == null || vu <= 0 || !plugin.dates().gains(plugin.horloge().maintenant())) {
            return;
        }
        long absence = plugin.horloge().millis() - vu;
        if (absence < absentJours * 86_400_000L) {
            return;
        }
        Profil profil = plugin.profils().obtenir(joueur.getUniqueId(), joueur.getName());
        if (profil.retour != null) {
            return;
        }
        LocalDateTime maintenant = plugin.horloge().local();
        profil.retour = maintenant;
        if (heures > 0 && multiplicateur > 1) {
            profil.multiPerso = multiplicateur;
            profil.multiPersoFin = maintenant.plusHours(heures);
        }
        plugin.profils().sauver(profil);
        long jours = absence / 86_400_000L;
        Console.executer(plugin.getLogger(), commandes, "{joueur}", joueur.getName(), "{uuid}", joueur.getUniqueId().toString(),
                "{jours}", jours);
        plugin.messages().envoyer(joueur, "retour.bienvenue", "{jours}", jours, "{facteur}", Textes.facteur(multiplicateur),
                "{heures}", heures);
        plugin.son("retour", joueur);
        plugin.getLogger().info("Retour : " + joueur.getName() + " absent depuis " + jours + " jours");
    }
}
