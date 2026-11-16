package org.Cry_Legende.sHChristmasPresents.rendu;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.entity.Player;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Paquets des entités d'affichage et des zones de clic : elles n'existent que chez les joueurs qui les reçoivent, le
 * serveur n'en garde aucune. Seule classe (avec {@link Entrees}) qui touche à packetevents. Un paquet est construit pour
 * chaque joueur (packetevents réutilise le tampon d'un paquet tant qu'il n'est pas envoyé).
 */
public final class Paquets {

    // Métadonnées (26.3) d'un display, puis d'un item display / block display, puis d'une interaction
    private static final int INTERPOLATION_DELAY = 8;
    private static final int TRANSFORMATION_DURATION = 9;
    private static final int TRANSLATION = 11;
    private static final int SCALE = 12;
    private static final int LEFT_ROTATION = 13;
    private static final int RIGHT_ROTATION = 14;
    private static final int VIEW_RANGE = 17;
    private static final int CONTENT = 23;
    private static final int WIDTH = 8;
    private static final int HEIGHT = 9;
    private static final int RESPONSIVE = 10;

    private Paquets() {
    }

    public static int nouvelId() {
        return SpigotReflectionUtil.generateEntityId();
    }

    /** Montre une pièce au joueur, à la pose donnée, entité posée en (x, y, z) sans rotation propre. */
    static void montrer(Player joueur, int id, double x, double y, double z, Piece piece, Affine pose, float portee) {
        envoyer(joueur, new WrapperPlayServerSpawnEntity(id, Optional.of(UUID.randomUUID()),
                piece.bloc() ? EntityTypes.BLOCK_DISPLAY : EntityTypes.ITEM_DISPLAY,
                new Vector3d(x, y, z), 0.0f, 0.0f, 0.0f, 0, Optional.empty()));
        List<EntityData<?>> data = new ArrayList<>(9);
        data.add(new EntityData<>(INTERPOLATION_DELAY, EntityDataTypes.INT, 0));
        data.add(new EntityData<>(TRANSFORMATION_DURATION, EntityDataTypes.INT, 0));
        pose(data, pose);
        data.add(new EntityData<>(VIEW_RANGE, EntityDataTypes.FLOAT, portee));
        if (piece.bloc()) {
            data.add(new EntityData<>(CONTENT, EntityDataTypes.BLOCK_STATE, piece.etatBloc()));
        } else {
            data.add(new EntityData<>(CONTENT, EntityDataTypes.ITEMSTACK, piece.objetPaquet()));
        }
        envoyer(joueur, new WrapperPlayServerEntityMetadata(id, data));
    }

    /** Nouvelle pose atteinte en {@code ticks} ticks : le client lisse le mouvement. */
    static void poser(Player joueur, int id, Affine pose, int ticks) {
        List<EntityData<?>> data = new ArrayList<>(6);
        data.add(new EntityData<>(INTERPOLATION_DELAY, EntityDataTypes.INT, 0));
        data.add(new EntityData<>(TRANSFORMATION_DURATION, EntityDataTypes.INT, Math.max(0, ticks)));
        pose(data, pose);
        envoyer(joueur, new WrapperPlayServerEntityMetadata(id, data));
    }

    /** Zone de clic invisible : une entité interaction dont le pied est centré en (x, y, z). */
    static void zone(Player joueur, int id, double x, double y, double z, float largeur, float hauteur) {
        envoyer(joueur, new WrapperPlayServerSpawnEntity(id, Optional.of(UUID.randomUUID()), EntityTypes.INTERACTION,
                new Vector3d(x, y, z), 0.0f, 0.0f, 0.0f, 0, Optional.empty()));
        envoyer(joueur, new WrapperPlayServerEntityMetadata(id, List.of(
                new EntityData<>(WIDTH, EntityDataTypes.FLOAT, largeur),
                new EntityData<>(HEIGHT, EntityDataTypes.FLOAT, hauteur),
                new EntityData<>(RESPONSIVE, EntityDataTypes.BOOLEAN, true))));
    }

    static void retirer(Player joueur, int[] ids) {
        if (ids.length > 0) {
            envoyer(joueur, new WrapperPlayServerDestroyEntities(ids));
        }
    }

    private static void pose(List<EntityData<?>> data, Affine a) {
        data.add(new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, vecteur(a.translation())));
        data.add(new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, vecteur(a.scale())));
        data.add(new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION, quaternion(a.left())));
        data.add(new EntityData<>(RIGHT_ROTATION, EntityDataTypes.QUATERNION, quaternion(a.right())));
    }

    private static com.github.retrooper.packetevents.util.Vector3f vecteur(Vector3fc v) {
        return new com.github.retrooper.packetevents.util.Vector3f(v.x(), v.y(), v.z());
    }

    private static Quaternion4f quaternion(Quaternionfc q) {
        return new Quaternion4f(q.x(), q.y(), q.z(), q.w());
    }

    private static void envoyer(Player joueur, PacketWrapper<?> paquet) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(joueur, paquet);
        } catch (RuntimeException ignored) {
            // joueur en cours de déconnexion
        }
    }
}
