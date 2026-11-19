package org.Cry_Legende.sHChristmasPresents.sapin;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Accent;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetConfig;
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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grand Sapin : jauge commune, décorée palier par palier.
 * <ul>
 * <li>Points automatiques : 1 point pour {@code automatique.flocons-par-point} Flocons gagnés en jouant (après
 * multiplicateurs, sources exclues mises à part), sans rien coûter au joueur ;</li>
 * <li>offrandes : 1 point par Flocon offert (× coup de pouce), au plus {@code offrandes.plafond-jour} Flocons par
 * joueur et par jour ;</li>
 * <li>rangs de contributeur (points de l'évènement) : le rang multiplie les cadeaux des paliers ({@code {fois:N}} =
 * N × rang dans les commandes et les textes) ;</li>
 * <li>chaque palier part une fois (réservé dans shxmas_actions) : commandes serveur (décor du sapin), puis cadeau de
 * chaque contributeur selon son rang à ce moment (hors ligne : à sa prochaine connexion) ;</li>
 * <li>le palier marqué veillee ne part que par {@code sapin veillee}, dès seuil-veillee % ; sous le seuil, ses
 * commandes-echec partent. Une fois la Veillée passée, le sapin ne prend plus rien.</li>
 * </ul>
 * Fil principal pour tout ce qui écrit.
 */
public final class Sapin {

    /**
     * Un palier : pourcentage, commandes serveur (décor), commandes de chaque contributeur, commandes en plus à partir
     * d'un rang, commandes si la Veillée échoue, icône du menu.
     */
    public record Palier(int pourcent, boolean veillee, List<String> serveur, List<String> contributeur,
                         TreeMap<Integer, List<String>> parRang, List<String> echec, ObjetConfig icone) {
    }

    /** Contribution d'un joueur. */
    public static final class Part {
        final UUID uuid;
        volatile String pseudo;
        volatile long total;
        volatile LocalDate jour;
        /** Points du jour (automatiques et offrandes). */
        volatile int partJour;
        /** Flocons offerts aujourd'hui (plafond des offrandes). */
        volatile int offertJour;
        /** Fraction de point automatique pas encore comptée (mémoire seulement). */
        volatile double reste;
        volatile int niveau;

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
    private static final String CLE_VEILLEE = "sapin-veillee-passee";
    private static final Pattern FOIS = Pattern.compile("\\{fois:(\\d+)}");

    private final SHChristmasPresents plugin;
    private final Base base;
    private final int annee;
    private final Map<UUID, Part> parts = new ConcurrentHashMap<>();
    private final CoupDePouce pouce;
    private volatile long somme;
    private volatile int cibleConfig = 95_000;
    private volatile double depart = 10;
    private volatile int floconsParPoint = 5;
    private volatile Set<String> sourcesExclues = Set.of("sapin-palier", "cadeau-recu", "admin");
    private volatile int plafondOffrande = 500;
    /** Points minimaux de chaque rang, du rang 1 (contributeur) au plus haut ; index 0 inutilisé. */
    private volatile int[] seuils = {0, 50};
    private volatile double seuilVeillee = 90;
    /** Fin de la Veillée : jusque-là, le palier de la Veillée part dès que la jauge atteint le seuil. */
    private volatile java.time.LocalDateTime veilleeJusqua;
    private volatile List<String> veilleeAttente = List.of();
    private volatile List<Integer> offrandes = List.of(10, 50, 100, 500);
    private volatile List<Palier> paliers = List.of();
    private volatile List<Part> top = List.of();
    private volatile long topCalcule;
    volatile int lignes = 5;
    volatile int caseJauge = 4;
    volatile int caseExplication = 10;
    volatile int caseRang = 13;
    volatile int caseTop = 16;
    volatile List<Integer> casesPaliers = List.of(18, 19, 20, 21, 22, 23, 24, 25, 26);
    volatile List<Integer> casesOffrandes = List.of(28, 29, 33, 34);
    volatile int caseJour = 31;
    volatile int caseRetour = 40;
    volatile List<String> commandesRetour = List.of("noel");
    volatile Material cadre = Material.BLACK_STAINED_GLASS_PANE;
    volatile Accent accent = Accent.AUCUN;

    public Sapin(SHChristmasPresents plugin, Base base, int annee) {
        this.plugin = plugin;
        this.base = base;
        this.annee = annee;
        this.pouce = new CoupDePouce(plugin, this);
    }

    public void configurer(ConfigurationSection s) {
        if (s == null) {
            return;
        }
        cibleConfig = Math.max(1, s.getInt("cible", 95_000));
        depart = Math.max(0, s.getDouble("depart-pourcent", 10));
        ConfigurationSection auto = s.getConfigurationSection("automatique");
        floconsParPoint = auto == null ? 0 : Math.max(0, auto.getInt("flocons-par-point", 5));
        sourcesExclues = auto == null ? Set.of() : Set.copyOf(LectureConfig.textes(auto, "sources-exclues"));
        ConfigurationSection off = s.getConfigurationSection("offrandes");
        if (off != null) {
            plafondOffrande = Math.max(0, off.getInt("plafond-jour", 500));
            List<Integer> lues = LectureConfig.entiers(off, "boutons");
            offrandes = lues.isEmpty() ? List.of(10, 50, 100, 500) : List.copyOf(lues);
        } else {
            // format 2.1 : plafond-joueur-jour et offrandes au premier niveau
            plafondOffrande = Math.max(0, s.getInt("plafond-joueur-jour", 600));
            List<Integer> lues = LectureConfig.entiers(s, "offrandes");
            offrandes = lues.isEmpty() ? List.of(10, 50, 100, 500) : List.copyOf(lues);
        }
        ConfigurationSection r = s.getConfigurationSection("rangs");
        List<Integer> lus = new ArrayList<>();
        if (r != null) {
            for (int k = 1; r.contains(Integer.toString(k)); k++) {
                lus.add(Math.max(1, r.getInt(Integer.toString(k))));
            }
        }
        if (lus.isEmpty()) {
            lus.add(Math.max(1, s.getInt("contribution-min", 50)));
        }
        int[] nouveaux = new int[lus.size() + 1];
        for (int k = 0; k < lus.size(); k++) {
            nouveaux[k + 1] = Math.max(lus.get(k), k > 0 ? nouveaux[k] + 1 : 1);
        }
        seuils = nouveaux;
        seuilVeillee = s.getDouble("seuil-veillee", 90);
        try {
            veilleeJusqua = s.contains("veillee-jusqua") ? java.time.LocalDateTime.parse(s.getString("veillee-jusqua", "")) : null;
        } catch (java.time.format.DateTimeParseException e) {
            plugin.getLogger().warning("sapin.veillee-jusqua illisible : " + s.getString("veillee-jusqua"));
            veilleeJusqua = null;
        }
        veilleeAttente = LectureConfig.textes(s, "commandes-attente-veillee");
        List<Palier> liste = new ArrayList<>();
        ConfigurationSection p = s.getConfigurationSection("paliers");
        if (p != null) {
            for (String cle : p.getKeys(false)) {
                ConfigurationSection palier = p.getConfigurationSection(cle);
                try {
                    TreeMap<Integer, List<String>> parRang = new TreeMap<>();
                    ConfigurationSection pr = palier == null ? null : palier.getConfigurationSection("commandes-rang");
                    if (pr != null) {
                        for (String rang : pr.getKeys(false)) {
                            parRang.put(Integer.parseInt(rang.trim()), LectureConfig.textes(pr, rang));
                        }
                    }
                    liste.add(new Palier(Integer.parseInt(cle.trim()), palier != null && palier.getBoolean("veillee", false),
                            LectureConfig.textes(palier, "commandes-serveur"), LectureConfig.textes(palier, "commandes-contributeur"),
                            parRang, LectureConfig.textes(palier, "commandes-echec"),
                            ObjetConfig.lire(palier == null ? null : palier.getConfigurationSection("icone"),
                                    ObjetConfig.simple(Material.SPRUCE_SAPLING))));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("sapin.paliers." + cle + " : pourcentage ou rang illisible");
                }
            }
        }
        liste.sort(Comparator.comparingInt(Palier::pourcent));
        paliers = List.copyOf(liste);
        pouce.configurer(s.getConfigurationSection("coup-de-pouce"));
        ConfigurationSection menu = s.getConfigurationSection("menu");
        if (menu != null) {
            lignes = menu.getInt("lignes", 5);
            caseJauge = menu.getInt("case-jauge", 4);
            caseExplication = menu.getInt("case-explication", 10);
            caseRang = menu.getInt("case-rang", 13);
            caseTop = menu.getInt("case-top", 16);
            List<Integer> cp = LectureConfig.entiers(menu, "cases-paliers");
            casesPaliers = cp.isEmpty() ? List.of(18, 19, 20, 21, 22, 23, 24, 25, 26) : List.copyOf(cp);
            List<Integer> co = LectureConfig.entiers(menu, "cases-offrandes");
            casesOffrandes = co.isEmpty() ? List.of(28, 29, 33, 34) : List.copyOf(co);
            caseJour = menu.getInt("case-jour", 31);
            caseRetour = menu.getInt("case-retour", 40);
            List<String> retour = LectureConfig.textes(menu, "commandes-retour");
            commandesRetour = retour.isEmpty() ? List.of() : List.copyOf(retour);
            Material vitre = Material.matchMaterial(menu.getString("cadre", "BLACK_STAINED_GLASS_PANE"));
            cadre = vitre == null ? Material.AIR : vitre;
            accent = Accent.lire(menu);
        }
        for (Part part : parts.values()) {
            part.niveau = niveauDe(part.total);
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
                    part.niveau = niveauDe(part.total);
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

    public double depart() {
        return depart;
    }

    public double pourcent() {
        return Math.max(0, Math.min(100, depart + 100.0 * somme / cible()));
    }

    /** Points qui manquent pour amener la jauge à ce pourcentage (0 s'il est dépassé). */
    public long manquePour(double pourcent) {
        double besoin = (pourcent - depart) / 100.0 * cible();
        return Math.max(0, (long) Math.ceil(besoin - somme));
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

    /** Flocons que le joueur peut encore offrir aujourd'hui. */
    public int resteOffrandeJour(UUID uuid, LocalDate jour) {
        return plafondOffrande <= 0 ? Integer.MAX_VALUE : Math.max(0, plafondOffrande - offertJour(uuid, jour));
    }

    public int plafondOffrande() {
        return plafondOffrande;
    }

    public int floconsParPoint() {
        return floconsParPoint;
    }

    /** Rang pour un total de points : 0 (pas contributeur) à rangMax(). */
    public int niveauDe(long total) {
        int[] s = seuils;
        int n = 0;
        for (int k = 1; k < s.length; k++) {
            if (total >= s[k]) {
                n = k;
            }
        }
        return n;
    }

    public int niveau(UUID uuid) {
        return niveauDe(contribution(uuid));
    }

    public int rangMax() {
        return seuils.length - 1;
    }

    /** Points minimaux du rang k (1 = contributeur). */
    public int seuil(int k) {
        int[] s = seuils;
        return k >= 1 && k < s.length ? s[k] : Integer.MAX_VALUE;
    }

    public boolean contributeur(UUID uuid) {
        return contribution(uuid) >= seuil(1);
    }

    public int contributeurs() {
        int n = 0;
        int min = seuil(1);
        for (Part part : parts.values()) {
            if (part.total >= min) {
                n++;
            }
        }
        return n;
    }

    /** Nombre de contributeurs à chaque rang (index = rang). */
    public int[] parRang() {
        int[] n = new int[rangMax() + 1];
        for (Part part : parts.values()) {
            n[niveauDe(part.total)]++;
        }
        return n;
    }

    /** Place parmi les contributeurs (1 = le plus gros), 0 si pas contributeur. */
    public int place(UUID uuid) {
        long mien = contribution(uuid);
        if (mien < seuil(1)) {
            return 0;
        }
        int place = 1;
        for (Part part : parts.values()) {
            if (part.total > mien) {
                place++;
            }
        }
        return place;
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

    public CoupDePouce pouce() {
        return pouce;
    }

    public double seuilVeillee() {
        return seuilVeillee;
    }

    public boolean palierFait(Palier palier) {
        return plugin.actions().traitee(idPalier(palier));
    }

    /** Pourcentage du prochain palier pas encore parti, null s'il n'y en a plus. */
    public Integer palierSuivant() {
        Palier p = prochain();
        return p == null ? null : p.pourcent();
    }

    /** Prochain palier pas encore parti, null s'il n'y en a plus. */
    public Palier prochain() {
        for (Palier palier : paliers) {
            if (!palierFait(palier)) {
                return palier;
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

    public Palier palierVeillee() {
        Palier v = null;
        for (Palier p : paliers) {
            if (p.veillee()) {
                v = p;
            }
        }
        return v;
    }

    /** Vrai une fois la Veillée passée (palier lancé ou refusé à l'heure) : le sapin ne prend plus rien. */
    public boolean veilleePassee() {
        return plugin.etat().get(CLE_VEILLEE) != null;
    }

    /** Vrai si le sapin prend des points maintenant (phase de gains, Veillée pas encore passée). */
    public boolean ouvert() {
        return plugin.dates().gains(plugin.horloge().maintenant()) && !veilleePassee();
    }

    /** Sapin plein (jauge à 100 %). */
    public boolean complet() {
        return pourcent() >= 100;
    }

    /** Nom du décor d'un palier (messages.yml sapin.paliers.&lt;pourcent&gt;.nom). */
    public String nomPalier(Palier palier) {
        return plugin.messages().brut("sapin.paliers." + palier.pourcent() + ".nom");
    }

    /**
     * Texte du cadeau d'un palier pour un rang : liste (une ligne par rang, la dernière pour les rangs au-delà) ou texte
     * avec {fois:N}. Vide si le palier n'a pas de cadeau décrit.
     */
    public String cadeauPalier(Palier palier, int rang) {
        String cle = "sapin.paliers." + palier.pourcent() + ".cadeau";
        var m = plugin.messages();
        if (!m.existe(cle)) {
            return "";
        }
        List<String> l = m.lignes(cle);
        if (l.isEmpty()) {
            return "";
        }
        int r = Math.max(1, rang);
        String texte = l.size() > 1 ? l.get(Math.min(r, l.size()) - 1) : l.getFirst();
        return fois(texte, r);
    }

    /** Remplace {fois:N} par N × multiplicateur. */
    public static String fois(String texte, int multiplicateur) {
        Matcher m = FOIS.matcher(texte);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Long.toString(Long.parseLong(m.group(1)) * multiplicateur));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Étoiles d'un rang (messages.yml sapin.rangs.&lt;k&gt;), vide pour 0. */
    public String etoiles(int rang) {
        return rang <= 0 ? "" : plugin.messages().brut("sapin.rangs." + rang);
    }

    // ---- écriture

    /**
     * Ajoute des points à la part d'un joueur (fil principal), sans plafond : le plafond du jour porte sur les Flocons
     * offerts (offrir). {@code plafonne} est gardé pour les appels 2.1 et n'a plus d'effet.
     *
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
        long avant = part.total;
        part.total = Math.max(0, avant + points);
        part.partJour = (int) Math.max(0, part.partJour + (part.total - avant));
        somme += part.total - avant;
        sauver(part);
        int ancien = part.niveau;
        part.niveau = niveauDe(part.total);
        if (part.niveau > ancien) {
            monteeDeRang(uuid, part.niveau);
        }
        verifierPaliers();
        return (int) (part.total - avant);
    }

    private void monteeDeRang(UUID uuid, int rang) {
        Player joueur = Bukkit.getPlayer(uuid);
        if (joueur == null) {
            return;
        }
        String cle = rang >= rangMax() ? "sapin.rang-max" : "sapin.rang-atteint";
        plugin.messages().envoyer(joueur, cle, "{rang}", etoiles(rang), "{multiplicateur}", rang,
                "{suivant}", etoiles(rang + 1), "{reste}", Textes.nombre(Math.max(0, seuil(rang + 1) - contribution(uuid))));
        plugin.son("rang", joueur);
    }

    /**
     * Points automatiques d'un gain de Flocons (appelé par Flocons) : 1 point pour flocons-par-point Flocons versés, la
     * fraction est gardée pour le gain suivant.
     */
    public void automatique(UUID uuid, String pseudo, int verse, String source) {
        if (verse <= 0 || floconsParPoint <= 0 || sourcesExclues.contains(source) || veilleePassee()) {
            return;
        }
        Part part = parts.computeIfAbsent(uuid, k -> new Part(uuid, pseudo));
        double exact = part.reste + (double) verse / floconsParPoint;
        int points = (int) Math.floor(exact + 1e-9);
        part.reste = Math.max(0, exact - points);
        if (points > 0) {
            contribuer(uuid, pseudo, points, false);
        }
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

    /** /sapin offrir : retire les Flocons (au plus le reste du jour), compte 1 point par Flocon (× coup de pouce). */
    public void offrir(Player joueur, int demande) {
        var messages = plugin.messages();
        if (veilleePassee()) {
            messages.envoyer(joueur, "sapin.ferme-veillee");
            plugin.son("refus", joueur);
            return;
        }
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
        int reste = resteOffrandeJour(uuid, jour);
        if (reste <= 0) {
            messages.envoyer(joueur, "sapin.part-complete", "{plafond}", Textes.nombre(plafondOffrande));
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
                boolean dejaComplet = complet();
                double facteur = pouce.facteur();
                int points = (int) Math.round(montant * facteur);
                LocalDate jour = plugin.horloge().aujourdhui();
                Part part = parts.computeIfAbsent(uuid, k -> new Part(uuid, joueur.getName()));
                if (!jour.equals(part.jour)) {
                    part.jour = jour;
                    part.partJour = 0;
                    part.offertJour = 0;
                }
                part.offertJour += montant;
                contribuer(uuid, joueur.getName(), points, false);
                plugin.compteurs().ajouter(uuid, "offert", montant);
                plugin.carte().compteurChange(uuid, joueur.getName(), "offert");
                String cle = dejaComplet ? "sapin.offert-complet" : facteur > 1 ? "sapin.offert-pouce" : "sapin.offert";
                messages.envoyer(joueur, cle, "{montant}", Textes.nombre(montant), "{points}", Textes.nombre(points),
                        "{pourcent}", Textes.pourcent(pourcent()), "{facteur}", Textes.facteur(facteur));
                if (montant < demande) {
                    messages.envoyer(joueur, "sapin.offert-partiel", "{plafond}", Textes.nombre(plafondOffrande));
                }
                plugin.son("offrande", joueur);
            }
        }
    }

    /**
     * Paliers atteints qui ne sont pas encore partis. Le palier de la Veillée ne part que pendant sa fenêtre (de la date
     * veillee à veillee-jusqua), dès que la jauge atteint le seuil.
     */
    public void verifierPaliers() {
        double actuel = pourcent();
        for (Palier palier : paliers) {
            if (palierFait(palier)) {
                continue;
            }
            if (!palier.veillee() && actuel >= palier.pourcent()) {
                declencher(palier, null);
            } else if (palier.veillee() && actuel >= seuilVeillee && fenetreVeillee(plugin.horloge().maintenant())
                    && !veilleePassee()) {
                plugin.etat().set(CLE_VEILLEE, plugin.horloge().local().toString());
                pouce.arreter(true);
                declencher(palier, null);
            }
        }
    }

    /** Vrai pendant la Veillée : de la date veillee (dates:) jusqu'à sapin.veillee-jusqua (ou sans fin). */
    public boolean fenetreVeillee(ZonedDateTime maintenant) {
        if (!plugin.dates().connu("veillee") || maintenant.isBefore(plugin.dates().get("veillee"))) {
            return false;
        }
        return veilleeJusqua == null || maintenant.toLocalDateTime().isBefore(veilleeJusqua);
    }

    /** Fin de la fenêtre de la Veillée, null sans fin réglée. */
    public java.time.LocalDateTime veilleeJusqua() {
        return veilleeJusqua;
    }

    /**
     * /shchristmaspresents sapin veillee : le palier de la veillée part si la jauge est au seuil, sinon ses
     * commandes-echec. À l'heure de la Veillée (ou après), le sapin se ferme dans les deux cas.
     */
    public void veillee(CommandSender auteur) {
        Palier palier = palierVeillee();
        if (palier == null) {
            plugin.messages().envoyer(auteur, "admin.sapin-veillee-absent");
            return;
        }
        if (palierFait(palier)) {
            plugin.messages().envoyer(auteur, "admin.sapin-veillee-fait");
            return;
        }
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        ZonedDateTime heure = plugin.dates().connu("veillee") ? plugin.dates().get("veillee") : null;
        boolean aLHeure = heure == null || !maintenant.isBefore(heure.minusMinutes(5));
        double actuel = pourcent();
        if (actuel < seuilVeillee) {
            plugin.messages().envoyer(auteur, "admin.sapin-veillee-refus", "{pourcent}", Textes.pourcent(actuel),
                    "{seuil}", Textes.facteur(seuilVeillee));
            plugin.getLogger().info("Veillée : jauge à " + Textes.pourcent(actuel) + " %, sous le seuil de " + seuilVeillee + " %");
            if (!aLHeure) {
                return;
            }
            if (veilleeJusqua != null && maintenant.toLocalDateTime().isBefore(veilleeJusqua)) {
                // la Veillée reste ouverte : le palier part tout seul si le seuil est atteint avant veillee-jusqua
                Console.executer(plugin.getLogger(), veilleeAttente, "{pourcent}", (int) Math.floor(actuel),
                        "{seuil}", Textes.facteur(seuilVeillee), "{manque}", Textes.nombre(manquePour(seuilVeillee)),
                        "{jusqua}", Textes.heure(veilleeJusqua.toLocalTime()));
                return;
            }
            echecVeillee(palier, actuel);
            return;
        }
        if (aLHeure) {
            plugin.etat().set(CLE_VEILLEE, maintenant.toLocalDateTime().toString());
            pouce.arreter(true);
        }
        declencher(palier, auteur);
    }

    private void echecVeillee(Palier palier, double actuel) {
        if (veilleePassee()) {
            return;
        }
        plugin.etat().set(CLE_VEILLEE, plugin.horloge().local().toString());
        pouce.arreter(true);
        plugin.getLogger().info("Veillée : terminée sans le palier, jauge à " + Textes.pourcent(actuel) + " %");
        Console.executer(plugin.getLogger(), palier.echec(), "{pourcent}", (int) Math.floor(actuel),
                "{seuil}", Textes.facteur(seuilVeillee));
    }

    private void declencher(Palier palier, CommandSender auteur) {
        plugin.actions().reclamer(idPalier(palier), "fait", null, reserve -> {
            if (!reserve) {
                return;
            }
            String pourcent = Integer.toString(palier.pourcent());
            int serveur = Console.executer(plugin.getLogger(), palier.serveur(), "{palier}", pourcent,
                    "{contributeurs}", contributeurs(), "{top1}", topNom(0), "{top2}", topNom(1), "{top3}", topNom(2));
            int enLigne = 0;
            int plusTard = 0;
            int min = seuil(1);
            for (Part part : parts.values()) {
                if (part.total < min) {
                    continue;
                }
                int rang = niveauDe(part.total);
                Player joueur = Bukkit.getPlayer(part.uuid);
                if (joueur != null) {
                    recompenser(joueur, palier, rang);
                    enLigne++;
                } else {
                    plugin.livraisons().ajouter(part.uuid, "sapin-palier", pourcent + ":" + rang);
                    plusTard++;
                }
            }
            if (palier.pourcent() >= 100 || pourcent() >= 100) {
                pouce.arreter(true);
            }
            plugin.getLogger().info("Sapin : palier " + pourcent + " % atteint (" + serveur + " commande(s) serveur, "
                    + enLigne + " contributeur(s) en ligne, " + plusTard + " à leur connexion)");
            if (auteur != null) {
                plugin.messages().envoyer(auteur, "admin.sapin-palier", "{palier}", pourcent, "{en-ligne}", enLigne, "{plus-tard}", plusTard);
            }
        });
    }

    private String topNom(int i) {
        List<Part> t = top();
        return i < t.size() ? t.get(i).pseudo() : "-";
    }

    /** Cadeau d'un palier pour un joueur en ligne, selon son rang au moment du palier (aussi à la livraison). */
    public void recompenser(Player joueur, Palier palier, int rang) {
        int r = Math.max(1, rang);
        List<String> commandes = new ArrayList<>(palier.contributeur());
        for (Map.Entry<Integer, List<String>> e : palier.parRang().entrySet()) {
            if (r >= e.getKey()) {
                commandes.addAll(e.getValue());
            }
        }
        List<String> finales = new ArrayList<>();
        for (String c : commandes) {
            finales.add(fois(c, r));
        }
        Console.executer(plugin.getLogger(), finales, "{joueur}", joueur.getName(), "{uuid}", joueur.getUniqueId().toString(),
                "{palier}", Integer.toString(palier.pourcent()), "{rang}", etoiles(r), "{multiplicateur}", r);
        String cadeau = cadeauPalier(palier, r);
        if (!cadeau.isEmpty()) {
            plugin.messages().envoyer(joueur, "sapin.palier-recu", "{palier}", palier.pourcent(), "{nom}", nomPalier(palier),
                    "{cadeau}", cadeau, "{rang}", etoiles(r));
        }
    }

    /** Livraison d'un palier à la connexion : référence « pourcent:rang » (2.2) ou « pourcent » (2.1, rang actuel). */
    public void livrer(Player joueur, String ref) {
        String[] parties = ref.split(":", 2);
        Palier palier = palier(parties[0]);
        if (palier == null) {
            return;
        }
        int rang;
        try {
            rang = parties.length > 1 ? Integer.parseInt(parties[1]) : niveau(joueur.getUniqueId());
        } catch (NumberFormatException e) {
            rang = niveau(joueur.getUniqueId());
        }
        recompenser(joueur, palier, rang);
    }

    /** /shchristmaspresents sapin cible : 0 revient à la cible de config.yml. */
    public void reglerCible(int cible) {
        plugin.etat().set(CLE_CIBLE, cible > 0 ? Integer.toString(cible) : null);
        verifierPaliers();
    }

    /** Toutes les 5 secondes (fil principal) : relevés de la jauge, coup de pouce, fin de la Veillée. */
    public void tic(ZonedDateTime maintenant) {
        pouce.tic(maintenant);
        Palier v = palierVeillee();
        if (v == null || palierFait(v) || veilleePassee() || !plugin.dates().connu("veillee")
                || maintenant.isBefore(plugin.dates().get("veillee"))) {
            return;
        }
        if (pourcent() >= seuilVeillee && fenetreVeillee(maintenant)) {
            verifierPaliers();
        } else if (veilleeJusqua != null && !maintenant.toLocalDateTime().isBefore(veilleeJusqua)) {
            echecVeillee(v, pourcent());
        }
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
        int[] n = parRang();
        StringBuilder rangs = new StringBuilder();
        for (int k = 1; k < n.length; k++) {
            rangs.append(k > 1 ? " · " : "").append(etoiles(k)).append(' ').append(n[k]);
        }
        Double prevision = pouce.prevision(plugin.horloge().maintenant());
        plugin.messages().envoyer(cible, "admin.sapin-info", "{pourcent}", Textes.pourcent(pourcent()),
                "{points}", Textes.nombre(somme), "{cible}", Textes.nombre(cible()), "{origine}",
                plugin.messages().brut(cibleReglee() ? "admin.sapin-cible-commande" : "admin.sapin-cible-config"),
                "{depart}", Textes.facteur(depart), "{contributeurs}", contributeurs(),
                "{paliers}", faits.isEmpty() ? "-" : faits.toString(), "{rangs}", rangs.toString(),
                "{prevision}", prevision == null ? "-" : Textes.pourcent(Math.min(999, prevision)),
                "{pouce}", pouce.actif() ? "×" + Textes.facteur(pouce.facteurConfig()) : "-",
                "{veillee}", veilleePassee() ? plugin.messages().brut("admin.sapin-veillee-passee") : "-");
    }

    /** Ordre des paliers pour les menus : pourcentage → palier. */
    public Map<Integer, Palier> parPourcent() {
        Map<Integer, Palier> m = new LinkedHashMap<>();
        for (Palier p : paliers) {
            m.put(p.pourcent(), p);
        }
        return m;
    }

    /** Joueurs (uuid) qui ont une part : pour le bilan. */
    public Set<UUID> participants() {
        return new HashSet<>(parts.keySet());
    }
}
