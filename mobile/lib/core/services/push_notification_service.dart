import 'dart:convert';
import 'dart:io' show Platform;
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/auth/data/dto/auth_user.dart';
import '../../features/auth/presentation/providers/auth_controller.dart';
import '../constants/api_paths.dart';
import '../network/network_providers.dart';
import '../routing/app_router.dart';
import '../routing/routes.dart';

@pragma('vm:entry-point')
Future<void> _firebaseBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp();
  debugPrint("Handling background FCM message: ${message.messageId}");
}

class PushNotificationService {
  PushNotificationService(this.ref);

  final Ref ref;
  final FlutterLocalNotificationsPlugin _localNotificationsPlugin =
      FlutterLocalNotificationsPlugin();

  static const AndroidNotificationChannel _channel = AndroidNotificationChannel(
    'high_importance_channel',
    'High Importance Notifications',
    description: 'This channel is used for important push notifications.',
    importance: Importance.max,
  );

  /// Initializes FCM and Local Notifications settings.
  Future<void> init() async {
    if (kIsWeb || !Platform.isAndroid) return;

    try {
      // 1. Request notification permissions from user
      final messaging = FirebaseMessaging.instance;
      final settings = await messaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );

      debugPrint('FCM Notification permission status: ${settings.authorizationStatus}');

      // 2. Configure background message handler
      FirebaseMessaging.onBackgroundMessage(_firebaseBackgroundHandler);

      // 3. Initialize Flutter Local Notifications for Foreground message display
      const initializationSettingsAndroid =
          AndroidInitializationSettings('@mipmap/ic_launcher');
      const initializationSettings = InitializationSettings(
        android: initializationSettingsAndroid,
      );

      await _localNotificationsPlugin.initialize(
        initializationSettings,
        onDidReceiveNotificationResponse: (NotificationResponse response) {
          final payload = response.payload;
          if (payload != null) {
            _handleNotificationPayload(payload);
          }
        },
      );

      // 4. Create the high importance channel (required for Android 8+)
      await _localNotificationsPlugin
          .resolvePlatformSpecificImplementation<
              AndroidFlutterLocalNotificationsPlugin>()
          ?.createNotificationChannel(_channel);

      // 5. Handle foreground FCM messages
      FirebaseMessaging.onMessage.listen((RemoteMessage message) {
        debugPrint('FCM Foreground message received: ${message.messageId}');
        final notification = message.notification;
        final android = message.notification?.android;

        if (notification != null) {
          _localNotificationsPlugin.show(
            notification.hashCode,
            notification.title,
            notification.body,
            NotificationDetails(
              android: AndroidNotificationDetails(
                _channel.id,
                _channel.name,
                channelDescription: _channel.description,
                icon: android?.smallIcon ?? '@mipmap/ic_launcher',
                importance: Importance.max,
                priority: Priority.high,
              ),
            ),
            payload: jsonEncode(message.data),
          );
        }
      });

      // 6. Handle notification click when app is in background but still running
      FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
        debugPrint('FCM Notification clicked while app in background: ${message.messageId}');
        _handleNotificationData(message.data);
      });

      // 7. Handle notification click when app is opened from a terminated state
      final initialMessage = await messaging.getInitialMessage();
      if (initialMessage != null) {
        debugPrint('FCM Initial message received: ${initialMessage.messageId}');
        _handleNotificationData(initialMessage.data);
      }

      // 8. Listen to token refresh
      messaging.onTokenRefresh.listen((newToken) {
        debugPrint('FCM Token refreshed: $newToken');
        final user = ref.read(currentUserProvider);
        if (user != null) {
          _registerToken(newToken);
        }
      });

      // 9. If user is already authenticated at boot, register token immediately
      final user = ref.read(currentUserProvider);
      if (user != null) {
        registerCurrentToken();
      }

    } catch (e) {
      debugPrint('Error initializing PushNotificationService: $e');
    }
  }

  /// Get current FCM token and register it to backend
  Future<void> registerCurrentToken() async {
    try {
      final token = await FirebaseMessaging.instance.getToken();
      if (token != null) {
        await _registerToken(token);
      }
    } catch (e) {
      debugPrint('Error getting current FCM token: $e');
    }
  }

  /// Delete registration of current FCM token on logout
  Future<void> unregisterCurrentToken() async {
    try {
      final token = await FirebaseMessaging.instance.getToken();
      if (token != null) {
        await _unregisterToken(token);
      }
    } catch (e) {
      debugPrint('Error unregistering current FCM token: $e');
    }
  }

  Future<void> _registerToken(String token) async {
    try {
      final client = ref.read(apiClientProvider);
      final deviceInfo = 'Android ${Platform.operatingSystemVersion}';
      
      await client.post<void>(
        ApiPaths.deviceTokens,
        data: {
          'fcmToken': token,
          'deviceInfo': deviceInfo,
        },
        decode: (_) {},
      );
      debugPrint('Registered FCM token to backend successfully.');
    } catch (e) {
      debugPrint('Failed to register FCM token to backend: $e');
    }
  }

  Future<void> _unregisterToken(String token) async {
    try {
      final client = ref.read(apiClientProvider);
      await client.delete<void>(
        '${ApiPaths.deviceTokens}?fcmToken=${Uri.encodeComponent(token)}',
        decode: (_) {},
      );
      debugPrint('Unregistered FCM token from backend successfully.');
    } catch (e) {
      debugPrint('Failed to unregister FCM token from backend: $e');
    }
  }

  void _handleNotificationPayload(String payloadJson) {
    try {
      final data = jsonDecode(payloadJson) as Map<String, dynamic>;
      _handleNotificationData(data);
    } catch (e) {
      debugPrint('Error parsing local notification payload: $e');
    }
  }

  void _handleNotificationData(Map<String, dynamic> data) {
    final relatedEntity = data['relatedEntity'] as String?;
    final relatedId = data['relatedId'] as String?;

    if (relatedEntity == null || relatedEntity.trim().isEmpty) {
      // Default fallback: navigate to notifications center
      ref.read(routerProvider).push(Routes.notifications);
      return;
    }

    String? path;
    final entityKey = relatedEntity.toUpperCase();

    if (entityKey.contains('TASK') && relatedId != null) {
      path = Routes.taskDetailPath(relatedId);
    } else if (entityKey.contains('LEAD') && relatedId != null) {
      path = Routes.leadDetailPath(relatedId);
    } else if (entityKey.contains('DEAL') && relatedId != null) {
      path = Routes.dealDetailPath(relatedId);
    } else if (entityKey.contains('BOOKING') && relatedId != null) {
      path = Routes.bookingDetailPath(relatedId);
    } else if (entityKey.contains('PAYMENT') && relatedId != null) {
      path = Routes.paymentDetailPath(relatedId);
    } else if (entityKey.contains('QUOTATION') && relatedId != null) {
      path = Routes.quotationDetailPath(relatedId);
    } else if (entityKey.contains('CUSTOMER') && relatedId != null) {
      path = Routes.customerDetailPath(relatedId);
    } else if (entityKey.contains('SLA')) {
      path = Routes.slaPath(highlightId: relatedId);
    } else if (entityKey.contains('REMINDER')) {
      path = Routes.remindersPath(highlightId: relatedId);
    } else {
      path = Routes.notifications;
    }

    debugPrint('Deep-linking notification to: $path');
    ref.read(routerProvider).push(path);
  }
}

final pushNotificationServiceProvider = Provider<PushNotificationService>((ref) {
  final service = PushNotificationService(ref);

  // Sync token whenever user logs in or out
  ref.listen<AuthUser?>(currentUserProvider, (previous, next) {
    if (next != null) {
      service.registerCurrentToken();
    } else if (previous != null) {
      service.unregisterCurrentToken();
    }
  });

  return service;
});
