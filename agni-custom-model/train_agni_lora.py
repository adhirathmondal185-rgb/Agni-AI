"""
AGNI AI — Free Google Colab (T4 GPU) QLoRA Fine-Tuning Script
=============================================================
Run this in Google Colab (Runtime -> Change runtime type -> T4 GPU):

    !pip install -q -U transformers peft trl bitsandbytes datasets accelerate

Then run:
    !python train_agni_lora.py
"""

import os
import torch
from datasets import load_dataset
from peft import LoraConfig
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments,
)
from trl import SFTTrainer

# Multilingual open-source base model that fits comfortably on a free Colab T4 (15 GB VRAM)
BASE_MODEL_ID = os.getenv("AGNI_BASE_MODEL", "Qwen/Qwen2.5-1.5B-Instruct")
DATASET_PATH = os.getenv("AGNI_DATASET_PATH", "agni_dataset.jsonl")
OUTPUT_DIR = os.getenv("AGNI_OUTPUT_DIR", "./agni-lora-adapter")
HF_PUSH_REPO = os.getenv("AGNI_HF_REPO", "")  # Optional: e.g. "your-username/agni-ai-v1-lora"


def main() -> None:
    print(f"[AGNI Train] Loading base model: {BASE_MODEL_ID}")

    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.float16,
        bnb_4bit_use_double_quant=True,
    )

    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL_ID, trust_remote_code=False)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    model = AutoModelForCausalLM.from_pretrained(
        BASE_MODEL_ID,
        quantization_config=bnb_config,
        device_map="auto",
        trust_remote_code=False,
    )
    model.config.use_cache = False

    dataset = load_dataset("json", data_files=DATASET_PATH, split="train")
    print(f"[AGNI Train] Loaded {len(dataset)} training conversations from {DATASET_PATH}")

    def format_chat(example):
        text = tokenizer.apply_chat_template(
            example["messages"],
            tokenize=False,
            add_generation_prompt=False,
        )
        return {"text": text}

    formatted_dataset = dataset.map(format_chat)

    lora_config = LoraConfig(
        r=16,
        lora_alpha=32,
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    )

    training_args = TrainingArguments(
        output_dir=OUTPUT_DIR,
        num_train_epochs=5,
        per_device_train_batch_size=2,
        gradient_accumulation_steps=4,
        learning_rate=2e-4,
        fp16=True,
        logging_steps=1,
        save_strategy="epoch",
        optim="paged_adamw_8bit",
        report_to="none",
    )

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=formatted_dataset,
        dataset_text_field="text",
        peft_config=lora_config,
        max_seq_length=1024,
        args=training_args,
    )

    print("[AGNI Train] Starting QLoRA fine-tuning...")
    trainer.train()

    print(f"[AGNI Train] Saving AGNI LoRA adapter to {OUTPUT_DIR}")
    trainer.model.save_pretrained(OUTPUT_DIR)
    tokenizer.save_pretrained(OUTPUT_DIR)

    if HF_PUSH_REPO:
        hf_token = os.getenv("HF_TOKEN")
        print(f"[AGNI Train] Pushing adapter to Hugging Face Hub: {HF_PUSH_REPO}")
        trainer.model.push_to_hub(HF_PUSH_REPO, token=hf_token)
        tokenizer.push_to_hub(HF_PUSH_REPO, token=hf_token)

    print("[AGNI Train] Done! You can now serve this adapter with fastapi_server.py.")


if __name__ == "__main__":
    main()
