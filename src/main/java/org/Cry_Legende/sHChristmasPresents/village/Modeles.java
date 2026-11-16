package org.Cry_Legende.sHChristmasPresents.village;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.Cry_Legende.sHChristmasPresents.rendu.Piece;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/** Modèles BDEngine (models/*.bdengine) des cadeaux du village : lus une fois, pièces prêtes à montrer par paquets. */
final class Modeles {

    /**
     * Un modèle prêt à poser : pièces recentrées (pied du modèle à l'origine, centré en x et z), dimensions, et ce
     * qu'il faut pour animer le couvercle : la boîte (la plus grosse pièce), les pièces du couvercle (tout ce qui est
     * au-dessus de la moitié haute de la boîte : couvercle, nœud, paillettes) et le pivot (dessus de la boîte).
     */
    static final class Modele {
        final String nom;
        final List<Piece> pieces;
        final float largeur;
        final float hauteur;
        final int boite;
        final boolean[] couvercle;
        final Vector3f pivot;
        final float basBoite;
        final float hautBoite;

        Modele(String nom, List<Piece> brutes) {
            this.nom = nom;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (Piece p : brutes) {
                for (Vector3f c : p.coins()) {
                    minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x);
                    minY = Math.min(minY, c.y); maxY = Math.max(maxY, c.y);
                    minZ = Math.min(minZ, c.z); maxZ = Math.max(maxZ, c.z);
                }
            }
            Matrix4f recentrage = new Matrix4f().translation(-(minX + maxX) / 2f, -minY, -(minZ + maxZ) / 2f);
            List<Piece> pieces = new ArrayList<>(brutes.size());
            for (Piece p : brutes) {
                pieces.add(p.avec(new Matrix4f(recentrage).mul(p.matrice()), ""));
            }
            this.pieces = List.copyOf(pieces);
            this.largeur = Math.max(0.5f, Math.max(maxX - minX, maxZ - minZ));
            this.hauteur = Math.max(0.4f, maxY - minY);
            int grosse = 0;
            for (int i = 1; i < pieces.size(); i++) {
                if (pieces.get(i).volume() > pieces.get(grosse).volume()) {
                    grosse = i;
                }
            }
            this.boite = grosse;
            float[] b = boite(pieces.get(grosse));
            this.basBoite = b[1];
            this.hautBoite = b[4];
            float milieu = (b[1] + b[4]) / 2f;
            float seuil = (milieu + b[4]) / 2f;
            this.couvercle = new boolean[pieces.size()];
            for (int i = 0; i < pieces.size(); i++) {
                if (i != grosse) {
                    float[] c = boite(pieces.get(i));
                    couvercle[i] = (c[1] + c[4]) / 2f > seuil;
                }
            }
            this.pivot = new Vector3f((b[0] + b[3]) / 2f, b[4], (b[2] + b[5]) / 2f);
        }

        /** minX, minY, minZ, maxX, maxY, maxZ d'une pièce. */
        private static float[] boite(Piece p) {
            float[] r = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (Vector3f c : p.coins()) {
                r[0] = Math.min(r[0], c.x); r[1] = Math.min(r[1], c.y); r[2] = Math.min(r[2], c.z);
                r[3] = Math.max(r[3], c.x); r[4] = Math.max(r[4], c.y); r[5] = Math.max(r[5], c.z);
            }
            return r;
        }

        int nombreCouvercle() {
            int n = 0;
            for (boolean c : couvercle) {
                n += c ? 1 : 0;
            }
            return n;
        }
    }

    private Modeles() {
    }

    static Map<String, Modele> charger(File dossier, Logger log) {
        Map<String, Modele> modeles = new HashMap<>();
        if (!dossier.exists()) {
            dossier.mkdirs();
        }
        File[] fichiers = dossier.listFiles();
        if (fichiers == null) {
            return modeles;
        }
        for (File fichier : fichiers) {
            String nom = fichier.getName();
            if (!fichier.isFile() || !(nom.endsWith(".bdengine") || nom.endsWith(".txt"))) {
                continue;
            }
            try {
                String contenu = Files.readString(fichier.toPath(), StandardCharsets.UTF_8).trim()
                        .replace("\n", "").replace("\r", "").replace(" ", "");
                String court = nom.replace(".bdengine", "").replace(".txt", "");
                Modele modele = lire(court, contenu);
                if (modele != null && !modele.pieces.isEmpty()) {
                    modeles.put(court, modele);
                }
            } catch (Exception e) {
                log.severe("Modèle illisible " + nom + " : " + e.getMessage());
            }
        }
        return modeles;
    }

    private static Modele lire(String nom, String base64) {
        try {
            StringBuilder complet = new StringBuilder(base64);
            while (complet.length() % 4 != 0) {
                complet.append('=');
            }
            byte[] compresse = Base64.getDecoder().decode(complet.toString());
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compresse));
                 BufferedReader lecteur = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                String ligne;
                while ((ligne = lecteur.readLine()) != null) {
                    json.append(ligne);
                }
                JsonElement racine = JsonParser.parseString(json.toString());
                List<Piece> parties = new ArrayList<>();
                parcourir(racine, new Matrix4f(), parties);
                return parties.isEmpty() ? null : new Modele(nom, parties);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void parcourir(JsonElement element, Matrix4f parent, List<Piece> parties) {
        if (element.isJsonArray()) {
            for (JsonElement enfant : element.getAsJsonArray()) {
                parcourir(enfant, parent, parties);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject obj = element.getAsJsonObject();
        if (obj.has("settings") && obj.has("mainNBT") && obj.has("children")) {
            parcourir(obj.get("children"), parent, parties);
            return;
        }
        Matrix4f locale = new Matrix4f();
        float[] brute = null;
        if (obj.has("transforms")) {
            brute = flottants(obj.getAsJsonArray("transforms"));
        } else if (obj.has("transformation")) {
            brute = flottants(obj.getAsJsonArray("transformation"));
        }
        if (brute != null && brute.length == 16) {
            locale.set(brute);
            locale.transpose();
        }
        Matrix4f cumulee = new Matrix4f(parent).mul(locale);
        boolean bloc = obj.has("blockState") || obj.has("block_state") || (obj.has("isBlockDisplay") && obj.get("isBlockDisplay").getAsBoolean());
        boolean objet = obj.has("tagHead") || obj.has("item") || (obj.has("isItemDisplay") && obj.get("isItemDisplay").getAsBoolean());
        if (bloc) {
            parties.add(Piece.bloc(donneesBloc(nomBloc(obj)), cumulee, ""));
        } else if (objet) {
            String texture = texture(obj);
            parties.add(texture != null ? Piece.objet(Material.PLAYER_HEAD, texture, cumulee, "")
                    : Piece.objet(materiau(nomObjet(obj)), null, cumulee, ""));
        }
        if (obj.has("children")) {
            parcourir(obj.get("children"), cumulee, parties);
        }
        if (obj.has("Passengers")) {
            parcourir(obj.get("Passengers"), cumulee, parties);
        }
    }

    private static String nomBloc(JsonObject obj) {
        for (String cle : new String[]{"blockState", "block_state"}) {
            if (obj.has(cle)) {
                JsonElement etat = obj.get(cle);
                if (etat.isJsonObject() && etat.getAsJsonObject().has("Name")) {
                    return etat.getAsJsonObject().get("Name").getAsString();
                }
                if (etat.isJsonPrimitive()) {
                    return etat.getAsString();
                }
            }
        }
        return obj.has("name") ? obj.get("name").getAsString() : "stone";
    }

    private static String nomObjet(JsonObject obj) {
        if (obj.has("item")) {
            JsonElement item = obj.get("item");
            if (item.isJsonObject() && item.getAsJsonObject().has("id")) {
                return item.getAsJsonObject().get("id").getAsString();
            }
            if (item.isJsonPrimitive()) {
                return item.getAsString();
            }
        }
        return obj.has("name") ? obj.get("name").getAsString() : "stone";
    }

    private static BlockData donneesBloc(String nom) {
        String propre = nom == null ? "stone" : nom.toLowerCase();
        if (propre.startsWith("minecraft:")) {
            propre = propre.substring(10);
        }
        try {
            return Bukkit.createBlockData(propre);
        } catch (Exception e) {
            if (propre.contains("[")) {
                try {
                    return Bukkit.createBlockData(propre.substring(0, propre.indexOf('[')));
                } catch (Exception ignored) {
                    // pierre ci-dessous
                }
            }
            return Bukkit.createBlockData(Material.STONE);
        }
    }

    private static Material materiau(String nom) {
        if (nom == null) {
            return Material.STONE;
        }
        String c = nom.toUpperCase().replace("MINECRAFT:", "").replace(" ", "_");
        if (c.contains("[")) {
            c = c.substring(0, c.indexOf('['));
        }
        Material m = Material.getMaterial(c);
        if (m == null) {
            m = Material.matchMaterial(c);
        }
        return m != null ? m : Material.STONE;
    }

    private static float[] flottants(JsonArray tableau) {
        if (tableau == null || tableau.size() != 16) {
            return null;
        }
        float[] f = new float[16];
        for (int i = 0; i < 16; i++) {
            f[i] = tableau.get(i).getAsFloat();
        }
        return f;
    }

    private static String texture(JsonObject obj) {
        try {
            if (obj.has("tagHead")) {
                return obj.getAsJsonObject("tagHead").get("Value").getAsString();
            }
            if (obj.has("defaultTextureValue") && obj.get("defaultTextureValue").isJsonPrimitive()) {
                return obj.get("defaultTextureValue").getAsString();
            }
            if (obj.has("item")) {
                return obj.getAsJsonObject("item").getAsJsonObject("tag").getAsJsonObject("SkullOwner")
                        .getAsJsonObject("Properties").getAsJsonArray("textures").get(0).getAsJsonObject().get("Value").getAsString();
            }
        } catch (Exception ignored) {
            // pas de texture
        }
        return null;
    }
}
