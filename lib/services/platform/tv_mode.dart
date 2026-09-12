import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:kazumi/services/platform/platform_environment_service.dart';

/// Process-wide Android TV capability detected before the widget tree starts.
class TvMode {
  TvMode._();

  static bool _enabled = false;

  static bool get enabled => _enabled;

  static Future<void> initialize() async {
    _enabled =
        Platform.isAndroid && await PlatformEnvironmentService.isTelevision();
  }

  @visibleForTesting
  static void setEnabledForTesting(bool value) {
    _enabled = value;
  }
}
