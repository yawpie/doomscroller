enum HandAction { idle, moveUp, moveDown, start, stop }

class HandMotion {
  const HandMotion({
    required this.action,
    this.verticalDisplacement = 0,
    this.verticalVelocity = 0,
    this.smoothedY,
    this.neutralY,
  });

  final HandAction action;
  final double verticalDisplacement;
  // Upright normalized image heights / second; negative means upward.
  final double verticalVelocity;
  final double? smoothedY;
  final double? neutralY;
  bool get calibrated => neutralY != null;
}
