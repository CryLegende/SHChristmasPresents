package org.Cry_Legende.sHChristmasPresents.commandes;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.horloge.Phase;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Commandes joueur (/calendrier, /carte, /sapin, /flocons, /cadeau et leurs alias) absentes de la liste du client hors
 * de l'Avent, du village et des emplettes : rien ne se voit avant le 1er décembre. La liste est renvoyée à chaque
 * changement de phase.
 */
public final class ListeCommandes implements Listener {

    public static final Set<String> NOMS = Set.of("calendrier", "carte", "sapin", "flocons", "cadeau");

    private final SHChristmasPresents plugin;
    private volatile Phase derniere;

    public ListeCommandes(SHChristmasPresents plugin) {
        this.plugin = plugin;
        this.derniere = plugin.phase();
    }

    /** Vrai pendant les phases où les commandes joueur servent. */
    public static boolean ouvertes(Phase phase) {
        return phase == Phase.AVENT || phase == Phase.VILLAGE || phase == Phase.EMPLETTES;
    }

    /** Noms à retirer de la liste : nos commandes, leurs alias et leurs formes « shchristmaspresents:… ». */
    private Set<String> nomsCaches() {
        Set<String> out = new HashSet<>();
        for (String nom : NOMS) {
            PluginCommand commande = plugin.getCommand(nom);
            if (commande == null) {
                continue;
            }
            out.add("shchristmaspresents:" + commande.getName().toLowerCase(Locale.ROOT));
            for (String alias : commande.getAliases()) {
                out.add("shchristmaspresents:" + alias.toLowerCase(Locale.ROOT));
            }
            // La forme courte n'est retirée que si elle mène bien à notre commande (pas à celle d'un autre plugin).
            PluginCommand courte = Bukkit.getPluginCommand(commande.getName());
            if (courte != null && courte.getPlugin() == plugin) {
                out.add(commande.getName().toLowerCase(Locale.ROOT));
            }
            for (String alias : commande.getAliases()) {
                PluginCommand parAlias = Bukkit.getPluginCommand(alias);
                if (parAlias != null && parAlias.getPlugin() == plugin) {
                    out.add(alias.toLowerCase(Locale.ROOT));
                }
            }
        }
        // Commandes d'autres plugins liées à l'évènement (liste-commandes-autres : le menu /noel), toutes formes
        for (String autre : plugin.getConfig().getStringList("liste-commandes-autres")) {
            String nom = autre.toLowerCase(Locale.ROOT).replaceFirst("^/", "");
            out.add(nom);
            out.add("deluxemenus:" + nom);
        }
        return out;
    }

    // MONITOR : EasyCommandBlocker remet dans la liste les commandes de sa liste blanche (dont /calendrier) avant nous.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onListe(PlayerCommandSendEvent event) {
        if (ouvertes(plugin.phase())) {
            return;
        }
        Set<String> caches = nomsCaches();
        event.getCommands().removeIf(nom -> caches.contains(nom.toLowerCase(Locale.ROOT))
                || caches.stream().anyMatch(c -> nom.toLowerCase(Locale.ROOT).endsWith(":" + c)));
    }

    /** Appelé au tic : à un changement de phase, chaque joueur reçoit sa nouvelle liste de commandes. */
    public void verifier() {
        Phase phase = plugin.phase();
        if (phase == derniere) {
            return;
        }
        boolean avant = ouvertes(derniere);
        derniere = phase;
        if (avant != ouvertes(phase)) {
            for (Player joueur : Bukkit.getOnlinePlayers()) {
                joueur.updateCommands();
            }
        }
    }
}
