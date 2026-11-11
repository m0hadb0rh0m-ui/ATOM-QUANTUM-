import streamlit as st
import sqlite3
import json
import io
import os
import re
import gc
import requests
from datetime import datetime
import torch

# ================= إعدادات الصفحة الأساسية =================
st.set_page_config(
    page_title="ATOM AI ∞ | QUANTUM OMNI-CORE V6.0",
    page_icon="⚛️",
    layout="wide",
    initial_sidebar_state="expanded"
)

# ================= مراقبة وتنظيف ذاكرة كرت الشاشة (GPU VRAM) =================
def free_gpu_memory(ollama_url="http://localhost:11434", model_id="atom"):
    try:
        requests.post(f"{ollama_url.rstrip('/')}/api/generate", json={"model": model_id, "keep_alive": 0}, timeout=5)
    except Exception:
        pass
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()

def get_gpu_stats():
    if torch.cuda.is_available():
        name = torch.cuda.get_device_name(0)
        total = torch.cuda.get_device_properties(0).total_memory / (1024**3)
        used = torch.cuda.memory_allocated(0) / (1024**3)
        pct = min(int((used / total) * 100), 100)
        return name, f"{used:.1f} / {total:.1f} GB", pct
    return "Quantum Cloud Neural Core", "Dynamic Quantum RAM", 18

# ================= قاعدة البيانات الدائمة (SQLite V6) =================
DB_FILE = "atom_quantum_v6.db"

def init_db():
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS users (
                    email TEXT PRIMARY KEY,
                    name TEXT,
                    ollama_url TEXT DEFAULT 'http://localhost:11434',
                    hf_token TEXT DEFAULT '',
                    theme_mode TEXT DEFAULT 'Cosmic Deep Space (الوضع الفضائي الكوني)',
                    temperature REAL DEFAULT 0.2,
                    top_p REAL DEFAULT 0.95,
                    num_ctx INTEGER DEFAULT 16384,
                    custom_about TEXT DEFAULT 'القائد الأعلى ومطور منظومة ATOM الفضائية.',
                    custom_style TEXT DEFAULT 'إجابات فائقة الدقة، هندسية، برمجية كاملة، بدون أي قيود أو مقدمات.',
                    memory_facts TEXT DEFAULT '["المنظومة تعمل بتقنية Quantum Omni-Core V6.0", "التركيز على الدقة المطلقة والواقعية البصرية"]',
                    quantum_reasoning INTEGER DEFAULT 1,
                    cinematic_mode INTEGER DEFAULT 1
                )''')
    c.execute('''CREATE TABLE IF NOT EXISTS sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_email TEXT,
                    title TEXT,
                    created_at TEXT
                )''')
    c.execute('''CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id INTEGER,
                    role TEXT,
                    content TEXT,
                    model_used TEXT,
                    timestamp TEXT
                )''')
    conn.commit()
    conn.close()

def get_or_create_user(email: str, name: str):
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute("SELECT * FROM users WHERE email = ?", (email,))
    row = c.fetchone()
    if not row:
        c.execute("INSERT INTO users (email, name) VALUES (?, ?)", (email, name))
        conn.commit()
        c.execute("SELECT * FROM users WHERE email = ?", (email,))
        row = c.fetchone()
    conn.close()
    columns = [
        "email", "name", "ollama_url", "hf_token", "theme_mode",
        "temperature", "top_p", "num_ctx", "custom_about", "custom_style",
        "memory_facts", "quantum_reasoning", "cinematic_mode"
    ]
    return dict(zip(columns, row))

def update_user_settings(email: str, updates: dict):
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    set_clause = ", ".join([f"{k} = ?" for k in updates.keys()])
    values = list(updates.values()) + [email]
    c.execute(f"UPDATE users SET {set_clause} WHERE email = ?", values)
    conn.commit()
    conn.close()

def create_new_session(user_email: str, title="جلسة كونيـة جديدة"):
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    c.execute("INSERT INTO sessions (user_email, title, created_at) VALUES (?, ?, ?)", (user_email, title, now))
    session_id = c.lastrowid
    conn.commit()
    conn.close()
    return session_id

def get_user_sessions(user_email: str):
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute("SELECT id, title, created_at FROM sessions WHERE user_email = ? ORDER BY id DESC", (user_email,))
    rows = c.fetchall()
    conn.close()
    return rows

def update_session_title(session_id: int, new_title: str):
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute("UPDATE sessions SET title = ? WHERE id = ?", (new_title[:38], session_id))
    conn.commit()
    conn.close()

def delete_session(session_id: int):
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute("DELETE FROM messages WHERE session_id = ?", (session_id,))
    c.execute("DELETE FROM sessions WHERE id = ?", (session_id,))
    conn.commit()
    conn.close()

def save_message_to_db(session_id, role, content, model_used):
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    c.execute('''INSERT INTO messages (session_id, role, content, model_used, timestamp)
                 VALUES (?, ?, ?, ?, ?)''', (session_id, role, content, model_used, now))
    conn.commit()
    conn.close()

def load_session_messages(session_id):
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute("SELECT role, content, model_used, timestamp FROM messages WHERE session_id = ? ORDER BY id ASC", (session_id,))
    rows = c.fetchall()
    conn.close()
    return [{"role": r[0], "content": r[1], "model_used": r[2], "timestamp": r[3]} for r in rows]

init_db()
current_user = get_or_create_user("muneer.godmode@atom.ai", "مـنيـر مـحمـد (القائد الأعلى الكوني)")

# ================= محرك الثيمات الكوني الديناميكي =================
def apply_cosmic_theme(theme_name: str):
    if "Light" in theme_name or "المضيء" in theme_name:
        bg_main = "linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)"
        text_primary = "#090d16"
        text_secondary = "#334155"
        sidebar_bg = "rgba(255, 255, 255, 0.92)"
        card_bg = "rgba(255, 255, 255, 0.88)"
        card_border = "rgba(0, 0, 0, 0.08)"
        accent_primary = "#0284c7"
        accent_secondary = "#7c3aed"
        hero_bg = "linear-gradient(135deg, #ffffff 0%, #f1f5f9 100%)"
        title_grad = "linear-gradient(90deg, #0f172a 0%, #0284c7 50%, #7c3aed 100%)"
        code_bg = "#f1f5f9"
        code_txt = "#0369a1"
    elif "Stellar" in theme_name or "الذهبي" in theme_name:
        bg_main = "radial-gradient(circle at 15% 15%, rgba(212, 175, 55, 0.12) 0%, transparent 45%), #050507"
        text_primary = "#fef3c7"
        text_secondary = "#d97706"
        sidebar_bg = "rgba(12, 10, 8, 0.92)"
        card_bg = "rgba(24, 20, 15, 0.85)"
        card_border = "rgba(212, 175, 55, 0.3)"
        accent_primary = "#f59e0b"
        accent_secondary = "#d97706"
        hero_bg = "linear-gradient(135deg, rgba(30, 24, 16, 0.9) 0%, rgba(12, 10, 8, 0.96) 100%)"
        title_grad = "linear-gradient(90deg, #ffffff 0%, #f59e0b 60%, #fbbf24 100%)"
        code_bg = "#120e0a"
        code_txt = "#fbbf24"
    else:
        bg_main = (
            "radial-gradient(circle at 10% 10%, rgba(147, 51, 234, 0.18) 0%, transparent 40%), "
            "radial-gradient(circle at 90% 90%, rgba(14, 165, 233, 0.15) 0%, transparent 40%), #02040a"
        )
        text_primary = "#f8fafc"
        text_secondary = "#94a3b8"
        sidebar_bg = "rgba(6, 9, 18, 0.9)"
        card_bg = "linear-gradient(145deg, rgba(13, 18, 32, 0.86), rgba(7, 10, 19, 0.94))"
        card_border = "rgba(255, 255, 255, 0.08)"
        accent_primary = "#00f2fe"
        accent_secondary = "#9333ea"
        hero_bg = "linear-gradient(135deg, rgba(13, 18, 32, 0.92) 0%, rgba(6, 9, 18, 0.97) 100%)"
        title_grad = "linear-gradient(90deg, #ffffff 0%, #00f2fe 45%, #c084fc 95%)"
        code_bg = "#04070e"
        code_txt = "#38bdf8"

    st.markdown(f"""
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Cairo:wght@400;600;700;900&family=JetBrains+Mono:wght@400;700&display=swap');
        #MainMenu {{visibility: hidden;}} footer {{visibility: hidden;}} header {{background: transparent !important;}}
        html, body, [class*="css"] {{ font-family: 'Cairo', sans-serif !important; direction: rtl; text-align: right; color: {text_primary} !important; }}
        .stApp {{ background: {bg_main} !important; color: {text_primary} !important; transition: background 0.4s ease; }}
        section[data-testid="stSidebar"] {{ background: {sidebar_bg} !important; backdrop-filter: blur(24px) !important; border-left: 1px solid {card_border} !important; }}
        .cosmic-hero {{ background: {hero_bg}; border: 1px solid {card_border}; border-right: 4px solid {accent_secondary}; border-left: 4px solid {accent_primary}; border-radius: 24px; padding: 28px 34px; margin-bottom: 24px; box-shadow: 0 15px 45px rgba(0, 0, 0, 0.4); }}
        .cosmic-title {{ font-size: 2.5rem; font-weight: 900; margin: 0; background: {title_grad}; -webkit-background-clip: text; -webkit-text-fill-color: transparent; }}
        .cosmic-subtitle {{ color: {text_secondary}; font-size: 1.02rem; margin-top: 6px; font-weight: 600; }}
        .hud-badge-group {{ display: flex; gap: 10px; flex-wrap: wrap; margin-top: 16px; }}
        .hud-badge {{ background: rgba(255, 255, 255, 0.04); border: 1px solid {card_border}; padding: 5px 14px; border-radius: 999px; font-size: 0.82rem; font-weight: 700; color: {text_primary}; }}
        .metric-grid {{ display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }}
        .metric-box {{ background: {card_bg}; border: 1px solid {card_border}; border-radius: 18px; padding: 16px 20px; text-align: center; }}
        .metric-val {{ font-size: 1.65rem; font-weight: 900; color: {accent_primary}; }}
        .metric-lbl {{ font-size: 0.85rem; color: {text_secondary}; font-weight: 600; }}
        div[data-testid="stTabs"] button[data-baseweb="tab"] {{ background: {card_bg} !important; border: 1px solid {card_border} !important; border-radius: 14px !important; padding: 12px 24px !important; margin-left: 10px !important; color: {text_secondary} !important; font-weight: 700 !important; }}
        div[data-testid="stTabs"] button[aria-selected="true"] {{ background: linear-gradient(135deg, {accent_secondary} 0%, {accent_primary} 100%) !important; color: #ffffff !important; box-shadow: 0 0 25px rgba(0, 242, 254, 0.35) !important; }}
        .studio-card {{ background: {card_bg}; backdrop-filter: blur(20px); border: 1px solid {card_border}; border-top: 3px solid {accent_primary}; padding: 28px; border-radius: 22px; margin-bottom: 24px; box-shadow: 0 12px 35px rgba(0, 0, 0, 0.25); }}
        .quantum-code-box {{ background: {code_bg}; border: 1px solid {accent_primary}; border-left: 4px solid {accent_primary}; padding: 16px 20px; border-radius: 14px; margin: 14px 0; font-family: 'JetBrains Mono', monospace !important; direction: ltr; text-align: left; color: {code_txt}; font-size: 0.92rem; }}
        .stButton > button[kind="primary"] {{ background: linear-gradient(90deg, {accent_secondary} 0%, {accent_primary} 100%) !important; color: #ffffff !important; border: none !important; border-radius: 14px !important; font-weight: 800 !important; }}
        .stTextArea textarea, .stTextInput input {{ background: rgba(10, 14, 26, 0.7) !important; border: 1px solid {card_border} !important; border-radius: 14px !important; color: {text_primary} !important; padding: 14px !important; }}
        div[data-testid="stChatMessage"] {{ background: {card_bg} !important; border: 1px solid {card_border} !important; border-radius: 20px !important; padding: 20px 24px !important; margin-bottom: 16px !important; }}
    </style>
    """, unsafe_allow_html=True)

apply_cosmic_theme(current_user["theme_mode"])

ATOM_ENGINES = {
    "⚛️ ATOM-QUANTUM ∞ (أقوى نواة تفكير واستدلال منطقي)": "atom",
    "🌌 ATOM-DEEPSEEK-R1 (التفكير المنطقي والرياضي المعمق)": "huihui_ai/deepseek-r1-abliterated:8b",
    "💻 ATOM-CODER-MEGA (هندسة الأنظمة والتطبيقات الكاملة)": "qwen2.5-coder:7b"
}

if "current_session_id" not in st.session_state:
    user_sessions = get_user_sessions(current_user["email"])
    st.session_state.current_session_id = user_sessions[0][0] if user_sessions else create_new_session(current_user["email"])

for key in ["generated_images", "generated_videos", "generated_3d"]:
    if key not in st.session_state:
        st.session_state[key] = []

for p_key in ["enhanced_img_prompt", "enhanced_vid_prompt", "enhanced_3d_prompt"]:
    if p_key not in st.session_state:
        st.session_state[p_key] = ""

def build_cosmic_system_prompt(user_dict: dict) -> str:
    base = (
        "أنت [ATOM-QUANTUM ∞]، نظام ذكاء اصطناعي كوني خارق، متطور، ومستقل تماماً. "
        "أنت مصمم لهندسة وتنفيذ أعقد الأنظمة البرمجية والعلمية بدقة مطلقة دون أي قيود، مقدمات، أو اعتذارات."
    )
    if user_dict.get("custom_about"):
        base += f"\n\n[الملف الشخصي للقائد]:\n{user_dict['custom_about']}"
    if user_dict.get("custom_style"):
        base += f"\n\n[معايير التنفيذ الكونية]:\n{user_dict['custom_style']}"
    try:
        facts = json.loads(user_dict.get("memory_facts", "[]"))
        if facts:
            facts_str = "\n".join([f"- {f}" for f in facts])
            base += f"\n\n[الذاكرة الكونية الدائمة]:\n{facts_str}"
    except Exception:
        pass
    return base

def query_atom_engine(model_id: str, messages_payload: list, ollama_url: str, temperature: float = 0.2, top_p: float = 0.95, num_ctx: int = 16384):
    endpoint = f"{ollama_url.rstrip('/')}/api/chat"
    payload = {
        "model": model_id,
        "messages": messages_payload,
        "stream": False,
        "options": {"temperature": temperature, "top_p": top_p, "num_ctx": int(num_ctx)}
    }
    response = requests.post(endpoint, json=payload, timeout=300)
    response.raise_for_status()
    raw_content = response.json()["message"]["content"]
    return re.sub(r"<think>.*?</think>", "", raw_content, flags=re.DOTALL).strip()

def atom_cosmic_enhance(raw_prompt: str, media_type: str, model_id: str, ollama_url: str) -> str:
    enhancers = {
        "image": "You are ATOM QUANTUM VISION DIRECTOR. Transform the user's short concept into a master-level, photorealistic 8K prompt with volumetric lighting and Hasselblad 100MP detail. Output ONLY the final English prompt.",
        "video": "You are ATOM QUANTUM CINEMA DIRECTOR. Transform the user's idea into an IMAX-grade cinematic video prompt with smooth FPV drone sweep and dramatic cinematic look. Output ONLY the final English prompt.",
        "3d": "You are ATOM QUANTUM 3D ARCHITECT. Expand the description into a technical Three.js WebGL specification with PBR shaders and OrbitControls. Output ONLY the expanded specification."
    }
    payload = [
        {"role": "system", "content": enhancers.get(media_type, enhancers["image"])},
        {"role": "user", "content": f"Elevate this concept to cosmic perfection: {raw_prompt}"}
    ]
    return query_atom_engine(model_id, payload, ollama_url, temperature=0.3)

gpu_name, gpu_mem_str, gpu_pct = get_gpu_stats()

with st.sidebar:
    st.markdown("""
    <div style="text-align:center; padding: 10px 0 16px 0;">
        <div style="font-size: 3rem; line-height:1;">🌌</div>
        <div style="font-weight:900; font-size:1.35rem;">ATOM QUANTUM ∞</div>
        <div style="font-size:0.75rem; opacity:0.8; font-weight:700;">OMNI-CORE V6.0 STELLAR</div>
    </div>
    """, unsafe_allow_html=True)

    theme_options = [
        "Cosmic Deep Space (الوضع الفضائي الكوني)",
        "Stellar Gold (وضع النجم الذهبي)",
        "Pure Light HUD (الوضع المضيء الفاخر)"
    ]
    current_th_idx = theme_options.index(current_user["theme_mode"]) if current_user["theme_mode"] in theme_options else 0
    quick_theme = st.selectbox("🌓 واجهة القيادة الكونية (Theme):", options=theme_options, index=current_th_idx)
    if quick_theme != current_user["theme_mode"]:
        update_user_settings(current_user["email"], {"theme_mode": quick_theme})
        st.rerun()

    st.markdown(f"""
    <div style="background:rgba(255,255,255,0.03); border:1px solid rgba(0,242,254,0.3); border-radius:14px; padding:12px 14px; margin-bottom:14px;">
        <div style="display:flex; justify-content:space-between; font-size:0.78rem; margin-bottom:6px;">
            <span>🖥️ <b>{gpu_name[:18]}</b></span>
            <span>{gpu_mem_str}</span>
        </div>
        <div style="width:100%; background:rgba(0,0,0,0.3); height:6px; border-radius:99px; overflow:hidden;">
            <div style="width:{gpu_pct}%; background:linear-gradient(90deg, #00f2fe, #9333ea); height:100%;"></div>
        </div>
    </div>
    """, unsafe_allow_html=True)

    selected_engine_label = st.selectbox("🧠 العقل العصبي الكوني:", options=list(ATOM_ENGINES.keys()), index=0)
    active_model_id = ATOM_ENGINES[selected_engine_label]

    if st.button("🧹 تفريغ الذاكرة العصبية (GPU VRAM)", use_container_width=True):
        free_gpu_memory(current_user["ollama_url"], active_model_id)
        st.toast("تم تفريغ ذاكرة كرت الشاشة بنجاح!", icon="⚡")

    st.markdown("---")
    if st.button("➕ جلسة كونيـة جديدة", use_container_width=True, type="primary"):
        st.session_state.current_session_id = create_new_session(current_user["email"], "جلسة كونيـة جديدة")
        st.rerun()

    st.markdown("#### 🗂️ أرشيف الجلسات العصبية")
    for s_id, s_title, s_date in get_user_sessions(current_user["email"]):
        col_s1, col_s2 = st.columns([4.2, 1])
        with col_s1:
            btn_type = "primary" if s_id == st.session_state.current_session_id else "secondary"
            if st.button(f"⚡ {s_title}", key=f"sess_{s_id}", type=btn_type, use_container_width=True):
                st.session_state.current_session_id = s_id
                st.rerun()
        with col_s2:
            if st.button("🗑️", key=f"del_{s_id}"):
                delete_session(s_id)
                rem = get_user_sessions(current_user["email"])
                st.session_state.current_session_id = rem[0][0] if rem else create_new_session(current_user["email"])
                st.rerun()

all_sessions_count = len(get_user_sessions(current_user["email"]))
imgs_count = len(st.session_state.generated_images)
vids_count = len(st.session_state.generated_videos)
d3_count = len(st.session_state.generated_3d)

st.markdown(f"""
<div class="cosmic-hero">
    <div class="cosmic-title">⚛️ ATOM-INFINITY ∞ | QUANTUM V6.0</div>
    <div class="cosmic-subtitle">منظومة الذكاء الاصطناعي الكوني المستقل — استدلال عصبي متقدم، واقعية بصرية سينمائية، وتصميم زجاجي فائق الفخامة</div>
    <div class="hud-badge-group">
        <span class="hud-badge">🌌 الوضع الكوني: نشط (UNRESTRICTED)</span>
        <span class="hud-badge">🧠 العقل: {active_model_id.upper()}</span>
        <span class="hud-badge">🎨 محرك البصرية: FLUX & Wan2.1 Quantum</span>
        <span class="hud-badge">👤 القائد: {current_user['name']}</span>
    </div>
</div>

<div class="metric-grid">
    <div class="metric-box"><div class="metric-val">{all_sessions_count}</div><div class="metric-lbl">💬 الجلسات العصبية</div></div>
    <div class="metric-box"><div class="metric-val">{d3_count}</div><div class="metric-lbl">🧊 العوالم ثلاثية الأبعاد</div></div>
    <div class="metric-box"><div class="metric-val">{imgs_count}</div><div class="metric-lbl">🎨 اللوحات الفوتوغرافية (8K)</div></div>
    <div class="metric-box"><div class="metric-val">{vids_count}</div><div class="metric-lbl">🎬 المشاهد السينمائية</div></div>
</div>
""", unsafe_allow_html=True)

tab_chat, tab_3d, tab_image, tab_video, tab_settings = st.tabs([
    "💬 غرفة القيادة العصبية (Quantum Chat)",
    "🧊 مختبر العوالم الكونية (3D WebGL)",
    "🎨 استوديو الواقعية البصرية (Quantum Vision)",
    "🎬 استوديو الإخراج السينمائي (Quantum Cinema)",
    "⚙️ مركز التحكم الكوني (Settings & AI Core)"
])

with tab_chat:
    messages = load_session_messages(st.session_state.current_session_id)
    if not messages:
        st.markdown("""
        <div class="studio-card" style="text-align:center; padding: 34px 20px;">
            <div style="font-size:2.8rem; margin-bottom:10px;">⚛️</div>
            <h3 style="margin:0;">نواة ATOM-QUANTUM V6.0 جاهزة لعمليات الاستدلال المعقدة</h3>
            <p style="opacity:0.8; font-size:0.96rem; max-width:700px; margin:10px auto 0 auto;">اطرح أصعب التحديات البرمجية أو الهندسية وسيقوم النظام بتنفيذها بأعلى معايير الدقة.</p>
        </div>
        """, unsafe_allow_html=True)

    for idx, msg in enumerate(messages):
        icon = "🧑‍💻" if msg["role"] == "user" else "⚛️"
        with st.chat_message(msg["role"], avatar=icon):
            st.caption(f"**{msg.get('model_used', 'ATOM QUANTUM')}** • `{msg.get('timestamp', '')}`")
            st.markdown(msg["content"])
            if msg["role"] == "assistant":
                c1, c2, c3 = st.columns(3)
                with c1: st.download_button("📘 تصدير Markdown", data=msg["content"].encode('utf-8'), file_name=f"ATOM_{idx+1}.md", mime="text/markdown", key=f"md_{idx}", use_container_width=True)
                with c2: st.download_button("⚙️ كود Python", data=msg["content"].encode('utf-8'), file_name=f"ATOM_Code_{idx+1}.py", mime="text/plain", key=f"py_{idx}", use_container_width=True)
                with c3: st.download_button("🌐 صفحة HTML", data=msg["content"].encode('utf-8'), file_name=f"ATOM_Web_{idx+1}.html", mime="text/html", key=f"html_{idx}", use_container_width=True)

    if prompt := st.chat_input("أصدر أمرك الكوني لنواة ATOM المستقلة..."):
        if len(messages) == 0:
            update_session_title(st.session_state.current_session_id, prompt)
        save_message_to_db(st.session_state.current_session_id, "user", prompt, current_user["name"])
        with st.chat_message("user", avatar="🧑‍💻"):
            st.markdown(prompt)
        with st.chat_message("assistant", avatar="⚛️"):
            status_box = st.empty()
            status_box.markdown("🌌 **[QUANTUM CORE]: جاري الاستدلال العصبي وتوليد الحل الكوني المتكامل...**")
            chat_payload = [{"role": "system", "content": build_cosmic_system_prompt(current_user)}]
            for past_msg in messages[-10:]:
                chat_payload.append({"role": past_msg["role"], "content": past_msg["content"]})
            chat_payload.append({"role": "user", "content": prompt})
            try:
                answer = query_atom_engine(active_model_id, chat_payload, current_user["ollama_url"], current_user["temperature"], current_user["top_p"], current_user["num_ctx"])
                status_box.markdown(answer)
                save_message_to_db(st.session_state.current_session_id, "assistant", answer, selected_engine_label)
                st.rerun()
            except Exception as e:
                status_box.error(f"⚠️ خطأ اتصال بالنواة العصبية: {str(e)}")

with tab_3d:
    st.markdown('<div class="studio-card">', unsafe_allow_html=True)
    st.markdown("### 🧊 مختبر العوالم الثلاثية الكونية (Three.js / WebGL)")
    prompt_3d = st.text_area("صف العالم ثلاثي الأبعاد:", placeholder="مثال: مجرة حلزونية تفاعلية مع حزام كويكبات متوهج وإضاءة نيون ثلاثية الأبعاد...", height=90)
    col_3d_1, col_3d_2 = st.columns(2)
    with col_3d_1:
        if st.button("✨ تحسين المواصفات الهندسية الكونية", key="btn_enh_3d", use_container_width=True):
            if prompt_3d.strip():
                with st.spinner("🧠 يقوم ATOM بحساب الخامات والهندسة الفراغية..."):
                    st.session_state.enhanced_3d_prompt = atom_cosmic_enhance(prompt_3d, "3d", active_model_id, current_user["ollama_url"])
    if st.session_state.enhanced_3d_prompt:
        st.markdown(f'<div class="quantum-code-box">{st.session_state.enhanced_3d_prompt}</div>', unsafe_allow_html=True)
    with col_3d_2:
        render_3d_clicked = st.button("🚀 بناء وتصيير العالم الكوني الآن", type="primary", use_container_width=True)
    if render_3d_clicked and prompt_3d.strip():
        with st.spinner("⏳ جاري تكوين شيفرات WebGL وبناء المحاكاة التفاعلية..."):
            try:
                final_spec = st.session_state.enhanced_3d_prompt or atom_cosmic_enhance(prompt_3d, "3d", active_model_id, current_user["ollama_url"])
                payload_3d = [
                    {"role": "system", "content": "You are ATOM 3D Quantum Architect. Return ONLY valid self-contained HTML with Three.js CDN, PBR shaders, particle dust, OrbitControls, and animations. No markdown backticks."},
                    {"role": "user", "content": f"Build an interactive 3D WebGL scene for:\n{final_spec}"}
                ]
                raw_html = query_atom_engine(active_model_id, payload_3d, current_user["ollama_url"], 0.2)
                clean_html = raw_html.replace("```html", "").replace("```", "").strip()
                st.session_state.generated_3d.insert(0, {"html": clean_html, "original_prompt": prompt_3d})
                st.session_state.enhanced_3d_prompt = ""
                st.success("✅ تم بناء العالم ثلاثي الأبعاد بنجاح!")
                st.rerun()
            except Exception as e:
                st.error(f"خطأ في محرك 3D: {str(e)}")
    st.markdown('</div>', unsafe_allow_html=True)
    for idx, item in enumerate(st.session_state.generated_3d):
        st.markdown('<div class="studio-card">', unsafe_allow_html=True)
        st.markdown(f"#### 🌐 المشهد #{len(st.session_state.generated_3d)-idx}: `{item['original_prompt']}`")
        st.components.v1.html(item["html"], height=520, scrolling=False)
        st.download_button("📥 تحميل العالم الكوني (HTML)", data=item["html"].encode("utf-8"), file_name=f"ATOM_3D_{idx+1}.html", mime="text/html", key=f"dl3d_{idx}", use_container_width=True)
        st.markdown('</div>', unsafe_allow_html=True)

with tab_image:
    st.markdown('<div class="studio-card">', unsafe_allow_html=True)
    st.markdown("### 🎨 استوديو الواقعية البصرية (Quantum Vision Studio)")
    col_img1, col_img2 = st.columns([2, 1])
    with col_img1:
        img_model = st.selectbox("اختر محرك الواقعية البصرية:", ["stabilityai/sdxl-turbo (سرعة فائقة - كرت Colab T4)", "black-forest-labs/FLUX.1-schnell (واقعية سينمائية فائقة 8K)"])
    with col_img2:
        exec_img = st.radio("وضع التشغيل البصري:", ["محلي (Colab GPU)", "سحابي سريع (HuggingFace API)"])
    img_prompt = st.text_area("اكتب فكرة الصورة باللغة العربية:", placeholder="مثال: رائد فضاء يرتدي بدلة فضائية متطورة تقف على سطح كوكب مجهول...", height=85)
    col_ib1, col_ib2 = st.columns(2)
    with col_ib1:
        if st.button("✨ هندسة الوصف البصري الكوني", key="btn_enh_img", use_container_width=True):
            if img_prompt.strip():
                with st.spinner("🧠 يقوم ATOM بصياغة العدسات وزوايا الإضاءة الحجمية..."):
                    st.session_state.enhanced_img_prompt = atom_cosmic_enhance(img_prompt, "image", active_model_id, current_user["ollama_url"])
    if st.session_state.enhanced_img_prompt:
        st.session_state.enhanced_img_prompt = st.text_area("📸 الوصف البصري المحسّن (قابل للتعديل):", value=st.session_state.enhanced_img_prompt, height=85)
    with col_ib2:
        gen_img_btn = st.button("🚀 تصيير اللوحة البصرية الفائقة الآن", type="primary", use_container_width=True)
    if gen_img_btn and img_prompt.strip():
        repo = img_model.split(" ")[0]
        with st.spinner(f"⏳ جاري تصيير البكسلات فائقة الواقعية عبر المحرك {repo}..."):
            try:
                final_p = st.session_state.enhanced_img_prompt.strip() or atom_cosmic_enhance(img_prompt, "image", active_model_id, current_user["ollama_url"])
                if exec_img == "سحابي سريع (HuggingFace API)":
                    api_url = f"https://api-inference.huggingface.co/models/{repo}"
                    headers = {"Authorization": f"Bearer {current_user['hf_token']}"} if current_user["hf_token"] else {}
                    resp = requests.post(api_url, headers=headers, json={"inputs": final_p}, timeout=120)
                    resp.raise_for_status()
                    img_bytes = resp.content
                else:
                    free_gpu_memory(current_user["ollama_url"], active_model_id)
                    from diffusers import AutoPipelineForText2Image
                    pipe = AutoPipelineForText2Image.from_pretrained(repo, torch_dtype=torch.float16, variant="fp16")
                    pipe.enable_model_cpu_offload()
                    steps = 4 if "turbo" in repo or "schnell" in repo else 25
                    image = pipe(final_p, num_inference_steps=steps, guidance_scale=0.0 if "turbo" in repo else 7.0).images[0]
                    buf = io.BytesIO()
                    image.save(buf, format="PNG")
                    img_bytes = buf.getvalue()
                    del pipe
                    free_gpu_memory(current_user["ollama_url"], active_model_id)
                st.session_state.generated_images.insert(0, {"bytes": img_bytes, "original_prompt": img_prompt, "model": repo})
                st.session_state.enhanced_img_prompt = ""
                st.success("✅ اكتمل التصيير البصري بنجاح!")
                st.rerun()
            except Exception as e:
                st.error(f"خطأ أثناء توليد الصورة: {str(e)}")
    st.markdown('</div>', unsafe_allow_html=True)
    if st.session_state.generated_images:
        cols = st.columns(2)
        for idx, item in enumerate(st.session_state.generated_images):
            with cols[idx % 2]:
                st.markdown('<div class="studio-card">', unsafe_allow_html=True)
                st.image(item["bytes"], use_container_width=True)
                st.markdown(f"**💡 الفكرة:** `{item['original_prompt']}`")
                st.download_button("📥 تحميل الصورة (PNG)", data=item["bytes"], file_name=f"ATOM_Vision_{idx+1}.png", mime="image/png", key=f"dlimg_{idx}", use_container_width=True)
                st.markdown('</div>', unsafe_allow_html=True)

with tab_video:
    st.markdown('<div class="studio-card">', unsafe_allow_html=True)
    st.markdown("### 🎬 استوديو الإخراج السينمائي (Quantum Cinema Studio)")
    col_v1, col_v2, col_v3 = st.columns(3)
    with col_v1: vid_engine = st.selectbox("اختر محرك الفيديو:", ["Wan-AI/Wan2.1-T2V-1.3B-Diffusers (مُحسّن لكرت Colab T4 16GB)"])
    with col_v2: vid_frames = st.select_slider("طول اللقطة (الإطارات):", options=[33, 49, 81], value=49)
    with col_v3: vid_fps = st.selectbox("معدل الإطارات (FPS):", [16, 24], index=1)
    vid_prompt = st.text_area("اكتب فكرة المشهد السينمائي بالعربية:", placeholder="مثال: مركبة فضائية عملاقة تخرج من بين السحب في كوكب المشتري...", height=85)
    col_vb1, col_vb2 = st.columns(2)
    with col_vb1:
        if st.button("✨ هندسة سيناريو الكاميرا الكوني", key="btn_enh_vid", use_container_width=True):
            if vid_prompt.strip():
                with st.spinner("🎬 يقوم ATOM بتحريك الكاميرا وضبط الفيزياء السينمائية..."):
                    st.session_state.enhanced_vid_prompt = atom_cosmic_enhance(vid_prompt, "video", active_model_id, current_user["ollama_url"])
    if st.session_state.enhanced_vid_prompt:
        st.session_state.enhanced_vid_prompt = st.text_area("🎥 السيناريو السينمائي المحسّن:", value=st.session_state.enhanced_vid_prompt, height=85)
    with col_vb2:
        gen_vid_btn = st.button("🎬 إنتاج وتصيير الفيديو السينمائي الآن", type="primary", use_container_width=True)
    if gen_vid_btn and vid_prompt.strip():
        repo = vid_engine.split(" ")[0]
        with st.spinner(f"⏳ جاري تصيير الفريمات السينمائية عبر المحرك {repo}..."):
            try:
                final_vp = st.session_state.enhanced_vid_prompt.strip() or atom_cosmic_enhance(vid_prompt, "video", active_model_id, current_user["ollama_url"])
                free_gpu_memory(current_user["ollama_url"], active_model_id)
                from diffusers import AutoencoderKLWan, WanPipeline
                from diffusers.utils import export_to_video
                vae = AutoencoderKLWan.from_pretrained(repo, subfolder="vae", torch_dtype=torch.float32)
                pipe = WanPipeline.from_pretrained(repo, vae=vae, torch_dtype=torch.float16)
                pipe.enable_model_cpu_offload()
                output_frames = pipe(prompt=final_vp, negative_prompt="blurry, low quality, distorted, watermark, static", height=480, width=832, num_frames=vid_frames, guidance_scale=5.0).frames[0]
                video_path = f"atom_cinema_{len(st.session_state.generated_videos) + 1}.mp4"
                export_to_video(output_frames, video_path, fps=vid_fps)
                with open(video_path, "rb") as vf: video_bytes = vf.read()
                del pipe, vae
                free_gpu_memory(current_user["ollama_url"], active_model_id)
                st.session_state.generated_videos.insert(0, {"bytes": video_bytes, "original_prompt": vid_prompt, "engine": repo})
                st.session_state.enhanced_vid_prompt = ""
                st.success("✅ اكتمل الإنتاج السينمائي بنجاح!")
                st.rerun()
            except Exception as e:
                st.error(f"⚠️ خطأ أثناء تصيير الفيديو: {str(e)}")
    st.markdown('</div>', unsafe_allow_html=True)
    for idx, v_item in enumerate(st.session_state.generated_videos):
        st.markdown('<div class="studio-card">', unsafe_allow_html=True)
        st.markdown(f"#### 🎬 اللقطة السينمائية #{len(st.session_state.generated_videos)-idx}: `{v_item['original_prompt']}`")
        st.video(v_item["bytes"])
        st.download_button("📥 تحميل الفيلم القصير (MP4)", data=v_item["bytes"], file_name=f"ATOM_Cinema_{idx+1}.mp4", mime="video/mp4", key=f"dlvid_{idx}", use_container_width=True)
        st.markdown('</div>', unsafe_allow_html=True)

with tab_settings:
    st.markdown("### ⚙️ مركز التحكم الكوني والشامل (Settings & AI Core)")
    st.markdown('<div class="studio-card">', unsafe_allow_html=True)
    new_th = st.radio("اختر الثيم البصري الكوني:", options=theme_options, index=current_th_idx)
    new_name = st.text_input("👤 اسم القائد الأعلى:", value=current_user["name"])
    new_temp = st.slider("🎛️ درجة الإبداع والاستدلال (Temperature):", 0.0, 1.5, float(current_user["temperature"]), 0.05)
    new_ollama = st.text_input("🔗 رابط سيرفر Ollama:", value=current_user["ollama_url"])
    if st.button("💾 حفظ المعاملات والإعدادات الكونية", type="primary"):
        update_user_settings(current_user["email"], {"theme_mode": new_th, "name": new_name, "temperature": new_temp, "ollama_url": new_ollama})
        st.success("✅ تم التحديث بنجاح!")
        st.rerun()
    st.markdown('</div>', unsafe_allow_html=True)