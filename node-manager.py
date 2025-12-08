#!/usr/bin/env python3
"""
Node Manager Service - Manages Raft cluster nodes for demo purposes
Runs on port 5002 and can start/stop individual nodes
"""

from flask import Flask, jsonify, request
from flask_cors import CORS
import subprocess
import os
import signal
import time

app = Flask(__name__)
CORS(app)

# Track running node processes
node_processes = {}

# Project directory (where the jar file is)
PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
JAR_PATH = os.path.join(PROJECT_DIR, "target", "raft-cache-1.0-SNAPSHOT.jar")

# Java path - use Homebrew OpenJDK 17 if available
JAVA_PATH = "/opt/homebrew/opt/openjdk@17/bin/java"
if not os.path.exists(JAVA_PATH):
    JAVA_PATH = "java"  # Fall back to system java

# Node configurations
NODES = {
    "node1": {"config": "config/node1.yaml", "port": 8081},
    "node2": {"config": "config/node2.yaml", "port": 8082},
    "node3": {"config": "config/node3.yaml", "port": 8083},
}


@app.route("/status", methods=["GET"])
def status():
    """Get status of all nodes"""
    status = {}
    for node_id, info in NODES.items():
        pid = node_processes.get(node_id)
        running = False
        if pid:
            try:
                os.kill(pid, 0)  # Check if process exists
                running = True
            except OSError:
                running = False
                node_processes.pop(node_id, None)
        status[node_id] = {
            "pid": pid if running else None,
            "managed": running,
            "port": info["port"]
        }
    return jsonify(status)


@app.route("/start/<node_id>", methods=["POST"])
def start_node(node_id):
    """Start a specific node"""
    if node_id not in NODES:
        return jsonify({"error": f"Unknown node: {node_id}"}), 400

    # Check if already running (managed by us)
    if node_id in node_processes:
        try:
            os.kill(node_processes[node_id], 0)
            # Process exists, but let's check if port is actually in use
            port = NODES[node_id]["port"]
            import socket
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            result = sock.connect_ex(('localhost', port))
            sock.close()
            if result == 0:
                return jsonify({"error": f"Node {node_id} is already running", "pid": node_processes[node_id]}), 400
            else:
                # Process exists but port not listening, clean up
                node_processes.pop(node_id, None)
        except OSError:
            node_processes.pop(node_id, None)

    # Also check if port is in use by another process
    port = NODES[node_id]["port"]
    try:
        result = subprocess.run(f"lsof -ti:{port}", shell=True, capture_output=True, text=True)
        if result.stdout.strip():
            # Kill any existing process on that port
            subprocess.run(f"lsof -ti:{port} | xargs kill -9", shell=True)
            time.sleep(1)
    except Exception:
        pass

    config = NODES[node_id]["config"]
    config_path = os.path.join(PROJECT_DIR, config)
    log_path = f"/tmp/{node_id}.log"

    # Start the node
    cmd = [JAVA_PATH, "-jar", JAR_PATH, "--config", config_path]

    with open(log_path, "w") as log_file:
        process = subprocess.Popen(
            cmd,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            cwd=PROJECT_DIR,
            preexec_fn=os.setsid  # Create new process group
        )

    node_processes[node_id] = process.pid

    # Wait a bit for the node to start
    time.sleep(4)

    return jsonify({
        "status": "started",
        "node_id": node_id,
        "pid": process.pid,
        "port": NODES[node_id]["port"],
        "log": log_path
    })


@app.route("/stop/<node_id>", methods=["POST"])
def stop_node(node_id):
    """Stop a specific node"""
    if node_id not in NODES:
        return jsonify({"error": f"Unknown node: {node_id}"}), 400

    pid = node_processes.get(node_id)

    if pid:
        try:
            # Kill the process group
            os.killpg(os.getpgid(pid), signal.SIGTERM)
            node_processes.pop(node_id, None)
            return jsonify({"status": "stopped", "node_id": node_id, "pid": pid})
        except OSError as e:
            node_processes.pop(node_id, None)
            return jsonify({"status": "already_stopped", "node_id": node_id, "error": str(e)})
    else:
        # Try to find and kill any java process on that port
        port = NODES[node_id]["port"]
        try:
            result = subprocess.run(
                f"lsof -ti:{port} | xargs kill -9 2>/dev/null",
                shell=True,
                capture_output=True
            )
            return jsonify({"status": "stopped", "node_id": node_id, "note": "Killed by port"})
        except Exception as e:
            return jsonify({"status": "not_running", "node_id": node_id})


@app.route("/kill/<node_id>", methods=["POST"])
def kill_node(node_id):
    """Kill a specific node (alias for stop)"""
    return stop_node(node_id)


@app.route("/restart/<node_id>", methods=["POST"])
def restart_node(node_id):
    """Restart a specific node"""
    stop_node(node_id)
    time.sleep(1)
    return start_node(node_id)


@app.route("/start-all", methods=["POST"])
def start_all():
    """Start all nodes"""
    results = {}
    for node_id in NODES:
        try:
            response = start_node(node_id)
            results[node_id] = "started"
        except Exception as e:
            results[node_id] = str(e)
    return jsonify(results)


@app.route("/stop-all", methods=["POST"])
def stop_all():
    """Stop all nodes"""
    results = {}
    for node_id in NODES:
        try:
            stop_node(node_id)
            results[node_id] = "stopped"
        except Exception as e:
            results[node_id] = str(e)
    return jsonify(results)


if __name__ == "__main__":
    print("=" * 50)
    print("Node Manager Service")
    print("=" * 50)
    print(f"Project Dir: {PROJECT_DIR}")
    print(f"JAR Path: {JAR_PATH}")
    print(f"Running on http://localhost:5002")
    print("=" * 50)
    print("Endpoints:")
    print("  GET  /status         - Get status of all nodes")
    print("  POST /start/<node>   - Start a node (node1, node2, node3)")
    print("  POST /stop/<node>    - Stop a node")
    print("  POST /restart/<node> - Restart a node")
    print("  POST /start-all      - Start all nodes")
    print("  POST /stop-all       - Stop all nodes")
    print("=" * 50)
    app.run(host="0.0.0.0", port=5002, debug=False)
