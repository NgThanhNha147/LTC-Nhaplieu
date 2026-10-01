# Khung van hanh an toan thong tin

Tai lieu nay bo sung cho thiet ke RBAC va audit trong ung dung. Muc tieu la xac dinh nhung kiem soat ngoai code can co truoc khi dung du lieu that.

## Phan tach moi truong

- Development, staging va production dung database, bucket tai lieu, tai khoan va secret rieng.
- Khong dua du lieu ca nhan that vao link Cloudflare demo.
- PostgreSQL production khong public port 5432 ra Internet.
- Compose local chi bind PostgreSQL vao `127.0.0.1`. Production nen bo hoan toan `ports` cua PostgreSQL bang compose override va chi cho backend truy cap qua private network.
- Chi reverse proxy/WAF duoc phep truy cap backend; frontend va API bat buoc HTTPS.
- OCR va PostgreSQL chi nam trong private network.

## Tai khoan ky thuat

| Tai khoan | Quyen toi da |
|---|---|
| Migration | DDL trong schema ung dung, chi dung khi deploy |
| Runtime | CRUD du lieu ung dung, khong tao role/database |
| Backup | Doc database va volume backup, ghi kho backup |
| Restore | Quyen dac biet, chi cap trong cua so bao tri |
| Read-only | Bao cao/kiem tra co kiem soat |

Khong dung chung mat khau giua cac tai khoan. Secret phai nam trong secret manager, co owner, ngay tao va chu ky luan chuyen.

## Logging va audit

- Log ung dung phai co timestamp UTC, request ID, actor, action, result va latency.
- Khong log password, access token, refresh token, cookie, noi dung tep hoac day du CCCD.
- Log 401/403, login fail, export, upload/download, thay doi quyen, sinh/xoa bang, backup va restore.
- Dong bo gio bang NTP.
- Chuyen log ra he thong tap trung; tai khoan runtime khong duoc sua/xoa audit.
- Canh bao khi login fail tang dot bien, export lon, nhieu 403, scan URL hoac backup that bai.

## Quan ly su co

1. Phat hien va tao ma su co.
2. Co lap tai khoan/token/service bi anh huong.
3. Bao toan log va bang chung; khong sua truc tiep.
4. Danh gia pham vi du lieu va nguoi dung bi anh huong.
5. Khac phuc, rotate secret, scan lai va restore neu can.
6. Xac minh he thong, mo lai traffic co kiem soat.
7. Lap bien ban post-incident va theo doi action item.

## Checklist truoc production

- RBAC duoc kiem thu ca UI va goi API truc tiep.
- Khong con tai khoan/mat khau mac dinh.
- CORS chi cho phep origin chinh thuc.
- Security headers, rate limit va gioi han upload/export da bat.
- Upload kiem tra noi dung that va quet malware.
- Tat ca secret duoc lay tu secret manager.
- CI security gate dat; khong con High/Critical chua xu ly.
- Backup tu dong chay, checksum hop le va restore test thanh cong.
- Co dashboard/canh bao va danh sach truc xu ly su co.
- Co chinh sach retention cho du lieu nghiep vu, document va audit.
