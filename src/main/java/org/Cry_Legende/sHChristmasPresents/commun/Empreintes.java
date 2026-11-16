package org.Cry_Legende.sHChristmasPresents.commun;

import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Empreinte d'une adresse IP : SHA-256 du sel du serveur et de l'adresse. Sert à compter les comptes d'une même
 * connexion sans garder l'adresse elle-même.
 */
public final class Empreintes {

    private final String sel;

    public Empreintes(String sel) {
        this.sel = sel;
    }

    public String de(InetAddress adresse) {
        if (adresse == null) {
            return null;
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hache = sha.digest((sel + ":" + adresse.getHostAddress()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hache);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String de(Player joueur) {
        InetSocketAddress adresse = joueur.getAddress();
        return adresse == null ? null : de(adresse.getAddress());
    }
}
