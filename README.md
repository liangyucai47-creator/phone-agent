# 📱 Phone Agent

> **OpenClaw's Hand on Your Phone** — An AI-powered remote phone control tool that bridges OpenClaw and Android devices.

Phone Agent lets OpenClaw understand and control your Android phone through UI tree analysis and AccessibilityService. Currently a local network remote control tool; evolving into an AI phone agent that can proactively manage your device.

## 🎯 Vision

```
Phase 1 (Now): Remote Control
  OpenClaw → WebSocket → Phone Agent → Control Phone
  "OpenClaw has hands on your phone"

Phase 2 (Next): AI Phone Manager
  Phone Agent ↔ OpenClaw (bidirectional)
  Proactive notifications, autonomous decisions
  "OpenClaw manages your phone for you"

Phase 3 (Future): Conversational Phone Agent
  Phone Agent pushes events like a chatbot (Feishu-style)
  "Phone: New WeChat message from Boss"
  "You: Read it and summarize"
  "OpenClaw: Boss asks about the report, due tomorrow 5pm. Reply?"
```

## ✨ Features

- **AccessibilityService**: Click, swipe, tap, input text, back, home, recent
- **WebSocket Server**: Remote control over local network (port 19876)
- **UI Tree Reading**: Parse Android UI hierarchy for LLM understanding
- **Command Parser**: Commands like `click(500,800)`, `tap("蓝牙")`, `input("hello")`
- **Foreground Service**: Persistent WebSocket connection

## 🏗️ Architecture

```
┌─────────────────────────────────────────────┐
│                  OpenClaw                    │
│  (ACP / Telegram / Web Chat)                │
│                                             │
│  LLM reads UI tree → decides action         │
│  → sends command → receives result          │
└──────────────────┬──────────────────────────┘
                   │ WebSocket (JSON)
                   │ ws://phone-ip:19876
                   │
┌──────────────────▼──────────────────────────┐
│              Phone Agent APP                 │
│                                             │
│  ┌─────────────┐  ┌──────────────────────┐  │
│  │  WebSocket   │  │  Accessibility       │  │
│  │  Server      │←→│  Service             │  │
│  │  (port 19876)│  │  (UI control core)   │  │
│  └─────────────┘  └──────────┬───────────┘  │
│                              │               │
└──────────────────────────────┼───────────────┘
                               │
                    ┌──────────▼───────────┐
                    │   Android System UI  │
                    │   (Settings, Apps)   │
                    └──────────────────────┘
```

## 📡 WebSocket Protocol

**Connect**: `ws://<phone-ip>:19876`

### Request

```json
{"action": "read_ui"}
{"action": "click", "x": 500, "y": 800}
{"action": "tap", "text": "蓝牙"}
{"action": "tap_desc", "description": "Bluetooth switch"}
{"action": "swipe", "startX": 500, "startY": 1500, "endX": 500, "endY": 500, "duration": 300}
{"action": "input", "text": "Hello World"}
{"action": "back"}
{"action": "home"}
{"action": "recent"}
{"action": "open_app", "package": "com.tencent.mm"}
{"action": "get_ip"}
{"action": "screenshot"}
{"action": "get_notifications"}
```

### Response

```json
{"type": "ui_tree", "data": "<xml>...UI hierarchy...</xml>"}
{"type": "result", "success": true, "message": "clicked at (500,800)"}
{"type": "ip", "address": "192.168.1.100"}
{"type": "screenshot", "data": "base64..."}
{"type": "notifications", "data": [...]}
{"type": "error", "message": "element not found"}
```

## 🚀 Getting Started

### Prerequisites

- Android 8.0+ (API 26+)
- JDK 17
- Android SDK with API 34
- Same local network as OpenClaw

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
4. The app displays `ws://<your-ip>:19876` — use this in OpenClaw

### Connect from OpenClaw

```bash
# Test connection
wscat -c ws://192.168.1.100:19876

# Read phone UI
echo '{"action":"read_ui"}' | wscat -c ws://192.168.1.100:19876

# Toggle Bluetooth
echo '{"action":"tap","text":"蓝牙"}' | wscat -c ws://192.168.1.100:19876
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

## 📁 Project Structure

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

- **Local network only**: Currently requires phone and OpenClaw on the same WiFi
- **WeChat blocks UI tree**: Apps like WeChat/Alipay restrict AccessibilityService. Screenshot + multimodal LLM needed.
- **Manufacturer restrictions**: Honor/Xiaomi may block foreground service startup
- **No proactive notifications yet**: Phase 2 feature

## 🛣️ Roadmap

### Phase 1 — Remote Control ✅ (In Progress)
- [x] AccessibilityService (click/swipe/tap/input)
- [x] WebSocket server (JSON protocol)
- [x] UI tree reading
- [x] ADB validation (Bluetooth toggle loop)
- [ ] WebSocket connection stability
- [ ] Screenshot capture (MediaProjection)
- [ ] OpenClaw ACP integration

### Phase 2 — AI Phone Manager 🔄
- [ ] Notification listener (read phone notifications)
- [ ] Proactive event push to OpenClaw
- [ ] Autonomous decision making (auto-reply, filter alerts)
- [ ] Battery/status monitoring
- [ ] Tailscale/Cloudflare Tunnel for remote access

### Phase 3 — Conversational Agent 📋
- [ ] Bidirectional chat protocol
- [ ] Phone-initiated messages ("New message from Boss")
- [ ] Human-in-the-loop confirmation for sensitive actions
- [ ] Multi-phone support

## 📄 License

MIT
