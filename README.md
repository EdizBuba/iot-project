# Mini-projet IoT - Serveur et application Android

Ce depot contient la partie haute de l'architecture IoT du projet :
- le serveur passerelle UDP <-> UART
- l'application Android qui dialogue avec ce serveur

Le firmware micro:bit se trouve dans le depot voisin `micro-bit`.

## 1. Architecture couverte par ce depot

```text
micro:bit sender -> radio -> micro:bit receiver -> UART USB -> serveur Python -> UDP -> application Android
application Android -> UDP -> serveur Python -> UART USB -> micro:bit receiver -> radio -> micro:bit sender
```

## 2. Contenu du depot
- `server/serveur.py` : passerelle principale entre l'UART de la micro:bit receiver et les clients UDP
- `server/controller.py` : ancien exemple fourni, conserve comme reference historique
- `server/client_send.py` et `server/client_send_receive.py` : scripts de test UDP simples
- `android/` : application Android de visualisation et configuration

## 3. Role du serveur
Le serveur lit les lignes serie produites par le firmware receiver et les convertit en messages JSON pour Android.

Il sait :
- memoriser la derniere mesure recue
- diffuser les nouvelles mesures aux clients abonnes
- transmettre une configuration d'affichage vers le sender via la receiver
- renvoyer un accusé de configuration a Android quand le sender confirme l'ordre applique

## 4. Role de l'application Android
L'application permet de :
- saisir l'IP et le port du serveur
- verifier que le serveur repond en UDP
- afficher temperature, luminosite, humidite et pression
- demander la derniere mesure avec `GET`
- envoyer des ordres d'affichage `TLH`, `LTH` et `THL`

## 5. Demarrage rapide
### 5.1 Serveur

```bash
cd /workspaces/iot-project
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install pyserial
python server/serveur.py --serial-port /dev/cu.usbmodemXXXXX --udp-port 10000
```

### 5.2 Application Android
- ouvrir le projet `android/` dans Android Studio sur la machine hote
- verifier l'IP du serveur dans l'ecran de connexion
- lancer l'application sur emulateur ou smartphone reel

## 6. Contrat reseau resume
Commandes UDP reconnues par le serveur :
- `GET`
- `getValues()`
- `GET_VALUES`
- `subscribe()`
- `unsubscribe()`
- `getStatus()`
- `GET_STATUS`
- `TLH`, `LTH`, `THL`, `TLHP`
- `CFG|<ordre>`

Reponses JSON principales :
- `data`
- `config_sent`
- `config_ack`
- `status`
- `subscribed`
- `unsubscribed`
- `error`
- `config_error`

## 7. Documentation detaillee
- `server/README.md` : protocole, execution et structure du serveur
- `android/README.md` : organisation et fonctionnement de l'application Android
- `../micro-bit/README.md` : vue globale du projet complet
- `../micro-bit/docs/protocole-radio-final.md` : specification radio, UART et UDP

## 8. Points d'attention
- le serveur doit etre connecte a la micro:bit receiver, pas a la sender
- le port UART par defaut du firmware receiver est `115200`
- l'adresse `10.0.2.2` ne vaut que pour un emulateur Android ; sur smartphone reel il faut l'IP du PC sur le Wi-Fi local
- l'application Android build plus simplement sur l'hote que dans le conteneur de dev