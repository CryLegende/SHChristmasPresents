package org.Cry_Legende.sHChristmasPresents.joueurs;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ligne de shxmas_joueurs : ce qui n'arrive qu'une fois par évènement, l'empreinte de la dernière IP et le temps de
 * jeu relevé à la dernière déconnexion. Écrit sur le fil principal, lu de partout.
 */
public final class Profil {

    public final UUID uuid;
    public volatile String pseudo;
    public volatile String empreinte;
    public volatile int tempsJeu;
    public volatile LocalDateTime bienvenue;
    public volatile LocalDateTime carteComplete;
    public volatile LocalDateTime retour;
    public volatile double multiPerso = 1;
    public volatile LocalDateTime multiPersoFin;
    public volatile LocalDateTime recap;
    public volatile LocalDateTime premiereVisite;

    public Profil(UUID uuid, String pseudo) {
        this.uuid = uuid;
        this.pseudo = pseudo;
    }

    /** Multiplicateur personnel en cours, 1 sinon. */
    public double multiplicateur(LocalDateTime maintenant) {
        LocalDateTime fin = multiPersoFin;
        return fin != null && maintenant.isBefore(fin) ? multiPerso : 1;
    }
}
