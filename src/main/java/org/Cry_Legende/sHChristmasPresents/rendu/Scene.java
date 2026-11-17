package org.Cry_Legende.sHChristmasPresents.rendu;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Objets montrés par paquets (cadeaux, bonhommes de neige) : le serveur n'a aucune entité, chaque joueur proche reçoit
 * les siennes. Toutes les 10 ticks, chaque joueur reçoit les objets entrés dans leur portée et perd ceux qu'il a
 * quittés (8 blocs de marge) ; à chaque tick, les objets vus s'animent. Un joueur qui change de monde ou réapparaît
 * a tout oublié côté client : il reçoit tout à nouveau au balayage suivant. Fil principal, sauf les clics reçus du
 * réseau, renvoyés au fil principal.
 */
public final class Scene implements Listener {

    /** Objets envoyés au plus à un joueur par balayage (un joueur qui arrive les reçoit en quelques balayages). */
    private static final int ENVOIS_PAR_BALAYAGE = 16;

    private final Plugin plugin;
    private final List<Objet3D> objets = new ArrayList<>();
    private final Map<Integer, Objet3D> zones = new ConcurrentHashMap<>();
    private BukkitTask tache;
    private Object ecoute;
    private long tick;

    public Scene(Plugin plugin) {
        this.plugin = plugin;
    }

    public void demarrer() {
        if (tache == null) {
            tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tic, 1L, 1L);
        }
        if (ecoute == null) {
            ecoute = Entrees.enregistrer(this);
        }
    }

    public void arreter() {
        if (tache != null) {
            tache.cancel();
            tache = null;
        }
        if (ecoute != null) {
            Entrees.retirer(ecoute);
            ecoute = null;
        }
        for (Objet3D o : objets) {
            o.cacherTous();
        }
        objets.clear();
        zones.clear();
    }

    public long tick() {
        return tick;
    }

    public void ajouter(Objet3D objet) {
        objets.add(objet);
        if (objet.zoneId() >= 0) {
            zones.put(objet.zoneId(), objet);
        }
        // Montré tout de suite à ceux qui sont déjà là (un cadeau qui apparaît sous les yeux d'un joueur)
        double r2 = (double) objet.portee() * objet.portee();
        for (Player p : objet.monde().getPlayers()) {
            if (p.getLocation().distanceSquared(new Location(objet.monde(), objet.x(), objet.y(), objet.z())) <= r2) {
                objet.montrer(p);
            }
        }
    }

    public void retirer(Objet3D objet) {
        if (objets.remove(objet)) {
            zones.remove(objet.zoneId());
            objet.cacherTous();
        }
    }

    public List<Objet3D> objets() {
        return objets;
    }

    private void tic() {
        tick++;
        if (tick % 10 == 0) {
            balayer();
        }
        for (Objet3D o : List.copyOf(objets)) {
            if (o.vu()) {
                try {
                    o.animer(tick);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Animation en erreur : " + e);
                }
            }
        }
    }

    private void balayer() {
        Map<World, List<Objet3D>> parMonde = new HashMap<>();
        for (Objet3D o : objets) {
            parMonde.computeIfAbsent(o.monde(), k -> new ArrayList<>()).add(o);
        }
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            UUID uuid = joueur.getUniqueId();
            Location ici = joueur.getLocation();
            World monde = ici.getWorld();
            int envois = 0;
            for (Map.Entry<World, List<Objet3D>> e : parMonde.entrySet()) {
                boolean memeMonde = e.getKey().equals(monde);
                for (Objet3D o : e.getValue()) {
                    boolean voit = o.voit(uuid);
                    if (!memeMonde) {
                        if (voit) {
                            o.cacher(joueur);
                        }
                        continue;
                    }
                    double dx = o.x() - ici.getX(), dy = o.y() - ici.getY(), dz = o.z() - ici.getZ();
                    double d2 = dx * dx + dy * dy + dz * dz;
                    int r = o.portee();
                    if (!voit && d2 <= (double) r * r && envois < ENVOIS_PAR_BALAYAGE) {
                        o.montrer(joueur);
                        envois++;
                    } else if (voit && d2 > (double) (r + 8) * (r + 8)) {
                        o.cacher(joueur);
                    }
                }
            }
        }
    }

    /** Clic reçu du réseau sur une zone : vrai si elle est à nous (le paquet est alors bloqué). */
    boolean clic(UUID uuid, int id, boolean gauche) {
        Objet3D objet = zones.get(id);
        if (objet == null) {
            return false;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur != null && joueur.isOnline() && objets.contains(objet) && objet.voit(uuid)) {
                objet.clic(joueur, gauche);
            }
        });
        return true;
    }

    boolean enTrace() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    /** Ligne de debug (config debug: true). */
    void trace(String texte) {
        if (plugin instanceof org.Cry_Legende.sHChristmasPresents.SHChristmasPresents p) {
            p.debug(texte);
        }
    }

    boolean possede(int id) {
        return zones.containsKey(id);
    }

    private void oublier(UUID uuid) {
        for (Objet3D o : objets) {
            o.oublier(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDepart(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> oublier(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMonde(PlayerChangedWorldEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> oublier(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReapparition(PlayerRespawnEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> oublier(uuid));
    }
}
