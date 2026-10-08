import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:autoscroll_compvis/app/app.dart';
import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/auto_scroll/infrastructure/flutter_scroll_service.dart';
import 'package:autoscroll_compvis/features/gesture/domain/flick_event.dart';
import 'package:autoscroll_compvis/features/gesture/domain/hand_motion.dart';

FlickEvent flickEvent(FlickDirection direction) => FlickEvent(
  direction: direction,
  timestamp: const Duration(milliseconds: 166),
  verticalDisplacement: direction == FlickDirection.up ? -0.16 : 0.16,
  verticalVelocity: direction == FlickDirection.up ? -1 : 1,
  duration: const Duration(milliseconds: 166),
);

Future<void> mountScroll(WidgetTester tester, FlutterScrollService scroll) =>
    tester.pumpWidget(
      MaterialApp(
        home: ListView(
          controller: scroll.controller,
          children: List.generate(50, (i) => const SizedBox(height: 100)),
        ),
      ),
    );

void main() {
  testWidgets(
    'page flick replaces continuous movement and survives held idle',
    (tester) async {
      final scroll = FlutterScrollService(const ScrollConfig());
      await mountScroll(tester, scroll);
      final viewport = scroll.controller.position.viewportDimension;
      scroll.handle(
        const HandMotion(action: HandAction.moveDown, verticalVelocity: 0.5),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 30));
      final start = scroll.controller.offset;
      scroll.handleFlick(flickEvent(FlickDirection.down));
      scroll.handle(const HandMotion(action: HandAction.idle));
      await tester.pumpAndSettle();
      expect(scroll.controller.offset, closeTo(start + viewport, 0.01));
      expect(scroll.velocity.value, 0);
      await tester.pump(const Duration(milliseconds: 400));
      expect(scroll.controller.offset, closeTo(start + viewport, 0.01));
      await tester.pumpWidget(const SizedBox.shrink());
      scroll.dispose();
    },
  );

  testWidgets(
    'flick replaces an existing animation and a later flick replaces it',
    (tester) async {
      final scroll = FlutterScrollService(const ScrollConfig());
      await mountScroll(tester, scroll);
      unawaited(
        scroll.controller.animateTo(
          1400,
          duration: const Duration(seconds: 1),
          curve: Curves.linear,
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      scroll.handleFlick(flickEvent(FlickDirection.down));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 80));
      final start = scroll.controller.offset;
      final target = (start - scroll.controller.position.viewportDimension)
          .clamp(0.0, scroll.controller.position.maxScrollExtent);
      scroll.handleFlick(flickEvent(FlickDirection.up));
      await tester.pumpAndSettle();
      expect(scroll.controller.offset, closeTo(target, 0.01));
      await tester.pump(const Duration(seconds: 1));
      expect(scroll.controller.offset, closeTo(target, 0.01));
      await tester.pumpWidget(const SizedBox.shrink());
      scroll.dispose();
    },
  );

  testWidgets('fast-scroll distance honors inversion and clamps both edges', (
    tester,
  ) async {
    final scroll = FlutterScrollService(
      const ScrollConfig(
        flickAction: FlickScrollAction.fastScroll,
        flickScrollDistance: 240,
      ),
    );
    await mountScroll(tester, scroll);
    scroll.controller.jumpTo(500);
    scroll.configure(inverted: true);
    scroll.handleFlick(flickEvent(FlickDirection.down));
    await tester.pumpAndSettle();
    expect(scroll.controller.offset, closeTo(260, 0.01));
    scroll.controller.jumpTo(20);
    scroll.handleFlick(flickEvent(FlickDirection.down));
    await tester.pumpAndSettle();
    expect(scroll.controller.offset, 0);
    final maximum = scroll.controller.position.maxScrollExtent;
    scroll.controller.jumpTo(maximum - 20);
    scroll.handleFlick(flickEvent(FlickDirection.up));
    await tester.pumpAndSettle();
    expect(scroll.controller.offset, maximum);
    await tester.pumpWidget(const SizedBox.shrink());
    scroll.dispose();
  });

  for (final stopping in ['stop', 'tracking loss', 'configuration']) {
    testWidgets('$stopping cancels a running flick animation', (tester) async {
      final scroll = FlutterScrollService(const ScrollConfig());
      await mountScroll(tester, scroll);
      scroll.handleFlick(flickEvent(FlickDirection.down));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      final offset = scroll.controller.offset;
      switch (stopping) {
        case 'stop':
          scroll.stop();
        case 'tracking loss':
          scroll.handle(const HandMotion(action: HandAction.stop));
        case 'configuration':
          scroll.configure(sensitivity: 2000);
      }
      await tester.pumpAndSettle();
      expect(scroll.controller.offset, closeTo(offset, 0.01));
      expect(scroll.velocity.value, 0);
      await tester.pumpWidget(const SizedBox.shrink());
      scroll.dispose();
    });
  }

  testWidgets('manual touch interrupts a flick without resuming its target', (
    tester,
  ) async {
    final scroll = FlutterScrollService(const ScrollConfig());
    await mountScroll(tester, scroll);
    scroll.controller.jumpTo(700);
    final target = 700 + scroll.controller.position.viewportDimension;
    scroll.handleFlick(flickEvent(FlickDirection.down));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.drag(find.byType(ListView), const Offset(0, 100));
    await tester.pumpAndSettle();
    expect(scroll.controller.offset, lessThan(target - 1));
    final offset = scroll.controller.offset;
    await tester.pump(const Duration(milliseconds: 400));
    expect(scroll.controller.offset, closeTo(offset, 0.01));
    await tester.pumpWidget(const SizedBox.shrink());
    scroll.dispose();
  });

  testWidgets('new continuous movement cancels rather than overlaps a flick', (
    tester,
  ) async {
    final scroll = FlutterScrollService(
      const ScrollConfig(staleAfter: Duration(hours: 1)),
    );
    await mountScroll(tester, scroll);
    scroll.controller.jumpTo(700);
    scroll.handleFlick(flickEvent(FlickDirection.down));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    final offset = scroll.controller.offset;
    scroll.handle(
      const HandMotion(action: HandAction.moveUp, verticalVelocity: -0.5),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(scroll.controller.offset, lessThan(offset));
    scroll.handle(const HandMotion(action: HandAction.idle));
    final stopped = scroll.controller.offset;
    await tester.pumpAndSettle();
    expect(scroll.controller.offset, closeTo(stopped, 0.01));
    await tester.pumpWidget(const SizedBox.shrink());
    scroll.dispose();
  });

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
    expect(find.text('Action: IDLE'), findsOneWidget);
    expect(find.text('Tracking: DISABLED'), findsOneWidget);
    expect(find.byType(Slider), findsOneWidget);
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

  testWidgets(
    'controls work without overflow on a narrow screen with large text',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(320, 640));
      tester.platformDispatcher.textScaleFactorTestValue = 1.6;
      addTearDown(() async {
        tester.platformDispatcher.clearTextScaleFactorTestValue();
        await tester.binding.setSurfaceSize(null);
      });
      await tester.pumpWidget(const ProviderScope(child: AutoScrollApp()));
      expect(tester.takeException(), isNull);
      final slider = find.byType(Slider);
      await tester.ensureVisible(slider);
      await tester.drag(slider, const Offset(40, 0));
      await tester.pump();
      expect(tester.takeException(), isNull);
      final switches = find.byType(Switch);
      await tester.ensureVisible(switches.first);
      await tester.tap(switches.first);
      await tester.pump();
      expect(tester.widget<Switch>(switches.first).value, isTrue);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
    },
  );
}
