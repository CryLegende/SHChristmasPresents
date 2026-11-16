package org.Cry_Legende.sHChristmasPresents.flocons;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Le seul chemin des gains de Flocons : plafond du jour de la source (avec rendement réduit au-delà), multiplicateurs,
 * journal, versement par le portefeuille, points du sapin, barre d'action. Fil principal seulement.
 */
public final class Flocons {

    private final SHChristmasPresents plugin;
    private final Comptes comptes;
    private final Multiplicateurs multiplicateurs;
    private final Portefeuille portefeuille;
    private volatile Map<String, Source> sources = Map.of();

    public Flocons(SHChristmasPresents plugin, Comptes comptes, Multiplicateurs multiplicateurs, Portefeuille portefeuille) {
        this.plugin = plugin;
        this.comptes = comptes;
        this.multiplicateurs = multiplicateurs;
        this.portefeuille = portefeuille;
    }

    public void configurer(ConfigurationSection section) {
        Map<String, Source> lues = new LinkedHashMap<>();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                lues.put(id, Source.lire(id, section.getConfigurationSection(id)));
            }
        }
        sources = java.util.Collections.unmodifiableMap(lues);
    }

    public Source source(String id) {
        return sources.get(id);
    }

    public Map<String, Source> sources() {
        return sources;
    }

    public Comptes comptes() {
        return comptes;
    }

    public Multiplicateurs multiplicateurs() {
        return multiplicateurs;
    }

    public Portefeuille portefeuille() {
        return portefeuille;
    }

    /**
     * Gain de Flocons.
     *
     * @param horsPhase vrai pour verser même hors de la phase de gains (cadeau d'un joueur, déjà payé)
     * @return Flocons versés (0 hors phase, au-delà d'un plafond à l'arrêt…), -1 si la source est inconnue
     */
    public int gagner(UUID uuid, String pseudo, int montant, String sourceId, boolean horsPhase) {
        Source source = sources.get(sourceId);
        if (source == null) {
            plugin.getLogger().warning("Source de Flocons inconnue : " + sourceId + " (" + pseudo + ", " + montant + ")");
            return -1;
        }
        if (montant <= 0) {
            return 0;
        }
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        boolean gains = plugin.dates().gains(maintenant);
        if (!horsPhase && !gains) {
            plugin.debug("Hors phase de gains : " + pseudo + " +" + montant + " (" + sourceId + ") non versé");
            return 0;
        }
        plugin.fenetres().verifier(maintenant);
        LocalDate jour = maintenant.toLocalDate();
        Comptes.Compte compte = comptes.compte(uuid, pseudo, jour);
        long[] duJour = compte.parSource.computeIfAbsent(sourceId, k -> new long[2]);

        double effectif = montant;
        if (source.plafondJour() > 0) {
            long dans = Math.max(0, Math.min(montant, source.plafondJour() - duJour[0]));
            effectif = dans + (montant - dans) * source.auDela();
        }
        double facteur = source.multiplicateurs() ? multiplicateurs.total(uuid) : 1;
        double exact = effectif * facteur + compte.restes.getOrDefault(sourceId, 0.0);
        int verse = (int) Math.floor(exact + 1e-9);
        compte.restes.put(sourceId, Math.max(0, exact - verse));

        duJour[0] += montant;
        duJour[1] += verse;
        compte.total += verse;
        comptes.journal(uuid, pseudo, sourceId, montant, verse, facteur, jour, maintenant.toLocalDateTime());
        portefeuille.donner(uuid, pseudo, verse);
        plugin.debug("Gain " + pseudo + " " + sourceId + " base " + montant + " versé " + verse + " (×" + Textes.facteur(facteur) + ")");

        int points = source.pointsSapin(montant);
        if (points > 0 && gains) {
            plugin.sapin().contribuer(uuid, pseudo, points, true);
        }
        Player enLigne = Bukkit.getPlayer(uuid);
        if (enLigne != null && verse > 0) {
            String cle = "flocons.gains." + sourceId;
            if (!plugin.messages().existe(cle)) {
                cle = "flocons.gains.defaut";
            }
            plugin.messages().barre(enLigne, cle, "{montant}", verse, "{source}", nomSource(sourceId));
        }
        return verse;
    }

    /** Nom affiché d'une source (messages.yml flocons.sources.&lt;id&gt;). */
    public String nomSource(String id) {
        String cle = "flocons.sources." + id;
        return plugin.messages().existe(cle) ? plugin.messages().brut(cle) : id;
    }

    /** Flocons de base restant avant le plafond du jour, -1 sans plafond. */
    public long restePlafond(UUID uuid, String sourceId, LocalDate jour) {
        Source source = sources.get(sourceId);
        if (source == null || source.plafondJour() <= 0) {
            return -1;
        }
        return Math.max(0, source.plafondJour() - comptes.baseJour(uuid, sourceId, jour));
    }

    /** /flocons : solde, gains du jour par source, ce qui reste avant chaque plafond. */
    public void afficher(Player joueur) {
        var messages = plugin.messages();
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        LocalDate jour = maintenant.toLocalDate();
        UUID uuid = joueur.getUniqueId();
        long solde = portefeuille.solde(joueur);
        messages.envoyer(joueur, "flocons.commande.entete", "{solde}", solde < 0 ? "?" : Textes.nombre(solde),
                "{jour}", Textes.nombre(comptes.verseJour(uuid, jour)), "{total}", Textes.nombre(comptes.total(uuid)));
        if (!plugin.dates().gains(maintenant)) {
            messages.envoyer(joueur, "flocons.commande.hors-phase", "{avent}", Textes.date(plugin.dates().get("avent")),
                    "{fin-gains}", Textes.date(plugin.dates().get("fin-gains")));
            return;
        }
        Map<String, long[]> parSource = comptes.jourParSource(uuid, jour);
        for (Source source : sources.values()) {
            long[] v = parSource.get(source.id());
            if (v == null && source.plafondJour() <= 0) {
                continue;
            }
            long verse = v == null ? 0 : v[1];
            String reste = "";
            if (source.plafondJour() > 0) {
                long restant = restePlafond(uuid, source.id(), jour);
                reste = restant > 0 ? messages.brut("flocons.commande.reste", "{reste}", Textes.nombre(restant))
                        : messages.brut(source.auDela() > 0 ? "flocons.commande.plafond-reduit" : "flocons.commande.plafond-atteint");
            }
            messages.envoyer(joueur, "flocons.commande.ligne", "{source}", nomSource(source.id()),
                    "{verse}", Textes.nombre(verse), "{reste}", reste);
        }
        double facteur = multiplicateurs.total(uuid);
        if (facteur > 1) {
            messages.envoyer(joueur, "flocons.commande.multiplicateur", "{facteur}", Textes.facteur(facteur));
        }
    }

    /** Ligne d'information pour /shchristmaspresents info. */
    public void info(CommandSender cible, UUID uuid, String pseudo) {
        LocalDate jour = plugin.horloge().aujourdhui();
        plugin.messages().envoyer(cible, "admin.info-flocons", "{joueur}", pseudo, "{total}", Textes.nombre(comptes.total(uuid)),
                "{jour}", Textes.nombre(comptes.verseJour(uuid, jour)), "{facteur}", Textes.facteur(multiplicateurs.total(uuid)));
    }
}
