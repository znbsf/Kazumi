import 'dart:math';
import 'package:kazumi/request/apis/bangumi_api.dart';
import 'package:kazumi/modules/bangumi/bangumi_item.dart';
import 'package:kazumi/services/storage/storage.dart';
import 'package:mobx/mobx.dart';

part 'popular_controller.g.dart';

class PopularController = _PopularController with _$PopularController;

abstract class _PopularController with Store {
  static const int _trendPageSize = 24;

  int _trendOffset = 0;
  int _tagQueryGeneration = 0;
  int _activeLoadingRequests = 0;
  // Small session cache of metadata only; images keep their own cache.
  final _tagCache = <(bool, String), (DateTime, List<BangumiItem>)>{};
  static const _tagCacheLifetime = Duration(minutes: 10);
  static const _maxCachedTags = 8;
  static const _maxCachedItems = 240;

  @observable
  String currentTag = '';

  @observable
  ObservableList<BangumiItem> bangumiList = ObservableList.of([]);

  @observable
  ObservableList<BangumiItem> trendList = ObservableList.of([]);

  double scrollOffset = 0.0;

  @observable
  bool isLoadingMore = false;

  @observable
  bool isTimeOut = false;

  bool get _bangumiMirrorEnabled =>
      GStorage.getSetting(SettingsKeys.enableBangumiProxy);

  void _beginLoading() {
    _activeLoadingRequests += 1;
    isLoadingMore = true;
  }

  void _endLoading() {
    if (_activeLoadingRequests > 0) {
      _activeLoadingRequests -= 1;
    }
    isLoadingMore = _activeLoadingRequests > 0;
  }

  void setCurrentTag(String s) {
    if (s != currentTag) _tagQueryGeneration += 1;
    currentTag = s;
  }

  void clearBangumiList() {
    bangumiList.clear();
  }

  // Async actions commit each segment between awaits as one transaction,
  // batching the completion writes into a single notification.
  @action
  Future<void> queryBangumiByTrend({String type = 'add'}) async {
    if (type == 'init') {
      trendList.clear();
      _trendOffset = 0;
    }
    _beginLoading();
    try {
      final result = _bangumiMirrorEnabled
          ? await BangumiApi.getBangumiMirrorPopularSubjects(
              limit: _trendPageSize,
              offset: _trendOffset,
            )
          : await BangumiApi.getBangumiTrendsList(
              limit: _trendPageSize,
              offset: _trendOffset,
            );
      if (result.isNotEmpty) {
        _trendOffset += _trendPageSize;
      }
      final existingIds = trendList.map((item) => item.id).toSet();
      trendList.addAll(result.where((item) => existingIds.add(item.id)));
      isTimeOut = trendList.isEmpty;
    } finally {
      _endLoading();
    }
  }

  @action
  Future<void> queryBangumiByTag({String type = 'add'}) async {
    final cacheKey = (_bangumiMirrorEnabled, currentTag);
    if (type == 'init') {
      _tagQueryGeneration += 1;
      final cached = _tagCache.remove(cacheKey);
      if (cached != null &&
          DateTime.now().difference(cached.$1) < _tagCacheLifetime) {
        _tagCache[cacheKey] = cached;
        bangumiList = ObservableList.of(cached.$2);
        isTimeOut = false;
        return;
      }
      bangumiList.clear();
    }
    final requestGeneration = _tagQueryGeneration;
    _beginLoading();
    var tag = currentTag;
    try {
      var result = _bangumiMirrorEnabled
          ? await BangumiApi.getBangumiMirrorPopularSubjects(
              tag: tag,
              offset: bangumiList.length,
            )
          : await BangumiApi.getBangumiList(
              rank: Random().nextInt(8000) + 1,
              tag: tag,
            );
      if (requestGeneration != _tagQueryGeneration || tag != currentTag) {
        return;
      }
      bangumiList.addAll(result);
      isTimeOut = bangumiList.isEmpty;
      if (bangumiList.isNotEmpty) {
        _tagCache.remove(cacheKey);
        _tagCache[cacheKey] = (
          DateTime.now(),
          bangumiList.take(_maxCachedItems).toList(growable: false),
        );
        while (_tagCache.length > _maxCachedTags) {
          _tagCache.remove(_tagCache.keys.first);
        }
      }
    } finally {
      _endLoading();
    }
  }
}
