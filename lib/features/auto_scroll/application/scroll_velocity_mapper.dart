import '../../../core/config.dart';
import '../../gesture/domain/hand_motion.dart';

class ScrollVelocityMapper {
  ScrollVelocityMapper(this.config)
    : sensitivity = config.pixelsPerNormalizedUnit;

  final ScrollConfig config;
  double sensitivity;
  bool inverted = false;

  double map(HandMotion motion) {
    final v = motion.verticalVelocity;
    if (!v.isFinite ||
        v.abs() <= config.deadZone ||
        (motion.action != HandAction.moveUp &&
            motion.action != HandAction.moveDown)) {
      return 0;
    }
    final speed = (v.abs() * sensitivity).clamp(
      0.0,
      config.maximumPixelsPerSecond,
    );
    return speed *
        (motion.action == HandAction.moveUp ? -1 : 1) *
        (inverted ? -1 : 1);
  }

  void configure({double? sensitivity, bool? inverted}) {
    if (sensitivity != null) {
      if (!sensitivity.isFinite) {
        throw ArgumentError.value(sensitivity, 'sensitivity');
      }
      this.sensitivity = sensitivity.clamp(
        config.minimumSensitivity,
        config.maximumSensitivity,
      );
    }
    if (inverted != null) this.inverted = inverted;
  }
}
