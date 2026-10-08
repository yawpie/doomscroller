enum FlickDirection { up, down }

class FlickEvent {
  const FlickEvent({
    required this.direction,
    required this.timestamp,
    required this.verticalDisplacement,
    required this.verticalVelocity,
    required this.duration,
  });

  final FlickDirection direction;
  final Duration timestamp;
  final double verticalDisplacement;
  // Normalized upright image heights / second; negative means upward.
  final double verticalVelocity;
  final Duration duration;
}
