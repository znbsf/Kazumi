import 'dart:io';
import 'dart:ui';
import 'package:kazumi/bean/dialog/adaptive_bottom_sheet.dart';
import 'package:kazumi/bean/dialog/dialog_helper.dart';
import 'package:kazumi/pages/info/rating_review_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_modular/flutter_modular.dart';
import 'package:kazumi/bean/widget/collect_button.dart';
import 'package:kazumi/bean/widget/embedded_native_control_area.dart';
import 'package:kazumi/services/storage/storage.dart';
import 'package:kazumi/pages/info/info_controller.dart';
import 'package:kazumi/bean/card/bangumi_info_card.dart';
import 'package:kazumi/pages/info/source_sheet.dart';
import 'package:kazumi/pages/info/tv_detail_actions.dart';
import 'package:kazumi/plugins/plugins_controller.dart';
import 'package:kazumi/bean/card/network_img_layer.dart';
import 'package:kazumi/services/logging/logger.dart';
import 'package:kazumi/pages/info/info_tabview.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:window_manager/window_manager.dart';
import 'package:flutter_mobx/flutter_mobx.dart';
import 'package:kazumi/modules/bangumi/bangumi_item.dart';
import 'package:kazumi/bean/appbar/drag_to_move_bar.dart' as dtb;
import 'package:kazumi/utils/device.dart';
import 'package:kazumi/services/platform/tv_mode.dart';
import 'package:kazumi/services/platform/tv_navigation.dart';
import 'package:kazumi/repositories/history_repository.dart';
import 'package:kazumi/services/player/history_playback_service.dart';
import 'package:kazumi/services/plugin/rule_engine_models.dart'
    show RuleCancelToken;

class InfoPage extends StatefulWidget {
  const InfoPage({
    super.key,
    required this.inputBangumiItem,
    required this.infoController,
    required this.pluginsController,
  });

  final BangumiItem inputBangumiItem;
  final InfoController infoController;
  final PluginsController pluginsController;

  @override
  State<InfoPage> createState() => _InfoPageState();
}

class _InfoPageState extends State<InfoPage> with TickerProviderStateMixin {
  static const List<String> _infoTabs = <String>[
    '概览',
    '吐槽',
    '角色',
    '关联',
    '制作人员',
  ];
  static const int _commentsTabIndex = 1;
  static const Duration _minimumBangumiInfoLoadingDuration =
      Duration(milliseconds: 600);

  InfoController get infoController => widget.infoController;
  PluginsController get pluginsController => widget.pluginsController;
  late TabController infoTabController;
  late bool showRating;

  bool commentsIsLoading = false;
  bool charactersIsLoading = false;
  bool commentsQueryTimeout = false;
  bool commentsIsEmpty = false;
  bool charactersQueryTimeout = false;
  bool charactersIsEmpty = false;
  bool staffIsLoading = false;
  bool staffQueryTimeout = false;
  bool staffIsEmpty = false;
  bool _showBangumiInfoSkeleton = false;
  int _fabTabIndex = 0;
  bool _startingPlayback = false;
  RuleCancelToken? _playCancelToken;
  final _detailScrollController = ScrollController();
  final _tvPlayFocus = FocusNode(debugLabel: 'TV detail play');
  final _tvBackFocus = FocusNode(debugLabel: 'TV detail back');
  final _tvTabsFocus = FocusNode(debugLabel: 'TV detail tabs');

  void _focusTvPlay() {
    _tvPlayFocus.requestFocus();
    if (_detailScrollController.hasClients) {
      _detailScrollController.animateTo(0,
          duration: const Duration(milliseconds: 180), curve: Curves.easeOut);
    }
  }

  void _focusTvTab() {
    final tabs = _tvTabsFocus.traversalDescendants
        .where((node) => node.canRequestFocus && node.context != null)
        .toList()
      ..sort((a, b) => a.rect.left.compareTo(b.rect.left));
    if (tabs.isEmpty) return;
    final node = tabs[infoTabController.index.clamp(0, tabs.length - 1)];
    node.requestFocus();
    Scrollable.ensureVisible(node.context!,
        duration: const Duration(milliseconds: 180),
        alignmentPolicy: ScrollPositionAlignmentPolicy.keepVisibleAtEnd);
  }

  KeyEventResult _tvTabKey(FocusNode node, KeyEvent event) {
    if (TvMode.enabled &&
        (event is KeyDownEvent || event is KeyRepeatEvent) &&
        event.logicalKey == LogicalKeyboardKey.arrowUp) {
      _focusTvPlay();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  KeyEventResult _tvBackKey(FocusNode node, KeyEvent event) {
    if (TvMode.enabled &&
        (event is KeyDownEvent || event is KeyRepeatEvent) &&
        event.logicalKey == LogicalKeyboardKey.arrowDown) {
      _focusTvPlay();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  Future<void> _startWatching() async {
    if (_startingPlayback) return;
    _startingPlayback = true;
    try {
      if (TvMode.enabled) {
        final histories = inject<IHistoryRepository>().getAllHistories().where(
              (item) => item.bangumiItem.id == infoController.bangumiItem.id,
            );
        if (histories.isNotEmpty) {
          final cancelToken = RuleCancelToken();
          _playCancelToken = cancelToken;
          KazumiDialog.showLoading(msg: '继续观看', onDismiss: cancelToken.cancel);
          final result = await inject<HistoryPlaybackService>().open(
            histories.first,
            cancelToken: cancelToken,
          );
          if (!mounted) return;
          final cancelled = cancelToken.isCancelled;
          if (cancelled) return;
          KazumiDialog.dismiss();
          if (result is HistoryPlaybackReady) {
            context.pushNamed('/video/', arguments: result.args);
            return;
          }
          KazumiDialog.showToast(message: '上次的播放源不可用，请重新选源');
        }
      }
      if (!mounted) return;
      await showAdaptiveBottomSheet<void>(
        backgroundColor: Theme.of(context).scaffoldBackgroundColor,
        context: context,
        builder: (_) => SourceSheet(infoController: infoController),
      );
    } finally {
      _playCancelToken = null;
      _startingPlayback = false;
    }
  }

  BangumiItem get inputBangumiIten => widget.inputBangumiItem;

  bool get _isShowingBangumiInfoSkeleton =>
      infoController.isLoading || _showBangumiInfoSkeleton;

  bool _needsBangumiInfoRefresh(BangumiItem bangumiItem) {
    final votesCount = bangumiItem.votesCount;
    final missingVoteDistribution =
        votesCount.isEmpty || bangumiItem.votes <= 0 || votesCount.length < 10;
    return bangumiItem.summary == '' || missingVoteDistribution;
  }

  Future<void> loadCharacters() async {
    if (charactersIsLoading) return;
    setState(() {
      charactersIsLoading = true;
      charactersQueryTimeout = false;
      charactersIsEmpty = false;
    });
    try {
      await infoController
          .queryBangumiCharactersByID(infoController.bangumiItem.id);
      if (mounted) {
        setState(() {
          charactersIsLoading = false;
          if (infoController.characterList.isEmpty) {
            charactersIsEmpty = true;
          }
        });
      }
    } catch (e) {
      KazumiLogger().e('InfoPage: failed to load characters', error: e);
      if (mounted) {
        setState(() {
          charactersIsLoading = false;
          charactersQueryTimeout = true;
        });
      }
    }
  }

  Future<void> loadStaff() async {
    if (staffIsLoading) return;
    setState(() {
      staffIsLoading = true;
      staffQueryTimeout = false;
      staffIsEmpty = false;
    });
    try {
      await infoController
          .queryBangumiStaffsByID(infoController.bangumiItem.id);
      if (mounted) {
        setState(() {
          staffIsLoading = false;
          if (infoController.staffList.isEmpty) {
            staffIsEmpty = true;
          }
        });
      }
    } catch (e) {
      KazumiLogger().e('InfoPage: failed to load staff', error: e);
      if (mounted) {
        setState(() {
          staffIsLoading = false;
          staffQueryTimeout = true;
        });
      }
    }
  }

  Future<void> loadRelations() async {
    try {
      await infoController
          .queryBangumiRelationsByID(infoController.bangumiItem.id);
    } catch (e) {
      KazumiLogger().e('InfoPage: failed to load relations', error: e);
    }
  }

  Future<void> loadMoreComments({bool loadMore = false}) async {
    if (commentsIsLoading) return;
    setState(() {
      commentsIsLoading = true;
      commentsQueryTimeout = false;
      commentsIsEmpty = false;
    });
    try {
      await infoController.queryBangumiCommentsByID(
          infoController.bangumiItem.id,
          refresh: !loadMore);
      if (mounted) {
        setState(() {
          commentsIsLoading = false;
          if (infoController.commentsList.isEmpty &&
              !(infoController.bangumiItem.interest?.hasReviewContent ??
                  false)) {
            commentsIsEmpty = true;
          }
        });
      }
    } catch (e) {
      KazumiLogger().e('InfoPage: failed to load comments', error: e);
      if (mounted) {
        setState(() {
          commentsIsLoading = false;
          commentsQueryTimeout = true;
        });
      }
    }
  }

  void onBangumiRatingTap() {
    final token =
        GStorage.getSetting(SettingsKeys.bangumiAccessToken).toString().trim();
    if (token.isEmpty) {
      KazumiDialog.showToast(message: '请先在同步设置中绑定你的 Bangumi 配置以发表吐槽');
      return;
    }
    final localType = infoController.collectController
        .getCollectType(infoController.bangumiItem);
    if (localType == 0) {
      KazumiDialog.showToast(message: '请先追番后再发表评价');
      return;
    }
    KazumiDialog.show(
      builder: (context) => RatingReviewDialog(
        bangumiItem: infoController.bangumiItem,
        onSubmit: (data) async {
          final updated =
              await infoController.rateBangumi(data, localType: localType);
          if (updated && mounted) {
            setState(() {});
          }
          return updated;
        },
      ),
    );
  }

  @override
  void initState() {
    super.initState();
    infoController.bangumiItem = inputBangumiIten;
    infoController.characterList.clear();
    infoController.clearComments();
    infoController.staffList.clear();
    infoController.clearRelations();
    infoController.pluginSearchResponseList.clear();
    // Search results can miss rating distribution or summaries, so fill those
    // fields without replacing image URLs that are already rendered.
    if (_needsBangumiInfoRefresh(infoController.bangumiItem)) {
      _showBangumiInfoSkeleton = true;
      queryBangumiInfoByID(
        infoController.bangumiItem.id,
        type: 'attach',
        enforceMinimumLoadingDuration: true,
      );
    }
    infoTabController = TabController(length: _infoTabs.length, vsync: this);
    _fabTabIndex = infoTabController.index;
    showRating = GStorage.getSetting(SettingsKeys.showRating);
    infoTabController.addListener(onInfoTabChanged);
    infoTabController.addListener(_syncFabTabIndex);
    infoTabController.animation?.addListener(_syncFabTabIndex);
  }

  void onInfoTabChanged() {
    final index = infoTabController.index;
    if (index == 2 &&
        infoController.characterList.isEmpty &&
        !charactersIsLoading &&
        !charactersIsEmpty &&
        !charactersQueryTimeout) {
      loadCharacters();
    }
    if (index == 3 && infoController.canLoadRelations) {
      loadRelations();
    }
    if (index == 4 &&
        infoController.staffList.isEmpty &&
        !staffIsLoading &&
        !staffIsEmpty &&
        !staffQueryTimeout) {
      loadStaff();
    }
  }

  void _syncFabTabIndex() {
    final animation = infoTabController.animation;
    final targetIndex = infoTabController.indexIsChanging
        ? infoTabController.index
        : (animation?.value.round() ?? infoTabController.index);
    final nextIndex =
        targetIndex.clamp(0, infoTabController.length - 1).toInt();

    if (_fabTabIndex == nextIndex) {
      return;
    }
    if (!mounted) {
      return;
    }
    setState(() {
      _fabTabIndex = nextIndex;
    });
  }

  Future<void> onCommentsTabSelected() async {
    final interest = infoController.bangumiItem.interest;
    final token =
        GStorage.getSetting(SettingsKeys.bangumiAccessToken).toString().trim();
    if (interest != null && token.isNotEmpty) {
      final updated = await infoController.fillInterestUserProfileIfNeeded();
      if (updated && mounted) {
        setState(() {});
      }
    }
    if (infoController.commentsList.isEmpty &&
        !commentsIsLoading &&
        !commentsIsEmpty &&
        !commentsQueryTimeout) {
      loadMoreComments();
    }
  }

  @override
  void dispose() {
    _playCancelToken?.cancel();
    _detailScrollController.dispose();
    _tvPlayFocus.dispose();
    _tvBackFocus.dispose();
    _tvTabsFocus.dispose();
    infoTabController.removeListener(onInfoTabChanged);
    infoTabController.removeListener(_syncFabTabIndex);
    infoTabController.animation?.removeListener(_syncFabTabIndex);
    infoController.characterList.clear();
    infoController.clearComments();
    infoController.staffList.clear();
    infoController.clearRelations();
    infoController.pluginSearchResponseList.clear();
    infoTabController.dispose();
    super.dispose();
  }

  Future<void> queryBangumiInfoByID(
    int id, {
    String type = "init",
    bool enforceMinimumLoadingDuration = false,
  }) async {
    final loadingStartedAt = DateTime.now();
    try {
      await infoController.queryBangumiInfoByID(id, type: type);
    } catch (e) {
      KazumiLogger()
          .e('InfoPage: failed to query bangumi info by ID', error: e);
    } finally {
      if (enforceMinimumLoadingDuration && mounted) {
        await _waitForMinimumBangumiInfoLoadingDuration(loadingStartedAt);
      }
      if (mounted) {
        setState(() {
          _showBangumiInfoSkeleton = false;
        });
      }
    }
  }

  Future<void> _waitForMinimumBangumiInfoLoadingDuration(
      DateTime loadingStartedAt) async {
    final elapsed = DateTime.now().difference(loadingStartedAt);
    final remaining = _minimumBangumiInfoLoadingDuration - elapsed;
    if (remaining > Duration.zero) {
      await Future.delayed(remaining);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bool showWindowButton =
        GStorage.getSetting(SettingsKeys.showWindowButton);
    final bool showRatingFab = _fabTabIndex == _commentsTabIndex;
    final headerHeight =
        TvMode.enabled ? BangumiInfoCardV.tvHeaderHeight(context) + 8 : 308.0;
    final page = PopScope(
      canPop: true,
      child: DefaultTabController(
        length: _infoTabs.length,
        child: Scaffold(
          body: NestedScrollView(
            controller: _detailScrollController,
            headerSliverBuilder:
                (BuildContext context, bool innerBoxIsScrolled) {
              return <Widget>[
                SliverOverlapAbsorber(
                  handle:
                      NestedScrollView.sliverOverlapAbsorberHandleFor(context),
                  sliver: SliverAppBar.medium(
                    title: EmbeddedNativeControlArea(
                      child: dtb.DragToMoveArea(
                        child: Container(
                          width: double.infinity,
                          alignment: Alignment.centerLeft,
                          child: Text(
                            infoController.bangumiItem.nameCn == ''
                                ? infoController.bangumiItem.name
                                : infoController.bangumiItem.nameCn,
                          ),
                        ),
                      ),
                    ),
                    automaticallyImplyLeading: false,
                    scrolledUnderElevation: 0.0,
                    leading: EmbeddedNativeControlArea(
                      child: Focus(
                        skipTraversal: true,
                        canRequestFocus: false,
                        onKeyEvent: _tvBackKey,
                        child: IconButton(
                          focusNode: TvMode.enabled ? _tvBackFocus : null,
                          onPressed: () {
                            context.maybePop();
                          },
                          icon: Icon(Icons.arrow_back),
                        ),
                      ),
                    ),
                    actions: [
                      if (innerBoxIsScrolled && !TvMode.enabled)
                        EmbeddedNativeControlArea(
                          child: CollectButton(
                            bangumiItem: infoController.bangumiItem,
                            color:
                                Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                        ),
                      EmbeddedNativeControlArea(
                        child: IconButton(
                          onPressed: () {
                            launchUrl(
                              Uri.parse(
                                  'https://bangumi.tv/subject/${infoController.bangumiItem.id}'),
                              mode: LaunchMode.externalApplication,
                            );
                          },
                          icon: const Icon(Icons.open_in_browser_rounded),
                        ),
                      ),
                      if (!showWindowButton && isDesktop())
                        CloseButton(onPressed: () => windowManager.close()),
                      SizedBox(width: 8),
                    ],
                    toolbarHeight: (Platform.isMacOS && showWindowButton)
                        ? kToolbarHeight + 22
                        : kToolbarHeight,
                    stretch: true,
                    centerTitle: false,
                    expandedHeight: (Platform.isMacOS && showWindowButton)
                        ? headerHeight + kTextTabBarHeight + kToolbarHeight + 22
                        : headerHeight + kTextTabBarHeight + kToolbarHeight,
                    collapsedHeight: (Platform.isMacOS && showWindowButton)
                        ? kTextTabBarHeight +
                            kToolbarHeight +
                            MediaQuery.paddingOf(context).top +
                            22
                        : kTextTabBarHeight +
                            kToolbarHeight +
                            MediaQuery.paddingOf(context).top,
                    flexibleSpace: FlexibleSpaceBar(
                      collapseMode: CollapseMode.pin,
                      background: Observer(builder: (context) {
                        final showBangumiInfoSkeleton =
                            _isShowingBangumiInfoSkeleton;
                        return Stack(
                          children: [
                            // No background image when loading to make loading looks better
                            if (!showBangumiInfoSkeleton)
                              Positioned.fill(
                                bottom: kTextTabBarHeight,
                                child: IgnorePointer(
                                  child: _InfoHeaderBackground(
                                    imageUrl: infoController
                                            .bangumiItem.images['large'] ??
                                        '',
                                  ),
                                ),
                              ),
                            SafeArea(
                              bottom: false,
                              child: EmbeddedNativeControlArea(
                                child: Align(
                                  alignment: Alignment.topCenter,
                                  child: Padding(
                                    padding: const EdgeInsets.fromLTRB(
                                        16, kToolbarHeight, 16, 0),
                                    child: BangumiInfoCardV(
                                      bangumiItem: infoController.bangumiItem,
                                      isLoading: showBangumiInfoSkeleton,
                                      showRating: showRating,
                                      tvActions: TvMode.enabled
                                          ? TvDetailActions(
                                              playFocus: _tvPlayFocus,
                                              onPlay: _startWatching,
                                              collectionBuilder: (focusNode) =>
                                                  CollectButton.extend(
                                                bangumiItem:
                                                    infoController.bangumiItem,
                                                focusNode: focusNode,
                                              ),
                                              onReview: onBangumiRatingTap,
                                              onUp: _tvBackFocus.requestFocus,
                                              onDown: _focusTvTab,
                                            )
                                          : null,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ],
                        );
                      }),
                    ),
                    forceElevated: innerBoxIsScrolled,
                    bottom: PreferredSize(
                      preferredSize: const Size.fromHeight(kTextTabBarHeight),
                      child: Focus(
                        focusNode: _tvTabsFocus,
                        skipTraversal: true,
                        canRequestFocus: false,
                        onKeyEvent: _tvTabKey,
                        child: TabBar(
                          controller: infoTabController,
                          isScrollable: true,
                          tabAlignment: TabAlignment.center,
                          dividerHeight: 0,
                          tabs:
                              _infoTabs.map((name) => Tab(text: name)).toList(),
                        ),
                      ),
                    ),
                  ),
                ),
              ];
            },
            body: Observer(builder: (context) {
              final showBangumiInfoSkeleton = _isShowingBangumiInfoSkeleton;
              return InfoTabView(
                tabController: infoTabController,
                bangumiItem: infoController.bangumiItem,
                commentsQueryTimeout: commentsQueryTimeout,
                commentsIsEmpty: commentsIsEmpty,
                charactersQueryTimeout: charactersQueryTimeout,
                charactersIsEmpty: charactersIsEmpty,
                staffQueryTimeout: staffQueryTimeout,
                staffIsEmpty: staffIsEmpty,
                loadMoreComments: loadMoreComments,
                loadCharacters: loadCharacters,
                loadStaff: loadStaff,
                commentsList: infoController.commentsList,
                commentsIsLoading: commentsIsLoading,
                onCommentsTabSelected: onCommentsTabSelected,
                characterList: infoController.characterList,
                staffList: infoController.staffList,
                relationList: infoController.relationList,
                relationsIsLoading: infoController.relationsIsLoading,
                relationsQueryTimeout: infoController.relationsQueryTimeout,
                relationsHasLoaded: infoController.relationsHasLoaded,
                loadRelations: loadRelations,
                isLoading: showBangumiInfoSkeleton,
              );
            }),
          ),
          floatingActionButton: TvMode.enabled
              ? null
              : showRatingFab
                  ? FloatingActionButton.extended(
                      tooltip: '吐槽',
                      onPressed: onBangumiRatingTap,
                      label: const Text('发表吐槽'),
                      icon: const Icon(Icons.rate_review_rounded),
                    )
                  : FloatingActionButton.extended(
                      tooltip: '开始观看',
                      onPressed: _startWatching,
                      label: const Text('开始观看'),
                      icon: const Icon(Icons.play_arrow_rounded),
                    ),
        ),
      ),
    );
    return TvMode.enabled
        ? TvDetailPlayShortcut(onPlay: _startWatching, child: page)
        : page;
  }
}

class _InfoHeaderBackground extends StatelessWidget {
  const _InfoHeaderBackground({
    required this.imageUrl,
  });

  static const double _downsample = 0.5;
  static const double _blurSigma = 15.0;
  static const double _opacity = 0.4;
  static const double _edgeBleed = 32.0;
  static const double _bottomFeatherHeight = 48.0;

  final String imageUrl;

  @override
  Widget build(BuildContext context) {
    if (imageUrl.isEmpty) {
      return const SizedBox.shrink();
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        final height = constraints.maxHeight;
        if (width <= 0 || height <= 0) {
          return const SizedBox.shrink();
        }

        final rasterWidth = width * _downsample;
        final rasterHeight = (height + _edgeBleed) * _downsample;

        final backgroundColor = Theme.of(context).scaffoldBackgroundColor;

        return ClipRect(
          child: Stack(
            fit: StackFit.expand,
            children: [
              ShaderMask(
                shaderCallback: (bounds) {
                  return const LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.white,
                      Colors.transparent,
                    ],
                    stops: [0.8, 1],
                  ).createShader(bounds);
                },
                child: Align(
                  alignment: Alignment.topCenter,
                  child: RepaintBoundary(
                    child: Transform.scale(
                      scale: 1 / _downsample,
                      alignment: Alignment.topCenter,
                      filterQuality: FilterQuality.low,
                      child: SizedBox(
                        width: rasterWidth,
                        height: rasterHeight,
                        child: ImageFiltered(
                          imageFilter: ImageFilter.blur(
                            sigmaX: _blurSigma * _downsample,
                            sigmaY: _blurSigma * _downsample,
                          ),
                          child: NetworkImgLayer(
                            src: imageUrl,
                            width: rasterWidth,
                            height: rasterHeight,
                            fadeInDuration: Duration.zero,
                            fadeOutDuration: Duration.zero,
                            filterQuality: FilterQuality.low,
                            color: Colors.white.withValues(alpha: _opacity),
                            colorBlendMode: BlendMode.modulate,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                height: _bottomFeatherHeight,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        backgroundColor.withValues(alpha: 0),
                        backgroundColor.withValues(alpha: 0.55),
                        backgroundColor,
                      ],
                      stops: const [0, 0.72, 1],
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
