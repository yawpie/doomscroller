import 'package:flutter_test/flutter_test.dart';
import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/gesture/application/windowed_motion_detection_service.dart';
import 'package:autoscroll_compvis/features/gesture/domain/hand_motion.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_models.dart';

HandTrackingResult sample(int ms, double? y) => HandTrackingResult(
  timestamp: Duration(milliseconds: ms),
  pose: y == null
      ? null
      : HandPose(
          landmarks: List.generate(21, (_) => HandLandmark(x: 0.5, y: y, z: 0)),
        ),
);

void main() {
  late WindowedMotionDetectionService detector;
  setUp(() => detector = WindowedMotionDetectionService(const MotionConfig()));

  List<HandMotion> sequence(double speed, {int count = 12}) => List.generate(
    count,
    (i) => detector.process(sample(i * 100, 0.5 + speed * i * 0.1)),
  );

  test('stationary hand warms up then stays idle', () {
    final motions = sequence(0);
    expect(motions.first.action, HandAction.start);
    expect(motions.skip(1).every((m) => m.action == HandAction.idle), isTrue);
    expect(motions.last.verticalVelocity, closeTo(0, 0.000001));
  });

  for (final speed in [-0.08, -0.4, 0.08, 0.4]) {
    final expected = speed < 0 ? HandAction.moveUp : HandAction.moveDown;
    test('${speed.abs() < 0.1 ? 'slow' : 'fast'} ${expected.name}', () {
      final motions = sequence(speed);
      expect(motions.take(3).any((m) => m.action == expected), isFalse);
      expect(motions.last.action, expected);
      expect(motions.last.verticalVelocity, closeTo(speed, 0.012));
      expect(motions.last.verticalDisplacement.sign, speed.sign);
    });
  }

  test('small jitter stays idle', () {
    final actions = List.generate(
      40,
      (i) => detector
          .process(sample(i * 83, 0.5 + (i.isEven ? 0.003 : -0.003)))
          .action,
    );
    expect(
      actions.any((a) => a == HandAction.moveUp || a == HandAction.moveDown),
      isFalse,
    );
  });

  test('temporary missing landmarks stop and require a fresh warmup', () {
    expect(sequence(0.2).last.action, HandAction.moveDown);
    expect(detector.process(sample(1200, null)).action, HandAction.stop);
    expect(detector.process(sample(1300, null)).action, HandAction.idle);
    expect(detector.process(sample(1400, 0.2)).action, HandAction.start);
    expect(detector.process(sample(1500, 0.2)).action, HandAction.idle);
    expect(detector.process(sample(1600, 0.2)).action, HandAction.idle);
    expect(
      detector.process(sample(1700, 0.2)).verticalVelocity,
      closeTo(0, 1e-6),
    );
  });

  test('movement settles back to idle', () {
    sequence(0.2);
    final motions = List.generate(
      20,
      (i) => detector.process(sample(1200 + i * 100, 0.72)),
    );
    expect(motions.last.action, HandAction.idle);
  });

  test('long frame gap resets movement history', () {
    sequence(0.2);
    final motion = detector.process(sample(3000, 0.1));
    expect(motion.action, HandAction.start);
    expect(motion.verticalVelocity, 0);
  });

  test('duplicate and out-of-order timestamps cannot emit movement', () {
    sequence(0.2);
    expect(detector.process(sample(1100, 0.1)).action, HandAction.idle);
    expect(detector.process(sample(1000, 0.1)).action, HandAction.idle);
  });

  test('velocity accounts for uneven frame spacing', () {
    HandMotion motion = const HandMotion(action: HandAction.idle);
    for (final ms in [0, 85, 200, 290, 410, 530, 640, 800, 910, 1020]) {
      motion = detector.process(sample(ms, 0.3 + 0.2 * ms / 1000));
    }
    expect(motion.action, HandAction.moveDown);
    expect(motion.verticalVelocity, closeTo(0.2, 0.025));
  });

  test('reversal stops before a confirmed opposite direction', () {
    sequence(0.2);
    final actions = List.generate(
      15,
      (i) =>
          detector.process(sample(1200 + i * 100, 0.72 - 0.3 * i * 0.1)).action,
    );
    expect(actions, contains(HandAction.idle));
    expect(actions.last, HandAction.moveUp);
  });

  test('malformed landmarks are rejected at the domain boundary', () {
    expect(() => HandPose(landmarks: []), throwsArgumentError);
    expect(
      () => HandPose(
        landmarks: List.generate(
          21,
          (_) => const HandLandmark(x: double.nan, y: 0.5, z: 0),
        ),
      ),
      throwsArgumentError,
    );
  });
}
