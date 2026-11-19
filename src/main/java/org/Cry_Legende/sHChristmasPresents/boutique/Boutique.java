package org.Cry_Legende.sHChristmasPresents.boutique;

import org.Cry_Legende.sHChristmasPresents.SHChristmasPresents;
import org.Cry_Legende.sHChristmasPresents.commun.Console;
import org.Cry_Legende.sHChristmasPresents.commun.LectureConfig;
import org.Cry_Legende.sHChristmasPresents.commun.Textes;
import org.Cry_Legende.sHChristmasPresents.stockage.Base;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boutique des Flocons : ouverte de village à cloture. /shchristmaspresents acheter (console, appelée par les menus)
 * contrôle la phase, la condition, les limites et le solde, retire, lance les commandes de l'article et journalise
 * (shxmas_achats). Un achat à la fois par joueur, fil principal.
 */
public final class Boutique {

    /**
     * Article de la boutique. {@code possede} : permission que l'article donne ; un joueur qui l'a déjà (gagnée dans la
     * box, par exemple) voit l'article obtenu et ne le paie pas une deuxième fois.
     */
    public record Article(String id, int prix, int limiteJour, int limiteTotal, String requiert, java.time.ZonedDateTime des,
                          List<String> commandes, String possede) {
    }

    /** Pourquoi un article peut ou ne peut pas être acheté maintenant. */
    public enum Etat {
        OK, FERMEE_AVANT, FERMEE, INDISPONIBLE, BIENTOT, REQUIERT, DEJA, LIMITE_TOTALE, LIMITE_JOUR, MANQUE
    }

    private static final class Achats {
        volatile LocalDate jour;
        final Map<String, Integer> total = new ConcurrentHashMap<>();
        final Map<String, Integer> duJour = new ConcurrentHashMap<>();
    }

    private final SHChristmasPresents plugin;
    private final Base base;
    private final int annee;
    private final Map<UUID, Achats> achats = new ConcurrentHashMap<>();
    private volatile Map<String, Article> articles = Map.of();

    public Boutique(SHChristmasPresents plugin, Base base, int annee) {
        this.plugin = plugin;
        this.base = base;
        this.annee = annee;
    }

    public void configurer(ConfigurationSection section) {
        Map<String, Article> lus = new LinkedHashMap<>();
        ConfigurationSection liste = section == null ? null : section.getConfigurationSection("articles");
        if (liste != null) {
            for (String id : liste.getKeys(false)) {
                ConfigurationSection a = liste.getConfigurationSection(id);
                if (a == null) {
                    continue;
                }
                String requiert = a.getString("requiert", "");
                java.time.ZonedDateTime des = null;
                String texteDes = a.getString("des", "");
                if (texteDes != null && !texteDes.isBlank()) {
                    try {
                        des = java.time.LocalDateTime.parse(texteDes.trim()).atZone(plugin.horloge().zone());
                    } catch (java.time.format.DateTimeParseException e) {
                        plugin.getLogger().warning("boutique.articles." + id + ".des illisible (AAAA-MM-JJTHH:MM) : " + texteDes);
                    }
                }
                String possede = a.getString("possede", "");
                lus.put(id, new Article(id, Math.max(0, a.getInt("prix", 0)), Math.max(0, a.getInt("limite-jour", 0)),
                        Math.max(0, a.getInt("limite-total", 0)), requiert == null || requiert.isBlank() ? null : requiert.trim(),
                        des, LectureConfig.textes(a, "commandes"), possede == null || possede.isBlank() ? null : possede.trim()));
            }
        }
        articles = java.util.Collections.unmodifiableMap(lus);
    }

    public void charger(Connection connexion, LocalDate aujourdhui) throws SQLException {
        try (PreparedStatement r = connexion.prepareStatement("SELECT uuid, article, COUNT(*), SUM(jour = ?) FROM shxmas_achats"
                + " WHERE annee = ? GROUP BY uuid, article")) {
            r.setDate(1, Date.valueOf(aujourdhui));
            r.setInt(2, annee);
            try (ResultSet l = r.executeQuery()) {
                while (l.next()) {
                    Achats a = achats.computeIfAbsent(UUID.fromString(l.getString(1)), k -> new Achats());
                    a.jour = aujourdhui;
                    a.total.put(l.getString(2), l.getInt(3));
                    a.duJour.put(l.getString(2), l.getInt(4));
                }
            }
        }
    }

    public Article article(String id) {
        return articles.get(id);
    }

    public Map<String, Article> articles() {
        return articles;
    }

    public String nom(String id) {
        String cle = "boutique.articles." + id;
        return plugin.messages().existe(cle) ? plugin.messages().brut(cle) : id;
    }

    public int achatsTotal(UUID uuid, String article) {
        Achats a = achats.get(uuid);
        return a == null ? 0 : a.total.getOrDefault(article, 0);
    }

    public int achatsJour(UUID uuid, String article, LocalDate jour) {
        Achats a = achats.get(uuid);
        return a == null || !jour.equals(a.jour) ? 0 : a.duJour.getOrDefault(article, 0);
    }

    /** Vrai si le joueur (en ligne) a déjà la permission que l'article donne. */
    private boolean possede(UUID uuid, Article article) {
        if (article == null || article.possede() == null) {
            return false;
        }
        org.bukkit.entity.Player joueur = org.bukkit.Bukkit.getPlayer(uuid);
        return joueur != null && joueur.hasPermission(article.possede());
    }

    /** État d'un article pour ce joueur ; solde &lt; 0 = solde pas lu (on ne juge pas le prix). */
    public Etat etat(UUID uuid, Article article, long solde) {
        ZonedDateTime maintenant = plugin.horloge().maintenant();
        if (!plugin.dates().boutique(maintenant)) {
            return maintenant.isBefore(plugin.dates().get("village")) ? Etat.FERMEE_AVANT : Etat.FERMEE;
        }
        if (article.commandes().isEmpty()) {
            // Article pas encore rempli dans la config : il ne se vend pas
            return Etat.INDISPONIBLE;
        }
        if (article.des() != null && maintenant.isBefore(article.des())) {
            return Etat.BIENTOT;
        }
        if (article.requiert() != null && achatsTotal(uuid, article.requiert()) < 1 && !possede(uuid, articles.get(article.requiert()))) {
            return Etat.REQUIERT;
        }
        if (possede(uuid, article)) {
            return Etat.DEJA;
        }
        int total = achatsTotal(uuid, article.id());
        if (article.limiteTotal() > 0 && total >= article.limiteTotal()) {
            return article.limiteTotal() == 1 ? Etat.DEJA : Etat.LIMITE_TOTALE;
        }
        if (article.limiteJour() > 0 && achatsJour(uuid, article.id(), maintenant.toLocalDate()) >= article.limiteJour()) {
            return Etat.LIMITE_JOUR;
        }
        if (solde >= 0 && solde < article.prix()) {
            return Etat.MANQUE;
        }
        return Etat.OK;
    }

    /** Ligne prête à afficher d'un article (placeholder article_&lt;id&gt;_ligne). */
    public String ligne(OfflinePlayer joueur, Article article) {
        long solde = plugin.flocons().portefeuille().solde(joueur);
        Etat etat = etat(joueur.getUniqueId(), article, solde);
        var m = plugin.messages();
        return switch (etat) {
            case OK -> m.brut("placeholders.article-ligne.ok");
            case FERMEE_AVANT, FERMEE -> m.brut("placeholders.article-ligne.fermee");
            case INDISPONIBLE -> m.brut("placeholders.article-ligne.indisponible");
            case BIENTOT -> m.brut("placeholders.article-ligne.bientot", "{date}", Textes.date(article.des()));
            case REQUIERT -> m.brut("placeholders.article-ligne.requiert", "{article}", nom(article.requiert()));
            case DEJA -> m.brut("placeholders.article-ligne.deja");
            case LIMITE_TOTALE -> m.brut("placeholders.article-ligne.limite-totale");
            case LIMITE_JOUR -> m.brut("placeholders.article-ligne.limite-jour");
            case MANQUE -> m.brut("placeholders.article-ligne.manque", "{manque}", Textes.nombre(article.prix() - solde));
        };
    }

    public boolean peut(OfflinePlayer joueur, Article article) {
        long solde = plugin.flocons().portefeuille().solde(joueur);
        return solde >= 0 && etat(joueur.getUniqueId(), article, solde) == Etat.OK;
    }

    /** Limites d'un article pour ce joueur (placeholder article_&lt;id&gt;_limite), vide sans limite à montrer. */
    public String limite(OfflinePlayer joueur, Article article) {
        var m = plugin.messages();
        int achetes = achatsTotal(joueur.getUniqueId(), article.id());
        if (article.limiteJour() > 0 && article.limiteTotal() > 0) {
            return m.brut("boutique.limite.jour-total", "{jour}", article.limiteJour(), "{achats}", achetes, "{total}", article.limiteTotal());
        }
        if (article.limiteJour() > 0) {
            return m.brut("boutique.limite.jour", "{jour}", article.limiteJour(),
                    "{achats-jour}", achatsJour(joueur.getUniqueId(), article.id(), plugin.horloge().aujourdhui()));
        }
        if (article.limiteTotal() > 1) {
            return m.brut("boutique.limite.total", "{achats}", achetes, "{total}", article.limiteTotal());
        }
        return article.limiteTotal() == 1 ? m.brut("boutique.limite.unique") : "";
    }

    /** /shchristmaspresents acheter (fil principal). */
    public void acheter(CommandSender auteur, Player joueur, String id) {
        var m = plugin.messages();
        Article article = articles.get(id);
        if (article == null) {
            m.envoyer(auteur, "admin.article-inconnu", "{article}", id);
            return;
        }
        UUID uuid = joueur.getUniqueId();
        if (!plugin.verrouiller(uuid)) {
            plugin.debug("Achat de " + id + " par " + joueur.getName() + " ignoré : un autre achat est en cours");
            return;
        }
        long solde = plugin.flocons().portefeuille().solde(joueur);
        Etat etat = etat(uuid, article, solde);
        String nom = nom(id);
        if (etat == Etat.OK) {
            // Le verrou est rendu quand le retrait est confirmé
            payer(joueur, article, nom);
            return;
        }
        try {
            switch (etat) {
                case FERMEE_AVANT -> m.envoyer(joueur, "boutique.fermee-avant", "{date}", Textes.date(plugin.dates().get("village")));
                case FERMEE -> m.envoyer(joueur, "boutique.fermee");
                case INDISPONIBLE -> {
                    plugin.getLogger().warning("Achat de " + id + " refusé : l'article n'a aucune commande");
                    m.envoyer(joueur, "boutique.indisponible", "{article}", nom);
                }
                case BIENTOT -> m.envoyer(joueur, "boutique.bientot", "{article}", nom, "{date}", Textes.date(article.des()));
                case REQUIERT -> m.envoyer(joueur, "boutique.requiert", "{article}", nom(article.requiert()));
                case DEJA -> m.envoyer(joueur, "boutique.deja", "{article}", nom);
                case LIMITE_TOTALE -> m.envoyer(joueur, "boutique.limite-totale", "{article}", nom);
                case LIMITE_JOUR -> m.envoyer(joueur, "boutique.limite-jour", "{article}", nom);
                case MANQUE -> m.envoyer(joueur, "boutique.manque", "{manque}", Textes.nombre(article.prix() - solde), "{article}", nom);
                default -> {
                }
            }
            plugin.son("refus", joueur);
        } finally {
            plugin.deverrouiller(uuid);
        }
    }

    private void payer(Player joueur, Article article, String nom) {
        UUID uuid = joueur.getUniqueId();
        if (article.prix() <= 0) {
            livrer(joueur, article, nom);
            plugin.deverrouiller(uuid);
            return;
        }
        plugin.flocons().portefeuille().retirer(joueur, article.prix(), retrait -> {
            try {
                var m = plugin.messages();
                switch (retrait) {
                    case INSUFFISANT -> {
                        long solde = plugin.flocons().portefeuille().solde(joueur);
                        m.envoyer(joueur, "boutique.manque", "{manque}", Textes.nombre(article.prix() - Math.max(0, solde)), "{article}", nom);
                        plugin.son("refus", joueur);
                    }
                    case ILLISIBLE, ECHEC -> {
                        m.envoyer(joueur, "erreur");
                        plugin.son("refus", joueur);
                    }
                    case FAIT -> livrer(joueur, article, nom);
                }
            } finally {
                plugin.deverrouiller(uuid);
            }
        });
    }

    /** Achat payé : enregistré, commandes de l'article, message et son. */
    private void livrer(Player joueur, Article article, String nom) {
        var m = plugin.messages();
        UUID uuid = joueur.getUniqueId();
        LocalDateTime maintenant = plugin.horloge().local();
        LocalDate jour = maintenant.toLocalDate();
        Achats a = achats.computeIfAbsent(uuid, k -> new Achats());
        if (!jour.equals(a.jour)) {
            a.duJour.clear();
            a.jour = jour;
        }
        a.total.merge(article.id(), 1, Integer::sum);
        a.duJour.merge(article.id(), 1, Integer::sum);
        String pseudo = joueur.getName();
        base.ecrire("achat " + article.id(), c -> {
            try (PreparedStatement r = c.prepareStatement("INSERT INTO shxmas_achats (annee, uuid, pseudo, article, prix, jour, ts)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                r.setInt(1, annee);
                r.setString(2, uuid.toString());
                r.setString(3, pseudo);
                r.setString(4, article.id());
                r.setInt(5, article.prix());
                r.setDate(6, Date.valueOf(jour));
                r.setTimestamp(7, Timestamp.valueOf(maintenant));
                r.executeUpdate();
            }
        });
        Console.executer(plugin.getLogger(), article.commandes(), "{joueur}", pseudo, "{uuid}", uuid.toString(), "{article}", article.id());
        m.envoyer(joueur, "boutique.achete", "{article}", nom, "{prix}", Textes.nombre(article.prix()));
        plugin.son("achat", joueur);
        plugin.getLogger().info("Achat : " + pseudo + " " + article.id() + " (" + article.prix() + " Flocons)");
    }
}
