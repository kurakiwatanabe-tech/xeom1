import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:driver/main.dart';

void main() {
  testWidgets('shows phone login dialog for customer info', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: HomePage()));

    await tester.tap(find.byTooltip('Customer info'));
    await tester.pumpAndSettle();

    expect(find.text('Số điện thoại'), findsOneWidget);
    expect(find.text('Đăng nhập'), findsOneWidget);
  });
}
