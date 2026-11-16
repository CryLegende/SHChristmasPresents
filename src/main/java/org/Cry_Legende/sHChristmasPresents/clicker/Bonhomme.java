package org.Cry_Legende.sHChristmasPresents.clicker;

import org.Cry_Legende.sHChristmasPresents.rendu.Affine;
import org.Cry_Legende.sHChristmasPresents.rendu.Objet3D;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Un bonhomme de neige du clicker montré par paquets. Au repos, la tête regarde autour d'elle et les bras se
 * balancent (une image toutes les 10 ticks, lissée par le client). Au clic : il s'écrase, lève les bras, puis
 * s'étire et revient au repos ; un nouveau clic relance l'animation au plus toutes les 4 ticks. Tout le monde voit les
 * clics des autres. Fil principal.
 */
final class Bonhomme extends Objet3D {

    private final ModeleBonhomme modele;
    private final Affine[][] repos;
    private final Affine[] ecrase;
    private final Affine[] etire;
    private final Affine[] base;
    private final int decalage;
    private final BiConsumer<Bonhomme, Player> auClic;
    private long clicA = Long.MIN_VALUE / 2;
    private int image = -1;
    final float lacet;

    Bonhomme(World monde, double x, double y, double z, float lacet, float echelle, ModeleBonhomme modele,
             BiConsumer<Bonhomme, Player> auClic) {
        super(monde, x, y, z, lacet, echelle, modele.pieces, ModeleBonhomme.LARGEUR * echelle,
                ModeleBonhomme.HAUTEUR * echelle, 64);
        this.lacet = lacet;
        this.modele = modele;
        this.auClic = auClic;
        this.decalage = ThreadLocalRandom.current().nextInt(ModeleBonhomme.IMAGES_REPOS * ModeleBonhomme.PAS);
        int n = modele.pieces.length;
        this.repos = new Affine[ModeleBonhomme.IMAGES_REPOS][n];
        for (int f = 0; f < ModeleBonhomme.IMAGES_REPOS; f++) {
            for (int i = 0; i < n; i++) {
                repos[f][i] = modele.repos[f][i] == null ? null : placer(modele.repos[f][i]);
            }
        }
        this.ecrase = new Affine[n];
        this.etire = new Affine[n];
        this.base = new Affine[n];
        for (int i = 0; i < n; i++) {
            base[i] = pose(i);
            ecrase[i] = placer(modele.ecrase[i]);
            etire[i] = placer(modele.etire[i]);
        }
    }

    @Override
    protected void animer(long tick) {
        long depuis = tick - clicA;
        if (depuis == 2) {
            envoyer(etire, 3);
            return;
        }
        if (depuis == 5) {
            // Retour au repos : l'image courante, corps compris
            int f = imageDe(tick);
            for (int i = 0; i < pieces.length; i++) {
                Affine a = repos[f][i];
                poser(i, a != null ? a : base[i], 4);
            }
            image = f;
            return;
        }
        if (depuis < 9) {
            return;
        }
        int f = imageDe(tick);
        if ((tick + decalage) % ModeleBonhomme.PAS != 0 || f == image) {
            return;
        }
        image = f;
        // L'image suivante, atteinte en PAS ticks
        int suivante = (f + 1) % ModeleBonhomme.IMAGES_REPOS;
        Affine[] im = repos[suivante];
        for (int i = 0; i < pieces.length; i++) {
            if (im[i] != null) {
                poser(i, im[i], ModeleBonhomme.PAS);
            }
        }
    }

    private int imageDe(long tick) {
        return (int) (((tick + decalage) / ModeleBonhomme.PAS) % ModeleBonhomme.IMAGES_REPOS);
    }

    private void envoyer(Affine[] poses, int ticks) {
        for (int i = 0; i < pieces.length; i++) {
            poser(i, poses[i], ticks);
        }
    }

    /** Animation du clic (au plus toutes les 4 ticks). */
    void sauter(long tick) {
        if (tick - clicA < 4) {
            return;
        }
        clicA = tick;
        envoyer(ecrase, 2);
    }

    Location position() {
        return new Location(monde, x, y, z, lacet, 0);
    }

    @Override
    protected void clic(Player joueur, boolean gauche) {
        auClic.accept(this, joueur);
    }
}
