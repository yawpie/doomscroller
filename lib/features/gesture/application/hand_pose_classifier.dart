import 'dart:math' as math;

import '../../../core/config.dart';
import '../../hand_tracking/domain/hand_models.dart';
import '../domain/pose_event.dart';

class HandPoseClassifier {
  HandPoseClassifier(this.config) {
    final values = [
      config.minimumIndexAngle,
      config.maximumCurledAngle,
      config.minimumVerticalAlignment,
      config.exitIndexAngle,
      config.exitCurledAngle,
      config.exitVerticalAlignment,
      config.minimumFingerLengthRatio,
      config.minimumBoneLengthRatio,
    ];
    if (values.any((value) => !value.isFinite) ||
        config.minimumIndexAngle > 180 ||
        config.exitIndexAngle <= 0 ||
        config.exitIndexAngle > config.minimumIndexAngle ||
        config.maximumCurledAngle <= 0 ||
        config.maximumCurledAngle > config.exitCurledAngle ||
        config.exitCurledAngle >= config.exitIndexAngle ||
        config.minimumVerticalAlignment > 1 ||
        config.exitVerticalAlignment <= 0 ||
        config.exitVerticalAlignment > config.minimumVerticalAlignment ||
        config.minimumFingerLengthRatio <= 0 ||
        config.minimumBoneLengthRatio <= 0 ||
        config.minimumBoneLengthRatio * 3 > config.minimumFingerLengthRatio) {
      throw ArgumentError('Invalid hand pose geometry thresholds.');
    }
  }

  final HandPoseDetectionConfig config;

  RecognizedHandPose classify(
    List<HandLandmark> landmarks, {
    RecognizedHandPose? activePose,
  }) {
    if (landmarks.length != 21) return RecognizedHandPose.unknown;
    for (var i = 0; i < 21; i++) {
      final point = landmarks[i];
      if (point.index != i ||
          !point.x.isFinite ||
          !point.y.isFinite ||
          !point.z.isFinite ||
          point.x < 0 ||
          point.x > 1 ||
          point.y < 0 ||
          point.y > 1) {
        return RecognizedHandPose.unknown;
      }
    }
    final palmLength = _distance(landmarks[0], landmarks[9]);
    if (palmLength <= 1e-9) return RecognizedHandPose.unknown;
    for (var mcp = 5; mcp <= 17; mcp += 4) {
      if (!_validFinger(landmarks, mcp, palmLength)) {
        return RecognizedHandPose.unknown;
      }
    }

    if ((activePose == RecognizedHandPose.pointingUp ||
            activePose == RecognizedHandPose.pointingDown) &&
        _pointing(landmarks, activePose!, exit: true)) {
      return activePose;
    }
    if (_pointing(landmarks, RecognizedHandPose.pointingUp)) {
      return RecognizedHandPose.pointingUp;
    }
    if (_pointing(landmarks, RecognizedHandPose.pointingDown)) {
      return RecognizedHandPose.pointingDown;
    }
    for (var mcp = 5; mcp <= 17; mcp += 4) {
      if (!_extended(landmarks, mcp, config.minimumIndexAngle)) {
        return RecognizedHandPose.unknown;
      }
    }
    return RecognizedHandPose.neutral;
  }

  bool _pointing(
    List<HandLandmark> points,
    RecognizedHandPose pose, {
    bool exit = false,
  }) {
    if (!_extended(
      points,
      5,
      exit ? config.exitIndexAngle : config.minimumIndexAngle,
    )) {
      return false;
    }
    final curledAngle = exit
        ? config.exitCurledAngle
        : config.maximumCurledAngle;
    for (var mcp = 9; mcp <= 17; mcp += 4) {
      if (_angle(points[mcp], points[mcp + 1], points[mcp + 2]) > curledAngle) {
        return false;
      }
    }
    final dx = points[8].x - points[5].x;
    final dy = points[8].y - points[5].y;
    final length = math.sqrt(dx * dx + dy * dy);
    if (length <= 1e-9) return false;
    final alignment = dy / length;
    final threshold = exit
        ? config.exitVerticalAlignment
        : config.minimumVerticalAlignment;
    return pose == RecognizedHandPose.pointingDown
        ? alignment >= threshold
        : alignment <= -threshold;
  }

  bool _extended(List<HandLandmark> points, int mcp, double angle) =>
      _angle(points[mcp], points[mcp + 1], points[mcp + 2]) >= angle &&
      _angle(points[mcp + 1], points[mcp + 2], points[mcp + 3]) >= angle;

  bool _validFinger(List<HandLandmark> points, int mcp, double palmLength) {
    var length = 0.0;
    for (var i = mcp; i < mcp + 3; i++) {
      final bone = _distance(points[i], points[i + 1]);
      if (bone < palmLength * config.minimumBoneLengthRatio) return false;
      length += bone;
    }
    return length >= palmLength * config.minimumFingerLengthRatio;
  }

  double _distance(HandLandmark a, HandLandmark b) {
    final dx = a.x - b.x;
    final dy = a.y - b.y;
    return math.sqrt(dx * dx + dy * dy);
  }

  double _angle(HandLandmark a, HandLandmark joint, HandLandmark b) {
    final ax = a.x - joint.x;
    final ay = a.y - joint.y;
    final bx = b.x - joint.x;
    final by = b.y - joint.y;
    final denominator = math.sqrt((ax * ax + ay * ay) * (bx * bx + by * by));
    if (denominator <= 1e-18) return 0;
    return math.acos(((ax * bx + ay * by) / denominator).clamp(-1.0, 1.0)) *
        180 /
        math.pi;
  }
}
