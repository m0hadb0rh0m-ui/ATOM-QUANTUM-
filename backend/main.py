"""ATOM AI X: stateless, rate-limited streaming inference gateway."""
import asyncio
import json
import os
import time
from collections import defaultdict, deque
from datetime import datetime, timezone
from typing import Literal

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field, model_validator

MODEL = os.getenv("ATOM_MODEL", "Qwen/Qwen3-Next-80B-A3B-Instruct")
ROUTER_URL = "https://router.huggingface.co/v1/chat/completions"
PER_MINUTE = max(1, int(os.getenv("ATOM_RPM", "10")))
DAILY_LIMIT = max(1, int(os.getenv("ATOM_DAILY_LIMIT", "100")))
CALLS = defaultdict(deque)
DAY = {"date": "", "count": 0}
LOCK = asyncio.Lock()
app = FastAPI(title="ATOM Intelligence Gateway", version="0.1.0")


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=6000)


class ChatRequest(BaseModel):
    messages: list[ChatMessage] = Field(min_length=1, max_length=24)
    mode: Literal["fast", "study", "coding", "research", "deep"] = "fast"

    @model_validator(mode="after")
    def validate_messages(self):
        if self.messages[-1].role != "user":
            raise ValueError("The final message must be from the user")
        if sum(len(item.content) for item in self.messages) > 30000:
            raise ValueError("Total context too large")
        return self


PROMPTS = {
    "fast": "You are ATOM AI X. Answer accurately, succinctly, in the user's language. Never claim web access.",
    "study": "You are ATOM AI X, a patient tutor. Explain in steps and check assumptions. Use the user's language.",
    "coding": "You are ATOM AI X, a careful software engineer. Provide testable code and mention limitations.",
    "research": "You are ATOM AI X. No live search is connected. Do not invent citations or claim browsing.",
    "deep": "You are ATOM AI X. Take extra care verifying logic. Do not reveal private reasoning traces."
}


def sse(kind: str, **data: str) -> str:
    return "data: " + json.dumps({"type": kind, **data}, ensure_ascii=False) + "\n\n"


async def reserve_quota(ip: str) -> None:
    now = time.monotonic()
    today = datetime.now(timezone.utc).date().isoformat()
    async with LOCK:
        if DAY["date"] != today:
            DAY.update(date=today, count=0)
        q = CALLS[ip]
        while q and now - q[0] > 60:
            q.popleft()
        if len(q) >= PER_MINUTE or DAY["count"] >= DAILY_LIMIT:
            raise HTTPException(status_code=429, detail="Rate limit reached")
        q.append(now)
        DAY["count"] += 1


async def inference_stream(payload: dict, token: str):
    """Produce only genuine output received from the remote model."""
    async with httpx.AsyncClient(timeout=httpx.Timeout(120.0, connect=15.0)) as client:
        async with client.stream(
            "POST", ROUTER_URL,
            headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"},
            json=payload,
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if not line.startswith("data: "):
                    continue
                raw = line[6:]
                if raw == "[DONE]":
                    break
                try:
                    chunk = json.loads(raw)
                    text = chunk.get("choices", [{}])[0].get("delta", {}).get("content")
                    if isinstance(text, str) and text:
                        yield text
                except (ValueError, KeyError, TypeError, IndexError):
                    continue


@app.get("/health")
async def health():
    return {"status": "configured" if os.getenv("HF_TOKEN") else "needs_hf_token", "model": MODEL}


@app.get("/v1/models")
async def models():
    return {"models": [{"id": MODEL, "configured": bool(os.getenv("HF_TOKEN"))}]}


@app.post("/v1/chat/stream")
async def chat(body: ChatRequest, request: Request):
    token = os.getenv("HF_TOKEN", "").strip()
    if not token:
        raise HTTPException(status_code=503, detail="Cloud inference is not configured")
    await reserve_quota(request.client.host if request.client else "unknown")
    upstream_payload = {
        "model": MODEL,
        "stream": True,
        "max_tokens": 640 if body.mode == "fast" else 1300,
        "temperature": 0.4 if body.mode in ("coding", "study") else 0.6,
        "messages": [{"role": "system", "content": PROMPTS[body.mode]}]
        + [message.model_dump() for message in body.messages],
    }

    async def events():
        sent = False
        try:
            async for part in inference_stream(upstream_payload, token):
                if await request.is_disconnected():
                    return
                sent = True
                yield sse("delta", text=part)
            if not sent:
                yield sse("error", message="The inference provider returned no text")
            else:
                yield sse("done")
        except httpx.HTTPStatusError as err:
            yield sse("error", message="Inference provider HTTP " + str(err.response.status_code))
        except (httpx.TimeoutException, httpx.TransportError):
            yield sse("error", message="Cloud inference is temporarily unreachable")
        except Exception:
            yield sse("error", message="Inference failed unexpectedly")

    return StreamingResponse(events(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})
