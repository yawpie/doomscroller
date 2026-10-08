import '../../gesture/domain/hand_motion.dart';
import '../../gesture/domain/flick_event.dart';

abstract interface class ScrollService {
  void handle(HandMotion motion);
  void handleFlick(FlickEvent event);
  void stop();
  void dispose();
}
