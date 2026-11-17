package org.Cry_Legende.sHChristmasPresents.calendrier;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Menu;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetConfig;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** /calendrier : les portes et leur état ; clic sur une porte ouvrable ou à rattraper pour l'ouvrir. */
final class MenuCalendrier extends Menu {

    private final SHChristmasPresents plugin;
    private final Calendrier calendrier;

    MenuCalendrier(SHChristmasPresents plugin, Calendrier calendrier, Player joueur) {
        super(joueur, calendrier.lignes(), plugin.messages().objet("calendrier.menu.titre"));
        this.plugin = plugin;
        this.calendrier = calendrier;
    }

    @Override
    protected void dessiner() {
        var messages = plugin.messages();
        UUID uuid = joueur.getUniqueId();
        LocalDate jour = plugin.horloge().aujourdhui();
        poser(calendrier.caseInfo(), ObjetConfig.simple(Material.CLOCK).creer(messages.objet("calendrier.menu.info.nom"),
                messages.lore("calendrier.menu.info.description", "{ouvertes}", calendrier.ouvertes(uuid),
                        "{total}", calendrier.nombre(), "{palier}", calendrier.palierTexte(uuid))), null);
        List<Integer> cases = calendrier.cases();
        for (int porte = 1; porte <= calendrier.nombre() && porte <= cases.size(); porte++) {
            EtatPorte etat = calendrier.etat(uuid, porte, jour);
            LocalDate ouverture = calendrier.jourDe(porte);
            String base = "calendrier.menu.porte." + etat.cle();
            String contenuCle = "calendrier.contenu." + porte;
            String contenu = messages.existe(contenuCle) ? messages.brut(contenuCle) : messages.brut("calendrier.contenu.defaut");
            Object[] remplacements = {"{porte}", porte, "{date}", Textes.jour(ouverture),
                    "{limite}", Textes.jour(ouverture.plusDays(calendrier.rattrapageJours())),
                    "{contenu}", contenu.replace("{flocons}", Integer.toString(calendrier.floconsDe(porte)))};
            var objet = calendrier.objet(porte, etat).creer(messages.objet(base + ".nom", remplacements),
                    messages.lore(base + ".lore", remplacements));
            objet.setAmount(Math.max(1, Math.min(64, porte)));
            final int numero = porte;
            // Toute porte répond au clic : ouverture, ou la raison du refus
            poser(cases.get(porte - 1), objet, type -> {
                calendrier.ouvrir(joueur, numero);
                rafraichir();
            });
        }
        accent(calendrier.accent());
        cadre(calendrier.cadre());
    }
}
