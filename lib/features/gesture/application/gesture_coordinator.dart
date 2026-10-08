import '../../../core/config.dart';
import '../../hand_tracking/domain/hand_models.dart';
import '../domain/flick_event.dart';
import '../domain/hand_motion.dart';
import '../domain/motion_detection_service.dart';
import '../domain/pose_event.dart';
import 'flick_detector.dart';
import 'hand_pose_detector.dart';

enum GestureMode { combined, poseOnly }

class GestureDecision {
  const GestureDecision({
    required this.motion,
    this.flick,
    this.pose,
    this.suppressesContinuous = false,
  });

  final HandMotion motion;
  final FlickEvent? flick;
  final PoseEvent? pose;
  final bool suppressesContinuous;
}

class GestureCoordinator {
  GestureCoordinator({
    required this.motionDetector,
    required this.flickDetector,
    HandPoseDetector? poseDetector,
    this.mode = GestureMode.combined,
  }) : poseDetector =
           poseDetector ?? HandPoseDetector(const HandPoseDetectionConfig());

  final MotionDetectionService motionDetector;
  final FlickDetector flickDetector;
  HandPoseDetector poseDetector;
  GestureMode mode;
  bool _wasSuppressed = false;

  GestureDecision process(HandTrackingResult result) {
    if (result.pose == null) {
      loseTracking();
      return const GestureDecision(
        motion: HandMotion(action: HandAction.stop),
        suppressesContinuous: true,
      );
    }

    if (poseDetector.suppressesContinuous || mode == GestureMode.poseOnly) {
      final pose = poseDetector.process(result);
      flickDetector.reset();
      return _suppressed(result, pose: pose);
    }
    // An existing flick candidate or cooldown keeps ownership of this sample.
    if (flickDetector.ownsGesture) {
      return _suppressed(result, flick: flickDetector.process(result));
    }

    final pose = poseDetector.process(result);
    if (poseDetector.suppressesContinuous) {
      flickDetector.reset();
      return _suppressed(result, pose: pose);
    }
    final flick = flickDetector.process(result);
    if (flickDetector.suppressesContinuous) {
      return _suppressed(result, flick: flick);
    }
    _wasSuppressed = false;
    return GestureDecision(motion: motionDetector.process(result));
  }

  GestureDecision _suppressed(
    HandTrackingResult result, {
    PoseEvent? pose,
    FlickEvent? flick,
  }) {
    if (!_wasSuppressed) motionDetector.reset();
    _wasSuppressed = true;
    return GestureDecision(
      motion: HandMotion(
        action: HandAction.idle,
        smoothedY: result.pose!.palmY,
      ),
      pose: pose,
      flick: flick,
      suppressesContinuous: true,
    );
  }

  void configure({HandPoseDetector? detector, GestureMode? mode}) {
    reset();
    if (detector != null) poseDetector = detector;
    if (mode != null) this.mode = mode;
  }

  void loseTracking() {
    motionDetector.reset();
    flickDetector.loseTracking();
    poseDetector.loseTracking();
    _wasSuppressed = true;
  }

  void reset() {
    motionDetector.reset();
    flickDetector.reset();
    poseDetector.reset();
    _wasSuppressed = false;
  }
}
