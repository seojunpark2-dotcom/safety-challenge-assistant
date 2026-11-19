# 안전실천 도우미

안전실천 챌린지의 화면을 확인하는 개인용 Android 도우미입니다. 스크린샷에서 확인한 공지, 출석 완료, 퀴즈 참여 완료, 룰렛 잔액과 응모 마감 문구를 접근성 노드에서 찾습니다. 대상 패키지를 지정하고 서비스에서 자동 참여가 허용된 기능을 확인한 후 실행할 수 있습니다.

## 다운로드 및 설치

- [APK 1.4.0 바로 다운로드](https://github.com/seojunpark2-dotcom/safety-challenge-assistant/raw/refs/heads/apk-download/safety-assistant-1.4.0.apk)
- [ZIP 1.4.0 바로 다운로드](https://github.com/seojunpark2-dotcom/safety-challenge-assistant/raw/refs/heads/apk-download/safety-assistant-1.4.0.zip) — 브라우저에서 APK가 내려받아지지 않으면 ZIP을 받아 압축을 풀고 APK를 설치하세요.

비공개 저장소의 다운로드 링크는 접근 권한이 있는 GitHub 계정으로 로그인한 브라우저에서 열어야 합니다. 404가 표시되면 [GitHub 로그인](https://github.com/login) 후 [1.4.0 릴리스 페이지](https://github.com/seojunpark2-dotcom/safety-challenge-assistant/releases/tag/v1.4.0)를 열고 **Assets → safety-assistant-1.4.0.apk**를 누르세요. 카카오톡 등 앱 내 브라우저에서 열었다면 GitHub에 로그인한 Chrome 또는 Safari에서 다시 시도하세요.

APK를 스마트폰으로 옮긴 뒤, 다운로드 또는 파일 앱의 알 수 없는 앱 설치를 허용하고 APK를 엽니다. 진단용 APK는 디버그 키로 서명합니다.

## 화면 진단

1. 대상 앱의 패키지명을 확인합니다. Google Play 공유 링크에서 `id=` 뒤에 표시되는 값을 사용하거나, USB 디버깅이 켜져 있다면 대상 앱을 화면에 띄우고 `adb shell dumpsys activity activities`의 `mResumedActivity`를 확인합니다.
2. 도우미에 패키지명을 입력하고 저장한 후 Android 접근성 설정에서 안전실천 도우미를 켭니다.
3. 대상 앱에서 볼 화면을 연 뒤 도우미에서 **최근 화면 진단 결과 보기**를 누릅니다.

대상 앱의 보이는 접근성 문구를 메모리에서 검사하고 판정과 구조를 표시합니다. 이미지에만 있는 버튼과 표시에는 기기 확인이 필요합니다. 3,000포인트 버튼이 표시돼도 실제 응모 가능 여부는 앱에서 확인하세요.

## 자동 실행

- 출석, 퀴즈, 포인트 확인, 룰렛을 각각 실행하거나 예약 대상에 추가할 수 있습니다.
- 사진에서 정답이 확인된 크레인 문항은 기존 정답을 사용합니다. 새 문항은 Gemini로 판단합니다. 앱에 관련 공식 자료가 있으면 함께 전달합니다.
- 룰렛은 잔액과 3,000포인트 버튼, 사용 동의 및 일일 한도를 검사합니다. 마감되거나 비용이 다르면 중단합니다.
- 출석, 제출, 룰렛 버튼을 누르기 전에 해당 날짜의 시도를 기록합니다. 응답을 읽지 못하거나 앱을 다시 시작해도 같은 날 다시 누르지 않습니다.
- 예약은 기본 오전 9시입니다. WorkManager 실행은 지연될 수 있고 잠금 상태에서는 알림으로 안내합니다. 잠금 해제 후 도우미에서 실행할 수 있습니다.
- 실제 기기에서 접근성 버튼, 스크롤, 결과 문구를 검증해야 합니다. 이미지로만 표시된 버튼은 사용자가 직접 조작합니다.

## AI 퀴즈 설정

[Google AI Studio](https://aistudio.google.com/app/apikey)에서 발급한 Gemini API 키를 앱의 **Gemini API 키** 항목에 입력하고 **키 저장**을 누릅니다. 키는 기기에서 Android Keystore로 보호해 저장하며 앱에서 삭제할 수 있습니다. 결제를 연결하지 않은 무료 프로젝트의 키를 사용하면 무료 한도에서 작동합니다. 앱은 기본 텍스트 요청만 보내며 Google 검색 도구를 호출하지 않습니다.

새 문항을 풀 때는 접근성으로 읽은 문제 문장과 주제가 일치하는 법령 요약을 Gemini로 전송합니다. 자료는 국가법령정보센터의 산업안전보건기준에 관한 규칙 제32조, 제146조, 제301조를 바탕으로 작성했습니다. 자료가 있으면 응답이 해당 조문을 인용해야 제출을 시도합니다. 자료가 없으면 문제만 보내며, 답과 이유가 명확하면 제출을 시도합니다. 이 경우 실제 정답이 확인된 것은 아니며 실행 기록에 공식 근거 없는 Gemini 추론으로 표시합니다. Gemini가 판단을 보류하거나 요청 한도에 걸리면 실행 기록을 남기고 중단합니다. 접근성 문구가 없는 이미지 퀴즈는 수동으로 진행합니다.

## 빌드

Android SDK 35와 JDK 17 이상이 필요합니다. Windows에서 한글 경로로 테스트가 실패하면 영문 경로에서 실행합니다.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

결과는 `app/build/outputs/apk/debug/`에 생성됩니다.
