package org.Cry_Legende.sHChristmasPresents.commun;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Apparence d'un objet lue en config : matériau, modèle (item_model, ex. minecraft:red_shulker_box), lueur
 * d'enchantement, texture de tête (base64) pour une PLAYER_HEAD.
 */
public record ObjetConfig(Material materiau, NamespacedKey modele, boolean lueur, String tete) {

    public static ObjetConfig simple(Material materiau) {
        return new ObjetConfig(materiau, null, false, null);
    }

    /** Lit une section (materiau, modele, lueur, tete) ; ce qui manque vient de l'objet par défaut. */
    public static ObjetConfig lire(ConfigurationSection section, ObjetConfig defaut) {
        if (section == null) {
            return defaut;
        }
        Material materiau = defaut.materiau();
        String nom = section.getString("materiau");
        if (nom != null && !nom.isBlank()) {
            Material lu = Material.matchMaterial(nom.trim().toUpperCase(Locale.ROOT));
            if (lu != null && lu.isItem()) {
                materiau = lu;
            }
        }
        NamespacedKey modele = defaut.modele();
        if (section.contains("modele")) {
            String texte = section.getString("modele", "");
            modele = texte == null || texte.isBlank() ? null : NamespacedKey.fromString(texte.trim().toLowerCase(Locale.ROOT));
        }
        boolean lueur = section.getBoolean("lueur", defaut.lueur());
        String tete = section.getString("tete", defaut.tete());
        return new ObjetConfig(materiau, modele, lueur, tete == null || tete.isBlank() ? null : tete.trim());
    }

    public ItemStack creer(Component nom, List<Component> lore) {
        ItemStack item = new ItemStack(materiau);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (nom != null) {
            meta.displayName(nom);
        }
        if (lore != null) {
            meta.lore(lore);
        }
        if (modele != null) {
            meta.setItemModel(modele);
        }
        if (lueur) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.values());
        if (tete != null && meta instanceof SkullMeta crane) {
            PlayerProfile profil = Bukkit.createProfile(UUID.nameUUIDFromBytes(tete.getBytes()), "SHNoel");
            profil.setProperty(new ProfileProperty("textures", tete));
            crane.setPlayerProfile(profil);
        }
        item.setItemMeta(meta);
        return item;
    }
}
