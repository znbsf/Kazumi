import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_modular/flutter_modular.dart';
import 'package:kazumi/bean/widget/bangumi_mirror_error_widget.dart';
import 'package:kazumi/bean/widget/custom_dropdown_menu.dart';
import 'package:kazumi/bean/widget/tv_focusable_surface.dart';
import 'package:kazumi/modules/bangumi/bangumi_item.dart';
import 'package:kazumi/pages/popular/popular_controller.dart';
import 'package:kazumi/bean/card/bangumi_card.dart';
import 'package:kazumi/utils/constants.dart';
import 'package:flutter_mobx/flutter_mobx.dart';
import 'package:window_manager/window_manager.dart';
import 'package:kazumi/services/logging/logger.dart';
import 'package:kazumi/services/storage/storage.dart';
import 'package:kazumi/bean/appbar/drag_to_move_bar.dart' as dtb;
import 'package:kazumi/utils/device.dart';
import 'package:kazumi/bean/dialog/dialog_helper.dart';
import 'package:kazumi/services/platform/tv_channel_input.dart';
import 'package:kazumi/services/platform/tv_mode.dart';

class PopularPage extends StatefulWidget {
  const PopularPage({
    super.key,
    required this.controller,
  });

  final PopularController controller;

  @override
  State<PopularPage> createState() => _PopularPageState();
}

class _PopularPageState extends State<PopularPage> {
  static const _channelInputDelay = Duration(milliseconds: 1800);
  static const _channelLookupTimeout = Duration(seconds: 10);
  static const _channelLookupPageLimit = 5;

  late final ScrollController scrollController;
  PopularController get popularController => widget.controller;

  // Key used to position the dropdown menu for the tag selector
  final GlobalKey selectorKey = GlobalKey();
  final Map<int, FocusNode> _channelFocusNodes = {};
  final Map<String, FocusNode> _tagFocusNodes = {};
  Timer? _tagSelectionTimer;
  Timer? _channelCommitTimer;
  Future<void>? _channelLookupFuture;
  String _channelInput = '';
  int? _channelCandidate;
  bool _channelSearching = false;
  bool _channelLookupFailed = false;
  bool _channelLookupLimited = false;

  @override
  void initState() {
    super.initState();
    scrollController = ScrollController(
      initialScrollOffset: popularController.scrollOffset,
    );
    scrollController.addListener(scrollListener);
    tvChannelInputController.addListener(_handleChannelDigit);
    if (popularController.trendList.isEmpty) {
      popularController.queryBangumiByTrend();
    }
  }

  @override
  void dispose() {
    scrollController.removeListener(scrollListener);
    tvChannelInputController.removeListener(_handleChannelDigit);
    _tagSelectionTimer?.cancel();
    _channelCommitTimer?.cancel();
    for (final node in _channelFocusNodes.values) {
      node.dispose();
    }
    for (final node in _tagFocusNodes.values) {
      node.dispose();
    }
    scrollController.dispose();
    super.dispose();
  }

  void scrollListener() {
    popularController.scrollOffset = scrollController.offset;
    if (scrollController.position.pixels >=
            scrollController.position.maxScrollExtent - 200 &&
        !popularController.isLoadingMore) {
      KazumiLogger()
          .i('PopularPageController: Fetching next recommendation batch');
      if (popularController.currentTag != '') {
        popularController.queryBangumiByTag();
      } else {
        popularController.queryBangumiByTrend();
      }
    }
  }

  List<BangumiItem> get _visibleBangumiList =>
      popularController.currentTag == ''
          ? popularController.trendList
          : popularController.bangumiList;

  FocusNode _focusNodeForChannel(int channelNumber) =>
      _channelFocusNodes.putIfAbsent(
        channelNumber,
        () => FocusNode(debugLabel: 'TV channel $channelNumber'),
      );

  FocusNode _focusNodeForTag(String tag) => _tagFocusNodes.putIfAbsent(
        tag,
        () =>
            FocusNode(debugLabel: 'TV category ${tag.isEmpty ? '热门番组' : tag}'),
      );

  void _handleChannelDigit() {
    final event = tvChannelInputController.value;
    if (!mounted || !TvMode.enabled) return;
    if (event == null) {
      if (_channelInput.isNotEmpty || _channelSearching) {
        _clearChannelInput(notifyController: false);
      }
      return;
    }

    final nextInput = _channelInput.length >= 3
        ? '${event.digit}'
        : '$_channelInput${event.digit}';
    final channelNumber = int.tryParse(nextInput) ?? 0;
    _channelCommitTimer?.cancel();
    setState(() {
      _channelInput = nextInput;
      _channelCandidate = channelNumber;
      _channelSearching = channelNumber > _visibleBangumiList.length;
      _channelLookupFailed = false;
      _channelLookupLimited = false;
    });
    _channelLookupFuture = _previewChannel(channelNumber, nextInput);
    unawaited(_channelLookupFuture);
    _channelCommitTimer = Timer(
      _channelInputDelay,
      () => unawaited(_commitChannel(nextInput)),
    );
  }

  Future<void> _previewChannel(int channelNumber, String expectedInput) async {
    await _ensureChannelAvailable(channelNumber, expectedInput);
    if (!mounted || _channelInput != expectedInput) return;
    setState(() => _channelSearching = false);
    final itemCount = _visibleBangumiList.length;
    if (channelNumber < 1 || channelNumber > itemCount) {
      await _scrollToListEnd();
      return;
    }

    await _scrollToChannel(channelNumber);
    if (!mounted || _channelInput != expectedInput) return;
    _focusNodeForChannel(channelNumber).requestFocus();
  }

  Future<void> _ensureChannelAvailable(
    int channelNumber,
    String expectedInput,
  ) async {
    final deadline = DateTime.now().add(_channelLookupTimeout);
    var loadedPages = 0;
    while (mounted &&
        _channelInput == expectedInput &&
        channelNumber > _visibleBangumiList.length) {
      if (loadedPages >= _channelLookupPageLimit ||
          !DateTime.now().isBefore(deadline)) {
        _channelLookupLimited = true;
        break;
      }
      if (popularController.isLoadingMore) {
        await Future<void>.delayed(const Duration(milliseconds: 80));
        continue;
      }

      final previousCount = _visibleBangumiList.length;
      try {
        final remaining = deadline.difference(DateTime.now());
        if (remaining <= Duration.zero) {
          _channelLookupLimited = true;
          break;
        }
        if (popularController.currentTag == '') {
          await popularController.queryBangumiByTrend().timeout(remaining);
        } else {
          await popularController.queryBangumiByTag().timeout(remaining);
        }
        loadedPages += 1;
      } on TimeoutException {
        if (mounted && _channelInput == expectedInput) {
          _channelLookupLimited = true;
        }
        break;
      } catch (error, stackTrace) {
        KazumiLogger().e(
          'PopularPage: channel number lookup failed',
          error: error,
          stackTrace: stackTrace,
        );
        if (mounted && _channelInput == expectedInput) {
          _channelLookupFailed = true;
        }
        break;
      }
      if (_visibleBangumiList.length <= previousCount) {
        break;
      }
    }
  }

  Future<void> _commitChannel(String expectedInput) async {
    if (!mounted || _channelInput != expectedInput) return;
    await _channelLookupFuture;
    if (!mounted || _channelInput != expectedInput) return;
    final channelNumber = int.tryParse(expectedInput) ?? 0;
    final items = _visibleBangumiList;
    if (channelNumber >= 1 && channelNumber <= items.length) {
      _clearChannelInput();
      context.pushNamed('/info/', arguments: items[channelNumber - 1]);
      return;
    }

    if (_channelLookupFailed) {
      _clearChannelInput();
      KazumiDialog.showToast(
        message: '加载更多节目失败，无法确认 $channelNumber 号是否存在',
        context: context,
      );
      return;
    }
    if (_channelLookupLimited) {
      await _scrollToListEnd();
      if (!mounted) return;
      final currentCount = _visibleBangumiList.length;
      _clearChannelInput();
      KazumiDialog.showToast(
        message: '暂未加载到 $channelNumber 号，已在 5 页/10 秒处停止（当前 $currentCount 个）',
        context: context,
      );
      return;
    }

    await _scrollToListEnd();
    if (!mounted) return;
    final currentCount = _visibleBangumiList.length;
    _clearChannelInput();
    KazumiDialog.showToast(
      message: currentCount == 0
          ? '当前没有可选节目'
          : '没有 $channelNumber 号节目，已到列表末尾（当前 $currentCount 个）',
      context: context,
    );
  }

  void _clearChannelInput({bool notifyController = true}) {
    _channelCommitTimer?.cancel();
    _channelCommitTimer = null;
    if (mounted) {
      setState(() {
        _channelInput = '';
        _channelCandidate = null;
        _channelSearching = false;
        _channelLookupFailed = false;
        _channelLookupLimited = false;
      });
    }
    if (notifyController && tvChannelInputController.value != null) {
      tvChannelInputController.cancel();
    }
  }

  void _openChannel(BangumiItem item) {
    _clearChannelInput();
    context.pushNamed('/info/', arguments: item);
  }

  int _gridCrossCount() {
    var crossCount = 3;
    final width = MediaQuery.sizeOf(context).width;
    if (width > LayoutBreakpoint.compact['width']!) {
      crossCount = 5;
    }
    if (width > LayoutBreakpoint.medium['width']!) {
      crossCount = 6;
    }
    return crossCount;
  }

  double _gridItemExtent(int crossCount) =>
      MediaQuery.sizeOf(context).width / crossCount / 0.65 +
      MediaQuery.textScalerOf(context).scale(32.0);

  Future<void> _scrollToChannel(int channelNumber) async {
    if (!scrollController.hasClients) return;
    final crossCount = _gridCrossCount();
    final row = (channelNumber - 1) ~/ crossCount;
    final target =
        (row * (_gridItemExtent(crossCount) + StyleString.cardSpace - 2))
            .clamp(0.0, scrollController.position.maxScrollExtent);
    await scrollController.animateTo(
      target,
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOutCubic,
    );
    if (!mounted) return;
    await WidgetsBinding.instance.endOfFrame;
  }

  Future<void> _scrollToListEnd() async {
    if (!scrollController.hasClients) return;
    await scrollController.animateTo(
      scrollController.position.maxScrollExtent,
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeOutCubic,
    );
  }

  bool showWindowButton() {
    return GStorage.getSetting(SettingsKeys.showWindowButton);
  }

  void _scheduleTvTagSelection(String tag) {
    _tagSelectionTimer?.cancel();
    _tagSelectionTimer = Timer(const Duration(milliseconds: 280), () {
      unawaited(_selectTag(tag));
    });
  }

  Future<void> _selectTag(String tag) async {
    _tagSelectionTimer?.cancel();
    if (tag == popularController.currentTag) return;
    tvChannelInputController.cancel();
    if (scrollController.hasClients) {
      unawaited(scrollController.animateTo(
        0,
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOut,
      ));
    }
    popularController.setCurrentTag(tag);
    if (tag.isEmpty) {
      popularController.clearBangumiList();
      if (popularController.trendList.isEmpty) {
        await popularController.queryBangumiByTrend();
      }
      return;
    }
    await popularController.queryBangumiByTag(type: 'init');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          CustomScrollView(
            controller: scrollController,
            slivers: [
              buildSliverAppBar(),
              SliverToBoxAdapter(
                child: Observer(
                  builder: (_) => AnimatedOpacity(
                    opacity: popularController.isLoadingMore ? 1.0 : 0.0,
                    duration: const Duration(milliseconds: 300),
                    child: popularController.isLoadingMore
                        ? const LinearProgressIndicator(minHeight: 4)
                        : const SizedBox(height: 4),
                  ),
                ),
              ),
              SliverPadding(
                  padding: const EdgeInsets.fromLTRB(
                      StyleString.cardSpace, 0, StyleString.cardSpace, 0),
                  sliver: Observer(builder: (_) {
                    if (popularController.isTimeOut) {
                      return SliverToBoxAdapter(
                        child: SizedBox(
                          height: 400,
                          child: BangumiMirrorErrorWidget(
                            onRetry: () {
                              if (popularController.trendList.isEmpty) {
                                popularController.queryBangumiByTrend();
                              } else {
                                popularController.queryBangumiByTag();
                              }
                            },
                            onSettingsReturned: () {
                              if (mounted) {
                                setState(() {});
                              }
                            },
                          ),
                        ),
                      );
                    }
                    return contentGrid(
                      (popularController.currentTag == '')
                          ? popularController.trendList
                          : popularController.bangumiList,
                    );
                  })),
            ],
          ),
          if (TvMode.enabled && _channelInput.isNotEmpty)
            _buildChannelInputOverlay(),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => scrollController.animateTo(0,
            duration: const Duration(milliseconds: 350), curve: Curves.easeOut),
        child: const Icon(Icons.arrow_upward),
      ),
    );
  }

  Widget contentGrid(List<BangumiItem> bangumiList) {
    final crossCount = _gridCrossCount();
    return SliverPadding(
      padding: const EdgeInsets.all(8),
      sliver: SliverGrid(
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          // 行间距
          mainAxisSpacing: StyleString.cardSpace - 2,
          // 列间距
          crossAxisSpacing: StyleString.cardSpace,
          // 列数
          crossAxisCount: crossCount,
          mainAxisExtent: _gridItemExtent(crossCount),
        ),
        delegate: SliverChildBuilderDelegate(
          (BuildContext context, int index) {
            final channelNumber = index + 1;
            return bangumiList.isNotEmpty
                ? BangumiCardV(
                    bangumiItem: bangumiList[index],
                    channelNumber: TvMode.enabled ? channelNumber : null,
                    highlighted: _channelCandidate == channelNumber,
                    focusNode: TvMode.enabled
                        ? _focusNodeForChannel(channelNumber)
                        : null,
                    onPressed: TvMode.enabled
                        ? () => _openChannel(bangumiList[index])
                        : null,
                  )
                : null;
          },
          childCount: bangumiList.isNotEmpty ? bangumiList.length : 10,
        ),
      ),
    );
  }

  Widget _buildChannelInputOverlay() {
    final theme = Theme.of(context);
    final itemCount = _visibleBangumiList.length;
    final channelNumber = int.tryParse(_channelInput) ?? 0;
    final found = channelNumber >= 1 && channelNumber <= itemCount;
    final colorScheme = theme.colorScheme;
    return Positioned(
      top: 18,
      left: 0,
      right: 0,
      child: IgnorePointer(
        child: Center(
          child: AnimatedContainer(
            key: const Key('tv-channel-input-overlay'),
            duration: const Duration(milliseconds: 120),
            padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 12),
            decoration: BoxDecoration(
              color: (found || _channelSearching) &&
                      !_channelLookupFailed &&
                      !_channelLookupLimited
                  ? colorScheme.inverseSurface.withValues(alpha: 0.94)
                  : colorScheme.errorContainer.withValues(alpha: 0.96),
              borderRadius: BorderRadius.circular(14),
              boxShadow: const [
                BoxShadow(color: Colors.black38, blurRadius: 16),
              ],
            ),
            child: Text(
              _channelLookupFailed
                  ? '无法加载 $_channelInput 号节目'
                  : _channelLookupLimited
                      ? '暂未加载到 $_channelInput 号'
                      : _channelSearching
                          ? '正在查找 $_channelInput 号…'
                          : found
                              ? '转到 $_channelInput 号…'
                              : '没有 $_channelInput 号节目',
              style: theme.textTheme.headlineSmall?.copyWith(
                color: (found || _channelSearching) &&
                        !_channelLookupFailed &&
                        !_channelLookupLimited
                    ? colorScheme.onInverseSurface
                    : colorScheme.onErrorContainer,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget buildSliverAppBar() {
    final theme = Theme.of(context);
    if (TvMode.enabled) {
      return SliverAppBar(
        pinned: true,
        toolbarHeight: 88,
        elevation: 0,
        titleSpacing: 20,
        backgroundColor: theme.colorScheme.surface,
        actions: buildActions(),
        title: Observer(
          builder: (_) => SizedBox(
            height: 70,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(vertical: 8),
              children: [
                _buildTvCategoryTab('', 0),
                for (var index = 0; index < defaultAnimeTags.length; index++)
                  _buildTvCategoryTab(defaultAnimeTags[index], index + 1),
              ],
            ),
          ),
        ),
      );
    }
    return SliverAppBar(
      pinned: true,
      stretch: true,
      expandedHeight: 120,
      elevation: 0,
      titleSpacing: 0,
      centerTitle: false,
      backgroundColor: Theme.of(context).colorScheme.surface,
      actions: buildActions(),
      title: null,
      flexibleSpace: SafeArea(
        child: dtb.DragToMoveArea(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final double maxExtent = 120 - MediaQuery.of(context).padding.top;
              final t = (1 -
                  ((constraints.maxHeight - kToolbarHeight) /
                          (maxExtent - kToolbarHeight))
                      .clamp(0.0, 1.0));
              // 字重收缩后为 w500，展开时为 w700
              final fontWeight = t < 0.5 ? FontWeight.w700 : FontWeight.w500;
              final fontSize = lerpDouble(28, 20, t)!;
              return Align(
                alignment: Alignment.centerLeft,
                child: Padding(
                  padding: const EdgeInsets.only(
                      left: 16, top: 8, bottom: 8, right: 60),
                  child: SizedBox(
                    height: 44,
                    child: Observer(
                      builder: (_) {
                        final bool isTrend = popularController.currentTag == '';
                        return InkWell(
                          key: selectorKey,
                          borderRadius: BorderRadius.circular(8),
                          onTap: showTagMenu,
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                isTrend ? '热门番组' : popularController.currentTag,
                                style: theme.textTheme.headlineMedium!.copyWith(
                                  fontWeight: fontWeight,
                                  fontSize: fontSize,
                                ),
                              ),
                              const SizedBox(width: 4),
                              Icon(Icons.keyboard_arrow_down,
                                  size: fontSize, color: theme.iconTheme.color),
                            ],
                          ),
                        );
                      },
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildTvCategoryTab(String tag, int index) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final selected = popularController.currentTag == tag;
    final label = tag.isEmpty ? '热门番组' : tag;
    return Padding(
      padding: const EdgeInsets.only(right: 10),
      child: TvFocusableSurface(
        focusNode: _focusNodeForTag(tag),
        borderRadius: 22,
        highlighted: selected,
        onKeyEvent: (node, event) {
          if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
            return KeyEventResult.ignored;
          }
          final tags = ['', ...defaultAnimeTags];
          if (event.logicalKey == LogicalKeyboardKey.arrowLeft && index > 0) {
            _focusNodeForTag(tags[index - 1]).requestFocus();
            return KeyEventResult.handled;
          }
          if (event.logicalKey == LogicalKeyboardKey.arrowRight &&
              index < tags.length - 1) {
            _focusNodeForTag(tags[index + 1]).requestFocus();
            return KeyEventResult.handled;
          }
          return KeyEventResult.ignored;
        },
        onFocusChange: (focused) {
          if (focused) _scheduleTvTagSelection(tag);
        },
        onPressed: () => unawaited(_selectTag(tag)),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          curve: Curves.easeOut,
          alignment: Alignment.center,
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 9),
          decoration: BoxDecoration(
            color: selected
                ? colorScheme.primaryContainer
                : colorScheme.surfaceContainerHigh,
            borderRadius: BorderRadius.circular(20),
          ),
          child: Text(
            label,
            style: theme.textTheme.titleLarge?.copyWith(
              color: selected
                  ? colorScheme.onPrimaryContainer
                  : colorScheme.onSurfaceVariant,
              fontWeight: selected ? FontWeight.w800 : FontWeight.w500,
            ),
          ),
        ),
      ),
    );
  }

  List<Widget> buildActions() {
    final actions = <Widget>[
      if (MediaQuery.of(context).orientation == Orientation.portrait)
        IconButton(
          tooltip: '搜索',
          onPressed: () => context.pushNamed('/search/'),
          icon: const Icon(Icons.search),
        ),
    ];
    if (!TvMode.enabled) {
      actions.add(
        IconButton(
          tooltip: '历史记录',
          onPressed: () => context.pushNamed('/settings/history/'),
          icon: const Icon(Icons.history),
        ),
      );
    }
    if (isDesktop()) {
      if (!showWindowButton()) {
        actions.add(
          IconButton(
            tooltip: '退出',
            onPressed: () => windowManager.close(),
            icon: const Icon(Icons.close),
          ),
        );
      }
    }
    return actions;
  }

  Future<void> showTagMenu() async {
    // Calculate the position of the button manually to position the dropdown menu.
    // Using CustomDropdownMenu instead of PopupMenuButton to avoid flickering issues
    // and to support different font sizes in the button and menu items.
    final RenderBox renderBox =
        selectorKey.currentContext!.findRenderObject() as RenderBox;
    final Offset offset = renderBox.localToGlobal(Offset.zero);
    final Size size = renderBox.size;

    final selected = await Navigator.push<String>(
      context,
      PageRouteBuilder(
        opaque: false,
        barrierDismissible: true,
        barrierColor: Colors.transparent,
        pageBuilder: (context, animation, secondaryAnimation) {
          return CustomDropdownMenu(
            offset: offset,
            buttonSize: size,
            animation: animation,
            maxWidth: 80,
            items: [
              '',
              ...defaultAnimeTags,
            ],
            itemBuilder: (item) => item.isEmpty ? '热门番组' : item,
          );
        },
        transitionDuration: const Duration(milliseconds: 200),
        reverseTransitionDuration: const Duration(milliseconds: 150),
      ),
    );

    if (selected == null) return;
    await _selectTag(selected);
  }
}
