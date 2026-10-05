import '../../../core/config.dart';
import '../../hand_tracking/domain/hand_models.dart';
import '../domain/hand_motion.dart';
import '../domain/motion_detection_service.dart';
import 'position_smoother.dart';

class WindowedMotionDetectionService implements MotionDetectionService {
  WindowedMotionDetectionService(this.config, {PositionSmoother? smoother})
    : _smoother =
          smoother ?? ExponentialPositionSmoother(config.smoothingAlpha);

  final MotionDetectionConfig config;
  final PositionSmoother _smoother;
  final List<({Duration time, double y})> _samples = [];
  Duration? _lastTime;
  Duration? _lastDirectionChange;
  HandAction _direction = HandAction.idle;
  HandAction _candidate = HandAction.idle;
  int _confirmationCount = 0;
  bool _hadHand = false;
  double? _lastRawY;
  double? _neutralY;
  double? _stableAnchor;
  Duration? _stableSince;

  @override
  HandMotion process(HandTrackingResult result) {
    final lastTime = _lastTime;
    if (lastTime != null && result.timestamp <= lastTime) {
      return const HandMotion(action: HandAction.idle);
    }
    if (lastTime != null &&
        result.timestamp - lastTime > config.maximumSampleGap) {
      reset();
    }
    _lastTime = result.timestamp;
    final pose = result.pose;
    if (pose == null) {
      final action = _hadHand ? HandAction.stop : HandAction.idle;
      // Reacquisition must warm up again, never measure across a missing hand.
      reset();
      _lastTime = result.timestamp;
      return HandMotion(action: action);
    }
    final first = !_hadHand;
    final previousY = _lastRawY;
    if (previousY != null &&
        (pose.palmY - previousY).abs() > config.maximumPositionJump) {
      // A discontinuity can mean a replacement hand; never turn it into a fling.
      reset();
      return process(result);
    }
    _hadHand = true;
    _lastRawY = pose.palmY;
    if (_neutralY == null) {
      if (_stableAnchor == null ||
          (pose.palmY - _stableAnchor!).abs() > config.calibrationRadius) {
        _stableAnchor = pose.palmY;
        _stableSince = result.timestamp;
      } else if (result.timestamp - _stableSince! >=
          config.calibrationDuration) {
        _neutralY = _samples.isEmpty
            ? pose.palmY
            : _samples.fold<double>(0, (sum, sample) => sum + sample.y) /
                  _samples.length;
      }
    }
    final rawVelocity = previousY == null || lastTime == null
        ? null
        : (pose.palmY - previousY) /
              ((result.timestamp - lastTime).inMicroseconds / 1000000);
    if (rawVelocity != null && rawVelocity.abs() <= config.stationaryVelocity) {
      // Flush regression/EMA momentum when the hand stops; displaced hands stay idle.
      _samples.clear();
      _smoother.reset();
      _direction = _candidate = HandAction.idle;
      _confirmationCount = 0;
    }
    final y = _smoother.add(pose.palmY);
    _samples.add((time: result.timestamp, y: y));
    if (_samples.length > config.windowSize) _samples.removeAt(0);
    if (_samples.length < config.minimumFrames) {
      return HandMotion(
        action: first ? HandAction.start : HandAction.idle,
        smoothedY: y,
        neutralY: _neutralY,
      );
    }

    final displacement = _samples.last.y - _samples.first.y;
    // Least-squares velocity over the window tolerates uneven frame intervals.
    final origin = _samples.first.time;
    var sumT = 0.0, sumY = 0.0, sumTT = 0.0, sumTY = 0.0;
    for (final sample in _samples) {
      final t = (sample.time - origin).inMicroseconds / 1000000;
      sumT += t;
      sumY += sample.y;
      sumTT += t * t;
      sumTY += t * sample.y;
    }
    final n = _samples.length;
    final denominator = n * sumTT - sumT * sumT;
    final velocity = denominator <= 0
        ? 0.0
        : (n * sumTY - sumT * sumY) / denominator;
    final threshold = _direction == HandAction.idle
        ? config.movementThreshold
        : config.deadZone;
    final candidate =
        displacement.abs() >= threshold &&
            velocity.abs() >= config.minimumVelocity
        ? (velocity < 0 ? HandAction.moveUp : HandAction.moveDown)
        : HandAction.idle;

    if (candidate == HandAction.idle) {
      _direction = _candidate = HandAction.idle;
      _confirmationCount = 0;
    } else if (candidate != _direction) {
      _confirmationCount = candidate == _candidate ? _confirmationCount + 1 : 1;
      _candidate = candidate;
      final cooledDown =
          _lastDirectionChange == null ||
          result.timestamp - _lastDirectionChange! >= config.directionCooldown;
      // Stop during an unconfirmed reversal; do not keep scrolling the old way.
      _direction = HandAction.idle;
      if (_confirmationCount >= config.confirmationFrames && cooledDown) {
        _direction = candidate;
        _lastDirectionChange = result.timestamp;
      }
    }
    return HandMotion(
      action: _direction,
      verticalDisplacement: displacement,
      verticalVelocity: velocity,
      smoothedY: y,
      neutralY: _neutralY,
    );
  }

  @override
  void reset() {
    _samples.clear();
    _smoother.reset();
    _lastTime = _lastDirectionChange = null;
    _direction = _candidate = HandAction.idle;
    _confirmationCount = 0;
    _hadHand = false;
    _lastRawY = _neutralY = _stableAnchor = null;
    _stableSince = null;
  }
}
