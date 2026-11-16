package org.Cry_Legende.sHChristmasPresents.rendu;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Un modèle posé dans un monde et montré par paquets aux joueurs proches (voir {@link Scene}). Chaque pièce garde sa
 * pose courante (repère de l'entité : pied du modèle, orientation et échelle déjà appliquées) ; une sous-classe anime
 * en appelant {@link #poser(int, Affine, int)} et répond aux clics sur sa zone. Fil principal uniquement.
 */
public abstract class Objet3D {

    private static final Vector3f ZERO = new Vector3f();

    protected final World monde;
    protected final double x;
    protected final double y;
    protected final double z;
    private final Quaternionf orientation;
    private final float echelle;
    protected final Piece[] pieces;
    private final int[] ids;
    private final int[] tousIds;
    private final int zoneId;
    private final float zoneLargeur;
    private final float zoneHauteur;
    private final Affine[] poses;
    private final Map<UUID, Player> spectateurs = new HashMap<>();
    private final int portee;

    /**
     * @param lacet       lacet Minecraft en degrés (0 = le devant du modèle, +z, regarde le sud)
     * @param zoneLargeur 0 = pas de zone de clic
     */
    protected Objet3D(World monde, double x, double y, double z, float lacet, float echelle, Piece[] pieces,
                      float zoneLargeur, float zoneHauteur, int portee) {
        this.monde = monde;
        this.x = x;
        this.y = y;
        this.z = z;
        this.orientation = new Quaternionf().rotationY((float) Math.toRadians(-lacet));
        this.echelle = echelle;
        this.pieces = pieces;
        this.ids = new int[pieces.length];
        this.poses = new Affine[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            ids[i] = Paquets.nouvelId();
            poses[i] = placer(Affine.of(pieces[i].matrice()));
        }
        this.zoneLargeur = zoneLargeur;
        this.zoneHauteur = zoneHauteur;
        this.zoneId = zoneLargeur > 0 ? Paquets.nouvelId() : -1;
        this.tousIds = new int[ids.length + (zoneId >= 0 ? 1 : 0)];
        System.arraycopy(ids, 0, tousIds, 0, ids.length);
        if (zoneId >= 0) {
            tousIds[ids.length] = zoneId;
        }
        this.portee = Math.max(8, portee);
    }

    /** Une pose du repère du modèle placée dans celui de l'entité (orientation et échelle de l'objet). */
    public final Affine placer(Affine modele) {
        return modele.placed(ZERO, orientation, echelle);
    }

    /** Appelé à chaque tick tant qu'au moins un joueur voit l'objet. */
    protected void animer(long tick) {
    }

    /** Clic sur la zone (fil principal). */
    protected void clic(Player joueur, boolean gauche) {
    }

    /** Pose envoyée à un joueur qui arrive (par défaut la pose courante). */
    protected Affine poseArrivee(int piece) {
        return poses[piece];
    }

    /** Nouvelle pose d'une pièce, envoyée à tous ceux qui voient l'objet, atteinte en ticks. */
    protected final void poser(int piece, Affine pose, int ticks) {
        poses[piece] = pose;
        for (Player p : spectateurs.values()) {
            Paquets.poser(p, ids[piece], pose, ticks);
        }
    }

    protected final Affine pose(int piece) {
        return poses[piece];
    }

    protected final boolean vu() {
        return !spectateurs.isEmpty();
    }

    protected final Collection<Player> spectateurs() {
        return spectateurs.values();
    }

    final void montrer(Player joueur) {
        float vue = portee / 48f;
        for (int i = 0; i < ids.length; i++) {
            Paquets.montrer(joueur, ids[i], x, y, z, pieces[i], poseArrivee(i), vue);
        }
        if (zoneId >= 0) {
            Paquets.zone(joueur, zoneId, x, y, z, zoneLargeur, zoneHauteur);
        }
        spectateurs.put(joueur.getUniqueId(), joueur);
    }

    final void cacher(Player joueur) {
        if (spectateurs.remove(joueur.getUniqueId()) != null) {
            Paquets.retirer(joueur, tousIds);
        }
    }

    final void cacherTous() {
        for (Player p : spectateurs.values()) {
            Paquets.retirer(p, tousIds);
        }
        spectateurs.clear();
    }

    final void oublier(UUID uuid) {
        spectateurs.remove(uuid);
    }

    final boolean voit(UUID uuid) {
        return spectateurs.containsKey(uuid);
    }

    final int zoneId() {
        return zoneId;
    }

    final int portee() {
        return portee;
    }

    public final World monde() {
        return monde;
    }

    public final double x() {
        return x;
    }

    public final double y() {
        return y;
    }

    public final double z() {
        return z;
    }

    public final float echelle() {
        return echelle;
    }
}
