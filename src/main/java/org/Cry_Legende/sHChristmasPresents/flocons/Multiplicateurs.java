package org.Cry_Legende.sHChristmasPresents.flocons;

import org.Cry_Legende.sHChristmasPresents.horloge.Fenetres;
import org.Cry_Legende.sHChristmasPresents.horloge.Horloge;
import org.Cry_Legende.sHChristmasPresents.joueurs.Profil;
import org.Cry_Legende.sHChristmasPresents.joueurs.Profils;
import org.Cry_Legende.sHChristmasPresents.stockage.Etat;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Multiplicateurs des Flocons : global (commande multiplicateur, gardé en base), fenêtres datées, personnel (retour
 * des absents). Ils se multiplient, plafonnés à multiplicateur-max.
 */
public final class Multiplicateurs {

    private static final String CLE = "multiplicateur-global";

    /** Multiplicateur global en cours : facteur, début et fin (millisecondes de l'horloge de l'évènement). */
    public record Global(double facteur, long debut, long fin) {
    }

    private final Etat etat;
    private final Horloge horloge;
    private final Fenetres fenetres;
    private final Profils profils;
    private volatile Global global;
    private volatile double max = 3;

    public Multiplicateurs(Etat etat, Horloge horloge, Fenetres fenetres, Profils profils) {
        this.etat = etat;
        this.horloge = horloge;
        this.fenetres = fenetres;
        this.profils = profils;
        this.global = lire(etat.get(CLE));
    }

    public void configurer(double max) {
        this.max = max <= 0 ? 3 : max;
    }

    private static Global lire(String texte) {
        if (texte == null) {
            return null;
        }
        try {
            String[] p = texte.split(";");
            return new Global(Double.parseDouble(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Lance un multiplicateur global maintenant pour ces minutes ; facteur 1 ou 0 minute l'arrête. */
    public void lancer(double facteur, int minutes) {
        if (facteur == 1 || minutes <= 0) {
            global = null;
            etat.set(CLE, null);
            return;
        }
        long debut = horloge.millis();
        Global nouveau = new Global(facteur, debut, debut + minutes * 60_000L);
        global = nouveau;
        etat.set(CLE, nouveau.facteur() + ";" + nouveau.debut() + ";" + nouveau.fin());
    }

    public Global global() {
        return global;
    }

    public double facteurGlobal() {
        Global g = global;
        long maintenant = horloge.millis();
        return g != null && maintenant >= g.debut() && maintenant < g.fin() ? g.facteur() : 1;
    }

    /** Multiplicateur total d'un joueur maintenant (global × fenêtres × personnel, plafonné). */
    public double total(UUID joueur) {
        ZonedDateTime maintenant = horloge.maintenant();
        double facteur = facteurGlobal() * fenetres.facteur(joueur, maintenant);
        Profil profil = profils.get(joueur);
        if (profil != null) {
            facteur *= profil.multiplicateur(maintenant.toLocalDateTime());
        }
        return Math.min(max, facteur);
    }

    public double max() {
        return max;
    }
}
