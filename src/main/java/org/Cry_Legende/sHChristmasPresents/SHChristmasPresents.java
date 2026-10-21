package org.Cry_Legende.sHChristmasPresents;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;

public class SHChristmasPresents extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final NamespacedKey GIFT_KEY = new NamespacedKey(this, "xmas_gift");
    private final NamespacedKey RARITY_KEY = new NamespacedKey(this, "xmas_rarity");
    public final NamespacedKey SNOWBALL_KEY = new NamespacedKey(this, "xmas_special_snowball");

    private final Map<UUID, GiftInstance> activeGifts = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<String, LoadedModel> cachedModels = new HashMap<>();

    private Location regionMin;
    private Location regionMax;
    private boolean debugMode = false;

    // --- NOUVEAUX CHAMPS ---
    private ClickerManager clickerManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        debugMode = getConfig().getBoolean("debug", false);

        // --- INIT CLICKER MANAGER ---
        this.clickerManager = new ClickerManager(this);
        // ENREGISTREMENT DES EVENTS CLICKER (CRUCIAL)
        getServer().getPluginManager().registerEvents(this.clickerManager, this);

        // PlaceholderAPI Hook
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ChristmasExpansion(this, clickerManager).register();
            getLogger().info("Hook PlaceholderAPI activé !");
        }

        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("shchristmaspresents");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        loadRegion();
        loadModels();
        cleanupGifts();
        startSpawningTask();
        getLogger().info("SHChristmasPresents activé ! " + cachedModels.size() + " modèles chargés.");
    }

    @Override
    public void onDisable() {
        cleanupGifts();
        if (clickerManager != null) {
            clickerManager.saveAll();
            clickerManager.cleanup();
        }
    }

    private void debug(String msg) {
        if (debugMode) getLogger().info("[DEBUG] " + msg);
    }

    // --- INSTANCE ---

    private class GiftInstance {
        Interaction interaction;
        List<Display> parts;
        LoadedModel model;

        public GiftInstance(Interaction interaction, List<Display> parts, LoadedModel model) {
            this.interaction = interaction;
            this.parts = parts;
            this.model = model;
        }

        public void stop() {
            for (Display d : parts) d.remove();
            if (interaction != null) interaction.remove();
        }
    }

    // --- MODEL DATA ---

    private enum DisplayType {ITEM, BLOCK}

    private static class LoadedModel {
        String name;
        List<ModelPart> parts;
        float width, height;
        Vector3f normalizationOffset;

        public LoadedModel(String name, List<ModelPart> parts) {
            this.name = name;
            this.parts = parts;
            calculateDimensionsAndNormalization();
        }

        private void calculateDimensionsAndNormalization() {
            if (parts.isEmpty()) {
                width = 1f;
                height = 1f;
                normalizationOffset = new Vector3f(0, 0, 0);
                return;
            }

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

            Vector3f pos = new Vector3f();
            Vector3f scale = new Vector3f();

            for (ModelPart part : parts) {
                part.matrix.getTranslation(pos);
                part.matrix.getScale(scale);

                float halfX = Math.abs(scale.x) / 2.0f;
                float halfY = Math.abs(scale.y) / 2.0f;
                float halfZ = Math.abs(scale.z) / 2.0f;

                minX = Math.min(minX, pos.x - halfX);
                maxX = Math.max(maxX, pos.x + halfX);
                minY = Math.min(minY, pos.y - halfY);
                maxY = Math.max(maxY, pos.y + halfY);
                minZ = Math.min(minZ, pos.z - halfZ);
                maxZ = Math.max(maxZ, pos.z + halfZ);
            }

            this.width = Math.max(0.6f, Math.max(maxX - minX, maxZ - minZ) + 0.1f);
            this.height = Math.max(0.6f, (maxY - minY) + 0.1f);

            this.normalizationOffset = new Vector3f(
                -(minX + maxX) / 2f,
                -minY,
                -(minZ + maxZ) / 2f
            );
        }
    }

    private static class ModelPart {
        DisplayType type;
        Material material;
        BlockData blockData;
        String textureBase64;
        Matrix4f matrix;

        public ModelPart(DisplayType type, Matrix4f matrix) {
            this.type = type;
            this.matrix = matrix;
        }
    }

    // --- CHARGEMENT ---

    private void loadModels() {
        cachedModels.clear();
        File modelsFolder = new File(getDataFolder(), "models");
        if (!modelsFolder.exists()) modelsFolder.mkdirs();

        File[] files = modelsFolder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile() && (file.getName().endsWith(".bdengine") || file.getName().endsWith(".txt"))) {
                try {
                    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                    content = content.replace("\n", "").replace("\r", "").replace(" ", "");
                    String simpleName = file.getName().replace(".bdengine", "").replace(".txt", "");

                    LoadedModel model = parseBDEngineString(simpleName, content);
                    if (model != null && !model.parts.isEmpty()) {
                        cachedModels.put(simpleName, model);
                        getLogger().info("Modèle chargé: " + simpleName + " (" + model.parts.size() + " parties)");
                    }
                } catch (Exception e) {
                    getLogger().severe("Erreur lecture " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private LoadedModel parseBDEngineString(String name, String base64Content) {
        try {
            while (base64Content.length() % 4 != 0) base64Content += "=";
            byte[] compressed = Base64.getDecoder().decode(base64Content);
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
                 BufferedReader br = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {
                StringBuilder jsonBuilder = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) jsonBuilder.append(line);
                JsonElement root = JsonParser.parseString(jsonBuilder.toString());
                List<ModelPart> parts = new ArrayList<>();
                if (root.isJsonArray()) {
                    for (JsonElement e : root.getAsJsonArray()) processNodeRecursively(e, new Matrix4f(), parts);
                } else if (root.isJsonObject()) {
                    processNodeRecursively(root, new Matrix4f(), parts);
                }
                return new LoadedModel(name, parts);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void processNodeRecursively(JsonElement element, Matrix4f parentMatrix, List<ModelPart> results) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) processNodeRecursively(child, parentMatrix, results);
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("settings") && obj.has("mainNBT") && obj.has("children")) {
                processNodeRecursively(obj.get("children"), parentMatrix, results);
                return;
            }
            Matrix4f currentLocalMatrix = new Matrix4f();
            float[] rawMatrix = null;
            if (obj.has("transforms")) rawMatrix = parseJsonFloatArray(obj.getAsJsonArray("transforms"));
            else if (obj.has("transformation")) rawMatrix = parseJsonFloatArray(obj.getAsJsonArray("transformation"));
            if (rawMatrix != null && rawMatrix.length == 16) {
                currentLocalMatrix.set(rawMatrix);
                currentLocalMatrix.transpose();
            }
            Matrix4f accumulatedMatrix = new Matrix4f(parentMatrix).mul(currentLocalMatrix);
            boolean isDisplay = obj.has("blockState") || obj.has("block_state") || (obj.has("isBlockDisplay") && obj.get("isBlockDisplay").getAsBoolean()) ||
                obj.has("tagHead") || obj.has("item") || (obj.has("isItemDisplay") && obj.get("isItemDisplay").getAsBoolean());
            if (isDisplay) {
                boolean isBlock = obj.has("blockState") || obj.has("block_state") || (obj.has("isBlockDisplay") && obj.get("isBlockDisplay").getAsBoolean());
                ModelPart part = new ModelPart(isBlock ? DisplayType.BLOCK : DisplayType.ITEM, new Matrix4f(accumulatedMatrix));
                if (isBlock) {
                    String blockStr = "stone";
                    if (obj.has("blockState")) {
                        JsonElement bs = obj.get("blockState");
                        if (bs.isJsonObject() && bs.getAsJsonObject().has("Name"))
                            blockStr = bs.getAsJsonObject().get("Name").getAsString();
                        else if (bs.isJsonPrimitive()) blockStr = bs.getAsString();
                    } else if (obj.has("block_state")) {
                        JsonElement bs = obj.get("block_state");
                        if (bs.isJsonObject() && bs.getAsJsonObject().has("Name"))
                            blockStr = bs.getAsJsonObject().get("Name").getAsString();
                        else if (bs.isJsonPrimitive()) blockStr = bs.getAsString();
                    } else if (obj.has("name")) blockStr = obj.get("name").getAsString();
                    part.blockData = parseBlockData(blockStr);
                } else {
                    String texture = extractTexture(obj);
                    if (texture != null) {
                        part.textureBase64 = texture;
                        part.material = Material.PLAYER_HEAD;
                    } else {
                        String itemStr = "stone";
                        if (obj.has("item")) {
                            JsonElement itemEl = obj.get("item");
                            if (itemEl.isJsonObject() && itemEl.getAsJsonObject().has("id"))
                                itemStr = itemEl.getAsJsonObject().get("id").getAsString();
                            else if (itemEl.isJsonPrimitive()) itemStr = itemEl.getAsString();
                        } else if (obj.has("name")) itemStr = obj.get("name").getAsString();
                        part.material = parseMaterialStrict(itemStr);
                    }
                }
                results.add(part);
            }
            if (obj.has("children")) processNodeRecursively(obj.get("children"), accumulatedMatrix, results);
            if (obj.has("Passengers")) processNodeRecursively(obj.get("Passengers"), accumulatedMatrix, results);
        }
    }

    private BlockData parseBlockData(String name) {
        if (name == null) return Bukkit.createBlockData(Material.STONE);
        String clean = name.toLowerCase();
        if (clean.startsWith("minecraft:")) clean = clean.substring(10);
        try {
            return Bukkit.createBlockData(clean);
        } catch (Exception e) {
            if (clean.contains("[")) try {
                return Bukkit.createBlockData(clean.substring(0, clean.indexOf("[")));
            } catch (Exception ex) {
            }
            return Bukkit.createBlockData(Material.STONE);
        }
    }

    private Material parseMaterialStrict(String name) {
        if (name == null) return Material.STONE;
        String c = name.toUpperCase().replace("MINECRAFT:", "").replace(" ", "_");
        if (c.contains("[")) c = c.substring(0, c.indexOf("["));
        Material m = Material.getMaterial(c);
        if (m == null) m = Material.matchMaterial(c);
        return m != null ? m : Material.STONE;
    }

    private float[] parseJsonFloatArray(JsonArray arr) {
        if (arr == null || arr.size() != 16) return null;
        float[] f = new float[16];
        for (int i = 0; i < 16; i++) f[i] = arr.get(i).getAsFloat();
        return f;
    }

    private String extractTexture(JsonObject obj) {
        try {
            if (obj.has("tagHead")) return obj.getAsJsonObject("tagHead").get("Value").getAsString();
            if (obj.has("item"))
                return obj.getAsJsonObject("item").getAsJsonObject("tag").getAsJsonObject("SkullOwner").getAsJsonObject("Properties").getAsJsonArray("textures").get(0).getAsJsonObject().get("Value").getAsString();
        } catch (Exception e) {
        }
        return null;
    }

    private void loadRegion() {
        FileConfiguration config = getConfig();
        if (config.contains("region.world")) {
            World world = Bukkit.getWorld(config.getString("region.world"));
            if (world != null) {
                double x1 = config.getDouble("region.x1");
                double y1 = config.getDouble("region.y1");
                double z1 = config.getDouble("region.z1");
                double x2 = config.getDouble("region.x2");
                double y2 = config.getDouble("region.y2");
                double z2 = config.getDouble("region.z2");
                regionMin = new Location(world, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
                regionMax = new Location(world, Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
            }
        }
    }

    private void cleanupGifts() {
        for (GiftInstance g : activeGifts.values()) g.stop();
        activeGifts.clear();
        if (regionMin != null && regionMin.getWorld() != null) {
            for (Entity entity : regionMin.getWorld().getEntities()) {
                if (entity.getPersistentDataContainer().has(GIFT_KEY, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    private void removeGift(Interaction interaction) {
        GiftInstance instance = activeGifts.remove(interaction.getUniqueId());
        if (instance != null) instance.stop();
        else interaction.remove();

        Location loc = interaction.getLocation();
        loc.getWorld().spawnParticle(Particle.DUST, loc.add(0, 0.5, 0), 20, 0.5, 0.5, 0.5, new Particle.DustOptions(Color.RED, 1.5f));
        loc.getWorld().playSound(loc, Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.5f);
    }

    // --- SPAWN ---

    private void startSpawningTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (regionMin == null || regionMax == null) return;
                activeGifts.entrySet().removeIf(entry -> {
                    Entity ent = Bukkit.getEntity(entry.getKey());
                    if (ent == null || !ent.isValid()) {
                        entry.getValue().stop();
                        return true;
                    }
                    return false;
                });

                int maxGifts = getConfig().getInt("settings.max_concurrent_gifts", 10);
                if (activeGifts.size() >= maxGifts) return;

                spawnWeightedGift();
            }
        }.runTaskTimer(this, 100L, getConfig().getLong("settings.respawn_time_ticks", 100L));
    }

    private void spawnWeightedGift() {
        if (cachedModels.isEmpty()) return;

        ConfigurationSection rewardsSec = getConfig().getConfigurationSection("rewards");
        if (rewardsSec == null) return;

        double totalWeight = 0;
        Map<String, Double> weights = new HashMap<>();
        for (String key : rewardsSec.getKeys(false)) {
            double chance = rewardsSec.getDouble(key + ".chance", 0);
            if (chance > 0) {
                weights.put(key, chance);
                totalWeight += chance;
            }
        }

        if (totalWeight <= 0) return;
        double randomVal = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double currentWeight = 0;
        String selectedRarity = null;

        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            currentWeight += entry.getValue();
            if (randomVal <= currentWeight) {
                selectedRarity = entry.getKey();
                break;
            }
        }
        if (selectedRarity == null) return;

        List<String> allowedModels = rewardsSec.getStringList(selectedRarity + ".models");
        if (allowedModels.isEmpty()) return;

        String randomModelName = allowedModels.get(ThreadLocalRandom.current().nextInt(allowedModels.size()));
        LoadedModel model = cachedModels.get(randomModelName);

        if (model == null) {
            debug("Modèle introuvable pour " + selectedRarity + ": " + randomModelName);
            return;
        }

        spawnGiftInWorld(model, selectedRarity);
    }

    private void spawnGiftInWorld(LoadedModel model, String rarityKey) {
        World world = regionMin.getWorld();
        double x = ThreadLocalRandom.current().nextDouble(regionMin.getX(), regionMax.getX());
        double z = ThreadLocalRandom.current().nextDouble(regionMin.getZ(), regionMax.getZ());

        if (!world.getChunkAt((int) x >> 4, (int) z >> 4).isLoaded())
            world.getChunkAt((int) x >> 4, (int) z >> 4).load();
        Block block = world.getHighestBlockAt((int) x, (int) z);

        double configOffset = getConfig().getDouble("settings.vertical_offset", 0.0);
        Location spawnLoc = block.getLocation().add(0.5, 1.0 + configOffset, 0.5);

        if (spawnLoc.getY() < regionMin.getY() || spawnLoc.getY() > regionMax.getY()) return;

        // CHECK COLLISION (Rayon de 1.5 blocs)
        for (GiftInstance active : activeGifts.values()) {
            if (active.interaction.isValid() && active.interaction.getLocation().distanceSquared(spawnLoc) < 2.25) {
                // debug("Spawn annulé : Trop proche d'un autre cadeau.");
                return;
            }
        }

        float randomYaw = ThreadLocalRandom.current().nextFloat() * 360f;
        spawnLoc.setYaw(randomYaw);
        Matrix4f globalRotation = new Matrix4f().rotateY((float) Math.toRadians(-randomYaw));

        Location interactionLoc = spawnLoc.clone();

        Interaction interaction = world.spawn(interactionLoc, Interaction.class, entity -> {
            entity.setInteractionWidth(model.width);
            entity.setInteractionHeight(model.height);
            entity.setResponsive(true);
            entity.getPersistentDataContainer().set(GIFT_KEY, PersistentDataType.BYTE, (byte) 1);
            entity.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarityKey);
        });

        // SON D'APPARITION (POP)
        world.playSound(interactionLoc, Sound.ENTITY_CHICKEN_EGG, 1.0f, 0.5f);
        world.spawnParticle(Particle.CLOUD, interactionLoc.clone().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.05);

        debug("Spawn [" + rarityKey + "] " + model.name + " @ " + interactionLoc.toVector());

        List<Display> partsEntities = new ArrayList<>();

        for (ModelPart part : model.parts) {
            Matrix4f adjustedMatrix = new Matrix4f(part.matrix);
            adjustedMatrix.translateLocal(model.normalizationOffset);

            Matrix4f finalMatrix = new Matrix4f(globalRotation).mul(adjustedMatrix);

            Class<? extends Entity> entityClass = (part.type == DisplayType.BLOCK) ? BlockDisplay.class : ItemDisplay.class;

            Display display = (Display) world.spawn(spawnLoc, entityClass, entity -> {
                entity.setRotation(spawnLoc.getYaw(), 0);
                entity.getPersistentDataContainer().set(GIFT_KEY, PersistentDataType.BYTE, (byte) 1);

                if (entity instanceof ItemDisplay itemDisplay) {
                    ItemStack item;
                    if (part.textureBase64 != null) {
                        item = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta meta = (SkullMeta) item.getItemMeta();
                        PlayerProfile profile = (PlayerProfile) Bukkit.createProfile(UUID.randomUUID(), "GP");
                        profile.setProperty(new ProfileProperty("textures", part.textureBase64));
                        meta.setPlayerProfile(profile);
                        item.setItemMeta(meta);
                    } else {
                        item = new ItemStack(part.material != null ? part.material : Material.STONE);
                    }
                    itemDisplay.setItemStack(item);
                } else if (entity instanceof BlockDisplay blockDisplay) {
                    blockDisplay.setBlock(part.blockData != null ? part.blockData : Bukkit.createBlockData(Material.STONE));
                }

                if (entity instanceof Display d) d.setTransformationMatrix(finalMatrix);
            });
            partsEntities.add(display);
        }

        activeGifts.put(interaction.getUniqueId(), new GiftInstance(interaction, partsEntities, model));
    }

    // --- EVENTS ---

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (getConfig().getInt("settings.cooldown_seconds", 5) * 1000L));
    }

    private boolean isOnCooldown(Player player) {
        if (!cooldowns.containsKey(player.getUniqueId())) return false;
        if (System.currentTimeMillis() > cooldowns.get(player.getUniqueId())) {
            cooldowns.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Interaction interaction
            && interaction.getPersistentDataContainer().has(GIFT_KEY, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Snowball snowball && snowball.getShooter() instanceof Player player) {
                if (snowball.hasMetadata("xmas_snowball")) {
                    debug("Hit (Damage)");
                    handleHit(player, interaction);
                    snowball.remove();
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !snowball.hasMetadata("xmas_snowball")) return;

        // 1. Check Direct Hit (Si la boule touche l'interaction)
        if (event.getHitEntity() instanceof Interaction i && i.getPersistentDataContainer().has(GIFT_KEY, PersistentDataType.BYTE)) {
            debug("Hit (Direct)");
            handleHit((Player) snowball.getShooter(), i);
            return;
        }

        // 2. Check Proximité (Si la boule touche le sol ou passe à travers)
        Location loc;
        if (event.getHitBlock() != null) {
            loc = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        } else {
            loc = event.getEntity().getLocation();
        }

        // Recherche de l'interaction la plus proche dans un rayon
        Interaction closestGift = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (Entity e : loc.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5)) {
            if (e instanceof Interaction i && i.getPersistentDataContainer().has(GIFT_KEY, PersistentDataType.BYTE)) {
                double distSq = e.getLocation().distanceSquared(loc);
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                    closestGift = i;
                }
            }
        }

        if (closestGift != null) {
            debug("Hit (Proximité, dist=" + Math.sqrt(minDistanceSq) + ")");
            handleHit((Player) snowball.getShooter(), closestGift);
        } else {
            // Rate : On rend la boule
            if (snowball.getShooter() instanceof Player p && p.getGameMode() != GameMode.CREATIVE) {
                ItemStack s = getSpecialSnowball();
                s.setAmount(1);
                if (!p.getInventory().addItem(s).isEmpty()) p.getWorld().dropItem(p.getLocation(), s);
            }
        }
    }

    private void handleHit(Player player, Interaction interaction) {
        if (isOnCooldown(player)) {
            long remaining = (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            player.sendMessage(MiniMessage.miniMessage().deserialize(getConfig().getString("messages.cooldown_message", "Wait %time%s").replace("%time%", String.valueOf(remaining))));
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack s = getSpecialSnowball();
                s.setAmount(1);
                if (!player.getInventory().addItem(s).isEmpty()) player.getWorld().dropItem(player.getLocation(), s);
            }
            return;
        }

        String rarity = interaction.getPersistentDataContainer().get(RARITY_KEY, PersistentDataType.STRING);
        if (rarity == null) rarity = "common";

        setCooldown(player);
        giveReward(player, rarity);
        removeGift(interaction);
    }

    private void giveReward(Player player, String rarity) {
        List<String> commands = getConfig().getStringList("rewards." + rarity + ".commands");
        for (String cmd : commands) {
            String f = cmd.replace("%player%", player.getName());
            if (f.startsWith("msg:")) player.sendMessage(MiniMessage.miniMessage().deserialize(f.substring(4)));
            else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), f);
        }
    }

    // --- COMMANDES ---

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shchristmaspresents.admin")) return true;
        if (args.length == 0) return false;

        if (args[0].equalsIgnoreCase("setregion") && sender instanceof Player player) {
            Location loc = player.getLocation();
            FileConfiguration config = getConfig();
            String key = (args.length > 1 && args[1].equalsIgnoreCase("2")) ? "2" : "1";
            config.set("region.world", loc.getWorld().getName());
            config.set("region.x" + key, loc.getX());
            config.set("region.y" + key, loc.getY());
            config.set("region.z" + key, loc.getZ());
            player.sendMessage("§aPosition " + key + " définie.");
            saveConfig();
            loadRegion();
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            Player target = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player) sender : null);
            if (target != null) {
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (Exception e) {
                    }
                }
                ItemStack item = getSpecialSnowball();
                item.setAmount(amount);
                target.getInventory().addItem(item);
                target.sendMessage(MiniMessage.miniMessage().deserialize(getConfig().getString("messages.snowball_received", "<green>Reçu!")));
            }
            return true;
        }

        // --- COMMANDES CLICKER SNOWMAN ---
        if (args[0].equalsIgnoreCase("createclicker") && sender instanceof Player player) {
            clickerManager.createSnowman(player.getLocation());
            player.sendMessage("§aBonhomme de neige clicker créé !");
            return true;
        }

        if (args[0].equalsIgnoreCase("removeclicker") && sender instanceof Player player) {
            if (clickerManager.removeNearestSnowman(player.getLocation())) {
                player.sendMessage("§aBonhomme de neige supprimé.");
            } else {
                player.sendMessage("§cAucun bonhomme de neige clicker trouvé à proximité (3 blocs).");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("listclickers")) {
            sender.sendMessage("§eListe des Clicker Snowmen (" + clickerManager.getSnowmanCount() + "):");
            clickerManager.getSnowmanLocations().forEach(loc ->
                sender.sendMessage(" - " + loc.getWorld().getName() + " " + (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ())
            );
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            debugMode = getConfig().getBoolean("debug", false);
            loadRegion();
            loadModels();
            cleanupGifts();
            clickerManager.reload(); // Reload clicker config
            sender.sendMessage("§aReload OK.");
            return true;
        }
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shchristmaspresents.admin")) return Collections.emptyList();
        if (args.length == 1)
            return Arrays.asList("give", "setregion", "reload", "createclicker", "removeclicker", "listclickers");
        if (args.length == 2 && args[0].equalsIgnoreCase("setregion")) return Arrays.asList("1", "2");
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return Arrays.asList("1", "16", "64");
        return null;
    }

    public ItemStack getSpecialSnowball() {
        FileConfiguration config = getConfig();
        ItemStack snow = new ItemStack(Material.SNOWBALL);
        var meta = snow.getItemMeta();
        String name = "<!italic>" + config.getString("snowball.name", "<red>Boule</red>");
        meta.displayName(MiniMessage.miniMessage().deserialize(name));
        List<Component> lore = new ArrayList<>();
        if (config.contains("snowball.lore")) {
            for (String line : config.getStringList("snowball.lore")) {
                lore.add(MiniMessage.miniMessage().deserialize("<!italic>" + line));
            }
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(SNOWBALL_KEY, PersistentDataType.BYTE, (byte) 1);
        snow.setItemMeta(meta);
        return snow;
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball snowball && snowball.getShooter() instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(SNOWBALL_KEY, PersistentDataType.BYTE))
                item = player.getInventory().getItemInOffHand();
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(SNOWBALL_KEY, PersistentDataType.BYTE))
                snowball.setMetadata("xmas_snowball", new FixedMetadataValue(this, true));
        }
    }

    @EventHandler
    public void onRightClickInteraction(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getRightClicked() instanceof Interaction interaction && interaction.getPersistentDataContainer().has(GIFT_KEY, PersistentDataType.BYTE)) {
            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(SNOWBALL_KEY, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                if (event.getPlayer().getGameMode() != GameMode.CREATIVE) item.subtract(1);
                handleHit(event.getPlayer(), interaction);
            }
        }
    }
}
