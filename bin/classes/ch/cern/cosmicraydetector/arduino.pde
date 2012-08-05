//Seeeduino ADK Demo using Niels Brouwers' MicroBridge library. 
//Read an 
 
#include <SPI.h>
#include <Adb.h>
 
// Adb connection.
Connection * connection;
 
// Elapsed time for ADC sampling. The rate at which ADC value is sent to Android device.
long lastTime;
int counternum = 0;
int tagnumber = 0;
int inputPin = 7;
int sensorVal, outputVal;
boolean sensorValone, sensorValtwo;
  


 
// Event handler for the shell connection. 
// This event handler should simply reset the counternum if any signal is received from the Android device 
void adbEventHandler(Connection * connection, adb_eventType event, uint16_t length, uint8_t * data)
{
 
  if (event == ADB_CONNECTION_RECEIVE)
  {
    counternum=0;
  }
}
 
void setup()
{
  // initialize serial:
  Serial.begin(9600);
  //attachInterrupt(0, count, RISING);
  // reserve 200 bytes for the inputString:

  // Note start time
  lastTime = millis();

  // Set digital pin 7 as input
  pinMode(7, INPUT_PULLUP);
 
  // Initialise the ADB subsystem.  
  ADB::init();
 
  // Open an ADB stream to the phone's shell. Auto-reconnect. Use any unused port number eg:4568
  connection = ADB::addConnection("tcp:4568", true, adbEventHandler);  

}
 
void loop()
{
// Digital Input Version
  sensorVal = HIGH;
  counternum++;
  if (counternum == 10000) {
    Serial.println("Reset");
    counternum = 0;
  }

// Digital Reading and Sending
  sensorValone = LOW;
  sensorValone = digitalRead(2);
  sensorValtwo = LOW;
  sensorValtwo = digitalRead(2);
  if (sensorValone = HIGH) {//((sensorValone = LOW)*(sensorValtwo = HIGH)) {
  Serial.println(counternum);  
  //tagnumber = counternum;
  Serial.println("Tag");
  connection->write(2, (uint8_t*)&counternum);
  }
  
//  sensorVal = LOW;
  //if (sensorVal == HIGH) {
    //tagnumber = counternum;
    //Serial.println("Tag");
    //connection->write(2, (uint8_t*)&tagnumber);
  //}
  //else
  //;
//  long temp = millis()-lastTime;
//  Serial.println(temp);
 
  // Poll the ADB subsystem.
ADB::poll();

}

//void count() {
//  Serial.println(counternum);  
  //tagnumber = counternum;
//  Serial.println("Tag");
//  connection->write(2, (uint8_t*)&counternum);
//}
