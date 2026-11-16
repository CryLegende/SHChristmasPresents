package org.Cry_Legende.sHChristmasPresents.carte;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Menu;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** /carte : les tampons, obtenus ou non, et l'état de la carte. */
final class MenuCarte extends Menu {

    private final SHChristmasPresents plugin;
    private final Carte carte;

    MenuCarte(SHChristmasPresents plugin, Carte carte, Player joueur) {
        super(joueur, carte.lignes(), plugin.messages().objet("carte.menu.titre"));
        this.plugin = plugin;
        this.carte = carte;
    }

    @Override
    protected void dessiner() {
        var messages = plugin.messages();
        var uuid = joueur.getUniqueId();
        int n = carte.nombre(uuid);
        int total = carte.tampons().size();
        boolean complete = carte.complete(uuid);
        poser(carte.caseInfo(), ObjetConfig.simple(complete ? Material.FILLED_MAP : Material.MAP).creer(
                messages.objet("carte.menu.info.nom"),
                messages.lore(complete ? "carte.menu.info.complete" : "carte.menu.info.description", "{n}", n, "{total}", total)), null);
        for (Carte.Tampon t : carte.tampons()) {
            boolean obtenu = carte.a(uuid, t.id());
            String base = "carte.tampons." + t.id();
            poser(t.case_(), (obtenu ? t.objetObtenu() : t.objet()).creer(
                    messages.objet(obtenu ? "carte.menu.tampon-obtenu" : "carte.menu.tampon-manquant", "{tampon}", messages.brut(base + ".nom")),
                    messages.lore(base + ".description")), null);
        }
        cadre(carte.cadre());
    }
}
