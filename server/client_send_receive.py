import socket
import sys

HOST, PORT = "localhost", 10000
#data = " ".join(sys.argv[1:])
data = '{"T":23,"L":120,"H":45}'

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

sock.sendto(data.encode("utf-8"), (HOST, PORT))
received = sock.recv(1024)

print("Sent:     {}".format(data))
print(f"Received: {received.decode('utf-8')}")