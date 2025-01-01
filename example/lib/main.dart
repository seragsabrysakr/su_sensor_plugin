import 'package:collection/collection.dart';
import 'package:flutter/material.dart';

import 'package:su_sensor_plugin/su_sensor_plugin.dart';
import 'package:usb_serial/usb_serial.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: SensorPage(),
    );
  }
}
class SensorPage extends StatefulWidget {
  const SensorPage({super.key});

  @override
  _SensorPageState createState() => _SensorPageState();
}

class _SensorPageState extends State<SensorPage> {
  List<UsbDevice> devices = [];
  Stream<int>? stream;
  Stream<int>? stream2;
  bool isError = false;
  String? error;

  @override
  void initState() {
    super.initState();
    setState(() {
      startSensor();
    });
  }

  startSensor() async {
    devices = await UsbSerial.listDevices();
    UsbDevice? device = devices.firstWhereOrNull(
      (device) =>
          (device.manufacturerName?.contains('Proximity') ?? false) ||
          (device.productName?.contains('Proximity') ?? false) ||
          (device.productName?.contains('SU100') ?? false) ||
          (device.productName?.contains('Sensor') ?? false) ||
          (device.productName?.startsWith('SU100') ?? false) ||
          (device.productName?.contains('Sensor') ?? false) ||
          (device.manufacturerName?.contains('Sensor') ?? false),
    );
    if (device == null) {
      setState(() {
        error = 'No compatible Sensor found';
      });
      throw Exception('No compatible Sensor found');
    }
    try {
      await SuSensorPlugin.startSensor(
          device.deviceName, device.interfaceCount ?? 100);
    } catch (e) {
      setState(() {
        error = 'Failed to start sensor at ${device.deviceName} : $e';
      });
      throw Exception('Failed to start sensor at ${device.deviceName} : $e');
    }
    try {
      stream = SuSensorPlugin.listenToSensorStream();
      stream2 = getSensorValueStream();
    } catch (e) {
      setState(() {
        error = 'Failed to start sensor Streams at ${device.deviceName} : $e';
      });
      throw Exception('Failed to start Streams $e');
    }
  }

  Stream<int> getSensorValueStream() {
    return Stream.periodic(const Duration(seconds: 1), (_) async {
      return await SuSensorPlugin.getSensorValue();
    }).asyncMap((event) => event);
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 200,
      color: Colors.white,
      child: SingleChildScrollView(
        child: Center(
            child: error != null
                ? Text('$error',
                    style: const TextStyle(fontSize: 20, color: Colors.black))
                : Column(
                    children: [
                      sensorStream1(),
                      sensorStream2(),
                    ],
                  )),
      ),
    );
  }

  StreamBuilder<int> sensorStream1() {
    return StreamBuilder<int>(
      stream: stream, // Listen to the sensor stream
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const CircularProgressIndicator(); // Show loading indicator while waiting for data
        } else if (snapshot.hasError) {
          print('---------');
          print(snapshot.error);
          return Text('Error: ${snapshot.error}',
              style: const TextStyle(
                  fontSize: 20,
                  color: Colors.black)); // Show error if there's any
        }
        else if (!snapshot.hasData) {
          return const Text('No data received',
              style: TextStyle(
                  fontSize: 20, color: Colors.black)); // Show message if no data is received
        }
        else {
          // Show the sensor value when data is received
          return Text("Sensor1 Value: ${snapshot.data}",
              style: const TextStyle(fontSize: 20, color: Colors.black));
        }
      },
    );
  }

  StreamBuilder<int> sensorStream2() {
    return StreamBuilder<int>(
      stream: stream2, // Listen to the sensor stream
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const CircularProgressIndicator(); // Show loading indicator while waiting for data
        } else if (snapshot.hasError) {
          print('---------');
          print(snapshot.error);
          return Text('Error: ${snapshot.error}',
              style: const TextStyle(
                  fontSize: 20,
                  color: Colors.black)); // Show error if there's any
        }
        else if (!snapshot.hasData) {
          return const Text('No data received',
              style: TextStyle(
                  fontSize: 20, color: Colors.black)); // Show message if no data is received
        }
        else {
          // Show the sensor value when data is received
          return Text("Sensor2 Value: ${snapshot.data}",
              style: const TextStyle(fontSize: 20, color: Colors.black));
        }
      },
    );
  }
}
