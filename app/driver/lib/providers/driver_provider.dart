import 'package:flutter/foundation.dart';

class DriverProvider extends ChangeNotifier {
  String id = '';
  String name = '';
  String status = '';
  double? lat;
  double? lng;
  String? fcmToken;

  void setFromMap(Map<String, dynamic> map) {
    id = map['id']?.toString() ?? id;
    name = map['name']?.toString() ?? name;
    status = map['status']?.toString() ?? status;
    lat = map['lat'] is num ? (map['lat'] as num).toDouble() : lat;
    lng = map['lng'] is num ? (map['lng'] as num).toDouble() : lng;
    fcmToken = map['fcmToken']?.toString() ?? fcmToken;
    notifyListeners();
  }

  void updateStatus(String newStatus) {
    if (newStatus != status) {
      status = newStatus;
      notifyListeners();
    }
  }

  void updateLocation(double newLat, double newLng) {
    lat = newLat;
    lng = newLng;
    notifyListeners();
  }

  void clear() {
    id = '';
    name = '';
    status = '';
    lat = null;
    lng = null;
    fcmToken = null;
    notifyListeners();
  }
}
