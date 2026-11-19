<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHChristmasPresents

*L'évènement de Noël d'Opale : calendrier de l'Avent, Flocons, Grand Sapin, carte de Noël, boutique et cadeaux.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/MariaDB-HikariCP-003545?style=for-the-badge" alt="MariaDB"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Le moteur de Noël d'Opale. Tout tourne autour d'une horloge à l'heure de Paris et de six phases (`avant`, `avent`,
`village`, `emplettes`, `cloture`, `apres`) fixées dans `dates:` :

- **Flocons** : un seul chemin pour les gains (`/shchristmaspresents flocons <joueur> <montant> <source>`), avec un
  plafond par jour et par source, un rendement réduit au-delà, des multiplicateurs (global, fenêtres datées dont une
  « sous la médiane », bonus de retour) plafonnés, un journal de chaque gain. Le portefeuille est PlayerPoints, piloté
  par commandes (aucun appel à son API).
- **Calendrier de l'Avent** (`/calendrier`) : 25 portes, 3 jours de rattrapage, temps de jeu minimal, 2 comptes par
  empreinte d'IP et par porte, paliers à 7, 14 et 21 portes, porte 25 spéciale, cadeau de bienvenue à la 1re porte.
- **Grand Sapin** (`/sapin`) : jauge commune qui se décore palier par palier (commandes serveur de chaque palier, par
  exemple une structure de plus sur le sapin). Points en jouant (1 point pour N Flocons gagnés, sans rien coûter) et
  offrandes (1 point par Flocon, plafond de Flocons offerts par jour). Rangs de contributeur (★, ★★, ★★★ par défaut)
  qui multiplient le cadeau de chaque palier (`{fois:N}` = N × rang), donné selon le rang au moment du palier, hors
  ligne à la connexion suivante. Relevé quotidien de la jauge, prévision pour la Veillée et coup de pouce (offrandes
  comptées double) quand elle n'arrive pas à 100 %. Palier de la Veillée : part dès le seuil pendant sa fenêtre, sinon
  ses commandes d'échec à la fin ; ensuite le sapin ne prend plus rien. Menu avec la jauge, à quoi il sert, le rang du
  joueur, les meilleurs décorateurs, chaque palier (décor et cadeau du joueur) et les offrandes.
- **Carte de Noël** (`/carte`) : 10 tampons (première visite du village, compteurs, niveau du clicker, commande) ;
  carte complète, une fois.
- **Boutique** : achats appelés par les menus, limites par jour et au total, articles qui en demandent un autre,
  placeholders prêts pour les menus.
- **Cadeaux entre joueurs** (`/cadeau`) : des Flocons emballés dans un objet unique, livré même hors ligne, qui ne
  s'ouvre qu'une fois.
- **Petits cadeaux sur l'île**, **Drop** (pluie de cadeaux du ciel), **retour des absents**, livre **« Mon Noël »**
  pendant la clôture, **bilan** de l'évènement.
- **Cadeaux du village** à ouvrir avec une boule de neige magique (rareté dorée en plus) et **bonhommes de neige
  clicker**, tous deux montrés par paquets (packetevents) : le serveur n'a aucune entité, chaque joueur proche reçoit
  les siennes. Le couvercle des cadeaux tremble de temps en temps comme s'il voulait s'ouvrir (vitesse et taille par
  rareté), le cadeau apparaît en grandissant et saute en s'ouvrant. Une boule lancée est suivie à chaque tick : un
  cadeau traversé s'ouvre même si la boule ne s'y arrête pas. Un clic à la main explique quoi faire. Les cadeaux se
  posent sur le vrai dessus des dalles, couches de neige et tapis, jamais sur un escalier, une barrière ou des feuilles,
  ni sous une entité. Les bonhommes de neige (block displays) bougent la tête et les bras et sautent au clic.

Les actions datées de l'évènement (ouverture du village, Veillée, Drop final…) sont dans `actions:` et `chaque-jour:` :
chacune ne part qu'une fois, même après un redémarrage, et celle qui tombe pendant un arrêt part au démarrage si son
retard reste dans sa marge.

Une annonce périodique (`annonce:`) rappelle l'évènement dans le chat ; un article de la boutique peut attendre sa date de
mise en vente (`des:`) ; la première visite du monde du village lance `carte.commandes-premiere-visite`.

Utilisé sur : **Opale** (un seul serveur, rien à synchroniser). Demande **packetevents**.

## Commandes

| Commande | Description | Permission |
|---|---|---|
| `/calendrier` (`/avent`) | Calendrier de l'Avent | `shchristmaspresents.use` |
| `/carte` | Carte de Noël | `shchristmaspresents.use` |
| `/sapin [offrir <nombre>]` | Grand Sapin, offrandes | `shchristmaspresents.use` |
| `/flocons` | Solde, gains du jour, reste avant chaque plafond | `shchristmaspresents.use` |
| `/cadeau <joueur> <montant>` | Offrir des Flocons à un joueur | `shchristmaspresents.use` |

Administration : `/shchristmaspresents` (alias `/xmas`, `/scp`), une permission `shchristmaspresents.admin.<sous-commande>`
par sous-commande, toutes sous `shchristmaspresents.admin` (op par défaut). Tout marche depuis la console sauf
`setregion`, `createclicker` et `removeclicker`.

| Sous-commande | Description |
|---|---|
| `give [joueur] [nombre]` | Boules de neige magiques |
| `setregion <1\|2>` | Coin de la zone des cadeaux du village |
| `createclicker`, `removeclicker`, `listclickers` | Bonhommes de neige clicker |
| `reload` | Relit `config.yml`, `messages.yml` et `models/` (refusé si un fichier est invalide) |
| `info <joueur>` | Clicker, Flocons, portes, tampons, sapin, cadeaux, livraisons d'un joueur |
| `flocons <joueur> <montant> <source>` | Gain de Flocons |
| `multiplicateur <facteur> <minutes>` | Multiplicateur global |
| `compteur <joueur> <id> [n]` | Compteur de la carte (pendant les gains) |
| `tampon <joueur> <id>` | Tampon de la carte (pendant les gains) |
| `acheter <joueur> <article>` | Achat dans la boutique |
| `sapin info\|cible <points>\|ajouter <joueur> <points>\|veillee` | Grand Sapin (info : rangs, prévision, coup de pouce) |
| `drop <zone> <secondes> <nombre>` | Pluie de cadeaux du ciel |
| `horloge <AAAA-MM-JJTHH:MM\|reel>` | Horloge de test (`test.horloge-autorisee`) |
| `bilan` | Bilan dans `bilan-<date>.yml` et en console |
| `tous [monde] <commande>` | Une commande console par joueur en ligne (`{joueur}`) |

## Placeholders

`%shchristmaspresents_<x>%`. Booléens `true`/`false` ; nombres sans séparateur, ou « 1 250 » avec le suffixe `_texte`.

| Placeholder | Valeur |
|---|---|
| `phase`, `phase_ligne` | Phase courante, et une ligne qui la décrit |
| `avant_<repère>` | Temps avant un repère de `dates:` (« 4 j 3 h »), vide une fois passé |
| `multiplicateur` | Multiplicateur du joueur (sans joueur : global) |
| `flocons_jour`, `flocons_jour_<source>`, `plafond_<source>`, `flocons_total` | Gains du jour, par source, reste avant le plafond, total |
| `porte_jour`, `porte_disponible`, `porte_ligne`, `portes_ouvertes`, `portes_palier` | Calendrier |
| `sapin_pourcent`, `sapin_pourcent_entier`, `sapin_palier_suivant`, `sapin_points`, `sapin_cible` | Jauge |
| `sapin_pourcent_affiche`, `sapin_palier_suivant_affiche` | « 37 % », « 50 % » (avec le signe : un « % » seul à côté d'un placeholder casse la lecture des suivants) |
| `date` | Jour de l'évènement (horloge du plugin, horloge de test comprise) en AAAAMMJJ, pour les conditions numériques |
| `sapin_contribution`, `sapin_contribution_jour`, `offert_jour`, `sapin_contributeurs` | Contributions |
| `sapin_rang`, `sapin_rang_ligne`, `sapin_multiplicateur`, `sapin_rang_suivant_reste`, `sapin_place`, `sapin_offrande_reste` | Rang du joueur (étoiles, ligne prête, multiplicateur des cadeaux, points avant le rang suivant, place, Flocons encore offrables aujourd'hui) |
| `sapin_palier_suivant_nom`, `sapin_palier_suivant_cadeau`, `sapin_etat_ligne`, `sapin_prevision`, `sapin_coup_de_pouce` | Prochain palier (décor, cadeau du joueur), ligne d'état prête (barre de boss), prévision pour la Veillée, coup de pouce en cours |
| `sapin_top_<n>_nom`, `sapin_top_<n>_valeur` | Meilleurs contributeurs (1 à 10) |
| `carte_tampons`, `carte_tampon_<id>`, `carte_complete`, `compteur_<id>` | Carte de Noël |
| `article_<id>_ligne`, `_peut`, `_prix`, `_achats`, `_limite` | Boutique |
| `cadeaux_envoyes_jour`, `ile_jour` | Cadeaux offerts, petits cadeaux trouvés aujourd'hui |
| `clicks`, `clicks_jour`, `level` | Clicker |

Le détail de chaque commande, permission, placeholder et réglage est en tête de [config.yml](src/main/resources/config.yml).

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHChristmasPresents/
├── <a href="src/main/resources/config.yml">config.yml</a>
├── <a href="src/main/resources/messages.yml">messages.yml</a>
├── bonhommes.yml     places des bonhommes de neige clicker (écrit par createclicker / removeclicker)
└── models/          modèles BDEngine des cadeaux du village
</pre>

Depuis la 2.2.2 : `connexion` (message du jour à la connexion, une fois par jour et par joueur, commandes par phase),
`liste-commandes-autres` (commandes d'autres plugins, comme le menu `/noel`, retirées de la liste du client hors de
l'évènement), `possede` sur un article de la boutique (permission que l'article donne : un joueur qui l'a déjà le voit
obtenu). Les Flocons de bienvenue partent à la 1re case ou à la 1re visite du monde de Noël, au plus `comptes-par-ip`
comptes d'une même connexion.

Les vrais bonhommes de neige des versions 1.x à 2.0 (`snowmen.yml`) sont remplacés à leur chargement : leur place passe
dans `bonhommes.yml`, l'entité est retirée et son chunk n'est plus gardé chargé.

## Données

Base MariaDB (pilote et HikariCP chargés par `libraries:`), tables créées au démarrage, toutes avec une colonne `annee` :
changer `annee` repart de zéro sans rien effacer, l'année précédente reste en base.

| Table | Contenu |
|---|---|
| `shxmas_gains` | Journal des gains (source, base, versé, facteur) |
| `shxmas_joueurs` | Ce qui n'arrive qu'une fois par joueur (bienvenue, carte complète, retour, livre), empreinte d'IP, temps de jeu |
| `shxmas_portes` | Portes ouvertes |
| `shxmas_sapin` | Contribution de chaque joueur au sapin |
| `shxmas_compteurs`, `shxmas_tampons` | Carte de Noël |
| `shxmas_achats` | Achats de la boutique |
| `shxmas_cadeaux` | Cadeaux entre joueurs (un objet = une ligne) |
| `shxmas_livraisons` | Récompenses gardées pour un joueur hors ligne |
| `shxmas_ile` | Petits cadeaux trouvés par jour |
| `shxmas_clicker` | Clics et niveau du clicker |
| `shxmas_actions` | Actions datées et paliers du sapin déjà lancés |
| `shxmas_etat` | Réglages de l'évènement (cible du sapin, multiplicateur, médianes des fenêtres) |

Au premier démarrage, l'ancien `playerdata.yml` du clicker (Noël 2025) est repris une fois dans `shxmas_clicker` sous
l'année 2025.

Toutes les données de l'année sont lues au démarrage et tenues en mémoire ; chaque changement est écrit en file, dans
l'ordre, hors du fil principal. Avec les mondes en parallèle de DivineMC, les commandes et les événements des joueurs
arrivent sur le fil de leur monde : tout ce qui change les données est rejoué sur le fil principal.

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
