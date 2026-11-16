package org.Cry_Legende.sHChristmasPresents.commun;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Son lu en config : « NOM [volume] [hauteur] », ancien nom (ENTITY_PLAYER_LEVELUP) ou clé (minecraft:entity.player.levelup). */
public record Son(Sound son, float volume, float hauteur) {

    public static Son lire(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        String[] parties = texte.trim().split("\\s+");
        Sound son = sound(parties[0]);
        if (son == null) {
            return null;
        }
        float volume = parties.length > 1 ? nombre(parties[1], 1f) : 1f;
        float hauteur = parties.length > 2 ? nombre(parties[2], 1f) : 1f;
        return new Son(son, volume, hauteur);
    }

    private static float nombre(String texte, float defaut) {
        try {
            return Float.parseFloat(texte);
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    /** Ancien nom ou clé de registre ; null si inconnu. */
    public static Sound sound(String brut) {
        if (brut == null || brut.isBlank()) {
            return null;
        }
        String s = brut.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey cle = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return cle == null ? null : Registry.SOUND_EVENT.get(cle);
        }
        try {
            return (Sound) Sound.class.getField(s.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    public void jouer(Player joueur) {
        joueur.playSound(joueur.getLocation(), son, volume, hauteur);
    }
}
