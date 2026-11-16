package org.Cry_Legende.sHChristmasPresents.village;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.rendu.Scene;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Snow;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cadeaux du village : ils apparaissent dans village.zone (montrés par paquets, aucune entité côté serveur) et
 * s'ouvrent avec une boule de neige magique, lancée ou tenue en main au clic. Chaque rareté (village.raretes) a sa
 * chance, ses modèles, son échelle, la vitesse de son couvercle et ses commandes ; feu-artifice ajoute un feu
 * d'artifice, messages.yml village.titres.&lt;rareté&gt; un titre court.
 * <p>
 * Une boule lancée est suivie à chaque tick : le trajet qu'elle va faire pendant le tick (position + vitesse) est
 * testé contre la boîte de chaque cadeau, si bien qu'un cadeau traversé s'ouvre même si la boule ne s'y arrête pas.
 * Une boule qui tombe à côté d'un cadeau (1,2 bloc) l'ouvre aussi ; une boule perdue est rendue.
 */
public final class CadeauxVillage implements Listener {

    private final SHChristmasPresents plugin;
    private final BouleDeNeige boules;
    private final Scene scene;
    private final NamespacedKey cleCadeau;
    private final NamespacedKey cleFeu;
    private final List<Cadeau> actifs = new ArrayList<>();
    private final Map<UUID, Snowball> lancees = new ConcurrentHashMap<>();
    private final Map<UUID, Long> attentes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> explications = new ConcurrentHashMap<>();
    private volatile Map<String, Modeles.Modele> modeles = Map.of();
    private volatile Location min;
    private volatile Location max;
    private BukkitTask tache;
    private BukkitTask suivi;

    public CadeauxVillage(SHChristmasPresents plugin, BouleDeNeige boules, Scene scene) {
        this.plugin = plugin;
        this.boules = boules;
        this.scene = scene;
        this.cleCadeau = new NamespacedKey(plugin, "xmas_gift");
        this.cleFeu = new NamespacedKey(plugin, "xmas_firework");
    }

    // ---- chargement (fil principal)

    public void demarrer() {
        zone();
        modeles = Map.copyOf(Modeles.charger(new java.io.File(plugin.getDataFolder(), "models"), plugin.getLogger()));
        nettoyer();
        if (tache != null) {
            tache.cancel();
        }
        long periode = Math.max(1L, plugin.getConfig().getLong("village.apparition-ticks", 20L));
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tic, 100L, periode);
        if (suivi == null) {
            suivi = Bukkit.getScheduler().runTaskTimer(plugin, this::suivre, 1L, 1L);
        }
    }

    public int nombreModeles() {
        return modeles.size();
    }

    public void arreter() {
        if (tache != null) {
            tache.cancel();
            tache = null;
        }
        if (suivi != null) {
            suivi.cancel();
            suivi = null;
        }
        nettoyer();
    }

    void zone() {
        FileConfiguration config = plugin.getConfig();
        min = null;
        max = null;
        if (config.contains("village.zone.monde")) {
            World monde = Bukkit.getWorld(config.getString("village.zone.monde", ""));
            if (monde != null) {
                double x1 = config.getDouble("village.zone.x1"), y1 = config.getDouble("village.zone.y1"), z1 = config.getDouble("village.zone.z1");
                double x2 = config.getDouble("village.zone.x2"), y2 = config.getDouble("village.zone.y2"), z2 = config.getDouble("village.zone.z2");
                min = new Location(monde, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
                max = new Location(monde, Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
            }
        }
    }

    /** /shchristmaspresents setregion : coin 1 ou 2 à la position du joueur. */
    public void definirCoin(Player joueur, String coin) {
        Location l = joueur.getLocation();
        FileConfiguration config = plugin.getConfig();
        config.set("village.zone.monde", l.getWorld().getName());
        config.set("village.zone.x" + coin, Math.round(l.getX() * 100) / 100.0);
        config.set("village.zone.y" + coin, Math.round(l.getY() * 100) / 100.0);
        config.set("village.zone.z" + coin, Math.round(l.getZ() * 100) / 100.0);
        plugin.saveConfig();
        zone();
    }

    private void nettoyer() {
        for (Cadeau c : actifs) {
            scene.retirer(c);
        }
        actifs.clear();
    }

    // ---- apparition (fil principal)

    private void tic() {
        long maintenant = System.currentTimeMillis();
        attentes.values().removeIf(fin -> fin < maintenant);
        explications.values().removeIf(fin -> fin < maintenant);
        // Cadeaux ouverts : retirés une fois l'animation jouée, même si plus personne ne les regarde
        long t = scene.tick();
        actifs.removeIf(c -> {
            if (c.ouvertDepuis(t) > 12) {
                scene.retirer(c);
                return true;
            }
            return false;
        });
        if (min == null || max == null) {
            return;
        }
        if (actifs.size() >= plugin.getConfig().getInt("village.max-cadeaux", 30)) {
            return;
        }
        apparaitre();
    }

    private void apparaitre() {
        if (modeles.isEmpty()) {
            return;
        }
        ConfigurationSection raretes = plugin.getConfig().getConfigurationSection("village.raretes");
        if (raretes == null) {
            return;
        }
        double total = 0;
        Map<String, Double> poids = new HashMap<>();
        for (String cle : raretes.getKeys(false)) {
            double chance = raretes.getDouble(cle + ".chance", 0);
            if (chance > 0) {
                poids.put(cle, chance);
                total += chance;
            }
        }
        if (total <= 0) {
            return;
        }
        double tirage = ThreadLocalRandom.current().nextDouble() * total;
        String rarete = null;
        for (Map.Entry<String, Double> entree : poids.entrySet()) {
            tirage -= entree.getValue();
            if (tirage <= 0) {
                rarete = entree.getKey();
                break;
            }
        }
        if (rarete == null) {
            return;
        }
        List<String> noms = raretes.getStringList(rarete + ".modeles");
        if (noms.isEmpty()) {
            return;
        }
        Modeles.Modele modele = modeles.get(noms.get(ThreadLocalRandom.current().nextInt(noms.size())));
        if (modele == null) {
            plugin.debug("Modèle introuvable pour " + rarete);
            return;
        }
        float echelle = (float) raretes.getDouble(rarete + ".echelle", 1.0);
        double vitesse = raretes.getDouble(rarete + ".vitesse-couvercle", 1.0);
        poser(modele, rarete, echelle, vitesse);
    }

    private void poser(Modeles.Modele modele, String rarete, float echelle, double vitesse) {
        World monde = min.getWorld();
        if (monde == null) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double demi = modele.largeur * echelle / 2.0;
        double[] place = trouverPlace(monde, r, demi, modele.hauteur * echelle);
        if (place == null) {
            return;
        }
        float lacet = r.nextFloat() * 360f;
        Cadeau cadeau = new Cadeau(monde, place[0], place[1], place[2], lacet, echelle, vitesse, modele, rarete, this::clicMain);
        actifs.add(cadeau);
        scene.ajouter(cadeau);
        Location ici = new Location(monde, place[0], place[1], place[2]);
        monde.playSound(ici, Sound.ENTITY_CHICKEN_EGG, 0.6f, 0.5f);
        monde.spawnParticle(Particle.CLOUD, ici.clone().add(0, 0.4, 0), 5, 0.2, 0.2, 0.2, 0.03);
        plugin.debug("Cadeau " + rarete + " (" + modele.nom + ") en " + ici.toVector());
    }

    /**
     * Une place libre dans la zone : le dessus réel du bloc (dalle du bas, couche de neige, tapis), jamais un escalier,
     * une barrière, une clôture, un mur, des feuilles, de l'eau ou un bloc de décoration ; assez de place au-dessus ;
     * aucune entité (décoration, PNJ) ni autre cadeau tout près. Le cadeau n'est pas toujours au centre du bloc : il
     * glisse au hasard tant qu'il reste posé sur une surface de même hauteur. Rend x, y, z ou null.
     */
    private double[] trouverPlace(World monde, ThreadLocalRandom r, double demi, double hauteur) {
        double x = r.nextDouble(min.getX(), Math.max(min.getX() + 0.01, max.getX()));
        double z = r.nextDouble(min.getZ(), Math.max(min.getZ() + 0.01, max.getZ()));
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        // Pas de chargement de chunk : un cadeau hors des chunks chargés ne serait vu par personne
        if (!monde.isChunkLoaded(bx >> 4, bz >> 4)) {
            return null;
        }
        int haut = (int) Math.floor(max.getY()) + 1, bas = (int) Math.floor(min.getY()) - 1;
        Double dessus = null;
        for (int by = haut; by >= bas; by--) {
            Block b = monde.getBlockAt(bx, by, bz);
            if (b.getType().isAir()) {
                continue;
            }
            Double s = surface(b);
            if (s == null && b.isPassable() && !b.isLiquid()) {
                continue; // herbe, fleurs, torches… : on regarde en dessous
            }
            dessus = s;
            break;
        }
        if (dessus == null || dessus < min.getY() || dessus > max.getY() + 1) {
            return null;
        }
        double sol = dessus;
        // Place au-dessus : de l'air sur toute la hauteur du cadeau (sauf la couche de neige ou le tapis porteurs)
        for (int by = (int) Math.floor(sol - 0.001); by <= (int) Math.floor(sol + hauteur + 0.2); by++) {
            Block b = monde.getBlockAt(bx, by, bz);
            if (b.getY() + 1 <= sol + 0.001 || b.getType().isAir()) {
                continue;
            }
            Double s = surface(b);
            if (s == null || Math.abs(s - sol) > 0.001) {
                return null;
            }
        }
        // Décalage au hasard dans le bloc, gardé seulement si le cadeau repose entièrement sur la même hauteur
        double cx = bx + 0.5, cz = bz + 0.5;
        double jeu = Math.max(0.0, 0.42 - demi * 0.5);
        double ox = cx + (r.nextDouble() * 2 - 1) * jeu, oz = cz + (r.nextDouble() * 2 - 1) * jeu;
        double appui = Math.max(0.05, demi * 0.6);
        if (memeHauteur(monde, ox, oz, appui, sol)) {
            cx = ox;
            cz = oz;
        } else if (!memeHauteur(monde, cx, cz, appui, sol)) {
            return null;
        }
        // Entités sur le bloc (décorations, PNJ, cadres…) et autres cadeaux
        BoundingBox zone = new BoundingBox(cx - demi - 0.3, sol - 0.3, cz - demi - 0.3, cx + demi + 0.3, sol + hauteur + 0.3, cz + demi + 0.3);
        for (Entity e : monde.getNearbyEntities(zone)) {
            if (!(e instanceof Player)) {
                return null;
            }
        }
        for (Cadeau c : actifs) {
            if (c.monde().equals(monde) && c.distanceCarre(cx, sol, cz) < 2.25) {
                return null;
            }
        }
        // Les bonhommes de neige n'ont pas d'entité côté serveur : on garde leur place libre
        for (Location b : plugin.clicker().positions()) {
            if (monde.equals(b.getWorld())) {
                double dx = b.getX() - cx, dz = b.getZ() - cz;
                if (dx * dx + dz * dz < 1.6 * 1.6 && Math.abs(b.getY() - sol) < 3) {
                    return null;
                }
            }
        }
        return new double[]{cx, sol, cz};
    }

    /** Hauteur exacte du dessus d'un bloc sur lequel un cadeau peut reposer, ou null si on n'y pose rien. */
    private static Double surface(Block b) {
        Material m = b.getType();
        if (b.isLiquid() || m == Material.BARRIER || m == Material.LIGHT || m == Material.STRUCTURE_VOID
                || Tag.STAIRS.isTagged(m) || Tag.FENCES.isTagged(m) || Tag.FENCE_GATES.isTagged(m) || Tag.WALLS.isTagged(m)
                || Tag.LEAVES.isTagged(m) || Tag.TRAPDOORS.isTagged(m) || Tag.BEDS.isTagged(m) || Tag.ALL_SIGNS.isTagged(m)
                || m == Material.IRON_BARS || m.name().endsWith("GLASS_PANE") || m == Material.IRON_CHAIN
                || m == Material.LANTERN || m == Material.SOUL_LANTERN || m == Material.PLAYER_HEAD
                || m == Material.PLAYER_WALL_HEAD || m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.ENDER_CHEST) {
            return null;
        }
        if (b.getBlockData() instanceof Snow neige) {
            return b.getY() + neige.getLayers() / 8.0;
        }
        if (Tag.WOOL_CARPETS.isTagged(m) || m == Material.MOSS_CARPET || m == Material.PALE_MOSS_CARPET) {
            return b.getY() + 1 / 16.0;
        }
        if (b.getBlockData() instanceof Slab dalle) {
            return dalle.getType() == Slab.Type.BOTTOM ? b.getY() + 0.5 : b.getY() + 1.0;
        }
        if (b.getBlockData() instanceof Bisected) {
            return null; // portes, grandes plantes
        }
        if (b.isPassable()) {
            return null;
        }
        BoundingBox boite = b.getBoundingBox();
        // Un bloc plein ou presque : son dessus ; sinon (pots, enclumes, bougies…) on n'y pose rien
        if (boite.getWidthX() < 0.99 || boite.getWidthZ() < 0.99) {
            return null;
        }
        return boite.getMaxY();
    }

    /** Vrai si les quatre coins de l'appui (x ± a, z ± a) reposent sur un dessus à la hauteur sol. */
    private static boolean memeHauteur(World monde, double x, double z, double a, double sol) {
        int y = (int) Math.floor(sol - 0.001);
        for (double[] c : new double[][]{{x - a, z - a}, {x + a, z - a}, {x - a, z + a}, {x + a, z + a}}) {
            int bx = (int) Math.floor(c[0]), bz = (int) Math.floor(c[1]);
            Double s = null;
            for (int by = y + 1; by >= y - 1 && s == null; by--) {
                Block b = monde.getBlockAt(bx, by, bz);
                if (b.getType().isAir()) {
                    continue;
                }
                s = surface(b);
                if (s == null) {
                    return false;
                }
            }
            if (s == null || Math.abs(s - sol) > 0.06) {
                return false;
            }
        }
        return true;
    }

    // ---- boules lancées (fil du monde → suivi au fil principal)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLancer(PlayerLaunchProjectileEvent event) {
        if (event.getProjectile() instanceof Snowball boule && boule.getPersistentDataContainer().has(boules.cle(), PersistentDataType.BYTE)) {
            lancees.put(boule.getUniqueId(), boule);
        }
    }

    /** Chaque tick : le trajet du tick à venir de chaque boule est testé contre les cadeaux. */
    private void suivre() {
        if (lancees.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Snowball> e : List.copyOf(lancees.entrySet())) {
            Snowball boule = e.getValue();
            if (!boule.isValid()) {
                // Arrêtée par un bloc ou une entité : onImpact s'en charge (ou elle a disparu)
                Bukkit.getScheduler().runTaskLater(plugin, () -> lancees.remove(e.getKey()), 2L);
                continue;
            }
            Location p = boule.getLocation();
            Vector v = boule.getVelocity();
            Cadeau cible = null;
            double meilleur = Double.MAX_VALUE;
            for (Cadeau c : actifs) {
                if (c.ouvert() || !c.monde().equals(p.getWorld())) {
                    continue;
                }
                double t = c.touche(p.getX(), p.getY(), p.getZ(), v.getX(), v.getY(), v.getZ(), 0.2);
                if (t >= 0 && t < meilleur) {
                    meilleur = t;
                    cible = c;
                }
            }
            if (cible != null && lancees.remove(e.getKey()) != null) {
                boule.remove();
                if (boule.getShooter() instanceof Player joueur) {
                    toucher(joueur, cible);
                }
            }
        }
    }

    @EventHandler
    public void onImpact(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball boule) || !boule.getPersistentDataContainer().has(boules.cle(), PersistentDataType.BYTE)) {
            return;
        }
        if (!(boule.getShooter() instanceof Player joueur)) {
            return;
        }
        UUID id = boule.getUniqueId();
        Location impact = boule.getLocation();
        plugin.sync(() -> {
            if (lancees.remove(id) == null) {
                return; // déjà comptée par le suivi
            }
            Cadeau proche = null;
            double meilleure = 1.2 * 1.2;
            for (Cadeau c : actifs) {
                if (!c.ouvert() && c.monde().equals(impact.getWorld())) {
                    double d = c.distanceCarre(impact.getX(), impact.getY(), impact.getZ());
                    if (d < meilleure) {
                        meilleure = d;
                        proche = c;
                    }
                }
            }
            if (proche != null) {
                toucher(joueur, proche);
            } else {
                rendreBoule(joueur);
            }
        });
    }

    @EventHandler
    public void onDegats(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework feu && feu.getPersistentDataContainer().has(cleFeu, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    // ---- clic sur un cadeau (fil principal)

    /** Clic gauche ou droit : avec une boule magique en main, elle est utilisée ; sinon on explique au joueur. */
    private void clicMain(Cadeau cadeau, Player joueur) {
        if (cadeau.ouvert()) {
            return;
        }
        ItemStack main = joueur.getInventory().getItemInMainHand();
        if (boules.est(main)) {
            if (joueur.getGameMode() != GameMode.CREATIVE) {
                main.subtract(1);
            }
            toucher(joueur, cadeau);
            return;
        }
        long maintenant = System.currentTimeMillis();
        Long fin = explications.get(joueur.getUniqueId());
        if (fin != null && fin > maintenant) {
            return;
        }
        explications.put(joueur.getUniqueId(), maintenant + 4000L);
        plugin.messages().envoyer(joueur, "village.a-la-main");
        joueur.playSound(joueur.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 0.8f);
    }

    private void rendreBoule(Player joueur) {
        if (joueur.isOnline() && joueur.getGameMode() != GameMode.CREATIVE) {
            org.Cry_Legende.sHChristmasPresents.commun.Joueurs.donner(joueur, boules.creer(1));
        }
    }

    /** Fil principal : un cadeau ne s'ouvre qu'une fois, la touche d'après rend la boule. */
    private void toucher(Player joueur, Cadeau cadeau) {
        if (!joueur.isOnline()) {
            return;
        }
        Long fin = attentes.get(joueur.getUniqueId());
        if (fin != null && fin > System.currentTimeMillis()) {
            joueur.sendMessage(plugin.messages().mini("cooldown_message", "%time%", String.valueOf((fin - System.currentTimeMillis()) / 1000)));
            rendreBoule(joueur);
            return;
        }
        if (cadeau.ouvert() || !actifs.contains(cadeau)) {
            rendreBoule(joueur);
            return;
        }
        attentes.put(joueur.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getInt("village.attente-secondes", 1) * 1000L);
        long t = scene.tick();
        cadeau.ouvrir(t);
        Bukkit.getScheduler().runTaskLater(plugin, cadeau::retrecir, 5L);
        Location position = new Location(cadeau.monde(), cadeau.x(), cadeau.y(), cadeau.z());
        World monde = position.getWorld();
        Color couleur = switch (cadeau.rarete) {
            case "rare" -> Color.fromRGB(0x5AB4FF);
            case "dore" -> Color.fromRGB(0xFFD24A);
            default -> Color.RED;
        };
        monde.spawnParticle(Particle.DUST, position.clone().add(0, 0.6, 0), 24, 0.45, 0.45, 0.45, new Particle.DustOptions(couleur, 1.4f));
        monde.spawnParticle(Particle.SNOWFLAKE, position.clone().add(0, 0.8, 0), 10, 0.3, 0.3, 0.3, 0.05);
        monde.playSound(position, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        monde.playSound(position, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.5f);
        monde.playSound(position, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        recompenser(joueur, cadeau.rarete, position);
    }

    private void recompenser(Player joueur, String rarete, Location position) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("village.raretes." + rarete);
        if (section == null) {
            return;
        }
        for (String commande : section.getStringList("commandes")) {
            String c = commande.replace("%player%", joueur.getName()).replace("{joueur}", joueur.getName());
            if (c.startsWith("msg:")) {
                joueur.sendMessage(MiniMessage.miniMessage().deserialize(c.substring(4)));
            } else {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.startsWith("/") ? c.substring(1) : c);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Commande de cadeau en erreur : " + c + " (" + e.getMessage() + ")");
                }
            }
        }
        if (section.getBoolean("feu-artifice", false)) {
            feuDArtifice(position);
        }
        String titre = "village.titres." + rarete;
        if (plugin.messages().existe(titre)) {
            String sousTitre = "village.sous-titres." + rarete;
            joueur.showTitle(Title.title(plugin.messages().texte(titre),
                    plugin.messages().existe(sousTitre) ? plugin.messages().texte(sousTitre) : net.kyori.adventure.text.Component.empty(),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(500))));
        }
    }

    private void feuDArtifice(Location position) {
        Firework feu = position.getWorld().spawn(position.clone().add(0, 0.8, 0), Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.STAR).withColor(Color.fromRGB(0xFFD700), Color.WHITE)
                    .withFade(Color.fromRGB(0xFFF2B3)).flicker(true).build());
            meta.setPower(0);
            f.setFireworkMeta(meta);
            f.setPersistent(false);
            f.getPersistentDataContainer().set(cleFeu, PersistentDataType.BYTE, (byte) 1);
        });
        Bukkit.getScheduler().runTaskLater(plugin, feu::detonate, 2L);
    }

    // ---- monde

    /** Le monde de la zone peut être chargé après le plugin (PhantomWorlds). */
    @EventHandler
    public void onMondeCharge(WorldLoadEvent event) {
        if ((min == null || min.getWorld() == null) && event.getWorld().getName().equals(plugin.getConfig().getString("village.zone.monde"))) {
            plugin.sync(this::zone);
        }
    }

    /** Cadeaux des versions 1.x à 2.0 (vraies entités) restés dans un chunk sauvegardé : retirés. */
    @EventHandler
    public void onEntites(EntitiesLoadEvent event) {
        for (Entity entite : event.getEntities()) {
            if (entite.getPersistentDataContainer().has(cleCadeau, PersistentDataType.BYTE)) {
                entite.remove();
            }
        }
    }

    public int actifs() {
        return actifs.size();
    }
}
