package org.Cry_Legende.sHChristmasPresents.cadeaux;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Joueurs;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetConfig;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetsMarques;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.Cry_Legende.sHChristmasPresents.joueurs.Profil;
import org.Cry_Legende.sHChristmasPresents.stockage.Base;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /cadeau : un joueur emballe des Flocons pour un autre. Le donneur paie ; le receveur reçoit un objet « Cadeau de
 * &lt;pseudo&gt; » unique (shxmas_cadeaux), livré à sa connexion s'il est absent, qui ne s'ouvre qu'une fois. Ouvrir =
 * Flocons de la source cadeau-recu (hors plafond, hors multiplicateurs : réglage de la source).
 */
public final class CadeauxJoueurs implements Listener {

    static final class Cadeau {
        final String id;
        final UUID de;
        final String dePseudo;
        final UUID a;
        final String aPseudo;
        final int montant;
        final LocalDateTime cree;
        volatile String statut;

        Cadeau(String id, UUID de, String dePseudo, UUID a, String aPseudo, int montant, LocalDateTime cree, String statut) {
            this.id = id;
            this.de = de;
            this.dePseudo = dePseudo;
            this.a = a;
            this.aPseudo = aPseudo;
            this.montant = montant;
            this.cree = cree;
            this.statut = statut;
        }
    }

    private static final String ATTENTE = "attente";
    private static final String LIVRE = "livre";
    private static final String OUVERT = "ouvert";

    private final SHChristmasPresents plugin;
    private final Base base;
    private final int annee;
    private final NamespacedKey cleId;
    private final Map<String, Cadeau> cadeaux = new ConcurrentHashMap<>();
    private volatile int montantMin = 20;
    private volatile int montantMax = 500;
    private volatile int envoyesParJour = 3;
    private volatile int recusParJour = 5;
    private volatile int heuresDeJeu = 5;
    private volatile boolean empreintesDifferentes = true;
    private volatile int pointsSapin = 5;
    private volatile ObjetConfig objet = new ObjetConfig(Material.PAPER, NamespacedKey.minecraft("red_shulker_box"), true, null);

    public CadeauxJoueurs(SHChristmasPresents plugin, Base base, int annee) {
        this.plugin = plugin;
        this.base = base;
        this.annee = annee;
        this.cleId = new NamespacedKey(plugin, "cadeau");
    }

    public void configurer(ConfigurationSection s) {
        if (s == null) {
            return;
        }
        montantMin = Math.max(1, s.getInt("montant-min", 20));
        montantMax = Math.max(montantMin, s.getInt("montant-max", 500));
        envoyesParJour = Math.max(0, s.getInt("envoyes-par-jour", 3));
        recusParJour = Math.max(0, s.getInt("recus-par-jour", 5));
        heuresDeJeu = Math.max(0, s.getInt("temps-de-jeu-heures", 5));
        empreintesDifferentes = s.getBoolean("empreintes-differentes", true);
        pointsSapin = Math.max(0, s.getInt("sapin-points", 5));
        objet = ObjetConfig.lire(s.getConfigurationSection("objet"), objet);
    }

    public void charger(Connection connexion) throws SQLException {
        try (PreparedStatement r = connexion.prepareStatement("SELECT id, de_uuid, de_pseudo, a_uuid, a_pseudo, montant, statut, cree"
                + " FROM shxmas_cadeaux WHERE annee = ?")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    Cadeau c = new Cadeau(l.getString(1), UUID.fromString(l.getString(2)), l.getString(3), UUID.fromString(l.getString(4)),
                            l.getString(5), l.getInt(6), l.getTimestamp(8).toLocalDateTime(), l.getString(7));
                    cadeaux.put(c.id, c);
                }
            }
        }
    }

    public int envoyesJour(UUID uuid, LocalDate jour) {
        int n = 0;
        for (Cadeau c : cadeaux.values()) {
            if (c.de.equals(uuid) && c.cree.toLocalDate().equals(jour)) {
                n++;
            }
        }
        return n;
    }

    public int recusJour(UUID uuid, LocalDate jour) {
        int n = 0;
        for (Cadeau c : cadeaux.values()) {
            if (c.a.equals(uuid) && c.cree.toLocalDate().equals(jour)) {
                n++;
            }
        }
        return n;
    }

    /** Statistiques du bilan : nombre de cadeaux, Flocons offerts, cadeaux ouverts. */
    public long[] stats() {
        long n = 0, flocons = 0, ouverts = 0;
        for (Cadeau c : cadeaux.values()) {
            n++;
            flocons += c.montant;
            if (OUVERT.equals(c.statut)) {
                ouverts++;
            }
        }
        return new long[]{n, flocons, ouverts};
    }

    /** /cadeau &lt;joueur&gt; &lt;montant&gt; (fil principal). */
    public void envoyer(Player donneur, String nomCible, String montantTexte) {
        var m = plugin.messages();
        if (!plugin.dates().echanges(plugin.horloge().maintenant())) {
            m.envoyer(donneur, "cadeau.ferme");
            plugin.son("refus", donneur);
            return;
        }
        int montant;
        try {
            montant = Integer.parseInt(montantTexte.trim());
        } catch (NumberFormatException e) {
            m.envoyer(donneur, "cadeau.usage");
            return;
        }
        if (montant < montantMin || montant > montantMax) {
            refus(donneur, "cadeau.montant", "{min}", montantMin, "{max}", montantMax);
            return;
        }
        Joueurs.Cible cible = Joueurs.trouver(nomCible);
        Profil profilCible = cible == null ? null : plugin.profils().get(cible.uuid());
        if (cible == null || (profilCible == null && cible.enLigne() == null)) {
            refus(donneur, "cadeau.inconnu", "{joueur}", nomCible);
            return;
        }
        UUID uuid = donneur.getUniqueId();
        if (cible.uuid().equals(uuid)) {
            refus(donneur, "cadeau.soi-meme");
            return;
        }
        LocalDate jour = plugin.horloge().aujourdhui();
        if (envoyesJour(uuid, jour) >= envoyesParJour) {
            refus(donneur, "cadeau.limite-envoyes", "{n}", envoyesParJour);
            return;
        }
        if (recusJour(cible.uuid(), jour) >= recusParJour) {
            refus(donneur, "cadeau.limite-recus", "{joueur}", cible.pseudo(), "{n}", recusParJour);
            return;
        }
        int minimum = heuresDeJeu * 60;
        if (Joueurs.minutesDeJeu(donneur) < minimum) {
            refus(donneur, "cadeau.temps-donneur", "{heures}", heuresDeJeu);
            return;
        }
        int minutesCible = cible.enLigne() != null ? Joueurs.minutesDeJeu(cible.enLigne()) : profilCible.tempsJeu;
        if (minutesCible < minimum) {
            refus(donneur, "cadeau.temps-receveur", "{joueur}", cible.pseudo(), "{heures}", heuresDeJeu);
            return;
        }
        if (empreintesDifferentes) {
            String mienne = plugin.empreintes().de(donneur);
            String sienne = cible.enLigne() != null ? plugin.empreintes().de(cible.enLigne()) : profilCible.empreinte;
            if (mienne == null || sienne == null || mienne.equals(sienne)) {
                refus(donneur, "cadeau.meme-connexion", "{joueur}", cible.pseudo());
                return;
            }
        }
        if (!plugin.verrouiller(uuid)) {
            return;
        }
        plugin.flocons().portefeuille().retirer(donneur, montant, retrait -> {
            try {
                switch (retrait) {
                    case INSUFFISANT -> {
                        long solde = plugin.flocons().portefeuille().solde(donneur);
                        refus(donneur, "cadeau.manque", "{manque}", Textes.nombre(montant - Math.max(0, solde)));
                    }
                    case ILLISIBLE, ECHEC -> refus(donneur, "erreur");
                    case FAIT -> creer(donneur, cible, montant);
                }
            } finally {
                plugin.deverrouiller(uuid);
            }
        });
    }

    private void refus(Player joueur, String cle, Object... remplacements) {
        plugin.messages().envoyer(joueur, cle, remplacements);
        plugin.son("refus", joueur);
    }

    private void creer(Player donneur, Joueurs.Cible cible, int montant) {
        LocalDateTime maintenant = plugin.horloge().local();
        Cadeau cadeau = new Cadeau(UUID.randomUUID().toString(), donneur.getUniqueId(), donneur.getName(), cible.uuid(),
                cible.pseudo(), montant, maintenant, ATTENTE);
        cadeaux.put(cadeau.id, cadeau);
        base.ecrire("cadeau " + cadeau.id, c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT INTO shxmas_cadeaux (id, annee, de_uuid, de_pseudo, a_uuid, a_pseudo,"
                    + " montant, statut, cree) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                r.setString(1, cadeau.id);
                r.setInt(2, annee);
                r.setString(3, cadeau.de.toString());
                r.setString(4, cadeau.dePseudo);
                r.setString(5, cadeau.a.toString());
                r.setString(6, cadeau.aPseudo);
                r.setInt(7, cadeau.montant);
                r.setString(8, ATTENTE);
                r.setTimestamp(9, Timestamp.valueOf(maintenant));
                r.executeUpdate();
            }
        });
        plugin.compteurs().ajouter(donneur.getUniqueId(), "cadeaux-envoyes", 1);
        plugin.carte().compteurChange(donneur.getUniqueId(), donneur.getName(), "cadeaux-envoyes");
        if (pointsSapin > 0 && plugin.dates().gains(plugin.horloge().maintenant())) {
            plugin.sapin().contribuer(donneur.getUniqueId(), donneur.getName(), pointsSapin, true);
        }
        plugin.messages().envoyer(donneur, "cadeau.envoye", "{joueur}", cible.pseudo(), "{montant}", Textes.nombre(montant));
        plugin.son("cadeau-envoye", donneur);
        plugin.getLogger().info("Cadeau : " + donneur.getName() + " → " + cible.pseudo() + " (" + montant + " Flocons, " + cadeau.id + ")");
        Player receveur = Bukkit.getPlayer(cible.uuid());
        if (receveur != null) {
            livrer(receveur, cadeau);
            plugin.messages().envoyer(receveur, "cadeau.recu", "{joueur}", donneur.getName());
            plugin.son("cadeau-recu", receveur);
        }
    }

    private void livrer(Player receveur, Cadeau cadeau) {
        ItemStack item = objet.creer(plugin.messages().objet("cadeau.objet.nom", "{joueur}", cadeau.dePseudo),
                plugin.messages().lore("cadeau.objet.lore", "{joueur}", cadeau.dePseudo));
        ItemMeta meta = item.getItemMeta();
        ObjetsMarques.marquer(meta, "cadeau");
        meta.getPersistentDataContainer().set(cleId, PersistentDataType.STRING, cadeau.id);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        Joueurs.donner(receveur, item);
        cadeau.statut = LIVRE;
        majStatut(cadeau, "livre");
    }

    private void majStatut(Cadeau cadeau, String colonne) {
        String statut = cadeau.statut;
        LocalDateTime maintenant = plugin.horloge().local();
        base.ecrire("cadeau " + cadeau.id, c -> {
            try (PreparedStatement r = c.prepareStatement("UPDATE shxmas_cadeaux SET statut = ?, " + colonne + " = ? WHERE id = ?")) {
                r.setString(1, statut);
                r.setTimestamp(2, Timestamp.valueOf(maintenant));
                r.setString(3, cadeau.id);
                r.executeUpdate();
            }
        });
    }

    /** À la connexion : cadeaux reçus pendant l'absence. */
    public void livrerEnAttente(Player joueur) {
        List<Cadeau> enAttente = new ArrayList<>();
        for (Cadeau c : cadeaux.values()) {
            if (c.a.equals(joueur.getUniqueId()) && ATTENTE.equals(c.statut)) {
                enAttente.add(c);
            }
        }
        if (enAttente.isEmpty()) {
            return;
        }
        for (Cadeau c : enAttente) {
            livrer(joueur, c);
        }
        plugin.messages().envoyer(joueur, "cadeau.recus-absent", "{n}", enAttente.size());
        plugin.son("cadeau-recu", joueur);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClic(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack objetEnMain = event.getItem();
        if (!"cadeau".equals(ObjetsMarques.type(objetEnMain))) {
            return;
        }
        event.setCancelled(true);
        String id = objetEnMain.getItemMeta().getPersistentDataContainer().get(cleId, PersistentDataType.STRING);
        Player joueur = event.getPlayer();
        plugin.sync(() -> ouvrir(joueur, id));
    }

    /** Ouvre le cadeau (fil principal) ; un double ou un cadeau déjà ouvert disparaît sans rien donner. */
    void ouvrir(Player joueur, String id) {
        if (id == null || !joueur.isOnline()) {
            return;
        }
        Cadeau cadeau = cadeaux.get(id);
        if (cadeau != null && !cadeau.a.equals(joueur.getUniqueId()) && !OUVERT.equals(cadeau.statut)) {
            plugin.messages().envoyer(joueur, "cadeau.pas-pour-toi", "{joueur}", cadeau.aPseudo);
            plugin.son("refus", joueur);
            return;
        }
        if (!retirerObjet(joueur, id)) {
            return;
        }
        if (cadeau == null || OUVERT.equals(cadeau.statut)) {
            plugin.messages().envoyer(joueur, "cadeau.deja-ouvert");
            plugin.son("refus", joueur);
            plugin.getLogger().warning("Cadeau " + id + " déjà ouvert ou inconnu, objet retiré à " + joueur.getName());
            return;
        }
        cadeau.statut = OUVERT;
        majStatut(cadeau, "ouvert");
        int verse = plugin.flocons().gagner(joueur.getUniqueId(), joueur.getName(), cadeau.montant, "cadeau-recu", true);
        plugin.messages().envoyer(joueur, "cadeau.ouvert", "{joueur}", cadeau.dePseudo, "{montant}", Textes.nombre(Math.max(0, verse)));
        plugin.son("cadeau-ouvert", joueur);
    }

    private boolean retirerObjet(Player joueur, String id) {
        ItemStack[] contenu = joueur.getInventory().getContents();
        for (int i = 0; i < contenu.length; i++) {
            ItemStack item = contenu[i];
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            String lu = item.getItemMeta().getPersistentDataContainer().get(cleId, PersistentDataType.STRING);
            if (id.equals(lu)) {
                item.setAmount(item.getAmount() - 1);
                joueur.getInventory().setItem(i, item.getAmount() <= 0 ? null : item);
                return true;
            }
        }
        return false;
    }

}
