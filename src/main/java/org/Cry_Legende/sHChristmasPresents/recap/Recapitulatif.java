package org.Cry_Legende.sHChristmasPresents.recap;

import net.kyori.adventure.text.Component;
import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Joueurs;
import org.Cry_Legende.sHChristmasPresents.commun.Messages;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.Cry_Legende.sHChristmasPresents.horloge.Phase;
import org.Cry_Legende.sHChristmasPresents.joueurs.Profil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * « Mon Noël » : pendant la phase cloture (jusqu'à apres), un livre de souvenirs donné et ouvert à la connexion,
 * une fois, aux joueurs qui ont participé.
 */
public final class Recapitulatif {

    private final SHChristmasPresents plugin;

    public Recapitulatif(SHChristmasPresents plugin) {
        this.plugin = plugin;
    }

    /** À la connexion (fil principal). */
    public void connexion(Player joueur) {
        if (!joueur.isOnline() || plugin.phase() != Phase.CLOTURE) {
            return;
        }
        UUID uuid = joueur.getUniqueId();
        Profil profil = plugin.profils().obtenir(uuid, joueur.getName());
        if (profil.recap != null) {
            return;
        }
        long flocons = plugin.flocons().comptes().total(uuid);
        int portes = plugin.calendrier().ouvertes(uuid);
        long sapin = plugin.sapin().contribution(uuid);
        if (flocons <= 0 && portes <= 0 && sapin <= 0) {
            return;
        }
        profil.recap = plugin.horloge().local();
        plugin.profils().sauver(profil);
        ItemStack livre = livre(joueur);
        Joueurs.donner(joueur, livre.clone());
        joueur.openBook(livre);
        plugin.messages().envoyer(joueur, "recap.donne", "{annee}", plugin.annee());
        plugin.son("recap", joueur);
    }

    /** Le livre du joueur (aussi pour /shchristmaspresents info). */
    public ItemStack livre(Player joueur) {
        UUID uuid = joueur.getUniqueId();
        var m = plugin.messages();
        int place = plugin.sapin().place(uuid);
        int niveau = plugin.sapin().niveau(uuid);
        Object[] r = {"{pseudo}", joueur.getName(), "{annee}", plugin.annee(),
                "{portes}", plugin.calendrier().ouvertes(uuid), "{portes-total}", plugin.calendrier().nombre(),
                "{tampons}", plugin.carte().nombre(uuid), "{tampons-total}", plugin.carte().tampons().size(),
                "{flocons}", Textes.nombre(plugin.flocons().comptes().total(uuid)),
                "{cadeaux}", plugin.compteurs().valeur(uuid, "cadeaux-envoyes"),
                "{sapin}", Textes.nombre(plugin.sapin().contribution(uuid)),
                "{rang}", place > 0 ? m.brut("recap.rang", "{rang}", place, "{place}", place, "{etoiles}", plugin.sapin().etoiles(niveau))
                        : m.brut("recap.sans-rang"),
                "{contributeurs}", plugin.sapin().contributeurs()};
        ItemStack livre = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) livre.getItemMeta();
        meta.title(Messages.legacy(m.brut("recap.titre", r)));
        meta.author(Messages.legacy(m.brut("recap.auteur", r)));
        List<Component> pages = new ArrayList<>();
        for (String page : m.lignes("recap.pages", r)) {
            pages.add(Messages.legacy(page.replace("\\n", "\n")));
        }
        meta.addPages(pages.toArray(new Component[0]));
        livre.setItemMeta(meta);
        return livre;
    }
}
