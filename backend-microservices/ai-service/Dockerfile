FROM python:3.11-slim

# libgl/libglib are required by opencv-python-headless' video decoding path.
RUN apt-get update \
 && apt-get install --no-install-recommends -y libglib2.0-0 libgl1 \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app \
 && useradd --system --gid app --home-dir /home/app --create-home app

WORKDIR /app
ENV HF_HOME=/home/app/.cache/huggingface \
    PYTHONUNBUFFERED=1

COPY requirements.txt .
RUN pip install --no-cache-dir --default-timeout=1000 -r requirements.txt

COPY main.py .
RUN chown -R app:app /app /home/app

USER app
VOLUME ["/home/app/.cache/huggingface"]
EXPOSE 5000

# Models are downloaded on first start, so allow a long warm-up before the container is healthy.
HEALTHCHECK --interval=30s --timeout=10s --start-period=300s --retries=20 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:5000/health').status==200 else 1)"

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "5000"]
