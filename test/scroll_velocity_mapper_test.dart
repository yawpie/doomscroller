import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/auto_scroll/application/scroll_velocity_mapper.dart';
import 'package:autoscroll_compvis/features/gesture/domain/hand_motion.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'proportional speed, velocity clamp, inversion and dead-zone boundaries',
    () {
      final mapper = ScrollVelocityMapper(const ScrollConfig());
      HandMotion down(double velocity) =>
          HandMotion(action: HandAction.moveDown, verticalVelocity: velocity);
      expect(mapper.map(down(0.1)), 180);
      expect(mapper.map(down(0.2)), 360);
      expect(mapper.map(down(10)), 1200);
      expect(mapper.map(down(0.025)), 0);
      expect(mapper.map(down(0.0249)), 0);
      expect(mapper.map(down(0.0251)), greaterThan(0));
      expect(mapper.map(down(double.nan)), 0);
      expect(
        mapper.map(
          const HandMotion(action: HandAction.idle, verticalVelocity: 1),
        ),
        0,
      );
      expect(
        mapper.map(
          const HandMotion(action: HandAction.moveUp, verticalVelocity: -0.2),
        ),
        -360,
      );
      mapper.configure(sensitivity: 3000, inverted: true);
      expect(mapper.map(down(0.1)), -300);
      mapper.configure(sensitivity: 9000);
      expect(mapper.sensitivity, 4000);
      expect(
        () => mapper.configure(sensitivity: double.nan),
        throwsArgumentError,
      );
    },
  );
}
