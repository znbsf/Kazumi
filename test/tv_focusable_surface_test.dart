import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kazumi/bean/widget/tv_focusable_surface.dart';

void main() {
  testWidgets('TV select key activates the focused surface', (tester) async {
    var activations = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: TvFocusableSurface(
            enabled: true,
            autofocus: true,
            onPressed: () => activations += 1,
            child: const SizedBox(width: 120, height: 80),
          ),
        ),
      ),
    );
    await tester.pump();

    await tester.sendKeyEvent(LogicalKeyboardKey.select);
    await tester.pump();

    expect(activations, 1);
  });

  testWidgets('disabled TV surface preserves the unwrapped mobile child',
      (tester) async {
    const childKey = Key('mobile-child');
    await tester.pumpWidget(
      MaterialApp(
        home: TvFocusableSurface(
          enabled: false,
          onPressed: () {},
          child: const SizedBox(key: childKey),
        ),
      ),
    );

    expect(find.byKey(childKey), findsOneWidget);
    expect(
      find.descendant(
        of: find.byType(TvFocusableSurface),
        matching: find.byType(Focus),
      ),
      findsNothing,
    );
  });
}
