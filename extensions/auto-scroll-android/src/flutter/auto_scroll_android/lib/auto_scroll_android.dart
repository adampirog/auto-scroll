import 'dart:ui';

import 'package:flet/flet.dart';
import 'package:flutter/services.dart';
import 'package:flutter_accessibility_service/flutter_accessibility_service.dart';
import 'package:flutter_accessibility_service/gesture_description.dart';


/// Registers our Android integration with Flet.
class Extension extends FletExtension {
  @override
  FletService? createService(Control control) {
    if (control.type == "AndroidAccessibility") {
      return AndroidAccessibilityService(control: control);
    }
    return null;
  }
}


/// Provides accessibility gestures and native overlay management.
class AndroidAccessibilityService extends FletService {
  AndroidAccessibilityService({required super.control});

  static const MethodChannel _overlayChannel = MethodChannel(
    'auto_scroll_android/overlay',
  );

  @override
  void init() {
    super.init();
    control.addInvokeMethodListener(_invokeMethod);
    _overlayChannel.setMethodCallHandler(_handleOverlayAction);
  }

  Future<dynamic> _invokeMethod(
    String name,
    dynamic args,
  ) async {
    switch (name) {
      // Accessibility — M0.2
      case "is_enabled":
        return await FlutterAccessibilityService
            .isAccessibilityPermissionEnabled();

      case "request_permission":
        return await FlutterAccessibilityService
            .requestAccessibilityPermission();

      case "test_swipe":
        final delayMs = (args["delay_ms"] as num).toInt();

        if (delayMs < 0 || delayMs > 15000) {
          throw ArgumentError("Invalid swipe delay");
        }

        if (!await FlutterAccessibilityService
            .isAccessibilityPermissionEnabled()) {
          return false;
        }

        await Future<void>.delayed(
          Duration(milliseconds: delayMs),
        );

        if (!await FlutterAccessibilityService
            .isAccessibilityPermissionEnabled()) {
          return false;
        }

        return await _swipe(direction: "down", speed: 1.0);

      // Floating overlay — M0.3
      case "overlay_permission_granted":
        return await _overlayChannel.invokeMethod<bool>(
              "overlay_permission_granted",
            ) ??
            false;

      case "request_overlay_permission":
        return await _overlayChannel.invokeMethod<bool>(
              "request_overlay_permission",
            ) ??
            false;

      case "show_overlay":
        return await _overlayChannel.invokeMethod<bool>(
              "show_overlay",
            ) ??
            false;

      case "hide_overlay":
        return await _overlayChannel.invokeMethod<bool>(
              "hide_overlay",
            ) ??
            false;

      case "is_overlay_visible":
        return await _overlayChannel.invokeMethod<bool>(
              "is_overlay_visible",
            ) ??
            false;

      default:
        throw UnsupportedError(
          "Unknown accessibility method: $name",
        );
    }
  }

  Future<dynamic> _handleOverlayAction(MethodCall call) async {
    switch (call.method) {
      case "overlay_swipe":
        final data = Map<String, dynamic>.from(
          call.arguments as Map,
        );

        final direction = data["direction"] as String;
        final speed = (data["speed"] as num).toDouble();

        if (direction != "up" && direction != "down") {
          return false;
        }

        if (speed < 0.5 || speed > 3.0) {
          return false;
        }

        if (!await FlutterAccessibilityService
            .isAccessibilityPermissionEnabled()) {
          return false;
        }

        return await _swipe(
          direction: direction,
          speed: speed,
        );

      default:
        throw UnsupportedError(
          "Unknown overlay action: ${call.method}",
        );
    }
  }

  Future<bool> _swipe({
    required String direction,
    required double speed,
  }) async {
    final views = PlatformDispatcher.instance.views;

    if (views.isEmpty) {
      return false;
    }

    final size = views.first.physicalSize;

    if (size.isEmpty) {
      return false;
    }

    final x = size.width * 0.5;

    final top = size.height * 0.30;
    final bottom = size.height * 0.75;

    // Finger movement is opposite to document movement.
    final startY = direction == "down" ? bottom : top;
    final endY = direction == "down" ? top : bottom;

    final durationMs = (400 / speed).round();

    final gesture = GestureDescription(
      strokes: [
        GestureStroke(
          path: [
            GesturePoint(x, startY),
            GesturePoint(x, endY),
          ],
          startTime: 0,
          duration: durationMs,
        ),
      ],
    );

    return await FlutterAccessibilityService
        .dispatchGesture(gesture);
  }

  @override
  void dispose() {
    control.removeInvokeMethodListener(_invokeMethod);
    _overlayChannel.setMethodCallHandler(null);
    super.dispose();
  }
}
