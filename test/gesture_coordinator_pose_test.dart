import 'package:flutter_test/flutter_test.dart';
import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/gesture/application/flick_detector.dart';
import 'package:autoscroll_compvis/features/gesture/application/gesture_coordinator.dart';
import 'package:autoscroll_compvis/features/gesture/application/hand_pose_detector.dart';
import 'package:autoscroll_compvis/features/gesture/application/windowed_motion_detection_service.dart';
import 'package:autoscroll_compvis/features/gesture/domain/flick_event.dart';
import 'package:autoscroll_compvis/features/gesture/domain/hand_motion.dart';
import 'package:autoscroll_compvis/features/gesture/domain/motion_detection_service.dart';
import 'package:autoscroll_compvis/features/gesture/domain/pose_event.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_models.dart';

import 'support/hand_pose_fixtures.dart';

class RecordingMotionDetector implements MotionDetectionService {
  int calls = 0;
  int resets = 0;

  @override
  HandMotion process(HandTrackingResult result) {
    calls++;
    return const HandMotion(action: HandAction.moveDown, verticalVelocity: 0.1);
  }

  @override
  void reset() => resets++;
}

bool scrolling(HandMotion motion) =>
    motion.action == HandAction.moveUp || motion.action == HandAction.moveDown;

void main() {
  late RecordingMotionDetector motion;
  late HandPoseDetector pose;
  late GestureCoordinator coordinator;

  setUp(() {
    motion = RecordingMotionDetector();
    pose = HandPoseDetector(const HandPoseDetectionConfig());
    coordinator = GestureCoordinator(
      motionDetector: motion,
      flickDetector: FlickDetector(const FlickDetectionConfig()),
      poseDetector: pose,
    );
  });

  test(
    'pose candidate and latch suppress motion and every competing action',
    () {
      final decisions = [
        for (var i = 0; i <= 40; i++)
          coordinator.process(
            poseSample(i * 100, RecognizedHandPose.pointingDown),
          ),
      ];
      expect(
        decisions.where((decision) => decision.pose != null),
        hasLength(1),
      );
      expect(decisions.every((decision) => decision.flick == null), isTrue);
      expect(
        decisions.every((decision) => !scrolling(decision.motion)),
        isTrue,
      );
      expect(
        decisions.every((decision) => decision.suppressesContinuous),
        isTrue,
      );
      expect(motion.calls, 0);
      expect(motion.resets, greaterThan(0));
    },
  );

  test('a flick candidate retains ownership when pointing appears', () {
    coordinator.process(poseSample(0, RecognizedHandPose.neutral, y: 0.4));
    final candidate = coordinator.process(
      poseSample(100, RecognizedHandPose.neutral, y: 0.48),
    );
    expect(candidate.suppressesContinuous, isTrue);
    final completed = coordinator.process(
      poseSample(200, RecognizedHandPose.pointingDown, y: 0.56),
    );
    expect(completed.flick?.direction, FlickDirection.down);
    expect(completed.pose, isNull);
    expect(scrolling(completed.motion), isFalse);
    for (var i = 3; i < 8; i++) {
      final decision = coordinator.process(
        poseSample(i * 100, RecognizedHandPose.pointingDown, y: 0.56),
      );
      expect(decision.pose, isNull);
      expect(decision.flick, isNull);
    }
  });

  test('each inference result has at most one actionable gesture', () {
    final samples = [
      for (var i = 0; i <= 8; i++)
        poseSample(i * 100, RecognizedHandPose.pointingUp),
      for (var i = 9; i <= 11; i++)
        poseSample(i * 100, RecognizedHandPose.neutral),
      poseSample(1200, RecognizedHandPose.neutral, y: 0.58),
      poseSample(1300, RecognizedHandPose.pointingDown, y: 0.66),
    ];
    for (final sample in samples) {
      final decision = coordinator.process(sample);
      final actions = [
        decision.pose != null,
        decision.flick != null,
        scrolling(decision.motion),
      ].where((active) => active).length;
      expect(actions, lessThanOrEqualTo(1));
    }
  });

  test('suppression discards the previous continuous motion window', () {
    final realMotion = WindowedMotionDetectionService(
      const MotionDetectionConfig(),
    );
    coordinator = GestureCoordinator(
      motionDetector: realMotion,
      flickDetector: FlickDetector(const FlickDetectionConfig()),
      poseDetector: pose,
    );
    GestureDecision? previous;
    for (var i = 0; i < 14; i++) {
      previous = coordinator.process(
        poseSample(i * 100, RecognizedHandPose.unknown, y: 0.3 + i * 0.015),
      );
    }
    expect(scrolling(previous!.motion), isTrue);
    expect(
      coordinator
          .process(poseSample(1400, RecognizedHandPose.pointingDown, y: 0.5))
          .motion
          .action,
      HandAction.idle,
    );
    final released = coordinator.process(
      poseSample(1500, RecognizedHandPose.unknown, y: 0.515),
    );
    expect(scrolling(released.motion), isFalse);
    final fresh = coordinator.process(
      poseSample(1600, RecognizedHandPose.unknown, y: 0.53),
    );
    expect(scrolling(fresh.motion), isFalse);
  });

  test('flick can start from a fresh baseline after pose re-arm', () {
    for (final ms in [0, 100, 200]) {
      coordinator.process(poseSample(ms, RecognizedHandPose.pointingDown));
    }
    for (final ms in [500, 600, 700]) {
      coordinator.process(poseSample(ms, RecognizedHandPose.neutral));
    }
    coordinator.process(poseSample(800, RecognizedHandPose.neutral, y: 0.4));
    coordinator.process(poseSample(900, RecognizedHandPose.neutral, y: 0.48));
    final decision = coordinator.process(
      poseSample(1000, RecognizedHandPose.neutral, y: 0.56),
    );
    expect(decision.flick?.direction, FlickDirection.down);
    expect(decision.pose, isNull);
    expect(scrolling(decision.motion), isFalse);
  });

  test(
    'configuration resets active ownership and starts a fresh pose candidate',
    () {
      for (final ms in [0, 100, 200]) {
        coordinator.process(poseSample(ms, RecognizedHandPose.pointingDown));
      }
      expect(pose.phase, HandPosePhase.latched);
      final resets = motion.resets;
      coordinator.configure(mode: GestureMode.poseOnly);
      expect(pose.phase, HandPosePhase.ready);
      expect(pose.lastEvent, isNull);
      expect(motion.resets, greaterThan(resets));
      expect(coordinator.flickDetector.ownsGesture, isFalse);
      for (final ms in [300, 400]) {
        expect(
          coordinator
              .process(poseSample(ms, RecognizedHandPose.pointingUp))
              .pose,
          isNull,
        );
      }
      expect(
        coordinator
            .process(poseSample(500, RecognizedHandPose.pointingUp))
            .pose
            ?.pose,
        RecognizedHandPose.pointingUp,
      );
      coordinator.configure(
        detector: HandPoseDetector(
          const HandPoseDetectionConfig(enabled: false),
        ),
      );
      for (final ms in [600, 700, 800]) {
        final decision = coordinator.process(
          poseSample(ms, RecognizedHandPose.pointingDown),
        );
        expect(decision.pose, isNull);
        expect(decision.flick, isNull);
        expect(scrolling(decision.motion), isFalse);
      }
    },
  );

  test('pose-only mode disables flick and continuous actions', () {
    coordinator = GestureCoordinator(
      motionDetector: motion,
      flickDetector: FlickDetector(const FlickDetectionConfig()),
      poseDetector: pose,
      mode: GestureMode.poseOnly,
    );
    for (var i = 0; i < 5; i++) {
      final decision = coordinator.process(
        poseSample(i * 100, RecognizedHandPose.neutral, y: 0.3 + i * 0.08),
      );
      expect(decision.flick, isNull);
      expect(decision.pose, isNull);
      expect(scrolling(decision.motion), isFalse);
    }
    final decisions = [
      for (final ms in [500, 600, 700])
        coordinator.process(poseSample(ms, RecognizedHandPose.pointingUp)),
    ];
    expect(decisions.last.pose?.pose, RecognizedHandPose.pointingUp);
    expect(motion.calls, 0);
  });

  test(
    'tracking loss preserves pose ownership and explicit reset clears it',
    () {
      for (final ms in [0, 100, 200]) {
        coordinator.process(poseSample(ms, RecognizedHandPose.pointingDown));
      }
      coordinator.loseTracking();
      for (final ms in [500, 600, 700]) {
        final decision = coordinator.process(
          poseSample(ms, RecognizedHandPose.pointingDown),
        );
        expect(decision.pose, isNull);
        expect(decision.flick, isNull);
        expect(scrolling(decision.motion), isFalse);
      }
      coordinator.reset();
      for (final ms in [800, 900]) {
        expect(
          coordinator
              .process(poseSample(ms, RecognizedHandPose.pointingUp))
              .pose,
          isNull,
        );
      }
      expect(
        coordinator
            .process(poseSample(1000, RecognizedHandPose.pointingUp))
            .pose
            ?.pose,
        RecognizedHandPose.pointingUp,
      );
    },
  );
}
