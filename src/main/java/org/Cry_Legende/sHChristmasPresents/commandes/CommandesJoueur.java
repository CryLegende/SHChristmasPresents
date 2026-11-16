package org.Cry_Legende.sHChristmasPresents.commandes;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /calendrier (/avent), /carte, /sapin [offrir &lt;n&gt;], /flocons, /cadeau &lt;joueur&gt; &lt;montant&gt;. Fil principal. */
public final class CommandesJoueur implements CommandExecutor, TabCompleter {

    private final SHChristmasPresents plugin;

    public CommandesJoueur(SHChristmasPresents plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender auteur, @NotNull Command commande, @NotNull String label, @NotNull String[] args) {
        if (!(auteur instanceof Player joueur)) {
            auteur.sendMessage(plugin.messages().prefixe("player-only"));
            return true;
        }
        String nom = commande.getName().toLowerCase(Locale.ROOT);
        String[] copie = args.clone();
        plugin.sync(() -> {
            if (joueur.isOnline()) {
                executer(joueur, nom, copie);
            }
        });
        return true;
    }

    private void executer(Player joueur, String commande, String[] args) {
        // Hors de l'Avent, du village et des emplettes : comme une commande inconnue (les admins passent, pour tester).
        if (!ListeCommandes.ouvertes(plugin.phase()) && !joueur.hasPermission("shchristmaspresents.admin")) {
            plugin.messages().envoyer(joueur, "commande-fermee");
            return;
        }
        switch (commande) {
            case "calendrier" -> plugin.calendrier().ouvrirMenu(joueur);
            case "carte" -> plugin.carte().ouvrirMenu(joueur);
            case "flocons" -> plugin.flocons().afficher(joueur);
            case "sapin" -> {
                if (args.length >= 1 && args[0].equalsIgnoreCase("offrir")) {
                    int n;
                    try {
                        n = args.length >= 2 ? Integer.parseInt(args[1].trim()) : 0;
                    } catch (NumberFormatException e) {
                        n = 0;
                    }
                    plugin.sapin().offrir(joueur, n);
                } else {
                    plugin.sapin().ouvrirMenu(joueur);
                }
            }
            case "cadeau" -> {
                if (args.length < 2) {
                    plugin.messages().envoyer(joueur, "cadeau.usage");
                    return;
                }
                plugin.cadeaux().envoyer(joueur, args[0], args[1]);
            }
            default -> {
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender auteur, @NotNull Command commande, @NotNull String label, @NotNull String[] args) {
        String nom = commande.getName().toLowerCase(Locale.ROOT);
        if (nom.equals("sapin") && args.length == 1) {
            return "offrir".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("offrir") : List.of();
        }
        if (nom.equals("sapin") && args.length == 2 && args[0].equalsIgnoreCase("offrir")) {
            List<String> montants = new ArrayList<>();
            plugin.sapin().offrandes().forEach(n -> montants.add(String.valueOf(n)));
            return montants;
        }
        if (nom.equals("cadeau") && args.length == 1) {
            String debut = args[0].toLowerCase(Locale.ROOT);
            List<String> noms = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(auteur) && p.getName().toLowerCase(Locale.ROOT).startsWith(debut)) {
                    noms.add(p.getName());
                }
            }
            return noms;
        }
        if (nom.equals("cadeau") && args.length == 2) {
            return List.of("20", "50", "100");
        }
        return List.of();
    }
}
