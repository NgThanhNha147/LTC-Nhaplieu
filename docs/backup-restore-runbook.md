# Quy trinh backup va phuc hoi du lieu

Tai lieu nay ap dung cho ban POC chay bang Docker Compose. Backup bao gom dong bo:

- PostgreSQL o dinh dang custom cua `pg_dump`.
- Toan bo volume tai lieu nguoi dung da tai len.
- Manifest ghi kich thuoc va SHA-256 cua tung tep.

## Muc tieu van hanh

| Chi tieu | POC | Khuyen nghi production |
|---|---:|---:|
| RPO | 24 gio | 15 phut hoac theo nghiep vu |
| RTO | 4 gio | 1-2 gio |
| Backup ngay | 14 ban | 30 ban |
| Backup tuan | Chua ap dung | 8-12 ban |
| Backup thang | Chua ap dung | 12 ban |
| Dien tap restore | Hang thang | Hang thang/quy |

Backup chi duoc coi la hop le khi script verify checksum thanh cong va da co mot lan restore thu nghiem gan nhat dat yeu cau.

## Dieu kien truoc khi chay

1. Docker Desktop/Docker Engine va `docker compose` dang hoat dong.
2. Service `postgres` va `backend` dang chay.
3. Chay lenh tu thu muc goc repository. Script tam dung frontend, backend va OCR trong luc tao snapshot de database va tai lieu cung mot moc.
4. Thu muc backup nam tren dia luu tru rieng, du dung luong va chi tai khoan van hanh duoc truy cap.
5. Khong dua thu muc `backups/` len Git. Thu muc nay da duoc khai bao trong `.gitignore`.

## Tao backup

```powershell
pwsh ./scripts/ops/Backup-DynamicForm.ps1
```

Chi dinh noi luu va retention:

```powershell
pwsh ./scripts/ops/Backup-DynamicForm.ps1 `
  -OutputRoot "D:\LTC-Backups" `
  -RetentionDays 30
```

Moi backup co cau truc:

```text
20260930T010000Z/
  database.dump
  documents.tar.gz
  manifest.json
```

Script chi xoa cac thu muc het han co ten dung dinh dang timestamp va nam ben trong `OutputRoot`. No khong xoa duong dan ngoai backup root.

## Kiem tra backup

```powershell
pwsh ./scripts/ops/Test-DynamicFormBackup.ps1 `
  -BackupPath "D:\LTC-Backups\20260930T010000Z"
```

Buoc nay kiem tra manifest, kich thuoc va SHA-256. No khong thay the dien tap restore.

## Dat lich backup

Co the dung Windows Task Scheduler voi program `pwsh.exe` va arguments:

```text
-NoProfile -File F:\Database_API_Generator\scripts\ops\Backup-DynamicForm.ps1 -OutputRoot D:\LTC-Backups -RetentionDays 30
```

Tai khoan chay task phai co quyen su dung Docker va ghi vao thu muc backup. Cau hinh canh bao khi task tra exit code khac `0`.

## Phuc hoi

Phuc hoi la thao tac pha huy du lieu hien tai. Script bat buoc:

1. Verify checksum cua backup nguon.
2. Tao backup an toan cua trang thai hien tai.
3. Dung frontend, backend va OCR.
4. Restore PostgreSQL.
5. Thay noi dung volume tai lieu.
6. Khoi dong lai backend, frontend va OCR khi toan bo thao tac thanh cong.

Lenh phuc hoi:

```powershell
pwsh ./scripts/ops/Restore-DynamicForm.ps1 `
  -BackupPath "D:\LTC-Backups\20260930T010000Z" `
  -SafetyBackupRoot "D:\LTC-Backups\pre-restore" `
  -Confirmation "RESTORE DYNAMIC FORM DATA"
```

Neu co loi sau khi da dung ung dung, cac service ung dung duoc giu o trang thai dung. Khong tu y khoi dong lai truoc khi xac dinh database va volume tai lieu co cung mot moc backup hay khong.

## Checklist sau restore

1. Chay `docker compose ps`; PostgreSQL va backend phai healthy/running.
2. Chay `./scripts/smoke-test.ps1` neu moi truong da duoc cau hinh.
3. Dang nhap va mo it nhat mot bieu mau.
4. Kiem tra tong so ho so theo mot bieu mau mau.
5. Mo mot tai lieu dinh kem cu.
6. Tao va xoa mot ho so thu nghiem.
7. Xuat Excel theo khoang ngay.
8. Kiem tra Flyway khong bao checksum mismatch.
9. Ghi bien ban restore: backup ID, nguoi thuc hien, thoi gian, ket qua va sai lech.

## Luu y production

POC chua ma hoa tep backup tai lop ung dung. Production phai luu backup tren S3/MinIO/NAS co encryption at rest, versioning/immutable retention va tai khoan rieng. Ban sao thu hai phai nam ngoai may chu chay ung dung theo quy tac 3-2-1.

Khi yeu cau RPO nho hon 24 gio, thay `pg_dump` bang pgBackRest/WAL archive de ho tro point-in-time recovery. Viec restore production nen thuc hien vao database tam, chay smoke test, sau do moi chuyen traffic.
