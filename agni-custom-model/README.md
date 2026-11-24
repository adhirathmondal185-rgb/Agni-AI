# AGNI AI — Custom Fine-Tuned Model & FastAPI Backend Guide (Phone-Friendly)

এই ফোল্ডারে আপনার **নিজস্ব AGNI AI মডেল Fine-Tune (QLoRA)** করা এবং **FastAPI ব্যাকএন্ড**-এর মাধ্যমে আপনার Android **Agni AI** অ্যাপে চালানোর সম্পূর্ণ কোড রয়েছে।

---

## ফাইল তালিকা
1. `agni_dataset.jsonl` — বাংলা ও ইংরেজি প্রশ্নোত্তর ডেটাসেট (আপনি আরও শত শত প্রশ্নোত্তর যোগ করতে পারবেন)।
2. `train_agni_lora.py` — Google Colab-এর ফ্রি **T4 GPU**-তে নিজের **AGNI Fine-Tuned LoRA** মডেল ট্রেন করার কোড।
3. `fastapi_server.py` — আপনার ফাইন-টিউনড মডেলকে `/v1/chat/completions` API হিসেবে চালানোর **FastAPI** সার্ভার।

---

## ধাপে ধাপে নির্দেশিকা (ফোনের ব্রাউজার থেকেই করা যাবে)

### ধাপ ১: Google Colab চালু করুন
1. ফোনের ব্রাউজারে [colab.research.google.com](https://colab.research.google.com) খুলুন এবং নতুন Notebook তৈরি করুন।
2. ওপরের মেনু থেকে **Runtime → Change runtime type → T4 GPU** সিলেক্ট করে Save করুন।

### ধাপ ২: এই কোডগুলো Colab-এ আনুন ও প্যাকেজ ইনস্টল করুন
Colab-এর প্রথম সেলে নিচের কমান্ডটি চালান:
```bash
!git clone https://github.com/adhirathmondal185-rgb/Agni-AI.git
%cd Agni-AI/agni-custom-model
!pip install -q -U transformers peft trl bitsandbytes datasets accelerate fastapi uvicorn
```

### ধাপ ৩: নিজের AGNI AI মডেল ট্রেন করুন
দ্বিতীয় সেলে চালান:
```bash
!python train_agni_lora.py
```
এটি শেষ হলে `./agni-lora-adapter` ফোল্ডারে আপনার **AGNI Fine-Tuned Model** তৈরি হয়ে যাবে!

### ধাপ ৪: FastAPI সার্ভার ও ফ্রি HTTPS লিংক চালু করুন
তৃতীয় সেলে চালান:
```bash
import os, subprocess, threading, time

os.environ["AGNI_API_KEY"] = "my-agni-secret-123"  # আপনার নিজের পাসওয়ার্ড দিন

subprocess.Popen(["uvicorn", "fastapi_server:app", "--host", "0.0.0.0", "--port", "8000"])
time.sleep(3)

!wget -q https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
!chmod +x cloudflared-linux-amd64
!./cloudflared-linux-amd64 tunnel --url http://localhost:8000
```
কয়েক সেকেন্ড পর স্ক্রিনে এরকম একটি **HTTPS লিংক** দেখাবে:
```
https://something-random.trycloudflare.com
```

### ধাপ ৫: আপনার Agni AI Android অ্যাপে যুক্ত করুন
1. ফোনে **Agni AI** অ্যাপ খুলুন → বাম মেনু (**☰**) → **Settings**।
2. **Custom AGNI Model (FastAPI / Open-Source)** সেকশনে যান:
   - **Display Name**: `Agni AI v1 (My Model)`
   - **HTTPS Endpoint URL**: `https://something-random.trycloudflare.com/v1/chat/completions`
   - **Model ID**: `agni-ai-v1`
   - **API Key**: `my-agni-secret-123` (যেটি ধাপ ৪-এ দিয়েছিলেন)
3. **Test Connection** চাপুন → সফল হলে **Save & Activate** চাপুন!
4. এবার চ্যাটে প্রশ্ন করলে সরাসরি আপনার নিজের **AGNI Fine-Tuned Model** উত্তর দেবে!
