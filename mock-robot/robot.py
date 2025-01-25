import socket


def udp_listener(port):
    # Create a UDP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    # Bind the socket to the port
    server_address = ('0.0.0.0', port)
    print(f"Listening on port {port}...")
    sock.bind(server_address)

    try:
        while True:
            # Receive data
            data, address = sock.recvfrom(4096)

            # Print the received data and the sender's address
            print(f"Received message from {address}: {data.decode()}")

    except KeyboardInterrupt:
        print("\nServer is shutting down...")
    finally:
        # Close the socket
        sock.close()


if __name__ == "__main__":
    udp_listener(12345)
