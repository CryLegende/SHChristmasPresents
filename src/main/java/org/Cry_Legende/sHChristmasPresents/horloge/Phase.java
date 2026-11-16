package org.Cry_Legende.sHChristmasPresents.horloge;

import java.util.Locale;

/** Phases de l'évènement, dans l'ordre. */
public enum Phase {
    AVANT, AVENT, VILLAGE, EMPLETTES, CLOTURE, APRES;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
