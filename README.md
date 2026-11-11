# ATOM QUANTUM — Android

Native Android client written in Java-compatible Android SDK APIs, built with Gradle. The original Streamlit source is preserved separately in `app.py`.

## Build APK
Open **Actions → Build ATOM QUANTUM Android APK → Run workflow**. After a successful run, download **ATOM-QUANTUM-debug-APK** from the workflow artifacts.

## Connect to Ollama
Set the Ollama server URL in the app settings. For a physical phone, use a reachable server address such as `http://192.168.1.10:11434`, **not** `localhost` (which refers to the phone). Configure Ollama to listen on the network and protect the endpoint; avoid exposing it to the public internet without authentication/TLS.

The app provides chat, model selection, persistent local conversation, and connection settings. Image/video generation and the Streamlit WebGL studio remain in the Python backend; they are **not** implemented natively in this APK.

## Security
This debug app permits HTTP for local Ollama connections. Use HTTPS and appropriate server-side authentication for remote deployments.
