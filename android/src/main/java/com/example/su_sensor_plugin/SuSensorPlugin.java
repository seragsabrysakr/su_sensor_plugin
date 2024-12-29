package com.example.su_sensor_plugin;


import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import androidx.annotation.NonNull;

import com.pos.susdk.SUFunctions;

import java.util.ArrayList;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class SuSensorPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler {
  private MethodChannel channel;
  private Context context;
  private UsbManager usbManager;
  private PendingIntent permissionIntent;
  private ArrayList<UsbDevice> connectedDevices = new ArrayList<>();
  private String currentPortName;
  private byte[] readBuffer = new byte[128];

  private static final String ACTION_USB_PERMISSION = "com.example.su_sensor_plugin.USB_PERMISSION";

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    channel = new MethodChannel(binding.getBinaryMessenger(), "su_sensor_plugin");
    channel.setMethodCallHandler(this);
    context = binding.getApplicationContext();
    usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
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

  private void startSensor(MethodCall call, MethodChannel.Result result) {
    String portName = call.argument("portName");
    int irLevel = call.argument("irLevel");
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
    result.success(null);
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
    IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    context.registerReceiver(usbReceiver, filter);
    result.success(null);
  }

  private void cleanupUsb(MethodChannel.Result result) {
    cleanupUsbResources();
    result.success(null);
  }

  private void cleanupUsbResources() {
    context.unregisterReceiver(usbReceiver);
  }

  private final android.content.BroadcastReceiver usbReceiver = new android.content.BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (ACTION_USB_PERMISSION.equals(action)) {
        synchronized (this) {
          UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            connectedDevices.add(device);
          }
        }
      }
    }
  };
}
