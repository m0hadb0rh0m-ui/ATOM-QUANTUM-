# ATOM QUANTUM — Android

Native Android client written in Java-compatible Android SDK APIs, built with Gradle. The original Streamlit source is preserved separately in `app.py`.

## Build APK
Open **Actions → Build ATOM QUANTUM Android APK → Run workflow**. After a successful run, download **ATOM-QUANTUM-debug-APK** from the workflow artifacts.

## Connect to Ollama
Set the Ollama server URL in the app settings. For a physical phone, use a reachable server address such as `http://192.168.1.10:11434`, **not** `localhost` (which refers to the phone). Configure Ollama to listen on the network and protect the endpoint; avoid exposing it to the public internet without authentication/TLS.

The app provides chat, model selection, persistent local conversation, and connection settings. Image/video generation and the Streamlit WebGL studio remain in the Python backend; they are **not** implemented natively in this APK.

## Security
This debug app permits HTTP for local Ollama connections. Use HTTPS and appropriate server-side authentication for remote deployments.

## Full original ATOM QUANTUM studio on Android

The **🌌 فتح الاستوديو الكامل** button opens the existing Streamlit `app.py` interface inside an Android WebView. This preserves the original code and exposes its tabs (chat, 3D, images, video, settings) **when their backends are available**.

On the computer/server hosting the app:

```bash
python -m pip install streamlit requests torch
streamlit run app.py --server.address 0.0.0.0 --server.port 8501
```

Install other Python packages required by the chosen media pipelines separately (e.g. `diffusers`, `transformers`, `accelerate`, `imageio-ffmpeg`). Install and start Ollama on the server, and download the configured models. The Streamlit backend's default Ollama URL `http://localhost:11434` is appropriate only if Ollama and Streamlit run on the same server.

On the Android device, tap **🌌 فتح الاستوديو الكامل** and enter `http://SERVER_LAN_IP:8501`, replacing `SERVER_LAN_IP` with the server's reachable IP. Both devices should be on the same network. If using a public endpoint, configure HTTPS, authentication and access controls; the original app does not implement secure multi-user authentication. **Do not expose the Streamlit server or Ollama endpoint directly to the public internet.**

The native chat screen uses a **separate Ollama URL**, configured from the gear icon, while the full studio uses the Streamlit URL. The two screens do not share conversation databases. Image, video and 3D generation require the original backend's corresponding dependencies, model weights and compatible compute resources; the WebView itself does not perform generation.
