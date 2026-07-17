import 'dart:convert';
import 'package:flutter/services.dart' show rootBundle;

class AppConfig {
  static late String apiBase;
  static late String osrmBase;
  static late String tileUrl;
  static late String userAgent;

  static Future<void> load() async {
    try {
      final raw = await rootBundle.loadString('assets/config.json');
      final Map<String, dynamic> map = jsonDecode(raw) as Map<String, dynamic>;
      apiBase = map['apiBase']?.toString() ?? 'http://localhost:8080';
      osrmBase =
          map['osrmBase']?.toString() ?? 'https://router.project-osrm.org';
      tileUrl =
          map['tileUrl']?.toString() ??
          'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
      userAgent = map['userAgent']?.toString() ?? 'com.example.driver';
    } catch (_) {
      apiBase = 'http://localhost:8080';
      osrmBase = 'https://router.project-osrm.org';
      tileUrl = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
      userAgent = 'com.example.driver';
    }
  }
}
