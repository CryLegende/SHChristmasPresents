package org.Cry_Legende.sHChristmasPresents.drop;

import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetConfig;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetsMarques;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /shchristmaspresents drop &lt;zone&gt; &lt;secondes&gt; &lt;nombre&gt; : des « Cadeaux du ciel » tombent au hasard dans une zone
 * pendant ce temps. Ramassé par un joueur = un tirage dans drops.recompenses ; ni entonnoir ni mob ne les prennent ;
 * ceux qui restent disparaissent à la fin + disparition-secondes.
 */
public final class Drops implements Listener {

    public record Zone(String id, String monde, double x1, double y1, double z1, double x2, double y2, double z2) {
    }

    record Recompense(String id, int poids, List<String> commandes) {
    }

    private final class Pluie {
        final Zone zone;
        final World monde;
        final int nombre;
        final int dureeTicks;
        final Set<UUID> objets = ConcurrentHashMap.newKeySet();
        int lances;
        int ticks;
        BukkitTask tache;

        Pluie(Zone zone, World monde, int nombre, int dureeTicks) {
            this.zone = zone;
            this.monde = monde;
            this.nombre = nombre;
            this.dureeTicks = dureeTicks;
        }
    }

    private final SHChristmasPresents plugin;
    private final NamespacedKey cle;
    private final Map<UUID, Pluie> vivants = new ConcurrentHashMap<>();
    private final List<Pluie> pluies = new ArrayList<>();
    private volatile Map<String, Zone> zones = Map.of();
    private volatile List<Recompense> recompenses = List.of();
    private volatile int disparition = 30;
    private volatile boolean lueurEntite = true;
    private volatile ObjetConfig objet = new ObjetConfig(Material.PAPER, NamespacedKey.minecraft("red_shulker_box"), true, null);

    public Drops(SHChristmasPresents plugin) {
        this.plugin = plugin;
        this.cle = new NamespacedKey(plugin, "drop");
    }

    public void configurer(ConfigurationSection s) {
        if (s == null) {
            return;
        }
        Map<String, Zone> lues = new LinkedHashMap<>();
        ConfigurationSection z = s.getConfigurationSection("zones");
        if (z != null) {
            for (String id : z.getKeys(false)) {
                ConfigurationSection zone = z.getConfigurationSection(id);
                if (zone == null || zone.getString("monde") == null) {
                    plugin.getLogger().warning("drops.zones." + id + " : monde manquant, zone ignorée");
                    continue;
                }
                lues.put(id.toLowerCase(), new Zone(id, zone.getString("monde"), zone.getDouble("x1"), zone.getDouble("y1"),
                        zone.getDouble("z1"), zone.getDouble("x2"), zone.getDouble("y2"), zone.getDouble("z2")));
            }
        }
        zones = java.util.Collections.unmodifiableMap(lues);
        List<Recompense> lus = new ArrayList<>();
        ConfigurationSection r = s.getConfigurationSection("recompenses");
        if (r != null) {
            for (String id : r.getKeys(false)) {
                ConfigurationSection rec = r.getConfigurationSection(id);
                int poids = rec == null ? 0 : rec.getInt("poids", 0);
                if (poids > 0) {
                    lus.add(new Recompense(id, poids, LectureConfig.textes(rec, "commandes")));
                }
            }
        }
        recompenses = List.copyOf(lus);
        disparition = Math.max(0, s.getInt("disparition-secondes", 30));
        lueurEntite = s.getBoolean("lueur-entite", true);
        objet = ObjetConfig.lire(s.getConfigurationSection("objet"), objet);
    }

    public Set<String> zones() {
        return zones.keySet();
    }

    /** Lance une pluie (fil principal). */
    public boolean lancer(CommandSender auteur, String zoneId, int secondes, int nombre) {
        Zone zone = zones.get(zoneId.toLowerCase());
        if (zone == null) {
            plugin.messages().envoyer(auteur, "admin.drop-zone-inconnue", "{zone}", zoneId, "{zones}", String.join(", ", zones.keySet()));
            return false;
        }
        World monde = Bukkit.getWorld(zone.monde());
        if (monde == null) {
            plugin.messages().envoyer(auteur, "admin.drop-monde-absent", "{monde}", zone.monde());
            return false;
        }
        if (recompenses.isEmpty() || nombre <= 0 || secondes <= 0) {
            plugin.messages().envoyer(auteur, "admin.drop-usage");
            return false;
        }
        Pluie pluie = new Pluie(zone, monde, nombre, secondes * 20);
        pluie.tache = Bukkit.getScheduler().runTaskTimer(plugin, () -> tic(pluie), 1L, 1L);
        pluies.add(pluie);
        plugin.messages().envoyer(auteur, "admin.drop-lance", "{zone}", zone.id(), "{nombre}", nombre, "{secondes}", secondes);
        plugin.getLogger().info("Drop : " + nombre + " cadeaux en " + secondes + " s sur " + zone.id());
        return true;
    }

    private void tic(Pluie pluie) {
        pluie.ticks++;
        int attendu = (int) Math.min(pluie.nombre, Math.ceil((double) pluie.nombre * pluie.ticks / pluie.dureeTicks));
        int essais = 0;
        while (pluie.lances < attendu && essais < 20) {
            essais++;
            if (lacher(pluie)) {
                pluie.lances++;
            }
        }
        if (pluie.ticks >= pluie.dureeTicks + disparition * 20) {
            arreter(pluie);
        }
    }

    private boolean lacher(Pluie pluie) {
        Zone z = pluie.zone;
        ThreadLocalRandom aleatoire = ThreadLocalRandom.current();
        double x = Math.min(z.x1(), z.x2()) + aleatoire.nextDouble() * Math.max(0.01, Math.abs(z.x2() - z.x1()));
        double zz = Math.min(z.z1(), z.z2()) + aleatoire.nextDouble() * Math.max(0.01, Math.abs(z.z2() - z.z1()));
        double y = Math.max(z.y1(), z.y2());
        if (!pluie.monde.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(zz) >> 4)) {
            return false;
        }
        ItemStack stack = objet.creer(plugin.messages().objet("drop.objet.nom"), plugin.messages().lore("drop.objet.lore"));
        ItemMeta meta = stack.getItemMeta();
        ObjetsMarques.marquer(meta, "drop");
        meta.getPersistentDataContainer().set(cle, PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
        Item item = pluie.monde.dropItem(new Location(pluie.monde, x, y, zz), stack, entite -> {
            entite.setVelocity(new Vector(0, 0, 0));
            entite.setPickupDelay(10);
            entite.setCanMobPickup(false);
            entite.setUnlimitedLifetime(true);
            entite.setPersistent(false);
            entite.setGlowing(lueurEntite);
            entite.getPersistentDataContainer().set(cle, PersistentDataType.STRING, "1");
        });
        pluie.objets.add(item.getUniqueId());
        vivants.put(item.getUniqueId(), pluie);
        return true;
    }

    private void arreter(Pluie pluie) {
        if (pluie.tache != null) {
            pluie.tache.cancel();
        }
        for (UUID id : pluie.objets) {
            if (vivants.remove(id) != null) {
                Entity entite = Bukkit.getEntity(id);
                if (entite != null) {
                    entite.remove();
                }
            }
        }
        pluies.remove(pluie);
    }

    public void arreterTout() {
        for (Pluie pluie : new ArrayList<>(pluies)) {
            arreter(pluie);
        }
    }

    private boolean estDrop(Item item) {
        return item.getPersistentDataContainer().has(cle, PersistentDataType.STRING) || "drop".equals(ObjetsMarques.type(item.getItemStack()));
    }

    // ---- événements (fil du monde)

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRamasse(PlayerAttemptPickupItemEvent event) {
        Item item = event.getItem();
        if (!estDrop(item)) {
            return;
        }
        event.setCancelled(true);
        event.setFlyAtPlayer(false);
        if (vivants.remove(item.getUniqueId()) == null) {
            return;
        }
        Player joueur = event.getPlayer();
        plugin.sync(() -> {
            item.remove();
            recompenser(joueur);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRamasseEntite(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player) && estDrop(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntonnoir(InventoryPickupItemEvent event) {
        if (estDrop(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFusion(ItemMergeEvent event) {
        if (estDrop(event.getEntity()) || estDrop(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDisparition(ItemDespawnEvent event) {
        if (vivants.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void recompenser(Player joueur) {
        if (!joueur.isOnline()) {
            return;
        }
        int total = 0;
        for (Recompense r : recompenses) {
            total += r.poids();
        }
        if (total <= 0) {
            return;
        }
        int tirage = ThreadLocalRandom.current().nextInt(total);
        Recompense choisie = recompenses.get(recompenses.size() - 1);
        for (Recompense r : recompenses) {
            tirage -= r.poids();
            if (tirage < 0) {
                choisie = r;
                break;
            }
        }
        Console.executer(plugin.getLogger(), choisie.commandes(), "{joueur}", joueur.getName(), "{uuid}", joueur.getUniqueId().toString());
        String cleMessage = "drop.recompenses." + choisie.id();
        plugin.messages().envoyer(joueur, plugin.messages().existe(cleMessage) ? cleMessage : "drop.attrape");
        plugin.son("drop", joueur);
    }
}
