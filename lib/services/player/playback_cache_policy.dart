import 'dart:async';

import 'package:kazumi/services/logging/logger.dart';
import 'package:kazumi/services/network/metered_network_service.dart';
import 'package:kazumi/services/storage/storage.dart';
import 'package:kazumi/services/platform/tv_mode.dart';
import 'package:kazumi/utils/async_serial_queue.dart';
import 'package:media_kit/media_kit.dart';

/// Owns the demuxer cache size of the player returned by [currentPlayer].
///
/// [PlayerConfiguration] seeds the size so a failed write degrades to the right
/// value instead of the media_kit default; [apply] is authoritative.
class PlaybackCachePolicy {
  PlaybackCachePolicy({
    required this.isLocalPlayback,
    required this.currentPlayer,
  });

  static const int _lowMemoryBufferSize = 2 * 1024 * 1024;
  static const int _defaultBufferSize = 1500 * 1024 * 1024;

  final bool Function() isLocalPlayback;
  final Player? Function() currentPlayer;
  final AsyncSerialQueue _writes = AsyncSerialQueue();

  bool _watching = false;

  bool get _userEnabled =>
      GStorage.getSetting<bool>(SettingsKeys.lowMemoryMode);

  /// Temporary override; local playback is exempt because it costs no data.
  bool get networkForced =>
      !_userEnabled && !isLocalPlayback() && MeteredNetworkService.isMetered;

  /// Limits compressed packet caching, not total process or decoder memory.
  static int cacheBytes({
    required bool television,
    required bool lowMemory,
    required bool metered,
    bool backward = false,
  }) {
    if (metered) return _lowMemoryBufferSize;
    if (television) {
      final mib = backward ? (lowMemory ? 16 : 64) : (lowMemory ? 64 : 256);
      return mib * 1024 * 1024;
    }
    return lowMemory ? _lowMemoryBufferSize : _defaultBufferSize;
  }

  int _cacheSize({bool backward = false}) => cacheBytes(
        television: TvMode.enabled,
        lowMemory: _userEnabled,
        metered: !isLocalPlayback() && MeteredNetworkService.isMetered,
        backward: backward,
      );

  int get bufferSize => _cacheSize();

  void startWatching() {
    if (_watching) {
      return;
    }
    _watching = true;
    MeteredNetworkService.listenable.addListener(_onNetworkChanged);
  }

  void stopWatching() {
    if (!_watching) {
      return;
    }
    _watching = false;
    MeteredNetworkService.listenable.removeListener(_onNetworkChanged);
  }

  /// Waits for initialization so the seed cannot land after this write. That
  /// wait stays outside the queue, where an unresolved one would stall every
  /// later write.
  Future<void> apply() async {
    final player = currentPlayer();
    if (player == null) {
      return;
    }
    try {
      final pp = player.platform as NativePlayer;
      await pp.waitForPlayerInitialization;
      await _writes.run(() async {
        if (!identical(currentPlayer(), player)) {
          return;
        }
        final size = bufferSize.toString();
        await pp.setProperty('demuxer-max-bytes', size);
        await pp.setProperty(
            'demuxer-max-back-bytes', _cacheSize(backward: true).toString());
      });
    } catch (e) {
      KazumiLogger().w(
        'PlaybackCachePolicy: failed to apply demuxer cache size',
        error: e,
      );
    }
  }

  void _onNetworkChanged() {
    unawaited(apply());
  }
}
