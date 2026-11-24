"""
AGNI AI — Free Google Colab (T4 GPU) QLoRA Fine-Tuning Script
=============================================================
Uses standard transformers.Trainer + peft (QLoRA 4-bit) so it works reliably
across all Colab transformers/peft versions without TRL API breaks.
"""

import os
import torch
from datasets import load_dataset
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    DataCollatorForLanguageModeling,
    Trainer,
    TrainingArguments,
)

BASE_MODEL_ID = os.getenv("AGNI_BASE_MODEL", "Qwen/Qwen2.5-1.5B-Instruct")
DATASET_PATH = os.getenv("AGNI_DATASET_PATH", "agni_dataset.jsonl")
OUTPUT_DIR = os.getenv("AGNI_OUTPUT_DIR", "./agni-lora-adapter")
HF_PUSH_REPO = os.getenv("AGNI_HF_REPO", "")


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
    model = prepare_model_for_kbit_training(model)

    lora_config = LoraConfig(
        r=16,
        lora_alpha=32,
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    dataset = load_dataset("json", data_files=DATASET_PATH, split="train")
    print(f"[AGNI Train] Loaded {len(dataset)} training conversations from {DATASET_PATH}")

    def tokenize_chat(example):
        text = tokenizer.apply_chat_template(
            example["messages"],
            tokenize=False,
            add_generation_prompt=False,
        )
        encoded = tokenizer(
            text,
            truncation=True,
            max_length=512,
            padding=False,
        )
        encoded["labels"] = encoded["input_ids"].copy()
        return encoded

    tokenized_dataset = dataset.map(tokenize_chat, remove_columns=dataset.column_names)

    training_args = TrainingArguments(
        output_dir=OUTPUT_DIR,
        num_train_epochs=5,
        per_device_train_batch_size=1,
        gradient_accumulation_steps=4,
        learning_rate=2e-4,
        fp16=True,
        logging_steps=1,
        save_strategy="no",
        optim="paged_adamw_8bit",
        report_to="none",
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=tokenized_dataset,
        data_collator=DataCollatorForLanguageModeling(tokenizer=tokenizer, mlm=False),
    )

    print("[AGNI Train] Starting QLoRA fine-tuning...")
    trainer.train()

    print(f"[AGNI Train] Saving AGNI LoRA adapter to {OUTPUT_DIR}")
    model.save_pretrained(OUTPUT_DIR)
    tokenizer.save_pretrained(OUTPUT_DIR)

    if HF_PUSH_REPO:
        hf_token = os.getenv("HF_TOKEN")
        print(f"[AGNI Train] Pushing adapter to Hugging Face Hub: {HF_PUSH_REPO}")
        model.push_to_hub(HF_PUSH_REPO, token=hf_token)
        tokenizer.push_to_hub(HF_PUSH_REPO, token=hf_token)

    print("[AGNI Train] Done! You can now serve this adapter with fastapi_server.py.")


if __name__ == "__main__":
    main()
