"""
AGNI AI — Permanent FastAPI Inference Backend (GPU + Free CPU Compatible)
=========================================================================
Works both on:
  1. Hugging Face Spaces (Free CPU Basic, 16 GB RAM, Port 7860) — Permanent HTTPS URL
  2. Google Colab / Cloud GPU (CUDA T4/A10G, 4-bit QLoRA)

Environment Variables (never hardcode secrets):
  - AGNI_BASE_MODEL  : Base open-source model (default: Qwen/Qwen2.5-1.5B-Instruct)
  - AGNI_LORA_PATH   : Local LoRA folder (default: ./agni-lora-adapter)
  - AGNI_LORA_REPO   : Optional Hugging Face Hub LoRA repo ID (e.g. username/agni-ai-v1-lora)
  - AGNI_API_KEY     : Optional Bearer token to protect /v1/chat/completions
  - HF_TOKEN         : Optional Hugging Face token if your LoRA repo is private
"""

import hmac
import os
import time
from typing import List, Optional

import torch
from fastapi import Depends, FastAPI, Header, HTTPException
from peft import PeftModel
from pydantic import BaseModel, Field
from transformers import AutoModelForCausalLM, AutoTokenizer

BASE_MODEL_ID = os.getenv("AGNI_BASE_MODEL", "Qwen/Qwen2.5-1.5B-Instruct").strip()
LORA_ADAPTER_PATH = os.getenv("AGNI_LORA_PATH", "./agni-lora-adapter").strip()
LORA_ADAPTER_REPO = os.getenv("AGNI_LORA_REPO", "").strip()
EXPECTED_API_KEY = os.getenv("AGNI_API_KEY", "").strip()
HF_TOKEN = os.getenv("HF_TOKEN", "").strip() or None

app = FastAPI(title="AGNI AI FastAPI Backend", version="1.1.0")

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
    max_tokens: int = Field(default=384, ge=16, le=2048)
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

    use_cuda = torch.cuda.is_available()
    tokenizer = AutoTokenizer.from_pretrained(
        BASE_MODEL_ID,
        trust_remote_code=False,
        token=HF_TOKEN,
    )

    if use_cuda:
        from transformers import BitsAndBytesConfig

        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.float16,
        )
        base_model = AutoModelForCausalLM.from_pretrained(
            BASE_MODEL_ID,
            quantization_config=bnb_config,
            device_map="auto",
            trust_remote_code=False,
            token=HF_TOKEN,
        )
    else:
        # CPU-friendly loading for free Hugging Face Spaces (16 GB RAM)
        base_model = AutoModelForCausalLM.from_pretrained(
            BASE_MODEL_ID,
            torch_dtype=torch.float32,
            low_cpu_mem_usage=True,
            trust_remote_code=False,
            token=HF_TOKEN,
        )

    adapter_source = None
    if LORA_ADAPTER_REPO:
        adapter_source = LORA_ADAPTER_REPO
    elif os.path.isdir(LORA_ADAPTER_PATH):
        adapter_source = LORA_ADAPTER_PATH

    if adapter_source:
        peft_model = PeftModel.from_pretrained(
            base_model,
            adapter_source,
            token=HF_TOKEN,
        )
        # Merge LoRA weights on CPU for faster inference
        model = peft_model.merge_and_unload() if not use_cuda else peft_model
        _loaded_model_label = f"agni-ai-v1 ({adapter_source})"
    else:
        model = base_model
        _loaded_model_label = BASE_MODEL_ID

    model.eval()
    _tokenizer = tokenizer
    _model = model
    return _tokenizer, _model


@app.get("/")
def root():
    return {
        "service": "AGNI AI FastAPI Backend",
        "status": "online",
        "device": "cuda" if torch.cuda.is_available() else "cpu",
        "endpoint": "/v1/chat/completions",
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "model": _loaded_model_label,
        "device": "cuda" if torch.cuda.is_available() else "cpu",
    }


@app.post("/v1/chat/completions", dependencies=[Depends(verify_bearer_token)])
def create_chat_completion(req: ChatCompletionRequest):
    tokenizer, model = load_agni_model()

    conversation = [{"role": m.role, "content": m.content} for m in req.messages]
    prompt_text = tokenizer.apply_chat_template(
        conversation,
        tokenize=False,
        add_generation_prompt=True,
    )
    device = next(model.parameters()).device
    inputs = tokenizer(prompt_text, return_tensors="pt").to(device)

    max_new = min(req.max_tokens, 256 if not torch.cuda.is_available() else req.max_tokens)

    with torch.no_grad():
        output_ids = model.generate(
            **inputs,
            max_new_tokens=max_new,
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
