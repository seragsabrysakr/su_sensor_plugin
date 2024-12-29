import 'package:flutter/material.dart';
import 'package:su_sensor_plugin/su_sensor_plugin.dart';

class SensorPage extends StatefulWidget {
  @override
  _SensorPageState createState() => _SensorPageState();
}

class _SensorPageState extends State<SensorPage> {
  @override
  void initState() {
    super.initState();

    // Start the sensor when the page is initialized
    SuSensorPlugin.startSensor('COM1', 100);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text("Sensor Data")),
      body: Center(
        child: StreamBuilder<int>(
          stream: SuSensorPlugin.listenToSensorStream(), // Listen to the sensor stream
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return CircularProgressIndicator(); // Show loading indicator while waiting for data
            } else if (snapshot.hasError) {
              return Text('Error: ${snapshot.error}'); // Show error if there's any
            } else if (!snapshot.hasData) {
              return Text('No data received'); // Show message if no data is received
            } else {
              // Show the sensor value when data is received
              return Text("Sensor Value: ${snapshot.data}");
            }
          },
        ),
      ),
    );
  }
}
