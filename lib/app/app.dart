import 'package:flutter/material.dart';

import '../features/auto_scroll/presentation/tracking_screen.dart';

class AutoScrollApp extends StatelessWidget {
  const AutoScrollApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Hand Scroll Lab',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(colorSchemeSeed: Colors.teal, useMaterial3: true),
    home: const TrackingScreen(),
  );
}
