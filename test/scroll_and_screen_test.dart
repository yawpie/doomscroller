import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:autoscroll_compvis/app/app.dart';
import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/auto_scroll/infrastructure/flutter_scroll_service.dart';
import 'package:autoscroll_compvis/features/gesture/domain/hand_motion.dart';

void main() {
  testWidgets('scroll watchdog stops when tracking results cease', (
    tester,
  ) async {
    final scroll = FlutterScrollService(
      const ScrollConfig(staleAfter: Duration(milliseconds: 5)),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: ListView(
          controller: scroll.controller,
          children: List.generate(50, (i) => const SizedBox(height: 100)),
        ),
      ),
    );
    scroll.handle(
      const HandMotion(action: HandAction.moveDown, verticalVelocity: 0.5),
    );
    await tester.pump();
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 20)),
    );
    await tester.pump(const Duration(milliseconds: 50));
    expect(scroll.controller.offset, 0);
    await tester.pumpWidget(const SizedBox.shrink());
    scroll.dispose();
  });

  testWidgets('debug screen launches without accessing the camera', (
    tester,
  ) async {
    await tester.pumpWidget(const ProviderScope(child: AutoScrollApp()));
    expect(find.text('Start tracking'), findsOneWidget);
    expect(find.text('No hand detected'), findsOneWidget);
    expect(find.text('Action: idle'), findsOneWidget);
    expect(find.text('Reading item 1'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
  });

  testWidgets('scroll maps direction, clamps edges and stops on idle', (
    tester,
  ) async {
    final scroll = FlutterScrollService(
      const ScrollConfig(staleAfter: Duration(hours: 1)),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: ListView(
          controller: scroll.controller,
          children: List.generate(
            50,
            (i) => SizedBox(height: 100, child: Text('$i')),
          ),
        ),
      ),
    );
    scroll.handle(
      const HandMotion(action: HandAction.moveDown, verticalVelocity: 0.5),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(scroll.controller.offset, greaterThan(0));
    final down = scroll.controller.offset;
    scroll.handle(
      const HandMotion(action: HandAction.moveUp, verticalVelocity: -0.5),
    );
    await tester.pump(const Duration(milliseconds: 20));
    expect(scroll.controller.offset, lessThan(down));
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    expect(scroll.controller.offset, 0);
    scroll.handle(const HandMotion(action: HandAction.idle));
    await tester.pump(const Duration(milliseconds: 100));
    expect(scroll.controller.offset, 0);
    await tester.pumpWidget(const SizedBox.shrink());
    scroll.dispose();
  });
}
