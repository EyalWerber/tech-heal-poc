"""
Fake BLE server — streams physiological data to the Android app over TCP.

Usage:
    python fake_ble_server.py

Controls (type in terminal and press Enter):
    <number>   — set heart rate (e.g. "120" sets HR to 120 bpm)
    q          — quit

The app connects to port 9999. Start this script BEFORE running the app,
or the app will retry automatically every 2 seconds until it connects.
"""

import os
import socket
import threading
import time
import json
import sys

PORT = 9999
INTERVAL = 1.0  # seconds between samples

current_hr = 72  # starting heart rate (normal range — avoids triggering an alert on connect)


def hr_to_sample(hr: int) -> dict:
    """Derive realistic companion metrics from heart rate."""
    if hr > 100:
        # Hyperarousal: low HRV, elevated temp, high stress
        hrv = max(5.0, 80.0 - (hr - 100) * 0.8)
        temp = 36.6 + (hr - 100) * 0.01
        stress = min(100, int((hr - 100) * 1.5 + 40))
    elif hr < 50:
        # Hypoarousal: high HRV (vagal shutdown), low temp, low stress score
        hrv = 80.0 + (50 - hr) * 0.5
        temp = 36.6 - (50 - hr) * 0.02
        stress = max(0, int(30 - (50 - hr)))
    else:
        # Normal range: moderate HRV and stress
        hrv = 20.0 + (100 - hr) * 0.5
        temp = 36.6
        stress = int((hr - 50) * 0.4)

    return {
        "timestamp": int(time.time() * 1000),
        "heart_rate": hr,
        "hrv": round(hrv, 1),
        "skin_temperature": round(temp, 2),
        "stress_score": stress,
    }


def handle_client(conn: socket.socket, addr):
    print(f"[+] App connected from {addr}")
    try:
        while True:
            sample = hr_to_sample(current_hr)
            line = json.dumps(sample) + "\n"
            conn.sendall(line.encode())
            time.sleep(INTERVAL)
    except (BrokenPipeError, ConnectionResetError, OSError):
        print(f"[-] App disconnected from {addr}")
    finally:
        conn.close()


def input_loop():
    global current_hr
    print("Controls: type a heart rate number and press Enter. 'q' to quit.")
    print(f"Starting HR: {current_hr} bpm\n")
    for line in sys.stdin:
        line = line.strip()
        if line == "q":
            print("Shutting down.")
            os._exit(0)
        try:
            hr = int(line)
            if 20 <= hr <= 220:
                current_hr = hr
                print(f"  HR set to {current_hr} bpm")
            else:
                print("  HR must be between 20 and 220")
        except ValueError:
            print("  Enter a number or 'q' to quit")


def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", PORT))
    server.listen(5)
    server.settimeout(1.0)  # allows Ctrl+C to be delivered between accept() calls
    print(f"Fake BLE server listening on port {PORT}...")

    threading.Thread(target=input_loop, daemon=True).start()

    try:
        while True:
            try:
                conn, addr = server.accept()
                threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()
            except socket.timeout:
                continue
    except KeyboardInterrupt:
        print("\nShutting down.")
    finally:
        server.close()


if __name__ == "__main__":
    main()
