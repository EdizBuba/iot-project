import socketserver
import serial
import threading

HOST = "0.0.0.0"
UDP_PORT = 10000
LAST_VALUE = "{}"

SERIALPORT = "COM3"   # mettre COM3/COM4 si branché plus tard
BAUDRATE = 115200

ser = serial.Serial()
uart_available = False


def is_display_command(msg: str) -> bool:
    allowed = {"T", "L", "H"}
    if not msg:
        return False
    if any(c not in allowed for c in msg):
        return False
    if len(set(msg)) != len(msg):
        return False
    return True


class ThreadedUDPRequestHandler(socketserver.BaseRequestHandler):
    def handle(self):
        global LAST_VALUE

        raw_data = self.request[0].strip()
        sock = self.request[1]
        current_thread = threading.current_thread()

        try:
            data = raw_data.decode("utf-8")
        except AttributeError:
            data = raw_data

        print(f"{current_thread.name}: client {self.client_address}, wrote: {data}")

        if data != "":
            if is_display_command(data):
                sendUARTMessage(data)
                sock.sendto(b"OK", self.client_address)

            elif data == "GET":
                sock.sendto(LAST_VALUE.encode("utf-8"), self.client_address)

            elif data.startswith("{"):
                LAST_VALUE = data
                sock.sendto(b"OK", self.client_address)
                print("Stored JSON:", LAST_VALUE)

            else:
                print("Unknown message:", data)
                sock.sendto(b"ERROR: unknown message", self.client_address)


class ThreadedUDPServer(socketserver.ThreadingMixIn, socketserver.UDPServer):
    pass


def initUART():
    global uart_available

    ser.port = SERIALPORT
    ser.baudrate = BAUDRATE
    ser.bytesize = serial.EIGHTBITS
    ser.parity = serial.PARITY_NONE
    ser.stopbits = serial.STOPBITS_ONE
    ser.timeout = None
    ser.xonxoff = False
    ser.rtscts = False
    ser.dsrdtr = False

    print("Starting Up Serial Monitor")
    try:
        ser.open()
        uart_available = True
        print(f"Serial {SERIALPORT} opened")
    except serial.SerialException:
        uart_available = False
        print(f"Serial {SERIALPORT} port not available, UART disabled")


def sendUARTMessage(msg):
    if not uart_available:
        print(f"[UART disabled] Message not sent to micro-controller: {msg}")
        return

    ser.write((msg + "\n").encode("utf-8"))
    print("Message <" + msg + "> sent to micro-controller.")


if __name__ == "__main__":
    initUART()
    print("Press Ctrl-C to quit.")

    server = ThreadedUDPServer((HOST, UDP_PORT), ThreadedUDPRequestHandler)
    server_thread = threading.Thread(target=server.serve_forever)
    server_thread.daemon = True

    try:
        server_thread.start()
        print(f"Server started at {HOST} port {UDP_PORT}")

        while True:
            if uart_available and ser.is_open:
                if ser.in_waiting > 0:
                    data_bytes = ser.read(ser.in_waiting)
                    data_str = data_bytes.decode("utf-8", errors="ignore").strip()
                    if data_str:
                        LAST_VALUE = data_str
                        print("Received from UART:", data_str)

    except (KeyboardInterrupt, SystemExit):
        server.shutdown()
        server.server_close()
        if uart_available and ser.is_open:
            ser.close()
        print("Server stopped")