# 안전실천 도우미

안전실천 챌린지의 화면을 확인하는 개인용 Android 도우미입니다. 스크린샷에서 확인한 공지, 출석 완료, 퀴즈 참여 완료, 룰렛 잔액과 응모 마감 문구를 접근성 노드에서 찾습니다. 자동 참여 허용 여부와 실기기 노드가 확인되기 전까지 실제 버튼 조작은 보류합니다.

## 다운로드 및 설치

[GitHub Releases에서 APK 받기](https://github.com/seojunpark2-dotcom/safety-challenge-assistant/releases) (저장소 접근 권한이 있는 계정으로 로그인)

APK를 스마트폰으로 옮긴 뒤, 다운로드 또는 파일 앱의 알 수 없는 앱 설치를 허용하고 APK를 엽니다. 진단용 APK는 디버그 키로 서명합니다.

## 화면 진단

1. 대상 앱의 패키지명을 확인합니다. Google Play 공유 링크에서 `id=` 뒤에 표시되는 값을 사용하거나, USB 디버깅이 켜져 있다면 대상 앱을 화면에 띄우고 `adb shell dumpsys activity activities`의 `mResumedActivity`를 확인합니다.
2. 도우미에 패키지명을 입력하고 저장한 후 Android 접근성 설정에서 안전실천 도우미를 켭니다.
3. 대상 앱에서 볼 화면을 연 뒤 도우미에서 **최근 화면 진단 결과 보기**를 누릅니다.

대상 앱의 보이는 접근성 문구를 메모리에서 검사하고 판정과 구조를 표시합니다. 이미지에만 있는 버튼과 표시에는 기기 확인이 필요합니다. 3,000포인트 버튼이 표시돼도 실제 응모 가능 여부는 앱에서 확인하세요.

매일 확인 알림은 기본 오전 9시이며 변경할 수 있습니다. WorkManager 알림은 시스템 상태에 따라 지연될 수 있습니다.

## 빌드

Android SDK 35와 JDK 17 이상이 필요합니다. Windows에서 한글 경로로 테스트가 실패하면 영문 경로에서 실행합니다.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

결과는 `app/build/outputs/apk/debug/`에 생성됩니다.
