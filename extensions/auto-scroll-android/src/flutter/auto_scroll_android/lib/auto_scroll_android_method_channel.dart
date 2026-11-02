import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'auto_scroll_android_platform_interface.dart';

/// An implementation of [AutoScrollAndroidPlatform] that uses method channels.
class MethodChannelAutoScrollAndroid extends AutoScrollAndroidPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('auto_scroll_android');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }
}
