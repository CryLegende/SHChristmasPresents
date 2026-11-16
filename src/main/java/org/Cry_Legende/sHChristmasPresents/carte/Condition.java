package org.Cry_Legende.sHChristmasPresents.carte;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Condition d'un tampon : « auto: premiere-visite », « compteur: &lt;id&gt; &gt;= N », « niveau-clicker &gt;= N », « commande ».
 */
public sealed interface Condition {

    record PremiereVisite() implements Condition {
    }

    record Compteur(String id, int minimum) implements Condition {
    }

    record NiveauClicker(int minimum) implements Condition {
    }

    record Commande() implements Condition {
    }

    Pattern COMPTEUR = Pattern.compile("compteur\\s*:\\s*([\\w-]+)\\s*>=\\s*(\\d+)");
    Pattern CLICKER = Pattern.compile("niveau-clicker\\s*>=\\s*(\\d+)");

    /** null si illisible. */
    static Condition lire(String texte) {
        if (texte == null) {
            return null;
        }
        String t = texte.trim().toLowerCase(Locale.ROOT);
        if (t.replace(" ", "").equals("auto:premiere-visite")) {
            return new PremiereVisite();
        }
        if (t.equals("commande")) {
            return new Commande();
        }
        Matcher m = COMPTEUR.matcher(t);
        if (m.matches()) {
            return new Compteur(m.group(1), Integer.parseInt(m.group(2)));
        }
        m = CLICKER.matcher(t);
        if (m.matches()) {
            return new NiveauClicker(Integer.parseInt(m.group(1)));
        }
        return null;
    }
}
