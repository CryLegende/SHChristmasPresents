package org.Cry_Legende.sHChristmasPresents.commun;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Vitres de couleur posées à des cases fixes d'un menu (rouge de Noël là où les menus SkyHand mettent du gris), avant le
 * cadre noir. Config d'un menu : accent (matériau, absent = aucune) et cases-accent (liste de cases).
 */
public record Accent(Material materiau, List<Integer> cases) {

    public static final Accent AUCUN = new Accent(Material.AIR, List.of());

    public static Accent lire(ConfigurationSection menu) {
        if (menu == null) {
            return AUCUN;
        }
        Material m = Material.matchMaterial(menu.getString("accent", ""));
        if (m == null || m.isAir()) {
            return AUCUN;
        }
        return new Accent(m, List.copyOf(menu.getIntegerList("cases-accent")));
    }
}
