FROM python:3.11-slim

WORKDIR /app

# Create non-root user (required by Hugging Face Spaces)
RUN useradd -m -u 1000 user

COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

COPY fastapi_server.py .

RUN chown -R user:user /app
USER user

ENV HOME=/home/user \
    PATH=/home/user/.local/bin:$PATH \
    HF_HOME=/home/user/.cache/huggingface \
    PORT=7860

EXPOSE 7860

CMD ["uvicorn", "fastapi_server:app", "--host", "0.0.0.0", "--port", "7860"]
