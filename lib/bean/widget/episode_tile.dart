import 'package:flutter/material.dart';
import 'package:kazumi/bean/widget/tv_focusable_surface.dart';
import 'package:kazumi/services/platform/tv_mode.dart';

/// The playing indicator is persistent state; the outer frame is focus only.
class EpisodeTile extends StatelessWidget {
  const EpisodeTile(
      {super.key,
      required this.label,
      required this.isPlaying,
      required this.onPressed,
      this.focusNode,
      this.onKeyEvent,
      this.status = const SizedBox.shrink()});

  final String label;
  final bool isPlaying;
  final VoidCallback onPressed;
  final FocusNode? focusNode;
  final FocusOnKeyEventCallback? onKeyEvent;
  final Widget status;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      selected: isPlaying,
      label: isPlaying ? '正在播放' : null,
      child: TvFocusableSurface(
        focusScale: 1,
        focusNode: focusNode,
        autofocus: TvMode.enabled && isPlaying,
        borderRadius: 6,
        onPressed: onPressed,
        onKeyEvent: onKeyEvent,
        child: Material(
          color: colors.onInverseSurface
              .withValues(alpha: TvMode.enabled ? 0.30 : 1),
          borderRadius: BorderRadius.circular(6),
          clipBehavior: Clip.hardEdge,
          child: InkWell(
            canRequestFocus: !TvMode.enabled,
            onTap: onPressed,
            child: Padding(
              padding: EdgeInsets.symmetric(
                  vertical: TvMode.enabled ? 5 : 8,
                  horizontal: TvMode.enabled ? 8 : 10),
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(children: [
                      if (isPlaying) ...[
                        Image.asset('assets/images/playing.gif',
                            color: colors.primary, height: 12),
                        const SizedBox(width: 6),
                      ],
                      Expanded(
                          child: Text(label,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                  fontSize: TvMode.enabled ? 12 : 13,
                                  color: isPlaying
                                      ? colors.primary
                                      : colors.onSurface))),
                      status,
                      const SizedBox(width: 2),
                    ]),
                  ]),
            ),
          ),
        ),
      ),
    );
  }
}
