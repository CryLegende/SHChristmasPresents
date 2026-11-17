package org.Cry_Legende.sHChristmasPresents.commun;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Menu natif d'un joueur. Les clics sont annulés par {@link MenusListener} et rejoués sur le fil principal.
 */
public abstract class Menu implements InventoryHolder {

    protected final Player joueur;
    private final Inventory inventaire;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    protected Menu(Player joueur, int lignes, Component titre) {
        this.joueur = joueur;
        this.inventaire = Bukkit.createInventory(this, Math.max(1, Math.min(6, lignes)) * 9, titre);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventaire;
    }

    /** Remplit le menu (appelé à l'ouverture et après chaque action). */
    protected abstract void dessiner();

    public void ouvrir() {
        rafraichir();
        joueur.openInventory(inventaire);
    }

    public void rafraichir() {
        inventaire.clear();
        actions.clear();
        dessiner();
    }

    protected void poser(int case_, ItemStack objet, Consumer<ClickType> action) {
        if (case_ < 0 || case_ >= inventaire.getSize()) {
            return;
        }
        inventaire.setItem(case_, objet);
        if (action != null) {
            actions.put(case_, action);
        } else {
            actions.remove(case_);
        }
    }

    /** Vitres de couleur aux cases de l'accent, sur les cases encore libres (avant le cadre). */
    protected void accent(Accent accent) {
        if (accent == null || accent.materiau().isAir()) {
            return;
        }
        ItemStack verre = ObjetConfig.simple(accent.materiau()).creer(Component.empty(), List.of());
        for (int c : accent.cases()) {
            if (c >= 0 && c < inventaire.getSize() && inventaire.getItem(c) == null) {
                inventaire.setItem(c, verre);
            }
        }
    }

    /** Cadre noir sur les cases libres du bord. */
    protected void cadre(Material vitre) {
        if (vitre == null || vitre == Material.AIR) {
            return;
        }
        ItemStack verre = ObjetConfig.simple(vitre).creer(Component.empty(), List.of());
        int taille = inventaire.getSize();
        int lignes = taille / 9;
        for (int i = 0; i < taille; i++) {
            int ligne = i / 9;
            int colonne = i % 9;
            boolean bord = ligne == 0 || ligne == lignes - 1 || colonne == 0 || colonne == 8;
            if (bord && inventaire.getItem(i) == null) {
                inventaire.setItem(i, verre);
            }
        }
    }

    void clic(int case_, ClickType type) {
        Consumer<ClickType> action = actions.get(case_);
        if (action != null) {
            action.accept(type);
        }
    }

    public Player joueur() {
        return joueur;
    }
}
