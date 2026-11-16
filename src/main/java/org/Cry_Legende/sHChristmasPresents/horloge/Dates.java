package org.Cry_Legende.sHChristmasPresents.horloge;

import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Repères datés (dates: dans config.yml) et ce qui en découle : phase courante, gains ouverts, boutique ouverte.
 */
public final class Dates {

    public static final List<String> REPERES = List.of("avent", "village", "veillee", "fin-gains", "emplettes", "cloture", "apres");

    private static final Map<String, String> DEFAUTS = Map.of(
            "avent", "2026-12-01T00:00",
            "village", "2026-12-12T20:00",
            "veillee", "2026-12-24T20:30",
            "fin-gains", "2026-12-31T23:59:59",
            "emplettes", "2027-01-01T00:00",
            "cloture", "2027-01-04T06:00",
            "apres", "2027-02-01T00:00");

    private final Map<String, ZonedDateTime> reperes;

    private Dates(Map<String, ZonedDateTime> reperes) {
        this.reperes = reperes;
    }

    public static Dates lire(ConfigurationSection section, ZoneId zone, Logger log) {
        Map<String, ZonedDateTime> lus = new LinkedHashMap<>();
        for (String repere : REPERES) {
            Object brut = section == null ? null : section.get(repere);
            LocalDateTime date = LectureConfig.dateHeure(brut);
            if (date == null) {
                if (brut != null) {
                    log.warning("dates." + repere + " illisible (" + brut + "), valeur par défaut " + DEFAUTS.get(repere));
                }
                date = LocalDateTime.parse(DEFAUTS.get(repere));
            }
            lus.put(repere, date.atZone(zone));
        }
        return new Dates(lus);
    }

    public ZonedDateTime get(String repere) {
        return reperes.get(repere);
    }

    public boolean connu(String repere) {
        return reperes.containsKey(repere);
    }

    public Phase phase(ZonedDateTime t) {
        if (t.isBefore(get("avent"))) {
            return Phase.AVANT;
        }
        if (t.isBefore(get("village"))) {
            return Phase.AVENT;
        }
        if (t.isBefore(get("emplettes"))) {
            return Phase.VILLAGE;
        }
        if (t.isBefore(get("cloture"))) {
            return Phase.EMPLETTES;
        }
        if (t.isBefore(get("apres"))) {
            return Phase.CLOTURE;
        }
        return Phase.APRES;
    }

    /** Gains de Flocons : de avent jusqu'à fin-gains. */
    public boolean gains(ZonedDateTime t) {
        return !t.isBefore(get("avent")) && t.isBefore(get("fin-gains"));
    }

    /** Boutique : de village jusqu'à cloture. */
    public boolean boutique(ZonedDateTime t) {
        return !t.isBefore(get("village")) && t.isBefore(get("cloture"));
    }

    /** Offrandes au sapin et cadeaux entre joueurs : de avent jusqu'à cloture. */
    public boolean echanges(ZonedDateTime t) {
        return !t.isBefore(get("avent")) && t.isBefore(get("cloture"));
    }
}
