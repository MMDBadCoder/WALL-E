#include <WiFi.h>

// WiFi credentials
const char* ssid = "server";
const char* password = "12345678";

// Motor control pins for L298N
#define MOTOR_LEFT_IN1 26
#define MOTOR_LEFT_IN2 27
#define MOTOR_LEFT_ENA 14

#define MOTOR_RIGHT_IN1 33
#define MOTOR_RIGHT_IN2 25
#define MOTOR_RIGHT_ENB 32

// LED pins
#define WIFI_LED_PIN 19  // GPIO pin for WiFi status LED
#define CLIENT_LED_PIN 13 // GPIO pin for client connection status LED

// PWM properties
const int freq = 30000; // PWM frequency
const int resolution = 8; // 8-bit resolution (0-255)
const int leftChannel = 0; // LEDC channel for left motor
const int rightChannel = 1; // LEDC channel for right motor

// TCP server settings
WiFiServer server(12345); // Port number

// Timer for inactivity
unsigned long lastCommandTime = 0; // Tracks the last time a command was received
const unsigned long INACTIVITY_TIMEOUT = 50; // Timeout in milliseconds

void setup() {
  initializeSerial();
  setupMotorPins();
  setupLEDs(); // Initialize LED pins
  connectToWiFi();
  startTCPServer();
}

void loop() {
  handleClientConnection();
}

// Initialize serial communication
void initializeSerial() {
  Serial.begin(115200);
  Serial.println("Initializing...");
}

// Set motor control pins as output and configure PWM
void setupMotorPins() {
  // Set direction pins as outputs
  pinMode(MOTOR_LEFT_IN1, OUTPUT);
  pinMode(MOTOR_LEFT_IN2, OUTPUT);
  pinMode(MOTOR_RIGHT_IN1, OUTPUT);
  pinMode(MOTOR_RIGHT_IN2, OUTPUT);

  // Set enable pins as outputs
  pinMode(MOTOR_LEFT_ENA, OUTPUT);
  pinMode(MOTOR_RIGHT_ENB, OUTPUT);

  // Configure PWM for motor speed control
  ledcAttachChannel(MOTOR_LEFT_ENA, freq, resolution, leftChannel);
  ledcAttachChannel(MOTOR_RIGHT_ENB, freq, resolution, rightChannel);

  stopMotors(); // Ensure motors are stopped initially
}

// Initialize LED pins
void setupLEDs() {
  pinMode(WIFI_LED_PIN, OUTPUT);
  pinMode(CLIENT_LED_PIN, OUTPUT);
  digitalWrite(WIFI_LED_PIN, LOW); // Turn off WiFi LED initially
  digitalWrite(CLIENT_LED_PIN, LOW); // Turn off client LED initially
}

// Connect to WiFi
void connectToWiFi() {
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nConnected to WiFi");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());

  // Turn on WiFi LED when connected
  digitalWrite(WIFI_LED_PIN, HIGH);
}

// Start TCP server
void startTCPServer() {
  server.begin();
  Serial.println("TCP server started");
}

// Handle client connection and data
void handleClientConnection() {
  WiFiClient client = server.available();
  if (client) {
    Serial.println("New client connected");
    digitalWrite(CLIENT_LED_PIN, HIGH); // Turn on client LED when a client is connected

    while (client.connected()) {
      if (client.available()) {
        String data = client.readStringUntil('\n');
        data.trim();
        processClientData(data);
        lastCommandTime = millis(); // Update the last command time
      }
      checkInactivity(); // Check if the robot should stop due to inactivity
    }

    Serial.println("Client disconnected");
    client.stop();
    digitalWrite(CLIENT_LED_PIN, LOW); // Turn off client LED when client disconnects
    stopMotors(); // Stop motors when client disconnects
  }
}

// Process client data (two numbers separated by a comma)
void processClientData(String data) {
  int commaIndex = data.indexOf(',');
  if (commaIndex > 0) {
    int leftPower = data.substring(0, commaIndex).toInt();
    int rightPower = data.substring(commaIndex + 1).toInt();

    // Constrain values to -100 to +100 range
    leftPower = constrain(leftPower, -100, 100);
    rightPower = constrain(rightPower, -100, 100);

    // Set motor speeds and directions
    setMotorSpeed(MOTOR_LEFT_IN1, MOTOR_LEFT_IN2, MOTOR_LEFT_ENA, leftPower);
    setMotorSpeed(MOTOR_RIGHT_IN1, MOTOR_RIGHT_IN2, MOTOR_RIGHT_ENB, rightPower);

    Serial.print("Left Motor: ");
    Serial.print(leftPower);
    Serial.print(", Right Motor: ");
    Serial.println(rightPower);
  }
}

// Set motor speed and direction using PWM and direction pins
void setMotorSpeed(int in1Pin, int in2Pin, int enable1Pin, int speed) {
  if (speed > 0) {
    // Forward direction
    digitalWrite(in1Pin, HIGH);
    digitalWrite(in2Pin, LOW);
  } else if (speed < 0) {
    // Reverse direction
    digitalWrite(in1Pin, LOW);
    digitalWrite(in2Pin, HIGH);
  } else {
    // Stop
    digitalWrite(in1Pin, LOW);
    digitalWrite(in2Pin, LOW);
  }
  // Map the speed from -100 to 100 to 0-255 for PWM
  int pwmValue = map(abs(speed), 0, 100, 0, 255);
  ledcWrite(enable1Pin, pwmValue);
}

// Stop both motors
void stopMotors() {
  setMotorSpeed(MOTOR_LEFT_IN1, MOTOR_LEFT_IN2, leftChannel, 0);
  setMotorSpeed(MOTOR_RIGHT_IN1, MOTOR_RIGHT_IN2, rightChannel, 0);
}

// Check for inactivity and stop motors if no command is received for 50ms
void checkInactivity() {
  if (millis() - lastCommandTime > INACTIVITY_TIMEOUT) {
    stopMotors();
  }
}