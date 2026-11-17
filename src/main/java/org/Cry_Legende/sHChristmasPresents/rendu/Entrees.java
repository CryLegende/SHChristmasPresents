package org.Cry_Legende.sHChristmasPresents.rendu;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.UUID;

/**
 * Clics sur les zones des objets de la {@link Scene}. Le serveur ne connaît pas ces entités : le paquet s'arrête ici.
 * Fil réseau ; la scène renvoie le clic au fil principal. Le clic droit arrive en un ou deux paquets (INTERACT_AT,
 * INTERACT) : le premier compte.
 */
final class Entrees extends PacketListenerAbstract {

    private final Scene scene;
    private final java.util.Map<UUID, Long> droits = new java.util.concurrent.ConcurrentHashMap<>();

    private Entrees(Scene scene) {
        super(PacketListenerPriority.NORMAL);
        this.scene = scene;
    }

    static Object enregistrer(Scene scene) {
        return PacketEvents.getAPI().getEventManager().registerListener(new Entrees(scene));
    }

    static void retirer(Object ecoute) {
        PacketEvents.getAPI().getEventManager().unregisterListener((PacketListenerCommon) ecoute);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        UUID uuid = event.getUser() == null ? null : event.getUser().getUUID();
        if (uuid == null) {
            return;
        }
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            // Depuis 26.1, le clic gauche sur une entité a son propre paquet
            if (scene.clic(uuid, new WrapperPlayClientAttack(event).getEntityId(), true)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }
        WrapperPlayClientInteractEntity paquet = new WrapperPlayClientInteractEntity(event);
        WrapperPlayClientInteractEntity.InteractAction action = paquet.getAction();
        int id = paquet.getEntityId();
        if (!scene.possede(id)) {
            return;
        }
        event.setCancelled(true);
        scene.trace("paquet " + action + " main " + paquet.getHand() + " sur " + id);
        if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            scene.clic(uuid, id, true);
            return;
        }
        // Clic droit : selon la version, le jeu envoie INTERACT_AT puis INTERACT, ou un seul des deux. Le premier
        // compte, l'autre (moins de 200 ms après) est ignoré ; la seconde main ne compte pas.
        if (paquet.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        long maintenant = System.currentTimeMillis();
        Long dernier = droits.get(uuid);
        if (dernier != null && maintenant - dernier < 200) {
            return;
        }
        droits.put(uuid, maintenant);
        scene.clic(uuid, id, false);
    }
}
