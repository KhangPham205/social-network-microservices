# Dùng Python nhẹ
FROM python:3.9-slim

WORKDIR /app

# Cài đặt các thư viện cần thiết
COPY requirements.txt .
# Thêm --no-cache-dir để giảm dung lượng image
RUN pip install --no-cache-dir --default-timeout=1000 -r requirements.txt

# Copy code vào container
COPY main.py .

# Mở cổng 5000
EXPOSE 5000

# Chạy app
CMD ["python", "main.py"]