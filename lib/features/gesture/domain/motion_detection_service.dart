import '../../hand_tracking/domain/hand_models.dart';
import 'hand_motion.dart';

abstract interface class MotionDetectionService {
  HandMotion process(HandTrackingResult result);
  void reset();
}
