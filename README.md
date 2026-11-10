<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHChristmasPresents

*Un plugin d'évènement de Noël avec des cadeaux BlockDisplay et des boules de neige.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Un plugin d'évènement de Noël avec des cadeaux BlockDisplay et des boules de neige.

Utilisé sur : **Opale**.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shchristmaspresents` | `/xmas`, `/scp` | Commande principale du plugin | `shchristmaspresents.admin` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shchristmaspresents.admin` | Permet d'utiliser les commandes d'admin | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHChristmasPresents/
└── <a href="src/main/resources/config.yml">config.yml</a>
</pre>

## Plusieurs serveurs de jeu

Les cadeaux (zone `region`) et les bonhommes de neige clicker sont dans le monde `Christmas`, et c'est le seul endroit où on peut les ouvrir ou cliquer dessus. Quand les joueurs sont répartis sur plusieurs Opale, ce monde n'est hébergé que par un seul d'entre eux (celui du spawn et des événements) : un joueur qui y entre depuis un autre Opale est envoyé sur celui-là. Le plugin ne doit donc tourner que sur cet Opale :

- `playerdata.yml` (clics et niveau de chaque joueur, réécrit en entier toutes les minutes) n'a qu'un seul serveur qui l'écrit : aucune progression ne s'écrase d'un serveur à l'autre ;
- les boules de neige magiques sont des objets du joueur : elles le suivent d'un Opale à l'autre avec son inventaire et ne servent qu'à Christmas ;
- sur les autres Opale, `%shchristmaspresents_clicks%` et `%shchristmaspresents_level%` n'existent pas : le plugin y est absent, il n'y a donc pas de compteur à 0 qui viendrait écraser les vrais totaux dans un classement partagé ;
- rien n'est partagé, il n'y a ni base ni bus à configurer et SHNetwork n'est pas utilisé.

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
