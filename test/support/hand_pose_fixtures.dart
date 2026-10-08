import 'dart:math' as math;

import 'package:autoscroll_compvis/features/gesture/domain/pose_event.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_models.dart';

List<HandLandmark> handPoseLandmarks(
  RecognizedHandPose pose, {
  double scale = 1,
  double x = 0.5,
  double y = 0.5,
  double rotation = 0,
}) {
  final points = List<(double, double)>.filled(21, (0, 0));
  points[0] = (0, 0.12);
  points[1] = (-0.07, 0.08);
  points[2] = (-0.11, 0.03);
  points[3] = (-0.13, -0.02);
  points[4] = (-0.14, -0.07);
  for (final (mcp, px, py) in [
    (5, -0.065, -0.06),
    (9, -0.02, -0.07),
    (13, 0.03, -0.06),
    (17, 0.075, -0.04),
  ]) {
    points[mcp] = (px, py);
    final extended =
        pose == RecognizedHandPose.neutral ||
        (mcp == 5 && pose != RecognizedHandPose.unknown);
    points[mcp + 1] = (px, py - 0.085);
    points[mcp + 2] = extended ? (px, py - 0.16) : (px + 0.05, py - 0.085);
    points[mcp + 3] = extended ? (px, py - 0.22) : (px + 0.05, py - 0.035);
  }
  final angle =
      rotation + (pose == RecognizedHandPose.pointingDown ? math.pi : 0);
  final cosine = math.cos(angle);
  final sine = math.sin(angle);
  return List.generate(21, (i) {
    final (px, py) = points[i];
    return HandLandmark(
      index: i,
      x: x + scale * (px * cosine - py * sine),
      y: y + scale * (px * sine + py * cosine),
      z: 0,
    );
  });
}

HandPose handPoseFixture(
  RecognizedHandPose pose, {
  double scale = 1,
  double x = 0.5,
  double y = 0.5,
  double rotation = 0,
}) => HandPose(
  landmarks: handPoseLandmarks(
    pose,
    scale: scale,
    x: x,
    y: y,
    rotation: rotation,
  ),
);

HandTrackingResult poseSample(
  int milliseconds,
  RecognizedHandPose? pose, {
  double scale = 1,
  double x = 0.5,
  double y = 0.5,
  double rotation = 0,
}) => HandTrackingResult(
  timestamp: Duration(milliseconds: milliseconds),
  pose: pose == null
      ? null
      : handPoseFixture(pose, scale: scale, x: x, y: y, rotation: rotation),
);
