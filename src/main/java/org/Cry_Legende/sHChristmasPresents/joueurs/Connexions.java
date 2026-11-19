package org.Cry_Legende.sHChristmasPresents.joueurs;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Joueurs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Connexion : profil (empreinte d'IP, temps de jeu), retour des absents, puis livraisons, cadeaux reçus, tampons,
 * rappel du calendrier et récapitulatif, un peu après l'arrivée pour ne pas se perdre dans les messages de connexion.
 */
public final class Connexions implements Listener {

    private final SHChristmasPresents plugin;
    /** Jour du dernier message du jour de chaque joueur (mémoire : un redémarrage peut le renvoyer une fois). */
    private final java.util.Map<java.util.UUID, java.time.LocalDate> messageDuJour = new java.util.concurrent.ConcurrentHashMap<>();

    public Connexions(SHChristmasPresents plugin) {
        this.plugin = plugin;
    }

    /** Commandes de connexion.phases.&lt;phase&gt; (console, {joueur}), une fois par jour et par joueur. */
    private void messageDuJour(Player joueur) {
        org.bukkit.configuration.ConfigurationSection c = plugin.getConfig().getConfigurationSection("connexion.phases");
        if (c == null || !joueur.isOnline()) {
            return;
        }
        java.util.List<String> commandes = c.getStringList(plugin.phase().id());
        if (commandes.isEmpty()) {
            return;
        }
        java.time.LocalDate jour = plugin.horloge().aujourdhui();
        if (jour.equals(messageDuJour.put(joueur.getUniqueId(), jour))) {
            return;
        }
        org.Cry_Legende.sHChristmasPresents.commun.Console.executer(plugin.getLogger(), commandes, "{joueur}", joueur.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrivee(PlayerJoinEvent event) {
        Player joueur = event.getPlayer();
        plugin.sync(() -> arrivee(joueur));
    }

    /** Fil principal ; aussi pour les joueurs déjà en ligne quand le plugin démarre. */
    public void arrivee(Player joueur) {
        if (!joueur.isOnline()) {
            return;
        }
        Profil profil = plugin.profils().obtenir(joueur.getUniqueId(), joueur.getName());
        profil.empreinte = plugin.empreintes().de(joueur);
        profil.tempsJeu = Joueurs.minutesDeJeu(joueur);
        plugin.profils().sauver(profil);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!joueur.isOnline()) {
                return;
            }
            plugin.retour().connexion(joueur);
            plugin.livraisons().livrer(joueur);
            plugin.cadeaux().livrerEnAttente(joueur);
            plugin.carte().verifierTout(joueur);
            plugin.calendrier().rappel(joueur);
        }, 60L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.recap().connexion(joueur), 100L);
        long delai = Math.max(1, plugin.getConfig().getLong("connexion.delai-secondes", 5)) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> messageDuJour(joueur), delai);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDepart(PlayerQuitEvent event) {
        Player joueur = event.getPlayer();
        int minutes = Joueurs.minutesDeJeu(joueur);
        plugin.sync(() -> {
            Profil profil = plugin.profils().get(joueur.getUniqueId());
            if (profil != null) {
                profil.tempsJeu = minutes;
                plugin.profils().sauver(profil);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonde(PlayerChangedWorldEvent event) {
        Player joueur = event.getPlayer();
        plugin.sync(() -> {
            if (joueur.isOnline()) {
                plugin.carte().visite(joueur);
            }
        });
    }
}
