package org.Cry_Legende.sHChristmasPresents.flocons;

import me.clip.placeholderapi.PlaceholderAPI;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Portefeuille des Flocons (PlayerPoints) piloté par commandes : versement, retrait, solde lu par placeholder. Aucun
 * appel à l'API de PlayerPoints. Ses commandes s'appliquent parfois un ou deux ticks plus tard : pour un joueur en
 * ligne, chaque opération attend que le solde la montre avant la suivante, et un retrait n'est confirmé (suite) qu'une
 * fois vu. Fil principal seulement.
 */
public final class Portefeuille {

    /** Résultat d'un retrait. */
    public enum Retrait {
        FAIT, INSUFFISANT, ILLISIBLE, ECHEC
    }

    private record Operation(boolean retrait, UUID uuid, String pseudo, int montant, Consumer<Retrait> suite) {
    }

    private static final class EnCours {
        final Operation operation;
        final long avant;
        final long attendu;
        int ticks;

        EnCours(Operation operation, long avant, long attendu) {
            this.operation = operation;
            this.avant = avant;
            this.attendu = attendu;
        }
    }

    private final Plugin plugin;
    private final Map<UUID, ArrayDeque<Operation>> files = new HashMap<>();
    private final Map<UUID, EnCours> enCours = new HashMap<>();
    private BukkitTask tache;
    private volatile String donner = "points give {joueur} {montant}";
    private volatile String retirer = "points take {joueur} {montant}";
    private volatile String placeholderSolde = "%playerpoints_points%";
    private volatile int attenteTicks = 60;

    public Portefeuille(Plugin plugin) {
        this.plugin = plugin;
    }

    public void configurer(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        donner = section.getString("donner", donner);
        retirer = section.getString("retirer", retirer);
        placeholderSolde = section.getString("solde-placeholder", placeholderSolde);
        attenteTicks = Math.max(1, section.getInt("attente-ticks", 60));
    }

    private static boolean papi() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    /** Solde lu par le placeholder, -1 s'il n'est pas lisible. */
    public long solde(OfflinePlayer joueur) {
        if (joueur == null || !papi()) {
            return -1;
        }
        String texte = PlaceholderAPI.setPlaceholders(joueur, placeholderSolde);
        if (texte == null) {
            return -1;
        }
        String chiffres = texte.replaceAll("[\\s\\u00A0\\u202F,.]", "");
        try {
            return Long.parseLong(chiffres);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Vrai tant qu'une opération du joueur attend d'être vue. */
    public boolean occupe(UUID uuid) {
        return enCours.containsKey(uuid) || files.containsKey(uuid);
    }

    /** Verse des Flocons. Hors ligne : la commande part tout de suite (rien à attendre). */
    public void donner(UUID uuid, String pseudo, int montant) {
        if (montant <= 0) {
            return;
        }
        if (Bukkit.getPlayer(uuid) == null) {
            commande(donner, pseudo, montant);
            return;
        }
        enfiler(new Operation(false, uuid, pseudo, montant, null));
    }

    /**
     * Retire des Flocons à un joueur en ligne. La suite reçoit FAIT une fois le retrait vu sur le solde ; INSUFFISANT
     * ou ILLISIBLE sans rien retirer ; ECHEC si rien n'est vu à temps (journalisé avec les soldes).
     */
    public void retirer(Player joueur, int montant, Consumer<Retrait> suite) {
        enfiler(new Operation(true, joueur.getUniqueId(), joueur.getName(), montant, suite));
    }

    private void enfiler(Operation operation) {
        files.computeIfAbsent(operation.uuid(), k -> new ArrayDeque<>()).add(operation);
        suivante(operation.uuid());
    }

    private void suivante(UUID uuid) {
        while (!enCours.containsKey(uuid)) {
            ArrayDeque<Operation> file = files.get(uuid);
            Operation operation = file == null ? null : file.poll();
            if (operation == null) {
                files.remove(uuid);
                return;
            }
            if (file.isEmpty()) {
                files.remove(uuid);
            }
            lancer(operation);
        }
    }

    private void lancer(Operation op) {
        OfflinePlayer joueur = Bukkit.getOfflinePlayer(op.uuid());
        long avant = solde(joueur);
        if (op.retrait()) {
            if (avant < 0) {
                plugin.getLogger().severe("Solde de " + op.pseudo() + " illisible (" + placeholderSolde + ") : retrait refusé");
                op.suite().accept(Retrait.ILLISIBLE);
                return;
            }
            if (avant < op.montant()) {
                op.suite().accept(Retrait.INSUFFISANT);
                return;
            }
            commande(retirer, op.pseudo(), op.montant());
        } else {
            commande(donner, op.pseudo(), op.montant());
            if (avant < 0 || Bukkit.getPlayer(op.uuid()) == null) {
                return;
            }
        }
        EnCours e = new EnCours(op, avant, op.retrait() ? avant - op.montant() : avant + op.montant());
        enCours.put(op.uuid(), e);
        if (!verifier(op.uuid(), e) && tache == null) {
            tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tic, 1L, 1L);
        }
    }

    private void tic() {
        for (Map.Entry<UUID, EnCours> entree : List.copyOf(enCours.entrySet())) {
            verifier(entree.getKey(), entree.getValue());
        }
        if (enCours.isEmpty() && tache != null) {
            tache.cancel();
            tache = null;
        }
    }

    /** Rend vrai si l'opération est terminée (vue, ou abandonnée faute d'être vue à temps). */
    private boolean verifier(UUID uuid, EnCours e) {
        if (enCours.get(uuid) != e) {
            return true;
        }
        long maintenant = solde(Bukkit.getOfflinePlayer(uuid));
        Operation op = e.operation;
        boolean vu = maintenant >= 0 && (op.retrait() ? maintenant <= e.attendu : maintenant >= e.attendu);
        if (!vu && ++e.ticks <= attenteTicks) {
            return false;
        }
        enCours.remove(uuid);
        if (op.retrait()) {
            if (!vu) {
                plugin.getLogger().warning("Retrait de " + op.montant() + " à " + op.pseudo() + " non vu en " + attenteTicks
                        + " ticks (solde " + e.avant + " puis " + maintenant + ") : rien n'est donné en échange");
            }
            op.suite().accept(vu ? Retrait.FAIT : Retrait.ECHEC);
        } else if (!vu) {
            plugin.getLogger().warning("Versement de " + op.montant() + " à " + op.pseudo() + " non vu en " + attenteTicks
                    + " ticks (solde " + e.avant + " puis " + maintenant + ")");
        }
        suivante(uuid);
        return true;
    }

    private void commande(String modele, String pseudo, int montant) {
        Console.executer(plugin.getLogger(), List.of(modele), "{joueur}", pseudo, "{montant}", Integer.toString(montant));
    }

    /** Arrêt : les opérations qui attendent sont abandonnées (journalisées). */
    public void arreter() {
        if (tache != null) {
            tache.cancel();
            tache = null;
        }
        for (EnCours e : enCours.values()) {
            plugin.getLogger().warning("Arrêt pendant une opération de portefeuille : " + e.operation);
        }
        enCours.clear();
        files.clear();
    }
}
