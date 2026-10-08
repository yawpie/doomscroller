import '../../gesture/domain/flick_event.dart';
import '../../gesture/domain/hand_motion.dart';
import '../../hand_tracking/domain/hand_models.dart';

enum TrackingPhase { disabled, searching, tracking, lost }

class TrackingState {
  const TrackingState({
    this.running = false,
    this.busy = false,
    this.result,
    this.motion = const HandMotion(action: HandAction.idle),
    this.flick,
    this.inferenceFps = 0,
    this.error,
    this.phase = TrackingPhase.disabled,
    this.inferenceMilliseconds = 0,
  });

  final bool running;
  final bool busy;
  final HandTrackingResult? result;
  final HandMotion motion;
  final FlickEvent? flick;
  final double inferenceFps;
  final String? error;
  final TrackingPhase phase;
  final double inferenceMilliseconds;
}
