import 'package:flutter/material.dart';
import 'package:flutter_modular/flutter_modular.dart';
import 'package:kazumi/bean/card/network_img_layer.dart';
import 'package:kazumi/bean/dialog/dialog_helper.dart';
import 'package:kazumi/modules/bangumi/bangumi_item.dart';
import 'package:kazumi/utils/device.dart';
import 'package:kazumi/bean/widget/tv_focusable_surface.dart';
import 'package:kazumi/services/platform/tv_mode.dart';

// 视频卡片 - 垂直布局
class BangumiCardV extends StatelessWidget {
  const BangumiCardV({
    super.key,
    required this.bangumiItem,
    this.canTap = true,
    this.enableHero = true,
    this.channelNumber,
    this.highlighted = false,
    this.focusNode,
    this.onPressed,
  });

  final BangumiItem bangumiItem;
  final bool canTap;
  final bool enableHero;
  final int? channelNumber;
  final bool highlighted;
  final FocusNode? focusNode;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    void openBangumi() {
      if (!canTap) {
        KazumiDialog.showToast(
          message: '编辑模式',
        );
        return;
      }
      if (onPressed != null) {
        onPressed!();
      } else {
        context.pushNamed('/info/', arguments: bangumiItem);
      }
    }

    final card = Card(
      elevation: 0,
      clipBehavior: Clip.antiAlias,
      margin: EdgeInsets.zero,
      child: GestureDetector(
        child: InkWell(
          canRequestFocus: !TvMode.enabled,
          onTap: openBangumi,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              AspectRatio(
                aspectRatio: 0.65,
                child: LayoutBuilder(builder: (context, boxConstraints) {
                  final double maxWidth = boxConstraints.maxWidth;
                  final double maxHeight = boxConstraints.maxHeight;
                  final image = NetworkImgLayer(
                    src: bangumiItem.images['large'] ?? '',
                    width: maxWidth,
                    height: maxHeight,
                  );
                  final poster = enableHero
                      ? Hero(
                          transitionOnUserGestures: true,
                          flightShuttleBuilder:
                              NetworkImgLayer.heroFlightShuttleBuilder,
                          tag: bangumiItem.id,
                          child: image,
                        )
                      : image;
                  return Stack(
                    fit: StackFit.expand,
                    children: [
                      poster,
                      if (channelNumber != null)
                        Positioned(
                          left: 8,
                          top: 8,
                          child: _ChannelNumberBadge(number: channelNumber!),
                        ),
                    ],
                  );
                }),
              ),
              BangumiContent(bangumiItem: bangumiItem)
            ],
          ),
        ),
      ),
    );
    return TvFocusableSurface(
      onPressed: openBangumi,
      focusNode: focusNode,
      highlighted: highlighted,
      child: card,
    );
  }
}

class _ChannelNumberBadge extends StatelessWidget {
  const _ChannelNumberBadge({required this.number});

  final int number;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Semantics(
      label: '$number 号节目',
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: colorScheme.surface.withValues(alpha: 0.88),
          borderRadius: BorderRadius.circular(7),
          border: Border.all(
            color: colorScheme.outlineVariant.withValues(alpha: 0.8),
          ),
          boxShadow: const [
            BoxShadow(color: Colors.black38, blurRadius: 4),
          ],
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          child: Text(
            number.toString(),
            style: TextStyle(
              color: colorScheme.onSurface,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ),
    );
  }
}

class BangumiContent extends StatelessWidget {
  const BangumiContent({super.key, required this.bangumiItem});

  final BangumiItem bangumiItem;

  static int maxTextLinesFor(BuildContext context) {
    return isDesktop()
        ? 3
        : (isTablet() &&
                MediaQuery.of(context).orientation == Orientation.landscape)
            ? 3
            : 2;
  }

  @override
  Widget build(BuildContext context) {
    final ts = MediaQuery.textScalerOf(context);
    final int maxTextLines = maxTextLinesFor(context);

    return Expanded(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(5, 3, 5, 1),
        child: Text(
          bangumiItem.nameCn,
          textAlign: TextAlign.start,
          style: const TextStyle(
            fontWeight: FontWeight.w500,
            letterSpacing: 0.3,
          ),
          textScaler: ts.clamp(maxScaleFactor: 1.1),
          maxLines: maxTextLines,
          overflow: TextOverflow.ellipsis,
        ),
      ),
    );
  }
}
