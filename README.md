# 📱 Phone Agent

AI remote phone control via AccessibilityService + WebSocket. Let LLM understand your phone's UI tree and make control decisions.

## ✨ Features

- **AccessibilityService**: Click, swipe, tap, input text, back, home, recent
- **WebSocket Server**: Remote control over WiFi (port 19876)
- **UI Tree Reading**: Parse Android UI hierarchy for LLM understanding
- **Command Parser**: Support commands like `click(500,800)`, `tap("蓝牙")`, `input("hello")`
- **Foreground Service**: Persistent WebSocket connection

## 🏗️ Architecture

```
AI (OpenClaw/LLM)
  ↕ WebSocket (JSON protocol)
Phone Agent APP
  ↕ AccessibilityService
Android System UI
```

## 📡 WebSocket Protocol

**Connect**: `ws://<phone-ip>:19876`

### Request

```json
{"action": "read_ui"}
{"action": "click", "x": 500, "y": 800}
{"action": "tap", "text": "蓝牙"}
{"action": "swipe", "startX": 500, "startY": 1500, "endX": 500, "endY": 500, "duration": 300}
{"action": "input", "text": "Hello World"}
{"action": "back"}
{"action": "home"}
{"action": "recent"}
{"action": "open_app", "package": "com.tencent.mm"}
{"action": "get_ip"}
```

### Response

```json
{"type": "ui_tree", "data": "<xml>...UI hierarchy...</xml>"}
{"type": "result", "success": true, "message": "clicked at (500,800)"}
{"type": "ip", "address": "192.168.1.100"}
{"type": "error", "message": "element not found"}
```

## 🚀 Getting Started

### Prerequisites

- Android 8.0+ (API 26+)
- JDK 17
- Android SDK with API 34

### Build

```bash
./gradlew assembleDebug
```

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Setup

1. Open Phone Agent on your phone
2. Go to **Settings → Accessibility → Phone Control Service** → Enable
3. Grant foreground service permission if prompted
4. The app will display `ws://<your-ip>:19876` connection address

### Quick Test (via ADB)

```bash
# Read UI tree
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml

# Toggle Bluetooth via accessibility
# 1. Read UI tree to find the switch
# 2. Click at coordinates
```

## 🔧 Tech Stack

| Component | Tech |
|-----------|------|
| Language | Kotlin 1.9.22 |
| UI | Jetpack Compose |
| Build | Gradle 8.2 + AGP 8.2.2 |
| WebSocket | Java-WebSocket 1.5.4 |
| JSON | Gson 2.10.1 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 |

## 📋 Project Structure

```
app/src/main/java/com/phoneagent/
├── PhoneControlService.kt              # AccessibilityService core
├── PhoneControlWebSocketServer.kt      # WebSocket server
├── PhoneControlForegroundService.kt    # Foreground service
├── CommandParser.kt                    # Command parser
└── ui/
    └── MainActivity.kt                # Compose UI
```

## ⚠️ Known Limitations

- **WeChat blocks UI tree reading**: Apps like WeChat and Alipay restrict AccessibilityService, returning empty nodes. Screenshot + multimodal LLM is needed for these apps.
- **Honor OS foreground service**: Some manufacturers restrict foreground service startup. May require manual permission grant.
- **Multimodal not yet supported**: Screenshot analysis pipeline is WIP.

## 🛣️ Roadmap

- [ ] Screenshot capture (MediaProjection)
- [ ] Multimodal LLM integration for UI understanding
- [ ] Operation confirmation & safety mechanisms
- [ ] Multi-phone support
- [ ] Shizuku integration as fallback for restricted devices

## 📄 License

MIT
