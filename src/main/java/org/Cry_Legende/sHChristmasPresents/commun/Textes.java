package org.Cry_Legende.sHChristmasPresents.commun;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Mise en forme des nombres, durées et dates pour les joueurs (français, heure de Paris). */
public final class Textes {

    /** Espace fine insécable des milliers (« 1 250 »). */
    public static final char ESPACE_FINE = ' ';

    private static final String[] MOIS = {"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août",
            "septembre", "octobre", "novembre", "décembre"};

    private Textes() {
    }

    /** 1250 → « 1 250 » (espace fine). */
    public static String nombre(long n) {
        String chiffres = Long.toString(Math.abs(n));
        StringBuilder out = new StringBuilder(chiffres.length() + 4);
        int longueur = chiffres.length();
        for (int i = 0; i < longueur; i++) {
            if (i > 0 && (longueur - i) % 3 == 0) {
                out.append(ESPACE_FINE);
            }
            out.append(chiffres.charAt(i));
        }
        return (n < 0 ? "-" : "") + out;
    }

    /** Temps restant : « 4 j 3 h », « 3 h 12 min », « 12 min », « moins d'1 min ». */
    public static String duree(Duration duree) {
        long secondes = Math.max(0, duree.getSeconds());
        long jours = secondes / 86_400;
        long heures = secondes % 86_400 / 3_600;
        long minutes = secondes % 3_600 / 60;
        if (jours > 0) {
            return heures > 0 ? jours + " j " + heures + " h" : jours + " j";
        }
        if (heures > 0) {
            return minutes > 0 ? heures + " h " + minutes + " min" : heures + " h";
        }
        if (minutes > 0) {
            return minutes + " min";
        }
        return "moins d'1 min";
    }

    /**
     * Pourcentage avec une décimale et un point (« 37.5 »), tronqué : la jauge n'affiche jamais un seuil qu'elle n'a pas
     * atteint (89.96 donne 89.9, pas 90.0).
     */
    public static String pourcent(double valeur) {
        return String.format(Locale.ROOT, "%.1f", Math.floor(valeur * 10 + 1e-9) / 10.0);
    }

    /** Multiplicateur : « 2 », « 1.5 ». */
    public static String facteur(double facteur) {
        if (Math.abs(facteur - Math.rint(facteur)) < 1e-9) {
            return Long.toString(Math.round(facteur));
        }
        String texte = String.format(Locale.ROOT, "%.2f", facteur);
        while (texte.endsWith("0")) {
            texte = texte.substring(0, texte.length() - 1);
        }
        return texte.endsWith(".") ? texte.substring(0, texte.length() - 1) : texte;
    }

    /** « 1er décembre », « 12 décembre ». */
    public static String jour(LocalDate date) {
        int numero = date.getDayOfMonth();
        return (numero == 1 ? "1er" : Integer.toString(numero)) + " " + MOIS[date.getMonthValue() - 1];
    }

    /** « 20 h », « 20 h 30 ». */
    public static String heure(LocalTime heure) {
        return heure.getMinute() == 0 ? heure.getHour() + " h" : heure.getHour() + " h " + String.format("%02d", heure.getMinute());
    }

    /** « 12 décembre à 20 h », ou « 1er décembre » à minuit. */
    public static String date(ZonedDateTime date) {
        LocalTime heure = date.toLocalTime().withSecond(0).withNano(0);
        if (heure.equals(LocalTime.MIDNIGHT)) {
            return jour(date.toLocalDate());
        }
        return jour(date.toLocalDate()) + " à " + heure(heure);
    }
}
