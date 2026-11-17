package org.Cry_Legende.sHChristmasPresents.sapin;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.Cry_Legende.sHChristmasPresents.flocons.Portefeuille;
import org.Cry_Legende.sHChristmasPresents.stockage.Base;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grand Sapin : jauge commune. Points = Flocons offerts (/sapin) et contributions automatiques des sources. La part
 * comptée de chaque joueur est plafonnée par jour ; contributeur = au moins contribution-min points sur l'évènement.
 * Chaque palier part une fois (réservé dans shxmas_actions) : commandes serveur, puis commandes de chaque
 * contributeur (hors ligne : à sa prochaine connexion). Le palier marqué veillee ne part que par la commande
 * sapin veillee, dès seuil-veillee %. Fil principal pour tout ce qui écrit.
 */
public final class Sapin {

    public record Palier(int pourcent, boolean veillee, List<String> serveur, List<String> contributeur) {
    }

    /** Contribution d'un joueur. */
    public static final class Part {
        final UUID uuid;
        volatile String pseudo;
        volatile long total;
        volatile LocalDate jour;
        volatile int partJour;
        volatile int offertJour;

        Part(UUID uuid, String pseudo) {
            this.uuid = uuid;
            this.pseudo = pseudo;
        }

        public String pseudo() {
            return pseudo;
        }

        public long total() {
            return total;
        }
    }

    private static final String CLE_CIBLE = "sapin-cible";

    private final SHChristmasPresents plugin;
    private final Base base;
    private final int annee;
    private final Map<UUID, Part> parts = new ConcurrentHashMap<>();
    private volatile long somme;
    private volatile int cibleConfig = 40_000;
    private volatile double depart = 10;
    private volatile int plafondJour = 600;
    private volatile int contributionMin = 50;
    private volatile double seuilVeillee = 90;
    private volatile List<Integer> offrandes = List.of(10, 50, 100, 500);
    private volatile List<Palier> paliers = List.of();
    private volatile List<Part> top = List.of();
    private volatile long topCalcule;
    volatile int lignes = 4;
    volatile int caseJauge = 4;
    volatile int caseContribution = 13;
    volatile int caseTop = 31;
    volatile List<Integer> casesOffrandes = List.of(19, 21, 23, 25);
    volatile Material cadre = Material.BLACK_STAINED_GLASS_PANE;
    volatile org.Cry_Legende.sHChristmasPresents.commun.Accent accent = org.Cry_Legende.sHChristmasPresents.commun.Accent.AUCUN;

    public Sapin(SHChristmasPresents plugin, Base base, int annee) {
        this.plugin = plugin;
        this.base = base;
        this.annee = annee;
    }

    public void configurer(ConfigurationSection s) {
        if (s == null) {
            return;
        }
        cibleConfig = Math.max(1, s.getInt("cible", 40_000));
        depart = Math.max(0, s.getDouble("depart-pourcent", 10));
        plafondJour = Math.max(0, s.getInt("plafond-joueur-jour", 600));
        contributionMin = Math.max(1, s.getInt("contribution-min", 50));
        seuilVeillee = s.getDouble("seuil-veillee", 90);
        List<Integer> lues = LectureConfig.entiers(s, "offrandes");
        offrandes = lues.isEmpty() ? List.of(10, 50, 100, 500) : List.copyOf(lues);
        List<Palier> lus = new ArrayList<>();
        ConfigurationSection p = s.getConfigurationSection("paliers");
        if (p != null) {
            for (String cle : p.getKeys(false)) {
                ConfigurationSection palier = p.getConfigurationSection(cle);
                try {
                    lus.add(new Palier(Integer.parseInt(cle.trim()), palier != null && palier.getBoolean("veillee", false),
                            LectureConfig.textes(palier, "commandes-serveur"), LectureConfig.textes(palier, "commandes-contributeur")));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("sapin.paliers." + cle + " : pourcentage illisible");
                }
            }
        }
        lus.sort(Comparator.comparingInt(Palier::pourcent));
        paliers = List.copyOf(lus);
        ConfigurationSection menu = s.getConfigurationSection("menu");
        if (menu != null) {
            lignes = menu.getInt("lignes", 4);
            caseJauge = menu.getInt("case-jauge", 4);
            caseContribution = menu.getInt("case-contribution", 13);
            caseTop = menu.getInt("case-top", 31);
            List<Integer> cases = LectureConfig.entiers(menu, "cases-offrandes");
            casesOffrandes = cases.isEmpty() ? List.of(19, 21, 23, 25) : List.copyOf(cases);
            Material vitre = Material.matchMaterial(menu.getString("cadre", "BLACK_STAINED_GLASS_PANE"));
            cadre = vitre == null ? Material.AIR : vitre;
            accent = org.Cry_Legende.sHChristmasPresents.commun.Accent.lire(menu);
        }
    }

    public void charger(Connection connexion) throws SQLException {
        long total = 0;
        try (PreparedStatement r = connexion.prepareStatement("SELECT uuid, pseudo, total, jour, part_jour, offert_jour FROM shxmas_sapin WHERE annee = ?")) {
            r.setInt(1, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    Part part = new Part(UUID.fromString(l.getString(1)), l.getString(2));
                    part.total = l.getLong(3);
                    Date jour = l.getDate(4);
                    part.jour = jour == null ? null : jour.toLocalDate();
                    part.partJour = l.getInt(5);
                    part.offertJour = l.getInt(6);
                    parts.put(part.uuid, part);
                    total += part.total;
                }
            }
        }
        somme = total;
    }

    // ---- lecture

    public int cible() {
        String regle = plugin.etat().get(CLE_CIBLE);
        if (regle != null) {
            try {
                int valeur = Integer.parseInt(regle);
                if (valeur > 0) {
                    return valeur;
                }
            } catch (NumberFormatException ignored) {
                // valeur de config
            }
        }
        return cibleConfig;
    }

    public boolean cibleReglee() {
        return plugin.etat().get(CLE_CIBLE) != null;
    }

    public long points() {
        return somme;
    }

    public double pourcent() {
        return Math.max(0, Math.min(100, depart + 100.0 * somme / cible()));
    }

    public long contribution(UUID uuid) {
        Part part = parts.get(uuid);
        return part == null ? 0 : part.total;
    }

    public int contributionJour(UUID uuid, LocalDate jour) {
        Part part = parts.get(uuid);
        return part == null || !jour.equals(part.jour) ? 0 : part.partJour;
    }

    /** Flocons offerts au sapin aujourd'hui (placeholder offert_jour). */
    public int offertJour(UUID uuid, LocalDate jour) {
        Part part = parts.get(uuid);
        return part == null || !jour.equals(part.jour) ? 0 : part.offertJour;
    }

    public int resteJour(UUID uuid, LocalDate jour) {
        return plafondJour <= 0 ? Integer.MAX_VALUE : Math.max(0, plafondJour - contributionJour(uuid, jour));
    }

    public int plafondJour() {
        return plafondJour;
    }

    public boolean contributeur(UUID uuid) {
        return contribution(uuid) >= contributionMin;
    }

    public int contributeurs() {
        int n = 0;
        for (Part part : parts.values()) {
            if (part.total >= contributionMin) {
                n++;
            }
        }
        return n;
    }

    /** Rang parmi les contributeurs (1 = le plus gros), 0 si pas contributeur. */
    public int rang(UUID uuid) {
        long mien = contribution(uuid);
        if (mien < contributionMin) {
            return 0;
        }
        int rang = 1;
        for (Part part : parts.values()) {
            if (part.total > mien) {
                rang++;
            }
        }
        return rang;
    }

    /** Meilleurs contributeurs (relu au plus toutes les 5 s). */
    public List<Part> top() {
        long maintenant = System.currentTimeMillis();
        if (maintenant - topCalcule > 5_000) {
            List<Part> tries = new ArrayList<>(parts.values());
            tries.removeIf(p -> p.total <= 0);
            tries.sort(Comparator.comparingLong((Part p) -> p.total).reversed());
            top = List.copyOf(tries.subList(0, Math.min(10, tries.size())));
            topCalcule = maintenant;
        }
        return top;
    }

    public List<Palier> paliers() {
        return paliers;
    }

    public List<Integer> offrandes() {
        return offrandes;
    }

    public boolean palierFait(Palier palier) {
        return plugin.actions().traitee(idPalier(palier));
    }

    /** Pourcentage du prochain palier pas encore parti, null s'il n'y en a plus. */
    public Integer palierSuivant() {
        for (Palier palier : paliers) {
            if (!palierFait(palier)) {
                return palier.pourcent();
            }
        }
        return null;
    }

    private static String idPalier(Palier palier) {
        return "sapin-palier-" + palier.pourcent();
    }

    public Palier palier(String ref) {
        for (Palier palier : paliers) {
            if (Integer.toString(palier.pourcent()).equals(ref)) {
                return palier;
            }
        }
        return null;
    }

    // ---- écriture

    /**
     * Ajoute des points à la part d'un joueur (fil principal).
     *
     * @param plafonne vrai pour respecter la part comptée du jour
     * @return points comptés
     */
    public int contribuer(UUID uuid, String pseudo, int points, boolean plafonne) {
        if (points == 0) {
            return 0;
        }
        LocalDate jour = plugin.horloge().aujourdhui();
        Part part = parts.computeIfAbsent(uuid, k -> new Part(uuid, pseudo));
        if (pseudo != null) {
            part.pseudo = pseudo;
        }
        if (!jour.equals(part.jour)) {
            part.jour = jour;
            part.partJour = 0;
            part.offertJour = 0;
        }
        int comptes = points;
        if (plafonne && points > 0 && plafondJour > 0) {
            comptes = Math.max(0, Math.min(points, plafondJour - part.partJour));
        }
        if (comptes == 0) {
            return 0;
        }
        long avant = part.total;
        part.total = Math.max(0, avant + comptes);
        if (plafonne) {
            part.partJour += comptes;
        }
        somme += part.total - avant;
        sauver(part);
        verifierPaliers();
        return (int) (part.total - avant);
    }

    private void sauver(Part part) {
        String pseudo = part.pseudo == null ? "?" : part.pseudo;
        long total = part.total;
        LocalDate jour = part.jour;
        int partJour = part.partJour;
        int offertJour = part.offertJour;
        base.ecrire("sapin " + pseudo, c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT INTO shxmas_sapin (annee, uuid, pseudo, total, jour, part_jour, offert_jour)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE pseudo = VALUES(pseudo), total = VALUES(total),"
                    + " jour = VALUES(jour), part_jour = VALUES(part_jour), offert_jour = VALUES(offert_jour)")) {
                r.setInt(1, annee);
                r.setString(2, part.uuid.toString());
                r.setString(3, pseudo);
                r.setLong(4, total);
                r.setDate(5, jour == null ? null : Date.valueOf(jour));
                r.setInt(6, partJour);
                r.setInt(7, offertJour);
                r.executeUpdate();
            }
        });
    }

    /** /sapin offrir : retire les Flocons, compte autant de points (jusqu'à la part du jour). */
    public void offrir(Player joueur, int demande) {
        var messages = plugin.messages();
        if (!plugin.dates().gains(plugin.horloge().maintenant())) {
            messages.envoyer(joueur, "sapin.ferme");
            plugin.son("refus", joueur);
            return;
        }
        if (demande <= 0) {
            messages.envoyer(joueur, "sapin.usage");
            return;
        }
        UUID uuid = joueur.getUniqueId();
        LocalDate jour = plugin.horloge().aujourdhui();
        int reste = resteJour(uuid, jour);
        if (reste <= 0) {
            messages.envoyer(joueur, "sapin.part-complete", "{plafond}", Textes.nombre(plafondJour));
            plugin.son("refus", joueur);
            return;
        }
        int montant = Math.min(demande, reste);
        if (!plugin.verrouiller(uuid)) {
            return;
        }
        plugin.flocons().portefeuille().retirer(joueur, montant, retrait -> {
            try {
                apresRetrait(joueur, demande, montant, retrait);
            } finally {
                plugin.deverrouiller(uuid);
            }
        });
    }

    private void apresRetrait(Player joueur, int demande, int montant, Portefeuille.Retrait retrait) {
        var messages = plugin.messages();
        UUID uuid = joueur.getUniqueId();
        switch (retrait) {
            case INSUFFISANT -> {
                long solde = plugin.flocons().portefeuille().solde(joueur);
                messages.envoyer(joueur, "sapin.manque", "{manque}", Textes.nombre(montant - Math.max(0, solde)));
                plugin.son("refus", joueur);
            }
            case ILLISIBLE, ECHEC -> {
                messages.envoyer(joueur, "erreur");
                plugin.son("refus", joueur);
            }
            case FAIT -> {
                contribuer(uuid, joueur.getName(), montant, true);
                Part part = parts.get(uuid);
                if (part != null) {
                    part.offertJour += montant;
                    sauver(part);
                }
                plugin.compteurs().ajouter(uuid, "offert", montant);
                plugin.carte().compteurChange(uuid, joueur.getName(), "offert");
                messages.envoyer(joueur, montant < demande ? "sapin.offert-partiel" : "sapin.offert",
                        "{montant}", Textes.nombre(montant), "{pourcent}", Textes.pourcent(pourcent()));
                plugin.son("offrande", joueur);
            }
        }
    }

    /** Paliers atteints qui ne sont pas encore partis (hors palier de la veillée). */
    public void verifierPaliers() {
        double actuel = pourcent();
        for (Palier palier : paliers) {
            if (!palier.veillee() && actuel >= palier.pourcent() && !palierFait(palier)) {
                declencher(palier, null);
            }
        }
    }

    /** /shchristmaspresents sapin veillee : le palier de la veillée part si la jauge est au seuil. */
    public void veillee(CommandSender auteur) {
        Palier palier = null;
        for (Palier p : paliers) {
            if (p.veillee()) {
                palier = p;
            }
        }
        if (palier == null) {
            plugin.messages().envoyer(auteur, "admin.sapin-veillee-absent");
            return;
        }
        if (palierFait(palier)) {
            plugin.messages().envoyer(auteur, "admin.sapin-veillee-fait");
            return;
        }
        double actuel = pourcent();
        if (actuel < seuilVeillee) {
            plugin.messages().envoyer(auteur, "admin.sapin-veillee-refus", "{pourcent}", Textes.pourcent(actuel),
                    "{seuil}", Textes.facteur(seuilVeillee));
            plugin.getLogger().info("Veillée : jauge à " + Textes.pourcent(actuel) + " %, sous le seuil de " + seuilVeillee + " %");
            return;
        }
        declencher(palier, auteur);
    }

    private void declencher(Palier palier, CommandSender auteur) {
        plugin.actions().reclamer(idPalier(palier), "fait", null, reserve -> {
            if (!reserve) {
                return;
            }
            String pourcent = Integer.toString(palier.pourcent());
            int serveur = Console.executer(plugin.getLogger(), palier.serveur(), "{palier}", pourcent);
            int enLigne = 0;
            int plusTard = 0;
            for (Part part : parts.values()) {
                if (part.total < contributionMin) {
                    continue;
                }
                Player joueur = Bukkit.getPlayer(part.uuid);
                if (joueur != null) {
                    recompenser(joueur, palier);
                    enLigne++;
                } else {
                    plugin.livraisons().ajouter(part.uuid, "sapin-palier", pourcent);
                    plusTard++;
                }
            }
            plugin.getLogger().info("Sapin : palier " + pourcent + " % atteint (" + serveur + " commande(s) serveur, "
                    + enLigne + " contributeur(s) en ligne, " + plusTard + " à leur connexion)");
            if (auteur != null) {
                plugin.messages().envoyer(auteur, "admin.sapin-palier", "{palier}", pourcent, "{en-ligne}", enLigne, "{plus-tard}", plusTard);
            }
        });
    }

    /** Commandes de contributeur d'un palier pour un joueur en ligne (aussi à la livraison). */
    public void recompenser(Player joueur, Palier palier) {
        Console.executer(plugin.getLogger(), palier.contributeur(), "{joueur}", joueur.getName(), "{uuid}",
                joueur.getUniqueId().toString(), "{palier}", Integer.toString(palier.pourcent()));
    }

    /** /shchristmaspresents sapin cible : 0 revient à la cible de config.yml. */
    public void reglerCible(int cible) {
        plugin.etat().set(CLE_CIBLE, cible > 0 ? Integer.toString(cible) : null);
        verifierPaliers();
    }

    public void ouvrirMenu(Player joueur) {
        new MenuSapin(plugin, this, joueur).ouvrir();
    }

    public void info(CommandSender cible) {
        StringBuilder faits = new StringBuilder();
        for (Palier palier : paliers) {
            if (palierFait(palier)) {
                faits.append(faits.isEmpty() ? "" : ", ").append(palier.pourcent());
            }
        }
        plugin.messages().envoyer(cible, "admin.sapin-info", "{pourcent}", Textes.pourcent(pourcent()),
                "{points}", Textes.nombre(somme), "{cible}", Textes.nombre(cible()), "{origine}",
                plugin.messages().brut(cibleReglee() ? "admin.sapin-cible-commande" : "admin.sapin-cible-config"),
                "{depart}", Textes.facteur(depart), "{contributeurs}", contributeurs(),
                "{paliers}", faits.isEmpty() ? "-" : faits.toString());
    }
}
