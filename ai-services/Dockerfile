FROM python:3.10-slim
WORKDIR /app
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources /etc/apt/sources.list 2>/dev/null || true
RUN apt-get update && apt-get install -y tesseract-ocr tesseract-ocr-chi-sim && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && pip install --no-cache-dir -i https://mirrors.aliyun.com/pypi/simple/ -r requirements.txt
COPY . .
RUN mkdir -p logs
EXPOSE 8081
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8081", "--workers", "1"]
