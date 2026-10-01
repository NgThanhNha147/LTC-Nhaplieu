# Quy trinh kiem tra ma nguon va lo hong

## Cac lop kiem tra da cau hinh

| Lop kiem tra | Cong cu | Khi nao chay | Dieu kien fail |
|---|---|---|---|
| Backend test | Maven/Spring Boot | Push, pull request | Test hoac build loi |
| Frontend build | TypeScript/Vite | Push, pull request | Type check/build loi |
| Secret scan | Gitleaks | Push, PR, hang tuan | Phat hien secret |
| SCA repository | Trivy filesystem | Push, PR, hang tuan | High/Critical da co ban sua |
| Frontend SCA | `npm audit` | Push, PR, hang tuan | High/Critical |
| Java SCA | OWASP Dependency-Check | Khi co `NVD_API_KEY` | CVSS tu 7 tro len |
| Container scan | Trivy image | Push, PR, hang tuan | High/Critical da co ban sua |
| Code quality/SAST | SonarQube | Khi co Sonar secrets | Quality Gate cua server |
| SAST bo sung | GitHub CodeQL | Push, PR, hang tuan | Theo rule cua GitHub Code Scanning |
| DAST staging | OWASP ZAP Baseline | Khi co staging URL | ZAP failure |
| SBOM | Trivy CycloneDX | Push, PR, hang tuan | Luu artifact 30 ngay |
| Cap nhat dependency | Dependabot | Hang tuan | Tao pull request de review |

Workflow nam tai `.github/workflows/ci.yml` va `.github/workflows/security.yml`.

## GitHub secrets can cau hinh

| Secret | Bat buoc | Y nghia |
|---|---|---|
| `SONAR_HOST_URL` | Khong | URL SonarQube/SonarCloud tuong thich scanner |
| `SONAR_TOKEN` | Khong | Token project Sonar |
| `NVD_API_KEY` | Khong | Tang toc va on dinh OWASP Dependency-Check |
| `STAGING_BASE_URL` | Khong | URL staging de OWASP ZAP baseline scan |

Khi chua co Sonar secrets, build/test va cac security gate con lai van chay; buoc Sonar ghi ro la da skip. Khi chua co NVD key, Trivy van la SCA gate bat buoc.

## Quality Gate Sonar de xuat

Ap dung tren New Code:

- Security Rating: A.
- Reliability Rating: A.
- Security Hotspots reviewed: 100%.
- Coverage: toi thieu 80%.
- Duplicated Lines: duoi 3%.
- Khong co issue Blocker/Critical.

Bat tuy chon `sonar.qualitygate.wait=true` tren Sonar scanner neu server khong tu dong danh dau check cua pull request.

## Xu ly ket qua scan

| Muc do | SLA de xuat | Xu ly phat hanh |
|---|---:|---|
| Critical | 24 gio | Dung phat hanh |
| High | 7 ngay | Khong merge neu nam trong code/image moi |
| Medium | 30 ngay | Tao ticket va owner |
| Low | 90 ngay | Dua vao backlog |

Khong them allowlist/suppression chi de lam pipeline xanh. Moi ngoai le phai co:

1. Ma ticket va owner.
2. Chung minh false positive hoac bien phap giam thieu.
3. Ngay het han.
4. Nguoi phe duyet doc lap.

Mau suppression Dependency-Check nam tai `security/dependency-check-suppressions.xml`; cau hinh Gitleaks nam tai `.gitleaks.toml`.

## Chay tai may lap trinh

```powershell
cd backend
./mvnw.cmd verify

cd ../frontend
npm ci
npm run build
npm audit --audit-level=high

cd ..
docker run --rm -v "${PWD}:/repo" zricethezav/gitleaks:latest git /repo --redact
docker run --rm -v "${PWD}:/work" aquasec/trivy:latest fs `
  --severity HIGH,CRITICAL --ignore-unfixed `
  --skip-dirs /work/.git,/work/.tools,/work/.ui-check,/work/backups,/work/frontend/node_modules,/work/frontend/dist,/work/backend/target `
  /work
```

Scan image sau khi build:

```powershell
docker compose build
docker image ls
trivy image --severity HIGH,CRITICAL --ignore-unfixed <image-name>
```

## Quy tac merge va phat hanh

1. Bao ve nhanh `main`, cam force push va xoa nhanh.
2. Tat ca thay doi qua pull request va it nhat mot reviewer.
3. Bat buoc cac check `Backend tests`, `Frontend build`, `Secret scan` va `Source and dependency scan` thanh cong.
4. Khong su dung secret production trong GitHub variables hoac file `.env` commit vao repository.
5. Tao SBOM CycloneDX va ky image la buoc tiep theo truoc production.
6. Chay DAST OWASP ZAP tren staging sau khi da co xac thuc test rieng.
