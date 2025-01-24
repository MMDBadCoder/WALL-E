import socket
import re
import keyboard  # Install using: pip install keyboard
from colorama import Fore, Style, init  # Install using: pip install colorama

# Initialize colorama
init(autoreset=True)

# Constants
PORT = 12345  # Port number of the ESP32 UDP server
RANGE_SIZE = 10  # Number of IP addresses to try (e.g., 100 to 109)

# Motor power settings
MAX_POWER = 100
MID_POWER = 80  # Mid-level power for turning

SPECIFIC_IP_FOR_ROBOT = "10.42.0.218"


def get_local_ip():
    """Get the local machine's IP address."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        return local_ip
    except Exception as e:
        print(f"{Fore.RED}Error getting local IP: {e}{Style.RESET_ALL}")
        return None


def generate_ip_range(local_ip):
    """Generate a range of IP addresses based on the local IP."""
    match = re.match(r"(\d+\.\d+\.\d+\.)\d+", local_ip)
    if not match:
        raise ValueError(f"{Fore.RED}Invalid local IP address format{Style.RESET_ALL}")

    prefix = match.group(1)
    last_octet = int(local_ip.split(".")[-1])

    start = (last_octet // 100) * 100
    return [f"{prefix}{i}" for i in range(start, start + RANGE_SIZE)]


def create_udp_socket():
    """Create a UDP socket."""
    try:
        udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        print(f"{Fore.GREEN}UDP socket created{Style.RESET_ALL}")
        return udp_socket
    except Exception as e:
        print(f"{Fore.RED}Error creating UDP socket: {e}{Style.RESET_ALL}")
        return None


def send_motor_values(udp_socket, ip, left_power, right_power):
    """Send motor values to the ESP32 over UDP."""
    try:
        message = f"{left_power},{right_power}\n"
        udp_socket.sendto(message.encode(), (ip, PORT))
        print(f"{Fore.CYAN}Sent motor values to {ip}: {message.strip()}{Style.RESET_ALL}")
    except Exception as e:
        print(f"{Fore.RED}Error sending data: {e}{Style.RESET_ALL}")


def calculate_motor_powers(forward, backward, left, right):
    """Calculate motor powers based on key presses."""
    if forward and not backward and not left and not right:
        # Forward only
        return MAX_POWER, MAX_POWER
    elif backward and not forward and not left and not right:
        # Backward only
        return -MAX_POWER, -MAX_POWER
    elif left and not forward and not backward and not right:
        # Left only: Right motor powered
        return 0, MAX_POWER
    elif right and not forward and not backward and not left:
        # Right only: Left motor powered
        return MAX_POWER, 0
    elif forward and left:
        # Forward + Left: Right motor at MAX, left motor at MID
        return MID_POWER, MAX_POWER
    elif forward and right:
        # Forward + Right: Left motor at MAX, right motor at MID
        return MAX_POWER, MID_POWER
    elif backward and left:
        # Backward + Left: Right motor at -MAX, left motor at -MID
        return -MID_POWER, -MAX_POWER
    elif backward and right:
        # Backward + Right: Left motor at -MAX, right motor at -MID
        return -MAX_POWER, -MID_POWER
    else:
        # Stop
        return 0, 0


def main():
    # Get the local IP address
    local_ip = get_local_ip()
    if not local_ip:
        print(f"{Fore.RED}Could not determine local IP address. Exiting.{Style.RESET_ALL}")
        ip_range = [SPECIFIC_IP_FOR_ROBOT]
    else:
        # Generate the IP range to scan
        ip_range = generate_ip_range(local_ip)

    print(f"{Fore.CYAN}Generated IP range: {ip_range}{Style.RESET_ALL}")

    # Create a UDP socket
    udp_socket = create_udp_socket()
    if not udp_socket:
        print(f"{Fore.RED}Could not create UDP socket. Exiting.{Style.RESET_ALL}")
        return

    print(
        f"{Fore.CYAN}Press ↑ (forward), ↓ (backward), ← (left), → (right) to control the motors. Press 'q' to quit.{Style.RESET_ALL}")

    try:
        while True:
            # Check key states
            try:
                forward = keyboard.is_pressed('up')  # ↑ key
                backward = keyboard.is_pressed('down')  # ↓ key
                left = keyboard.is_pressed('left')  # ← key
                right = keyboard.is_pressed('right')  # → key
                quit_program = keyboard.is_pressed('q')  # 'q' key
            except ValueError as e:
                print(f"{Fore.RED}Error reading key: {e}{Style.RESET_ALL}")
                continue

            # Calculate motor powers
            left_power, right_power = calculate_motor_powers(forward, backward, left, right)

            # Send motor values to ESP32 over UDP
            for ip in ip_range:
                send_motor_values(udp_socket, ip, left_power, right_power)

            # Exit on 'q' key press
            if quit_program:
                print(f"{Fore.YELLOW}Exiting...{Style.RESET_ALL}")
                break

            # Small delay to avoid flooding the ESP32
            keyboard.read_event()  # Clear the keyboard buffer
    finally:
        # Stop motors and close socket
        for ip in ip_range:
            send_motor_values(udp_socket, ip, 0, 0)
        udp_socket.close()
        print(f"{Fore.GREEN}UDP socket closed.{Style.RESET_ALL}")


if __name__ == "__main__":
    main()
