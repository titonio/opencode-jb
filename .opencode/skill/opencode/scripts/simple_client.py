import requests
import json
import time
import sys

BASE_URL = "http://127.0.0.1:4096"

def check_server():
    try:
        response = requests.get(f"{BASE_URL}/doc")
        return response.status_code == 200
    except requests.exceptions.ConnectionError:
        return False

def list_sessions():
    response = requests.get(f"{BASE_URL}/session")
    response.raise_for_status()
    return response.json()

def create_session(title=None):
    payload = {}
    if title:
        payload["title"] = title
    response = requests.post(f"{BASE_URL}/session", json=payload)
    response.raise_for_status()
    return response.json()

def send_message(session_id, message):
    payload = {
        "parts": [
            {
                "type": "text",
                "text": message
            }
        ]
    }
    print(f"Sending message to session {session_id}...")
    response = requests.post(f"{BASE_URL}/session/{session_id}/message", json=payload)
    response.raise_for_status()
    return response.json()

def main():
    if not check_server():
        print(f"Error: Could not connect to OpenCode server at {BASE_URL}")
        print("Make sure to run 'opencode serve' or start the OpenCode TUI.")
        sys.exit(1)

    print("Server is running.")

    # List existing sessions
    sessions = list_sessions()
    print(f"Found {len(sessions)} existing sessions.")

    # Create a new session
    print("Creating a new session...")
    session = create_session("Test Session from Client")
    session_id = session["id"]
    print(f"Created session: {session_id}")

    # Send a message
    user_msg = "Hello, can you tell me what is 2 + 2?"
    response = send_message(session_id, user_msg)
    
    # The response from POST /message typically contains the user message and the assistant's response parts
    # Note: The actual API behavior might involve streaming or polling depending on the specific version.
    # Based on the API docs, POST /message waits for response.
    
    print("\nResponse:")
    print(json.dumps(response, indent=2))

if __name__ == "__main__":
    main()
