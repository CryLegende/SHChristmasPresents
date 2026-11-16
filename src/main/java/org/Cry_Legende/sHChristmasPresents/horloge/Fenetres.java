package org.Cry_Legende.sHChristmasPresents.horloge;

import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.stockage.Etat;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Multiplicateurs datés (fenetres:). qui: sous-mediane ne vaut que pour les joueurs dont le total de l'évènement est
 * sous la médiane, calculée au début de la fenêtre sur les joueurs qui ont déjà gagné quelque chose ; ce relevé est
 * gardé (shxmas_etat) pour que la fenêtre ne change pas après un redémarrage.
 */
public final class Fenetres {

    public record Fenetre(String id, LocalDateTime debut, LocalDateTime fin, double facteur, boolean sousMediane) {
    }

    /** Relevé d'une fenêtre sous-médiane : la médiane et les joueurs qui étaient au-dessus ou dessus. */
    public record Releve(double mediane, Set<UUID> auDessus) {
    }

    private final Etat etat;
    private final Logger log;
    private final Supplier<Map<UUID, Long>> totaux;
    private final Map<String, Releve> releves = new ConcurrentHashMap<>();
    private volatile List<Fenetre> fenetres = List.of();

    public Fenetres(Etat etat, Logger log, Supplier<Map<UUID, Long>> totaux) {
        this.etat = etat;
        this.log = log;
        this.totaux = totaux;
    }

    public void configurer(ConfigurationSection section) {
        List<Fenetre> lues = new ArrayList<>();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection f = section.getConfigurationSection(id);
                LocalDateTime debut = f == null ? null : LectureConfig.dateHeure(f.get("debut"));
                LocalDateTime fin = f == null ? null : LectureConfig.dateHeure(f.get("fin"));
                if (debut == null || fin == null || !fin.isAfter(debut)) {
                    log.warning("fenetres." + id + " : debut ou fin illisible, fenêtre ignorée");
                    continue;
                }
                String qui = f.getString("qui", "tous").toLowerCase(Locale.ROOT);
                lues.add(new Fenetre(id, debut, fin, Math.max(0, f.getDouble("facteur", 1)), qui.equals("sous-mediane")));
            }
        }
        fenetres = List.copyOf(lues);
        for (Fenetre f : lues) {
            if (f.sousMediane() && !releves.containsKey(f.id())) {
                Releve lu = lireReleve(etat.get("fenetre." + f.id()));
                if (lu != null) {
                    releves.put(f.id(), lu);
                }
            }
        }
    }

    public List<Fenetre> liste() {
        return fenetres;
    }

    public static boolean active(Fenetre f, ZonedDateTime t) {
        LocalDateTime local = t.toLocalDateTime();
        return !local.isBefore(f.debut()) && local.isBefore(f.fin());
    }

    /** Produit des fenêtres actives qui s'appliquent à ce joueur. */
    public double facteur(UUID joueur, ZonedDateTime t) {
        double facteur = 1;
        for (Fenetre f : fenetres) {
            if (!active(f, t)) {
                continue;
            }
            if (!f.sousMediane()) {
                facteur *= f.facteur();
                continue;
            }
            Releve releve = releves.get(f.id());
            if (releve != null && !releve.auDessus().contains(joueur)) {
                facteur *= f.facteur();
            }
        }
        return facteur;
    }

    public Releve releve(String id) {
        return releves.get(id);
    }

    /** Relève la médiane des fenêtres sous-médiane qui viennent de commencer (fil principal). */
    public void verifier(ZonedDateTime t) {
        for (Fenetre f : fenetres) {
            if (f.sousMediane() && active(f, t) && !releves.containsKey(f.id())) {
                Map<UUID, Long> parJoueur = totaux.get();
                List<Long> valeurs = new ArrayList<>();
                for (long total : parJoueur.values()) {
                    if (total > 0) {
                        valeurs.add(total);
                    }
                }
                double mediane = mediane(valeurs);
                Set<UUID> auDessus = new HashSet<>();
                for (Map.Entry<UUID, Long> entree : parJoueur.entrySet()) {
                    if (entree.getValue() > 0 && entree.getValue() >= mediane) {
                        auDessus.add(entree.getKey());
                    }
                }
                Releve releve = new Releve(mediane, Set.copyOf(auDessus));
                releves.put(f.id(), releve);
                etat.set("fenetre." + f.id(), ecrireReleve(releve));
                log.info("Fenêtre " + f.id() + " : médiane " + mediane + " sur " + valeurs.size() + " joueurs, "
                        + auDessus.size() + " au-dessus (sans bonus)");
            }
        }
    }

    public static double mediane(List<Long> valeurs) {
        if (valeurs.isEmpty()) {
            return 0;
        }
        List<Long> triees = new ArrayList<>(valeurs);
        triees.sort(Long::compare);
        int n = triees.size();
        return n % 2 == 1 ? triees.get(n / 2) : (triees.get(n / 2 - 1) + triees.get(n / 2)) / 2.0;
    }

    private static String ecrireReleve(Releve releve) {
        StringBuilder out = new StringBuilder(Double.toString(releve.mediane())).append('|');
        boolean premier = true;
        for (UUID uuid : releve.auDessus()) {
            if (!premier) {
                out.append(',');
            }
            out.append(uuid);
            premier = false;
        }
        return out.toString();
    }

    private static Releve lireReleve(String texte) {
        if (texte == null || texte.indexOf('|') < 0) {
            return null;
        }
        try {
            String[] parties = texte.split("\\|", 2);
            Set<UUID> auDessus = new HashSet<>();
            for (String uuid : parties[1].split(",")) {
                if (!uuid.isBlank()) {
                    auDessus.add(UUID.fromString(uuid.trim()));
                }
            }
            return new Releve(Double.parseDouble(parties[0]), Set.copyOf(auDessus));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Collection<String> ids(List<Fenetre> fenetres) {
        List<String> out = new ArrayList<>();
        fenetres.forEach(f -> out.add(f.id()));
        return out;
    }
}
