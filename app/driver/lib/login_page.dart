import 'dart:convert';
import 'dart:developer';

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'config.dart';

String get backendBaseUrl => AppConfig.apiBase;

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _phoneController = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    final phone = _phoneController.text.trim();

    if (phone.isEmpty) {
      setState(() => _error = 'Vui lòng nhập số điện thoại');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      log('Attempting driver login for phone: $phone', name: 'driver_app');
      final response = await http
          .get(Uri.parse('$backendBaseUrl/api/drivers/by-phone/$phone'))
          .timeout(const Duration(seconds: 10));

      if (!mounted) return;

      if (response.statusCode == 200) {
        final driver = jsonDecode(response.body) as Map<String, dynamic>;
        log('Driver login success: ${driver['name']}', name: 'driver_app');
        await _registerFcmToken(driver['id'].toString());
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Đăng nhập thành công: ${driver['name']}')),
          );
          Navigator.of(
            context,
          ).pushReplacementNamed('/home', arguments: driver);
        }
      } else if (response.statusCode == 404) {
        log('Driver login failed: user not found', name: 'driver_app');
        setState(() => _error = 'Không tìm thấy tài xế với số điện thoại này');
      } else {
        log(
          'Driver login failed with status ${response.statusCode}',
          name: 'driver_app',
        );
        setState(() => _error = 'Lỗi: ${response.statusCode}');
      }
    } catch (e) {
      log('Driver login exception: $e', name: 'driver_app');
      setState(() => _error = 'Lỗi kết nối: $e');
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _registerFcmToken(String driverId) async {
    try {
      final messaging = FirebaseMessaging.instance;
      final settings = await messaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
        announcement: false,
        carPlay: false,
        criticalAlert: false,
        provisional: false,
      );
      log(
        'FCM permission status: ${settings.authorizationStatus}',
        name: 'driver_app',
      );

      final token = await messaging.getToken();
      if (token == null || token.isEmpty) {
        throw Exception('Không lấy được FCM token');
      }

      final uri = Uri.parse('$backendBaseUrl/api/drivers/$driverId/fcm-token');
      log(
        'Registering FCM token for driver $driverId at $uri',
        name: 'driver_app',
      );
      final response = await http
          .post(
            uri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'token': token}),
          )
          .timeout(const Duration(seconds: 10));

      if (response.statusCode != 200) {
        log(
          'FCM registration failed: ${response.statusCode} ${response.body}',
          name: 'driver_app',
        );
        throw Exception('Lỗi cập nhật token FCM: ${response.statusCode}');
      }

      log(
        'FCM token updated successfully for driver $driverId',
        name: 'driver_app',
      );
    } catch (e) {
      log('FCM registration error: $e', name: 'driver_app');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('FCM token không cập nhật được: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Đăng nhập Tài xế'), centerTitle: true),
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Center(
          child: SingleChildScrollView(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  Icons.directions_car,
                  size: 80,
                  color: Theme.of(context).primaryColor,
                ),
                const SizedBox(height: 32),
                Text(
                  'Đăng nhập hệ thống tài xế',
                  style: Theme.of(context).textTheme.headlineSmall,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                TextField(
                  controller: _phoneController,
                  keyboardType: TextInputType.phone,
                  decoration: InputDecoration(
                    labelText: 'Số điện thoại',
                    hintText: '0987654321',
                    prefixIcon: const Icon(Icons.phone),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    errorText: _error,
                  ),
                  enabled: !_loading,
                  onSubmitted: (_) => _login(),
                ),
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: ElevatedButton(
                    onPressed: _loading ? null : _login,
                    child: _loading
                        ? const SizedBox(
                            height: 24,
                            width: 24,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text(
                            'Đăng nhập',
                            style: TextStyle(fontSize: 16),
                          ),
                  ),
                ),
                if (_error != null) ...[
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Colors.red.shade50,
                      border: Border.all(color: Colors.red),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      _error!,
                      style: TextStyle(color: Colors.red.shade700),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
