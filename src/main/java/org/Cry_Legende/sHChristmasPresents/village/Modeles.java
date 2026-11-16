package org.Cry_Legende.sHChristmasPresents.village;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
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

/** Modèles BDEngine (models/*.bdengine) des cadeaux du village : lus une fois, parties prêtes à poser. */
final class Modeles {

    enum Type {ITEM, BLOCK}

    static final class Partie {
        final Type type;
        final Matrix4f matrice;
        Material materiau;
        BlockData bloc;
        String texture;

        Partie(Type type, Matrix4f matrice) {
            this.type = type;
            this.matrice = matrice;
        }
    }

    static final class Modele {
        final String nom;
        final List<Partie> parties;
        float largeur;
        float hauteur;
        Vector3f recentrage;

        Modele(String nom, List<Partie> parties) {
            this.nom = nom;
            this.parties = parties;
            dimensions();
        }

        private void dimensions() {
            if (parties.isEmpty()) {
                largeur = 1f;
                hauteur = 1f;
                recentrage = new Vector3f();
                return;
            }
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            Vector3f position = new Vector3f();
            Vector3f echelle = new Vector3f();
            for (Partie partie : parties) {
                partie.matrice.getTranslation(position);
                partie.matrice.getScale(echelle);
                float dx = Math.abs(echelle.x) / 2f, dy = Math.abs(echelle.y) / 2f, dz = Math.abs(echelle.z) / 2f;
                minX = Math.min(minX, position.x - dx);
                maxX = Math.max(maxX, position.x + dx);
                minY = Math.min(minY, position.y - dy);
                maxY = Math.max(maxY, position.y + dy);
                minZ = Math.min(minZ, position.z - dz);
                maxZ = Math.max(maxZ, position.z + dz);
            }
            largeur = Math.max(0.6f, Math.max(maxX - minX, maxZ - minZ) + 0.1f);
            hauteur = Math.max(0.6f, (maxY - minY) + 0.1f);
            recentrage = new Vector3f(-(minX + maxX) / 2f, -minY, -(minZ + maxZ) / 2f);
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
                if (modele != null && !modele.parties.isEmpty()) {
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
                List<Partie> parties = new ArrayList<>();
                parcourir(racine, new Matrix4f(), parties);
                return new Modele(nom, parties);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void parcourir(JsonElement element, Matrix4f parent, List<Partie> parties) {
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
        if (bloc || objet) {
            Partie partie = new Partie(bloc ? Type.BLOCK : Type.ITEM, new Matrix4f(cumulee));
            if (bloc) {
                partie.bloc = donneesBloc(nomBloc(obj));
            } else {
                String texture = texture(obj);
                if (texture != null) {
                    partie.texture = texture;
                    partie.materiau = Material.PLAYER_HEAD;
                } else {
                    partie.materiau = materiau(nomObjet(obj));
                }
            }
            parties.add(partie);
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
