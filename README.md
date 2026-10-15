# IT209 - Session 12 - Bài tập 4: Tự động triển khai ứng dụng Spring Boot (Container) lên VPS từ xa qua SSH

- **Sinh viên:** Phạm Thanh Phong
- **MSSV:** N24DTCN120
- **Lớp:** IT209
- **Học phần:** IT209
- **Đường dẫn nộp bài:** `homework/session_12/ex4/`
- **Trạng thái:** Hoàn thành (100%)

---

## 1. Danh mục tệp tin trong thư mục bài tập

| Tên tệp tin | Mô tả chức năng |
| :--- | :--- |
| [.github/workflows/deploy-vps.yml](./.github/workflows/deploy-vps.yml) | **Tệp kịch bản GitHub Actions CD chính thức:** Đóng gói Docker Image đẩy lên GHCR và tự động kết nối SSH triển khai lại container trên VPS từ xa |
| [screenshots/github-actions-ssh-deploy-log.png](./screenshots/github-actions-ssh-deploy-log.png) | **Ảnh minh chứng nộp bài:** Giao diện tab GitHub Actions hiển thị nhật ký Terminal thực thi lệnh SSH Action thành công (Tích xanh) |
| [IT209_Session12_BT4_PhamThanhPhong_N24DTCN120.md](./IT209_Session12_BT4_PhamThanhPhong_N24DTCN120.md) | Báo cáo kỹ thuật chi tiết, phân tích kiến trúc CD qua SSH, bảo mật Repository Secrets và nhật ký log runner |
| [Dockerfile](./Dockerfile) | Tệp cấu hình Multi-stage Dockerfile đóng gói ứng dụng Spring Boot kèm Spring Boot Actuator |
| [.dockerignore](./.dockerignore) | Tệp cấu hình loại trừ các thư mục tạm khỏi Docker build context |
| [build.gradle](./build.gradle) & [settings.gradle](./settings.gradle) | Cấu hình dự án Spring Boot 3.2.5 tích hợp Spring Boot Actuator |
| [gradlew](./gradlew) & [gradlew.bat](./gradlew.bat) | Bộ script thực thi Gradle Wrapper trên Linux và Windows |
| [src/](./src/) | Toàn bộ mã nguồn ứng dụng REST API và bộ kiểm thử JUnit 5 |

---

## 2. Hướng dẫn khai báo Repository Secrets trên GitHub

Để Action có thể xác thực và kết nối SSH an toàn tới máy chủ VPS:
1. Mở Repository trên GitHub $\rightarrow$ **Settings** $\rightarrow$ **Secrets and variables** $\rightarrow$ **Actions**.
2. Chọn **New repository secret** và thêm lần lượt 3 biến:
   - `SSH_HOST`: Địa chỉ IP công khai của máy chủ VPS (ví dụ: `123.45.67.89`).
   - `SSH_USER`: Tên tài khoản đăng nhập máy chủ (ví dụ: `ubuntu` hoặc `deployer`).
   - `SSH_KEY`: Nội dung khóa riêng tư SSH Private Key (`id_rsa` hoặc `id_ed25519`) dùng để xác thực không cần mật khẩu.

---

## 3. Nội dung tệp cấu hình `.github/workflows/deploy-vps.yml`

```yaml
name: Build and Deploy to Remote VPS via SSH

on:
  push:
    branches:
      - main

jobs:
  build-image:
    name: Build & Push Docker Image
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout mã nguồn
        uses: actions/checkout@v5

      - name: Đăng nhập vào GitHub Container Registry (GHCR)
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Trích xuất metadata Docker
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository }}
          tags: |
            type=raw,value=latest
            type=sha,prefix=

      - name: Đóng gói và đẩy Docker Image lên GHCR
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}

  deploy-vps:
    name: Deploy to Remote VPS via SSH
    runs-on: ubuntu-latest
    needs: build-image

    steps:
      - name: Thực thi câu lệnh triển khai trên VPS qua SSH
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.SSH_HOST }}
          username: ${{ secrets.SSH_USER }}
          key: ${{ secrets.SSH_KEY }}
          script: |
            echo "==> Đăng nhập vào GHCR trên máy chủ VPS..."
            echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
            
            echo "==> Kéo Docker Image mới nhất từ GHCR..."
            docker pull ghcr.io/${{ github.repository }}:latest
            
            echo "==> Dừng và gỡ bỏ container cũ nếu đang chạy..."
            docker stop app || true
            docker rm app || true
            
            echo "==> Khởi chạy container ứng dụng mới..."
            docker run -d \
              --name app \
              -p 8080:8080 \
              --restart unless-stopped \
              ghcr.io/${{ github.repository }}:latest
            
            echo "==> Xác minh trạng thái container..."
            docker ps --filter "name=app"
```

---

## 4. Ảnh minh chứng kết quả triển khai qua SSH trên GitHub Actions

Ảnh chụp màn hình tab **Actions** xác nhận: Sau khi Job `build-image` hoàn tất, Job `deploy-vps` tự động kết nối SSH vào máy chủ VPS từ xa, thực hiện kéo image mới và khởi chạy lại container đạt trạng thái **Tích xanh (Success)**:

![Log kết quả SSH Deploy](./screenshots/github-actions-ssh-deploy-log.png)

---

## 5. Hướng dẫn kiểm tra và xác nhận ứng dụng hoạt động

Sau khi đẩy mã nguồn lên nhánh `main`, theo dõi log thực thi và kiểm tra ứng dụng:
1. Mở trình duyệt web hoặc terminal kiểm tra endpoint sức khỏe (Health Check):
   ```bash
   curl -i http://<VPS_IP>:8080/actuator/health
   ```
2. Kết quả nhận được phản hồi chuẩn `HTTP 200 OK`:
   ```json
   {
     "status": "UP"
   }
   ```
3. Truy cập endpoint nghiệp vụ:
   ```bash
   curl -i http://<VPS_IP>:8080/
   ```
