package org.Cry_Legende.sHChristmasPresents.stockage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Base MariaDB. Toutes les requêtes passent par un seul fil, dans l'ordre où elles sont demandées : une lecture voit
 * toujours les écritures demandées avant elle. Rien ne touche la base depuis le fil principal ou celui d'un monde,
 * sauf au démarrage du plugin.
 */
public final class Base {

    @FunctionalInterface
    public interface Ecriture {
        void executer(Connection connexion) throws SQLException;
    }

    @FunctionalInterface
    public interface Lecture<T> {
        T lire(Connection connexion) throws SQLException;
    }

    private final HikariDataSource source;
    private final ExecutorService fil;
    private final Logger log;

    public Base(ConfigurationSection reglages, Logger log) throws SQLException {
        this.log = log;
        String hote = reglages == null ? "127.0.0.1" : reglages.getString("hote", "127.0.0.1");
        int port = reglages == null ? 3306 : reglages.getInt("port", 3306);
        String nom = reglages == null ? "minecraft" : reglages.getString("nom", "minecraft");
        HikariConfig config = new HikariConfig();
        config.setPoolName("SHChristmasPresents");
        // Source de données du pilote (chargé par libraries:) : DriverManager ne le voit pas depuis ce classloader
        config.setDataSourceClassName("org.mariadb.jdbc.MariaDbDataSource");
        config.addDataSourceProperty("url", "jdbc:mariadb://" + hote + ":" + port + "/" + nom);
        config.setUsername(reglages == null ? "" : reglages.getString("utilisateur", ""));
        config.setPassword(reglages == null ? "" : reglages.getString("mot-de-passe", ""));
        config.setMaximumPoolSize(Math.max(2, reglages == null ? 3 : reglages.getInt("connexions", 3)));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setMaxLifetime(1_800_000);
        try {
            this.source = new HikariDataSource(config);
        } catch (RuntimeException e) {
            throw new SQLException(e.getMessage(), e);
        }
        this.fil = Executors.newSingleThreadExecutor(tache -> {
            Thread thread = new Thread(tache, "SHChristmasPresents-base");
            thread.setDaemon(true);
            return thread;
        });
        try (Connection connexion = source.getConnection()) {
            Schema.creer(connexion);
        } catch (SQLException e) {
            fermer();
            throw e;
        }
    }

    /** Écriture en file ; trois essais, puis l'erreur est journalisée. */
    public void ecrire(String quoi, Ecriture ecriture) {
        if (fil.isShutdown()) {
            log.severe("Écriture refusée après l'arrêt (" + quoi + ")");
            return;
        }
        fil.execute(() -> {
            SQLException derniere = null;
            for (int essai = 1; essai <= 3; essai++) {
                try (Connection connexion = source.getConnection()) {
                    ecriture.executer(connexion);
                    return;
                } catch (SQLException e) {
                    derniere = e;
                    try {
                        Thread.sleep(400L * essai);
                    } catch (InterruptedException interrompu) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.severe("Écriture perdue (" + quoi + ") : " + (derniere == null ? "interrompue" : derniere.getMessage()));
        });
    }

    /** Lecture en file ; le résultat arrive sur le fil de la base. */
    public <T> CompletableFuture<T> lire(Lecture<T> lecture) {
        CompletableFuture<T> resultat = new CompletableFuture<>();
        fil.execute(() -> {
            try (Connection connexion = source.getConnection()) {
                resultat.complete(lecture.lire(connexion));
            } catch (Throwable e) {
                resultat.completeExceptionally(e);
            }
        });
        return resultat;
    }

    /** Lecture bloquante (démarrage du plugin seulement). */
    public <T> T bloquant(Lecture<T> lecture) throws SQLException {
        try {
            return lire(lecture).get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SQLException sql) {
                throw sql;
            }
            throw new SQLException(cause == null ? e.getMessage() : cause.toString(), cause);
        } catch (TimeoutException e) {
            throw new SQLException("délai dépassé", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("interrompu", e);
        }
    }

    /** Attend la fin des écritures en file (15 s au plus) et ferme les connexions. */
    public void fermer() {
        fil.shutdown();
        try {
            if (!fil.awaitTermination(15, TimeUnit.SECONDS)) {
                log.severe("Écritures encore en file à l'arrêt : abandonnées");
                fil.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        source.close();
    }
}
