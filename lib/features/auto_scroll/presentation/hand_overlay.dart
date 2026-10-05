import 'package:flutter/material.dart';

import '../../gesture/domain/hand_motion.dart';
import '../../hand_tracking/domain/hand_models.dart';

class HandOverlay extends CustomPainter {
  HandOverlay(this.pose, this.motion);
  final HandPose pose;
  final HandMotion motion;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = Colors.cyanAccent;
    for (final p in pose.landmarks) {
      final palm = [0, 5, 9, 13, 17].contains(p.index);
      canvas.drawCircle(
        Offset(p.x * size.width, p.y * size.height),
        palm ? 4 : 2,
        paint,
      );
    }
    final center = pose.palmCenter;
    paint.color = Colors.yellowAccent;
    canvas.drawCircle(
      Offset(center.normalizedX * size.width, center.normalizedY * size.height),
      6,
      paint,
    );
    final neutral = motion.neutralY;
    if (neutral != null) {
      paint
        ..color = Colors.greenAccent
        ..strokeWidth = 1;
      canvas.drawLine(
        Offset(0, neutral * size.height),
        Offset(size.width, neutral * size.height),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(HandOverlay oldDelegate) =>
      oldDelegate.pose != pose || oldDelegate.motion != motion;
}
