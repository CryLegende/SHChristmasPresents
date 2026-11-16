package org.Cry_Legende.sHChristmasPresents.village;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.horloge.Phase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;

/**
 * Village fermé hors des phases village et emplettes : un joueur qui se connecte dans le monde du village (déconnecté
 * là l'an passé) part au spawn, un téléport vers ce monde (/home, /back, /tpa…) est annulé. Le droit
 * {@value #ACCES} (op par défaut) passe quand même, pour préparer le village.
 */
public final class Fermeture implements Listener {

    public static final String ACCES = "shchristmaspresents.village.acces";

    private final SHChristmasPresents plugin;
    private volatile boolean actif = true;
    private volatile String monde = "Christmas";
    private volatile List<String> commandesConnexion = List.of("spawn {joueur}");

    public Fermeture(SHChristmasPresents plugin) {
        this.plugin = plugin;
    }

    /** Section village.fermeture (absente : actif, monde Christmas, « spawn {joueur} »). */
    public void configurer(ConfigurationSection section) {
        if (section == null) {
            actif = true;
            monde = "Christmas";
            commandesConnexion = List.of("spawn {joueur}");
            return;
        }
        actif = section.getBoolean("actif", true);
        monde = section.getString("monde", "Christmas");
        List<String> lues = section.getStringList("commandes-connexion");
        commandesConnexion = lues.isEmpty() ? List.of("spawn {joueur}") : List.copyOf(lues);
    }

    /** Vrai pendant les phases où le village est ouvert aux joueurs. */
    public static boolean ouvert(Phase phase) {
        return phase == Phase.VILLAGE || phase == Phase.EMPLETTES;
    }

    private boolean ferme(Player joueur, World vers) {
        return actif && vers != null && vers.getName().equalsIgnoreCase(monde) && !ouvert(plugin.phase())
                && !joueur.hasPermission(ACCES);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location vers = event.getTo();
        if (vers == null || vers.getWorld() == null || vers.getWorld().equals(event.getFrom().getWorld())) {
            return;
        }
        if (ferme(event.getPlayer(), vers.getWorld())) {
            event.setCancelled(true);
            plugin.messages().envoyer(event.getPlayer(), "village.ferme");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrivee(PlayerJoinEvent event) {
        Player joueur = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joueur.isOnline() && ferme(joueur, joueur.getWorld())) {
                Console.executer(plugin.getLogger(), commandesConnexion, "{joueur}", joueur.getName());
                plugin.getLogger().info("Village fermé : " + joueur.getName() + " connecté dans " + monde + ", renvoyé");
            }
        }, 1L);
    }
}
