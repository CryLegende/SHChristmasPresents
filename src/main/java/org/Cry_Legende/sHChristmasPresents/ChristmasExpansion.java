package org.Cry_Legende.sHChristmasPresents;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class ChristmasExpansion extends PlaceholderExpansion {

    private final SHChristmasPresents plugin;
    private final ClickerManager manager;

    public ChristmasExpansion(SHChristmasPresents plugin, ClickerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shchristmaspresents";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cry_Legende";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.isOnline()) return "";

        ClickerManager.PlayerData data = manager.getData(player.getUniqueId());

        if (params.equalsIgnoreCase("clicks")) {
            return String.valueOf(data.clicks);
        }

        if (params.equalsIgnoreCase("level")) {
            return String.valueOf(data.level);
        }

        return null;
    }
}