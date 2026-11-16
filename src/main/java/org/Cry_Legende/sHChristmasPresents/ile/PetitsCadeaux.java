package org.Cry_Legende.sHChristmasPresents.ile;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Joueurs;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetConfig;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetsMarques;
import org.Cry_Legende.sHChristmasPresents.stockage.Base;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Petits cadeaux trouvés sur l'île pendant les gains : minage à la main (blocs listés), pêche, récolte mûre, mob tué
 * par le joueur. Une chance par catégorie, max-jour par joueur. Clic droit = Flocons (source ile).
 * Ces événements arrivent sur le fil du monde ou de la région (DivineMC) : le tirage et le compteur du jour sont
 * atomiques, l'objet est donné sur le fil principal.
 */
public final class PetitsCadeaux implements Listener {

    private record Jour(LocalDate jour, int trouves) {
    }

    private final SHChristmasPresents plugin;
    private final Base base;
    private final int annee;
    private final Map<UUID, Jour> trouves = new ConcurrentHashMap<>();
    private volatile Set<String> mondes = Set.of("superiorworld");
    private volatile int maxJour = 4;
    private volatile Map<String, Double> chances = Map.of();
    private volatile Set<Material> blocs = EnumSet.noneOf(Material.class);
    private volatile int floconsMin = 3;
    private volatile int floconsMax = 8;
    private volatile ObjetConfig objet = new ObjetConfig(Material.PAPER, NamespacedKey.minecraft("lime_shulker_box"), true, null);

    public PetitsCadeaux(SHChristmasPresents plugin, Base base, int annee) {
        this.plugin = plugin;
        this.base = base;
        this.annee = annee;
    }

    public void configurer(ConfigurationSection s) {
        if (s == null) {
            return;
        }
        Set<String> lus = new HashSet<>();
        for (String monde : s.getStringList("mondes")) {
            lus.add(monde.toLowerCase(Locale.ROOT));
        }
        mondes = Set.copyOf(lus);
        maxJour = Math.max(0, s.getInt("max-jour", 4));
        Map<String, Double> c = new HashMap<>();
        ConfigurationSection cs = s.getConfigurationSection("chances");
        for (String categorie : List.of("minage", "peche", "recolte", "combat")) {
            c.put(categorie, cs == null ? 0 : Math.max(0, cs.getDouble(categorie, 0)));
        }
        chances = Map.copyOf(c);
        Set<Material> materiaux = EnumSet.noneOf(Material.class);
        for (String nom : s.getStringList("blocs-minage")) {
            Material m = Material.matchMaterial(nom.trim().toUpperCase(Locale.ROOT));
            if (m != null) {
                materiaux.add(m);
            } else {
                plugin.getLogger().warning("ile.blocs-minage : bloc inconnu " + nom);
            }
        }
        blocs = materiaux;
        floconsMin = Math.max(0, s.getInt("flocons-min", 3));
        floconsMax = Math.max(floconsMin, s.getInt("flocons-max", 8));
        objet = ObjetConfig.lire(s.getConfigurationSection("objet"), objet);
    }

    public void charger(Connection connexion, LocalDate aujourdhui) throws SQLException {
        try (PreparedStatement r = connexion.prepareStatement("SELECT uuid, trouves FROM shxmas_ile WHERE annee = ? AND jour = ?")) {
            r.setInt(1, annee);
            r.setDate(2, Date.valueOf(aujourdhui));
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    trouves.put(UUID.fromString(l.getString(1)), new Jour(aujourdhui, l.getInt(2)));
                }
            }
        }
    }

    public int trouvesAujourdhui(UUID uuid) {
        Jour j = trouves.get(uuid);
        return j == null || !j.jour().equals(plugin.horloge().aujourdhui()) ? 0 : j.trouves();
    }

    public int maxJour() {
        return maxJour;
    }

    // ---- événements (fil du monde)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCasse(BlockBreakEvent event) {
        BlockData donnees = event.getBlock().getBlockData();
        if (blocs.contains(event.getBlock().getType())) {
            tenter(event.getPlayer(), "minage", event.getBlock().getWorld());
        } else if (donnees instanceof Ageable age && age.getAge() >= age.getMaximumAge() && age.getMaximumAge() > 0) {
            tenter(event.getPlayer(), "recolte", event.getBlock().getWorld());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPeche(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            tenter(event.getPlayer(), "peche", event.getPlayer().getWorld());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMort(EntityDeathEvent event) {
        LivingEntity mort = event.getEntity();
        if (mort instanceof Player) {
            return;
        }
        Player tueur = mort.getKiller();
        if (tueur != null) {
            tenter(tueur, "combat", mort.getWorld());
        }
    }

    private void tenter(Player joueur, String categorie, World monde) {
        if (maxJour <= 0 || !mondes.contains(monde.getName().toLowerCase(Locale.ROOT))) {
            return;
        }
        GameMode mode = joueur.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        if (!plugin.dates().gains(plugin.horloge().maintenant())) {
            return;
        }
        double chance = chances.getOrDefault(categorie, 0.0);
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        LocalDate jour = plugin.horloge().aujourdhui();
        int[] obtenu = {0};
        trouves.compute(joueur.getUniqueId(), (uuid, avant) -> {
            Jour j = avant == null || !avant.jour().equals(jour) ? new Jour(jour, 0) : avant;
            if (j.trouves() >= maxJour) {
                return j;
            }
            obtenu[0] = j.trouves() + 1;
            return new Jour(jour, obtenu[0]);
        });
        if (obtenu[0] > 0) {
            int numero = obtenu[0];
            plugin.sync(() -> donner(joueur, jour, numero));
        }
    }

    // ---- fil principal

    private void donner(Player joueur, LocalDate jour, int numero) {
        UUID uuid = joueur.getUniqueId();
        base.ecrire("petit cadeau", c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT INTO shxmas_ile (annee, uuid, jour, trouves) VALUES (?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE trouves = GREATEST(trouves, VALUES(trouves))")) {
                r.setInt(1, annee);
                r.setString(2, uuid.toString());
                r.setDate(3, Date.valueOf(jour));
                r.setInt(4, numero);
                r.executeUpdate();
            }
        });
        if (!joueur.isOnline()) {
            return;
        }
        Joueurs.donner(joueur, creerObjet());
        plugin.messages().envoyer(joueur, "ile.trouve", "{n}", numero, "{max}", maxJour);
        plugin.son("petit-cadeau", joueur);
    }

    public ItemStack creerObjet() {
        ItemStack item = objet.creer(plugin.messages().objet("ile.objet.nom"), plugin.messages().lore("ile.objet.lore"));
        ItemMeta meta = item.getItemMeta();
        ObjetsMarques.marquer(meta, "petit-cadeau");
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClic(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!"petit-cadeau".equals(ObjetsMarques.type(event.getItem()))) {
            return;
        }
        event.setCancelled(true);
        Player joueur = event.getPlayer();
        plugin.sync(() -> ouvrir(joueur));
    }

    private void ouvrir(Player joueur) {
        if (!joueur.isOnline()) {
            return;
        }
        if (!plugin.dates().gains(plugin.horloge().maintenant())) {
            plugin.messages().envoyer(joueur, "ile.ferme");
            plugin.son("refus", joueur);
            return;
        }
        ItemStack[] contenu = joueur.getInventory().getContents();
        for (int i = 0; i < contenu.length; i++) {
            if ("petit-cadeau".equals(ObjetsMarques.type(contenu[i]))) {
                ItemStack item = contenu[i];
                item.setAmount(item.getAmount() - 1);
                joueur.getInventory().setItem(i, item.getAmount() <= 0 ? null : item);
                int montant = ThreadLocalRandom.current().nextInt(floconsMin, floconsMax + 1);
                plugin.flocons().gagner(joueur.getUniqueId(), joueur.getName(), montant, "ile", false);
                plugin.son("petit-cadeau-ouvert", joueur);
                return;
            }
        }
    }
}
