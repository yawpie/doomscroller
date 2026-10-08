import 'dart:math' as math;

import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/gesture/application/hand_pose_classifier.dart';
import 'package:autoscroll_compvis/features/gesture/application/hand_pose_detector.dart';
import 'package:autoscroll_compvis/features/gesture/domain/pose_event.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_models.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/hand_pose_fixtures.dart';

HandLandmark point(int index, double x, double y, {double z = 0}) =>
    HandLandmark(index: index, x: x, y: y, z: z);

List<HandLandmark> withJointAngle(double angle, {bool index = true}) {
  final points = handPoseLandmarks(RecognizedHandPose.pointingUp);
  final deflection = (180 - angle) * math.pi / 180;
  for (var mcp = index ? 5 : 9; mcp <= (index ? 5 : 17); mcp += 4) {
    final pip = points[mcp + 1];
    final dipX = pip.x + 0.075 * math.sin(deflection);
    final dipY = pip.y - 0.075 * math.cos(deflection);
    points[mcp + 2] = point(mcp + 2, dipX, dipY);
    points[mcp + 3] = index
        ? point(
            mcp + 3,
            dipX + 0.06 * math.sin(deflection),
            dipY - 0.06 * math.cos(deflection),
          )
        : point(mcp + 3, dipX, dipY + 0.05);
  }
  return points;
}

void main() {
  const up = RecognizedHandPose.pointingUp;
  const down = RecognizedHandPose.pointingDown;
  const neutral = RecognizedHandPose.neutral;
  const unknown = RecognizedHandPose.unknown;
  const config = HandPoseDetectionConfig();
  late HandPoseClassifier classifier;
  late HandPoseDetector detector;

  setUp(() {
    classifier = HandPoseClassifier(config);
    detector = HandPoseDetector(config);
  });

  group('classifier', () {
    for (final pose in [up, down, neutral]) {
      test('recognizes ${pose.name}', () {
        expect(classifier.classify(handPoseLandmarks(pose)), pose);
      });
    }

    test('closed fist is unknown, never neutral', () {
      expect(classifier.classify(handPoseLandmarks(unknown)), unknown);
    });

    for (final pose in [up, down]) {
      test('sideways ${pose.name} is unknown', () {
        expect(
          classifier.classify(handPoseLandmarks(pose, rotation: math.pi / 2)),
          unknown,
        );
      });

      test('moderate wrist rotation preserves ${pose.name}', () {
        for (final angle in [-0.45, 0.45]) {
          expect(
            classifier.classify(handPoseLandmarks(pose, rotation: angle)),
            pose,
          );
        }
      });

      test('${pose.name} tolerates different sizes and image positions', () {
        for (final (scale, x, y) in [
          (0.45, 0.2, 0.3),
          (0.6, 0.8, 0.7),
          (1.25, 0.5, 0.5),
        ]) {
          expect(
            classifier.classify(
              handPoseLandmarks(pose, scale: scale, x: x, y: y),
            ),
            pose,
          );
        }
      });

      test('${pose.name} entry/exit direction thresholds are separate', () {
        final points = handPoseLandmarks(pose, rotation: 0.65);
        expect(classifier.classify(points), unknown);
        expect(classifier.classify(points, activePose: pose), pose);
        expect(
          classifier.classify(points, activePose: pose == up ? down : up),
          unknown,
        );
        expect(
          classifier.classify(
            handPoseLandmarks(pose, rotation: 0.9),
            activePose: pose,
          ),
          unknown,
        );
      });
    }

    test('seeded small landmark jitter preserves stable poses', () {
      final random = math.Random(24);
      for (final pose in [up, down, neutral]) {
        for (var trial = 0; trial < 30; trial++) {
          final points = handPoseLandmarks(pose).map((p) {
            return point(
              p.index,
              p.x + (random.nextDouble() - 0.5) * 0.002,
              p.y + (random.nextDouble() - 0.5) * 0.002,
            );
          }).toList();
          expect(classifier.classify(points), pose);
        }
      }
    });

    test('index PIP extension uses hysteresis', () {
      expect(classifier.classify(withJointAngle(165)), up);
      expect(classifier.classify(withJointAngle(155)), unknown);
      expect(classifier.classify(withJointAngle(155), activePose: up), up);
      expect(classifier.classify(withJointAngle(145), activePose: up), unknown);
    });

    test('index DIP must also be extended', () {
      final points = handPoseLandmarks(up);
      points[8] = point(8, points[7].x + 0.06, points[7].y);
      expect(classifier.classify(points), unknown);
    });

    test('middle/ring/pinky curl uses hysteresis', () {
      expect(classifier.classify(withJointAngle(105, index: false)), up);
      final exitOnly = withJointAngle(120, index: false);
      expect(classifier.classify(exitOnly), unknown);
      expect(classifier.classify(exitOnly, activePose: up), up);
      expect(
        classifier.classify(withJointAngle(130, index: false), activePose: up),
        unknown,
      );
    });

    test('partially open fingers do not count as neutral', () {
      final points = handPoseLandmarks(neutral);
      final fist = handPoseLandmarks(unknown);
      for (var i = 13; i < 17; i++) {
        points[i] = fist[i];
      }
      expect(classifier.classify(points), unknown);
    });

    test('thumb configuration is unconstrained', () {
      final points = handPoseLandmarks(up);
      for (var i = 1; i <= 4; i++) {
        points[i] = point(i, 0.5, 0.5);
      }
      expect(classifier.classify(points), up);
    });

    test('incomplete and incorrectly ordered landmarks are unknown', () {
      final points = handPoseLandmarks(up);
      expect(classifier.classify(points.sublist(0, 20)), unknown);
      expect(classifier.classify([...points, points.last]), unknown);
      points[20] = point(19, points[20].x, points[20].y);
      expect(classifier.classify(points), unknown);
    });

    test('all 21 landmarks require finite normalized x/y and finite z', () {
      for (var i = 0; i < 21; i++) {
        for (final value in [double.nan, double.infinity, -0.001, 1.001]) {
          final points = handPoseLandmarks(up);
          points[i] = point(i, value, points[i].y);
          expect(classifier.classify(points), unknown);
          points[i] = point(i, 0.5, value);
          expect(classifier.classify(points), unknown);
        }
        final points = handPoseLandmarks(up);
        points[i] = point(i, points[i].x, points[i].y, z: double.nan);
        expect(classifier.classify(points), unknown);
      }
    });

    test('relative depth may be negative or exceed one', () {
      final points = handPoseLandmarks(up).map((p) {
        return point(p.index, p.x, p.y, z: p.index.isEven ? -2 : 3);
      }).toList();
      expect(classifier.classify(points), up);
    });

    test('collapsed palm, finger segment, and tiny finger are unknown', () {
      final points = handPoseLandmarks(up);
      points[0] = point(0, points[9].x, points[9].y);
      expect(classifier.classify(points), unknown);
      for (final collapsed in [6, 7, 8, 10, 11, 12, 14, 15, 16, 18, 19, 20]) {
        final points = handPoseLandmarks(up);
        points[collapsed] = point(
          collapsed,
          points[collapsed - 1].x,
          points[collapsed - 1].y,
        );
        expect(classifier.classify(points), unknown);
      }
      final tiny = handPoseLandmarks(up);
      for (var i = 6; i <= 8; i++) {
        tiny[i] = point(i, tiny[5].x, tiny[5].y - (i - 5) * 0.002);
      }
      expect(classifier.classify(tiny), unknown);
    });
  });

  group('temporal detector', () {
    PoseEvent confirm({RecognizedHandPose pose = up, int start = 0}) {
      expect(detector.process(poseSample(start, pose)), isNull);
      expect(detector.phase, HandPosePhase.confirming);
      expect(detector.process(poseSample(start + 90, pose)), isNull);
      expect(detector.confirmationProgress, closeTo(0.5, 1e-6));
      final event = detector.process(poseSample(start + 180, pose));
      expect(event, isNotNull);
      return event!;
    }

    test('short pose emits no event', () {
      for (final ms in [0, 50, 100, 179]) {
        expect(detector.process(poseSample(ms, up)), isNull);
      }
      expect(detector.process(poseSample(180, neutral)), isNull);
      expect(detector.phase, HandPosePhase.ready);
    });

    test('confirmation requires both duration and distinct observations', () {
      detector.process(poseSample(0, down));
      expect(detector.process(poseSample(180, down)), isNull);
      final event = detector.process(poseSample(181, down));
      expect(event?.pose, down);
      expect(event?.timestamp, const Duration(milliseconds: 181));
      expect(detector.phase, HandPosePhase.latched);
      expect(detector.lastEvent, same(event));
    });

    test('held pose emits exactly once for several seconds', () {
      final event = confirm();
      for (var ms = 270; ms <= 5000; ms += 90) {
        expect(detector.process(poseSample(ms, up)), isNull);
        expect(detector.phase, HandPosePhase.latched);
        expect(detector.suppressesContinuous, isTrue);
      }
      expect(detector.lastEvent, same(event));
      expect(detector.cooldownRemaining, Duration.zero);
    });

    test('duplicate timestamps discard confirmation evidence', () {
      detector.process(poseSample(0, up));
      detector.process(poseSample(90, up));
      for (var i = 0; i < 5; i++) {
        expect(detector.process(poseSample(90, up)), isNull);
      }
      expect(detector.phase, HandPosePhase.ready);
      expect(detector.process(poseSample(180, up)), isNull);
      expect(detector.process(poseSample(270, up)), isNull);
      expect(detector.process(poseSample(360, up))?.pose, up);
    });

    test('out of order and negative timestamps retain high water', () {
      detector.process(poseSample(0, up));
      detector.process(poseSample(90, up));
      for (final ms in [40, -1, 89, 90]) {
        expect(detector.process(poseSample(ms, up)), isNull);
        expect(detector.phase, HandPosePhase.ready);
      }
      expect(detector.process(poseSample(180, up)), isNull);
      expect(detector.process(poseSample(270, up)), isNull);
      expect(detector.process(poseSample(360, up))?.pose, up);
    });

    test('irregular sample intervals use actual timestamps', () {
      for (final ms in [0, 43, 117, 179]) {
        expect(detector.process(poseSample(ms, up)), isNull);
      }
      expect(
        detector.process(poseSample(203, up))?.timestamp,
        const Duration(milliseconds: 203),
      );
    });

    test('excessive gap starts fresh confirmation at the new sample', () {
      detector.process(poseSample(0, up));
      detector.process(poseSample(90, up));
      expect(detector.process(poseSample(400, up)), isNull);
      expect(detector.confirmationProgress, 0);
      expect(detector.process(poseSample(490, up)), isNull);
      expect(detector.process(poseSample(580, up))?.pose, up);
    });

    test('candidate direction reversal starts independent evidence', () {
      detector.process(poseSample(0, up));
      detector.process(poseSample(90, up));
      expect(detector.process(poseSample(180, down)), isNull);
      expect(detector.process(poseSample(270, down)), isNull);
      expect(detector.process(poseSample(360, down))?.pose, down);
    });

    test('direct up/down transition cannot unlock a confirmed pose', () {
      confirm();
      for (var ms = 270; ms < 3000; ms += 90) {
        expect(detector.process(poseSample(ms, down)), isNull);
        expect(detector.phase, HandPosePhase.latched);
      }
    });

    test('short neutral release never rearms', () {
      confirm();
      detector.process(poseSample(700, neutral));
      detector.process(poseSample(879, neutral));
      expect(detector.phase, HandPosePhase.waitingRearm);
      detector.process(poseSample(880, down));
      expect(detector.phase, HandPosePhase.latched);
      expect(detector.process(poseSample(1060, down)), isNull);
    });

    test('neutral dwell alone cannot bypass cooldown', () {
      confirm();
      detector.process(poseSample(270, neutral));
      detector.process(poseSample(360, neutral));
      detector.process(poseSample(450, neutral));
      expect(detector.phase, HandPosePhase.waitingRearm);
      expect(detector.cooldownRemaining, const Duration(milliseconds: 180));
      detector.process(poseSample(540, neutral));
      detector.process(poseSample(630, neutral));
      expect(detector.phase, HandPosePhase.ready);
      expect(detector.suppressesContinuous, isFalse);
      expect(confirm(pose: down, start: 720).pose, down);
    });

    test('cooldown alone never unlocks a held pose', () {
      confirm();
      detector.process(poseSample(1000, up));
      expect(detector.cooldownRemaining, Duration.zero);
      expect(detector.phase, HandPosePhase.latched);
    });

    test('neutral must have distinct samples and acceptable gaps', () {
      confirm();
      detector.process(poseSample(700, neutral));
      detector.process(poseSample(700, neutral));
      expect(detector.phase, HandPosePhase.latched);
      detector.process(poseSample(800, neutral));
      detector.process(poseSample(1100, neutral));
      expect(detector.phase, HandPosePhase.waitingRearm);
      detector.process(poseSample(1190, neutral));
      expect(detector.phase, HandPosePhase.waitingRearm);
      detector.process(poseSample(1280, neutral));
      expect(detector.phase, HandPosePhase.ready);
    });

    test('unknown interrupts continuous neutral evidence', () {
      confirm();
      detector.process(poseSample(700, neutral));
      detector.process(poseSample(790, unknown));
      expect(detector.phase, HandPosePhase.latched);
      detector.process(poseSample(880, neutral));
      detector.process(poseSample(970, neutral));
      expect(detector.phase, HandPosePhase.waitingRearm);
      detector.process(poseSample(1060, neutral));
      expect(detector.phase, HandPosePhase.ready);
    });

    test('tracking loss during confirmation discards candidate', () {
      detector.process(poseSample(0, up));
      detector.process(poseSample(90, up));
      detector.process(poseSample(180, null));
      expect(detector.phase, HandPosePhase.ready);
      expect(detector.process(poseSample(270, up)), isNull);
      expect(detector.process(poseSample(360, up)), isNull);
      expect(detector.process(poseSample(450, up))?.pose, up);
    });

    test(
      'explicit loss discards candidate but retains timestamp high water',
      () {
        detector.process(poseSample(0, up));
        detector.process(poseSample(90, up));
        detector.loseTracking();
        expect(detector.process(poseSample(90, up)), isNull);
        expect(detector.phase, HandPosePhase.ready);
        expect(detector.process(poseSample(180, up)), isNull);
        expect(detector.process(poseSample(270, up)), isNull);
        expect(detector.process(poseSample(360, up))?.pose, up);
      },
    );

    test('tracking loss and reacquisition preserve confirmed latch', () {
      final event = confirm();
      detector.process(poseSample(270, null));
      detector.loseTracking();
      expect(detector.currentPose, unknown);
      expect(detector.phase, HandPosePhase.latched);
      for (final ms in [1000, 1090, 1180, 1270]) {
        expect(detector.process(poseSample(ms, up)), isNull);
      }
      expect(detector.lastEvent, same(event));
      expect(detector.phase, HandPosePhase.latched);
    });

    test('loss during neutral release does not count as neutral time', () {
      confirm();
      detector.process(poseSample(700, neutral));
      detector.process(poseSample(790, null));
      detector.process(poseSample(1000, neutral));
      detector.process(poseSample(1090, neutral));
      expect(detector.phase, HandPosePhase.waitingRearm);
      detector.process(poseSample(1180, neutral));
      expect(detector.phase, HandPosePhase.ready);
    });

    test('reset clears confirmation, latch, events, and timestamps', () {
      confirm();
      detector.reset();
      expect(detector.phase, HandPosePhase.ready);
      expect(detector.currentPose, unknown);
      expect(detector.lastEvent, isNull);
      expect(detector.suppressesContinuous, isFalse);
      expect(confirm().pose, up);
      detector.reset();
      detector.process(poseSample(0, up));
      detector.reset();
      expect(detector.confirmationProgress, 0);
      expect(confirm(pose: down).pose, down);
    });

    test('hysteresis sustains candidate inside exit direction threshold', () {
      detector.process(poseSample(0, up));
      detector.process(poseSample(90, up, rotation: 0.65));
      expect(detector.process(poseSample(180, up, rotation: 0.65))?.pose, up);
    });

    test('exceeding hysteresis cancels a candidate safely', () {
      detector.process(poseSample(0, up));
      detector.process(poseSample(90, up, rotation: 0.9));
      expect(detector.phase, HandPosePhase.ready);
      expect(detector.process(poseSample(180, up)), isNull);
    });

    test('disabled detector never claims ownership or emits events', () {
      detector = HandPoseDetector(
        const HandPoseDetectionConfig(enabled: false),
      );
      for (final ms in [0, 90, 180, 500]) {
        expect(detector.process(poseSample(ms, up)), isNull);
        expect(detector.phase, HandPosePhase.ready);
        expect(detector.suppressesContinuous, isFalse);
      }
    });
  });

  group('stable-pose re-arm', () {
    setUp(() {
      detector = HandPoseDetector(
        const HandPoseDetectionConfig(rearmMode: HandPoseRearmMode.stablePose),
      );
    });

    void confirm() {
      for (final ms in [0, 90]) {
        expect(detector.process(poseSample(ms, up)), isNull);
      }
      expect(detector.process(poseSample(180, up))?.pose, up);
    }

    test('held pose rearms at the configured boundary and reconfirms', () {
      confirm();
      for (final ms in [380, 580, 780, 980, 1179]) {
        expect(detector.process(poseSample(ms, up)), isNull);
        expect(detector.phase, HandPosePhase.waitingRearm);
      }
      expect(detector.process(poseSample(1180, up)), isNull);
      expect(detector.phase, HandPosePhase.ready);
      expect(detector.process(poseSample(1200, up)), isNull);
      expect(detector.process(poseSample(1290, up)), isNull);
      expect(detector.process(poseSample(1380, up))?.pose, up);
    });

    test('held pose repeats only after each dwell and new confirmation', () {
      detector = HandPoseDetector(
        const HandPoseDetectionConfig(
          rearmMode: HandPoseRearmMode.stablePose,
          stablePoseDuration: Duration(milliseconds: 500),
        ),
      );
      final events = <int>[];
      for (var ms = 0; ms <= 1800; ms += 100) {
        final event = detector.process(poseSample(ms, down));
        if (event != null) events.add(event.timestamp.inMilliseconds);
      }
      expect(events, [200, 1000, 1800]);
    });

    test('a changed direction restarts stable evidence', () {
      confirm();
      detector.process(poseSample(380, up));
      detector.process(poseSample(580, down));
      for (final ms in [780, 980, 1180, 1380, 1579]) {
        expect(detector.process(poseSample(ms, down)), isNull);
        expect(detector.phase, HandPosePhase.waitingRearm);
      }
      detector.process(poseSample(1580, down));
      expect(detector.phase, HandPosePhase.ready);
    });

    for (final interruption in [
      'unknown',
      'missing',
      'gap',
      'duplicate',
      'reversed',
    ]) {
      test('$interruption interrupts timed re-arm without unlocking', () {
        confirm();
        detector.process(poseSample(380, up));
        switch (interruption) {
          case 'unknown':
            detector.process(poseSample(580, unknown));
          case 'missing':
            detector.process(poseSample(580, null));
          case 'gap':
            detector.process(poseSample(780, up));
          case 'duplicate':
            detector.process(poseSample(380, up));
          case 'reversed':
            detector.process(poseSample(300, up));
        }
        final deadline = interruption == 'gap' ? 1780 : 1800;
        for (final ms in [800, 1000, 1200, 1400, 1600, deadline - 1]) {
          expect(detector.process(poseSample(ms, up)), isNull);
          expect(detector.phase, isNot(HandPosePhase.ready));
        }
        detector.process(poseSample(deadline, up));
        expect(detector.phase, HandPosePhase.ready);
      });
    }

    test('timed re-arm still requires cooldown', () {
      detector = HandPoseDetector(
        const HandPoseDetectionConfig(
          rearmMode: HandPoseRearmMode.stablePose,
          stablePoseDuration: Duration(milliseconds: 100),
        ),
      );
      confirm();
      for (final ms in [280, 480, 629]) {
        detector.process(poseSample(ms, up));
        expect(detector.phase, HandPosePhase.waitingRearm);
      }
      detector.process(poseSample(630, up));
      expect(detector.phase, HandPosePhase.ready);
    });

    test('explicit reset clears timed evidence', () {
      confirm();
      detector.process(poseSample(380, up));
      detector.reset();
      expect(detector.lastEvent, isNull);
      expect(detector.phase, HandPosePhase.ready);
      confirm();
      detector.process(poseSample(380, up));
      expect(detector.phase, HandPosePhase.waitingRearm);
    });
  });

  test('re-arm config changes retain detection tuning', () {
    const tuned = HandPoseDetectionConfig(
      confirmationDuration: Duration(milliseconds: 360),
      minimumIndexAngle: 170,
      enabled: false,
    );
    final changed = tuned.copyWith(
      rearmMode: HandPoseRearmMode.stablePose,
      stablePoseDuration: const Duration(milliseconds: 1500),
    );
    expect(changed.confirmationDuration, tuned.confirmationDuration);
    expect(changed.minimumIndexAngle, tuned.minimumIndexAngle);
    expect(changed.enabled, isFalse);
    expect(changed.rearmMode, HandPoseRearmMode.stablePose);
    expect(changed.stablePoseDuration, const Duration(milliseconds: 1500));
  });

  test('invalid duration and geometry configs fail at construction', () {
    for (final invalid in [
      const HandPoseDetectionConfig(confirmationDuration: Duration.zero),
      const HandPoseDetectionConfig(releaseDuration: Duration.zero),
      const HandPoseDetectionConfig(stablePoseDuration: Duration.zero),
      const HandPoseDetectionConfig(
        stablePoseDuration: Duration(milliseconds: -1),
      ),
      const HandPoseDetectionConfig(cooldown: Duration(milliseconds: -1)),
      const HandPoseDetectionConfig(minimumObservations: 1),
      const HandPoseDetectionConfig(maximumSampleGap: Duration.zero),
      const HandPoseDetectionConfig(minimumIndexAngle: 181),
      const HandPoseDetectionConfig(exitIndexAngle: 161),
      const HandPoseDetectionConfig(exitIndexAngle: 0),
      const HandPoseDetectionConfig(maximumCurledAngle: 126),
      const HandPoseDetectionConfig(exitCurledAngle: 151),
      const HandPoseDetectionConfig(minimumVerticalAlignment: 1.01),
      const HandPoseDetectionConfig(exitVerticalAlignment: 0),
      const HandPoseDetectionConfig(exitVerticalAlignment: 0.86),
      const HandPoseDetectionConfig(minimumFingerLengthRatio: 0),
      const HandPoseDetectionConfig(minimumBoneLengthRatio: 0),
      const HandPoseDetectionConfig(minimumBoneLengthRatio: 0.2),
      const HandPoseDetectionConfig(minimumIndexAngle: double.nan),
      const HandPoseDetectionConfig(maximumCurledAngle: double.infinity),
    ]) {
      expect(() => HandPoseDetector(invalid), throwsArgumentError);
    }
  });
}
