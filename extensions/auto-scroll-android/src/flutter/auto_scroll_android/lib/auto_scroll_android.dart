import 'package:flet/flet.dart';
import 'package:flutter/services.dart';


class Extension extends FletExtension {
  @override
  FletService? createService(Control control) {
    if (control.type == 'AndroidAccessibility') {
      return AndroidAccessibilityService(control: control);
    }
    return null;
  }
}


class AndroidAccessibilityService extends FletService {
  AndroidAccessibilityService({required super.control});

  static const MethodChannel _channel = MethodChannel(
    'auto_scroll_android/overlay',
  );

  @override
  void init() {
    super.init();
    control.addInvokeMethodListener(_invokeMethod);
  }

  Future<dynamic> _invokeMethod(String name, dynamic args) async {
    switch (name) {
      case 'get_state':
        final response = await _channel.invokeMethod<Map>(
          'get_state',
        );

        return Map<String, dynamic>.from(
          response ?? {},
        );

      case 'request_permission':
        return await _channel.invokeMethod<bool>(
              'request_permission',
            ) ??
            false;

      case 'show_overlay':
        return await _channel.invokeMethod<bool>(
              'show_overlay',
            ) ??
            false;

      case 'hide_overlay':
        return await _channel.invokeMethod<bool>(
              'hide_overlay',
            ) ??
            false;

      case 'set_config':
        return await _channel.invokeMethod<bool>(
              'set_config',
              args,
            ) ??
            false;

      default:
        throw UnsupportedError(
          'Unknown Android method: $name',
        );
    }
  }

  @override
  void dispose() {
    control.removeInvokeMethodListener(_invokeMethod);
    super.dispose();
  }
}
