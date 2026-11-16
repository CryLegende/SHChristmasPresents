package org.Cry_Legende.sHChristmasPresents;

import org.Cry_Legende.sHChristmasPresents.boutique.Boutique;
import org.Cry_Legende.sHChristmasPresents.cadeaux.CadeauxJoueurs;
import org.Cry_Legende.sHChristmasPresents.calendrier.Calendrier;
import org.Cry_Legende.sHChristmasPresents.carte.Carte;
import org.Cry_Legende.sHChristmasPresents.carte.Compteurs;
import org.Cry_Legende.sHChristmasPresents.clicker.Clicker;
import org.Cry_Legende.sHChristmasPresents.commandes.CommandeAdmin;
import org.Cry_Legende.sHChristmasPresents.commandes.CommandesJoueur;
import org.Cry_Legende.sHChristmasPresents.commandes.ListeCommandes;
import org.Cry_Legende.sHChristmasPresents.commun.Empreintes;
import org.Cry_Legende.sHChristmasPresents.commun.Messages;
import org.Cry_Legende.sHChristmasPresents.commun.MenusListener;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetsMarques;
import org.Cry_Legende.sHChristmasPresents.commun.Son;
import org.Cry_Legende.sHChristmasPresents.drop.Drops;
import org.Cry_Legende.sHChristmasPresents.flocons.Comptes;
import org.Cry_Legende.sHChristmasPresents.flocons.Flocons;
import org.Cry_Legende.sHChristmasPresents.flocons.Multiplicateurs;
import org.Cry_Legende.sHChristmasPresents.flocons.Portefeuille;
import org.Cry_Legende.sHChristmasPresents.horloge.Actions;
import org.Cry_Legende.sHChristmasPresents.horloge.Dates;
import org.Cry_Legende.sHChristmasPresents.horloge.Fenetres;
import org.Cry_Legende.sHChristmasPresents.horloge.Horloge;
import org.Cry_Legende.sHChristmasPresents.horloge.Phase;
import org.Cry_Legende.sHChristmasPresents.ile.PetitsCadeaux;
import org.Cry_Legende.sHChristmasPresents.joueurs.Connexions;
import org.Cry_Legende.sHChristmasPresents.joueurs.Profils;
import org.Cry_Legende.sHChristmasPresents.livraison.Livraisons;
import org.Cry_Legende.sHChristmasPresents.placeholders.Expansion;
import org.Cry_Legende.sHChristmasPresents.recap.Bilan;
import org.Cry_Legende.sHChristmasPresents.recap.Recapitulatif;
import org.Cry_Legende.sHChristmasPresents.retour.Retour;
import org.Cry_Legende.sHChristmasPresents.sapin.Sapin;
import org.Cry_Legende.sHChristmasPresents.stockage.Base;
import org.Cry_Legende.sHChristmasPresents.stockage.Etat;
import org.Cry_Legende.sHChristmasPresents.village.BouleDeNeige;
import org.Cry_Legende.sHChristmasPresents.village.CadeauxVillage;
import org.Cry_Legende.sHChristmasPresents.village.Fermeture;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * SHChristmasPresents 2.0 : le moteur de l'évènement de Noël d'Opale. Toutes les données de l'année sont en mémoire
 * (lues au démarrage) et écrites en base en file ; tout ce qui les change tourne sur le fil principal, où les
 * commandes et les événements des mondes (DivineMC, mondes en parallèle) sont renvoyés.
 */
public final class SHChristmasPresents extends JavaPlugin {

    private Thread principal;
    private volatile boolean debug;
    private int annee;
    private String signatureBase;

    private Messages messages;
    private Base base;
    private Etat etat;
    private Horloge horloge;
    private volatile Dates dates;
    private Actions actions;
    private Fenetres fenetres;
    private Profils profils;
    private Empreintes empreintes;
    private Flocons flocons;
    private Compteurs compteurs;
    private Carte carte;
    private Calendrier calendrier;
    private Sapin sapin;
    private Livraisons livraisons;
    private Boutique boutique;
    private CadeauxJoueurs cadeaux;
    private PetitsCadeaux petitsCadeaux;
    private Drops drops;
    private Retour retour;
    private Recapitulatif recap;
    private Bilan bilan;
    private BouleDeNeige boules;
    private CadeauxVillage village;
    private Clicker clicker;
    private Connexions connexions;
    private final Fermeture fermeture = new Fermeture(this);
    private ListeCommandes listeCommandes;
    private volatile Map<String, Son> sons = Map.of();
    private final Set<UUID> verrous = new HashSet<>();
    private BukkitTask ticker;

    @Override
    public void onEnable() {
        principal = Thread.currentThread();
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        messages = new Messages(this);
        try {
            messages.charger();
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("messages.yml illisible, textes du jar utilisés : " + e.getMessage());
            messages.textesDuJar();
        }
        FileConfiguration config = getConfig();
        annee = config.getInt("annee", 2026);
        debug = config.getBoolean("debug", false);
        signatureBase = signatureBase(config);
        try {
            base = new Base(config.getConfigurationSection("base-de-donnees"), getLogger());
        } catch (SQLException e) {
            getLogger().severe("Base de données injoignable, plugin arrêté : " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        etat = new Etat(base, annee);
        horloge = new Horloge(etat);
        actions = new Actions(this, base, annee);
        profils = new Profils(base, annee);
        Comptes comptes = new Comptes(base, annee);
        fenetres = new Fenetres(etat, getLogger(), comptes::totaux);
        compteurs = new Compteurs(base, annee);
        carte = new Carte(this, base, annee);
        calendrier = new Calendrier(this, base, annee);
        sapin = new Sapin(this, base, annee);
        livraisons = new Livraisons(base, annee, getLogger(), () -> horloge.local());
        boutique = new Boutique(this, base, annee);
        cadeaux = new CadeauxJoueurs(this, base, annee);
        petitsCadeaux = new PetitsCadeaux(this, base, annee);
        drops = new Drops(this);
        retour = new Retour(this);
        recap = new Recapitulatif(this);
        bilan = new Bilan(this, base, annee);
        boules = new BouleDeNeige(this);
        village = new CadeauxVillage(this, boules);
        clicker = new Clicker(this, base, annee);
        try {
            base.bloquant(c -> {
                etat.charger(c);
                return null;
            });
            horloge.configurer(zone(config), config.getBoolean("test.horloge-autorisee", false));
            LocalDate aujourdhui = horloge.aujourdhui();
            base.bloquant(c -> {
                actions.charger(c);
                profils.charger(c);
                comptes.charger(c, aujourdhui);
                calendrier.charger(c);
                sapin.charger(c);
                compteurs.charger(c);
                carte.charger(c);
                boutique.charger(c, aujourdhui);
                cadeaux.charger(c);
                livraisons.charger(c);
                petitsCadeaux.charger(c, aujourdhui);
                clicker.charger(c);
                return null;
            });
        } catch (SQLException e) {
            getLogger().severe("Lecture des données impossible, plugin arrêté : " + e.getMessage());
            base.fermer();
            base = null;
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        String sel = etat.globale("sel-empreintes");
        if (sel == null) {
            byte[] octets = new byte[32];
            new SecureRandom().nextBytes(octets);
            sel = HexFormat.of().formatHex(octets);
            etat.setGlobale("sel-empreintes", sel);
        }
        empreintes = new Empreintes(sel);
        flocons = new Flocons(this, comptes, new Multiplicateurs(etat, horloge, fenetres, profils), new Portefeuille(this));
        appliquerReglages();

        livraisons.type("sapin-palier", (joueur, ref) -> {
            Sapin.Palier palier = sapin.palier(ref);
            if (palier != null) {
                sapin.recompenser(joueur, palier);
            }
        });
        connexions = new Connexions(this);
        listeCommandes = new ListeCommandes(this);
        for (Listener l : List.of(new MenusListener(this::sync), new ObjetsMarques(this), connexions, cadeaux, petitsCadeaux,
                drops, retour, boules, village, clicker, fermeture, listeCommandes)) {
            getServer().getPluginManager().registerEvents(l, this);
        }
        enregistrer("shchristmaspresents", new CommandeAdmin(this));
        CommandesJoueur joueur = new CommandesJoueur(this);
        for (String nom : List.of("calendrier", "carte", "sapin", "flocons", "cadeau")) {
            enregistrer(nom, joueur);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new Expansion(this).register();
        }
        village.demarrer();
        clicker.demarrer();
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tic, 20L, 100L);
        for (Player present : Bukkit.getOnlinePlayers()) {
            connexions.arrivee(present);
        }
        getLogger().info("Noël " + annee + " : phase " + phase().id() + " (" + horloge.local().withNano(0)
                + (horloge.decalee() ? ", horloge de test" : "") + "), " + profils.nombre() + " profils, "
                + actions.nombrePonctuelles() + " actions datées, " + actions.nombreQuotidiennes() + " quotidiennes, "
                + village.nombreModeles() + " modèles, sapin à " + String.format(java.util.Locale.ROOT, "%.1f", sapin.pourcent()) + " %");
    }

    private void enregistrer(String nom, Object executeur) {
        PluginCommand commande = getCommand(nom);
        if (commande == null) {
            getLogger().severe("Commande absente de plugin.yml : " + nom);
            return;
        }
        commande.setExecutor((CommandExecutor) executeur);
        commande.setTabCompleter((TabCompleter) executeur);
    }

    @Override
    public void onDisable() {
        if (ticker != null) {
            ticker.cancel();
        }
        if (drops != null) {
            drops.arreterTout();
        }
        if (flocons != null) {
            flocons.portefeuille().arreter();
        }
        if (village != null) {
            village.arreter();
        }
        if (clicker != null && base != null) {
            clicker.arreter();
        }
        if (base != null) {
            base.fermer();
        }
    }

    private ZoneId zone(FileConfiguration config) {
        String texte = config.getString("fuseau", "Europe/Paris");
        try {
            return ZoneId.of(texte);
        } catch (DateTimeException e) {
            getLogger().warning("fuseau inconnu : " + texte + ", Europe/Paris utilisé");
            return ZoneId.of("Europe/Paris");
        }
    }

    private static String signatureBase(FileConfiguration config) {
        ConfigurationSection b = config.getConfigurationSection("base-de-donnees");
        if (b == null) {
            return "";
        }
        return b.getString("hote") + "|" + b.getInt("port") + "|" + b.getString("nom") + "|" + b.getString("utilisateur") + "|"
                + Objects.hashCode(b.getString("mot-de-passe"));
    }

    /** Lit config.yml dans chaque module (démarrage et /shchristmaspresents reload). */
    private void appliquerReglages() {
        FileConfiguration c = getConfig();
        ZoneId zone = zone(c);
        horloge.configurer(zone, c.getBoolean("test.horloge-autorisee", false));
        dates = Dates.lire(c.getConfigurationSection("dates"), zone, getLogger());
        actions.configurer(c.getConfigurationSection("actions"), c.getConfigurationSection("chaque-jour"), c.getInt("rattrapage-minutes", 180));
        fenetres.configurer(c.getConfigurationSection("fenetres"));
        flocons.portefeuille().configurer(c.getConfigurationSection("portefeuille"));
        flocons.multiplicateurs().configurer(c.getDouble("multiplicateur-max", 3));
        flocons.configurer(c.getConfigurationSection("sources"));
        calendrier.configurer(c.getConfigurationSection("calendrier"), annee);
        sapin.configurer(c.getConfigurationSection("sapin"));
        carte.configurer(c.getConfigurationSection("carte"));
        boutique.configurer(c.getConfigurationSection("boutique"));
        cadeaux.configurer(c.getConfigurationSection("cadeaux"));
        petitsCadeaux.configurer(c.getConfigurationSection("ile"));
        drops.configurer(c.getConfigurationSection("drops"));
        retour.configurer(c.getConfigurationSection("retour"));
        fermeture.configurer(c.getConfigurationSection("village.fermeture"));
        Map<String, Son> lus = new HashMap<>();
        ConfigurationSection s = c.getConfigurationSection("sons");
        if (s != null) {
            for (String cle : s.getKeys(false)) {
                Son son = Son.lire(s.getString(cle));
                if (son != null) {
                    lus.put(cle, son);
                } else if (!s.getString(cle, "").isBlank()) {
                    getLogger().warning("sons." + cle + " : son inconnu");
                }
            }
        }
        sons = Map.copyOf(lus);
        clicker.configurer();
    }

    /** Toutes les 5 secondes : actions datées, médianes des fenêtres, première visite du village. */
    private void tic() {
        ZonedDateTime maintenant = horloge.maintenant();
        actions.verifier();
        fenetres.verifier(maintenant);
        listeCommandes.verifier();
        if (dates.phase(maintenant) == Phase.VILLAGE) {
            for (Player joueur : Bukkit.getOnlinePlayers()) {
                carte.visite(joueur);
            }
        }
    }

    /** Après /shchristmaspresents horloge : ce qui dépend de l'heure est revu tout de suite. */
    public void apresChangementHorloge() {
        tic();
        sapin.verifierPaliers();
    }

    // ---- rechargement

    private String verifierFichiers() {
        for (String nom : new String[]{"config.yml", "messages.yml"}) {
            File fichier = new File(getDataFolder(), nom);
            if (!fichier.exists()) {
                continue;
            }
            try {
                new YamlConfiguration().load(fichier);
            } catch (IOException | InvalidConfigurationException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String texte = String.valueOf(cause.getMessage()).replaceAll("\\s+", " ").trim();
                return nom + "|" + (texte.length() > 200 ? texte.substring(0, 200) + "..." : texte);
            }
        }
        return null;
    }

    /**
     * Relit config.yml et messages.yml (refusé s'ils sont invalides : l'ancienne config reste), les modèles, et relance
     * l'apparition des cadeaux du village. Les données ne sont jamais relues. annee et base-de-donnees : au redémarrage.
     */
    public void recharger(CommandSender auteur) {
        String erreur = verifierFichiers();
        if (erreur != null) {
            String[] parties = erreur.split("\\|", 2);
            getLogger().warning("Rechargement refusé, configuration précédente gardée (" + parties[0] + " : " + parties[1] + ").");
            auteur.sendMessage(messages.prefixe("reload-error", "%file%", parties[0], "%error%", parties[1]));
            return;
        }
        reloadConfig();
        try {
            messages.charger();
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().warning("messages.yml relu avec erreur : " + e.getMessage());
        }
        FileConfiguration config = getConfig();
        debug = config.getBoolean("debug", false);
        appliquerReglages();
        village.arreter();
        village.demarrer();
        sapin.verifierPaliers();
        tic();
        auteur.sendMessage(messages.prefixe("reload-success", "%models%", String.valueOf(village.nombreModeles())));
        if (config.getInt("annee", 2026) != annee || !signatureBase(config).equals(signatureBase)) {
            messages.envoyer(auteur, "admin.redemarrage-requis");
        }
    }

    // ---- outils partagés

    public boolean estPrincipal() {
        return Thread.currentThread() == principal;
    }

    /**
     * Exécute la tâche sur le fil principal, tout de suite si on y est déjà. Avec les mondes en parallèle de DivineMC,
     * commandes et événements d'un joueur arrivent sur le fil de son monde ; les tâches du fil principal ne tournent
     * jamais en même temps que les mondes.
     */
    public void sync(Runnable tache) {
        if (estPrincipal()) {
            tache.run();
        } else if (isEnabled()) {
            try {
                Bukkit.getScheduler().runTask(this, tache);
            } catch (RuntimeException e) {
                getLogger().fine("Tâche ignorée à l'arrêt : " + e.getMessage());
            }
        }
    }

    /** Une dépense à la fois par joueur (fil principal). */
    public boolean verrouiller(UUID uuid) {
        return verrous.add(uuid);
    }

    public void deverrouiller(UUID uuid) {
        verrous.remove(uuid);
    }

    public void son(String cle, Player joueur) {
        Son son = sons.get(cle);
        if (son != null && joueur != null) {
            son.jouer(joueur);
        }
    }

    public void debug(String texte) {
        if (debug) {
            getLogger().info("[debug] " + texte);
        }
    }

    public Phase phase() {
        return dates.phase(horloge.maintenant());
    }

    public int annee() {
        return annee;
    }

    public Messages messages() {
        return messages;
    }

    public Etat etat() {
        return etat;
    }

    public Horloge horloge() {
        return horloge;
    }

    public Dates dates() {
        return dates;
    }

    public Actions actions() {
        return actions;
    }

    public Fenetres fenetres() {
        return fenetres;
    }

    public Profils profils() {
        return profils;
    }

    public Empreintes empreintes() {
        return empreintes;
    }

    public Flocons flocons() {
        return flocons;
    }

    public Compteurs compteurs() {
        return compteurs;
    }

    public Carte carte() {
        return carte;
    }

    public Calendrier calendrier() {
        return calendrier;
    }

    public Sapin sapin() {
        return sapin;
    }

    public Livraisons livraisons() {
        return livraisons;
    }

    public Boutique boutique() {
        return boutique;
    }

    public CadeauxJoueurs cadeaux() {
        return cadeaux;
    }

    public PetitsCadeaux petitsCadeaux() {
        return petitsCadeaux;
    }

    public Drops drops() {
        return drops;
    }

    public Retour retour() {
        return retour;
    }

    public Recapitulatif recap() {
        return recap;
    }

    public Bilan bilan() {
        return bilan;
    }

    public BouleDeNeige boules() {
        return boules;
    }

    public CadeauxVillage village() {
        return village;
    }

    public Clicker clicker() {
        return clicker;
    }
}
