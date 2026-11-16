package org.Cry_Legende.sHChristmasPresents.clicker;

import org.Cry_Legende.sHChristmasPresents.rendu.Affine;
import org.Cry_Legende.sHChristmasPresents.rendu.Piece;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Le bonhomme de neige du clicker, en block displays : trois boules de neige (chacune deux cubes croisés à 45° pour
 * l'arrondir), écharpe rouge, boutons, visage (yeux, carotte, sourire), chapeau haut de forme, bras en bois. Pied à
 * l'origine, devant vers +z, environ 2,25 blocs de haut. Groupes animés : tete (pivot au cou), bras-g et bras-d (pivot
 * à l'épaule). Les poses de chaque image sont calculées une fois, dans le repère du modèle.
 */
final class ModeleBonhomme {

    static final float HAUTEUR = 2.25f;
    static final float LARGEUR = 1.0f;
    /** Images du repos (une toutes les PAS ticks, en boucle). */
    static final int IMAGES_REPOS = 8;
    static final int PAS = 10;

    private static final Vector3f COU = new Vector3f(0, 1.40f, 0);
    private static final Vector3f EPAULE_G = new Vector3f(-0.29f, 1.13f, 0);
    private static final Vector3f EPAULE_D = new Vector3f(0.29f, 1.13f, 0);

    final Piece[] pieces;
    /** repos[image][pièce] : null pour une pièce qui ne bouge pas au repos. */
    final Affine[][] repos;
    final Affine[] ecrase;
    final Affine[] etire;

    ModeleBonhomme() {
        List<Piece> p = new ArrayList<>();
        // Corps
        boite(p, "corps", Material.SNOW_BLOCK, 0, 0.39f, 0, 0.80f, 0.78f, 0.80f, 0, 0, 0);
        boite(p, "corps", Material.SNOW_BLOCK, 0, 0.39f, 0, 0.66f, 0.70f, 0.66f, 0, 45, 0);
        boite(p, "corps", Material.SNOW_BLOCK, 0, 1.06f, 0, 0.62f, 0.60f, 0.62f, 0, 0, 0);
        boite(p, "corps", Material.SNOW_BLOCK, 0, 1.06f, 0, 0.50f, 0.54f, 0.50f, 0, 45, 0);
        for (float by : new float[]{0.90f, 1.04f, 1.18f}) {
            boite(p, "corps", Material.COAL_BLOCK, 0, by, 0.315f, 0.075f, 0.075f, 0.04f, 0, 0, 0);
        }
        boite(p, "corps", Material.RED_WOOL, 0, 1.37f, 0, 0.58f, 0.10f, 0.58f, 0, 0, 0);
        boite(p, "corps", Material.RED_WOOL, 0.215f, 1.21f, 0.29f, 0.13f, 0.30f, 0.05f, 0, 0, -10);
        // Tête
        boite(p, "tete", Material.SNOW_BLOCK, 0, 1.63f, 0, 0.48f, 0.46f, 0.48f, 0, 0, 0);
        boite(p, "tete", Material.SNOW_BLOCK, 0, 1.63f, 0, 0.32f, 0.40f, 0.32f, 0, 45, 0);
        boite(p, "tete", Material.COAL_BLOCK, -0.10f, 1.69f, 0.243f, 0.07f, 0.07f, 0.03f, 0, 0, 0);
        boite(p, "tete", Material.COAL_BLOCK, 0.10f, 1.69f, 0.243f, 0.07f, 0.07f, 0.03f, 0, 0, 0);
        boite(p, "tete", Material.ORANGE_CONCRETE, 0, 1.615f, 0.35f, 0.06f, 0.06f, 0.22f, 8, 0, 0);
        boite(p, "tete", Material.COAL_BLOCK, -0.10f, 1.55f, 0.243f, 0.045f, 0.045f, 0.03f, 0, 0, 0);
        boite(p, "tete", Material.COAL_BLOCK, -0.035f, 1.527f, 0.243f, 0.045f, 0.045f, 0.03f, 0, 0, 0);
        boite(p, "tete", Material.COAL_BLOCK, 0.035f, 1.527f, 0.243f, 0.045f, 0.045f, 0.03f, 0, 0, 0);
        boite(p, "tete", Material.COAL_BLOCK, 0.10f, 1.55f, 0.243f, 0.045f, 0.045f, 0.03f, 0, 0, 0);
        boite(p, "tete", Material.BLACK_CONCRETE, 0, 1.88f, 0, 0.58f, 0.04f, 0.58f, 0, 0, 0);
        boite(p, "tete", Material.BLACK_CONCRETE, 0, 2.07f, 0, 0.36f, 0.34f, 0.36f, 0, 0, 0);
        boite(p, "tete", Material.RED_CONCRETE, 0, 1.93f, 0, 0.37f, 0.06f, 0.37f, 0, 0, 0);
        // Bras : un bâton depuis l'épaule, levé de 55° vers l'extérieur, et un petit doigt au bout
        bras(p, "bras-g", EPAULE_G, 55);
        bras(p, "bras-d", EPAULE_D, -55);
        this.pieces = p.toArray(new Piece[0]);

        this.repos = new Affine[IMAGES_REPOS][];
        for (int f = 0; f < IMAGES_REPOS; f++) {
            double phi = 2 * Math.PI * f / IMAGES_REPOS;
            Pose pose = new Pose();
            pose.teteLacet = (float) (9 * Math.sin(phi));
            pose.teteRoulis = (float) (4 * Math.sin(phi + Math.PI / 2));
            pose.brasG = (float) (7 * Math.sin(phi + Math.PI / 3));
            pose.brasD = (float) (-7 * Math.sin(phi + Math.PI));
            repos[f] = poses(pose, true);
        }
        Pose c1 = new Pose();
        c1.corps = new Vector3f(1.07f, 0.88f, 1.07f);
        c1.brasG = -30;
        c1.brasD = 30;
        c1.teteTangage = -8;
        this.ecrase = poses(c1, false);
        Pose c2 = new Pose();
        c2.corps = new Vector3f(0.97f, 1.06f, 0.97f);
        c2.brasG = -10;
        c2.brasD = 10;
        c2.teteTangage = 4;
        this.etire = poses(c2, false);
    }

    private static final class Pose {
        Vector3f corps = new Vector3f(1, 1, 1);
        float teteLacet;
        float teteRoulis;
        float teteTangage;
        float brasG;
        float brasD;
    }

    /** Pose de chaque pièce ; seulementAnimees : null pour le corps (immobile au repos). */
    private Affine[] poses(Pose pose, boolean seulementAnimees) {
        Affine[] out = new Affine[pieces.length];
        Matrix4f corps = new Matrix4f().scale(pose.corps);
        for (int i = 0; i < pieces.length; i++) {
            String g = pieces[i].groupe();
            Matrix4f groupe = switch (g) {
                case "tete" -> autour(COU, new Matrix4f().rotateY((float) Math.toRadians(pose.teteLacet))
                        .rotateZ((float) Math.toRadians(pose.teteRoulis)).rotateX((float) Math.toRadians(pose.teteTangage)));
                case "bras-g" -> autour(EPAULE_G, new Matrix4f().rotateZ((float) Math.toRadians(pose.brasG)));
                case "bras-d" -> autour(EPAULE_D, new Matrix4f().rotateZ((float) Math.toRadians(pose.brasD)));
                default -> null;
            };
            if (groupe == null && seulementAnimees) {
                continue;
            }
            Matrix4f m = new Matrix4f(corps);
            if (groupe != null) {
                m.mul(groupe);
            }
            m.mul(pieces[i].matrice());
            out[i] = Affine.of(m);
        }
        return out;
    }

    private static Matrix4f autour(Vector3f pivot, Matrix4f rotation) {
        return new Matrix4f().translate(pivot).mul(rotation).translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /** Une boîte de taille (sx, sy, sz) centrée en (cx, cy, cz), tournée autour de son centre (degrés, X puis Y puis Z). */
    private static void boite(List<Piece> p, String groupe, Material m, float cx, float cy, float cz,
                              float sx, float sy, float sz, float rx, float ry, float rz) {
        Matrix4f mat = new Matrix4f().translate(cx, cy, cz)
                .rotateY((float) Math.toRadians(ry)).rotateX((float) Math.toRadians(rx)).rotateZ((float) Math.toRadians(rz))
                .scale(sx, sy, sz).translate(-0.5f, -0.5f, -0.5f);
        p.add(Piece.bloc(Bukkit.createBlockData(m), mat, groupe));
    }

    private static void bras(List<Piece> p, String groupe, Vector3f epaule, float angle) {
        double a = Math.toRadians(angle);
        float dx = (float) -Math.sin(a), dy = (float) Math.cos(a);
        float longueur = 0.62f;
        boite(p, groupe, Material.STRIPPED_SPRUCE_LOG, epaule.x + dx * longueur / 2, epaule.y + dy * longueur / 2, 0,
                0.06f, longueur, 0.06f, 0, 0, angle);
        float bout = longueur * 0.8f;
        double b = Math.toRadians(angle * 0.4f);
        float ex = epaule.x + dx * bout, ey = epaule.y + dy * bout;
        float fx = (float) -Math.sin(b), fy = (float) Math.cos(b);
        boite(p, groupe, Material.STRIPPED_SPRUCE_LOG, ex + fx * 0.08f, ey + fy * 0.08f, 0, 0.045f, 0.17f, 0.045f, 0, 0, angle * 0.4f);
    }
}
