import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'auto_scroll_android_method_channel.dart';

abstract class AutoScrollAndroidPlatform extends PlatformInterface {
  /// Constructs a AutoScrollAndroidPlatform.
  AutoScrollAndroidPlatform() : super(token: _token);

  static final Object _token = Object();

  static AutoScrollAndroidPlatform _instance = MethodChannelAutoScrollAndroid();

  /// The default instance of [AutoScrollAndroidPlatform] to use.
  ///
  /// Defaults to [MethodChannelAutoScrollAndroid].
  static AutoScrollAndroidPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [AutoScrollAndroidPlatform] when
  /// they register themselves.
  static set instance(AutoScrollAndroidPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
