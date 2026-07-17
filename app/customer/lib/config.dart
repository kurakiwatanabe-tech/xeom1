import 'dart:convert';
import 'package:flutter/services.dart' show rootBundle;

class AppConfig {
  static late String apiBase;
  static late String osrmBase;
  static late String tileUrl;
  static late String userAgent;

  static Future<void> load() async {
    final raw = await rootBundle.loadString('assets/config.json');
    final Map<String, dynamic> map = jsonDecode(raw) as Map<String, dynamic>;
    apiBase = map['apiBase']?.toString() ?? '';
    osrmBase = map['osrmBase']?.toString() ?? '';
    tileUrl = map['tileUrl']?.toString() ?? '';
    userAgent = map['userAgent']?.toString() ?? 'com.example.driver';
  }
}
