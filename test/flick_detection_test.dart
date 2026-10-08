import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/gesture/application/flick_detector.dart';
import 'package:autoscroll_compvis/features/gesture/application/gesture_coordinator.dart';
import 'package:autoscroll_compvis/features/gesture/application/windowed_motion_detection_service.dart';
import 'package:autoscroll_compvis/features/gesture/domain/flick_event.dart';
import 'package:autoscroll_compvis/features/gesture/domain/hand_motion.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_models.dart';

HandTrackingResult sample(int ms, double? y, {double x = 0.5}) =>
    HandTrackingResult(
      timestamp: Duration(milliseconds: ms),
      pose: y == null
          ? null
          : HandPose(
              landmarks: List.generate(
                21,
                (i) => HandLandmark(index: i, x: x, y: y, z: 0),
              ),
            ),
    );

bool scrolling(HandMotion motion) =>
    motion.action == HandAction.moveUp || motion.action == HandAction.moveDown;

void main() {
  late FlickDetector detector;
  late GestureCoordinator coordinator;
  setUp(() {
    detector = FlickDetector(const FlickDetectionConfig());
    coordinator = GestureCoordinator(
      motionDetector: WindowedMotionDetectionService(
        const MotionDetectionConfig(),
      ),
      flickDetector: detector,
    );
  });

  List<FlickEvent> flick({double direction = 1, int start = 0}) => [
    for (var i = 0; i < 3; i++)
      ?detector.process(sample(start + i * 83, 0.5 + direction * i * 0.08)),
  ];

  for (final direction in [-1.0, 1.0]) {
    final expected = direction < 0 ? FlickDirection.up : FlickDirection.down;
    test('slow ${expected.name} preserves continuous scrolling', () {
      final decisions = [
        for (var i = 0; i < 14; i++)
          coordinator.process(sample(i * 83, 0.5 + direction * i * 0.015)),
      ];
      expect(decisions.every((decision) => decision.flick == null), isTrue);
      expect(
        decisions.last.motion.action,
        direction < 0 ? HandAction.moveUp : HandAction.moveDown,
      );
    });

    test('fast ${expected.name} emits once without continuous movement', () {
      final decisions = [
        for (var i = 0; i < 5; i++)
          coordinator.process(sample(i * 83, 0.5 + direction * i * 0.08)),
      ];
      final events = decisions.map((d) => d.flick).whereType<FlickEvent>();
      expect(events, hasLength(1));
      final event = events.single;
      expect(event.direction, expected);
      expect(event.verticalDisplacement, closeTo(direction * 0.16, 1e-6));
      expect(event.verticalVelocity, closeTo(direction * 0.16 / 0.166, 1e-6));
      expect(event.duration, const Duration(milliseconds: 166));
      expect(event.timestamp, const Duration(milliseconds: 166));
      expect(decisions.any((decision) => scrolling(decision.motion)), isFalse);
    });
  }

  test('stationary hand never produces a scrolling action or flick', () {
    for (var i = 0; i < 40; i++) {
      final decision = coordinator.process(sample(i * 83, 0.5));
      expect(decision.flick, isNull);
      expect(scrolling(decision.motion), isFalse);
    }
  });

  test('seeded random landmark jitter stays idle', () {
    final random = Random(19);
    for (var i = 0; i < 80; i++) {
      final decision = coordinator.process(
        sample(i * 83, 0.5 + (random.nextDouble() - 0.5) * 0.008),
      );
      expect(decision.flick, isNull);
      expect(scrolling(decision.motion), isFalse);
    }
  });

  test('fingertip noise cannot turn a stationary palm into a flick', () {
    for (var i = 0; i < 6; i++) {
      final result = HandTrackingResult(
        timestamp: Duration(milliseconds: i * 83),
        pose: HandPose(
          landmarks: List.generate(
            21,
            (index) => HandLandmark(
              index: index,
              x: 0.5,
              y: [0, 5, 9, 13, 17].contains(index) ? 0.5 : 0.1 + i * 0.1,
              z: 0,
            ),
          ),
        ),
      );
      expect(detector.process(result), isNull);
    }
  });

  test('sudden vertical position jump cannot produce a flick', () {
    expect(detector.process(sample(0, 0.2)), isNull);
    expect(detector.process(sample(83, 0.8)), isNull);
    expect(detector.process(sample(166, 0.8)), isNull);
    expect(detector.process(sample(249, 0.8)), isNull);
  });

  test('lateral hand replacement invalidates a vertical candidate', () {
    expect(detector.process(sample(0, 0.3, x: 0.2)), isNull);
    expect(detector.process(sample(83, 0.38, x: 0.2)), isNull);
    expect(detector.process(sample(166, 0.46, x: 0.8)), isNull);
    expect(detector.process(sample(249, 0.46, x: 0.8)), isNull);
  });

  test('tracking loss stops a candidate and reacquisition starts fresh', () {
    coordinator.process(sample(0, 0.5));
    coordinator.process(sample(83, 0.58));
    final missing = coordinator.process(sample(166, null));
    expect(missing.flick, isNull);
    expect(missing.motion.action, HandAction.stop);
    final reacquired = coordinator.process(sample(249, 0.2));
    expect(reacquired.flick, isNull);
    expect(scrolling(reacquired.motion), isFalse);
    expect(coordinator.process(sample(332, 0.2)).flick, isNull);
  });

  test('cooldown expires without rearming a continuously moving hand', () {
    expect(flick(), hasLength(1));
    for (var i = 0; i < 15; i++) {
      expect(
        detector.process(sample(249 + i * 83, i.isEven ? 0.74 : 0.66)),
        isNull,
      );
      expect(detector.suppressesContinuous, isTrue);
    }
  });

  test('cooldown and stationary dwell allow one fresh opposite flick', () {
    expect(flick().single.direction, FlickDirection.down);
    final events = <FlickEvent>[];
    for (final (ms, y) in [
      (249, 0.74),
      (332, 0.82),
      (415, 0.9),
      (498, 0.82),
      (581, 0.74),
      (664, 0.74),
      (747, 0.74),
      (830, 0.74),
      (913, 0.66),
      (996, 0.58),
    ]) {
      final event = detector.process(sample(ms, y));
      if (event != null) events.add(event);
    }
    expect(events, hasLength(1));
    expect(events.single.direction, FlickDirection.up);
  });

  test('tracking loss after a trigger cannot bypass cooldown and rearm', () {
    expect(flick(), hasLength(1));
    expect(detector.process(sample(249, null)), isNull);
    for (final (ms, y) in [(332, 0.5), (415, 0.42), (498, 0.34)]) {
      expect(detector.process(sample(ms, y)), isNull);
    }
  });

  test('watchdog tracking loss preserves a fired gesture lock', () {
    expect(flick(), hasLength(1));
    detector.loseTracking();
    for (final (ms, y) in [(249, 0.5), (332, 0.42), (415, 0.34)]) {
      expect(detector.process(sample(ms, y)), isNull);
    }
    expect(detector.suppressesContinuous, isTrue);
  });

  test('reset clears candidates and trigger lock for a new session', () {
    expect(flick(), hasLength(1));
    detector.reset();
    expect(detector.suppressesContinuous, isFalse);
    expect(flick(direction: -1).single.direction, FlickDirection.up);
  });

  test('actual irregular timestamps determine flick velocity', () {
    final events = <FlickEvent>[];
    for (final ms in [0, 57, 139]) {
      final event = detector.process(sample(ms, 0.3 + 0.9 * ms / 1000));
      if (event != null) events.add(event);
    }
    expect(events, hasLength(1));
    expect(events.single.verticalVelocity, closeTo(0.9, 1e-6));
    expect(events.single.duration, const Duration(milliseconds: 139));
  });

  test('duplicate and out-of-order timestamps cannot generate a flick', () {
    detector.process(sample(0, 0.5));
    detector.process(sample(83, 0.58));
    expect(detector.process(sample(83, 0.9)), isNull);
    expect(detector.process(sample(40, 0.1)), isNull);
    expect(detector.process(sample(166, 0.58)), isNull);
  });

  test('long sample gap discards candidate history', () {
    detector.process(sample(0, 0.5));
    detector.process(sample(83, 0.58));
    expect(detector.process(sample(500, 0.66)), isNull);
    expect(detector.process(sample(583, 0.66)), isNull);
    expect(detector.process(sample(666, 0.66)), isNull);
  });

  test('direction reversal rejects an inconsistent path', () {
    for (final (ms, y) in [(0, 0.5), (83, 0.58), (166, 0.5), (249, 0.42)]) {
      expect(detector.process(sample(ms, y)), isNull);
    }
  });

  for (final consistency in [0.75, 0.85]) {
    test('direction consistency $consistency governs a small reversal', () {
      final service = FlickDetector(
        FlickDetectionConfig(
          minimumVelocity: 0.4,
          minimumDisplacement: 0.05,
          directionConsistency: consistency,
        ),
      );
      expect(service.process(sample(0, 0.5)), isNull);
      expect(service.process(sample(100, 0.59)), isNull);
      final event = service.process(sample(110, 0.58));
      // Net travel 0.08 / path 0.10 = 0.8; the reversal is within rearmRadius.
      expect(event != null, consistency < 0.8);
      if (event != null) expect(event.direction, FlickDirection.down);
    });
  }

  for (final fps in [12, 15]) {
    test(
      'recognizes a flick at $fps FPS without assuming a fixed interval',
      () {
        final interval = (1000000 / fps).ceil();
        final events = <FlickEvent>[];
        for (var i = 0; i < 4; i++) {
          final timestamp = Duration(microseconds: i * interval);
          final result = sample(0, 0.3 + timestamp.inMicroseconds / 1000000);
          final event = detector.process(
            HandTrackingResult(timestamp: timestamp, pose: result.pose),
          );
          if (event != null) events.add(event);
        }
        expect(events, hasLength(1));
        expect(events.single.verticalVelocity, closeTo(1, 1e-6));
      },
    );
  }

  for (final (duration, expected) in [
    (79, false),
    (80, true),
    (350, true),
    (351, false),
  ]) {
    test('gesture duration $duration ms respects inclusive boundaries', () {
      final service = FlickDetector(
        const FlickDetectionConfig(
          minimumVelocity: 0.1,
          minimumDisplacement: 0.01,
          rearmVelocity: 0.02,
          rearmRadius: 0.002,
        ),
      );
      expect(service.process(sample(0, 0.2)), isNull);
      expect(service.process(sample(duration ~/ 2, 0.25)), isNull);
      final event = service.process(sample(duration, 0.3));
      expect(event != null, expected);
    });
  }

  test('minimum displacement and velocity are independently required', () {
    detector = FlickDetector(const FlickDetectionConfig(minimumVelocity: 0.8));
    for (final speed in [0.79, 0.81]) {
      detector.reset();
      final events = <FlickEvent>[];
      for (final ms in [0, 83, 166]) {
        final event = detector.process(sample(ms, 0.3 + speed * ms / 1000));
        if (event != null) events.add(event);
      }
      expect(events, hasLength(speed < 0.8 ? 0 : 1));
    }
    detector.reset();
    for (final (ms, y) in [(0, 0.3), (40, 0.34), (80, 0.38)]) {
      expect(detector.process(sample(ms, y)), isNull);
    }
  });

  test('starting a flick stops an already active continuous gesture', () {
    for (var i = 0; i < 10; i++) {
      coordinator.process(sample(i * 83, 0.3 + i * 0.015));
    }
    final candidate = coordinator.process(sample(830, 0.515));
    expect(candidate.flick, isNull);
    expect(scrolling(candidate.motion), isFalse);
    final triggered = coordinator.process(sample(913, 0.595));
    expect(triggered.flick?.direction, FlickDirection.down);
    expect(scrolling(triggered.motion), isFalse);
  });

  test('invalid duration configuration fails at detector construction', () {
    for (final config in [
      const FlickDetectionConfig(minimumDuration: Duration.zero),
      const FlickDetectionConfig(maximumDuration: Duration(milliseconds: 79)),
      const FlickDetectionConfig(cooldown: Duration(milliseconds: -1)),
      const FlickDetectionConfig(maximumSampleGap: Duration.zero),
      const FlickDetectionConfig(rearmDuration: Duration.zero),
    ]) {
      expect(() => FlickDetector(config), throwsArgumentError);
    }
  });
}
