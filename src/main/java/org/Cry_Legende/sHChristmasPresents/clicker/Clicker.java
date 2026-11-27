package org.Cry_Legende.sHChristmasPresents.clicker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Joueurs;
import org.Cry_Legende.sHChristmasPresents.commun.Son;
import org.Cry_Legende.sHChristmasPresents.rendu.Scene;
import org.Cry_Legende.sHChristmasPresents.stockage.Base;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bonhommes de neige clicker (1.1) : chaque clic compte, chaque niveau donne des boules de neige magiques. Clics et
 * niveau par année dans shxmas_clicker (écrits en lot toutes les minutes). Les clics arrivent sur le fil du monde :
 * données par joueur verrouillées, récompenses de niveau sur le fil principal.
 */
public final class Clicker implements Listener {

    /** Ancien playerdata.yml : clics de Noël 2025, repris une fois dans la base sous cette année. */
    private static final int ANNEE_PLAYERDATA = 2025;

    static final class Donnees {
        volatile int clics;
        volatile int niveau;
        volatile java.time.LocalDate jour;
        volatile int clicsJour;
        Location derniere;
        int immobiles;
    }

    private final SHChristmasPresents plugin;
    private final Base base;
    private final int annee;
    private final NamespacedKey cle;
    private final Map<UUID, Donnees> donnees = new ConcurrentHashMap<>();
    private final Set<UUID> modifies = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> derniersClics = new ConcurrentHashMap<>();
    private final Scene scene;
    private final ModeleBonhomme modele = new ModeleBonhomme();
    private final List<Bonhomme> bonhommes = new ArrayList<>();
    private final File fichierBonhommes;
    private BukkitTask ecriture;
    private volatile Son sonClic;
    private volatile String barre;
    private volatile String sousTitre;
    private volatile String niveauTexte;
    private volatile String antiAfk;

    public Clicker(SHChristmasPresents plugin, Base base, int annee, Scene scene) {
        this.plugin = plugin;
        this.base = base;
        this.annee = annee;
        this.scene = scene;
        this.cle = new NamespacedKey(plugin, "clicker_snowman");
        this.fichierBonhommes = new File(plugin.getDataFolder(), "bonhommes.yml");
    }

    public void configurer() {
        Sound s = Son.sound(plugin.getConfig().getString("clicker.son", "BLOCK_SNOW_BREAK"));
        sonClic = s == null ? null : new Son(s, 0.5f, 1.5f);
        barre = plugin.messages().brut("clicker.barre");
        sousTitre = plugin.messages().brut("clicker.sous-titre");
        niveauTexte = plugin.messages().brut("clicker.niveau");
        antiAfk = plugin.messages().brut("clicker.anti-afk");
    }

    public void charger(Connection connexion) throws SQLException {
        try (PreparedStatement r = connexion.prepareStatement("SELECT uuid, clics, niveau, jour, clics_jour FROM shxmas_clicker WHERE annee = ?")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    Donnees d = new Donnees();
                    d.clics = l.getInt(2);
                    d.niveau = l.getInt(3);
                    java.sql.Date jour = l.getDate(4);
                    d.jour = jour == null ? null : jour.toLocalDate();
                    d.clicsJour = l.getInt(5);
                    donnees.put(UUID.fromString(l.getString(1)), d);
                }
            }
        }
    }

    /**
     * Démarrage : bonhommes de bonhommes.yml (montrés par paquets), écriture toutes les minutes, reprise unique de
     * playerdata.yml. Les vrais bonhommes de neige des versions 1.x à 2.0 (snowmen.yml) sont remplacés à leur chargement :
     * leur place passe dans bonhommes.yml, l'entité est retirée et son chunk n'est plus gardé chargé.
     */
    public void demarrer() {
        bonhommes.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(fichierBonhommes);
        for (String ligne : yaml.getStringList("bonhommes")) {
            Location l = lirePlace(ligne);
            if (l != null) {
                ajouter(l);
            } else {
                plugin.getLogger().warning("bonhommes.yml : ligne ignorée (monde absent ou ligne invalide) : " + ligne);
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (org.bukkit.World monde : Bukkit.getWorlds()) {
                remplacerAnciens(monde.getEntities());
            }
        });
        if (ecriture == null) {
            ecriture = Bukkit.getScheduler().runTaskTimer(plugin, this::ecrire, 1200L, 1200L);
        }
        reprendreAncienFichier();
    }

    private Location lirePlace(String ligne) {
        String[] p = ligne.trim().split("\\s+");
        if (p.length < 4) {
            return null;
        }
        org.bukkit.World monde = Bukkit.getWorld(p[0]);
        if (monde == null) {
            return null;
        }
        try {
            return new Location(monde, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    p.length > 4 ? Float.parseFloat(p[4]) : 0f, 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void ajouter(Location l) {
        float echelle = (float) plugin.getConfig().getDouble("clicker.echelle", 1.0);
        Bonhomme b = new Bonhomme(l.getWorld(), l.getX(), l.getY(), l.getZ(), l.getYaw(), echelle, modele, this::clicBonhomme);
        bonhommes.add(b);
        scene.ajouter(b);
    }

    /** Anciens bonhommes (vraies entités) : leur place est reprise, l'entité retirée (fil principal). */
    private void remplacerAnciens(java.util.Collection<? extends Entity> entites) {
        boolean change = false;
        for (Entity e : entites) {
            if (!e.getPersistentDataContainer().has(cle, PersistentDataType.BYTE)) {
                continue;
            }
            Location l = e.getLocation();
            boolean connu = false;
            for (Bonhomme b : bonhommes) {
                if (b.monde().equals(l.getWorld()) && b.position().distanceSquared(l) < 0.25) {
                    connu = true;
                    break;
                }
            }
            if (!connu) {
                ajouter(new Location(l.getWorld(), Math.round(l.getX() * 100) / 100.0, l.getY(), Math.round(l.getZ() * 100) / 100.0,
                        Math.round(l.getYaw()), 0));
                change = true;
            }
            l.getChunk().setForceLoaded(false);
            e.remove();
            plugin.getLogger().info("Bonhomme de neige " + (connu ? "en double retiré" : "remplacé par le modèle animé")
                    + " en " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
        }
        if (change) {
            sauverBonhommes();
        }
    }

    @EventHandler
    public void onChargement(EntitiesLoadEvent event) {
        boolean ancien = false;
        for (Entity e : event.getEntities()) {
            if (e.getPersistentDataContainer().has(cle, PersistentDataType.BYTE)) {
                ancien = true;
                break;
            }
        }
        if (ancien) {
            List<Entity> entites = List.copyOf(event.getEntities());
            plugin.sync(() -> remplacerAnciens(entites));
        }
    }

    private void reprendreAncienFichier() {
        File ancien = new File(plugin.getDataFolder(), "playerdata.yml");
        String marque = "clicker-repris-" + ANNEE_PLAYERDATA;
        if (!ancien.exists() || plugin.etat().globale(marque) != null) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(ancien);
        Map<String, int[]> lignes = new HashMap<>();
        for (String uuid : yaml.getKeys(false)) {
            int clics = yaml.getInt(uuid + ".clicks", 0);
            if (clics > 0) {
                lignes.put(uuid, new int[]{clics, yaml.getInt(uuid + ".level", 0)});
            }
        }
        plugin.etat().setGlobale(marque, Integer.toString(lignes.size()));
        base.ecrire("reprise playerdata.yml", c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT IGNORE INTO shxmas_clicker (annee, uuid, clics, niveau) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<String, int[]> e : lignes.entrySet()) {
                    r.setInt(1, ANNEE_PLAYERDATA);
                    r.setString(2, e.getKey());
                    r.setInt(3, e.getValue()[0]);
                    r.setInt(4, e.getValue()[1]);
                    r.addBatch();
                }
                r.executeBatch();
            }
        });
        plugin.getLogger().info("playerdata.yml repris dans la base : " + lignes.size() + " joueurs du clicker " + ANNEE_PLAYERDATA);
    }

    /** Écrit les joueurs modifiés (fil principal → file de la base). */
    public void ecrire() {
        if (modifies.isEmpty()) {
            return;
        }
        List<Object[]> lot = new ArrayList<>();
        for (UUID uuid : List.copyOf(modifies)) {
            modifies.remove(uuid);
            Donnees d = donnees.get(uuid);
            if (d != null) {
                lot.add(new Object[]{uuid.toString(), d.clics, d.niveau, d.jour, d.clicsJour});
            }
        }
        base.ecrire("clicker", c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT INTO shxmas_clicker (annee, uuid, clics, niveau, jour, clics_jour)"
                    + " VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE clics = VALUES(clics), niveau = VALUES(niveau),"
                    + " jour = VALUES(jour), clics_jour = VALUES(clics_jour)")) {
                for (Object[] ligne : lot) {
                    r.setInt(1, annee);
                    r.setString(2, (String) ligne[0]);
                    r.setInt(3, (Integer) ligne[1]);
                    r.setInt(4, (Integer) ligne[2]);
                    r.setDate(5, ligne[3] == null ? null : java.sql.Date.valueOf((java.time.LocalDate) ligne[3]));
                    r.setInt(6, (Integer) ligne[4]);
                    r.addBatch();
                }
                r.executeBatch();
            }
        });
    }

    public void arreter() {
        if (ecriture != null) {
            ecriture.cancel();
            ecriture = null;
        }
        ecrire();
    }

    // ---- lecture

    public int clics(UUID uuid) {
        Donnees d = donnees.get(uuid);
        return d == null ? 0 : d.clics;
    }

    public int niveau(UUID uuid) {
        Donnees d = donnees.get(uuid);
        return d == null ? 0 : d.niveau;
    }

    /** Clics d'aujourd'hui (jour de Paris). */
    public int clicsJour(UUID uuid) {
        Donnees d = donnees.get(uuid);
        return d == null || !plugin.horloge().aujourdhui().equals(d.jour) ? 0 : d.clicsJour;
    }

    public boolean connu(UUID uuid) {
        return donnees.containsKey(uuid);
    }

    public int clicsRequis(int niveau) {
        FileConfiguration c = plugin.getConfig();
        double base_ = c.getDouble("clicker.clics-niveau-1", 50);
        double multiplicateur = c.getDouble("clicker.multiplicateur-clics", 1.5);
        return (int) Math.max(1, base_ * Math.pow(multiplicateur, niveau - 1));
    }

    private int recompense(int niveau) {
        FileConfiguration c = plugin.getConfig();
        int montant = c.getInt("clicker.boules-base", 1) + niveau * c.getInt("clicker.boules-par-niveau", 1);
        return Math.min(montant, 192);
    }

    // ---- bonhommes (fil principal)

    public void creer(Location position) {
        Location l = position.clone();
        l.setYaw(Math.round(l.getYaw()));
        ajouter(l);
        sauverBonhommes();
    }

    public boolean retirerProche(Location position) {
        double meilleure = 9;
        Bonhomme cible = null;
        for (Bonhomme b : bonhommes) {
            if (b.monde().equals(position.getWorld())) {
                double d = b.position().distanceSquared(position);
                if (d < meilleure) {
                    meilleure = d;
                    cible = b;
                }
            }
        }
        if (cible == null) {
            return false;
        }
        scene.retirer(cible);
        bonhommes.remove(cible);
        sauverBonhommes();
        return true;
    }

    private void sauverBonhommes() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Bonhommes de neige du clicker : monde x y z lacet (createclicker / removeclicker)."));
        List<String> lignes = new ArrayList<>();
        for (Bonhomme b : bonhommes) {
            Location l = b.position();
            lignes.add(l.getWorld().getName() + " " + l.getX() + " " + l.getY() + " " + l.getZ() + " " + Math.round(l.getYaw()));
        }
        yaml.set("bonhommes", lignes);
        try {
            yaml.save(fichierBonhommes);
        } catch (IOException e) {
            plugin.getLogger().severe("bonhommes.yml non écrit : " + e.getMessage());
        }
    }

    public int nombreBonhommes() {
        return bonhommes.size();
    }

    public List<Location> positions() {
        List<Location> out = new ArrayList<>();
        for (Bonhomme b : bonhommes) {
            out.add(b.position());
        }
        return out;
    }

    /** Arrêt du plugin : les bonhommes quittent la scène (les clients les retirent). */
    public void cacher() {
        for (Bonhomme b : bonhommes) {
            scene.retirer(b);
        }
    }

    // ---- clics (fil du monde)

    /** Clic sur la zone d'un bonhomme (fil principal) : il saute, le clic compte. */
    private void clicBonhomme(Bonhomme bonhomme, Player joueur) {
        Location tete = bonhomme.position().add(0, 1.6 * bonhomme.echelle(), 0);
        if (clic(joueur, tete)) {
            bonhomme.sauter(scene.tick());
        }
    }

    @EventHandler
    public void onDepart(PlayerQuitEvent event) {
        derniersClics.remove(event.getPlayer().getUniqueId());
    }

    /** Un clic : vrai s'il a compté (ni anti-AFK, ni trop rapide). */
    private boolean clic(Player joueur, Location tete) {
        FileConfiguration c = plugin.getConfig();
        UUID uuid = joueur.getUniqueId();
        Donnees d = donnees.computeIfAbsent(uuid, k -> new Donnees());
        int niveauAtteint = -1;
        int clics;
        int niveau;
        synchronized (d) {
            double seuil = c.getDouble("clicker.anti-afk.distance-min", 0.4);
            int maxImmobiles = c.getInt("clicker.anti-afk.clics-immobiles", 100);
            Location ici = joueur.getLocation();
            boolean bouge = d.derniere == null || !Objects.equals(d.derniere.getWorld(), ici.getWorld());
            if (!bouge) {
                double dx = ici.getX() - d.derniere.getX();
                double dz = ici.getZ() - d.derniere.getZ();
                bouge = dx * dx + dz * dz > seuil * seuil;
            }
            if (bouge) {
                d.immobiles = 0;
                d.derniere = ici;
            } else {
                d.immobiles++;
            }
            if (d.immobiles >= maxImmobiles) {
                joueur.playSound(ici, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                joueur.showTitle(Title.title(Component.empty(), MiniMessage.miniMessage().deserialize(antiAfk),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(500))));
                return false;
            }
            long maintenant = System.currentTimeMillis();
            long intervalle = 1000L / Math.max(1, c.getInt("clicker.max-cps", 6));
            Long dernier = derniersClics.get(uuid);
            if (dernier != null && maintenant - dernier < intervalle) {
                return false;
            }
            derniersClics.put(uuid, maintenant);
            d.clics++;
            java.time.LocalDate aujourdhui = plugin.horloge().aujourdhui();
            if (!aujourdhui.equals(d.jour)) {
                d.jour = aujourdhui;
                d.clicsJour = 0;
            }
            d.clicsJour++;
            if (d.clics >= clicsRequis(d.niveau + 1)) {
                d.niveau++;
                niveauAtteint = d.niveau;
            }
            clics = d.clics;
            niveau = d.niveau;
        }
        modifies.add(uuid);
        Son son = sonClic;
        if (son != null) {
            son.jouer(joueur);
        }
        tete.getWorld().spawnParticle(Particle.SNOWFLAKE, tete, 7, 0.3, 0.4, 0.3, 0.05);
        tete.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, tete, 3, 0.2, 0.2, 0.2, 0.1);
        String requis = String.valueOf(clicsRequis(niveau + 1));
        String[] remplacements = {"%clicks%", String.valueOf(clics), "%required%", requis, "%level%", String.valueOf(niveau),
                "%next_reward%", String.valueOf(recompense(niveau + 1))};
        joueur.sendActionBar(MiniMessage.miniMessage().deserialize(remplacer(barre, remplacements)));
        if (sousTitre != null && !sousTitre.isEmpty()) {
            joueur.showTitle(Title.title(Component.empty(), MiniMessage.miniMessage().deserialize(remplacer(sousTitre, remplacements)),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(250))));
        }
        if (niveauAtteint > 0) {
            int atteint = niveauAtteint;
            plugin.sync(() -> monter(joueur, atteint));
        }
        return true;
    }

    private static String remplacer(String texte, String[] remplacements) {
        String out = texte == null ? "" : texte;
        for (int i = 0; i + 1 < remplacements.length; i += 2) {
            out = out.replace(remplacements[i], remplacements[i + 1]);
        }
        return out;
    }

    /** Niveau atteint (fil principal) : boules de neige, message, tampon de la carte. */
    private void monter(Player joueur, int niveau) {
        if (joueur.isOnline()) {
            ItemStack boules = plugin.boules().creer(1);
            int montant = recompense(niveau);
            while (montant > 0) {
                int lot = Math.min(64, montant);
                ItemStack pile = boules.clone();
                pile.setAmount(lot);
                Joueurs.donner(joueur, pile);
                montant -= lot;
            }
            joueur.sendMessage(MiniMessage.miniMessage().deserialize(niveauTexte.replace("%level%", String.valueOf(niveau))
                    .replace("%amount%", String.valueOf(recompense(niveau)))));
            joueur.playSound(joueur.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        plugin.carte().niveauClicker(joueur.getUniqueId(), joueur.getName(), niveau);
    }
}
