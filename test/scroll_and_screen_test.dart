import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:autoscroll_compvis/app/app.dart';
import 'package:autoscroll_compvis/app/providers.dart';
import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/auto_scroll/application/tracking_controller.dart';
import 'package:autoscroll_compvis/features/auto_scroll/infrastructure/flutter_scroll_service.dart';
import 'package:autoscroll_compvis/features/gesture/application/gesture_coordinator.dart';
import 'package:autoscroll_compvis/features/gesture/application/windowed_motion_detection_service.dart';
import 'package:autoscroll_compvis/features/gesture/domain/flick_event.dart';
import 'package:autoscroll_compvis/features/gesture/domain/hand_motion.dart';
import 'package:autoscroll_compvis/features/gesture/domain/pose_event.dart';

import 'support/hand_pose_fixtures.dart';
import 'tracking_controller_test.dart' show FakeCamera, FakeTracker, FakeScroll;

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
  test('invalid re-arm duration leaves provider settings unchanged', () {
    final container = ProviderContainer();
    addTearDown(container.dispose);
    final before = container.read(handPoseConfigProvider);
    for (final duration in [Duration.zero, const Duration(milliseconds: -1)]) {
      expect(
        () => container
            .read(handPoseConfigProvider.notifier)
            .configureRearm(duration: duration),
        throwsArgumentError,
      );
      expect(container.read(handPoseConfigProvider), same(before));
    }
  });

  testWidgets(
    're-arm controls select timed repeats and return to open-hand release',
    (tester) async {
      final camera = FakeCamera();
      final tracker = FakeTracker();
      final scroll = FakeScroll();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            cameraServiceProvider.overrideWithValue(camera),
            handTrackingServiceProvider.overrideWithValue(tracker),
            scrollServiceProvider.overrideWithValue(scroll),
          ],
          child: const AutoScrollApp(),
        ),
      );
      final container = ProviderScope.containerOf(
        tester.element(find.byType(AutoScrollApp)),
      );
      final dropdown = find.byType(DropdownButtonFormField<HandPoseRearmMode>);
      expect(
        container.read(handPoseConfigProvider).rearmMode,
        HandPoseRearmMode.openHand,
      );
      expect(find.byType(Slider), findsOneWidget);
      await tester.ensureVisible(dropdown);
      await tester.tap(dropdown);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Hold same pose').last);
      await tester.pumpAndSettle();
      expect(
        container.read(handPoseConfigProvider).rearmMode,
        HandPoseRearmMode.stablePose,
      );
      expect(find.text('Re-arm after: 1000 ms'), findsOneWidget);
      tester.widget<Slider>(find.byType(Slider).last).onChanged!(500);
      await tester.pump();
      expect(find.text('Re-arm after: 500 ms'), findsOneWidget);
      await tester.ensureVisible(find.text('Start tracking'));
      await tester.tap(find.text('Start tracking'));
      await tester.pump();
      final streaming = camera.onFrame;
      for (var ms = 0; ms <= 1800; ms += 100) {
        tracker.pose = handPoseFixture(RecognizedHandPose.pointingDown);
        camera.emit(ms);
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(scroll.pages, List.filled(3, FlickDirection.down));
      expect(scroll.flicks, isEmpty);
      expect(scroll.motions.every((m) => m.action == HandAction.idle), isTrue);
      final stops = scroll.stops;
      await tester.ensureVisible(dropdown);
      await tester.tap(dropdown);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Open hand').last);
      await tester.pumpAndSettle();
      expect(camera.onFrame, same(streaming));
      expect(scroll.stops, greaterThan(stops));
      expect(
        container.read(handPoseConfigProvider).stablePoseDuration,
        const Duration(milliseconds: 500),
      );
      expect(find.byType(Slider), findsOneWidget);
      for (var ms = 1900; ms <= 4100; ms += 100) {
        camera.emit(ms);
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(scroll.pages, List.filled(4, FlickDirection.down));
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
      expect(camera.disposed && tracker.disposed, isTrue);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'live pose-only switch resets recognition while camera keeps streaming',
    (tester) async {
      final camera = FakeCamera();
      final tracker = FakeTracker();
      final scroll = FakeScroll();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            cameraServiceProvider.overrideWithValue(camera),
            handTrackingServiceProvider.overrideWithValue(tracker),
            scrollServiceProvider.overrideWithValue(scroll),
          ],
          child: const AutoScrollApp(),
        ),
      );
      await tester.tap(find.text('Start tracking'));
      await tester.pump();
      for (final ms in [0, 100, 200]) {
        tracker.pose = handPoseFixture(RecognizedHandPose.pointingDown);
        camera.emit(ms);
        await tester.pump();
      }
      expect(scroll.pages, [FlickDirection.down]);
      expect(find.text('Hand pose: Pointing Down'), findsOneWidget);
      expect(find.text('Pose state: LATCHED'), findsOneWidget);
      expect(find.text('Action: PAGE DOWN'), findsOneWidget);
      final streaming = camera.onFrame;
      final stops = scroll.stops;
      final poseSwitch = find.byType(Switch).last;
      await tester.ensureVisible(poseSwitch);
      await tester.tap(poseSwitch);
      await tester.pump();
      expect(tester.widget<Switch>(poseSwitch).value, isTrue);
      expect(camera.onFrame, same(streaming));
      expect(camera.disposed, isFalse);
      expect(scroll.stops, greaterThan(stops));
      expect(find.text('Pose state: READY'), findsOneWidget);
      expect(find.text('Action: IDLE'), findsOneWidget);
      for (final (ms, y) in [
        (400, 0.4),
        (500, 0.48),
        (600, 0.56),
        (700, 0.64),
      ]) {
        tracker.pose = handPoseFixture(RecognizedHandPose.neutral, y: y);
        camera.emit(ms);
        await tester.pump();
      }
      expect(scroll.pages, [FlickDirection.down]);
      expect(scroll.flicks, isEmpty);
      expect(
        scroll.motions.any(
          (motion) =>
              motion.action == HandAction.moveUp ||
              motion.action == HandAction.moveDown,
        ),
        isFalse,
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
      expect(camera.onFrame, isNull);
      expect(camera.disposed && tracker.disposed, isTrue);
      expect(tester.takeException(), isNull);
    },
  );

  for (final (pose, direction) in [
    (RecognizedHandPose.pointingDown, FlickDirection.down),
    (RecognizedHandPose.pointingUp, FlickDirection.up),
  ]) {
    testWidgets(
      'held ${pose.name} scrolls exactly one viewport through controller',
      (tester) async {
        final scroll = FlutterScrollService(
          const ScrollConfig(
            flickAction: FlickScrollAction.fastScroll,
            flickScrollDistance: 240,
          ),
        );
        await mountScroll(tester, scroll);
        scroll.controller.jumpTo(1000);
        final viewport = scroll.controller.position.viewportDimension;
        final camera = FakeCamera();
        final tracker = FakeTracker();
        final controller = TrackingController(
          camera: camera,
          tracker: tracker,
          motionDetector: WindowedMotionDetectionService(
            const MotionDetectionConfig(),
          ),
          gestureMode: GestureMode.poseOnly,
          scroll: scroll,
          config: const TrackingConfig(),
          onState: (_) {},
        );
        await controller.start();
        for (var i = 0; i <= 30; i++) {
          tracker.pose = handPoseFixture(pose);
          camera.emit(i * 100);
          await tester.pump(const Duration(milliseconds: 100));
        }
        final expected =
            1000 + (direction == FlickDirection.down ? viewport : -viewport);
        expect(scroll.controller.offset, closeTo(expected, 0.01));
        expect(scroll.velocity.value, 0);
        await controller.dispose();
        await tester.pumpWidget(const SizedBox.shrink());
        scroll.dispose();
        await tester.pump();
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('page action honors inversion and clamps either boundary', (
    tester,
  ) async {
    final scroll = FlutterScrollService(const ScrollConfig());
    await mountScroll(tester, scroll);
    final viewport = scroll.controller.position.viewportDimension;
    scroll.controller.jumpTo(1000);
    scroll.configure(inverted: true);
    scroll.scrollByPage(FlickDirection.down);
    await tester.pumpAndSettle();
    expect(scroll.controller.offset, closeTo(1000 - viewport, 0.01));
    scroll.controller.jumpTo(20);
    scroll.scrollByPage(FlickDirection.down);
    await tester.pumpAndSettle();
    expect(scroll.controller.offset, 0);
    final maximum = scroll.controller.position.maxScrollExtent;
    scroll.controller.jumpTo(maximum - 20);
    scroll.scrollByPage(FlickDirection.up);
    await tester.pumpAndSettle();
    expect(scroll.controller.offset, maximum);
    await tester.pumpWidget(const SizedBox.shrink());
    scroll.dispose();
  });

  testWidgets(
    'page action replaces animation and latched samples preserve its target',
    (tester) async {
      final scroll = FlutterScrollService(const ScrollConfig());
      await mountScroll(tester, scroll);
      scroll.controller.jumpTo(1000);
      scroll.scrollByPage(FlickDirection.down);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      final start = scroll.controller.offset;
      final target = start - scroll.controller.position.viewportDimension;
      scroll.scrollByPage(FlickDirection.up);
      for (var i = 0; i < 8; i++) {
        scroll.handle(const HandMotion(action: HandAction.idle));
        await tester.pump(const Duration(milliseconds: 40));
      }
      expect(scroll.controller.offset, closeTo(target, 0.01));
      expect(scroll.velocity.value, 0);
      await tester.pumpWidget(const SizedBox.shrink());
      scroll.dispose();
    },
  );

  for (final stopping in [
    'stop',
    'tracking loss',
    'configuration',
    'disposal',
  ]) {
    testWidgets('$stopping cancels a page action', (tester) async {
      final scroll = FlutterScrollService(const ScrollConfig());
      await mountScroll(tester, scroll);
      scroll.scrollByPage(FlickDirection.down);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      final offset = scroll.controller.offset;
      switch (stopping) {
        case 'stop':
          scroll.stop();
        case 'tracking loss':
          scroll.handle(const HandMotion(action: HandAction.stop));
        case 'configuration':
          scroll.configure(inverted: true);
        case 'disposal':
          await tester.pumpWidget(const SizedBox.shrink());
          scroll.dispose();
      }
      await tester.pumpAndSettle();
      if (stopping != 'disposal') {
        expect(scroll.controller.offset, closeTo(offset, 0.01));
        expect(scroll.velocity.value, 0);
        await tester.pumpWidget(const SizedBox.shrink());
        scroll.dispose();
      }
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets(
    'manual dragging interrupts a page action with no resumed target',
    (tester) async {
      final scroll = FlutterScrollService(const ScrollConfig());
      await mountScroll(tester, scroll);
      scroll.controller.jumpTo(700);
      final target = 700 + scroll.controller.position.viewportDimension;
      scroll.scrollByPage(FlickDirection.down);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      await tester.drag(find.byType(ListView), const Offset(0, 100));
      scroll.handle(const HandMotion(action: HandAction.idle));
      await tester.pumpAndSettle();
      expect(scroll.controller.offset, lessThan(target - 1));
      final offset = scroll.controller.offset;
      await tester.pump(const Duration(milliseconds: 400));
      expect(scroll.controller.offset, closeTo(offset, 0.01));
      await tester.pumpWidget(const SizedBox.shrink());
      scroll.dispose();
    },
  );

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
