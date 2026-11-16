package org.Cry_Legende.sHChristmasPresents.rendu;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Une pièce d'un modèle : un bloc (block display) ou un objet (item display, tête texturée ou non) et sa matrice dans
 * le repère du modèle (pied du modèle à l'origine, mètres). Le groupe sert aux animations (couvercle, tete, bras…).
 */
public final class Piece {

    private final BlockData bloc;
    private final Material materiau;
    private final String texture;
    private final Matrix4f matrice;
    private final String groupe;
    private volatile int etatBloc = -1;
    private volatile com.github.retrooper.packetevents.protocol.item.ItemStack objetPaquet;

    private Piece(BlockData bloc, Material materiau, String texture, Matrix4f matrice, String groupe) {
        this.bloc = bloc;
        this.materiau = materiau;
        this.texture = texture;
        this.matrice = new Matrix4f(matrice);
        this.groupe = groupe == null ? "" : groupe;
    }

    public static Piece bloc(BlockData bloc, Matrix4f matrice, String groupe) {
        return new Piece(bloc, null, null, matrice, groupe);
    }

    public static Piece objet(Material materiau, String texture, Matrix4f matrice, String groupe) {
        return new Piece(null, materiau == null ? Material.STONE : materiau, texture, matrice, groupe);
    }

    public Piece avec(Matrix4f nouvelle, String nouveauGroupe) {
        return new Piece(bloc, materiau, texture, nouvelle, nouveauGroupe);
    }

    public boolean bloc() {
        return bloc != null;
    }

    public Matrix4f matrice() {
        return new Matrix4f(matrice);
    }

    public String groupe() {
        return groupe;
    }

    public boolean tete() {
        return texture != null;
    }

    int etatBloc() {
        int e = etatBloc;
        if (e < 0) {
            e = SpigotConversionUtil.fromBukkitBlockData(bloc).getGlobalId();
            etatBloc = e;
        }
        return e;
    }

    com.github.retrooper.packetevents.protocol.item.ItemStack objetPaquet() {
        var o = objetPaquet;
        if (o == null) {
            o = SpigotConversionUtil.fromBukkitItemStack(objetBukkit());
            objetPaquet = o;
        }
        return o;
    }

    private ItemStack objetBukkit() {
        if (texture == null) {
            return new ItemStack(materiau);
        }
        ItemStack tete = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) tete.getItemMeta();
        PlayerProfile profil = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "SH");
        profil.setProperty(new ProfileProperty("textures", texture));
        meta.setPlayerProfile(profil);
        tete.setItemMeta(meta);
        return tete;
    }

    /**
     * Les 8 coins de la pièce dans le repère du modèle. Bloc : cube unité [0,1]³. Tête (contexte « none ») : cube
     * [-0,25 ; 0,25] × [-0,5 ; 0] × [-0,25 ; 0,25]. Autre objet : plaque [-0,5 ; 0,5]² épaisse de 1/16.
     */
    public Vector3f[] coins() {
        float x0, x1, y0, y1, z0, z1;
        if (bloc != null) {
            x0 = 0; x1 = 1; y0 = 0; y1 = 1; z0 = 0; z1 = 1;
        } else if (texture != null || materiau == Material.PLAYER_HEAD) {
            x0 = -0.25f; x1 = 0.25f; y0 = -0.5f; y1 = 0; z0 = -0.25f; z1 = 0.25f;
        } else {
            x0 = -0.5f; x1 = 0.5f; y0 = -0.5f; y1 = 0.5f; z0 = -0.03125f; z1 = 0.03125f;
        }
        Vector3f[] out = new Vector3f[8];
        int i = 0;
        for (float x : new float[]{x0, x1}) {
            for (float y : new float[]{y0, y1}) {
                for (float z : new float[]{z0, z1}) {
                    out[i++] = matrice.transformPosition(new Vector3f(x, y, z));
                }
            }
        }
        return out;
    }

    /** Volume (valeur absolue du déterminant de la partie linéaire). */
    public float volume() {
        return Math.abs(matrice.determinant3x3());
    }
}
