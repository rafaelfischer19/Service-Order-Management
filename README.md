<img width="1349" height="460" alt="rs" src="https://github.com/user-attachments/assets/3637105c-bdb7-411e-9b3b-91d6e21f9487" />


# 🚀 OS System

## Industrial Service Order & Technician Management System

OS Rohden is a real-time industrial maintenance management platform designed to improve communication between technicians, machines, and maintenance teams through MQTT, Android, and cloud infrastructure.

The system allows technicians to instantly receive, accept, manage, and close service orders directly from mobile devices while maintaining synchronization across all connected users.

---

# 📱 Main Features

## ✅ Real-Time Service Orders
- Instant OS reception via MQTT
- Real-time synchronization
- Automatic technician coordination
- Live updates between devices

## 👨‍🔧 Technician Workflow
- Accept service orders
- Register maintenance notes
- Add supporting technicians
- Close completed OS directly from the app

## ⚡ Smart Synchronization
When a technician accepts a service order:
- Other devices automatically remove the same OS
- Prevents duplicated maintenance actions
- Keeps maintenance workflow organized

## 🏭 Industrial Environment Ready
- Priority classification (P1 → P4)
- Maintenance sector filtering
- Electrical and mechanical support
- Designed for factory environments

---

# 🛠️ Technologies Used

| Technology | Description |
|---|---|
| Kotlin | Android development |
| Jetpack Compose | Modern Android UI |
| MQTT | Real-time communication |
| Eclipse Mosquitto | MQTT Broker |
| AWS EC2 | Cloud infrastructure |
| Firebase | Cloud synchronization |
| Material 3 | UI framework |
| Android Foreground Service | Persistent MQTT connection |

---

# 🧠 System Architecture

```text
Industrial Machines / PLCs
            │
            ▼
MQTT Broker (Mosquitto - AWS EC2)
            │
            ▼
Firebase Bridge (Node.js)
            │
            ▼
Android Application (OS Rohden)
            │
            ▼
Technician Management
📂 Project Structure
OS Rohden/
│
├── app/
│   ├── src/main/java/com/example/osrohden/
│   │   ├── MainActivity.kt
│   │   ├── MqttService.kt
│   │   ├── OsStorage.kt
│   │   ├── Prefs.kt
│   │   ├── SetupActivity.kt
│   │   └── BootReceiver.kt
│   │
│   ├── res/
│   └── AndroidManifest.xml
│
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
🔥 Core Features
📡 MQTT Communication
Real-time industrial communication
Lightweight messaging protocol
Persistent connection using foreground service
📲 Mobile Technician Management
Mobile-first workflow
Fast service response
Industrial maintenance coordination
☁️ Cloud Infrastructure
AWS EC2 hosting
Firebase integration
Scalable architecture
💾 Local Persistence
Stores accepted service orders
Technician observations
Offline-safe local management
🎨 User Interface

Built with:

Jetpack Compose
Material Design 3
Responsive layouts
Dynamic priority indicators
Industrial-focused UX

Priority color system:

🟢 P1 → Critical
🟡 P2 → High
🟠 P3 → Medium
🔴 P4 → Low
⚙️ MQTT Topics
Incoming Orders
devices/status
Accepted Orders
OSAceita
Closed Orders
OSEncerrada
🔒 Android Permissions

The application uses:

Notification permissions
Ignore battery optimization
Foreground service permissions
Internet access

These permissions ensure stable MQTT communication and real-time operation.

📦 Installation
Clone Repository
git clone https://github.com/YOUR_USERNAME/os-rohden.git
Open in Android Studio

Open the project folder and sync Gradle.

Configure MQTT Server

Inside MqttService.kt:

const val MQTT_SERVER = "tcp://YOUR_SERVER_IP:1883"
☁️ Firebase Bridge

The project includes a Node.js bridge responsible for synchronizing MQTT events with Firebase Realtime Database.

Features:

MQTT → Firebase
Firebase → MQTT
Android real-time updates
🚀 Future Improvements
Firebase Authentication
PDF service reports
Dashboard analytics
Push notifications
AI-assisted diagnostics
Multi-company support
Technician geolocation
Offline synchronization
📸 Suggested Screenshots

Recommended images for the repository:

Main OS list screen
Accept service order dialog
MQTT communication architecture
AWS EC2 + Firebase infrastructure
Technician workflow screen
🌍 Project Goals

This project was designed to:

Modernize industrial maintenance management
Reduce response time
Improve technician coordination
Enable real-time communication
Create scalable industrial IoT solutions
👨‍💻 Author

Rafael Fischer

Industrial Automation • IoT • Embedded Systems • Android Development • Cloud Infrastructure

⭐ Highlights
Real-time industrial communication
MQTT architecture
Android foreground services
Cloud-connected maintenance system
Scalable IoT infrastructure
Modern Android development with Compose
