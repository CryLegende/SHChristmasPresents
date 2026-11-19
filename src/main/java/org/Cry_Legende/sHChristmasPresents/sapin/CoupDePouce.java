package org.Cry_Legende.sHChristmasPresents.sapin;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Relevés de la jauge et coup de pouce du Grand Sapin.
 * <p>
 * Chaque jour de gains, à {@code heure}, la jauge est relevée (shxmas_etat, clé sapin-releves). À partir du jour
 * {@code du}, à la même heure, la prévision pour la Veillée est calculée au rythme des trois derniers jours : si elle
 * n'atteint pas 100 %, les offrandes comptent {@code facteur} fois jusqu'au lendemain même heure (commandes-debut la
 * première fois, commandes-suite les jours suivants), sinon le coup de pouce s'arrête (commandes-fin). Il s'arrête
 * aussi quand le sapin est complet ou que la Veillée est passée. L'état survit aux redémarrages.
 */
public final class CoupDePouce {

    private static final String CLE_RELEVES = "sapin-releves";
    private static final String CLE_JUSQUA = "sapin-pouce-jusqua";
    private static final String CLE_DECISION = "sapin-pouce-decision";

    private final SHChristmasPresents plugin;
    private final Sapin sapin;
    private volatile boolean configure;
    private volatile LocalDate du;
    private volatile LocalTime heure = LocalTime.of(18, 0);
    private volatile double facteur = 2;
    private volatile List<String> debut = List.of();
    private volatile List<String> suite = List.of();
    private volatile List<String> fin = List.of();

    CoupDePouce(SHChristmasPresents plugin, Sapin sapin) {
        this.plugin = plugin;
        this.sapin = sapin;
    }

    void configurer(ConfigurationSection s) {
        configure = s != null;
        if (s == null) {
            return;
        }
        try {
            du = s.contains("du") ? LocalDate.parse(s.getString("du", "")) : null;
        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("sapin.coup-de-pouce.du illisible : " + s.getString("du"));
            du = null;
        }
        try {
            heure = LocalTime.parse(s.getString("heure", "18:00"));
        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("sapin.coup-de-pouce.heure illisible : " + s.getString("heure"));
            heure = LocalTime.of(18, 0);
        }
        facteur = Math.max(1, s.getDouble("facteur", 2));
        debut = LectureConfig.textes(s, "commandes-debut");
        suite = LectureConfig.textes(s, "commandes-suite");
        fin = LectureConfig.textes(s, "commandes-fin");
    }

    // ---- état

    private LocalDateTime jusqua() {
        String v = plugin.etat().get(CLE_JUSQUA);
        if (v == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Vrai si le coup de pouce est en cours. */
    public boolean actif() {
        LocalDateTime j = jusqua();
        return j != null && plugin.horloge().local().isBefore(j);
    }

    /** Facteur appliqué maintenant aux offrandes (1 hors coup de pouce). */
    public double facteur() {
        return actif() ? facteur : 1;
    }

    public double facteurConfig() {
        return facteur;
    }

    /** Fin du coup de pouce en cours, null s'il n'y en a pas. */
    public LocalDateTime fin() {
        return actif() ? jusqua() : null;
    }

    private TreeMap<LocalDate, Long> releves() {
        TreeMap<LocalDate, Long> out = new TreeMap<>();
        String v = plugin.etat().get(CLE_RELEVES);
        if (v == null || v.isBlank()) {
            return out;
        }
        for (String paire : v.split(";")) {
            String[] kv = paire.split("=", 2);
            if (kv.length == 2) {
                try {
                    out.put(LocalDate.parse(kv[0]), Long.parseLong(kv[1]));
                } catch (RuntimeException ignored) {
                    // relevé illisible
                }
            }
        }
        return out;
    }

    private void sauverReleves(TreeMap<LocalDate, Long> releves) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<LocalDate, Long> e : releves.entrySet()) {
            b.append(b.isEmpty() ? "" : ";").append(e.getKey()).append('=').append(e.getValue());
        }
        plugin.etat().set(CLE_RELEVES, b.toString());
    }

    /**
     * Jauge prévue à l'heure de la Veillée au rythme des trois derniers jours (ou depuis le plus vieux relevé d'au moins
     * 20 heures), null sans relevé assez ancien ou sans date de Veillée. Peut dépasser 100.
     */
    public Double prevision(ZonedDateTime maintenant) {
        if (!plugin.dates().connu("veillee")) {
            return null;
        }
        LocalDateTime local = maintenant.toLocalDateTime();
        LocalDateTime veillee = plugin.dates().get("veillee").toLocalDateTime();
        TreeMap<LocalDate, Long> releves = releves();
        // le dernier relevé d'il y a trois jours ou plus ; à défaut, le plus ancien d'au moins 20 heures
        Map.Entry<LocalDate, Long> ref = null;
        LocalDate visee = local.toLocalDate().minusDays(3);
        for (Map.Entry<LocalDate, Long> e : releves.entrySet()) {
            if (Duration.between(e.getKey().atTime(heure), local).toHours() < 20) {
                continue;
            }
            if (e.getKey().isAfter(visee)) {
                if (ref == null) {
                    ref = e;
                }
                break;
            }
            ref = e;
        }
        if (ref == null) {
            return null;
        }
        double heures = Duration.between(ref.getKey().atTime(heure), local).toMinutes() / 60.0;
        if (heures <= 0) {
            return null;
        }
        double rythme = (sapin.points() - ref.getValue()) / heures;
        double reste = Math.max(0, Duration.between(local, veillee).toMinutes() / 60.0);
        double prevu = sapin.points() + rythme * reste;
        return sapin.depart() + 100.0 * prevu / sapin.cible();
    }

    // ---- tic

    void tic(ZonedDateTime maintenant) {
        if (!configure || !plugin.dates().gains(maintenant) || sapin.veilleePassee()) {
            arreterSiExpire(maintenant);
            return;
        }
        LocalDateTime local = maintenant.toLocalDateTime();
        LocalDate jour = local.toLocalDate();
        boolean apresHeure = !local.toLocalTime().isBefore(heure);
        if (apresHeure) {
            TreeMap<LocalDate, Long> releves = releves();
            if (!releves.containsKey(jour)) {
                releves.put(jour, sapin.points());
                sauverReleves(releves);
                plugin.debug("Sapin : relevé du " + jour + " à " + sapin.points() + " points");
            }
        }
        boolean veilleeAVenir = plugin.dates().connu("veillee") && maintenant.isBefore(plugin.dates().get("veillee"));
        // la prévision vise l'heure de la Veillée : après, plus de nouvelle décision
        if (du != null && apresHeure && !jour.isBefore(du) && veilleeAVenir && !jour.toString().equals(plugin.etat().get(CLE_DECISION))) {
            plugin.etat().set(CLE_DECISION, jour.toString());
            decider(maintenant);
        }
        arreterSiExpire(maintenant);
        if (actif() && sapin.complet()) {
            arreter(true);
        }
    }

    private void decider(ZonedDateTime maintenant) {
        if (sapin.complet()) {
            plugin.getLogger().info("Sapin : complet, pas de coup de pouce");
            arreter(true);
            return;
        }
        Double prevision = prevision(maintenant);
        String texte = prevision == null ? "?" : Textes.pourcent(prevision);
        if (prevision != null && prevision >= 100) {
            plugin.getLogger().info("Sapin : prévision " + texte + " % pour la Veillée, pas de coup de pouce");
            if (actif()) {
                arreter(false);
            }
            return;
        }
        boolean etaitActif = actif();
        LocalDateTime jusqua = maintenant.toLocalDateTime().toLocalDate().plusDays(1).atTime(heure);
        LocalDateTime borne = sapin.veilleeJusqua() != null ? sapin.veilleeJusqua() : plugin.dates().get("veillee").toLocalDateTime();
        if (jusqua.isAfter(borne)) {
            jusqua = borne;
        }
        plugin.etat().set(CLE_JUSQUA, jusqua.toString());
        plugin.getLogger().info("Sapin : prévision " + texte + " % pour la Veillée, offrandes ×" + Textes.facteur(facteur)
                + " jusqu'au " + jusqua);
        Console.executer(plugin.getLogger(), etaitActif && !suite.isEmpty() ? suite : debut,
                "{prevision}", prevision == null ? "?" : Integer.toString((int) Math.floor(Math.min(100, prevision))),
                "{pourcent}", (int) Math.floor(sapin.pourcent()), "{facteur}", Textes.facteur(facteur),
                "{jusqua}", Textes.heure(jusqua.toLocalTime()));
    }

    private void arreterSiExpire(ZonedDateTime maintenant) {
        LocalDateTime j = jusqua();
        if (j != null && !maintenant.toLocalDateTime().isBefore(j)) {
            arreter(false);
        }
    }

    /**
     * Arrête le coup de pouce s'il y en a un (commandes-fin, sauf {@code silencieux} : sapin complet, déjà annoncé
     * par son palier).
     */
    public void arreter(boolean silencieux) {
        if (plugin.etat().get(CLE_JUSQUA) == null) {
            return;
        }
        plugin.etat().set(CLE_JUSQUA, null);
        plugin.getLogger().info("Sapin : fin du coup de pouce");
        if (!silencieux) {
            Console.executer(plugin.getLogger(), fin, "{pourcent}", (int) Math.floor(sapin.pourcent()));
        }
    }
}
