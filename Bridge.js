const mqtt = require('mqtt');
const firebase = require('firebase/app');
require('firebase/database');

// Firebase Configuration
const firebaseConfig = {
    apiKey: "YOUR_API_KEY",
    authDomain: "YOUR_PROJECT.firebaseapp.com",
    databaseURL: "https://YOUR_PROJECT.firebaseio.com",
    projectId: "YOUR_PROJECT_ID",
    storageBucket: "YOUR_PROJECT.appspot.com",
    messagingSenderId: "XXXXXXXXXXXX",
    appId: "XXXXXXXXXXXX"
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);

const db = firebase.database();

// MQTT Connection
const mqttClient = mqtt.connect('mqtt://localhost:1883');

// MQTT Connected
mqttClient.on('connect', () => {

    console.log('Connected to MQTT Broker');

    // Subscribe to device topics
    mqttClient.subscribe('devices/status');
    mqttClient.subscribe('devices/data');

});

// MQTT Receive Messages
mqttClient.on('message', (topic, message) => {

    const payload = message.toString();

    console.log(`MQTT Message Received`);
    console.log(`Topic: ${topic}`);
    console.log(`Payload: ${payload}`);

    // Save incoming MQTT data to Firebase
    db.ref(topic).set({
        value: payload,
        timestamp: Date.now()
    });

});

// Firebase → MQTT Commands
db.ref('devices/commands').on('value', (snapshot) => {

    const command = snapshot.val();

    if(command) {

        console.log(`Sending MQTT Command`);
        console.log(command);

        mqttClient.publish(
            'devices/commands',
            JSON.stringify(command)
        );
    }

});

// MQTT Error Handling
mqttClient.on('error', (error) => {

    console.error('MQTT Error:', error);

});

// Firebase Connection Monitoring
db.ref(".info/connected").on("value", (snap) => {

    if (snap.val() === true) {

        console.log("Connected to Firebase");

    } else {

        console.log("Disconnected from Firebase");

    }

});

console.log('Firebase MQTT Bridge Started');
