# AGNI AI — Permanent 24/7 Hosting & Fine-Tuning Guide (Phone-Friendly)

এই ফোল্ডারে আপনার **নিজস্ব AGNI AI মডেল Fine-Tune (QLoRA)** করা এবং **স্থায়ীভাবে (Colab ছাড়াই)** বিনামূল্যে **Hugging Face Spaces**-এ হোস্ট করার সম্পূর্ণ ফাইল রয়েছে।

---

## স্থায়ীভাবে চালু করার ৪টি সহজ ধাপ (ফ্রি, কোনো কার্ড লাগবে না)

### ধাপ ১: Hugging Face অ্যাকাউন্ট ও Access Token তৈরি করুন
1. ফোনের ব্রাউজারে [huggingface.co](https://huggingface.co/join) গিয়ে ফ্রি অ্যাকাউন্ট খুলুন।
2. [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens) → **+ Create new token** → **Write** সিলেক্ট করুন → টোকেনটি কপি করে রাখুন।

### ধাপ ২: আপনার ট্রেন করা AGNI মডেলটি Hugging Face-এ স্থায়ীভাবে সেভ করুন
Google Colab-এ (যেখানে আপনার `./agni-lora-adapter` তৈরি হয়েছে) নতুন `+ Code` সেলে চালান:
```python
from huggingface_hub import HfApi

HF_TOKEN = "hf_আপনার_টোকেন_এখানে"
REPO_ID = "আপনার-username/agni-ai-v1-lora"

api = HfApi(token=HF_TOKEN)
api.create_repo(repo_id=REPO_ID, exist_ok=True)
api.upload_folder(
    folder_path="/content/Agni-AI/agni-custom-model/agni-lora-adapter",
    repo_id=REPO_ID,
)
print("Saved permanently to:", REPO_ID)
```

### ধাপ ৩: ফ্রি স্থায়ী FastAPI সার্ভার চালু করুন (Hugging Face Spaces)
1. যান: [huggingface.co/new-space](https://huggingface.co/new-space)
2. নাম দিন: `agni-ai-api`
3. **Space SDK**: **Docker** → **Blank**
4. **Hardware**: **CPU basic · 2 vCPU · 16 GB RAM (Free)** → **Create Space**।
5. নতুন Space-এর **Files** ট্যাবে গিয়ে এই ৩টি ফাইল আপলোড করুন (আপনার GitHub-এর `agni-custom-model` ফোল্ডার থেকে):
   - `Dockerfile`
   - `requirements.txt`
   - `fastapi_server.py`
6. Space-এর **Settings → Variables and secrets** সেকশনে যোগ করুন:
   - **Variable** `AGNI_LORA_REPO` = `আপনার-username/agni-ai-v1-lora`
   - **Secret** `AGNI_API_KEY` = `agni-secret-123` (আপনার পছন্দমতো গোপন পাসওয়ার্ড)

### ধাপ ৪: স্থায়ী HTTPS লিংকটি Agni AI অ্যাপে বসান
২–৩ মিনিটে Space-টি **Running** হয়ে যাবে এবং আপনি একটি **স্থায়ী লিংক** পাবেন:
```
https://আপনার-username-agni-ai-api.hf.space
```
এবার **Agni AI অ্যাপ → Settings → Custom AGNI Model**-এ এই স্থায়ী লিংকটি বসিয়ে **Save & Use** চাপুন — আর কখনো Colab খোলা রাখতে হবে না!
