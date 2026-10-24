# 길담 (부제: 길이야기) — Android Studio 멀티 모듈 스타터 프로젝트

본 프로젝트는 **길담 프로덕트 디자인 & 안드로이드 아키텍처 상세 설계서**와 Google 공식 **[android/skills](https://github.com/android/skills)** 가이드라인(`edge-to-edge`, Type-Safe Navigation, R8 Keep Rules, `.agents/skills/gildam-architecture/SKILL.md`)을 바탕으로 작성된 안드로이드 스튜디오 확장용 소스 코드입니다.

## 1. 모듈 구조 (6 Core Modules + `:app`)

```
gildam-android/
├── .agents/skills/gildam-architecture/SKILL.md  # Android Studio AI 에이전트용 아키텍처·정책 가드레일 스킬
├── gradle/libs.versions.toml                    # Gradle Version Catalog (Kotlin 2.1, Compose, Room, MapLibre, Hilt)
├── app/                                         # 엔트리 포인트, MainActivity(Edge-to-Edge), Manifest, 백업 제외 규칙
├── data/                                        # Room DB (WAL 모드), 로컬 전용 썸네일 매니저, 화이트리스트 GPX Exporter
├── auth/                                        # NUNI ID (id.nuni.co.kr) Custom Tabs + PKCE(S256) + Keystore 암호화 저장소
├── recording/                                   # LocationTrackingService (location 포그라운드 서비스), 하버사인 계산기
├── sync/                                        # OVH 길담 API (Retrofit + Gzip 압축 인터셉터), WorkManager 동기화 워커
├── map/                                         # MapLibre Native SDK Compose 뷰 & 실시간 테라코타 폴리라인 레이어
└── ui/                                          # Material 3 Warm Minimal 테마, 고정폭(Tabular) 계기판, 핵심 화면 5종
```

## 2. 안드로이드 스튜디오에서 열기 및 확장 방법

1. 압축을 해제한 `gildam-android` 폴더를 **Android Studio Ladybug (2024.2.1) 이상**에서 `Open`으로 엽니다.
2. Android Studio가 `settings.gradle.kts`와 `gradle/libs.versions.toml`을 인식하여 자동으로 Gradle Sync를 수행합니다.
3. `.agents/skills/gildam-architecture/SKILL.md`가 프로젝트에 포함되어 있으므로, Android Studio 내 AI 에이전트(Gemini / Claude Code / Cursor 등)나 `android skills` CLI를 사용할 때 **길담의 로컬 전용 사진 정책과 모듈 경계**를 자동으로 준수하며 추가 개발을 이어갈 수 있습니다.

## 3. 오픈 퀘스천 확정 후 교체할 위치 (`TODO` 마커)

- **NUNI ID OAuth 설정**: `auth/src/main/kotlin/kr/co/nuni/gildam/auth/NuniAuthConfig.kt`
  - `CLIENT_ID`, `AUTHORIZE_ENDPOINT`, `TOKEN_ENDPOINT`, `REDIRECT_URI`
- **OVH 서버 길담 API 도메인**: `sync/src/main/kotlin/kr/co/nuni/gildam/sync/di/SyncModule.kt`
  - `OVH_BASE_URL`
- **MapLibre 벡터 타일 스타일 URL**: `map/src/main/kotlin/kr/co/nuni/gildam/map/GildamMapConfig.kt`
  - `DEFAULT_LIGHT_STYLE_URL`, `DEFAULT_DARK_STYLE_URL` (OVH PMTiles 또는 상용 타일 JSON URL)
