import '../../gesture/domain/hand_motion.dart';

abstract interface class ScrollService {
  void handle(HandMotion motion);
  void stop();
  void dispose();
}
