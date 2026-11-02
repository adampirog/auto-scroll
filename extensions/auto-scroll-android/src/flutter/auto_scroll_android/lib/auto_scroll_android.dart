import 'dart:async';

import 'package:flet/flet.dart';
import 'package:flutter/services.dart';


class Extension extends FletExtension {
  @override
  FletService? createService(Control control) {
    if (control.type == "AndroidAccessibility") {
      return AndroidAccessibilityService(control: control);
    }
    return null;
  }
}


class AndroidAccessibilityService extends FletService {
  AndroidAccessibilityService({required super.control});

  static const MethodChannel _nativeChannel = MethodChannel(
    'auto_scroll_android/overlay',
  );

  bool _running = false;
  int _generation = 0;
  double _speed = 1.0;
  String _direction = 'down';
  Future<void>? _scrollTask;

  @override
  void init() {
    super.init();
    control.addInvokeMethodListener(_invokeMethod);
    _nativeChannel.setMethodCallHandler(_handleNativeAction);
  }

  Future<dynamic> _invokeMethod(String name, dynamic args) async {
    switch (name) {
      case 'is_enabled':
        return await _nativeChannel.invokeMethod<bool>(
              'is_enabled',
            ) ??
            false;

      case 'request_permission':
        return await _nativeChannel.invokeMethod<bool>(
              'request_permission',
            ) ??
            false;

      case 'overlay_permission_granted':
        return await _nativeChannel.invokeMethod<bool>(
              'overlay_permission_granted',
            ) ??
            false;

      case 'request_overlay_permission':
        return await _nativeChannel.invokeMethod<bool>(
              'request_overlay_permission',
            ) ??
            false;

      case 'show_overlay':
        return await _nativeChannel.invokeMethod<bool>(
              'show_overlay',
            ) ??
            false;

      case 'hide_overlay':
        _stopScrolling();
        return await _nativeChannel.invokeMethod<bool>(
              'hide_overlay',
            ) ??
            false;

      case 'is_overlay_visible':
        return await _nativeChannel.invokeMethod<bool>(
              'is_overlay_visible',
            ) ??
            false;

      default:
        throw UnsupportedError(
          'Unknown Android method: $name',
        );
    }
  }

  Future<dynamic> _handleNativeAction(MethodCall call) async {
    switch (call.method) {
      case 'scroll_start':
        _updateConfig(call.arguments);
        return await _startScrolling();

      case 'scroll_stop':
        _stopScrolling();
        return true;

      case 'scroll_config':
        _updateConfig(call.arguments);
        return true;

      default:
        throw UnsupportedError(
          'Unknown native action: ${call.method}',
        );
    }
  }

  void _updateConfig(dynamic arguments) {
    final data = Map<String, dynamic>.from(
      arguments as Map,
    );

    final direction = data['direction'] as String;
    final speed = (data['speed'] as num).toDouble();

    if (direction != 'up' && direction != 'down') {
      throw ArgumentError('Invalid scrolling direction');
    }

    if (!speed.isFinite || speed < 0.5 || speed > 5.0) {
      throw ArgumentError('Invalid scrolling speed');
    }

    _direction = direction;
    _speed = speed;
  }

  Future<bool> _startScrolling() async {
    if (_running) {
      return true;
    }

    final generation = ++_generation;

    final enabled = await _nativeChannel.invokeMethod<bool>(
          'is_enabled',
        ) ??
        false;

    if (!enabled || generation != _generation) {
      return false;
    }

    _running = true;

    final previousTask = _scrollTask;

    if (previousTask != null) {
      await previousTask;
    }

    if (!_running || generation != _generation) {
      return false;
    }

    _scrollTask = _scrollLoop(generation);
    return true;
  }

  void _stopScrolling() {
    _running = false;
    _generation++;
  }

  Future<void> _scrollLoop(int generation) async {
    var failed = false;

    try {
      while (_running && generation == _generation) {
        final completed = await _nativeChannel.invokeMethod<bool>(
              'dispatch_swipe',
              {
                'direction': _direction,
                'speed': _speed,
              },
            ) ??
            false;

        if (!_running || generation != _generation) {
          break;
        }

        if (!completed) {
          failed = true;
          break;
        }

        final delayMs = (170 / _speed)
            .round()
            .clamp(40, 340)
            .toInt();

        await Future<void>.delayed(
          Duration(milliseconds: delayMs),
        );
      }
    } catch (_) {
      failed = true;
    } finally {
      if (generation == _generation) {
        _running = false;
        _scrollTask = null;

        if (failed) {
          try {
            await _nativeChannel.invokeMethod<bool>(
              'scroll_finished',
            );
          } catch (_) {
            // The floating window might already be closed.
          }
        }
      }
    }
  }

  @override
  void dispose() {
    _stopScrolling();
    control.removeInvokeMethodListener(_invokeMethod);
    _nativeChannel.setMethodCallHandler(null);
    super.dispose();
  }
}

