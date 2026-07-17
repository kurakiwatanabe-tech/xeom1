import 'dart:async';
import 'dart:convert';
import 'dart:developer';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geocoding/geocoding.dart' as geocoding;
import 'package:geolocator/geolocator.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';
import 'config.dart';
import 'login_page.dart';
import 'package:provider/provider.dart';
import 'providers/driver_provider.dart';

String get backendBaseUrl => AppConfig.apiBase;

Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp();
  log(
    'Background FCM message received: ${message.messageId}',
    name: 'driver_app',
  );
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();
  await AppConfig.load();
  FirebaseMessaging.onBackgroundMessage(_firebaseMessagingBackgroundHandler);
  log('Driver app started', name: 'driver_app');
  runApp(
    ChangeNotifierProvider(
      create: (_) => DriverProvider(),
      child: const DriverApp(),
    ),
  );
}

class DriverApp extends StatelessWidget {
  const DriverApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Driver Map',
      theme: ThemeData.from(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
      ),
      home: const LoginPage(),
      routes: {
        '/home': (context) {
          final args =
              ModalRoute.of(context)?.settings.arguments
                  as Map<String, dynamic>?;
          final driverId = args?['id'] as String? ?? '';
          final driverName = args?['name'] as String? ?? 'Tài xế';
          final driverStatus = args?['status'] as String? ?? '';
          return DriverHomePage(
            driverId: driverId,
            driverName: driverName,
            driverStatus: driverStatus,
          );
        },
      },
    );
  }
}

class DriverHomePage extends StatefulWidget {
  final String driverId;
  final String driverName;
  final String? driverStatus;

  const DriverHomePage({
    super.key,
    required this.driverId,
    required this.driverName,
    this.driverStatus,
  });

  @override
  State<DriverHomePage> createState() => _DriverHomePageState();
}

class _DriverHomePageState extends State<DriverHomePage> {
  Position? _position;
  String _address = '';
  bool _loading = false;
  bool _sendingLocation = false;
  bool _routing = false;
  String? _statusMessage;
  String _driverStatus = '';
  final MapController _mapController = MapController();
  StreamSubscription<RemoteMessage>? _fcmSubscription;
  Timer? _gpsRefreshTimer;
  Timer? _heartbeatTimer;
  List<LatLng> _routePoints = <LatLng>[];
  LatLng? _destinationPoint;

  @override
  void initState() {
    super.initState();
    // initial value kept in _driverStatus; provider values will be used when available
    _driverStatus = widget.driverStatus ?? '';
    WidgetsBinding.instance.addPostFrameCallback((_) => _determinePosition());
    _gpsRefreshTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      if (!mounted) return;
      _determinePosition();
    });
    _heartbeatTimer = Timer.periodic(const Duration(seconds: 20), (_) {
      if (!mounted) return;
      _sendHeartbeat();
    });
    _fcmSubscription = FirebaseMessaging.onMessage.listen((message) {
      log(
        'Foreground FCM message received: ${message.messageId}',
        name: 'driver_app',
      );
      final notif = message.notification;
      final title = notif?.title ?? message.data['title'] ?? 'Thông báo';
      final body = notif?.body ?? message.data['body'] ?? '';
      final customerId = message.data['customerId'];
      final lat = message.data['lat'];
      final lng = message.data['lng'];
      final tripId = message.data['tripId'];
      final details = <String>[];
      if (tripId != null && tripId.isNotEmpty) {
        details.add('Trip ID: $tripId');
      }
      if (customerId != null && customerId.isNotEmpty) {
        details.add('Customer ID: $customerId');
      }
      if (lat != null && lat.isNotEmpty && lng != null && lng.isNotEmpty) {
        details.add('Pickup: $lat, $lng');
      }
      final contentText = [if (body.isNotEmpty) body, ...details].join('\n');
      final pickupLat = double.tryParse(lat ?? '');
      final pickupLng = double.tryParse(lng ?? '');
      if (!mounted) return;
      showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(title),
          content: Text(
            contentText.isNotEmpty ? contentText : 'You have a new message',
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
                if (pickupLat != null && pickupLng != null) {
                  _handleTripAccept(
                    pickupLat: pickupLat,
                    pickupLng: pickupLng,
                    tripId: tripId,
                  );
                } else {
                  _showSnack('Không thể đọc tọa độ chuyến từ thông báo.');
                }
              },
              child: const Text('Nhận chuyến'),
            ),
          ],
        ),
      );
    });
  }

  String _driverIdValue() {
    try {
      final provider = Provider.of<DriverProvider>(context, listen: false);
      return provider.id.isNotEmpty ? provider.id : widget.driverId;
    } catch (_) {
      return widget.driverId;
    }
  }

  String _driverNameValue() {
    try {
      final provider = Provider.of<DriverProvider>(context, listen: false);
      return provider.name.isNotEmpty ? provider.name : widget.driverName;
    } catch (_) {
      return widget.driverName;
    }
  }

  String _driverStatusValue() {
    try {
      final provider = Provider.of<DriverProvider>(context);
      return provider.status.isNotEmpty
          ? provider.status
          : (widget.driverStatus ?? _driverStatus);
    } catch (_) {
      return widget.driverStatus ?? _driverStatus;
    }
  }

  @override
  void dispose() {
    _gpsRefreshTimer?.cancel();
    _heartbeatTimer?.cancel();
    _fcmSubscription?.cancel();
    super.dispose();
  }

  Future<void> _determinePosition() async {
    log('Starting GPS detection', name: 'driver_app');
    setState(() {
      _loading = true;
      _statusMessage = null;
    });
    try {
      final serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        log('Location services are disabled', name: 'driver_app');
        _showSnack('Location services are disabled.');
        return;
      }

      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied) {
          log('Location permission denied', name: 'driver_app');
          _showSnack('Location permission denied.');
          return;
        }
      }

      if (permission == LocationPermission.deniedForever) {
        log('Location permission permanently denied', name: 'driver_app');
        _showSnack('Location permissions are permanently denied.');
        return;
      }

      final pos = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.best,
      );
      log(
        'GPS position resolved: ${pos.latitude}, ${pos.longitude}',
        name: 'driver_app',
      );
      final placemarks = await geocoding.Geocoding().placemarkFromCoordinates(
        pos.latitude,
        pos.longitude,
      );
      final address = placemarks.isNotEmpty
          ? [
              placemarks.first.name,
              placemarks.first.street,
              placemarks.first.locality,
              placemarks.first.postalCode,
              placemarks.first.country,
            ].where((s) => s != null && s.isNotEmpty).join(', ')
          : '';

      if (!mounted) return;
      setState(() {
        _position = pos;
        _address = address;
      });

      _mapController.move(LatLng(pos.latitude, pos.longitude), 16);
    } catch (e) {
      log('GPS detection failed: $e', name: 'driver_app');
      _showSnack('Error getting location: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _sendHeartbeat() async {
    final id = _driverIdValue();
    if (_position == null || id.isEmpty) return;
    try {
      final uri = Uri.parse('$backendBaseUrl/api/drivers/$id/heartbeat');
      final resp = await http
          .post(
            uri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'status': 'AVAILABLE',
              'lat': _position!.latitude,
              'lng': _position!.longitude,
            }),
          )
          .timeout(const Duration(seconds: 8));

      if (resp.statusCode == 200) {
        final Map<String, dynamic> body =
            jsonDecode(resp.body) as Map<String, dynamic>;
        final status = (body['status'] ?? '').toString();
        if (status.isNotEmpty && status != _driverStatus) {
          if (!mounted) return;
          setState(() => _driverStatus = status);
        }
      }
    } catch (e) {
      log('Heartbeat failed: $e', name: 'driver_app');
    }
  }

  Future<void> _sendLocation() async {
    if (_position == null) {
      _showSnack('Chưa có vị trí GPS. Nhấn Get GPS trước.');
      return;
    }

    setState(() {
      _sendingLocation = true;
      _statusMessage = null;
    });

    try {
      final uri = Uri.parse(
        '$backendBaseUrl/api/drivers/${widget.driverId}/location',
      );
      log('Sending driver location to $uri', name: 'driver_app');
      final response = await http
          .patch(
            uri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'lat': _position!.latitude,
              'lng': _position!.longitude,
            }),
          )
          .timeout(const Duration(seconds: 10));

      if (!mounted) return;
      if (response.statusCode == 200) {
        log('Driver location sent successfully', name: 'driver_app');
        setState(() {
          _statusMessage = 'Đã gửi vị trí thành công';
        });
      } else {
        log(
          'Driver location send failed with status ${response.statusCode}: ${response.body}',
          name: 'driver_app',
        );
        setState(() {
          _statusMessage = 'Lỗi gửi vị trí: ${response.statusCode}';
        });
      }
    } catch (e) {
      log('Driver location send exception: $e', name: 'driver_app');
      if (!mounted) return;
      setState(() {
        _statusMessage = 'Lỗi gửi vị trí: $e';
      });
    } finally {
      if (mounted) setState(() => _sendingLocation = false);
    }
  }

  void _showSnack(String text) {
    if (!mounted) return;
    log('Snack: $text', name: 'driver_app');
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _handleTripAccept({
    required double pickupLat,
    required double pickupLng,
    String? tripId,
  }) async {
    if (_position == null) {
      _showSnack('Chưa có vị trí GPS. Hãy lấy GPS trước khi nhận chuyến.');
      return;
    }

    if (_routing) return;

    setState(() {
      _routing = true;
      _statusMessage = 'Đang xử lý chuyến...';
    });

    try {
      if (tripId == null || tripId.isEmpty) {
        throw Exception('Thiếu tripId trong thông báo');
      }

      final updateUri = Uri.parse('$backendBaseUrl/api/trips/$tripId');
      final updateResponse = await http
          .put(
            updateUri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'driverId': _driverIdValue(),
              'status': 'ACCEPT',
            }),
          )
          .timeout(const Duration(seconds: 10));

      if (!mounted) return;
      if (updateResponse.statusCode != 200) {
        throw Exception(
          'Trip update failed with status ${updateResponse.statusCode}: ${updateResponse.body}',
        );
      }

      log('Trip $tripId updated to ongoing', name: 'driver_app');

      final uri = Uri.parse(
        'https://router.project-osrm.org/route/v1/driving/${_position!.longitude},${_position!.latitude};$pickupLng,$pickupLat?overview=full&geometries=geojson',
      );
      final response = await http.get(uri).timeout(const Duration(seconds: 15));
      if (!mounted) return;

      if (response.statusCode != 200) {
        throw Exception(
          'Routing failed with status ${response.statusCode}: ${response.body}',
        );
      }

      final body = jsonDecode(response.body) as Map<String, dynamic>;
      final routes = body['routes'] as List<dynamic>?;
      if (routes == null || routes.isEmpty) {
        throw Exception('No route found');
      }

      final geometry = routes.first as Map<String, dynamic>?;
      final coords = geometry?['geometry']?['coordinates'] as List<dynamic>?;
      if (coords == null || coords.isEmpty) {
        throw Exception('Route geometry is empty');
      }

      final points = coords.map<LatLng>((item) {
        final coord = item as List<dynamic>;
        return LatLng(coord[1] as double, coord[0] as double);
      }).toList();

      if (!mounted) return;
      setState(() {
        _routePoints = points;
        _destinationPoint = LatLng(pickupLat, pickupLng);
        _statusMessage = 'Đã nhận chuyến và vẽ tuyến đường tới điểm đón';
      });

      if (points.isNotEmpty) {
        final latSum = points.fold<double>(
          0,
          (sum, point) => sum + point.latitude,
        );
        final lngSum = points.fold<double>(
          0,
          (sum, point) => sum + point.longitude,
        );
        final center = LatLng(latSum / points.length, lngSum / points.length);
        _mapController.move(center, 13);
      }
    } catch (e) {
      log('Route calculation failed: $e', name: 'driver_app');
      if (!mounted) return;
      setState(() => _statusMessage = 'Không thể tạo tuyến: $e');
      _showSnack('Không thể tạo tuyến: $e');
    } finally {
      if (mounted) setState(() => _routing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final center = _routePoints.isNotEmpty
        ? LatLng(
            _routePoints.fold<double>(0, (sum, point) => sum + point.latitude) /
                _routePoints.length,
            _routePoints.fold<double>(
                  0,
                  (sum, point) => sum + point.longitude,
                ) /
                _routePoints.length,
          )
        : _position != null
        ? LatLng(_position!.latitude, _position!.longitude)
        : LatLng(10.8231, 106.6297);

    final markers = <Marker>[];
    if (_position != null) {
      markers.add(
        Marker(
          point: LatLng(_position!.latitude, _position!.longitude),
          width: 56,
          height: 56,
          child: const Icon(Icons.location_pin, color: Colors.red, size: 40),
        ),
      );
    }
    if (_destinationPoint != null) {
      markers.add(
        Marker(
          point: _destinationPoint!,
          width: 56,
          height: 56,
          child: const Icon(Icons.flag, color: Colors.green, size: 40),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(
          'Driver - ${_driverNameValue()}${_driverStatusValue().isNotEmpty ? ' - ${_driverStatusValue()}' : ''}',
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: _loading ? null : _onOpenNearbyDialog,
            tooltip: 'Tìm tài xế gần đây',
          ),
          IconButton(
            icon: const Icon(Icons.my_location),
            onPressed: _loading ? null : _determinePosition,
          ),
        ],
      ),
      body: Stack(
        children: [
          FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: center,
              initialZoom: 13.0,
              interactionOptions: const InteractionOptions(
                flags: InteractiveFlag.all,
              ),
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.example.driver',
              ),
              if (_routePoints.isNotEmpty)
                PolylineLayer(
                  polylines: [
                    Polyline(
                      points: _routePoints,
                      color: Colors.blue,
                      strokeWidth: 6.0,
                    ),
                  ],
                ),
              if (markers.isNotEmpty) MarkerLayer(markers: markers),
            ],
          ),
          Positioned(
            left: 12,
            top: 12,
            right: 12,
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(8.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _position != null
                          ? 'Lat: ${_position!.latitude.toStringAsFixed(6)}'
                          : 'Lat: -',
                    ),
                    Text(
                      _position != null
                          ? 'Lng: ${_position!.longitude.toStringAsFixed(6)}'
                          : 'Lng: -',
                    ),
                    const SizedBox(height: 6),
                    SizedBox(
                      width: double.infinity,
                      child: Text(
                        _address.isNotEmpty ? _address : 'Address: -',
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    if (_statusMessage != null) ...[
                      const SizedBox(height: 6),
                      Text(
                        _statusMessage!,
                        style: TextStyle(
                          color: _statusMessage!.contains('Lỗi')
                              ? Colors.red
                              : Colors.green,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
      floatingActionButton: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          FloatingActionButton.extended(
            onPressed: _loading ? null : _determinePosition,
            icon: const Icon(Icons.gps_fixed),
            label: Text(_loading ? 'Locating...' : 'Get GPS'),
          ),
          const SizedBox(height: 12),
          FloatingActionButton.extended(
            onPressed: _position == null || _sendingLocation
                ? null
                : _sendLocation,
            icon: const Icon(Icons.send),
            label: Text(_sendingLocation ? 'Sending...' : 'Send location'),
          ),
        ],
      ),
    );
  }

  Future<void> _onOpenNearbyDialog() async {
    if (_position == null) {
      _showSnack('Chưa có vị trí GPS. Nhấn Get GPS trước.');
      return;
    }

    final controller = TextEditingController(text: '5');
    final value = await showDialog<String?>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Tìm theo bán kính (km)'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(hintText: 'Số km (ví dụ: 5)'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Huỷ'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(context).pop(controller.text.trim()),
            child: const Text('Tìm'),
          ),
        ],
      ),
    );

    if (value == null || value.isEmpty) return;
    final radius = double.tryParse(value) ?? 5.0;
    log('Searching nearby drivers within ${radius}km', name: 'driver_app');
    await _searchNearby(radius);
  }

  Future<void> _searchNearby(double radiusKm) async {
    setState(() => _statusMessage = 'Đang tìm trong ${radiusKm}km...');
    try {
      final uri = Uri.parse('$backendBaseUrl/api/drivers/nearby').replace(
        queryParameters: {
          'lat': _position!.latitude.toString(),
          'lng': _position!.longitude.toString(),
          'radius_km': radiusKm.toString(),
          'limit': '20',
          'status': 'AVAILABLE',
        },
      );

      log('Requesting nearby drivers from $uri', name: 'driver_app');
      final resp = await http.get(uri).timeout(const Duration(seconds: 10));
      if (!mounted) return;
      if (resp.statusCode == 200) {
        final body = jsonDecode(resp.body) as Map<String, dynamic>;
        final List<dynamic> data = body['data'] ?? <dynamic>[];
        log(
          'Nearby drivers response received: ${data.length} items',
          name: 'driver_app',
        );
        setState(() => _statusMessage = 'Tìm thấy ${data.length} tài xế');
        showModalBottomSheet(
          context: context,
          builder: (context) => SafeArea(
            child: ListView.separated(
              padding: const EdgeInsets.all(12),
              itemCount: data.length,
              separatorBuilder: (context, index) => const Divider(),
              itemBuilder: (context, idx) {
                final item = data[idx] as Map<String, dynamic>;
                final name = item['name'] ?? 'Không tên';
                final phone = item['phone'] ?? '';
                final lat = item['lat'];
                final lng = item['lng'];
                return ListTile(
                  title: Text(name.toString()),
                  subtitle: Text('Phone: $phone\nLat: $lat, Lng: $lng'),
                );
              },
            ),
          ),
        );
      } else {
        log(
          'Nearby drivers request failed with status ${resp.statusCode}: ${resp.body}',
          name: 'driver_app',
        );
        setState(
          () => _statusMessage = 'Lỗi khi tìm nearby: ${resp.statusCode}',
        );
      }
    } catch (e) {
      log('Nearby drivers request exception: $e', name: 'driver_app');
      if (!mounted) return;
      setState(() => _statusMessage = 'Lỗi: $e');
    }
  }
}
