package org.Cry_Legende.sHChristmasPresents.village;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cadeaux du village : ils apparaissent dans village.zone et s'ouvrent avec une boule de neige magique. Chaque rareté
 * (village.raretes) a sa chance, ses modèles et ses commandes ; feu-artifice ajoute un feu d'artifice, et messages.yml
 * village.titres.&lt;rareté&gt; un titre court. Les touches arrivent sur le fil du monde : elles sont jouées sur le fil
 * principal, où un cadeau ne s'ouvre qu'une fois.
 */
public final class CadeauxVillage implements Listener {

    private record Instance(Interaction interaction, List<Display> parties) {
        void retirer() {
            for (Display d : parties) {
                d.remove();
            }
            if (interaction != null) {
                interaction.remove();
            }
        }
    }

    private final SHChristmasPresents plugin;
    private final BouleDeNeige boules;
    private final NamespacedKey cleCadeau;
    private final NamespacedKey cleRarete;
    private final NamespacedKey cleFeu;
    private final Map<UUID, Instance> actifs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> attentes = new ConcurrentHashMap<>();
    private volatile Map<String, Modeles.Modele> modeles = Map.of();
    private volatile Location min;
    private volatile Location max;
    private BukkitTask tache;

    public CadeauxVillage(SHChristmasPresents plugin, BouleDeNeige boules) {
        this.plugin = plugin;
        this.boules = boules;
        this.cleCadeau = new NamespacedKey(plugin, "xmas_gift");
        this.cleRarete = new NamespacedKey(plugin, "xmas_rarity");
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
    }

    public int nombreModeles() {
        return modeles.size();
    }

    public void arreter() {
        if (tache != null) {
            tache.cancel();
            tache = null;
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
        for (Instance instance : actifs.values()) {
            instance.retirer();
        }
        actifs.clear();
        Location bas = min;
        if (bas != null && bas.getWorld() != null) {
            for (Entity entite : bas.getWorld().getEntities()) {
                if (entite.getPersistentDataContainer().has(cleCadeau, PersistentDataType.BYTE)) {
                    entite.remove();
                }
            }
        }
    }

    // ---- apparition (fil principal)

    private void tic() {
        long maintenant = System.currentTimeMillis();
        attentes.values().removeIf(fin -> fin < maintenant);
        if (min == null || max == null) {
            return;
        }
        actifs.entrySet().removeIf(entree -> {
            Entity entite = Bukkit.getEntity(entree.getKey());
            if (entite == null || !entite.isValid()) {
                entree.getValue().retirer();
                return true;
            }
            return false;
        });
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
        poser(modele, rarete);
    }

    private void poser(Modeles.Modele modele, String rarete) {
        World monde = min.getWorld();
        if (monde == null) {
            return;
        }
        double x = ThreadLocalRandom.current().nextDouble(min.getX(), Math.max(min.getX() + 0.01, max.getX()));
        double z = ThreadLocalRandom.current().nextDouble(min.getZ(), Math.max(min.getZ() + 0.01, max.getZ()));
        // Pas de chargement de chunk : un cadeau hors des chunks chargés n'est vu par personne
        if (!monde.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            return;
        }
        Block sol = monde.getHighestBlockAt((int) Math.floor(x), (int) Math.floor(z));
        Location position = sol.getLocation().add(0.5, 1.0 + plugin.getConfig().getDouble("village.decalage-vertical", 0.0), 0.5);
        if (position.getY() < min.getY() || position.getY() > max.getY()) {
            return;
        }
        for (Instance actif : actifs.values()) {
            if (actif.interaction().isValid() && actif.interaction().getLocation().distanceSquared(position) < 2.25) {
                return;
            }
        }
        float lacet = ThreadLocalRandom.current().nextFloat() * 360f;
        position.setYaw(lacet);
        Matrix4f rotation = new Matrix4f().rotateY((float) Math.toRadians(-lacet));
        Interaction interaction = monde.spawn(position.clone(), Interaction.class, e -> {
            e.setInteractionWidth(modele.largeur);
            e.setInteractionHeight(modele.hauteur);
            e.setResponsive(true);
            // Jamais sauvé avec le chunk : un cadeau déchargé ou présent à l'arrêt ne revient pas orphelin
            e.setPersistent(false);
            e.getPersistentDataContainer().set(cleCadeau, PersistentDataType.BYTE, (byte) 1);
            e.getPersistentDataContainer().set(cleRarete, PersistentDataType.STRING, rarete);
        });
        monde.playSound(position, Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.5f);
        monde.spawnParticle(Particle.CLOUD, position.clone().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.05);
        List<Display> parties = new ArrayList<>();
        for (Modeles.Partie partie : modele.parties) {
            Matrix4f ajustee = new Matrix4f(partie.matrice).translateLocal(modele.recentrage);
            Matrix4f finale = new Matrix4f(rotation).mul(ajustee);
            Class<? extends Display> classe = partie.type == Modeles.Type.BLOCK ? BlockDisplay.class : ItemDisplay.class;
            Display affichage = monde.spawn(position, classe, e -> {
                e.setRotation(lacet, 0);
                e.setPersistent(false);
                e.getPersistentDataContainer().set(cleCadeau, PersistentDataType.BYTE, (byte) 1);
                if (e instanceof ItemDisplay item) {
                    item.setItemStack(objetPartie(partie));
                } else if (e instanceof BlockDisplay bloc) {
                    bloc.setBlock(partie.bloc != null ? partie.bloc : Bukkit.createBlockData(Material.STONE));
                }
                e.setTransformationMatrix(finale);
            });
            parties.add(affichage);
        }
        actifs.put(interaction.getUniqueId(), new Instance(interaction, parties));
        plugin.debug("Cadeau " + rarete + " (" + modele.nom + ") en " + position.toVector());
    }

    private static ItemStack objetPartie(Modeles.Partie partie) {
        if (partie.texture == null) {
            return new ItemStack(partie.materiau != null ? partie.materiau : Material.STONE);
        }
        ItemStack tete = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) tete.getItemMeta();
        PlayerProfile profil = Bukkit.createProfile(UUID.randomUUID(), "GP");
        profil.setProperty(new ProfileProperty("textures", partie.texture));
        meta.setPlayerProfile(profil);
        tete.setItemMeta(meta);
        return tete;
    }

    // ---- touches (fil du monde → fil principal)

    private boolean estCadeau(Entity entite) {
        return entite instanceof Interaction && entite.getPersistentDataContainer().has(cleCadeau, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onDegats(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework feu && feu.getPersistentDataContainer().has(cleFeu, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            return;
        }
        if (!estCadeau(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Snowball boule && boule.getShooter() instanceof Player joueur
                && boule.getPersistentDataContainer().has(boules.cle(), PersistentDataType.BYTE)) {
            Interaction interaction = (Interaction) event.getEntity();
            boule.remove();
            plugin.sync(() -> toucher(joueur, interaction));
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
        if (estCadeau(event.getHitEntity())) {
            Interaction interaction = (Interaction) event.getHitEntity();
            plugin.sync(() -> toucher(joueur, interaction));
            return;
        }
        Location impact = event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0.5, 0.5, 0.5) : boule.getLocation();
        Interaction proche = null;
        double meilleure = Double.MAX_VALUE;
        for (Entity e : impact.getWorld().getNearbyEntities(impact, 1.5, 1.5, 1.5)) {
            if (estCadeau(e)) {
                double d = e.getLocation().distanceSquared(impact);
                if (d < meilleure) {
                    meilleure = d;
                    proche = (Interaction) e;
                }
            }
        }
        Interaction cible = proche;
        plugin.sync(() -> {
            if (cible != null) {
                toucher(joueur, cible);
            } else {
                rendreBoule(joueur);
            }
        });
    }

    @EventHandler
    public void onClicDroit(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !estCadeau(event.getRightClicked())) {
            return;
        }
        Player joueur = event.getPlayer();
        ItemStack enMain = joueur.getInventory().getItemInMainHand();
        if (!boules.est(enMain)) {
            return;
        }
        event.setCancelled(true);
        Interaction interaction = (Interaction) event.getRightClicked();
        plugin.sync(() -> {
            ItemStack main = joueur.getInventory().getItemInMainHand();
            if (!boules.est(main)) {
                return;
            }
            if (joueur.getGameMode() != GameMode.CREATIVE) {
                main.subtract(1);
            }
            toucher(joueur, interaction);
        });
    }

    private void rendreBoule(Player joueur) {
        if (joueur.isOnline() && joueur.getGameMode() != GameMode.CREATIVE) {
            org.Cry_Legende.sHChristmasPresents.commun.Joueurs.donner(joueur, boules.creer(1));
        }
    }

    /** Fil principal : un cadeau ne s'ouvre qu'une fois, la touche d'après rend la boule. */
    private void toucher(Player joueur, Interaction interaction) {
        if (!joueur.isOnline()) {
            return;
        }
        Long fin = attentes.get(joueur.getUniqueId());
        if (fin != null && fin > System.currentTimeMillis()) {
            joueur.sendMessage(plugin.messages().mini("cooldown_message", "%time%", String.valueOf((fin - System.currentTimeMillis()) / 1000)));
            rendreBoule(joueur);
            return;
        }
        Instance instance = actifs.remove(interaction.getUniqueId());
        if (instance == null || !interaction.isValid()) {
            rendreBoule(joueur);
            return;
        }
        String rarete = interaction.getPersistentDataContainer().get(cleRarete, PersistentDataType.STRING);
        if (rarete == null) {
            rarete = "commun";
        }
        attentes.put(joueur.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getInt("village.attente-secondes", 1) * 1000L);
        Location position = interaction.getLocation();
        instance.retirer();
        World monde = position.getWorld();
        monde.spawnParticle(Particle.DUST, position.clone().add(0, 0.5, 0), 20, 0.5, 0.5, 0.5, new Particle.DustOptions(Color.RED, 1.5f));
        monde.playSound(position, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        monde.playSound(position, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.5f);
        recompenser(joueur, rarete, position);
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

    /** Cadeaux restés dans un chunk sauvegardé (versions précédentes) : plus suivis, on les retire. */
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
