package org.Cry_Legende.sHChristmasPresents.sapin;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Menu;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetConfig;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /sapin : la jauge, la contribution du joueur, les offrandes et les meilleurs contributeurs. */
final class MenuSapin extends Menu {

    private static final int LONGUEUR_BARRE = 20;

    private final SHChristmasPresents plugin;
    private final Sapin sapin;

    MenuSapin(SHChristmasPresents plugin, Sapin sapin, Player joueur) {
        super(joueur, sapin.lignes, plugin.messages().objet("sapin.menu.titre"));
        this.plugin = plugin;
        this.sapin = sapin;
    }

    @Override
    protected void dessiner() {
        var messages = plugin.messages();
        UUID uuid = joueur.getUniqueId();
        LocalDate jour = plugin.horloge().aujourdhui();
        double pourcent = sapin.pourcent();
        int pleins = (int) Math.floor(pourcent / 100 * LONGUEUR_BARRE);
        String barre = messages.brut("sapin.menu.barre-pleine").repeat(pleins)
                + messages.brut("sapin.menu.barre-vide").repeat(LONGUEUR_BARRE - pleins);
        Integer suivant = sapin.palierSuivant();
        poser(sapin.caseJauge, ObjetConfig.simple(Material.SPRUCE_SAPLING).creer(messages.objet("sapin.menu.jauge.nom"),
                messages.lore(suivant == null ? "sapin.menu.jauge.lore-fini" : "sapin.menu.jauge.lore",
                        "{pourcent}", Textes.pourcent(pourcent), "{barre}", barre, "{suivant}", suivant == null ? "" : suivant,
                        "{contributeurs}", sapin.contributeurs())), null);

        int reste = sapin.resteJour(uuid, jour);
        poser(sapin.caseContribution, ObjetConfig.simple(Material.PLAYER_HEAD).creer(messages.objet("sapin.menu.contribution.nom"),
                messages.lore(sapin.contributeur(uuid) ? "sapin.menu.contribution.lore-contributeur" : "sapin.menu.contribution.lore",
                        "{total}", Textes.nombre(sapin.contribution(uuid)), "{jour}", Textes.nombre(sapin.contributionJour(uuid, jour)),
                        "{plafond}", Textes.nombre(sapin.plafondJour()), "{reste}", Textes.nombre(reste))), null);

        List<Integer> montants = sapin.offrandes();
        for (int i = 0; i < montants.size() && i < sapin.casesOffrandes.size(); i++) {
            int montant = montants.get(i);
            poser(sapin.casesOffrandes.get(i), ObjetConfig.simple(Material.SNOWBALL).creer(
                    messages.objet("sapin.menu.offrande.nom", "{montant}", Textes.nombre(montant)),
                    messages.lore("sapin.menu.offrande.lore", "{montant}", Textes.nombre(montant))), type -> {
                sapin.offrir(joueur, montant);
                rafraichir();
            });
        }

        List<String> lignes = new ArrayList<>();
        List<Sapin.Part> top = sapin.top();
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            lignes.add(messages.brut("sapin.menu.top.ligne", "{rang}", i + 1, "{nom}", top.get(i).pseudo(),
                    "{valeur}", Textes.nombre(top.get(i).total())));
        }
        if (lignes.isEmpty()) {
            lignes.add(messages.brut("sapin.menu.top.vide"));
        }
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lignes.forEach(l -> lore.add(org.Cry_Legende.sHChristmasPresents.commun.Messages.objetTexte(l)));
        poser(sapin.caseTop, ObjetConfig.simple(Material.GOLDEN_HELMET).creer(messages.objet("sapin.menu.top.nom"), lore), null);
        cadre(sapin.cadre);
    }
}
