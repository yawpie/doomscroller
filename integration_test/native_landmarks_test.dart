import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:autoscroll_compvis/core/config.dart';
import 'package:autoscroll_compvis/features/auto_scroll/infrastructure/flutter_scroll_service.dart';
import 'package:autoscroll_compvis/features/gesture/application/windowed_motion_detection_service.dart';
import 'package:autoscroll_compvis/features/gesture/domain/hand_motion.dart';
import 'package:autoscroll_compvis/features/camera/domain/camera_frame.dart';
import 'package:autoscroll_compvis/features/hand_tracking/domain/hand_models.dart';
import 'package:autoscroll_compvis/features/hand_tracking/infrastructure/mediapipe_hand_tracking_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:integration_test/integration_test.dart';

import 'fixtures/hand_image.dart';

// Test-only RGB -> raw YUV conversion, inverse of the production sensor rotation.
Future<CameraFrame> fixtureFrame(
  ui.Image image,
  int ms, {
  int rotation = 0,
  double dy = 0,
  double dx = 0,
}) async {
  const uprightWidth = 320, uprightHeight = 480;
  final recorder = ui.PictureRecorder();
  final canvas = ui.Canvas(recorder)
    ..drawColor(const ui.Color(0xffeeeeee), ui.BlendMode.src);
  canvas.drawImageRect(
    image,
    ui.Rect.fromLTWH(0, 0, image.width.toDouble(), image.height.toDouble()),
    ui.Rect.fromLTWH(35 + dx, 100 + dy, 250, 266),
    ui.Paint(),
  );
  final picture = recorder.endRecording();
  final upright = await picture.toImage(uprightWidth, uprightHeight);
  picture.dispose();
  final rgba = (await upright.toByteData(
    format: ui.ImageByteFormat.rawRgba,
  ))!.buffer.asUint8List();
  upright.dispose();
  final swapped = rotation == 90 || rotation == 270;
  final width = swapped ? uprightHeight : uprightWidth;
  final height = swapped ? uprightWidth : uprightHeight;
  final luminance = Uint8List(width * height);
  final u = Uint8List(width * height ~/ 4), v = Uint8List(width * height ~/ 4);
  for (var y = 0; y < height; y++) {
    for (var x = 0; x < width; x++) {
      final (ux, uy) = switch (rotation) {
        90 => (height - 1 - y, x),
        180 => (width - 1 - x, height - 1 - y),
        270 => (y, width - 1 - x),
        _ => (x, y),
      };
      final offset = (uy * uprightWidth + ux) * 4;
      final r = rgba[offset], g = rgba[offset + 1], b = rgba[offset + 2];
      luminance[y * width + x] = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
      if (x.isEven && y.isEven) {
        final index = y ~/ 2 * (width ~/ 2) + x ~/ 2;
        u[index] = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
        v[index] = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
      }
    }
  }
  return CameraFrame(
    width: width,
    height: height,
    rotationDegrees: rotation,
    timestamp: Duration(milliseconds: ms),
    planes: [
      FramePlane(bytes: luminance, rowStride: width, pixelStride: 1),
      FramePlane(bytes: u, rowStride: width ~/ 2, pixelStride: 1),
      FramePlane(bytes: v, rowStride: width ~/ 2, pixelStride: 1),
    ],
  );
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'packaged MediaPipe returns indexed landmarks with upright y and mirrored x',
    (tester) async {
      final tracker = MediaPipeHandTrackingService(const TrackingConfig());
      await tester.runAsync(() async {
        final codec = await ui.instantiateImageCodec(
          base64Decode(handImageBase64),
        );
        final image = (await codec.getNextFrame()).image;
        codec.dispose();
        try {
          await tracker.initialize();
          Future<HandPose> pose(
            int ms, {
            int rotation = 0,
            double dy = 0,
            double dx = 0,
          }) async {
            // Isolate coordinate transforms from VIDEO mode's temporal ROI tracking.
            await tracker.initialize();
            final result = await tracker.track(
              await fixtureFrame(image, ms, rotation: rotation, dy: dy, dx: dx),
            );
            expect(result.timestamp, Duration(milliseconds: ms));
            expect(
              result.pose,
              isNotNull,
              reason: 'Official hand fixture must be detected',
            );
            expect(
              result.pose!.landmarks.map((p) => p.index),
              orderedEquals(List.generate(21, (i) => i)),
            );
            return result.pose!;
          }

          final center = await pose(0);
          for (final rotation in [90, 180, 270]) {
            final rotated = await pose(rotation);
            expect(rotated.palmY, closeTo(center.palmY, 0.025));
            expect(rotated.palmX, closeTo(center.palmX, 0.025));
          }
          final up = await pose(400, dy: -30);
          final down = await pose(500, dy: 30);
          expect(up.palmY, lessThan(center.palmY - 0.035));
          expect(down.palmY, greaterThan(center.palmY + 0.035));
          final left = await pose(600, dx: -25);
          final right = await pose(700, dx: 25);
          expect(left.palmX, greaterThan(right.palmX + 0.04));
        } finally {
          image.dispose();
          await tracker.dispose();
        }
      });
    },
  );

  testWidgets(
    'real fixture landmarks drive visible scrolling and stop on stationary/loss',
    (tester) async {
      final tracker = MediaPipeHandTrackingService(const TrackingConfig());
      final detector = WindowedMotionDetectionService(
        const MotionDetectionConfig(),
      );
      final scroll = FlutterScrollService(const ScrollConfig());
      await tester.pumpWidget(
        MaterialApp(
          home: ListView(
            controller: scroll.controller,
            children: List.generate(
              80,
              (i) => SizedBox(height: 100, child: Text('Content $i')),
            ),
          ),
        ),
      );
      ui.Image? image;
      var timestamp = 0;
      var downRecognized = false;
      final latencies = <int>[];
      try {
        await tester.runAsync(() async {
          final codec = await ui.instantiateImageCodec(
            base64Decode(handImageBase64),
          );
          image = (await codec.getNextFrame()).image;
          codec.dispose();
          await tracker.initialize();
        });
        for (var i = 0; i < 16; i++) {
          await tester.runAsync(() async {
            final frame = await fixtureFrame(image!, timestamp, dy: i * 4.0);
            timestamp += 100;
            final clock = Stopwatch()..start();
            final result = await tracker.track(frame);
            latencies.add(clock.elapsedMilliseconds);
            expect(result.pose, isNotNull);
            final motion = detector.process(result);
            downRecognized |= motion.action == HandAction.moveDown;
            scroll.handle(motion);
          });
          await tester.pump(const Duration(milliseconds: 30));
        }
        expect(downRecognized, isTrue);
        expect(scroll.controller.offset, greaterThan(0));
        final downOffset = scroll.controller.offset;
        var upRecognized = false;
        for (var i = 0; i < 16; i++) {
          await tester.runAsync(() async {
            final result = await tracker.track(
              await fixtureFrame(image!, timestamp, dy: 60 - i * 4.0),
            );
            timestamp += 100;
            final motion = detector.process(result);
            upRecognized |= motion.action == HandAction.moveUp;
            scroll.handle(motion);
          });
          await tester.pump(const Duration(milliseconds: 30));
        }
        expect(upRecognized, isTrue);
        expect(scroll.controller.offset, lessThan(downOffset));
        for (var i = 0; i < 8; i++) {
          await tester.runAsync(() async {
            final result = await tracker.track(
              await fixtureFrame(image!, timestamp),
            );
            timestamp += 100;
            scroll.handle(detector.process(result));
          });
          await tester.pump(const Duration(milliseconds: 30));
        }
        expect(scroll.velocity.value, 0);
        scroll.handle(
          detector.process(
            HandTrackingResult(timestamp: Duration(milliseconds: timestamp)),
          ),
        );
        final stopped = scroll.controller.offset;
        await tester.pump(const Duration(milliseconds: 100));
        expect(scroll.controller.offset, stopped);
        debugPrint(
          'Fixture native conversion + inference mean: ${latencies.reduce((a, b) => a + b) / latencies.length} ms (emulator, excludes fixture generation)',
        );
      } finally {
        await tester.runAsync(tracker.dispose);
        image?.dispose();
        await tester.pumpWidget(const SizedBox.shrink());
        scroll.dispose();
      }
    },
  );
}
