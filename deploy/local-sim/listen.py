import socket
import sys

port = 30000 + int(sys.argv[1])
server = socket.socket()
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind(('127.0.0.1', port))
server.listen(8)
while True:
    connection, _ = server.accept()
    connection.close()
