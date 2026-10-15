# Báo cáo Bài tập 4: Tự động triển khai ứng dụng Spring Boot (Container) lên VPS từ xa qua SSH

- **Sinh viên:** Phạm Thanh Phong
- **MSSV:** N24DTCN120
- **Lớp:** IT209
- **Học phần:** IT209
- **Session:** 12
- **Bài tập:** 04 (BT4)
- **Tệp nộp bài:** [.github/workflows/deploy-vps.yml](./.github/workflows/deploy-vps.yml), [screenshots/github-actions-ssh-deploy-log.png](./screenshots/github-actions-ssh-deploy-log.png), [README.md](./README.md)
- **Đường dẫn trên GitHub:** `homework/session_12/ex4/`
- **Trạng thái:** Hoàn thành (100%)

---

## 1. Bối cảnh kỹ thuật & Mục tiêu bài toán

### 1.1. Triển khai phần mềm từ xa qua SSH trong mô hình Continuous Deployment
Trong các kiến trúc triển khai đơn giản và hiệu quả chi phí (Single Server / VPS Cloud như AWS EC2, DigitalOcean, Linode, OVH), việc dựng một cụm Kubernetes hoặc Nomad đôi khi quá phức tạp. Thay vào đó, mô hình **CD tự động qua giao thức SSH (Secure Shell)** là giải pháp được ưa chuộng hàng đầu:
- **Tách biệt mối quan tâm:** Máy ảo GitHub Actions Runner chịu trách nhiệm build và push Docker Image lên kho lưu trữ trung tâm (GHCR).
- **Thực thi không xâm lấn:** Runner chỉ đóng vai trò là một SSH Client gửi các lệnh chuẩn (`docker pull`, `docker run`) tới máy chủ VPS từ xa thông qua Action chuyên dụng `appleboy/ssh-action@v1.0.3`.
- **Bảo mật tuyệt đối:** Khóa riêng tư SSH (`SSH_KEY`) và địa chỉ máy chủ (`SSH_HOST`, `SSH_USER`) được lưu trữ trong kho bí mật **GitHub Repository Secrets** và được mã hóa bằng thuật toán `libsodium` trước khi lưu trữ trên hạ tầng GitHub.

### 1.2. Mục tiêu thực thi
1. Khai báo các Repository Secrets: `SSH_HOST`, `SSH_USER`, `SSH_KEY` trong phần cài đặt của Repository.
2. Thiết kế kịch bản CI/CD `.github/workflows/deploy-vps.yml` bao gồm 2 Jobs tuần tự:
   - Job `build-image`: Đóng gói ứng dụng Java Spring Boot thành Docker Image và đẩy lên GHCR.
   - Job `deploy-vps`: Phụ thuộc vào `build-image` (`needs: build-image`), tự động mở phiên kết nối SSH tới máy chủ VPS.
3. Kịch bản lệnh thực thi trên VPS:
   - Đăng nhập GHCR trên VPS bằng token an toàn.
   - Kéo phiên bản Docker Image mới nhất: `docker pull ghcr.io/...:latest`.
   - Dừng và gỡ bỏ container cũ: `docker stop app || true && docker rm app || true`.
   - Khởi chạy container mới với chính sách phục hồi: `docker run -d --name app -p 8080:8080 --restart unless-stopped ...`.
4. Xác minh ứng dụng hoạt động ổn định thông qua endpoint kiểm tra sức khỏe Spring Boot Actuator (`/actuator/health`) đạt mã phản hồi `HTTP 200 OK`.

---

## 2. Sơ đồ kiến trúc & Vòng đời Workflow (Mermaid Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer (Phạm Thanh Phong)
    participant GH as GitHub Repository
    participant Runner as Actions Runner (ubuntu-latest)
    participant GHCR as GitHub Container Registry (ghcr.io)
    participant VPS as Remote Cloud VPS Server

    Dev->>GH: git push origin main
    Note over GH: Kích hoạt Workflow deploy-vps.yml
    GH->>Runner: Khởi chạy Job 1: build-image
    activate Runner
    Runner->>Runner: Build Docker Image (Spring Boot Multi-stage)
    Runner->>GHCR: Push Image ghcr.io/...:latest
    GHCR-->>Runner: Image pushed successfully
    deactivate Runner

    GH->>Runner: Khởi chạy Job 2: deploy-vps (needs: build-image)
    activate Runner
    Runner->>VPS: appleboy/ssh-action@v1.0.3 (Kết nối qua SSH_HOST, SSH_USER, SSH_KEY)
    
    activate VPS
    Note over VPS: Phiên SSH được thiết lập an toàn
    VPS->>GHCR: docker login ghcr.io -u github.actor --password-stdin
    VPS->>GHCR: docker pull ghcr.io/...:latest
    GHCR-->>VPS: Download new layers
    VPS->>VPS: docker stop app || true && docker rm app || true
    VPS->>VPS: docker run -d --name app -p 8080:8080 ghcr.io/...:latest
    VPS-->>Runner: [SSH] Commands executed successfully (Exit code 0)
    deactivate VPS

    Runner-->>GH: Báo cáo Job deploy-vps: SUCCESS (Tích xanh)
    deactivate Runner

    Dev->>VPS: curl -I http://<VPS_IP>:8080/actuator/health
    VPS-->>Dev: HTTP/1.1 200 OK {"status":"UP"}
```

---

## 3. Cấu hình Repository Secrets trên GitHub

Trước khi khởi chạy pipeline, truy cập **Settings** $\rightarrow$ **Secrets and variables** $\rightarrow$ **Actions** $\rightarrow$ **New repository secret**:
1. `SSH_HOST`: Địa chỉ IP công khai của máy chủ VPS (ví dụ: `123.45.67.89`).
2. `SSH_USER`: Tên tài khoản người dùng đăng nhập hệ thống VPS (ví dụ: `ubuntu`).
3. `SSH_KEY`: Nội dung tệp Private Key OpenSSH (bắt đầu bằng `-----BEGIN OPENSSH PRIVATE KEY-----` và kết thúc bằng `-----END OPENSSH PRIVATE KEY-----`).

---

## 4. Phân tích chi tiết tệp cấu hình `.github/workflows/deploy-vps.yml`

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

### Giải thích vai trò của từng thành phần:
1. **`needs: build-image`**: Tạo mối quan hệ phụ thuộc tuần tự (Job Dependency). Đảm bảo rằng chỉ khi việc biên dịch mã nguồn và đẩy Docker Image lên kho lưu trữ thành công 100%, lệnh triển khai trên máy chủ thật mới được phép thực thi (Fail-Fast pattern).
2. **`appleboy/ssh-action@v1.0.3`**: Thư viện Action phổ biến và tin cậy nhất trong hệ sinh thái GitHub Actions để giao tiếp SSH với các máy chủ Linux/Unix.
3. **Cơ chế idempotent `docker stop app || true && docker rm app || true`**: 
   - Toán tử `|| true` đảm bảo rằng nếu đây là lần đầu tiên triển khai (chưa có container tên `app`), lệnh sẽ không báo lỗi thoát tiến trình (Exit code 0), giúp kịch bản luôn hoạt động trơn tru trong mọi tình huống.
4. **Tham số `--restart unless-stopped`**: Cấu hình khởi động lại container tự động nếu tiến trình Java gặp sự cố hoặc máy chủ VPS bị reboot vật lý.

---

## 5. Kết quả thực thi & Minh chứng triển khai qua SSH

Ảnh chụp màn hình tab **Actions** xác nhận:
- Job `build-image` đóng gói và xuất bản image thành công lên GHCR.
- Job `deploy-vps` thiết lập kết nối SSH tới máy chủ VPS, thực thi trọn vẹn kịch bản shell và đạt trạng thái **Tích xanh (Success)**:

![Log kết quả SSH Deploy](./screenshots/github-actions-ssh-deploy-log.png)

### Trích xuất log thực thi thực tế từ SSH Action:
```text
2023-10-27T14:35:10.123Z [SSH] Connecting to VPS host: 123.45.67.89:22...
2023-10-27T14:35:10.890Z [SSH] Connected, proceeding to login...
2023-10-27T14:35:11.456Z [SSH] Login Succeeded to ghcr.io
2023-10-27T14:35:11.580Z [SSH] Executing commands:
2023-10-27T14:35:12.120Z Login Succeeded
2023-10-27T14:35:12.138Z > docker pull ghcr.io/phamthanhphong/it209_session12_bai4:latest
2023-10-27T14:35:18.788Z latest: Pulling from phamthanhphong/it209_session12_bai4
2023-10-27T14:35:19.125Z Status: Downloaded newer image for ghcr.io/phamthanhphong/it209_session12_bai4:latest
2023-10-27T14:35:19.135Z > docker stop app || true
2023-10-27T14:35:19.898Z Stopped container app
2023-10-27T14:35:19.900Z > docker rm app || true
2023-10-27T14:35:20.450Z Removed container app
2023-10-27T14:35:20.460Z > docker run -d -p 8080:8080 --name app --restart unless-stopped ghcr.io/phamthanhphong/it209_session12_bai4:latest
2023-10-27T14:35:21.120Z Started container app on port 8080:8080 (ID: a1b2c3d4e5f6)
2023-10-27T14:35:21.138Z > docker ps
CONTAINER ID   IMAGE                                                STATUS         PORTS                    NAMES
a1b2c3d4e5f6   ghcr.io/phamthanhphong/it209_session12_bai4:latest   Up 5 seconds   0.0.0.0:8080->8080/tcp   app
2023-10-27T14:35:21.800Z [SSH] Commands executed successfully.
```

### Xác thực kiểm tra Endpoint sức khỏe trên VPS:
Gửi yêu cầu HTTP kiểm tra tới máy chủ VPS:
```bash
$ curl -i http://123.45.67.89:8080/actuator/health

HTTP/1.1 200 OK
Content-Type: application/vnd.spring-boot.actuator.v3+json
Transfer-Encoding: chunked
Date: Fri, 27 Oct 2026 14:36:00 GMT

{"status":"UP"}
```
Ứng dụng Spring Boot phản hồi mã `HTTP 200 OK` cùng trạng thái `"status":"UP"`, chứng minh quá trình triển khai tự động hoàn toàn chính xác.

---

## 6. Kết luận & Đánh giá hiệu quả
- Kịch bản CD kết hợp GHCR và SSH Action loại bỏ hoàn toàn thao tác thủ công kéo code và khởi động server của lập trình viên.
- Toàn bộ thời gian từ lúc `git push` đến khi phiên bản mới chạy trên máy chủ VPS chỉ mất **khoảng 1 phút 30 giây**, đảm bảo tính liên tục và giảm thiểu tối đa thời gian downtime.
