import 'package:autoscroll_compvis/app/providers.dart';
import 'package:autoscroll_compvis/features/auto_scroll/presentation/tracking_screen.dart';
import 'package:autoscroll_compvis/main.dart' as app;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Android camera frames reach the packaged MediaPipe detector', (
    tester,
  ) async {
    await app.main();
    await tester.pumpAndSettle();
    final container = ProviderScope.containerOf(
      tester.element(find.byType(TrackingScreen)),
    );
    final coordinator = container.read(trackingProvider.notifier).controller;
    try {
      await tester.runAsync(() async {
        await coordinator.start();
        final timeout = Stopwatch()..start();
        while (container.read(trackingProvider).result == null &&
            container.read(trackingProvider).error == null &&
            timeout.elapsed < const Duration(seconds: 20)) {
          await Future<void>.delayed(const Duration(milliseconds: 100));
        }
      });
      final state = container.read(trackingProvider);
      expect(state.error, isNull);
      expect(state.running, isTrue);
      expect(
        state.result,
        isNotNull,
        reason:
            'Camera/MediaPipe must return a real result, including an empty-hand result.',
      );
      // Lifecycle restart must recreate both native owners without freezing.
      await tester.runAsync(() async {
        await coordinator.setForeground(false);
        expect(container.read(trackingProvider).running, isFalse);
        await coordinator.setForeground(true);
        final timeout = Stopwatch()..start();
        while (container.read(trackingProvider).result == null &&
            container.read(trackingProvider).error == null &&
            timeout.elapsed < const Duration(seconds: 20)) {
          await Future<void>.delayed(const Duration(milliseconds: 100));
        }
      });
      expect(container.read(trackingProvider).error, isNull);
      expect(container.read(trackingProvider).result, isNotNull);
      await tester.runAsync(coordinator.stop);
      await tester.pumpAndSettle();
      expect(container.read(trackingProvider).running, isFalse);
      expect(find.text('Start tracking'), findsOneWidget);
    } finally {
      await tester.runAsync(coordinator.stop);
    }
  });
}
