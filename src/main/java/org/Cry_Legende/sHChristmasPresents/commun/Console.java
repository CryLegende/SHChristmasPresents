package org.Cry_Legende.sHChristmasPresents.commun;

import org.bukkit.Bukkit;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Commandes lancées depuis la console, sur le fil principal. */
public final class Console {

    private Console() {
    }

    /**
     * Lance chaque commande (sans « / » de tête), remplacements faits ({joueur}, valeur, ...).
     *
     * @return nombre de commandes lancées
     */
    public static int executer(Logger log, List<String> commandes, Object... remplacements) {
        int lancees = 0;
        for (String brute : commandes) {
            if (brute == null || brute.isBlank()) {
                continue;
            }
            String commande = Messages.remplacer(brute.trim(), remplacements);
            if (commande.startsWith("/")) {
                commande = commande.substring(1);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commande);
                lancees++;
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Commande en erreur : " + commande, e);
            }
        }
        return lancees;
    }
}
