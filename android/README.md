# Application Android - Visualisation et configuration

Ce dossier contient l'application Android du projet. Elle se connecte au serveur UDP pour consulter les mesures et envoyer les commandes d'affichage.

## 1. Objectif de l'application
L'application remplit deux fonctions principales :
- afficher les mesures remontees par le serveur
- envoyer un ordre d'affichage pour le sender via la chaine serveur -> receiver -> radio

## 2. Structure utile du projet
- `app/src/main/java/fr/cpe/iotudpapp/ConnectionActivity.kt` : ecran de connexion et validation IP/port
- `app/src/main/java/fr/cpe/iotudpapp/MainActivity.kt` : ecran principal, ecoute UDP et commandes
- `app/src/main/res/values/strings.xml` : libelles et valeurs par defaut

## 3. Parcours utilisateur
### 3.1 Ecran de connexion
L'utilisateur saisit :
- l'adresse IP du serveur
- le port UDP du serveur

Au clic sur connexion, `ConnectionActivity` :
- valide le format IPv4 et le port
- ouvre une socket UDP temporaire
- envoie `GET`
- attend une reponse du serveur
- ouvre `MainActivity` si le serveur repond

### 3.2 Ecran principal
Une fois ouvert, `MainActivity` :
- ouvre une socket UDP persistante
- envoie `subscribe()` puis `GET`
- ecoute en continu les JSON du serveur
- met a jour temperature, luminosite, humidite et pression
- affiche le dernier message JSON brut pour le debug
- permet d'envoyer `GET`, `TLH`, `LTH` et `THL`

## 4. Messages attendus du serveur
L'application comprend principalement ces types JSON :
- `data`
- `subscribed`
- `config_sent`
- `config_ack`
- `status`
- `error`
- `config_error`

Les donnees capteurs peuvent etre lues avec les clefs longues ou courtes :
- `temperature` ou `T`
- `light` ou `L`
- `humidity` ou `H`
- `pressure` ou `P`

## 5. Commandes envoyees au serveur
Depuis l'interface actuelle :
- `GET`
- `TLH`
- `LTH`
- `THL`

Envoye automatiquement a l'ouverture de l'ecran principal :
- `subscribe()`
- `GET`

## 6. Valeurs par defaut importantes
Dans `strings.xml` :
- `default_ip = 10.0.2.2`
- `default_port = 10000`

Attention : `10.0.2.2` correspond a l'hote vu depuis un emulateur Android. Sur smartphone reel, il faut remplacer cette valeur par l'IP locale du PC serveur.

## 7. Construction et execution
Le plus simple est d'ouvrir `android/` avec Android Studio sur la machine hote.

Verifier avant execution :
- que le serveur Python tourne
- que le smartphone ou l'emulateur peut joindre le PC serveur
- que l'IP et le port sont corrects

## 8. Comportement reseau important
L'application n'est pas un simple emetteur UDP :
- elle envoie des commandes
- elle ecoute aussi les reponses du serveur
- elle maintient une socket ouverte pour recevoir les mises a jour poussees

Ce point est important car l'application ne se limite pas a une emission simple en UDP : elle maintient une communication bidirectionnelle avec retour des mesures et des ACK de configuration.

## 9. Limites actuelles
- l'interface expose seulement trois presets de mode : `TLH`, `LTH`, `THL`
- il n'y a pas encore de selection d'objet parmi plusieurs objets
- les mesures ne sont pas archivees dans l'application

## 10. Lien avec le reste du projet
- le serveur consomme et produit le protocole decrit dans `../server/README.md`
- le firmware micro:bit est documente dans `../../micro-bit/README.md`