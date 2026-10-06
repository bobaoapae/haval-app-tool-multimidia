import socket
import time

HOST = "192.168.33.117"
PORT = 23

def run_cmd(cmd):
    try:
        print(f"Connecting to {HOST}:{PORT}...")
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(5)
        s.connect((HOST, PORT))
        time.sleep(1)
        print(f"Sending command: {cmd}")
        s.sendall(cmd.encode('ascii') + b"\n")
        time.sleep(2)
        data = s.recv(4096)
        print("Output:")
        print(data.decode('ascii', errors='ignore'))
        s.close()
    except Exception as e:
        print(f"Error: {e}")

run_cmd("umount /vendor/app/AndroidAutoApp/AndroidAutoApp.apk")
run_cmd("am force-stop com.ts.androidauto.app")
