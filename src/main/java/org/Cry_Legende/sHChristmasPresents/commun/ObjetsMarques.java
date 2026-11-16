package org.Cry_Legende.sHChristmasPresents.commun;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareInventoryResultEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Objets du plugin (cadeau d'un joueur, petit cadeau, cadeau du ciel) : marqués par la clé shchristmaspresents:objet.
 * Ils ne servent à aucune fabrication et ne se posent pas.
 */
public final class ObjetsMarques implements Listener {

    private static NamespacedKey cle;

    public ObjetsMarques(Plugin plugin) {
        cle = new NamespacedKey(plugin, "objet");
    }

    public static NamespacedKey cle() {
        return cle;
    }

    /** Type marqué sur l'objet (cadeau, petit-cadeau, drop), null sinon. */
    public static String type(ItemStack objet) {
        if (objet == null || cle == null || !objet.hasItemMeta()) {
            return null;
        }
        return objet.getItemMeta().getPersistentDataContainer().get(cle, PersistentDataType.STRING);
    }

    public static void marquer(ItemMeta meta, String type) {
        meta.getPersistentDataContainer().set(cle, PersistentDataType.STRING, type);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack objet : event.getInventory().getMatrix()) {
            if (type(objet) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onResultat(PrepareInventoryResultEvent event) {
        for (ItemStack objet : event.getInventory().getContents()) {
            if (type(objet) != null) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPose(BlockPlaceEvent event) {
        if (type(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }
}
