package org.Cry_Legende.sHChristmasPresents.flocons;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Source de Flocons (sources: dans config.yml).
 *
 * @param plafondJour     Flocons de base comptés plein tarif par jour (0 = pas de plafond)
 * @param auDela          facteur appliqué à la part au-delà du plafond (0 = plus rien)
 * @param multiplicateurs vrai si les multiplicateurs (global, fenêtres, personnel) s'appliquent
 * @param sapinParUnite   points de jauge du sapin par Flocon de base
 * @param sapinFixe       points de jauge du sapin par gain
 */
public record Source(String id, int plafondJour, double auDela, boolean multiplicateurs, double sapinParUnite, int sapinFixe) {

    public static Source lire(String id, ConfigurationSection section) {
        if (section == null) {
            return new Source(id, 0, 0, true, 0, 0);
        }
        return new Source(id,
                Math.max(0, section.getInt("plafond-jour", 0)),
                Math.max(0, section.getDouble("au-dela", 0)),
                section.getBoolean("multiplicateurs", true),
                Math.max(0, section.getDouble("sapin", 0)),
                Math.max(0, section.getInt("sapin-fixe", 0)));
    }

    /** Points de jauge du sapin pour un gain de ce montant de base. */
    public int pointsSapin(int montant) {
        return sapinFixe + (int) Math.round(sapinParUnite * montant);
    }
}
