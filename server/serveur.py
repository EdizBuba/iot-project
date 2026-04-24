import json
import socket
import threading
import time

try:
    import serial
except ImportError:
    serial = None


HOST = "0.0.0.0"
UDP_PORT = 10000

SERIAL_PORT = "COM3"         # Windows : "COM3" / Linux : "/dev/ttyACM0"
BAUDRATE = 115200

SAVE_FILE = "values.json"

# Dernière valeur reçue depuis la passerelle
last_value = {}
last_value_lock = threading.Lock()

# Dernière adresse Android qui a parlé au serveur
last_android_addr = None
last_android_lock = threading.Lock()

# Référence série UART
ser = None


def save_last_value(data: dict) -> None:
    with open(SAVE_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)


def load_last_value() -> dict:
    try:
        with open(SAVE_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def is_display_command(msg: str) -> bool:
    """
    Commande valide si :
    - non vide
    - contient uniquement T, L, H
    - pas de doublons
    Exemples valides : T, TL, TLH, LTH
    """
    allowed = {"T", "L", "H"}
    chars = list(msg)

    if not chars:
        return False

    if any(c not in allowed for c in chars):
        return False

    if len(set(chars)) != len(chars):
        return False

    return True


def init_uart():
    global ser

    if serial is None:
        print("[UART] pyserial n'est pas installé. UART désactivé.")
        return

    try:
        ser = serial.Serial(SERIAL_PORT, BAUDRATE, timeout=1)
        print(f"[UART] Ouvert sur {SERIAL_PORT} à {BAUDRATE} bauds")
    except Exception as e:
        ser = None
        print(f"[UART] Impossible d'ouvrir le port série : {e}")


def send_uart_message(msg: str):
    global ser

    if ser is None:
        print(f"[UART] Port non disponible, message non envoyé : {msg}")
        return

    try:
        # \n pratique pour que la passerelle lise ligne par ligne
        ser.write((msg + "\n").encode("utf-8"))
        ser.flush()
        print(f"[UART] Envoyé vers passerelle : {msg}")
    except Exception as e:
        print(f"[UART] Erreur envoi : {e}")


def uart_listener(sock: socket.socket):
    """
    Lit ce qui vient de la passerelle sur l'UART.
    Si c'est du JSON, on le stocke.
    Si une app Android est connue, on peut aussi lui pousser la donnée.
    """
    global ser, last_value, last_android_addr

    while True:
        if ser is None:
            time.sleep(1)
            continue

        try:
            if ser.in_waiting > 0:
                line = ser.readline().decode("utf-8", errors="ignore").strip()
                if not line:
                    continue

                print(f"[UART] Reçu : {line}")

                # On attend du JSON depuis la passerelle
                if line.startswith("{"):
                    try:
                        data = json.loads(line)

                        with last_value_lock:
                            last_value = data
                            save_last_value(last_value)

                        print(f"[DATA] Dernière valeur mise à jour : {data}")

                        # Envoi optionnel à Android si connu
                        with last_android_lock:
                            addr = last_android_addr

                        if addr is not None:
                            sock.sendto(json.dumps(data).encode("utf-8"), addr)
                            print(f"[UDP] Données poussées vers Android {addr}")

                    except json.JSONDecodeError:
                        print("[UART] JSON invalide reçu sur l'UART")

        except Exception as e:
            print(f"[UART] Erreur lecture : {e}")
            time.sleep(1)


def udp_server():
    global last_value, last_android_addr

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((HOST, UDP_PORT))

    print(f"[UDP] Serveur en écoute sur {HOST}:{UDP_PORT}")

    # Thread pour écouter l'UART
    uart_thread = threading.Thread(target=uart_listener, args=(sock,), daemon=True)
    uart_thread.start()

    while True:
        try:
            raw_data, client_addr = sock.recvfrom(4096)
            msg = raw_data.decode("utf-8", errors="ignore").strip()

            print(f"[UDP] Reçu de {client_addr} : {msg}")

            # On mémorise le dernier client Android
            with last_android_lock:
                last_android_addr = client_addr

            # 1) Requête Android pour obtenir la dernière valeur
            if msg == "GET":
                with last_value_lock:
                    response = json.dumps(last_value)

                sock.sendto(response.encode("utf-8"), client_addr)
                print(f"[UDP] Réponse envoyée à {client_addr} : {response}")

            # 2) Commande d'affichage à envoyer à la passerelle
            elif is_display_command(msg):
                send_uart_message(msg)
                sock.sendto(b"OK", client_addr)
                print(f"[UDP] Commande acceptée : {msg}")

            # 3) Si jamais la passerelle envoie du JSON en UDP au lieu d'UART
            elif msg.startswith("{"):
                try:
                    data = json.loads(msg)

                    with last_value_lock:
                        last_value = data
                        save_last_value(last_value)

                    sock.sendto(b"OK", client_addr)
                    print(f"[UDP] JSON stocké : {data}")

                except json.JSONDecodeError:
                    sock.sendto(b"ERROR: invalid JSON", client_addr)
                    print("[UDP] JSON invalide")

            else:
                sock.sendto(b"ERROR: unknown message", client_addr)
                print("[UDP] Message inconnu")

        except KeyboardInterrupt:
            print("\n[STOP] Arrêt du serveur")
            break
        except Exception as e:
            print(f"[UDP] Erreur serveur : {e}")


if __name__ == "__main__":
    last_value = load_last_value()
    print(f"[INIT] Dernière valeur chargée : {last_value}")

    init_uart()
    udp_server()