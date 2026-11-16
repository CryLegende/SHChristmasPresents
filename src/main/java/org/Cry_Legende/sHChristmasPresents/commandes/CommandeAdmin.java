package org.Cry_Legende.sHChristmasPresents.commandes;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.commun.Joueurs;
import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /shchristmaspresents (alias /xmas, /scp) : chaque sous-commande a sa permission shchristmaspresents.admin.&lt;nom&gt;
 * et marche depuis la console (sauf setregion, createclicker, removeclicker). Toujours jouée sur le fil principal.
 */
public final class CommandeAdmin implements CommandExecutor, TabCompleter {

    private static final List<String> SOUS_COMMANDES = List.of("give", "setregion", "createclicker", "removeclicker",
            "listclickers", "reload", "info", "flocons", "multiplicateur", "compteur", "tampon", "acheter", "sapin", "drop",
            "horloge", "bilan", "tous");

    private final SHChristmasPresents plugin;

    public CommandeAdmin(SHChristmasPresents plugin) {
        this.plugin = plugin;
    }

    private List<String> permises(CommandSender auteur) {
        List<String> out = new ArrayList<>();
        for (String sous : SOUS_COMMANDES) {
            if (auteur.hasPermission("shchristmaspresents.admin." + sous)) {
                out.add(sous);
            }
        }
        return out;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender auteur, @NotNull Command commande, @NotNull String label, @NotNull String[] args) {
        if (!plugin.estPrincipal()) {
            String[] copie = args.clone();
            plugin.sync(() -> executer(auteur, copie));
            return true;
        }
        executer(auteur, args);
        return true;
    }

    private void executer(CommandSender auteur, String[] args) {
        List<String> permises = permises(auteur);
        if (permises.isEmpty()) {
            return;
        }
        String sous = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!permises.contains(sous)) {
            auteur.sendMessage(plugin.messages().prefixe("usage", "%subcommands%", String.join("|", permises)));
            return;
        }
        var m = plugin.messages();
        switch (sous) {
            case "give" -> give(auteur, args);
            case "setregion" -> {
                if (!(auteur instanceof Player joueur)) {
                    auteur.sendMessage(m.prefixe("player-only"));
                    return;
                }
                String coin = args.length > 1 && args[1].equals("2") ? "2" : "1";
                plugin.village().definirCoin(joueur, coin);
                joueur.sendMessage(m.prefixe("region-set", "%corner%", coin));
            }
            case "createclicker" -> {
                if (!(auteur instanceof Player joueur)) {
                    auteur.sendMessage(m.prefixe("player-only"));
                    return;
                }
                plugin.clicker().creer(joueur.getLocation());
                joueur.sendMessage(m.prefixe("clicker-created"));
            }
            case "removeclicker" -> {
                if (!(auteur instanceof Player joueur)) {
                    auteur.sendMessage(m.prefixe("player-only"));
                    return;
                }
                joueur.sendMessage(m.prefixe(plugin.clicker().retirerProche(joueur.getLocation()) ? "clicker-removed" : "clicker-none-nearby"));
            }
            case "listclickers" -> {
                auteur.sendMessage(m.prefixe("clickers-header", "%count%", String.valueOf(plugin.clicker().nombreBonhommes())));
                for (Location l : plugin.clicker().positions()) {
                    auteur.sendMessage(m.prefixe("clickers-line", "%world%", l.getWorld().getName(), "%x%", String.valueOf(l.getBlockX()),
                            "%y%", String.valueOf(l.getBlockY()), "%z%", String.valueOf(l.getBlockZ())));
                }
            }
            case "reload" -> plugin.recharger(auteur);
            case "info" -> info(auteur, args);
            case "flocons" -> flocons(auteur, args);
            case "multiplicateur" -> multiplicateur(auteur, args);
            case "compteur" -> compteur(auteur, args);
            case "tampon" -> tampon(auteur, args);
            case "acheter" -> acheter(auteur, args);
            case "sapin" -> sapin(auteur, args);
            case "drop" -> drop(auteur, args);
            case "horloge" -> horloge(auteur, args);
            case "bilan" -> plugin.bilan().lancer(auteur);
            case "tous" -> tous(auteur, args);
            default -> {
            }
        }
    }

    /** Retour d'une commande réussie : seulement à un joueur, la console des autres plugins reste propre. */
    private void retour(CommandSender auteur, String cle, Object... remplacements) {
        if (auteur instanceof Player) {
            plugin.messages().envoyer(auteur, cle, remplacements);
        } else {
            plugin.debug(plugin.messages().brut(cle, remplacements));
        }
    }

    private static Integer entier(String texte) {
        try {
            return Integer.parseInt(texte.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void give(CommandSender auteur, String[] args) {
        var m = plugin.messages();
        Player cible = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (auteur instanceof Player p ? p : null);
        if (cible == null) {
            auteur.sendMessage(m.prefixe(args.length >= 2 ? "player-not-found" : "give-usage", "%player%", args.length >= 2 ? args[1] : ""));
            return;
        }
        Integer nombre = args.length >= 3 ? entier(args[2]) : Integer.valueOf(1);
        int n = nombre == null || nombre < 1 ? 1 : nombre;
        plugin.boules().donner(cible, n);
        cible.sendMessage(m.mini("snowball_received"));
        if (!cible.equals(auteur)) {
            auteur.sendMessage(m.prefixe("give-done", "%player%", cible.getName(), "%amount%", String.valueOf(n)));
        }
    }

    private void info(CommandSender auteur, String[] args) {
        var m = plugin.messages();
        if (args.length < 2) {
            auteur.sendMessage(m.prefixe("info-usage"));
            return;
        }
        Joueurs.Cible cible = Joueurs.trouver(args[1]);
        if (cible == null) {
            auteur.sendMessage(m.prefixe("info-unknown", "%player%", args[1]));
            return;
        }
        UUID uuid = cible.uuid();
        if (plugin.clicker().connu(uuid)) {
            int niveau = plugin.clicker().niveau(uuid);
            auteur.sendMessage(m.prefixe("info-line", "%player%", cible.pseudo(), "%clicks%", String.valueOf(plugin.clicker().clics(uuid)),
                    "%level%", String.valueOf(niveau), "%required%", String.valueOf(plugin.clicker().clicsRequis(niveau + 1))));
        } else {
            auteur.sendMessage(m.prefixe("info-unknown", "%player%", cible.pseudo()));
        }
        plugin.flocons().info(auteur, uuid, cible.pseudo());
        m.envoyer(auteur, "admin.info-evenement", "{portes}", plugin.calendrier().ouvertes(uuid), "{tampons}", plugin.carte().nombre(uuid),
                "{tampons-total}", plugin.carte().tampons().size(), "{sapin}", Textes.nombre(plugin.sapin().contribution(uuid)),
                "{cadeaux}", plugin.compteurs().valeur(uuid, "cadeaux-envoyes"), "{livraisons}", plugin.livraisons().nombre(uuid));
    }

    private void flocons(CommandSender auteur, String[] args) {
        var m = plugin.messages();
        if (args.length < 4) {
            m.envoyer(auteur, "admin.flocons-usage");
            return;
        }
        Joueurs.Cible cible = Joueurs.trouver(args[1]);
        Integer montant = entier(args[2]);
        String source = args[3].toLowerCase(Locale.ROOT);
        if (cible == null) {
            m.envoyer(auteur, "admin.joueur-inconnu", "{joueur}", args[1]);
            plugin.getLogger().warning("flocons : joueur inconnu " + args[1] + " (" + String.join(" ", args) + ")");
            return;
        }
        if (montant == null || montant <= 0) {
            m.envoyer(auteur, "admin.flocons-usage");
            return;
        }
        if (plugin.flocons().source(source) == null) {
            m.envoyer(auteur, "admin.source-inconnue", "{source}", source);
            plugin.getLogger().warning("flocons : source inconnue " + source + " (" + cible.pseudo() + ")");
            return;
        }
        int verse = plugin.flocons().gagner(cible.uuid(), cible.pseudo(), montant, source, false);
        retour(auteur, "admin.flocons-fait", "{joueur}", cible.pseudo(), "{verse}", verse, "{montant}", montant, "{source}", source);
    }

    private void multiplicateur(CommandSender auteur, String[] args) {
        var m = plugin.messages();
        if (args.length < 3) {
            m.envoyer(auteur, "admin.multiplicateur-usage");
            return;
        }
        double facteur;
        try {
            facteur = Double.parseDouble(args[1].replace(',', '.'));
        } catch (NumberFormatException e) {
            m.envoyer(auteur, "admin.multiplicateur-usage");
            return;
        }
        Integer minutes = entier(args[2]);
        if (minutes == null || facteur <= 0) {
            m.envoyer(auteur, "admin.multiplicateur-usage");
            return;
        }
        plugin.flocons().multiplicateurs().lancer(facteur, minutes);
        plugin.getLogger().info("Multiplicateur global ×" + Textes.facteur(facteur) + " pendant " + minutes + " min");
        retour(auteur, facteur == 1 || minutes <= 0 ? "admin.multiplicateur-arrete" : "admin.multiplicateur-fait",
                "{facteur}", Textes.facteur(facteur), "{minutes}", minutes);
    }

    /** compteur et tampon ne comptent que pendant les gains, comme flocons. */
    private boolean horsGains(CommandSender auteur, String[] args) {
        if (plugin.dates().gains(plugin.horloge().maintenant())) {
            return false;
        }
        retour(auteur, "admin.hors-gains");
        plugin.debug("Hors phase de gains, ignoré : " + String.join(" ", args));
        return true;
    }

    private void compteur(CommandSender auteur, String[] args) {
        var m = plugin.messages();
        if (horsGains(auteur, args)) {
            return;
        }
        if (args.length < 3) {
            m.envoyer(auteur, "admin.compteur-usage");
            return;
        }
        Joueurs.Cible cible = Joueurs.trouver(args[1]);
        if (cible == null) {
            m.envoyer(auteur, "admin.joueur-inconnu", "{joueur}", args[1]);
            plugin.getLogger().warning("compteur : joueur inconnu " + args[1]);
            return;
        }
        Integer n = args.length >= 4 ? entier(args[3]) : Integer.valueOf(1);
        if (n == null) {
            m.envoyer(auteur, "admin.compteur-usage");
            return;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        int valeur = plugin.compteurs().ajouter(cible.uuid(), id, n);
        plugin.carte().compteurChange(cible.uuid(), cible.pseudo(), id);
        retour(auteur, "admin.compteur-fait", "{joueur}", cible.pseudo(), "{compteur}", id, "{valeur}", valeur);
    }

    private void tampon(CommandSender auteur, String[] args) {
        var m = plugin.messages();
        if (horsGains(auteur, args)) {
            return;
        }
        if (args.length < 3) {
            m.envoyer(auteur, "admin.tampon-usage");
            return;
        }
        Joueurs.Cible cible = Joueurs.trouver(args[1]);
        if (cible == null) {
            m.envoyer(auteur, "admin.joueur-inconnu", "{joueur}", args[1]);
            plugin.getLogger().warning("tampon : joueur inconnu " + args[1]);
            return;
        }
        if (plugin.carte().tampon(args[2]) == null) {
            m.envoyer(auteur, "admin.tampon-inconnu", "{tampon}", args[2]);
            return;
        }
        boolean nouveau = plugin.carte().accorder(cible.uuid(), cible.pseudo(), args[2].toLowerCase(Locale.ROOT));
        retour(auteur, nouveau ? "admin.tampon-fait" : "admin.tampon-deja", "{joueur}", cible.pseudo(), "{tampon}", args[2]);
    }

    private void acheter(CommandSender auteur, String[] args) {
        var m = plugin.messages();
        if (args.length < 3) {
            m.envoyer(auteur, "admin.acheter-usage");
            return;
        }
        Player joueur = Bukkit.getPlayerExact(args[1]);
        if (joueur == null) {
            m.envoyer(auteur, "admin.joueur-hors-ligne", "{joueur}", args[1]);
            return;
        }
        plugin.boutique().acheter(auteur, joueur, args[2].toLowerCase(Locale.ROOT));
    }

    private void sapin(CommandSender auteur, String[] args) {
        var m = plugin.messages();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "info";
        switch (action) {
            case "info" -> plugin.sapin().info(auteur);
            case "cible" -> {
                Integer cible = args.length > 2 ? entier(args[2]) : null;
                if (cible == null || cible < 0) {
                    m.envoyer(auteur, "admin.sapin-usage");
                    return;
                }
                plugin.sapin().reglerCible(cible);
                plugin.getLogger().info("Sapin : cible " + (cible == 0 ? "de config.yml" : cible));
                plugin.sapin().info(auteur);
            }
            case "ajouter" -> {
                Joueurs.Cible cible = args.length > 2 ? Joueurs.trouver(args[2]) : null;
                Integer points = args.length > 3 ? entier(args[3]) : null;
                if (cible == null || points == null) {
                    m.envoyer(auteur, "admin.sapin-usage");
                    return;
                }
                int comptes = plugin.sapin().contribuer(cible.uuid(), cible.pseudo(), points, false);
                retour(auteur, "admin.sapin-ajoute", "{joueur}", cible.pseudo(), "{points}", comptes,
                        "{pourcent}", Textes.pourcent(plugin.sapin().pourcent()));
            }
            case "veillee" -> plugin.sapin().veillee(auteur);
            default -> m.envoyer(auteur, "admin.sapin-usage");
        }
    }

    private void drop(CommandSender auteur, String[] args) {
        if (args.length < 4) {
            plugin.messages().envoyer(auteur, "admin.drop-usage");
            return;
        }
        Integer secondes = entier(args[2]);
        Integer nombre = entier(args[3]);
        if (secondes == null || nombre == null) {
            plugin.messages().envoyer(auteur, "admin.drop-usage");
            return;
        }
        plugin.drops().lancer(auteur, args[1], secondes, nombre);
    }

    private void horloge(CommandSender auteur, String[] args) {
        var m = plugin.messages();
        if (!plugin.horloge().autorisee()) {
            m.envoyer(auteur, "admin.horloge-interdite");
            return;
        }
        if (args.length < 2) {
            m.envoyer(auteur, "admin.horloge-etat", "{heure}", plugin.horloge().local().withNano(0).toString(),
                    "{phase}", plugin.phase().id(), "{test}", plugin.horloge().decalee() ? "test" : "réelle");
            return;
        }
        if (args[1].equalsIgnoreCase("reel")) {
            plugin.horloge().reel();
        } else {
            LocalDateTime cible = LectureConfig.dateHeure(args[1]);
            if (cible == null) {
                m.envoyer(auteur, "admin.horloge-usage");
                return;
            }
            plugin.horloge().regler(cible);
        }
        plugin.getLogger().info("Horloge : " + plugin.horloge().local().withNano(0) + " (phase " + plugin.phase().id() + ")");
        plugin.apresChangementHorloge();
        m.envoyer(auteur, "admin.horloge-etat", "{heure}", plugin.horloge().local().withNano(0).toString(),
                "{phase}", plugin.phase().id(), "{test}", plugin.horloge().decalee() ? "test" : "réelle");
    }

    private void tous(CommandSender auteur, String[] args) {
        if (args.length < 2) {
            plugin.messages().envoyer(auteur, "admin.tous-usage");
            return;
        }
        World monde = Bukkit.getWorld(args[1]);
        int debut = monde != null && args.length > 2 ? 2 : 1;
        if (debut == 1) {
            monde = null;
        }
        String commande = String.join(" ", Arrays.copyOfRange(args, debut, args.length));
        int n = 0;
        for (Player joueur : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (monde != null && !joueur.getWorld().equals(monde)) {
                continue;
            }
            Console.executer(plugin.getLogger(), List.of(commande), "{joueur}", joueur.getName(), "{uuid}", joueur.getUniqueId().toString());
            n++;
        }
        plugin.getLogger().info("tous" + (monde != null ? " (" + monde.getName() + ")" : "") + " : " + n + " joueur(s) — " + commande);
        retour(auteur, "admin.tous-fait", "{n}", n);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender auteur, @NotNull Command commande, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String debut = args[0].toLowerCase(Locale.ROOT);
            return permises(auteur).stream().filter(s -> s.startsWith(debut)).toList();
        }
        String sous = args[0].toLowerCase(Locale.ROOT);
        if (!auteur.hasPermission("shchristmaspresents.admin." + sous)) {
            return List.of();
        }
        String dernier = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> choix = switch (sous) {
            case "setregion" -> args.length == 2 ? List.of("1", "2") : List.of();
            case "give" -> args.length == 2 ? null : args.length == 3 ? List.of("1", "16", "64") : List.of();
            case "info", "acheter", "compteur", "tampon", "flocons" -> {
                if (args.length == 2) {
                    yield null;
                }
                if (sous.equals("flocons") && args.length == 4) {
                    yield new ArrayList<>(plugin.flocons().sources().keySet());
                }
                if (sous.equals("acheter") && args.length == 3) {
                    yield new ArrayList<>(plugin.boutique().articles().keySet());
                }
                if (sous.equals("tampon") && args.length == 3) {
                    List<String> ids = new ArrayList<>();
                    plugin.carte().tampons().forEach(t -> ids.add(t.id()));
                    yield ids;
                }
                if (sous.equals("compteur") && args.length == 3) {
                    yield List.of("maisons", "soirees", "defis", "portes", "offert", "cadeaux-envoyes");
                }
                yield List.of();
            }
            case "multiplicateur" -> args.length == 2 ? List.of("2", "1") : args.length == 3 ? List.of("60") : List.of();
            case "sapin" -> args.length == 2 ? List.of("info", "cible", "ajouter", "veillee") : args.length == 3 && args[1].equalsIgnoreCase("ajouter") ? null : List.of();
            case "drop" -> args.length == 2 ? new ArrayList<>(plugin.drops().zones()) : args.length == 3 ? List.of("60") : args.length == 4 ? List.of("30") : List.of();
            case "horloge" -> args.length == 2 ? List.of("reel", plugin.horloge().local().withSecond(0).withNano(0).toString().substring(0, 16)) : List.of();
            case "tous" -> {
                if (args.length == 2) {
                    List<String> mondes = new ArrayList<>();
                    Bukkit.getWorlds().forEach(w -> mondes.add(w.getName()));
                    yield mondes;
                }
                yield List.of();
            }
            default -> List.of();
        };
        if (choix == null) {
            return null;
        }
        return choix.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(dernier)).toList();
    }
}
