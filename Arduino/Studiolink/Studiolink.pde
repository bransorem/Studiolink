// Author:  Brannen Sorem
//#define DEBUG

#include <inttypes.h>
#include <avr/pgmspace.h>
#include <Max3421e.h>
#include <Max3421e_constants.h>
#include <Usb.h>
#include <canoneos.h>
#include <ptp.h>
 
#define DEV_ADDR        1
#define DATA_IN_EP      1
#define DATA_OUT_EP     2
#define INTERRUPT_EP    3
#define CONFIG_NUM      1

#define CANON 0x02
#define NIKON 0x01

#define MAX_USB_STRING_LEN 64

void Response(byte*);


// ==========================================================================================
// ==========================================================================================
// ==========================================================================================
// ==========================================================================================
byte CAMERA_DISCONNECTED[] =    { 0x00, 0xEE, 0x02, 0x00, 0x02 };
byte SHUTTER_FIRED[] =          { 0x00, 0x01, 0x01, 0x00, 0x02 };
byte GENERAL_ERROR[] =          { 0xFF, 0x01, 0x01, 0x00, 0x00 };
byte START[] =                  { 0x00, 0xFF, 0xFF, 0x00, 0x00 };
byte TIMER_SUCCESS[] =          { 0x00, 0x01, 0x02, 0x00, 0x02 };
byte INTERVAL_STARTED[] =       { 0x00, 0x01, 0x03, 0x00, 0x02 };
byte INTERVAL_STATUS[] =        { 0x00, 0x01, 0x04, 0x00, 0x00 };
byte INTERVAL_SUCCESS[] =       { 0x00, 0x03, 0x06, 0x00, 0x00 };
byte STOP[] =                   { 0x00, 0x01, 0x05, 0x00, 0x02 };
byte CONFIRM_TIMER_TIME[] =     { 0x00, 0x02, 0x02, 0x00, 0x00 };
byte CONFIRM_INTERVAL_TIME[] =  { 0x00, 0x03, 0x02, 0x00, 0x00 };
byte CONFIRM_INTERVAL_SHOTS[] = { 0x00, 0x03, 0x04, 0x00, 0x00 };
byte CONFIRM_INTERVAL_SHOT[] =  { 0x00, 0x03, 0x05, 0x00, 0x00 };
byte CANCEL[] =                 { 0x00, 0x04, 0x03, 0x00, 0x01 };

 
// ==========================================================================================
class CamStateHandlers : public PTPStateHandlers
{
      bool stateConnected;
 
public:
      CamStateHandlers() : stateConnected(false) {};
 
      virtual void OnDeviceDisconnectedState(PTP *ptp);
      virtual void OnDeviceInitializedState(PTP *ptp);
} CamStates;
 
PTP       Ptp(DEV_ADDR, DATA_IN_EP, DATA_OUT_EP, INTERRUPT_EP, CONFIG_NUM, &CamStates);
CanonEOS  Eos(DEV_ADDR, DATA_IN_EP, DATA_OUT_EP, INTERRUPT_EP, CONFIG_NUM, &CamStates);
 
void CamStateHandlers::OnDeviceDisconnectedState(PTP *ptp)
{
    if (stateConnected)
    {
        stateConnected = false;
        Response(CAMERA_DISCONNECTED);
        //Notify(PSTR("Camera disconnected\r\n"));
    }
}
 
void CamStateHandlers::OnDeviceInitializedState(PTP *ptp)
{
    if (!stateConnected)
      stateConnected = true;
}

// ==========================================================================================
// ==========================================================================================
struct INTERVAL
{
  unsigned short int shot;
  unsigned short int shots;
  unsigned short int time;
} Interval;

// ==========================================================================================
struct TIMER
{
  int time;
} Timer;

// ==========================================================================================
// ==========================================================================================
const byte len = 2;
const byte button = 4;
const byte led = 5;
byte Camera;

// ==========================================================================================
// Interrupts
boolean timeinterrupted;
boolean intinterrupted;
 
// ==========================================================================================
void setup() {
  pinMode(2, INPUT); // interrupt 0 (short to pin 0 - RX)
  pinMode(3, INPUT); // interrupt 1 (hook to button)
  pinMode(button, OUTPUT);
  pinMode(led, OUTPUT);
  
  digitalWrite(button, HIGH);
  digitalWrite(led, HIGH);
  Serial.begin( 57600 );
  return_message(START);
  Ptp.Setup();
  Eos.Setup();
  delay(200);
  Interval.shot = 0;
  Interval.shots = 0xFFFF;
  Interval.time = 10;
  Timer.time = 0;
  timeinterrupted = false;
  intinterrupted = false;
  
  attachInterrupt(0, interrupt, LOW);
  digitalWrite(led, LOW);
}
 
// ==========================================================================================
void loop() 
{
    Ptp.Task();
    Eos.Task();
    getData();
    
    byte shutterButton = digitalRead(3);
    if (shutterButton == HIGH) {
      if (Camera == 0){
        Camera = CANON; capture();
        Camera = NIKON; capture();
      }
      else capture();
      return_message(SHUTTER_FIRED);
    }
}


// ==========================================================================================
// ==========================================================================================
void capture(){
  if (Camera == CANON) Eos.Capture();
  if (Camera == NIKON) Ptp.CaptureImage();
}

// ==========================================================================================
// Get Packet from the USB Buffer 
// ==========================================================================================
void getData()
{
  byte message_id;
  byte group_id;
  byte camera;
  byte buffer[len];

  // nullify all entries (only use what's received)
  for (int i = 0; i < len; i++) { buffer[i] = false; }  
    
  if( Serial.available() > 0) { 
    boolean groupread = false;
    boolean messageread = false;
    boolean cameraread = false;
        
    // get DATA
    int idata = 0;
    while (Serial.available() > 0){
      if (!cameraread){
        delay(10);
        camera = Serial.read();
        Camera = camera;
        cameraread = true;
      }
      else if (!groupread){
        delay(10);
        group_id = Serial.read();
        groupread = true;
      }
      else if (!messageread){
        delay(10);
        message_id = Serial.read();
        messageread = true;
      }
      else {
        buffer[idata] = Serial.read();    // get next value
        idata++;                          // increase counter
        if (idata > len) { idata--; break; } // overstepping bounds? then quit
      }
    }
    Serial.flush(); // ignore everything after max buffer
    
    parse(group_id, message_id, buffer, idata);
  }
}

// ==========================================================================================
// Parse the data from USB to determine course of action
// ==========================================================================================
void parse(byte group, byte message, byte *data, int bufferLength)
{  
  // Turn data back into single value
  word value = (data[0] << 8) + data[1];
  int amount = (int) value;
  word header = (group << 8) + message;
  uint32_t ret = 0;
  
  switch(header){
    // Interrupt =============================================================
    case 0x0000:
      break;
    // Shutter ===============================================================
    case 0x0101: // Shutter - Fire Shutter Now
      capture();
      digitalWrite(led, HIGH);
      return_message(SHUTTER_FIRED);
      delay(200);
      digitalWrite(led, LOW);
      break;
    case 0x0102: // Shutter - Timer Shutter
      timer(Timer.time);
      if (!timeinterrupted){
        capture();
        return_message(TIMER_SUCCESS);
      }
      timeinterrupted = false;
      break;
    case 0x0103: // Shutter - Interval Program Start
      interval();
      if (!intinterrupted){
        return_message(INTERVAL_SUCCESS);
      }
      intinterrupted = false;
      break;
    case 0x0201: // Timer - Set Timer Duration
      blinkLED();
      Timer.time = amount;
      CONFIRM_TIMER_TIME[3] = Timer.time >> 8;
      CONFIRM_TIMER_TIME[4] = Timer.time & 0xFF;
      return_message(CONFIRM_TIMER_TIME);
      break;
    case 0x0301: // Interval - Set Interval
      blinkLED();
      Interval.time = amount;
      CONFIRM_INTERVAL_TIME[3] = Interval.time >> 8;
      CONFIRM_INTERVAL_TIME[4] = Interval.time & 0xFF;
      return_message(CONFIRM_INTERVAL_TIME);
      break;
    case 0x0303: // Interval - Set Number of Shots
      blinkLED();
      Interval.shots = amount;
      CONFIRM_INTERVAL_SHOTS[3] = Interval.shots >> 8;
      CONFIRM_INTERVAL_SHOTS[4] = Interval.shots & 0xFF;  
      return_message(CONFIRM_INTERVAL_SHOTS);
      break;
    case 0x0401: // Request Status - Request Camera status
      CONFIRM_TIMER_TIME[3] = Timer.time >> 8;
      CONFIRM_TIMER_TIME[4] = Timer.time & 0xFF;
      return_message(CONFIRM_TIMER_TIME);
      
      CONFIRM_INTERVAL_TIME[3] = Interval.time >> 8;
      CONFIRM_INTERVAL_TIME[4] = Interval.time & 0xFF;
      return_message(CONFIRM_INTERVAL_TIME);
      
      CONFIRM_INTERVAL_SHOTS[3] = Interval.shots >> 8;
      CONFIRM_INTERVAL_SHOTS[4] = Interval.shots & 0xFF;
      return_message(CONFIRM_INTERVAL_SHOTS);
      
      CONFIRM_INTERVAL_SHOT[3] = Interval.shot >> 8;
      CONFIRM_INTERVAL_SHOT[4] = Interval.shot & 0xFF;
      return_message(CONFIRM_INTERVAL_SHOT);
      break;
    default:
      return_message(GENERAL_ERROR);
      break;
  }
}

// ==========================================================================================
// Intervalometer
// ==========================================================================================
void interval(){
  Interval.shot = 0;
  intinterrupted = false;
  return_message(INTERVAL_STARTED);
  for (int i = 0; i < Interval.shots - 1; i++){
    if (!intinterrupted){
      if (!intinterrupted) Interval.shot++;
      if (!intinterrupted) capture();
      if (!intinterrupted) {
        INTERVAL_STATUS[3] = Interval.shot >> 8;
        INTERVAL_STATUS[4] = Interval.shot & 0xFF;
        return_message(INTERVAL_STATUS);
      }
      if (!intinterrupted) timer(Interval.time);
    }
    else {
      return_message(CANCEL);
      break;
    }
  }
  Interval.shot++;
  if (!intinterrupted){
    capture();
    INTERVAL_STATUS[3] = Interval.shot >> 8;
    INTERVAL_STATUS[4] = Interval.shot & 0xFF;
    return_message(INTERVAL_STATUS);
  }
}

// ==========================================================================================
// Timer
// ==========================================================================================
void timer(int length){
  // blink LED (seconds)
  timeinterrupted = false;
  for (int i = 0; i < length-2; i++){
    if (!timeinterrupted){
      digitalWrite(led, HIGH);
      delay(500);
      digitalWrite(led, LOW);
      delay(500);
    }
    else break;
  }
  if (timeinterrupted) {
    return_message(CANCEL);
    return;
  }
  for (int i = 0; i < 8; i++){
    if (!timeinterrupted){
      digitalWrite(led, HIGH);
      delay(125);
      digitalWrite(led, LOW);
      delay(125);
    }
    else {
      return_message(CANCEL);
      break;
    }
  }
}

void interrupt(){
  timeinterrupted = true;
  intinterrupted = true;
}

void return_message(byte *message){
  for (int i = 0; i < 5; i++){
    Serial.write(message[i]);
  }
}

void blinkLED(){
  digitalWrite(led, HIGH); delay(100);
  digitalWrite(led, LOW); delay(100);
  digitalWrite(led, HIGH); delay(100);
  digitalWrite(led, LOW);
}

