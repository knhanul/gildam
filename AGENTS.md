# Gildam (길담) — 개발 환경 메모

## Android SDK / 에뮬레이터 위치
- SDK: `D:\Android\Sdk` (원래 `C:\Users\knhan\AppData\Local\Android\Sdk`에서 이동됨, `local.properties`의 `sdk.dir` 및 사용자 환경변수 `ANDROID_HOME`/`ANDROID_SDK_ROOT`가 D:를 가리킴)
- AVD: `D:\Android\avd\gildam.avd` (`ANDROID_AVD_HOME=D:\Android\avd`, C: 용량 부족으로 이동)
- 시스템 이미지: `system-images;android-35;google_apis;x86_64` (compileSdk 35 기준)
- cmdline-tools 19.0: `D:\Android\Sdk\cmdline-tools\latest`

## 빌드
- **JAVA_HOME은 반드시 `D:\Android\jbr`(JBR 21)** 로 설정할 것. `gradle/gradle-daemon-jvm.properties`가 JetBrains vendor Java 21 데몬 JVM을 요구하며, 시스템 기본 jdk-24/25로는 실패함.
  ```powershell
  $env:JAVA_HOME = "D:\Android\jbr"
  .\gradlew.bat :app:assembleDebug
  ```
- 참고: config-cache report TimeoutException으로 BUILD FAILED가 떠도 `app/build/outputs/apk/debug/app-debug.apk`는 정상 생성되는 경우가 있음.

## 에뮬레이터 실행
```powershell
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"; $env:ANDROID_AVD_HOME = "D:\Android\avd"
D:\Android\Sdk\emulator\emulator.exe -avd gildam -gpu swiftshader_indirect
```
- 이 머신은 AMD(Ryzen 5 2500U) + WHPX 가속 사용. 호스트 GPU(Radeon Vega 8, Vulkan 1.1)는 gfxstream 요구(1.3) 미달이라 `-gpu host` 불가 → `swiftshader_indirect` 필수.
- 소프트웨어 렌더링이라 느림: AVD 해상도를 720x1560@320dpi로 낮춰 둠(1080x2400이면 콜드스타트 ANR 발생). `hw.ramSize=3072`.
- 애니메이션/ANR 완화 설정 적용됨: `window_animation_scale=0`, `transition_animation_scale=0`, `animator_duration_scale=0`, `anr_show_background=0`, `show_first_crash_dialog=0`.

## 앱 설치/실행
```powershell
D:\Android\Sdk\platform-tools\adb.exe -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
D:\Android\Sdk\platform-tools\adb.exe -s emulator-5554 shell monkey -p kr.co.nuni.gildam -c android.intent.category.LAUNCHER 1
```
- 실물 디바이스 `ce10171ac08c812903`이 상시 연결돼 있으므로 `-s` 시리얼 지정 필수.
