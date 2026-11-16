package org.Cry_Legende.sHChristmasPresents.horloge;

import org.Cry_Legende.sHChristmasPresents.stockage.Etat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Heure de l'évènement, toujours dans le fuseau de la config (la JVM tourne en UTC). L'horloge de test décale l'heure
 * réelle d'un écart fixe : le temps continue de passer, et l'écart survit au redémarrage. Sans
 * test.horloge-autorisee, l'écart est ignoré.
 */
public final class Horloge {

    private static final String CLE = "horloge-decalage";

    private final Etat etat;
    private volatile ZoneId zone = ZoneId.of("Europe/Paris");
    private volatile boolean autorisee;
    private volatile long decalage;

    public Horloge(Etat etat) {
        this.etat = etat;
    }

    public void configurer(ZoneId zone, boolean autorisee) {
        this.zone = zone;
        this.autorisee = autorisee;
        long lu = 0;
        if (autorisee) {
            try {
                String valeur = etat.globale(CLE);
                lu = valeur == null ? 0 : Long.parseLong(valeur);
            } catch (NumberFormatException ignored) {
                lu = 0;
            }
        }
        this.decalage = lu;
    }

    public Instant instant() {
        return Instant.ofEpochMilli(System.currentTimeMillis() + decalage);
    }

    public ZonedDateTime maintenant() {
        return instant().atZone(zone);
    }

    public LocalDateTime local() {
        return maintenant().toLocalDateTime();
    }

    public LocalDate aujourdhui() {
        return maintenant().toLocalDate();
    }

    public ZoneId zone() {
        return zone;
    }

    public boolean autorisee() {
        return autorisee;
    }

    public boolean decalee() {
        return decalage != 0;
    }

    /** Heure réelle depuis l'epoch, pour comparer aux dates du serveur (dernière connexion…). */
    public long millis() {
        return System.currentTimeMillis() + decalage;
    }

    public void regler(LocalDateTime cible) {
        decalage = cible.atZone(zone).toInstant().toEpochMilli() - System.currentTimeMillis();
        etat.setGlobale(CLE, Long.toString(decalage));
    }

    public void reel() {
        decalage = 0;
        etat.setGlobale(CLE, null);
    }
}
