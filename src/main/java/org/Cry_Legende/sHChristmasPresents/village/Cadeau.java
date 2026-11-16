package org.Cry_Legende.sHChristmasPresents.village;

import org.Cry_Legende.sHChristmasPresents.rendu.Affine;
import org.Cry_Legende.sHChristmasPresents.rendu.Objet3D;
import org.Cry_Legende.sHChristmasPresents.rendu.Piece;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Un cadeau du village montré par paquets. Il apparaît en grandissant, puis de temps en temps son couvercle se
 * soulève et tremble, comme s'il voulait s'ouvrir (vitesse selon la rareté). Touché : le couvercle saute, la boîte
 * rétrécit, puis il disparaît. Fil principal.
 */
final class Cadeau extends Objet3D {

    /** Images du couvercle : soulèvement, bascule avant/arrière, bascule de côté, tassement de la boîte, durée. */
    private record Image(float leve, float avant, float cote, float tasse, int duree) {
    }

    private static final Image[] TREMBLEMENT = {
            new Image(0.07f, 9f, 0f, 0.94f, 3),
            new Image(0.02f, -5f, 2f, 1f, 3),
            new Image(0.08f, 0f, 8f, 0.95f, 3),
            new Image(0.015f, 0f, -4f, 1f, 3),
            new Image(0f, 0f, 0f, 1f, 4)
    };
    private static final Image SAUT = new Image(0.6f, -38f, 6f, 0.9f, 4);
    private static final int CROISSANCE = 6;

    final String rarete;
    private final Modeles.Modele modele;
    private final double vitesse;
    private final Affine[][] images;
    private final Affine[] saut;
    private final Affine[] petites;
    private final Affine[] repos;
    private final int[] animees;
    private final double demiLargeur;
    private final double hauteur;
    private final BiConsumer<Cadeau, Player> auClic;
    private long prochaine;
    private int etape = -1;
    private boolean grandi;
    private long ouvertA = -1;

    Cadeau(World monde, double x, double y, double z, float lacet, float echelle, double vitesse, Modeles.Modele modele,
           String rarete, BiConsumer<Cadeau, Player> auClic) {
        super(monde, x, y, z, lacet, echelle, modele.pieces.toArray(new Piece[0]),
                modele.largeur * echelle + 0.2f, modele.hauteur * echelle + 0.1f, 40);
        this.rarete = rarete;
        this.modele = modele;
        this.vitesse = Math.max(0.3, vitesse);
        this.auClic = auClic;
        this.demiLargeur = modele.largeur * echelle / 2.0;
        this.hauteur = modele.hauteur * echelle;
        int n = pieces.length;
        int animeesN = 0;
        for (int i = 0; i < n; i++) {
            if (i == modele.boite || modele.couvercle[i]) {
                animeesN++;
            }
        }
        this.animees = new int[animeesN];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (i == modele.boite || modele.couvercle[i]) {
                animees[k++] = i;
            }
        }
        this.images = new Affine[TREMBLEMENT.length][];
        for (int f = 0; f < TREMBLEMENT.length; f++) {
            images[f] = image(TREMBLEMENT[f]);
        }
        this.saut = image(SAUT);
        this.repos = new Affine[n];
        this.petites = new Affine[n];
        Matrix4f minuscule = new Matrix4f().scale(0.04f);
        for (int i = 0; i < n; i++) {
            repos[i] = pose(i);
            petites[i] = placer(Affine.of(new Matrix4f(minuscule).mul(pieces[i].matrice())));
        }
        this.prochaine = attente(0);
    }

    /** Poses (repère de l'entité) des pièces animées pour une image. */
    private Affine[] image(Image im) {
        Affine[] out = new Affine[animees.length];
        float bas = modele.basBoite;
        float hautBoite = modele.hautBoite - bas;
        Vector3f pivot = modele.pivot;
        float descente = (1f - im.tasse()) * hautBoite;
        for (int k = 0; k < animees.length; k++) {
            int i = animees[k];
            Matrix4f m;
            if (i == modele.boite) {
                float large = 1f + (1f - im.tasse()) / 2f;
                m = new Matrix4f().translate(pivot.x, bas, pivot.z).scale(large, im.tasse(), large)
                        .translate(-pivot.x, -bas, -pivot.z).mul(pieces[i].matrice());
            } else {
                m = new Matrix4f().translate(0, im.leve() - descente, 0)
                        .translate(pivot)
                        .rotateX((float) Math.toRadians(im.avant()))
                        .rotateZ((float) Math.toRadians(im.cote()))
                        .translate(-pivot.x, -pivot.y, -pivot.z)
                        .mul(pieces[i].matrice());
            }
            out[k] = placer(Affine.of(m));
        }
        return out;
    }

    private long attente(long maintenant) {
        int min = (int) Math.round(60 / vitesse);
        int max = (int) Math.round(140 / vitesse);
        return maintenant + ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    @Override
    protected Affine poseArrivee(int piece) {
        return grandi ? pose(piece) : petites[piece];
    }

    @Override
    protected void animer(long tick) {
        if (ouvertA >= 0) {
            return;
        }
        if (!grandi) {
            grandi = true;
            for (int i = 0; i < pieces.length; i++) {
                poser(i, repos[i], CROISSANCE);
            }
            prochaine = Math.max(prochaine, tick + CROISSANCE + 20);
            return;
        }
        if (prochaine == 0) {
            prochaine = attente(tick);
        }
        if (tick < prochaine) {
            return;
        }
        if (etape < 0) {
            etape = 0;
        }
        if (etape >= TREMBLEMENT.length) {
            etape = -1;
            prochaine = attente(tick);
            return;
        }
        int duree = Math.max(1, (int) Math.round(TREMBLEMENT[etape].duree() / vitesse));
        Affine[] im = images[etape];
        for (int k = 0; k < animees.length; k++) {
            poser(animees[k], im[k], duree);
        }
        etape++;
        prochaine = tick + duree;
    }

    /** Le cadeau s'ouvre : le couvercle saute, puis tout rétrécit. */
    void ouvrir(long tick) {
        if (ouvertA >= 0) {
            return;
        }
        ouvertA = tick;
        for (int k = 0; k < animees.length; k++) {
            poser(animees[k], saut[k], SAUT.duree());
        }
    }

    /** Ticks depuis l'ouverture (−1 si fermé). */
    long ouvertDepuis(long tick) {
        return ouvertA < 0 ? -1 : tick - ouvertA;
    }

    void retrecir() {
        for (int i = 0; i < pieces.length; i++) {
            poser(i, petites[i], 4);
        }
    }

    boolean ouvert() {
        return ouvertA >= 0;
    }

    /**
     * Distance (fraction de 0 à 1 du segment) où le segment [a, a + d] entre dans la boîte du cadeau élargie de
     * marge, ou −1. Boîte alignée sur les axes, carrée (la plus grande largeur du modèle, quelle que soit sa rotation).
     */
    double touche(double ax, double ay, double az, double dx, double dy, double dz, double marge) {
        double[] min = {x - demiLargeur - marge, y - marge, z - demiLargeur - marge};
        double[] max = {x + demiLargeur + marge, y + hauteur + marge, z + demiLargeur + marge};
        double[] a = {ax, ay, az};
        double[] d = {dx, dy, dz};
        double t0 = 0, t1 = 1;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) {
                if (a[i] < min[i] || a[i] > max[i]) {
                    return -1;
                }
            } else {
                double u = (min[i] - a[i]) / d[i];
                double v = (max[i] - a[i]) / d[i];
                if (u > v) {
                    double w = u;
                    u = v;
                    v = w;
                }
                t0 = Math.max(t0, u);
                t1 = Math.min(t1, v);
                if (t0 > t1) {
                    return -1;
                }
            }
        }
        return t0;
    }

    double distanceCarre(double px, double py, double pz) {
        double dx = px - x, dy = py - (y + hauteur / 2), dz = pz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    protected void clic(Player joueur, boolean gauche) {
        auClic.accept(this, joueur);
    }
}
