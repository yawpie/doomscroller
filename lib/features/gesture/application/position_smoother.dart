abstract interface class PositionSmoother {
  double add(double value);
  void reset();
}

class ExponentialPositionSmoother implements PositionSmoother {
  ExponentialPositionSmoother(this.alpha) {
    if (!alpha.isFinite || alpha <= 0 || alpha > 1) {
      throw ArgumentError.value(alpha, 'alpha', 'Expected (0, 1].');
    }
  }

  final double alpha;
  double? _value;

  @override
  double add(double value) =>
      _value = _value == null ? value : alpha * value + (1 - alpha) * _value!;

  @override
  void reset() => _value = null;
}
