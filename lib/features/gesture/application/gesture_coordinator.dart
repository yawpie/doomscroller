import '../../hand_tracking/domain/hand_models.dart';
import '../domain/flick_event.dart';
import '../domain/hand_motion.dart';
import '../domain/motion_detection_service.dart';
import 'flick_detector.dart';

class GestureDecision {
  const GestureDecision({required this.motion, this.flick});

  final HandMotion motion;
  final FlickEvent? flick;
}

class GestureCoordinator {
  GestureCoordinator({
    required this.motionDetector,
    required this.flickDetector,
  });

  final MotionDetectionService motionDetector;
  final FlickDetector flickDetector;
  bool _wasSuppressed = false;

  GestureDecision process(HandTrackingResult result) {
    final flick = flickDetector.process(result);
    if (result.pose == null) {
      motionDetector.reset();
      _wasSuppressed = flickDetector.suppressesContinuous;
      return const GestureDecision(motion: HandMotion(action: HandAction.stop));
    }
    if (flickDetector.suppressesContinuous) {
      if (!_wasSuppressed) motionDetector.reset();
      _wasSuppressed = true;
      return GestureDecision(
        motion: HandMotion(
          action: HandAction.idle,
          smoothedY: result.pose!.palmY,
        ),
        flick: flick,
      );
    }
    _wasSuppressed = false;
    return GestureDecision(motion: motionDetector.process(result));
  }

  void loseTracking() {
    motionDetector.reset();
    flickDetector.loseTracking();
    _wasSuppressed = true;
  }

  void reset() {
    motionDetector.reset();
    flickDetector.reset();
    _wasSuppressed = false;
  }
}
