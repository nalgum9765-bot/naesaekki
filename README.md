# 내새끼

반려동물 나이·추억 기록 앱. 웹앱(PWA)과 안드로이드 앱(Capacitor)을 같은 코드로 만듭니다.

```
www/        웹앱 전체 (index.html 하나 + 누끼 모델 + 샘플 데이터 + 개인정보처리방침)
android/    안드로이드 프로젝트 (Capacitor 8)
.github/    자동 빌드 (안드로이드 AAB·APK, GitHub Pages 배포)
```

## 1. 서명 키 등록 (처음 한 번)

GitHub 저장소 → **Settings → Secrets and variables → Actions → New repository secret** 에서 4개를 추가합니다.

| 이름 | 값 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `ANDROID_KEYSTORE_BASE64.txt` 파일 내용 전체 |
| `ANDROID_KEYSTORE_PASSWORD` | `secrets.txt` 의 값 |
| `ANDROID_KEY_ALIAS` | `upload` |
| `ANDROID_KEY_PASSWORD` | `secrets.txt` 의 값 |

`naesaekki-upload.jks` 와 비밀번호는 **저장소에 올리지 말고** 따로 안전하게 보관하세요. 이 키로 서명해야 플레이스토어에 업데이트를 올릴 수 있습니다. (Play 앱 서명을 쓰면 잃어버렸을 때 업로드 키 재설정을 신청할 수 있습니다.)

## 2. 안드로이드 앱 만들기

GitHub 저장소 → **Actions → Android 앱 빌드 → Run workflow** → 버전 이름 입력(예: `1.0.0`) → 실행.
5~10분 뒤 실행 결과 아래 **Artifacts** 에서 압축 파일을 받으면 안에 두 개가 들어 있습니다.

- `app-release.aab` — 플레이 콘솔에 올리는 파일
- `app-release.apk` — 내 폰에 바로 설치해서 확인하는 파일

버전 코드는 실행할 때마다 자동으로 1씩 올라갑니다. `v1.0.1` 같은 태그를 푸시해도 빌드됩니다.

## 3. 웹앱 배포 (GitHub Pages)

저장소 → **Settings → Pages → Source: GitHub Actions** 로 한 번 설정하면, `www/` 가 바뀔 때마다 자동으로 배포됩니다.
개인정보처리방침 주소: `https://<아이디>.github.io/<저장소>/privacy.html` (플레이 콘솔에 입력)

## 4. 플레이 콘솔 (2023.11.13 이후 만든 개인 계정)

1. 앱 만들기 → 기본 정보·개인정보처리방침 URL·데이터 보안(수집 없음) 작성
2. **비공개 테스트** 트랙에 AAB 업로드 → 테스터 **12명 이상**이 **14일 연속** 참여
3. 14일이 지나면 프로덕션 액세스 신청 → 심사 후 출시

## 로컬에서 직접 빌드할 때

```
npm ci
npx cap sync android
npx cap open android   # 안드로이드 스튜디오에서 열기
```

앱 ID: `com.dogom.naesaekki` (플레이스토어에 처음 올린 뒤에는 바꿀 수 없습니다)
