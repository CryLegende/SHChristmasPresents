package org.Cry_Legende.sHChristmasPresents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClickerManager implements Listener {

    private final SHChristmasPresents plugin;
    private final NamespacedKey CLICKER_KEY;
    // Concurrente : PlaceholderAPI peut interroger les données depuis un thread asynchrone
    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();
    private final Set<UUID> snowmanIds = new HashSet<>();
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private Sound clickSound;

    // Écriture de playerdata.yml hors du thread principal : seul le dernier contenu figé est écrit
    private final Object saveLock = new Object();
    private String pendingPlayerData;

    private File snowmenFile;
    private FileConfiguration snowmenConfig;
    private File playerDataFile;
    private FileConfiguration playerDataConfig;

    public ClickerManager(SHChristmasPresents plugin) {
        this.plugin = plugin;
        this.CLICKER_KEY = new NamespacedKey(plugin, "clicker_snowman");
        loadFiles();
        loadSnowmen();
        clickSound = sound(plugin.getConfig().getString("clicker.click_sound", "UI_BUTTON_CLICK"));
    }

    // --- DATA MANAGEMENT ---

    private void loadFiles() {
        flushPlayerData(); // une écriture en attente doit être sur le disque avant de relire le fichier
        snowmenFile = new File(plugin.getDataFolder(), "snowmen.yml");
        if (!snowmenFile.exists()) {
            try {
                snowmenFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        snowmenConfig = YamlConfiguration.loadConfiguration(snowmenFile);

        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    public void reload() {
        loadFiles();
        loadSnowmen();
        clickSound = sound(plugin.getConfig().getString("clicker.click_sound", "UI_BUTTON_CLICK"));
    }

    public void saveAll() {
        for (Map.Entry<UUID, PlayerData> entry : playerDataCache.entrySet()) {
            String path = entry.getKey().toString();
            playerDataConfig.set(path + ".clicks", entry.getValue().clicks);
            playerDataConfig.set(path + ".level", entry.getValue().level);
        }
        synchronized (saveLock) {
            pendingPlayerData = playerDataConfig.saveToString();
        }
        flushPlayerData();
        try {
            snowmenConfig.save(snowmenFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save clicker data!");
        }
    }

    private void savePlayerDataAsync() {
        String content = playerDataConfig.saveToString();
        synchronized (saveLock) {
            pendingPlayerData = content;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushPlayerData);
    }

    private void flushPlayerData() {
        synchronized (saveLock) {
            if (pendingPlayerData == null || playerDataFile == null) return;
            try {
                Files.writeString(playerDataFile.toPath(), pendingPlayerData, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save clicker data!");
            }
            pendingPlayerData = null;
        }
    }

    public void cleanup() {
    }

    // --- PLAYER DATA ---

    public static class PlayerData {
        int clicks = 0;
        int level = 0;
        Location lastMoveLocation;
        int staticClickCount = 0;
    }

    public PlayerData getData(UUID uuid) {
        // lastMoveLocation reste nul jusqu'au premier clic ou à la connexion (onJoin) : traité comme "a bougé"
        return playerDataCache.computeIfAbsent(uuid, id -> {
            PlayerData data = new PlayerData();
            String path = id.toString();
            data.clicks = playerDataConfig.getInt(path + ".clicks", 0);
            data.level = playerDataConfig.getInt(path + ".level", 0);
            return data;
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        getData(e.getPlayer().getUniqueId()).lastMoveLocation = e.getPlayer().getLocation();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (playerDataCache.containsKey(uuid)) {
            PlayerData data = playerDataCache.get(uuid);
            String path = uuid.toString();
            playerDataConfig.set(path + ".clicks", data.clicks);
            playerDataConfig.set(path + ".level", data.level);
            savePlayerDataAsync();
            playerDataCache.remove(uuid);
        }
        clickCooldowns.remove(uuid);
    }

    // --- NPC MANAGEMENT ---

    private void loadSnowmen() {
        snowmanIds.clear();
        List<String> list = snowmenConfig.getStringList("ids");
        for (String s : list) {
            try {
                snowmanIds.add(UUID.fromString(s));
            } catch (Exception ignored) {
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID id : snowmanIds) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.getLocation().getChunk().setForceLoaded(true);
            }
        });
    }

    public void createSnowman(Location loc) {
        Snowman sm = (Snowman) loc.getWorld().spawnEntity(loc, EntityType.SNOW_GOLEM);
        sm.setAI(false);
        sm.setCollidable(false);
        sm.setRemoveWhenFarAway(false);
        sm.setPersistent(true);
        if (sm.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
            sm.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(2048);
            sm.setHealth(2048);
        }
        sm.getPersistentDataContainer().set(CLICKER_KEY, PersistentDataType.BYTE, (byte) 1);
        loc.getChunk().setForceLoaded(true);
        snowmanIds.add(sm.getUniqueId());
        saveSnowmanIds();
    }

    public boolean removeNearestSnowman(Location loc) {
        double minDst = 9;
        Entity target = null;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 3, 3, 3)) {
            if (snowmanIds.contains(e.getUniqueId())) {
                double d = e.getLocation().distanceSquared(loc);
                if (d < minDst) {
                    minDst = d;
                    target = e;
                }
            }
        }
        if (target != null) {
            target.getLocation().getChunk().setForceLoaded(false);
            target.remove();
            snowmanIds.remove(target.getUniqueId());
            saveSnowmanIds();
            return true;
        }
        return false;
    }

    private void saveSnowmanIds() {
        List<String> list = new ArrayList<>();
        for (UUID id : snowmanIds) list.add(id.toString());
        snowmenConfig.set("ids", list);
        try {
            snowmenConfig.save(snowmenFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getSnowmanCount() {
        return snowmanIds.size();
    }

    public List<Location> getSnowmanLocations() {
        List<Location> locs = new ArrayList<>();
        for (UUID id : snowmanIds) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) locs.add(e.getLocation());
        }
        return locs;
    }

    // --- GAMEPLAY LOGIC (CLIC GAUCHE & DROIT) ---

    // 1. Gestion du Clic Gauche (Dégâts)
    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!snowmanIds.contains(event.getEntity().getUniqueId())) return;
        event.setCancelled(true); // Annule les dégâts

        if (event.getDamager() instanceof Player player) {
            processClick(player, event.getEntity());
        }
    }

    // 2. Gestion du Clic Droit (Interaction)
    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // Ignore la main gauche pour éviter double proc
        if (!snowmanIds.contains(event.getRightClicked().getUniqueId())) return;
        event.setCancelled(true); // Annule l'ouverture d'inventaire/trade éventuel

        processClick(event.getPlayer(), event.getRightClicked());
    }

    /**
     * Logique centrale pour traiter un clic (Gauche ou Droit)
     */
    private void processClick(Player player, Entity snowman) {
        PlayerData data = getData(player.getUniqueId());

        // --- ANTI AUTO CLICK AFK ---
        double moveThreshold = plugin.getConfig().getDouble("clicker.anti_afk.min_move_distance", 1.0);
        int maxStaticClicks = plugin.getConfig().getInt("clicker.anti_afk.max_static_clicks", 100);

        Location currentLoc = player.getLocation();
        boolean hasMovedEnough = false;

        // 1. Vérification si c'est la première fois ou si le monde a changé (Fix du crash "distanceSquared")
        if (data.lastMoveLocation == null || !Objects.equals(data.lastMoveLocation.getWorld(), currentLoc.getWorld())) {
            hasMovedEnough = true;
        } else {
            // 2. Calcul de la distance horizontale uniquement (Ignorer Y pour bypass le saut)
            double dx = currentLoc.getX() - data.lastMoveLocation.getX();
            double dz = currentLoc.getZ() - data.lastMoveLocation.getZ();

            // Calcul distance au carré (Pythagore 2D) : a² + b² = c²
            if ((dx * dx) + (dz * dz) > (moveThreshold * moveThreshold)) {
                hasMovedEnough = true;
            }
        }

        if (hasMovedEnough) {
            data.staticClickCount = 0;
            data.lastMoveLocation = currentLoc;
        } else {
            data.staticClickCount++;
        }

        if (data.staticClickCount >= maxStaticClicks) {
            // Son doux (Villageois NO)
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

            Component title = Component.empty();
            Component subtitle = MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("clicker.anti_afk.warning_message", "<red>Bougez un peu !"));
            player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ofMillis(500))));
            return;
        }

        // --- ANTI CPS (PARTAGÉ GAUCHE/DROIT) ---
        long now = System.currentTimeMillis();
        long last = clickCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long minInterval = 1000L / Math.max(1, plugin.getConfig().getInt("clicker.max_cps", 10));

        if (now - last < minInterval) return;
        clickCooldowns.put(player.getUniqueId(), now);

        // --- VALIDATION DU CLIC ---
        data.clicks++;

        // Effets visuels et sonores
        playClickEffects(player, snowman);

        // Level Up Logic
        checkInfiniteLevelUp(player, data);

        // Calcul des variables pour l'affichage
        int nextLevel = data.level + 1;
        int requiredClicks = calculateRequiredClicks(nextLevel);
        int nextRewardAmount = calculateRewardAmount(nextLevel);

        String clickStr = String.valueOf(data.clicks);
        String reqStr = String.valueOf(requiredClicks);
        String lvlStr = String.valueOf(data.level);
        String nextRewStr = String.valueOf(nextRewardAmount);

        // Envoi Action Bar
        player.sendActionBar(MiniMessage.miniMessage().deserialize(
            plugin.getConfig().getString("clicker.action_bar", "<gold>%clicks% / %required%")
                .replace("%clicks%", clickStr)
                .replace("%required%", reqStr)
                .replace("%level%", lvlStr)
                .replace("%next_reward%", nextRewStr)
        ));

        // Envoi Subtitle (si configuré)
        String subFormat = plugin.getConfig().getString("clicker.subtitle_on_click", "");
        if (subFormat != null && !subFormat.isEmpty()) {
            Component sub = MiniMessage.miniMessage().deserialize(subFormat
                .replace("%clicks%", clickStr)
                .replace("%required%", reqStr)
                .replace("%level%", lvlStr)
                .replace("%next_reward%", nextRewStr));
            // Title vide pour n'afficher que le subtitle
            player.showTitle(Title.title(Component.empty(), sub, Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(250))));
        }
    }

    private void playClickEffects(Player p, Entity snowman) {
        // Son de clic (résolu au chargement)
        if (clickSound != null) p.playSound(p.getLocation(), clickSound, 0.5f, 1.5f);

        // Particules sur le NPC
        Location particleLoc = snowman.getLocation().add(0, 1.5, 0); // Au niveau de la tête
        // Particules douces (Flocons)
        snowman.getWorld().spawnParticle(Particle.SNOWFLAKE, particleLoc, 7, 0.3, 0.4, 0.3, 0.05);
        // Particules d'impact (Boule de neige qui casse)
        snowman.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, particleLoc, 3, 0.2, 0.2, 0.2, 0.1);
    }

    /** Son lu en config : ancien nom (ENTITY_PLAYER_LEVELUP) ou clé (minecraft:entity.player.levelup). */
    static Sound sound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.SOUND_EVENT.get(key);
        }
        try {
            return (Sound) Sound.class.getField(s.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    private int calculateRequiredClicks(int targetLevel) {
        double base = plugin.getConfig().getDouble("clicker.algorithm.base_clicks", 50);
        double multiplier = plugin.getConfig().getDouble("clicker.algorithm.multiplier", 1.5);
        return (int) Math.max(1, (base * Math.pow(multiplier, targetLevel - 1)));
    }

    private int calculateRewardAmount(int level) {
        int baseReward = plugin.getConfig().getInt("clicker.algorithm.base_reward", 1);
        int rewardPerLvl = plugin.getConfig().getInt("clicker.algorithm.reward_increase_per_level", 1);
        int amount = baseReward + (level * rewardPerLvl);
        return Math.min(amount, 192); // Cap à 3 stacks
    }

    private void checkInfiniteLevelUp(Player p, PlayerData data) {
        int nextLevel = data.level + 1;
        int required = calculateRequiredClicks(nextLevel);

        if (data.clicks >= required) {
            data.level = nextLevel;

            // On calcule la récompense pour le NOUVEAU niveau atteint
            int amount = calculateRewardAmount(data.level);

            org.bukkit.inventory.ItemStack balls = plugin.getSpecialSnowball();
            balls.setAmount(amount);

            Map<Integer, org.bukkit.inventory.ItemStack> left = p.getInventory().addItem(balls);
            if (!left.isEmpty()) {
                left.values().forEach(item -> p.getWorld().dropItem(p.getLocation(), item));
            }

            p.sendMessage(MiniMessage.miniMessage().deserialize(
                plugin.getConfig().getString("clicker.levelup_message", "<green>Level Up! %level%").replace("%level%", String.valueOf(nextLevel))
            ));

            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }
}
