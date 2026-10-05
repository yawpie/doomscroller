import '../../gesture/domain/hand_motion.dart';
import '../../hand_tracking/domain/hand_models.dart';

class TrackingState {
  const TrackingState({
    this.running = false,
    this.busy = false,
    this.result,
    this.motion = const HandMotion(action: HandAction.idle),
    this.inferenceFps = 0,
    this.error,
  });

  final bool running;
  final bool busy;
  final HandTrackingResult? result;
  final HandMotion motion;
  final double inferenceFps;
  final String? error;
}
