import asyncio
from fastapi.testclient import TestClient
import main


def test_health_without_token(monkeypatch):
    monkeypatch.delenv("HF_TOKEN", raising=False)
    r = TestClient(main.app).get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "needs_hf_token"


def test_chat_requires_configuration(monkeypatch):
    monkeypatch.delenv("HF_TOKEN", raising=False)
    r = TestClient(main.app).post("/v1/chat/stream", json={
        "messages": [{"role": "user", "content": "Hello"}]
    })
    assert r.status_code == 503


def test_input_validation(monkeypatch):
    monkeypatch.setenv("HF_TOKEN", "test-only-placeholder")
    r = TestClient(main.app).post("/v1/chat/stream", json={
        "messages": [{"role": "assistant", "content": "Hello"}]
    })
    assert r.status_code == 422


def test_stream_protocol_without_network(monkeypatch):
    monkeypatch.setenv("HF_TOKEN", "test-only-placeholder")
    async def simulated_provider(payload, token):
        # This mock exists ONLY in tests; never in production responses.
        yield "test"
        yield " text"
    monkeypatch.setattr(main, "inference_stream", simulated_provider)
    main.CALLS.clear()
    main.DAY.update(date="", count=0)
    r = TestClient(main.app).post("/v1/chat/stream", json={
        "messages": [{"role": "user", "content": "Hello"}]
    })
    assert r.status_code == 200
    assert '"type": "delta"' in r.text
    assert '"type": "done"' in r.text


def test_quota(monkeypatch):
    main.CALLS.clear()
    main.DAY.update(date="", count=0)
    monkeypatch.setattr(main, "PER_MINUTE", 1)
    asyncio.run(main.reserve_quota("test-quota-ip"))
    try:
        asyncio.run(main.reserve_quota("test-quota-ip"))
        assert False, "Must reject second request"
    except Exception as err:
        assert getattr(err, "status_code", None) == 429
