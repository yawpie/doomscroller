import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/auto_scroll/application/scroll_velocity_mapper.dart';
import 'package:autoscroll_compvis/features/gesture/application/flick_detector.dart';
import 'package:autoscroll_compvis/features/gesture/application/gesture_coordinator.dart';
import 'package:autoscroll_compvis/features/gesture/application/hand_pose_classifier.dart';
import 'package:autoscroll_compvis/features/gesture/application/hand_pose_detector.dart';
import 'package:autoscroll_compvis/features/gesture/application/windowed_motion_detection_service.dart';
import 'package:autoscroll_compvis/features/gesture/domain/pose_event.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_models.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/hand_pose_fixtures.dart';

Map<String, Object?> sample(
  int ms,
  double? y, {
  RecognizedHandPose pose = RecognizedHandPose.unknown,
  double x = 0.5,
  double rotation = 0,
}) => {
  'timestampMs': ms,
  'landmarks': y == null
      ? null
      : handPoseLandmarks(
          pose,
          x: x,
          y: y,
          rotation: rotation,
        ).map((p) => {'index': p.index, 'x': p.x, 'y': p.y, 'z': p.z}).toList(),
};

List<Map<String, Object?>> movement(double step, {int count = 14}) => [
  for (var i = 0; i < count; i++) sample(i * 83, 0.5 + step * i),
];

Map<String, Object?> fixture(
  String id,
  List<Map<String, Object?>> samples, {
  Map<String, Object?> motion = const {},
  Map<String, Object?> flick = const {},
  Map<String, Object?> pose = const {},
  String mode = 'HYBRID',
  bool inverted = false,
}) => {
  'caseId': id,
  'config': {
    'mode': mode,
    'inverted': inverted,
    'motion': {
      'smoothingAlpha': 0.4,
      'windowSize': 4,
      'minimumFrames': 3,
      'movementThreshold': 0.018,
      'deadZone': 0.008,
      'minimumVelocity': 0.025,
      'confirmationFrames': 2,
      'directionCooldownMs': 150,
      'maximumSampleGapMs': 350,
      'maximumPositionJump': 0.15,
      'stationaryVelocity': 0.015,
      'calibrationDurationMs': 600,
      'calibrationRadius': 0.012,
      ...motion,
    },
    'flick': {
      'minimumVelocity': 0.4,
      'minimumDisplacement': 0.1,
      'minimumDurationMs': 80,
      'maximumDurationMs': 350,
      'cooldownMs': 500,
      'directionConsistency': 0.75,
      'maximumSampleGapMs': 250,
      'maximumPositionJump': 0.25,
      'rearmVelocity': 0.15,
      'rearmDurationMs': 150,
      'rearmRadius': 0.012,
      ...flick,
    },
    'pose': {
      'enabled': true,
      'confirmationDurationMs': 180,
      'releaseDurationMs': 180,
      'cooldownMs': 450,
      'rearmMode': 'OPEN_HAND',
      'stablePoseDurationMs': 1000,
      'minimumObservations': 3,
      'minimumIndexAngle': 160.0,
      'maximumCurledAngle': 110.0,
      'minimumVerticalAlignment': 0.85,
      'exitIndexAngle': 150.0,
      'exitCurledAngle': 125.0,
      'exitVerticalAlignment': 0.70,
      'minimumFingerLengthRatio': 0.35,
      'minimumBoneLengthRatio': 0.03,
      'maximumSampleGapMs': 250,
      ...pose,
    },
    'scroll': {
      'pixelsPerNormalizedUnit': 1800.0,
      'maximumPixelsPerSecond': 1200.0,
      'staleAfterMs': 350,
      'deadZone': 0.025,
      'smoothingAlpha': 0.3,
      'minimumSensitivity': 300.0,
      'maximumSensitivity': 4000.0,
      'flickAction': 'PAGE',
      'flickScrollDistance': 600.0,
      'flickDurationMs': 220,
    },
  },
  'samples': samples,
};

List<Map<String, Object?>> scenarios() {
  const up = RecognizedHandPose.pointingUp;
  const down = RecognizedHandPose.pointingDown;
  const neutral = RecognizedHandPose.neutral;
  List<Map<String, Object?>> hold(RecognizedHandPose pose, int until) => [
    for (var ms = 0; ms <= until; ms += 100) sample(ms, 0.5, pose: pose),
  ];
  final invalid = sample(100, 0.5);
  (invalid['landmarks'] as List)[8] = {
    'index': 8,
    'x': -0.1,
    'y': 0.5,
    'z': 0.0,
  };
  return [
    fixture('stationary_hand', movement(0)),
    fixture('slow_up', movement(-0.015)),
    fixture('slow_down', movement(0.015)),
    fixture('fast_up_flick', movement(-0.08, count: 4)),
    fixture('fast_down_flick', movement(0.08, count: 4)),
    fixture('short_high_speed', [
      sample(0, 0.5),
      sample(30, 0.58),
      sample(60, 0.66),
      sample(90, 0.66),
    ]),
    fixture('tracking_discontinuity', [
      sample(0, 0.5),
      sample(83, 0.52),
      sample(166, 0.18),
      sample(249, 0.18),
      sample(450, 0.18),
    ]),
    fixture('tracking_loss', [
      ...movement(0.015, count: 8),
      sample(700, null),
      sample(800, null),
    ]),
    fixture('reacquisition_after_loss', [
      ...movement(0.015, count: 8),
      sample(700, null),
      sample(800, 0.3),
      sample(900, 0.32),
      sample(1000, 0.34),
      sample(1100, 0.36),
    ]),
    fixture('duplicate_timestamps', [
      sample(0, 0.5),
      sample(83, 0.58),
      sample(83, 0.66),
      sample(166, 0.66),
      sample(249, 0.66),
      sample(415, 0.66),
    ]),
    fixture('non_increasing_timestamps', [
      sample(0, 0.5),
      sample(83, 0.58),
      sample(40, 0.6),
      sample(166, 0.66),
      sample(249, 0.66),
      sample(415, 0.66),
    ]),
    fixture('irregular_intervals', [
      for (final ms in [0, 85, 200, 290, 410, 530, 640, 800, 910, 1020])
        sample(ms, 0.3 + 0.2 * ms / 1000),
    ]),
    fixture('direction_reversal', [
      ...movement(0.015, count: 8),
      for (var i = 0; i < 10; i++) sample(700 + i * 100, 0.605 - i * 0.02),
    ]),
    fixture('flick_cooldown', [
      sample(0, 0.5),
      sample(83, 0.58),
      sample(166, 0.66),
      for (final ms in [249, 332, 415, 498, 581, 665, 666]) sample(ms, 0.66),
    ]),
    fixture('flick_rearm', [
      sample(0, 0.3),
      sample(83, 0.38),
      sample(166, 0.46),
      for (final ms in [249, 415, 581, 666]) sample(ms, 0.46),
      sample(749, 0.54),
      sample(832, 0.62),
    ]),
    fixture(
      'continuous_smoothing',
      movement(0.015),
      motion: {'smoothingAlpha': 0.25},
    ),
    fixture('pose_up_confirmation', hold(up, 400)),
    fixture('pose_down_confirmation', hold(down, 400)),
    fixture('pose_hold_several_seconds', hold(down, 4000)),
    fixture('explicit_neutral_release', [
      ...hold(up, 400),
      for (final ms in [500, 600, 700]) sample(ms, 0.5, pose: neutral),
      for (final ms in [800, 900, 1000]) sample(ms, 0.5, pose: down),
    ]),
    fixture('direct_pose_direction_switch', [
      ...hold(up, 400),
      for (var ms = 500; ms <= 2000; ms += 100) sample(ms, 0.5, pose: down),
    ]),
    fixture('invalid_landmarks', [sample(0, 0.5), invalid, sample(200, 0.5)]),
    fixture('pose_boundary_conditions', [
      for (final ms in [0, 90, 179, 180]) sample(ms, 0.5, pose: up),
      for (final ms in [450, 540, 629, 630]) sample(ms, 0.5, pose: neutral),
    ]),
    fixture('configuration_change', [
      ...hold(up, 400),
      {
        ...sample(500, 0.5, pose: down),
        'configure': {'mode': 'POSE', 'confirmationDurationMs': 360},
      },
      for (final ms in [600, 700, 800, 859, 860]) sample(ms, 0.5, pose: down),
    ]),
    fixture('pose_gap_cancels_confirmation', [
      sample(0, 0.5, pose: up),
      sample(90, 0.5, pose: up),
      sample(400, 0.5, pose: up),
      sample(490, 0.5, pose: up),
      sample(580, 0.5, pose: up),
    ]),
    fixture('pose_loss_preserves_latch', [
      ...hold(up, 200),
      sample(300, null),
      for (final ms in [400, 500, 600, 700, 800]) sample(ms, 0.5, pose: up),
    ]),
    fixture('flick_candidate_owns_pointing', [
      sample(0, 0.3),
      sample(100, 0.38),
      sample(200, 0.46, pose: down),
      sample(300, 0.46, pose: down),
    ]),
    fixture('pose_hysteresis', [
      sample(0, 0.5, pose: up),
      sample(90, 0.5, pose: up, rotation: 0.65),
      sample(180, 0.5, pose: up, rotation: 0.65),
    ]),
    fixture('pose_hysteresis_exit', [
      sample(0, 0.5, pose: up),
      sample(90, 0.5, pose: up, rotation: 0.9),
      sample(180, 0.5, pose: up),
    ]),
    fixture(
      'stable_pose_compatibility',
      hold(down, 1800),
      pose: {'rearmMode': 'STABLE_POSE', 'stablePoseDurationMs': 500},
    ),
    fixture('pose_only_neutral_suppression', hold(neutral, 600), mode: 'POSE'),
    fixture('scroll_inversion_up', movement(-0.015), inverted: true),
    fixture('scroll_inversion_down', movement(0.015), inverted: true),
    fixture('pose_inversion', hold(down, 400), inverted: true),
    fixture('flick_inversion', movement(-0.08, count: 4), inverted: true),
    fixture('negative_timestamp', [
      sample(-1, 0.5),
      sample(0, 0.5),
      sample(100, 0.5),
    ]),
    fixture('landmark_jitter', [
      for (var i = 0; i < 40; i++)
        sample(i * 83, 0.5 + math.sin(i * 2.1) * 0.003),
    ]),
    fixture(
      'velocity_boundary_below',
      movement(0.0249 * 0.083),
      motion: {
        'smoothingAlpha': 1.0,
        'movementThreshold': 0.0,
        'deadZone': 0.0,
        'stationaryVelocity': 0.0,
        'confirmationFrames': 1,
      },
    ),
    fixture(
      'velocity_boundary_above',
      movement(0.0251 * 0.083),
      motion: {
        'smoothingAlpha': 1.0,
        'movementThreshold': 0.0,
        'deadZone': 0.0,
        'stationaryVelocity': 0.0,
        'confirmationFrames': 1,
      },
    ),
  ];
}

MotionDetectionConfig motionConfig(Map<String, dynamic> c) =>
    MotionDetectionConfig(
      smoothingAlpha: (c['smoothingAlpha'] as num).toDouble(),
      windowSize: c['windowSize'] as int,
      minimumFrames: c['minimumFrames'] as int,
      movementThreshold: (c['movementThreshold'] as num).toDouble(),
      deadZone: (c['deadZone'] as num).toDouble(),
      minimumVelocity: (c['minimumVelocity'] as num).toDouble(),
      confirmationFrames: c['confirmationFrames'] as int,
      directionCooldown: Duration(
        milliseconds: c['directionCooldownMs'] as int,
      ),
      maximumSampleGap: Duration(milliseconds: c['maximumSampleGapMs'] as int),
      maximumPositionJump: (c['maximumPositionJump'] as num).toDouble(),
      stationaryVelocity: (c['stationaryVelocity'] as num).toDouble(),
      calibrationDuration: Duration(
        milliseconds: c['calibrationDurationMs'] as int,
      ),
      calibrationRadius: (c['calibrationRadius'] as num).toDouble(),
    );

FlickDetectionConfig flickConfig(Map<String, dynamic> c) =>
    FlickDetectionConfig(
      minimumVelocity: (c['minimumVelocity'] as num).toDouble(),
      minimumDisplacement: (c['minimumDisplacement'] as num).toDouble(),
      minimumDuration: Duration(milliseconds: c['minimumDurationMs'] as int),
      maximumDuration: Duration(milliseconds: c['maximumDurationMs'] as int),
      cooldown: Duration(milliseconds: c['cooldownMs'] as int),
      directionConsistency: (c['directionConsistency'] as num).toDouble(),
      maximumSampleGap: Duration(milliseconds: c['maximumSampleGapMs'] as int),
      maximumPositionJump: (c['maximumPositionJump'] as num).toDouble(),
      rearmVelocity: (c['rearmVelocity'] as num).toDouble(),
      rearmDuration: Duration(milliseconds: c['rearmDurationMs'] as int),
      rearmRadius: (c['rearmRadius'] as num).toDouble(),
    );

HandPoseDetectionConfig poseConfig(
  Map<String, dynamic> c,
) => HandPoseDetectionConfig(
  enabled: c['enabled'] as bool,
  confirmationDuration: Duration(
    milliseconds: c['confirmationDurationMs'] as int,
  ),
  releaseDuration: Duration(milliseconds: c['releaseDurationMs'] as int),
  cooldown: Duration(milliseconds: c['cooldownMs'] as int),
  rearmMode: c['rearmMode'] == 'STABLE_POSE'
      ? HandPoseRearmMode.stablePose
      : HandPoseRearmMode.openHand,
  stablePoseDuration: Duration(milliseconds: c['stablePoseDurationMs'] as int),
  minimumObservations: c['minimumObservations'] as int,
  minimumIndexAngle: (c['minimumIndexAngle'] as num).toDouble(),
  maximumCurledAngle: (c['maximumCurledAngle'] as num).toDouble(),
  minimumVerticalAlignment: (c['minimumVerticalAlignment'] as num).toDouble(),
  exitIndexAngle: (c['exitIndexAngle'] as num).toDouble(),
  exitCurledAngle: (c['exitCurledAngle'] as num).toDouble(),
  exitVerticalAlignment: (c['exitVerticalAlignment'] as num).toDouble(),
  minimumFingerLengthRatio: (c['minimumFingerLengthRatio'] as num).toDouble(),
  minimumBoneLengthRatio: (c['minimumBoneLengthRatio'] as num).toDouble(),
  maximumSampleGap: Duration(milliseconds: c['maximumSampleGapMs'] as int),
);

String enumName(String value) =>
    value.replaceAllMapped(RegExp('[A-Z]'), (m) => '_${m[0]}').toUpperCase();

List<Map<String, Object?>> evaluate(Map<String, dynamic> f) {
  final config = f['config'] as Map<String, dynamic>;
  var detectorConfig = config['pose'] as Map<String, dynamic>;
  final pose = HandPoseDetector(poseConfig(detectorConfig));
  final flick = FlickDetector(
    flickConfig(config['flick'] as Map<String, dynamic>),
  );
  final coordinator = GestureCoordinator(
    motionDetector: WindowedMotionDetectionService(
      motionConfig(config['motion'] as Map<String, dynamic>),
    ),
    flickDetector: flick,
    poseDetector: pose,
    mode: config['mode'] == 'POSE'
        ? GestureMode.poseOnly
        : GestureMode.combined,
  );
  final mapper = ScrollVelocityMapper(const ScrollConfig())
    ..configure(inverted: config['inverted'] as bool);
  return [
    for (final raw in f['samples'] as List)
      (() {
        final s = raw as Map<String, dynamic>;
        if (s['configure'] case final Map<String, dynamic> changed) {
          detectorConfig = {...detectorConfig, ...changed};
          coordinator.configure(
            detector: HandPoseDetector(poseConfig(detectorConfig)),
            mode: changed['mode'] == 'POSE'
                ? GestureMode.poseOnly
                : GestureMode.combined,
          );
        }
        final landmarks = s['landmarks'] == null
            ? null
            : [
                for (final p in s['landmarks'] as List)
                  HandLandmark(
                    index: p['index'] as int,
                    x: (p['x'] as num).toDouble(),
                    y: (p['y'] as num).toDouble(),
                    z: (p['z'] as num).toDouble(),
                  ),
              ];
        HandPose? hand;
        try {
          if (landmarks != null) hand = HandPose(landmarks: landmarks);
        } on ArgumentError {
          return <String, Object?>{
            'rejected': true,
            'classification': enumName(
              HandPoseClassifier(
                poseConfig(detectorConfig),
              ).classify(landmarks!).name,
            ),
          };
        }
        final result = coordinator.process(
          HandTrackingResult(
            timestamp: Duration(milliseconds: s['timestampMs'] as int),
            pose: hand,
          ),
        );
        final motion = result.motion;
        final poseEvent = result.pose;
        final flickEvent = result.flick;
        final speed = mapper.map(motion);
        final down = poseEvent != null
            ? poseEvent.pose == RecognizedHandPose.pointingDown
            : flickEvent != null
            ? flickEvent.direction.name == 'down'
            : speed == 0
            ? null
            : speed > 0;
        final discreteDown = down == null
            ? null
            : (poseEvent != null || flickEvent != null) &&
                  (config['inverted'] as bool)
            ? !down
            : down;
        final active = coordinator.poseDetector;
        return <String, Object?>{
          'rejected': false,
          'motion': {
            'action': enumName(motion.action.name),
            'verticalDisplacement': motion.verticalDisplacement,
            'verticalVelocity': motion.verticalVelocity,
            'smoothedY': motion.smoothedY,
            'neutralY': motion.neutralY,
          },
          'flick': flickEvent == null
              ? null
              : {
                  'direction': enumName(flickEvent.direction.name),
                  'timestampMs': flickEvent.timestamp.inMilliseconds,
                  'verticalDisplacement': flickEvent.verticalDisplacement,
                  'verticalVelocity': flickEvent.verticalVelocity,
                  'durationMs': flickEvent.duration.inMilliseconds,
                },
          'pose': poseEvent == null
              ? null
              : {
                  'pose': enumName(poseEvent.pose.name),
                  'timestampMs': poseEvent.timestamp.inMilliseconds,
                },
          'posePhase': enumName(active.phase.name),
          'currentPose': enumName(active.currentPose.name),
          'confirmationProgress': active.confirmationProgress,
          'cooldownRemainingMs': active.cooldownRemaining.inMilliseconds,
          'flickOwnsGesture': flick.ownsGesture,
          'flickSuppressesContinuous': flick.suppressesContinuous,
          'suppressesContinuous': result.suppressesContinuous,
          'scrollPixelsPerSecond': speed,
          'scrollAction': discreteDown == null
              ? null
              : discreteDown
              ? 'FORWARD'
              : 'BACKWARD',
        };
      })(),
  ];
}

void main() {
  test('Dart produces and verifies native migration golden fixtures', () {
    final generated = {
      'formatVersion': 1,
      'generatedBy':
          'Existing Dart GestureCoordinator; no native expected output generation',
      'floatingPointTolerance': 1e-9,
      'cases': [
        for (final raw in scenarios())
          (() {
            final f = jsonDecode(jsonEncode(raw)) as Map<String, dynamic>;
            return {...f, 'expected': evaluate(f)};
          })(),
      ],
    };
    final file = File('parity-fixtures/gesture-goldens.json');
    if (const bool.fromEnvironment('GENERATE_GOLDENS')) {
      file.parent.createSync(recursive: true);
      file.writeAsStringSync(
        '${const JsonEncoder.withIndent('  ').convert(generated)}\n',
      );
    }
    expect(
      file.existsSync(),
      isTrue,
      reason:
          'Run with --dart-define=GENERATE_GOLDENS=true to create the baseline.',
    );
    expect(jsonDecode(file.readAsStringSync()), generated);
    expect((generated['cases'] as List).length, greaterThanOrEqualTo(24));
  });
}
