package org.Cry_Legende.sHChristmasPresents.calendrier;

/** État d'une porte pour un joueur. */
public enum EtatPorte {
    A_VENIR("a-venir"), OUVRABLE("ouvrable"), RATTRAPABLE("rattrapable"), OUVERTE("ouverte"), MANQUEE("manquee");

    private final String cle;

    EtatPorte(String cle) {
        this.cle = cle;
    }

    /** Clé de config et de messages.yml. */
    public String cle() {
        return cle;
    }
}
