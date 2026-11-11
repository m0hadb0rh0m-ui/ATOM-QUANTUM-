# ATOM AI X — Native Android + Cloud AI Gateway

Development branch: feat/atom-ai-x-native. The original ATOM QUANTUM app and app.py are preserved for comparison.

## What is implemented
- Native Android Kotlin + Jetpack Compose + Material 3 (not a WebView).
- Arabic and English interface with live RTL/LTR switching.
- Light, dark and system theme.
- Room-backed history: new chat, rename, pin, delete and search titles.
- Real model output only: streaming from FastAPI over SSE; cancellation and failure feedback.
- Markdown, tables, copy, share, native Android dictation and text-to-speech.
- Fast/Study/Coding/Analysis modes implemented as distinct system prompts.
- FastAPI gateway, upstream HF streaming, /health, /v1/models, input validation, basic IP and daily quotas.
- CI workflow with Android debug APK artifact and backend unit tests.

## What is NOT implemented yet
- No backend has been automatically deployed. Cloud AI cannot answer until the gateway is online.
- No web search, RAG, OCR, file ingestion, image generation/editing, memory synchronization or code sandbox.
- Full LaTeX rendering, code syntax highlighting, Android device smoke testing and production auth still pending.
- This project does NOT contain any commercial model weights. Hugging Face routes inference to a provider.
- FastAPI status 'configured' verifies an environment variable is present, not actual model availability.

## Deploy backend to Hugging Face Spaces
1. Create a Hugging Face Space with SDK **Docker**.
2. Copy backend/main.py, backend/requirements.txt and backend/Dockerfile to the Space repository root.
3. Use docs/hf-space/README.md as its Space README (YAML declares Docker and port 7860).
4. Add secret HF_TOKEN to Space Settings > Variables and secrets > Secrets. Never check it into GitHub.
5. Optionally set ATOM_MODEL to a supported provider model ID and ATOM_RPM / ATOM_DAILY_LIMIT to safe usage limits.
6. Verify https://YOUR-SPACE.hf.space/health returns a JSON response.
7. Send an actual POST to /v1/chat/stream with {"messages":[{"role":"user","content":"Hello"}]} to establish REAL model output; /health alone is not sufficient.
8. GitHub repository > Settings > Secrets and variables > Actions > Variables: set ATOM_BACKEND_URL to your verified https://YOUR-SPACE.hf.space URL. This is a public service URL, not an API secret.
9. Run ATOM AI X - Android and Backend CI, then download the ATOM-AI-X-debug-APK artifact and install it.

Users of the APK never enter a Gemini/OpenAI API key or an Ollama address. The operator must set up the backend.

## Safety, privacy, operating costs
- The Space calls https://router.huggingface.co/v1/chat/completions, and chat content goes to the configured model provider.
- HF_TOKEN is only stored as a server secret. The app uses HTTPS only.
- The supplied per-IP and daily quotas are in-memory, restartable and NOT production-grade anti-abuse protection.
- A public Space may be abused. Add authentication, provider spending limits and a persistent distributed rate limiter before broad release.
- The free Hugging Face inference allowance is limited and may change. No unlimited free inference is promised.
- Chats are stored in an on-device Room SQLite database. Android backup is disabled.
- Dictation is provided by an installed Android speech recognition service; it may process audio remotely.
- Text-to-speech uses the user's Android speech engine.
- No private conversation contents are logged by the ATOM gateway.

## Build
Android AGP 8.7.3, Kotlin 2.0.21, Compose compiler plugin 2.0.21, Gradle 8.9, JDK 17, compileSdk 35.
A checked-in Gradle Wrapper and Gradle 8.9 distribution URL are present; CI uses ./gradlew. Gradle dependencies still require downloads on a fresh build.
