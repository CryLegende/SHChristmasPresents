package org.Cry_Legende.sHChristmasPresents.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.boutique.Boutique;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.Cry_Legende.sHChristmasPresents.horloge.Dates;
import org.Cry_Legende.sHChristmasPresents.sapin.Sapin;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * %shchristmaspresents_&lt;x&gt;% : lectures en mémoire seulement (appelé depuis n'importe quel fil). Booléens en
 * true/false ; nombres sans séparateur, sauf avec le suffixe _texte (« 1 250 », espace fine).
 */
public final class Expansion extends PlaceholderExpansion {

    private static final Pattern ENTIER = Pattern.compile("-?\\d+");
    private static final List<String> CHAMPS_ARTICLE = List.of("ligne", "achats", "peut", "prix", "limite");

    private final SHChristmasPresents plugin;

    public Expansion(SHChristmasPresents plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shchristmaspresents";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cry_Legende";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer joueur, @NotNull String params) {
        String p = params.toLowerCase(Locale.ROOT);
        boolean texte = p.endsWith("_texte");
        if (texte) {
            p = p.substring(0, p.length() - "_texte".length());
        }
        String valeur;
        try {
            valeur = valeur(joueur, p);
        } catch (RuntimeException e) {
            plugin.debug("Placeholder " + params + " en erreur : " + e);
            return "";
        }
        if (valeur != null && texte && ENTIER.matcher(valeur).matches()) {
            return Textes.nombre(Long.parseLong(valeur));
        }
        return valeur;
    }

    private static String bool(boolean valeur) {
        return valeur ? "true" : "false";
    }

    private String valeur(OfflinePlayer joueur, String p) {
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        LocalDate jour = maintenant.toLocalDate();
        Dates dates = plugin.dates();
        Sapin sapin = plugin.sapin();

        // ---- sans joueur
        switch (p) {
            case "phase" -> {
                return plugin.phase().id();
            }
            case "date" -> {
                // Jour de l'évènement (horloge du plugin, horloge de test comprise) en AAAAMMJJ, pour les conditions numériques
                return plugin.horloge().aujourdhui().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            }
            case "phase_ligne" -> {
                List<Object> r = new ArrayList<>();
                for (String repere : Dates.REPERES) {
                    r.add("{" + repere + "}");
                    r.add(Textes.date(dates.get(repere)));
                }
                return plugin.messages().brut("placeholders.phase-ligne." + plugin.phase().id(), r.toArray());
            }
            case "porte_jour" -> {
                return Integer.toString(plugin.calendrier().porteDuJour(jour));
            }
            case "sapin_pourcent" -> {
                return Textes.pourcent(sapin.pourcent());
            }
            case "sapin_pourcent_entier" -> {
                return Integer.toString((int) Math.floor(sapin.pourcent()));
            }
            case "sapin_palier_suivant" -> {
                Integer suivant = sapin.palierSuivant();
                return suivant == null ? "" : suivant.toString();
            }
            // Avec le signe « % » (un « % » seul à côté d'un placeholder casse la lecture des placeholders suivants)
            case "sapin_palier_suivant_affiche" -> {
                Integer suivant = sapin.palierSuivant();
                return suivant == null ? "100 %" : suivant + " %";
            }
            case "sapin_pourcent_affiche" -> {
                return (int) Math.floor(sapin.pourcent()) + " %";
            }
            case "sapin_points" -> {
                return Long.toString(sapin.points());
            }
            case "sapin_cible" -> {
                return Integer.toString(sapin.cible());
            }
            case "sapin_contributeurs" -> {
                return Integer.toString(sapin.contributeurs());
            }
            case "sapin_palier_suivant_nom" -> {
                Sapin.Palier suivant = sapin.prochain();
                return suivant == null ? "" : sapin.nomPalier(suivant);
            }
            case "sapin_prevision" -> {
                Double prevision = sapin.pouce().prevision(maintenant);
                return prevision == null ? "" : Integer.toString((int) Math.floor(Math.min(100, prevision)));
            }
            case "sapin_coup_de_pouce" -> {
                return bool(sapin.ouvert() && sapin.pouce().actif());
            }
            case "sapin_etat_ligne" -> {
                return etatSapin(sapin);
            }
            default -> {
            }
        }
        if (p.startsWith("avant_")) {
            String repere = p.substring("avant_".length());
            if (!dates.connu(repere)) {
                return null;
            }
            Duration reste = Duration.between(maintenant, dates.get(repere));
            return reste.isNegative() || reste.isZero() ? "" : Textes.duree(reste);
        }
        if (p.startsWith("sapin_top_")) {
            String[] parties = p.substring("sapin_top_".length()).split("_", 2);
            if (parties.length < 2) {
                return null;
            }
            int rang;
            try {
                rang = Integer.parseInt(parties[0]);
            } catch (NumberFormatException e) {
                return null;
            }
            List<Sapin.Part> top = sapin.top();
            if (rang < 1 || rang > top.size()) {
                return "";
            }
            Sapin.Part part = top.get(rang - 1);
            return switch (parties[1]) {
                case "nom" -> part.pseudo();
                case "valeur" -> Long.toString(part.total());
                default -> null;
            };
        }
        if (p.equals("multiplicateur") && joueur == null) {
            return Textes.facteur(plugin.flocons().multiplicateurs().facteurGlobal());
        }

        // ---- par joueur
        if (joueur == null) {
            return "";
        }
        UUID uuid = joueur.getUniqueId();
        switch (p) {
            case "multiplicateur" -> {
                return Textes.facteur(plugin.flocons().multiplicateurs().total(uuid));
            }
            case "flocons_jour" -> {
                return Long.toString(plugin.flocons().comptes().verseJour(uuid, jour));
            }
            case "flocons_total" -> {
                return Long.toString(plugin.flocons().comptes().total(uuid));
            }
            case "porte_disponible" -> {
                return bool(plugin.calendrier().disponible(uuid));
            }
            case "porte_ligne" -> {
                return plugin.calendrier().ligne(joueur);
            }
            case "portes_ouvertes" -> {
                return Integer.toString(plugin.calendrier().ouvertes(uuid));
            }
            case "portes_palier" -> {
                return plugin.calendrier().palierTexte(uuid);
            }
            case "sapin_contribution" -> {
                return Long.toString(sapin.contribution(uuid));
            }
            case "sapin_contribution_jour" -> {
                return Integer.toString(sapin.contributionJour(uuid, jour));
            }
            case "sapin_rang" -> {
                return sapin.etoiles(sapin.niveau(uuid));
            }
            case "sapin_rang_ligne" -> {
                int niveau = sapin.niveau(uuid);
                long total = sapin.contribution(uuid);
                return niveau <= 0
                        ? plugin.messages().brut("placeholders.sapin-rang.aucun", "{points}", Textes.nombre(total),
                        "{reste}", Textes.nombre(Math.max(0, sapin.seuil(1) - total)))
                        : plugin.messages().brut("placeholders.sapin-rang.rang", "{rang}", sapin.etoiles(niveau),
                        "{points}", Textes.nombre(total));
            }
            case "sapin_multiplicateur" -> {
                return Integer.toString(sapin.niveau(uuid));
            }
            case "sapin_rang_suivant_reste" -> {
                int niveau = sapin.niveau(uuid);
                return niveau >= sapin.rangMax() ? "" : Long.toString(Math.max(0, sapin.seuil(niveau + 1) - sapin.contribution(uuid)));
            }
            case "sapin_place" -> {
                return Integer.toString(sapin.place(uuid));
            }
            case "sapin_offrande_reste" -> {
                int reste = sapin.resteOffrandeJour(uuid, jour);
                return Integer.toString(reste == Integer.MAX_VALUE ? 0 : reste);
            }
            case "sapin_palier_suivant_cadeau" -> {
                Sapin.Palier suivant = sapin.prochain();
                return suivant == null ? "" : sapin.cadeauPalier(suivant, Math.max(1, sapin.niveau(uuid)));
            }
            case "carte_tampons" -> {
                return Integer.toString(plugin.carte().nombre(uuid));
            }
            case "carte_complete" -> {
                return bool(plugin.carte().complete(uuid));
            }
            case "cadeaux_envoyes_jour" -> {
                return Integer.toString(plugin.cadeaux().envoyesJour(uuid, jour));
            }
            case "ile_jour" -> {
                return Integer.toString(plugin.petitsCadeaux().trouvesAujourdhui(uuid));
            }
            case "clicks" -> {
                return Integer.toString(plugin.clicker().clics(uuid));
            }
            case "clicks_jour" -> {
                return Integer.toString(plugin.clicker().clicsJour(uuid));
            }
            case "offert_jour" -> {
                return Integer.toString(sapin.offertJour(uuid, jour));
            }
            case "level" -> {
                return Integer.toString(plugin.clicker().niveau(uuid));
            }
            default -> {
            }
        }
        if (p.startsWith("flocons_jour_")) {
            return Long.toString(plugin.flocons().comptes().verseJour(uuid, p.substring("flocons_jour_".length()), jour));
        }
        if (p.startsWith("plafond_")) {
            long reste = plugin.flocons().restePlafond(uuid, p.substring("plafond_".length()), jour);
            return reste < 0 ? "" : Long.toString(reste);
        }
        if (p.startsWith("carte_tampon_")) {
            return bool(plugin.carte().a(uuid, p.substring("carte_tampon_".length())));
        }
        if (p.startsWith("compteur_")) {
            return Integer.toString(plugin.compteurs().valeur(uuid, p.substring("compteur_".length())));
        }
        if (p.startsWith("article_")) {
            return article(joueur, p.substring("article_".length()));
        }
        return null;
    }

    /** Une ligne sur le sapin (messages.yml placeholders.sapin-etat) : prochain palier, coup de pouce, complet, Veillée. */
    private String etatSapin(Sapin sapin) {
        var m = plugin.messages();
        if (sapin.veilleePassee()) {
            return m.brut("placeholders.sapin-etat.passee");
        }
        if (!sapin.ouvert()) {
            return m.brut("placeholders.sapin-etat.ferme");
        }
        if (sapin.pouce().actif()) {
            return m.brut("placeholders.sapin-etat.pouce", "{facteur}", Textes.facteur(sapin.pouce().facteurConfig()));
        }
        Sapin.Palier suivant = sapin.prochain();
        if (sapin.complet() || suivant == null) {
            return m.brut("placeholders.sapin-etat.complet");
        }
        if (suivant.veillee()) {
            return m.brut("placeholders.sapin-etat.veillee", "{seuil}", Textes.facteur(sapin.seuilVeillee()));
        }
        return m.brut("placeholders.sapin-etat.suivant", "{palier}", suivant.pourcent(), "{nom}", sapin.nomPalier(suivant));
    }

    private String article(OfflinePlayer joueur, String reste) {
        int coupure = reste.lastIndexOf('_');
        if (coupure <= 0) {
            return null;
        }
        String champ = reste.substring(coupure + 1);
        if (!CHAMPS_ARTICLE.contains(champ)) {
            return null;
        }
        Boutique boutique = plugin.boutique();
        Boutique.Article article = boutique.article(reste.substring(0, coupure));
        if (article == null) {
            return "";
        }
        return switch (champ) {
            case "ligne" -> boutique.ligne(joueur, article);
            case "achats" -> Integer.toString(boutique.achatsTotal(joueur.getUniqueId(), article.id()));
            case "peut" -> bool(boutique.peut(joueur, article));
            case "prix" -> Integer.toString(article.prix());
            case "limite" -> boutique.limite(joueur, article);
            default -> null;
        };
    }
}
