package com.example.su_sensor_plugin;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;

import com.pos.susdk.SUFunctions;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class SuSensorPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
  private MethodChannel channel;
  private EventChannel sensorStreamChannel;
  private EventChannel.EventSink eventSink;
  private Timer sensorTimer;
  private TimerTask sensorTask;
  private Context context;
  private UsbManager usbManager;
  private PendingIntent permissionIntent;
  private CopyOnWriteArrayList<UsbDevice> connectedDevices = new CopyOnWriteArrayList<>();
  private String currentPortName;
  private byte[] readBuffer = new byte[128];
  private Handler mainHandler = new Handler(Looper.getMainLooper());

  private static final String ACTION_USB_PERMISSION = "com.example.su_sensor_plugin.USB_PERMISSION";

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    channel = new MethodChannel(binding.getBinaryMessenger(), "su_sensor_plugin");
    sensorStreamChannel = new EventChannel(binding.getBinaryMessenger(), "su_sensor_plugin/sensor_stream");

    channel.setMethodCallHandler(this);
    sensorStreamChannel.setStreamHandler(this);
    context = binding.getApplicationContext();
    usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    sensorStreamChannel.setStreamHandler(null);
    stopSensorStream();
    cleanupUsbResources();
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
    switch (call.method) {
      case "startSensor":
        startSensor(call, result);
        break;
      case "stopSensor":
        stopSensor(result);
        break;
      case "getSensorValue":
        getSensorValue(result);
        break;
      case "getComList":
        getComList(result);
        break;
      case "initializeUsb":
        initializeUsb(result);
        break;
      case "cleanupUsb":
        cleanupUsb(result);
        break;
      default:
        result.notImplemented();
    }
  }

  @Override
  public void onListen(Object arguments, EventChannel.EventSink events) {
    this.eventSink = events;

    if (currentPortName == null) {
      startSensor(new MethodCall("startSensor", null), new MethodChannel.Result() {
        @Override
        public void success(Object result) {
          startSensorStream();
        }

        @Override
        public void error(String errorCode, String errorMessage, Object errorDetails) {
          if (eventSink != null) {
            eventSink.error(errorCode, errorMessage, errorDetails);
          }
        }

        @Override
        public void notImplemented() {
          // Not applicable here
        }
      });
    } else {
      startSensorStream();
    }
  }

  @Override
  public void onCancel(Object arguments) {
    stopSensorStream();
    this.eventSink = null;
  }

  private void startSensor(MethodCall call, MethodChannel.Result result) {
    String portName = call.argument("portName");
    Integer irLevel = call.argument("irLevel");

    if (portName == null || irLevel == null) {
      result.error("INVALID_ARGUMENTS", "Port name or IR level is null", null);
      return;
    }

    int openResult = SUFunctions.OpenPort(portName);
    if (openResult == 0) {
      SUFunctions.SetThresholdValue(portName, irLevel);
      currentPortName = portName;
      result.success(null);
    } else {
      result.error("PORT_ERROR", "Failed to open port", openResult);
    }
  }

  private void stopSensor(MethodChannel.Result result) {
    if (currentPortName != null) {
      SUFunctions.ClosePort(currentPortName);
      currentPortName = null;
    }
    stopSensorStream();
    result.success(null);
  }

  private void startSensorStream() {
    if (sensorTimer != null) return;

    sensorTimer = new Timer();
    sensorTask = new TimerTask() {
      @Override
      public void run() {
        if (currentPortName != null && eventSink != null) {
          SUFunctions.GetSensorValue(currentPortName, readBuffer);
          int sensorValue = ((readBuffer[3] & 0xFF) << 8) + (readBuffer[2] & 0xFF);

          // Send the sensor value to Flutter
          mainHandler.post(() -> eventSink.success(sensorValue));
        }
      }
    };

    sensorTimer.schedule(sensorTask, 0, 1000); // Adjust the interval as needed (1000ms = 1s)
  }

  private void stopSensorStream() {
    if (sensorTimer != null) {
      sensorTimer.cancel();
      sensorTimer = null;
    }
    if (sensorTask != null) {
      sensorTask.cancel();
      sensorTask = null;
    }
  }

  private void getSensorValue(MethodChannel.Result result) {
    if (currentPortName != null) {
      SUFunctions.GetSensorValue(currentPortName, readBuffer);
      int sensorValue = ((readBuffer[3] & 0xFF) << 8) + (readBuffer[2] & 0xFF);
      result.success(sensorValue);
    } else {
      result.error("NO_PORT", "No port is currently open", null);
    }
  }

  private void getComList(MethodChannel.Result result) {
    ArrayList<String> comList = SUFunctions.GetComList();
    result.success(comList);
  }

  private void initializeUsb(MethodChannel.Result result) {
    for (UsbDevice device : usbManager.getDeviceList().values()) {
      if (!usbManager.hasPermission(device)) {
        usbManager.requestPermission(device, permissionIntent);
      } else if (!connectedDevices.contains(device)) {
        connectedDevices.add(device);
      }
    }

    IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    context.registerReceiver(usbReceiver, filter);
    result.success(connectedDevices.size());
  }

  private void cleanupUsb(MethodChannel.Result result) {
    cleanupUsbResources();
    result.success(null);
  }

  private void cleanupUsbResources() {
    try {
      context.unregisterReceiver(usbReceiver);
    } catch (IllegalArgumentException e) {
      Log.w("SuSensorPlugin", "Receiver not registered: " + e.getMessage());
    }
  }

  private final android.content.BroadcastReceiver usbReceiver = new android.content.BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (ACTION_USB_PERMISSION.equals(action)) {
        synchronized (this) {
          UsbDevice device;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
          } else {
            device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          }

          if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            connectedDevices.add(device);
          }
        }
      }
    }
  };
}
