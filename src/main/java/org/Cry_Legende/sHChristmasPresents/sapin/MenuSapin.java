package org.Cry_Legende.sHChristmasPresents.sapin;

import net.kyori.adventure.text.Component;
import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Menu;
import org.Cry_Legende.sHChristmasPresents.commun.Messages;
import org.Cry_Legende.sHChristmasPresents.commun.ObjetConfig;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /sapin : la jauge et ce qui vient, à quoi sert le sapin, le rang du joueur, les meilleurs décorateurs, les neuf
 * paliers (décor et cadeau selon le rang), les offrandes et la part du jour. Textes : messages.yml sapin.menu.
 */
final class MenuSapin extends Menu {

    private static final int LONGUEUR_BARRE = 20;
    private static final String M = "sapin.menu.";

    private final SHChristmasPresents plugin;
    private final Sapin sapin;

    MenuSapin(SHChristmasPresents plugin, Sapin sapin, Player joueur) {
        super(joueur, sapin.lignes, plugin.messages().objet(M + "titre"));
        this.plugin = plugin;
        this.sapin = sapin;
    }

    private List<Component> lore(List<String> lignes) {
        List<Component> out = new ArrayList<>();
        for (String l : lignes) {
            out.add(Messages.objetTexte(l));
        }
        return out;
    }

    private List<String> l(String cle, Object... r) {
        return plugin.messages().lignes(M + cle, r);
    }

    private String b(String cle, Object... r) {
        return plugin.messages().brut(M + cle, r);
    }

    private static ItemStack lueur(ItemStack objet, boolean oui) {
        if (!oui) {
            return objet;
        }
        ItemMeta meta = objet.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            objet.setItemMeta(meta);
        }
        return objet;
    }

    @Override
    protected void dessiner() {
        UUID uuid = joueur.getUniqueId();
        LocalDate jour = plugin.horloge().aujourdhui();
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        double pourcent = sapin.pourcent();
        int rang = sapin.niveau(uuid);
        int mult = Math.max(1, rang);
        Sapin.Palier suivant = sapin.prochain();
        boolean ouvert = sapin.ouvert();

        // ---- jauge
        int pleins = (int) Math.floor(pourcent / 100 * LONGUEUR_BARRE);
        String barre = b("barre-pleine").repeat(pleins) + b("barre-vide").repeat(LONGUEUR_BARRE - pleins);
        List<String> jauge = new ArrayList<>(l("jauge.lore", "{barre}", barre, "{pourcent}", Textes.pourcent(pourcent),
                "{contributeurs}", sapin.contributeurs()));
        if (sapin.veilleePassee()) {
            jauge.addAll(l("jauge.veillee-passee"));
        } else if (suivant != null && !suivant.veillee()) {
            jauge.addAll(l("jauge.suivant", "{palier}", suivant.pourcent(), "{nom}", sapin.nomPalier(suivant),
                    "{manque}", Textes.nombre(sapin.manquePour(suivant.pourcent()))));
        } else if (sapin.complet()) {
            jauge.addAll(l("jauge.complet"));
        } else if (suivant != null && sapin.fenetreVeillee(maintenant)) {
            LocalDateTime fin = sapin.veilleeJusqua();
            jauge.addAll(l("jauge.veillee-en-cours", "{seuil}", Textes.facteur(sapin.seuilVeillee()),
                    "{manque}", Textes.nombre(sapin.manquePour(sapin.seuilVeillee())),
                    "{jusqua}", fin == null ? "" : Textes.heure(fin.toLocalTime())));
        } else if (suivant != null) {
            jauge.addAll(l("jauge.veillee", "{seuil}", Textes.facteur(sapin.seuilVeillee()),
                    "{manque}", Textes.nombre(sapin.manquePour(sapin.seuilVeillee()))));
        }
        if (ouvert && !sapin.complet()) {
            Double prevision = sapin.pouce().prevision(maintenant);
            if (prevision != null) {
                jauge.addAll(prevision >= 100 ? l("jauge.prevision-complet")
                        : l("jauge.prevision", "{prevision}", (int) Math.floor(prevision)));
            }
        }
        if (ouvert && sapin.pouce().actif()) {
            LocalDateTime fin = sapin.pouce().fin();
            jauge.addAll(l("jauge.pouce", "{facteur}", Textes.facteur(sapin.pouce().facteurConfig()),
                    "{fin}", fin == null ? "" : Textes.jour(fin.toLocalDate()) + " " + Textes.heure(fin.toLocalTime())));
        }
        poser(sapin.caseJauge, ObjetConfig.simple(Material.SPRUCE_SAPLING).creer(plugin.messages().objet(M + "jauge.nom",
                "{pourcent}", (int) Math.floor(pourcent)), lore(jauge)), null);

        // ---- à quoi ça sert
        poser(sapin.caseExplication, ObjetConfig.simple(Material.BOOK).creer(plugin.messages().objet(M + "explication.nom"),
                lore(l("explication.lore", "{flocons-par-point}", sapin.floconsParPoint(), "{seuil}", Textes.facteur(sapin.seuilVeillee()),
                        "{min}", Textes.nombre(sapin.seuil(1))))), null);

        // ---- ton rang
        long total = sapin.contribution(uuid);
        List<String> r = new ArrayList<>();
        if (rang <= 0) {
            r.addAll(l("rang.aucun", "{total}", Textes.nombre(total), "{min}", Textes.nombre(sapin.seuil(1)),
                    "{reste}", Textes.nombre(Math.max(0, sapin.seuil(1) - total))));
        } else {
            r.addAll(l("rang.lore", "{total}", Textes.nombre(total), "{offert}", Textes.nombre(plugin.compteurs().valeur(uuid, "offert")),
                    "{multiplicateur}", rang, "{place}", sapin.place(uuid), "{contributeurs}", sapin.contributeurs()));
            if (rang < sapin.rangMax()) {
                String cle = rang + 1 >= sapin.rangMax() ? "rang.suivant-max" : "rang.suivant";
                r.addAll(l(cle, "{suivant}", sapin.etoiles(rang + 1), "{reste}", Textes.nombre(Math.max(0, sapin.seuil(rang + 1) - total)),
                        "{multiplicateur}", rang + 1));
            } else {
                r.addAll(l("rang.max"));
            }
        }
        r.addAll(l("rang.seuils", "{r1}", Textes.nombre(sapin.seuil(1)), "{r2}", Textes.nombre(sapin.seuil(2)),
                "{r3}", Textes.nombre(sapin.seuil(3))));
        poser(sapin.caseRang, ObjetConfig.simple(Material.PLAYER_HEAD).creer(plugin.messages().objet(rang <= 0 ? M + "rang.nom-aucun" : M + "rang.nom",
                "{rang}", sapin.etoiles(rang)), lore(r)), null);

        // ---- meilleurs décorateurs
        List<String> t = new ArrayList<>();
        List<Sapin.Part> top = sapin.top();
        for (int i = 0; i < top.size(); i++) {
            t.add(b("top.ligne", "{rang}", i + 1, "{nom}", top.get(i).pseudo(), "{valeur}", Textes.nombre(top.get(i).total()),
                    "{etoiles}", sapin.etoiles(sapin.niveauDe(top.get(i).total()))));
        }
        if (t.isEmpty()) {
            t.addAll(l("top.vide"));
        }
        t.addAll(l("top.pied"));
        poser(sapin.caseTop, ObjetConfig.simple(Material.GOLDEN_HELMET).creer(plugin.messages().objet(M + "top.nom"), lore(t)), null);

        // ---- paliers
        List<Sapin.Palier> paliers = sapin.paliers();
        for (int i = 0; i < paliers.size() && i < sapin.casesPaliers.size(); i++) {
            Sapin.Palier p = paliers.get(i);
            boolean fait = sapin.palierFait(p);
            boolean prochain = !fait && suivant == p;
            String etat = fait ? "fait" : prochain ? "prochain" : "avenir";
            List<String> lp = new ArrayList<>(plugin.messages().lignes("sapin.paliers." + p.pourcent() + ".description"));
            String cadeau1 = sapin.cadeauPalier(p, 1);
            if (!cadeau1.isEmpty()) {
                lp.addAll(l("palier.cadeau", "{cadeau}", cadeau1));
                if (rang >= 1) {
                    lp.addAll(l(rang > 1 ? "palier.cadeau-toi" : "palier.cadeau-toi-1", "{cadeau}", sapin.cadeauPalier(p, rang),
                            "{rang}", sapin.etoiles(rang)));
                } else {
                    lp.addAll(l("palier.cadeau-pas-contributeur"));
                }
            }
            if (p.veillee()) {
                lp.addAll(l("palier.veillee-" + etat, "{seuil}", Textes.facteur(sapin.seuilVeillee())));
            } else {
                lp.addAll(l("palier." + etat, "{manque}", Textes.nombre(sapin.manquePour(p.pourcent()))));
            }
            ItemStack objet = p.icone().creer(plugin.messages().objet(M + "palier.nom-" + etat, "{palier}", p.pourcent(),
                    "{nom}", sapin.nomPalier(p)), lore(lp));
            poser(sapin.casesPaliers.get(i), lueur(objet, fait), null);
        }

        // ---- offrandes
        int resteJour = sapin.resteOffrandeJour(uuid, jour);
        double facteur = sapin.pouce().facteur();
        List<Integer> montants = sapin.offrandes();
        for (int i = 0; i < montants.size() && i < sapin.casesOffrandes.size(); i++) {
            int montant = montants.get(i);
            List<String> lo;
            if (!ouvert) {
                lo = l("offrande.ferme");
            } else if (resteJour <= 0) {
                lo = l("offrande.part-complete", "{plafond}", Textes.nombre(sapin.plafondOffrande()));
            } else {
                int compte = Math.min(montant, resteJour);
                lo = new ArrayList<>(l(sapin.complet() ? "offrande.lore-complet" : facteur > 1 ? "offrande.lore-pouce" : "offrande.lore",
                        "{montant}", Textes.nombre(compte), "{points}", Textes.nombre(Math.round(compte * facteur))));
                lo.addAll(l("offrande.clic"));
            }
            poser(sapin.casesOffrandes.get(i), ObjetConfig.simple(Material.SNOWBALL).creer(
                    plugin.messages().objet(M + "offrande.nom", "{montant}", Textes.nombre(montant)), lore(lo)), type -> {
                sapin.offrir(joueur, montant);
                // l'offrande est comptée une fois le retrait vu sur le solde (quelques ticks) : menu relu ensuite
                for (long delai : new long[]{8L, 30L}) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (joueur.isOnline() && joueur.getOpenInventory().getTopInventory().getHolder() == this) {
                            rafraichir();
                        }
                    }, delai);
                }
            });
        }

        // ---- aujourd'hui
        List<String> lj = new ArrayList<>(l("jour.lore", "{offert}", Textes.nombre(sapin.offertJour(uuid, jour)),
                "{plafond}", Textes.nombre(sapin.plafondOffrande()), "{reste}", Textes.nombre(resteJour),
                "{points}", Textes.nombre(sapin.contributionJour(uuid, jour)), "{flocons-par-point}", sapin.floconsParPoint()));
        if (ouvert && sapin.pouce().actif()) {
            lj.addAll(l("jour.pouce", "{facteur}", Textes.facteur(sapin.pouce().facteurConfig())));
        }
        poser(sapin.caseJour, ObjetConfig.simple(Material.CLOCK).creer(plugin.messages().objet(M + "jour.nom"), lore(lj)), null);

        // ---- retour
        if (!sapin.commandesRetour.isEmpty()) {
            poser(sapin.caseRetour, ObjetConfig.simple(Material.PAPER).creer(plugin.messages().objet(M + "retour.nom"),
                    lore(l("retour.lore"))), type -> {
                joueur.closeInventory();
                for (String c : sapin.commandesRetour) {
                    joueur.performCommand(Messages.remplacer(c, "{joueur}", joueur.getName()));
                }
                plugin.son("menu-retour", joueur);
            });
        }
        accent(sapin.accent);
        cadre(sapin.cadre);
    }
}
