#include <Arduino.h>
#include <SoftwareSerial.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <Servo.h>

#define leftEchoPin 2 //definiowanie pinow
#define leftTrigPin 3
#define rightEchoPin 4
#define rightTrigPin 5
#define speedMotor 6 //0-1023
#define forwardMotor 7
#define backwardMotor 8
#define turnServo 9

Servo myservo;

SoftwareSerial BTserial(10, 11); //TX RX nadanie nazwy i utworzenie portu do komunikacji z telefonem

int whereX, whereY, whereZ, turnDir;
int leftDistance, rightDistance;
float moveSpeed;
long leftDuration, rightDuration;
const int resetServo = 45;

const byte numChars = 32;
char receivedChars[numChars];
char tempChars[numChars];
boolean newData = false;

unsigned long lastTimeCheck = 0;
unsigned long timeCheck;

int correction = 0;

void setup() //pierwsze uruchomienie
{
  Serial.begin(9600);
  Serial.println("System starting waiting for message.");
  BTserial.begin(9600); //inicjalizacja portu do komunikacji z telefonem

  pinMode(leftTrigPin, OUTPUT); //definiowanie przeznaczenia pinow
  pinMode(leftEchoPin, INPUT);
  pinMode(rightTrigPin, OUTPUT);
  pinMode(rightEchoPin, INPUT);
  pinMode(speedMotor, OUTPUT);
  pinMode(forwardMotor, OUTPUT);
  pinMode(backwardMotor, OUTPUT);
  myservo.attach(turnServo);
  digitalWrite(leftTrigPin, LOW);
  digitalWrite(rightTrigPin, LOW);
  myservo.write(resetServo); //resetowanie pojazdu
  digitalWrite(forwardMotor, LOW);
  digitalWrite(backwardMotor, LOW);
  analogWrite(speedMotor, LOW);
}

char checkCollision() //sprawdzanie kolizji zwracając odpowiednio
{
  digitalWrite(leftTrigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(leftTrigPin, LOW);
  leftDuration = pulseIn(leftEchoPin, HIGH);
  leftDistance = leftDuration * 0.034 / 2; //odległość od lewego czujnika, wynik w cm

  digitalWrite(rightTrigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(rightTrigPin, LOW);
  rightDuration = pulseIn(rightEchoPin, HIGH);
  rightDistance = rightDuration * 0.034 / 2; //odległość od prawego czujnika, wynik w cm

  if (leftDistance < 30 && leftDistance > 10 && rightDistance >= 30)
  {
    return 'l'; //skrec w prawo
  }
  else if (leftDistance >= 30 && rightDistance < 30 && rightDistance > 10)
  {
    return 'r'; //skrec w lewo
  }
  else if ((leftDistance < 30 && rightDistance < 30) || rightDistance < 10 || leftDistance < 10)
  {
    return 's'; //stop, zatrzmaj sie
  }
  else
    return 'g'; //go, jedz - brak przeszkod
}

void recvWithStartEndMarkers() {
    static boolean recvInProgress = false;
    static byte ndx = 0;
    char startMarker = '<';
    char endMarker = '>';
    char rc;

    while (BTserial.available() > 0 && newData == false) {
        rc = BTserial.read();

        if (recvInProgress == true) {
            if (rc != endMarker) {
                receivedChars[ndx] = rc;
                ndx++;
                if (ndx >= numChars) {
                    ndx = numChars - 1;
                }
            }
            else {
                receivedChars[ndx] = '\0';
                recvInProgress = false;
                ndx = 0;
                newData = true;
            }
        }

        else if (rc == startMarker) {
            recvInProgress = true;
        }
    }
}

void parseData() {     

    char * strtokIndx; 

    strtokIndx = strtok(tempChars," ");      
    whereX = atoi(strtokIndx);     
 
    strtokIndx = strtok(NULL, " "); 
    whereY = atoi(strtokIndx);     

    strtokIndx = strtok(NULL, " ");
    whereZ = atoi(strtokIndx);     

}

void showData() {
  Serial.print("Czujnik ud lewy: ");
  Serial.println(leftDistance);
  Serial.print("Czujnik ud prawy: ");
  Serial.println(rightDistance);
  Serial.print("Pozycja X Y Z: ");
  Serial.print(whereX);
  Serial.print(" ");
  Serial.print(whereY);
  Serial.print(" ");
  Serial.print(whereZ);
  Serial.println();
  Serial.print("Decyzja: ");
  Serial.println(checkCollision());
}

int speedControlF()
{
  if(whereZ == 1){
    moveSpeed = 1023 * (1/3);
  } else if(whereZ == 2){
    moveSpeed = 1023 * (2/3);
  } else if(whereZ == 3){
    moveSpeed = 1023 * (4/5);
  }
  return moveSpeed;
}

int speedControlB()
{
  if(whereZ == -1){
    moveSpeed = 1023 * (1/2);
  }
  return moveSpeed;
}

int turnControlL()
{
  if(whereX == -1){
    turnDir = 42;
  } else if(whereX == -2){
    turnDir = 38;
  } else if(whereX == -3){
    turnDir = 34;
  } else if(whereX == -4){
    turnDir = 30;
  } else if(whereX == -5){
    turnDir = 28;
  }
  return turnDir;
}

int turnControlR()
{
    if(whereX == 1){
    turnDir = 48;
  } else if(whereX == 2){
    turnDir = 52;
  } else if(whereX == 3){
    turnDir = 56;
  } else if(whereX == 4){
    turnDir = 60;
  } else if(whereX == 5){
    turnDir = 62;
  }
  return turnDir;
}

void driveMotor()
{
  if (whereZ > 0) //do przodu
  {
    digitalWrite(forwardMotor, HIGH);
    digitalWrite(backwardMotor, LOW);
    analogWrite(speedMotor, speedControlF());
  }
  else if (whereZ < 0) //do tyłu
  {
    digitalWrite(forwardMotor, LOW);
    digitalWrite(backwardMotor, HIGH);
    analogWrite(speedMotor, speedControlB());
  }
  else  //cel dogoniony
  {
    digitalWrite(forwardMotor, LOW);
    digitalWrite(backwardMotor, LOW);
    analogWrite(speedMotor, LOW);
    moveSpeed = 0;
  }

  if (whereX > 0){ //w prawo
    myservo.write(turnControlR());
  } else if (whereX < 0){ //w lewo
    myservo.write(turnControlL());
  } else {
    myservo.write(resetServo);
  }
}

void correctionCalc(int distance){
  if (distance < 30 && distance >= 20){
      correction = 5;
  } else if (distance < 20 && distance > 10) {
      correction = 10;
  }
}

void collisionAvoid()
{
  if (checkCollision() == 'l') //skrec w lewo
  {
    analogWrite(speedMotor, speedControlF() * 0.5);
    correctionCalc(leftDistance);
    myservo.write(resetServo + correction);
  }
  else if (checkCollision() == 'r') //skrec w prawo
  {
    analogWrite(speedMotor, speedControlF() * 0.5);
    correctionCalc(rightDistance);
    myservo.write(resetServo - correction);
  }
  else if (checkCollision() == 's') //STOP AWARYJNY
  {
    myservo.write(resetServo);
    digitalWrite(forwardMotor, LOW);
    digitalWrite(backwardMotor, LOW);
    analogWrite(speedMotor, LOW);
    moveSpeed = 0;
  }
}

void loop() {
    recvWithStartEndMarkers();
    if (newData == true) {
        strcpy(tempChars, receivedChars);
        parseData();
        newData = false;
        showData();

    }
    checkCollision();
    collisionAvoid();
    if (checkCollision() == 'g')
      driveMotor();
}