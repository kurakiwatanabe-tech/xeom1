import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geocoding/geocoding.dart';
import 'package:geolocator/geolocator.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';
import 'config.dart';

class CustomerInfo {
  final String id;
  final String name;
  final String phone;
  final String email;
  final String address;
  final String createdAt;

  const CustomerInfo({
    required this.id,
    required this.name,
    required this.phone,
    required this.email,
    required this.address,
    required this.createdAt,
  });

  factory CustomerInfo.fromJson(Map<String, dynamic> json) {
    return CustomerInfo(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      phone: json['phone']?.toString() ?? '',
      email: json['email']?.toString() ?? '',
      address: json['address']?.toString() ?? '',
      createdAt: json['createdAt']?.toString() ?? '',
    );
  }
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppConfig.load();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Driver GPS Map',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
      ),
      home: const LoginGate(),
    );
  }
}

class LoginGate extends StatelessWidget {
  const LoginGate({super.key});

  @override
  Widget build(BuildContext context) {
    return CustomerLoginPage(
      onLoggedIn: (customer) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(
            builder: (_) => HomePage(customer: customer),
          ),
        );
      },
    );
  }
}

class HomePage extends StatefulWidget {
  final CustomerInfo customer;

  const HomePage({super.key, required this.customer});

  @override
  State<HomePage> createState() => _HomePageState();
}

class MapZoomControls extends StatelessWidget {
  final MapController mapController;

  const MapZoomControls({super.key, required this.mapController});

  void _zoomBy(double delta) {
    final currentZoom = mapController.camera.zoom;
    final nextZoom = (currentZoom + delta).clamp(2.0, 18.0);
    mapController.move(mapController.camera.center, nextZoom);
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.97),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.15),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            tooltip: 'Zoom in',
            icon: const Icon(Icons.zoom_in),
            onPressed: () => _zoomBy(1),
          ),
          const Divider(height: 1),
          IconButton(
            tooltip: 'Zoom out',
            icon: const Icon(Icons.zoom_out),
            onPressed: () => _zoomBy(-1),
          ),
        ],
      ),
    );
  }
}

class CustomerInfoPage extends StatelessWidget {
  final CustomerInfo customer;

  const CustomerInfoPage({super.key, required this.customer});

  @override
  Widget build(BuildContext context) {
    final infoRows = [
      _InfoRow(label: 'ID', value: customer.id),
      _InfoRow(label: 'Name', value: customer.name),
      _InfoRow(label: 'Phone', value: customer.phone),
      _InfoRow(label: 'Email', value: customer.email),
      _InfoRow(label: 'Address', value: customer.address),
      _InfoRow(label: 'Created At', value: customer.createdAt),
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('Customer Info')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Customer Details',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 16),
                ...infoRows.map(
                  (row) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          width: 100,
                          child: Text(
                            row.label,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                        ),
                        Expanded(child: Text(row.value)),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _InfoRow {
  final String label;
  final String value;

  const _InfoRow({required this.label, required this.value});
}

Future<CustomerInfo> fetchCustomerByPhone(String phone) async {
  final response = await http.get(
    Uri.parse('${AppConfig.apiBase}/api/customers/phone/$phone'),
  );

  if (response.statusCode != 200) {
    throw Exception('Login failed with status ${response.statusCode}.');
  }

  final decodedBody = jsonDecode(response.body);
  dynamic customerPayload = decodedBody;

  if (customerPayload is Map && customerPayload.containsKey('customer')) {
    customerPayload = customerPayload['customer'];
  } else if (customerPayload is Map && customerPayload.containsKey('data')) {
    customerPayload = customerPayload['data'];
  } else if (customerPayload is List && customerPayload.isNotEmpty) {
    customerPayload = customerPayload.first;
  }

  if (customerPayload is! Map) {
    throw Exception('Customer data was not returned in the expected format.');
  }

  return CustomerInfo.fromJson(Map<String, dynamic>.from(customerPayload));
}

class CustomerLoginPage extends StatefulWidget {
  final ValueChanged<CustomerInfo> onLoggedIn;

  const CustomerLoginPage({super.key, required this.onLoggedIn});

  @override
  State<CustomerLoginPage> createState() => _CustomerLoginPageState();
}

class _CustomerLoginPageState extends State<CustomerLoginPage> {
  final TextEditingController _phoneController =
      TextEditingController(text: '0901234567');
  bool _loading = false;

  @override
  void dispose() {
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final phone = _phoneController.text.trim();
    if (phone.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng nhập số điện thoại.')),
      );
      return;
    }

    setState(() {
      _loading = true;
    });

    try {
      final customer = await fetchCustomerByPhone(phone);
      widget.onLoggedIn(customer);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Không thể tìm khách hàng: $e')),
      );
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Đăng nhập khách hàng')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Nhập số điện thoại để tìm khách hàng',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(
                labelText: 'Số điện thoại',
                hintText: '0901234567',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _loading ? null : _submit,
              child: _loading
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Text('Tìm khách hàng'),
            ),
          ],
        ),
      ),
    );
  }
}

class _HomePageState extends State<HomePage> {
  final MapController _mapController = MapController();
  final TextEditingController _fromController = TextEditingController();
  final TextEditingController _toController = TextEditingController();
  Position? _position;
  String _address = '';
  bool _loading = false;
  bool _routeLoading = false;
  LatLng? _origin;
  LatLng? _destination;
  List<LatLng> _routePoints = [];
  String _routeInfo = '';
  double _routeDistanceKm = 0.0;
  late CustomerInfo _customer;

  @override
  void initState() {
    super.initState();
    _customer = widget.customer;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _getLocation();
    });
  }

  Future<void> _getLocation() async {
    setState(() {
      _loading = true;
      _address = '';
    });

    try {
      final serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Location services are disabled.')),
        );
        return;
      }

      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied) {
          if (!mounted) return;
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Location permission denied.')),
          );
          return;
        }
      }

      if (permission == LocationPermission.deniedForever) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Location permissions are permanently denied.'),
          ),
        );
        return;
      }

      final pos = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.best,
      );

      final placemarks = await placemarkFromCoordinates(
        pos.latitude,
        pos.longitude,
      );

      if (!mounted) return;

      setState(() {
        _position = pos;
        _address = placemarks.isNotEmpty
            ? [
                placemarks.first.name,
                placemarks.first.street,
                placemarks.first.locality,
                placemarks.first.postalCode,
                placemarks.first.country,
              ].where((value) => value != null && value.isNotEmpty).join(', ')
            : 'No address found';
        if (_fromController.text.isEmpty) {
          _fromController.text = _address;
        }
      });

      _mapController.move(LatLng(pos.latitude, pos.longitude), 16);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Error: $e')));
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  Future<void> _bookRide() async {
    if (_customer == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng đăng nhập khách hàng trước khi đặt xe.')),
      );
      return;
    }

    if (_origin == null || _destination == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Vui lòng tìm đường trước khi đặt xe.')),
      );
      return;
    }

    await _submitTrip();
  }

  Future<void> _submitTrip() async {
    final longitudeStart = _origin!.longitude;
    final latitudeStart = _origin!.latitude;
    final distance = _routeDistanceKm;
    final price = (distance * 10000).round();

    try {
      final response = await http.post(
        Uri.parse('${AppConfig.apiBase}/api/trips'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'customerId': _customer!.id,
          'price': price,
          'addressStart': _fromController.text.trim(),
          'addressEnd': _toController.text.trim(),
          'longitudeStart': longitudeStart,
          'latitudeStart': latitudeStart,
          'distance': distance,
        }),
      );

      if (response.statusCode != 200 && response.statusCode != 201) {
        throw Exception('Trip request failed with status ${response.statusCode}.');
      }

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đặt xe thành công!')), 
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Không thể đặt xe: $e')),
      );
    }
  }

  void _openLoginPage(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => CustomerLoginPage(
          onLoggedIn: (customer) {
            setState(() {
              _customer = customer;
            });
          },
        ),
      ),
    );
  }

  Future<void> _findRoute() async {
    final fromText = _fromController.text.trim();
    final toText = _toController.text.trim();

    if (fromText.isEmpty || toText.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please enter both start and destination.'),
        ),
      );
      return;
    }

    setState(() {
      _routeLoading = true;
      _routePoints = [];
      _routeInfo = '';
    });

    try {
      final startLocations = await locationFromAddress(fromText);
      final destinationLocations = await locationFromAddress(toText);

      if (startLocations.isEmpty || destinationLocations.isEmpty) {
        throw Exception('Unable to find the entered locations.');
      }

      final start = startLocations.first;
      final destination = destinationLocations.first;
      final startPoint = LatLng(start.latitude, start.longitude);
      final destinationPoint = LatLng(
        destination.latitude,
        destination.longitude,
      );

      final response = await http.get(
        Uri.parse(
          '${AppConfig.osrmBase}/route/v1/driving/${start.longitude},${start.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=geojson',
        ),
      );

      if (response.statusCode != 200) {
        throw Exception('Route service returned ${response.statusCode}.');
      }

      final body = jsonDecode(response.body) as Map<String, dynamic>;
      final routes = body['routes'] as List<dynamic>;
      if (routes.isEmpty) {
        throw Exception('No route found.');
      }

      final geometry = routes.first['geometry'] as Map<String, dynamic>;
      final coords = geometry['coordinates'] as List<dynamic>;
      final points = coords
          .map<LatLng>(
            (coord) => LatLng(coord[1].toDouble(), coord[0].toDouble()),
          )
          .toList();
      final distanceMeters = routes.first['distance'] as num;
      final distanceKm = distanceMeters / 1000.0;

      if (!mounted) return;
      setState(() {
        _origin = startPoint;
        _destination = destinationPoint;
        _routePoints = points;
        _routeDistanceKm = distanceKm;
        _routeInfo =
            'Shortest route: ${distanceKm.toStringAsFixed(1)} km';
      });

      final bounds = LatLngBounds.fromPoints(points);
      _mapController.fitCamera(
        CameraFit.bounds(bounds: bounds, padding: const EdgeInsets.all(48)),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Route error: $e')));
    } finally {
      if (mounted) {
        setState(() {
          _routeLoading = false;
        });
      }
    }
  }

  @override
  void dispose() {
    _fromController.dispose();
    _toController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final currentLocation = _position != null
        ? LatLng(_position!.latitude, _position!.longitude)
        : const LatLng(10.8231, 106.6297);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Khách hàng '),
        actions: [
          IconButton(
            icon: const Icon(Icons.person_outline),
            tooltip: 'Customer login',
            onPressed: () => _openLoginPage(context),
          ),
        ],
      ),
      body: Stack(
        children: [
          FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: currentLocation,
              initialZoom: 13.0,
              interactionOptions: const InteractionOptions(
                flags: InteractiveFlag.all,
              ),
            ),
            children: [
              TileLayer(
                urlTemplate: AppConfig.tileUrl,
                userAgentPackageName: AppConfig.userAgent,
              ),
              if (_routePoints.isNotEmpty)
                PolylineLayer(
                  polylines: [
                    Polyline(
                      points: _routePoints,
                      color: Colors.blueAccent,
                      strokeWidth: 6.0,
                    ),
                  ],
                ),
              MarkerLayer(
                markers: [
                  if (_position != null)
                    Marker(
                      point: currentLocation,
                      width: 60,
                      height: 60,
                      child: const Icon(
                        Icons.location_pin,
                        color: Colors.red,
                        size: 40,
                      ),
                    ),
                  if (_origin != null)
                    Marker(
                      point: _origin!,
                      width: 60,
                      height: 60,
                      child: const Icon(
                        Icons.trip_origin,
                        color: Colors.green,
                        size: 36,
                      ),
                    ),
                  if (_destination != null)
                    Marker(
                      point: _destination!,
                      width: 60,
                      height: 60,
                      child: const Icon(
                        Icons.flag,
                        color: Colors.deepOrange,
                        size: 36,
                      ),
                    ),
                ],
              ),
            ],
          ),
          Positioned(
            left: 16,
            right: 16,
            top: 16,
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.97),
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.15),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Column(
                children: [
                  TextField(
                    controller: _fromController,
                    decoration: const InputDecoration(
                      labelText: 'Điểm đi',
                      prefixIcon: Icon(Icons.meeting_room),
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _toController,
                    decoration: const InputDecoration(
                      labelText: 'Điểm đến',
                      prefixIcon: Icon(Icons.exit_to_app),
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: _routeLoading ? null : _findRoute,
                      icon: _routeLoading
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.route),
                      label: Text(
                        _routeLoading ? 'Đang tìm đường...' : 'Tìm đường',
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: _bookRide,
                      icon: const Icon(Icons.local_taxi),
                      label: const Text('Đặt xe'),
                    ),
                  ),

                  if (_routeInfo.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(
                      _routeInfo,
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                  ],
                ],
              ),
            ),
          ),
          Positioned(
            right: 16,
            bottom: 24,
            child: MapZoomControls(mapController: _mapController),
          ),
          Positioned(
            left: 16,
            right: 16,
            bottom: 132,
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.95),
                borderRadius: BorderRadius.circular(12),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.15),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _loading
                        ? 'Getting your location...'
                        : _position == null
                        ? 'Tap the button to get GPS'
                        : 'Current location',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 6),
                  if (_position != null) ...[
                    Text('Latitude: ${_position!.latitude.toStringAsFixed(6)}'),
                    Text(
                      'Longitude: ${_position!.longitude.toStringAsFixed(6)}',
                    ),
                  ],
                  if (_address.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: Text(_address),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _loading ? null : _getLocation,
        icon: const Icon(Icons.my_location),
        label: const Text('Get GPS'),
      ),
    );
  }
}
