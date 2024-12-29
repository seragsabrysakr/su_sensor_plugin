import 'package:flutter/services.dart';

class SuSensorPlugin {
  static const MethodChannel _channel = MethodChannel('su_sensor_plugin');
  static const EventChannel _sensorStreamChannel = EventChannel('su_sensor_plugin/sensor_stream');

  /// Starts the sensor.
  static Future<void> startSensor(String portName, int irLevel) async {
    await _channel.invokeMethod('startSensor', {'portName': portName, 'irLevel': irLevel});
  }

  /// Stops the sensor.
  static Future<void> stopSensor() async {
    await _channel.invokeMethod('stopSensor');
  }

  /// Gets the current sensor value.
  static Future<int> getSensorValue() async {
    return await _channel.invokeMethod('getSensorValue');
  }

  /// Fetches the list of available ports.
  static Future<List<String>> getComList() async {
    return List<String>.from(await _channel.invokeMethod('getComList'));
  }

  /// Initializes USB device management.
  static Future<void> initializeUsb() async {
    await _channel.invokeMethod('initializeUsb');
  }

  /// Cleans up USB resources.
  static Future<void> cleanupUsb() async {
    await _channel.invokeMethod('cleanupUsb');
  }

  static Stream<int> listenToSensorStream() {
    return _sensorStreamChannel.receiveBroadcastStream().map((sensorValue) {
      return sensorValue as int;
    });
  }
}
