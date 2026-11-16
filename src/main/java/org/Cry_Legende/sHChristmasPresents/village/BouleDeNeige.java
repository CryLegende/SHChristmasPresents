package org.Cry_Legende.sHChristmasPresents.village;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Boule de neige magique : ouvre les cadeaux du village. Nom et description : messages.yml boule.nom et boule.lore (MiniMessage). */
public final class BouleDeNeige implements Listener {

    private final SHChristmasPresents plugin;
    private final NamespacedKey cle;

    public BouleDeNeige(SHChristmasPresents plugin) {
        this.plugin = plugin;
        this.cle = new NamespacedKey(plugin, "xmas_special_snowball");
    }

    public NamespacedKey cle() {
        return cle;
    }

    public ItemStack creer(int nombre) {
        ItemStack boule = new ItemStack(Material.SNOWBALL, Math.max(1, Math.min(64, nombre)));
        ItemMeta meta = boule.getItemMeta();
        String nom = plugin.messages().brut("boule.nom");
        meta.displayName(MiniMessage.miniMessage().deserialize("<!italic>" + nom));
        List<Component> lore = new ArrayList<>();
        for (String ligne : plugin.messages().lignes("boule.lore")) {
            lore.add(MiniMessage.miniMessage().deserialize("<!italic>" + ligne));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(cle, PersistentDataType.BYTE, (byte) 1);
        boule.setItemMeta(meta);
        return boule;
    }

    public boolean est(ItemStack objet) {
        return objet != null && objet.hasItemMeta() && objet.getItemMeta().getPersistentDataContainer().has(cle, PersistentDataType.BYTE);
    }

    /** L'objet réellement lancé : une boule normale en main avec la magique en seconde main n'est pas marquée. */
    @EventHandler(ignoreCancelled = true)
    public void onLancer(PlayerLaunchProjectileEvent event) {
        if (event.getProjectile() instanceof Snowball boule && est(event.getItemStack())) {
            boule.getPersistentDataContainer().set(cle, PersistentDataType.BYTE, (byte) 1);
        }
    }

    /** /shchristmaspresents give : en ligne seulement. */
    public void donner(org.bukkit.entity.Player joueur, int nombre) {
        int reste = Math.max(1, nombre);
        while (reste > 0) {
            int lot = Math.min(64, reste);
            org.Cry_Legende.sHChristmasPresents.commun.Joueurs.donner(joueur, creer(lot));
            reste -= lot;
        }
    }
}
