# Serveur Python - Passerelle UDP vers UART

Ce dossier contient la passerelle Python qui relie le smartphone Android au firmware receiver branche en USB.

## 1. Fichier principal
- `serveur.py` : serveur final utilise pour la demo

Autres fichiers presents :
- `controller.py` : exemple initial fourni par l'enseignant
- `client_send.py` : petit client de test en emission seule
- `client_send_receive.py` : petit client de test emission + reception

## 2. Fonctionnement general
Le serveur ouvre deux interfaces en parallele :
- un port serie UART vers la micro:bit receiver
- une socket UDP vers les clients Android ou outils de test

Son role est de traduire :
- les lignes UART `DATA|...` et `CFG-ACK|...` en JSON UDP
- les commandes UDP `GET`, `subscribe()`, `TLH`, etc. en commandes serie `CFG|...`

## 3. Execution

```bash
cd /workspaces/iot-project
source .venv/bin/activate
python server/serveur.py --serial-port /dev/cu.usbmodemXXXXX --baudrate 115200 --udp-host 0.0.0.0 --udp-port 10000
```

Arguments disponibles :
- `--serial-port` : obligatoire
- `--baudrate` : optionnel, par defaut `115200`
- `--udp-host` : optionnel, par defaut `0.0.0.0`
- `--udp-port` : optionnel, par defaut `10000`

## 4. Ce que le serveur lit sur l'UART
Le firmware receiver peut emettre des logs humains et des lignes machine.

Lignes machine attendues :

```text
DATA|69|25|98|42|996
CFG-ACK|12|TLH
CFG-ERR|ordre invalide
```

Seules ces lignes pilotent le comportement applicatif du serveur.

## 5. Ce que le serveur expose en UDP
### 5.1 Commandes supportees
- `GET`
- `getValues()`
- `GET_VALUES`
- `subscribe()`
- `unsubscribe()`
- `getStatus()`
- `GET_STATUS`
- un ordre brut comme `TLH` ou `TLHP`
- une commande `CFG|TLH`

### 5.2 Reponses JSON principales
Exemple de donnees :

```json
{
  "type": "data",
  "sequence": 69,
  "temperature": 25,
  "light": 98,
  "humidity": 42,
  "pressure": 996,
  "T": 25,
  "L": 98,
  "H": 42,
  "P": 996
}
```

Exemple de configuration envoyee :

```json
{
  "type": "config_sent",
  "order": "TLH"
}
```

Exemple d'accuse de configuration :

```json
{
  "type": "config_ack",
  "sequence": 12,
  "order": "TLH"
}
```

## 6. Structure interne de `serveur.py`
Les elements principaux sont :
- `SensorData` : structure typant la derniere mesure connue
- `ConfigAck` : structure typant le dernier acquittement de configuration
- `parse_data_line()` : parse les lignes `DATA|...`
- `parse_config_ack_line()` : parse les lignes `CFG-ACK|...`
- `UdpUartGateway` : boucle principale de pontage

Dans `UdpUartGateway` :
- `handle_serial_line()` traite les lignes venant de la micro:bit
- `handle_udp_message()` traite les commandes des clients
- `broadcast_json()` notifie tous les abonnes
- `send_config()` pousse un ordre d'affichage vers la receiver

## 7. Logs utiles pour le debug
Exemples de traces normales :

```text
Gateway server started
SERIAL> DATA|69|25|98|42|996
UDP<10.42.233.42:51234> subscribe()
UDP<10.42.233.42:51234> GET
UDP<10.42.233.42:51234> TLH
SERIAL> CFG-ACK|12|TLH
```

## 8. Limites actuelles
- le serveur stocke seulement la derniere mesure en memoire
- il n'y a pas encore de base SQLite ou InfluxDB
- il n'y a pas encore de gestion multi-objet avec identifiants
- les clients morts sont seulement purges a l'echec d'envoi

## 9. Lien avec le reste du projet
- le firmware receiver documente le format UART dans `../../micro-bit/docs/protocole-radio-final.md`
- l'application Android consomme ce contrat, voir `../android/README.md`