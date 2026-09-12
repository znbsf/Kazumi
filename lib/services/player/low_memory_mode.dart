import 'package:kazumi/services/storage/storage.dart';
import 'package:kazumi/services/platform/tv_mode.dart';

enum LowMemoryMode {
  auto('auto'),
  always('always'),
  never('never');

  const LowMemoryMode(this._storageValue);

  final String _storageValue;

  static LowMemoryMode get current {
    // TV exposes an on/off choice. Preserve an explicit off preference while
    // mapping a previously saved automatic policy to the TV default: on.
    if (TvMode.enabled) {
      return GStorage.getSetting(SettingsKeys.lowMemoryPolicy) == 'never'
          ? never
          : always;
    }
    return switch (GStorage.getSetting(SettingsKeys.lowMemoryPolicy)) {
      'always' => always,
      'never' => never,
      null => GStorage.getSetting(SettingsKeys.lowMemoryMode) ? always : auto,
      _ => auto,
    };
  }

  static Stream<void> watch() => GStorage.watchSettings([
        SettingsKeys.lowMemoryPolicy,
        SettingsKeys.lowMemoryMode,
      ]);

  Future<void> save() =>
      GStorage.putSetting(SettingsKeys.lowMemoryPolicy, _storageValue);

  bool isEnabled({required bool isMetered, bool isLocalPlayback = false}) {
    return switch (this) {
      auto => isMetered && !isLocalPlayback,
      always => true,
      never => false,
    };
  }
}
