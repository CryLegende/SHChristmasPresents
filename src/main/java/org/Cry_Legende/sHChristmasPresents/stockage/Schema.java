package org.Cry_Legende.sHChristmasPresents.stockage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tables shxmas_*. Chaque ligne porte son année (colonne annee, config annee) : une nouvelle année repart de zéro
 * sans rien effacer. annee = 0 dans shxmas_etat : réglages qui ne dépendent pas de l'année.
 */
final class Schema {

    private static final String SUFFIXE = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    private static final String[] TABLES = {
            "CREATE TABLE IF NOT EXISTS shxmas_etat ("
                    + "annee SMALLINT NOT NULL, cle VARCHAR(64) NOT NULL, valeur MEDIUMTEXT NOT NULL,"
                    + " maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + " PRIMARY KEY (annee, cle))",
            "CREATE TABLE IF NOT EXISTS shxmas_actions ("
                    + "annee SMALLINT NOT NULL, id VARCHAR(128) NOT NULL, statut VARCHAR(8) NOT NULL,"
                    + " prevue DATETIME NULL, traitee DATETIME NOT NULL, PRIMARY KEY (annee, id))",
            "CREATE TABLE IF NOT EXISTS shxmas_joueurs ("
                    + "annee SMALLINT NOT NULL, uuid CHAR(36) NOT NULL, pseudo VARCHAR(32) NOT NULL,"
                    + " empreinte CHAR(64) NULL, temps_jeu INT NOT NULL DEFAULT 0, bienvenue DATETIME NULL,"
                    + " carte_complete DATETIME NULL, retour DATETIME NULL, multi_perso DOUBLE NOT NULL DEFAULT 1,"
                    + " multi_perso_fin DATETIME NULL, recap DATETIME NULL, premiere_visite DATETIME NULL,"
                    + " maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + " PRIMARY KEY (annee, uuid))",
            "CREATE TABLE IF NOT EXISTS shxmas_gains ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT, annee SMALLINT NOT NULL, uuid CHAR(36) NOT NULL,"
                    + " pseudo VARCHAR(32) NOT NULL, source VARCHAR(32) NOT NULL, base INT NOT NULL, verse INT NOT NULL,"
                    + " facteur DOUBLE NOT NULL, jour DATE NOT NULL, ts DATETIME(3) NOT NULL, PRIMARY KEY (id),"
                    + " KEY idx_joueur (annee, uuid, jour), KEY idx_source (annee, source))",
            "CREATE TABLE IF NOT EXISTS shxmas_portes ("
                    + "annee SMALLINT NOT NULL, uuid CHAR(36) NOT NULL, porte TINYINT NOT NULL, empreinte CHAR(64) NULL,"
                    + " rattrapage TINYINT(1) NOT NULL DEFAULT 0, ouverte DATETIME NOT NULL,"
                    + " PRIMARY KEY (annee, uuid, porte), KEY idx_empreinte (annee, porte, empreinte))",
            "CREATE TABLE IF NOT EXISTS shxmas_sapin ("
                    + "annee SMALLINT NOT NULL, uuid CHAR(36) NOT NULL, pseudo VARCHAR(32) NOT NULL,"
                    + " total INT NOT NULL DEFAULT 0, jour DATE NULL, part_jour INT NOT NULL DEFAULT 0, offert_jour INT NOT NULL DEFAULT 0,"
                    + " PRIMARY KEY (annee, uuid))",
            "CREATE TABLE IF NOT EXISTS shxmas_compteurs ("
                    + "annee SMALLINT NOT NULL, uuid CHAR(36) NOT NULL, compteur VARCHAR(32) NOT NULL,"
                    + " valeur INT NOT NULL DEFAULT 0, PRIMARY KEY (annee, uuid, compteur))",
            "CREATE TABLE IF NOT EXISTS shxmas_tampons ("
                    + "annee SMALLINT NOT NULL, uuid CHAR(36) NOT NULL, tampon VARCHAR(32) NOT NULL,"
                    + " obtenu DATETIME NOT NULL, PRIMARY KEY (annee, uuid, tampon))",
            "CREATE TABLE IF NOT EXISTS shxmas_achats ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT, annee SMALLINT NOT NULL, uuid CHAR(36) NOT NULL,"
                    + " pseudo VARCHAR(32) NOT NULL, article VARCHAR(32) NOT NULL, prix INT NOT NULL, jour DATE NOT NULL,"
                    + " ts DATETIME(3) NOT NULL, PRIMARY KEY (id), KEY idx_joueur (annee, uuid))",
            "CREATE TABLE IF NOT EXISTS shxmas_cadeaux ("
                    + "id CHAR(36) NOT NULL, annee SMALLINT NOT NULL, de_uuid CHAR(36) NOT NULL,"
                    + " de_pseudo VARCHAR(32) NOT NULL, a_uuid CHAR(36) NOT NULL, a_pseudo VARCHAR(32) NOT NULL,"
                    + " montant INT NOT NULL, statut VARCHAR(8) NOT NULL, cree DATETIME(3) NOT NULL,"
                    + " livre DATETIME NULL, ouvert DATETIME NULL, PRIMARY KEY (id),"
                    + " KEY idx_receveur (annee, a_uuid), KEY idx_donneur (annee, de_uuid))",
            "CREATE TABLE IF NOT EXISTS shxmas_livraisons ("
                    + "annee SMALLINT NOT NULL, uuid CHAR(36) NOT NULL, type VARCHAR(32) NOT NULL, ref VARCHAR(64) NOT NULL,"
                    + " cree DATETIME NOT NULL, livree DATETIME NULL, PRIMARY KEY (annee, uuid, type, ref))",
            "CREATE TABLE IF NOT EXISTS shxmas_ile ("
                    + "annee SMALLINT NOT NULL, uuid CHAR(36) NOT NULL, jour DATE NOT NULL, trouves INT NOT NULL DEFAULT 0,"
                    + " PRIMARY KEY (annee, uuid, jour))",
            "CREATE TABLE IF NOT EXISTS shxmas_clicker ("
                    + "annee SMALLINT NOT NULL, uuid CHAR(36) NOT NULL, clics INT NOT NULL DEFAULT 0,"
                    + " niveau INT NOT NULL DEFAULT 0, jour DATE NULL, clics_jour INT NOT NULL DEFAULT 0,"
                    + " maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + " PRIMARY KEY (annee, uuid))",
    };

    private Schema() {
    }

    static void creer(Connection connexion) throws SQLException {
        try (Statement statement = connexion.createStatement()) {
            for (String table : TABLES) {
                statement.execute(table + SUFFIXE);
            }
        }
    }
}
