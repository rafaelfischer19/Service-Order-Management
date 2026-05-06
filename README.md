<img width="1349" height="460" alt="rs" src="https://github.com/user-attachments/assets/9ceffb1d-7097-42b3-b0a6-dde5cee74d19" />

# OS Rohden - Service Order Management App

## Overview

OS Rohden is an Android application developed for real-time Service Order (OS) management and technician coordination inside industrial environments.

The application allows technicians to receive, accept, manage, and close service orders directly from mobile devices using MQTT communication integrated with cloud infrastructure.

---

# Main Features

## Real-Time Service Orders

- Receive service orders instantly via MQTT
- Automatic synchronization between technicians
- Real-time order updates

## Technician Management

- Accept service orders
- Add additional technicians
- Register observations and notes
- Close completed service orders

## Smart Synchronization

When one technician accepts an OS:
- Other devices automatically remove the same OS
- Prevents duplicated maintenance handling
- Maintains synchronization between all connected devices

## Priority and Sector Control

The app supports:
- Maintenance sectors
- Priority classification (P1 → P4)
- Maintenance type categorization

Example:
- Electrical
- Mechanical

---

# Technologies Used

- Kotlin
- Jetpack Compose
- MQTT Protocol
- Android Foreground Service
- Material 3
- AWS EC2
- Eclipse Mosquitto
- Firebase Integration
- Local Persistent Storage

---

# Application Architecture

```text
Industrial Machines / PLCs
            │
            ▼
MQTT Broker (Mosquitto - AWS EC2)
            │
            ▼
Android Application (OS Rohden)
            │
            ▼
Technician Management & Real-Time Updates
Main Components
MainActivity

Responsible for:

Application navigation
MQTT initialization
Service order management
Real-time event handling
MQTT Service

Background service responsible for:

MQTT connection
Receiving new OS messages
Sending acceptance and closing events
Maintaining persistent communication
Local Storage

Stores:

Accepted service orders
Technician notes
Additional technicians
Service history
Real-Time Workflow
New Service Order
Machine sends MQTT event
App receives new OS
Technician sees the order instantly
Accepting an OS
Technician accepts the order
Acceptance is published via MQTT
Other devices automatically remove the same OS
Accepted OS remains visible only to the assigned technician
Closing an OS
Technician adds notes
Technician closes the service order
MQTT publishes completion event
System updates all connected devices
User Interface

Built with Jetpack Compose and Material 3.

Features:

Responsive UI
Modern Android design
Real-time updates
Dynamic priority colors
Sector and maintenance type indicators
Permissions Used
Notifications

Used for:

Real-time service alerts
Background communication updates
Battery Optimization Ignore

Required to:

Keep MQTT communication active
Prevent Android from stopping the background service
Example Service Order Information
Machine: CNC_01
Type: Electrical
Sector: 2
Priority: P1
Observation: Emergency stop failure
Priority Levels
Priority	Description
P1	Critical
P2	High
P3	Medium
P4	Low
Future Improvements
Firebase Authentication
Cloud synchronization
PDF service reports
Maintenance history dashboard
Push notifications
Multi-company support
AI-assisted diagnostics
Offline synchronization
Project Highlights
Industrial maintenance workflow automation
Real-time technician coordination
MQTT-based communication system
Mobile-first maintenance management
Cloud-ready architecture
Author

Developed by Rafael Fischer

Automation • Industrial Software • IoT Systems • Mobile Development

Code Reference

Main application structure and MQTT event handling implemented in MainActivity.kt.
