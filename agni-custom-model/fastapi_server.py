"""
AGNI AI — FastAPI Custom Model Inference Backend
================================================
Matches the AGNI AI architecture pipeline:
  FastAPI Backend -> Request Validation -> Model Router ->
  (Open Source AI / AGNI Fine-Tuned LoRA) -> Response Processing -> Safety Checks

Install dependencies (Colab or GPU VM):
    pip install -q fastapi uvicorn pydantic transformers peft bitsandbytes accelerate torch

Run server:
    export AGNI_API_KEY="choose-your-own-secret-token"
    uvicorn fastapi_server:app --host 0.0.0.0 --port 8000

Expose free HTTPS URL from Google Colab (no account needed):
    !wget -q https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
    !chmod +x cloudflared-linux-amd64
    !./cloudflared-linux-amd64 tunnel --url http://localhost:8000
"""

import hmac
import os
import time
from typing import List, Optional

import torch
from fastapi import Depends, FastAPI, Header, HTTPException
from peft import PeftModel
from pydantic import BaseModel, Field
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig

BASE_MODEL_ID = os.getenv("AGNI_BASE_MODEL", "Qwen/Qwen2.5-1.5B-Instruct")
LORA_ADAPTER_PATH = os.getenv("AGNI_LORA_PATH", "./agni-lora-adapter")
EXPECTED_API_KEY = os.getenv("AGNI_API_KEY", "").strip()

app = FastAPI(title="AGNI AI FastAPI Backend", version="1.0.0")

_tokenizer = None
_model = None
_loaded_model_label = "agni-ai-v1"


class ChatTurn(BaseModel):
    role: str = Field(..., pattern="^(system|user|assistant)$")
    content: str = Field(..., min_length=1, max_length=12000)


class ChatCompletionRequest(BaseModel):
    model: str = Field(default="agni-ai-v1", max_length=128)
    messages: List[ChatTurn] = Field(..., min_length=1, max_length=32)
    temperature: float = Field(default=0.7, ge=0.0, le=1.5)
    max_tokens: int = Field(default=512, ge=16, le=2048)
    stream: bool = False


def verify_bearer_token(authorization: Optional[str] = Header(default=None)) -> None:
    if not EXPECTED_API_KEY:
        return
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing Bearer token.")
    provided = authorization[len("Bearer "):].strip()
    if not hmac.compare_digest(provided, EXPECTED_API_KEY):
        raise HTTPException(status_code=403, detail="Invalid AGNI API token.")


def load_agni_model():
    global _tokenizer, _model, _loaded_model_label
    if _model is not None:
        return _tokenizer, _model

    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.float16,
    )
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL_ID, trust_remote_code=False)
    base_model = AutoModelForCausalLM.from_pretrained(
        BASE_MODEL_ID,
        quantization_config=bnb_config,
        device_map="auto",
        trust_remote_code=False,
    )

    if os.path.isdir(LORA_ADAPTER_PATH):
        model = PeftModel.from_pretrained(base_model, LORA_ADAPTER_PATH)
        _loaded_model_label = "agni-ai-v1-finetuned"
    else:
        model = base_model
        _loaded_model_label = BASE_MODEL_ID

    model.eval()
    _tokenizer = tokenizer
    _model = model
    return _tokenizer, _model


@app.get("/health")
def health():
    return {"status": "ok", "model": _loaded_model_label}


@app.post("/v1/chat/completions", dependencies=[Depends(verify_bearer_token)])
def create_chat_completion(req: ChatCompletionRequest):
    tokenizer, model = load_agni_model()

    conversation = [{"role": m.role, "content": m.content} for m in req.messages]
    prompt_text = tokenizer.apply_chat_template(
        conversation,
        tokenize=False,
        add_generation_prompt=True,
    )
    inputs = tokenizer(prompt_text, return_tensors="pt").to(model.device)

    with torch.no_grad():
        output_ids = model.generate(
            **inputs,
            max_new_tokens=req.max_tokens,
            temperature=max(req.temperature, 0.05),
            do_sample=req.temperature > 0.0,
            pad_token_id=tokenizer.eos_token_id,
        )

    generated_ids = output_ids[0][inputs["input_ids"].shape[1]:]
    reply_text = tokenizer.decode(generated_ids, skip_special_tokens=True).strip()

    if not reply_text:
        reply_text = "দুঃখিত, আমি এই মুহূর্তে উত্তর তৈরি করতে পারিনি। অনুগ্রহ করে আবার চেষ্টা করুন।"

    return {
        "id": f"agni-{int(time.time())}",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": req.model or _loaded_model_label,
        "choices": [
            {
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": reply_text,
                },
                "finish_reason": "stop",
            }
        ],
    }
