import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_accessibility_service/flutter_accessibility_service.dart';
import 'package:flutter_accessibility_service/gesture_description.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _teal = Color(0xFF0796B8);
const _ink = Color(0xFF152238);
const _muted = Color(0xFF65758B);

const _overlayVisibleKey = 'autoscroll.overlay_visible';


void runAutoScrollOverlay() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AutoScrollOverlay());
}


enum ScrollDirection { up, down }


class AutoScrollOverlay extends StatefulWidget {
  const AutoScrollOverlay({super.key});

  @override
  State<AutoScrollOverlay> createState() => _AutoScrollOverlayState();
}


class _AutoScrollOverlayState extends State<AutoScrollOverlay> {
  final SharedPreferencesAsync _preferences = SharedPreferencesAsync();

  bool _running = false;
  bool _closing = false;

  double _speed = 1.0;
  ScrollDirection _direction = ScrollDirection.down;

  int _generation = 0;
  String _status = 'Ready';

  bool _isCurrent(int generation) {
    return mounted && _running && generation == _generation;
  }

  void _stop([String status = 'Paused']) {
    _generation++;

    if (!mounted) {
      _running = false;
      return;
    }

    setState(() {
      _running = false;
      _status = status;
    });
  }

  Future<void> _toggleScrolling() async {
    if (_running) {
      _stop();
      return;
    }

    if (_closing) {
      return;
    }

    try {
      final visible =
          await _preferences.getBool(_overlayVisibleKey) ?? false;

      if (!visible) {
        _stop('Overlay inactive');
        return;
      }

      final enabled = await FlutterAccessibilityService
          .isAccessibilityPermissionEnabled();

      if (!enabled) {
        _stop('Accessibility disabled');
        return;
      }
    } catch (_) {
      _stop('Unable to start');
      return;
    }

    if (!mounted || _closing || _running) {
      return;
    }

    final generation = ++_generation;

    setState(() {
      _running = true;
      _status = 'Scrolling';
    });

    unawaited(_scrollLoop(generation));
  }

  Future<void> _scrollLoop(int generation) async {
    while (_isCurrent(generation)) {
      try {
        // Check whether the main Flet app has hidden the overlay.
        final visible =
            await _preferences.getBool(_overlayVisibleKey) ?? false;

        if (!_isCurrent(generation)) {
          return;
        }

        if (!visible) {
          _stop('Stopped');
          return;
        }

        final completed = await _dispatchSwipe();

        if (!_isCurrent(generation)) {
          return;
        }

        if (!completed) {
          _stop('Gesture failed');
          return;
        }

        final intervalMs = (170 / _speed)
            .round()
            .clamp(80, 300)
            .toInt();

        await Future<void>.delayed(
          Duration(milliseconds: intervalMs),
        );
      } catch (_) {
        if (_isCurrent(generation)) {
          _stop('Scrolling error');
        }
        return;
      }
    }
  }

  Future<bool> _dispatchSwipe() async {
    // The overlay has its own small FlutterView.
    // Display.size gives us the full physical screen size.
    final displays = PlatformDispatcher.instance.displays;

    if (displays.isEmpty) {
      return false;
    }

    final size = displays.first.size;

    if (size.width <= 0 || size.height <= 0) {
      return false;
    }

    final x = size.width * 0.5;

    final isDown = _direction == ScrollDirection.down;

    final startY = size.height * (isDown ? 0.72 : 0.33);
    final endY = size.height * (isDown ? 0.33 : 0.72);

    final durationMs = (420 / _speed)
        .round()
        .clamp(150, 750)
        .toInt();

    final gesture = GestureDescription(
      strokes: [
        GestureStroke(
          path: [
            GesturePoint(x, startY),
            GesturePoint(x, endY),
          ],
          duration: durationMs,
        ),
      ],
    );

    return FlutterAccessibilityService.dispatchGesture(gesture);
  }

  Future<void> _closeOverlay() async {
    if (_closing) {
      return;
    }

    _stop('Stopped');

    setState(() {
      _closing = true;
    });

    try {
      await _preferences.setBool(_overlayVisibleKey, false);
      await FlutterAccessibilityService.hideOverlayWindow();
    } finally {
      if (mounted) {
        setState(() {
          _closing = false;
        });
      }
    }
  }

  void _changeDirection(ScrollDirection direction) {
    setState(() {
      _direction = direction;
    });
  }

  Widget _directionButton(
    ScrollDirection direction,
    IconData icon,
  ) {
    final selected = _direction == direction;

    return IconButton(
      tooltip: direction.name,
      onPressed: () => _changeDirection(direction),
      icon: Icon(icon),
      style: IconButton.styleFrom(
        foregroundColor: selected ? Colors.white : _ink,
        backgroundColor: selected ? _teal : const Color(0xFFE9F7FB),
        minimumSize: const Size(40, 40),
      ),
    );
  }

  @override
  void dispose() {
    _generation++;
    _running = false;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Material(
        color: Colors.transparent,
        child: Center(
          child: Container(
            margin: const EdgeInsets.all(8),
            padding: const EdgeInsets.symmetric(
              horizontal: 14,
              vertical: 10,
            ),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(18),
              boxShadow: const [
                BoxShadow(
                  color: Colors.black26,
                  blurRadius: 12,
                  offset: Offset(0, 3),
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Header
                Row(
                  children: [
                    const Icon(
                      Icons.swap_vert_rounded,
                      color: _teal,
                      size: 20,
                    ),
                    const SizedBox(width: 6),
                    const Expanded(
                      child: Text(
                        'AutoScroll',
                        style: TextStyle(
                          color: _ink,
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: 'Close overlay',
                      onPressed: _closing ? null : _closeOverlay,
                      icon: const Icon(Icons.close_rounded),
                      color: _muted,
                      iconSize: 19,
                      visualDensity: VisualDensity.compact,
                    ),
                  ],
                ),

                const SizedBox(height: 4),

                // Playback and direction
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _directionButton(
                      ScrollDirection.up,
                      Icons.arrow_upward_rounded,
                    ),
                    IconButton(
                      tooltip: _running ? 'Pause' : 'Play',
                      onPressed: _closing ? null : _toggleScrolling,
                      icon: Icon(
                        _running
                            ? Icons.pause_rounded
                            : Icons.play_arrow_rounded,
                      ),
                      iconSize: 30,
                      style: IconButton.styleFrom(
                        foregroundColor: Colors.white,
                        backgroundColor: _teal,
                        minimumSize: const Size(52, 52),
                      ),
                    ),
                    _directionButton(
                      ScrollDirection.down,
                      Icons.arrow_downward_rounded,
                    ),
                  ],
                ),

                const SizedBox(height: 10),

                // Speed readout
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Speed',
                      style: TextStyle(
                        color: _ink,
                        fontSize: 13,
                      ),
                    ),
                    Text(
                      '${_speed.toStringAsFixed(1)}×',
                      style: const TextStyle(
                        color: _ink,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),

                // Speed control
                SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    activeTrackColor: _teal,
                    inactiveTrackColor: const Color(0xFFE3EAF0),
                    thumbColor: _teal,
                    trackHeight: 3,
                    thumbShape: const RoundSliderThumbShape(
                      enabledThumbRadius: 7,
                    ),
                    overlayShape: const RoundSliderOverlayShape(
                      overlayRadius: 14,
                    ),
                  ),
                  child: Slider(
                    value: _speed,
                    min: 0.5,
                    max: 5.0,
                    divisions: 9,
                    onChanged: (value) {
                      setState(() {
                        _speed = value;
                      });
                    },
                  ),
                ),

                Text(
                  _status,
                  style: const TextStyle(
                    color: _muted,
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

