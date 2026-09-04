import 'package:flutter/material.dart';
import 'package:kazumi/services/platform/tv_mode.dart';

/// Applies TV-only focus behavior while preserving the normal mobile theme.
class TvAppShell extends StatelessWidget {
  const TvAppShell({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (!TvMode.enabled) {
      return child;
    }

    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    return Theme(
      data: theme.copyWith(
        focusColor: colorScheme.primary.withValues(alpha: 0.24),
        hoverColor: colorScheme.primary.withValues(alpha: 0.12),
        visualDensity: VisualDensity.comfortable,
      ),
      child: FocusTraversalGroup(
        policy: ReadingOrderTraversalPolicy(),
        child: child,
      ),
    );
  }
}
