package org.Cry_Legende.sHChristmasPresents.commun;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.function.Consumer;

/**
 * Clics dans les menus du plugin : toujours annulés tout de suite (fil du monde du joueur), l'action est rejouée sur
 * le fil principal.
 */
public final class MenusListener implements Listener {

    private final Consumer<Runnable> filPrincipal;

    public MenusListener(Consumer<Runnable> filPrincipal) {
        this.filPrincipal = filPrincipal;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player joueur) || !joueur.equals(menu.joueur())) {
            return;
        }
        int case_ = event.getSlot();
        var type = event.getClick();
        filPrincipal.accept(() -> {
            if (joueur.getOpenInventory().getTopInventory().getHolder(false) == menu) {
                menu.clic(case_, type);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
