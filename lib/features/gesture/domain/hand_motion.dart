enum HandAction { idle, moveUp, moveDown, start, stop }

class HandMotion {
  const HandMotion({
    required this.action,
    this.verticalDisplacement = 0,
    this.verticalVelocity = 0,
  });

  final HandAction action;
  final double verticalDisplacement;
  // Upright normalized image heights / second; negative means upward.
  final double verticalVelocity;
}
