package org.Cry_Legende.sHChristmasPresents.commun;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Lecture des dates et heures de config.yml. Écrites entre guillemets, ce sont des textes ; sans guillemets, le YAML
 * en fait des dates (en UTC, sans décalage) ou des nombres (« 18:00 » = 1080) : les deux sont repris tels qu'écrits.
 */
public final class LectureConfig {

    private LectureConfig() {
    }

    /** « 2026-12-01T00:00 », « 2026-12-01 00:00:00 »… ; null si absent ou illisible. */
    public static LocalDateTime dateHeure(Object valeur) {
        if (valeur instanceof Date date) {
            return date.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
        }
        if (valeur == null) {
            return null;
        }
        String texte = valeur.toString().trim().replace(' ', 'T');
        if (texte.isEmpty()) {
            return null;
        }
        try {
            if (texte.length() == 10) {
                return LocalDate.parse(texte).atStartOfDay();
            }
            return LocalDateTime.parse(texte);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static LocalDate date(Object valeur) {
        LocalDateTime dateHeure = dateHeure(valeur);
        return dateHeure == null ? null : dateHeure.toLocalDate();
    }

    /** « 18:00 », ou le nombre que le YAML tire de 18:00 sans guillemets. */
    public static LocalTime heure(Object valeur) {
        if (valeur instanceof Number nombre) {
            int minutes = nombre.intValue();
            return LocalTime.of(Math.floorMod(minutes / 60, 24), Math.floorMod(minutes, 60));
        }
        if (valeur == null) {
            return null;
        }
        try {
            return LocalTime.parse(valeur.toString().trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Liste de textes : liste YAML ou texte seul. */
    public static List<String> textes(ConfigurationSection section, String cle) {
        if (section == null) {
            return List.of();
        }
        if (section.isList(cle)) {
            return new ArrayList<>(section.getStringList(cle));
        }
        String seul = section.getString(cle);
        return seul == null || seul.isEmpty() ? List.of() : List.of(seul);
    }

    /** Liste d'entiers (cases de menu…). */
    public static List<Integer> entiers(ConfigurationSection section, String cle) {
        List<Integer> out = new ArrayList<>();
        if (section == null) {
            return out;
        }
        for (Object objet : section.getList(cle, List.of())) {
            if (objet instanceof Number nombre) {
                out.add(nombre.intValue());
            } else if (objet != null) {
                try {
                    out.add(Integer.parseInt(objet.toString().trim()));
                } catch (NumberFormatException ignored) {
                    // case illisible : ignorée
                }
            }
        }
        return out;
    }
}
