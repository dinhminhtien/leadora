import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:supabase_flutter/supabase_flutter.dart' as supabase;
import 'package:leadora_mobile/features/auth/data/auth_repository.dart';
import 'package:leadora_mobile/features/auth/data/dto/auth_user.dart';
import 'package:leadora_mobile/core/network/api_client.dart';
import 'package:leadora_mobile/core/storage/token_store.dart';
import 'package:leadora_mobile/core/storage/secure_storage.dart';
import 'package:leadora_mobile/core/constants/api_paths.dart';

class MockApiClient extends Mock implements ApiClient {}

class MockTokenStore extends Mock implements TokenStore {}

class MockSecureStorage extends Mock implements SecureStorage {}

class MockGoogleSignIn extends Mock implements GoogleSignIn {}

class MockGoogleSignInAccount extends Mock implements GoogleSignInAccount {}

class MockGoogleSignInAuthentication extends Mock
    implements GoogleSignInAuthentication {}

class MockSupabaseClient extends Mock implements supabase.SupabaseClient {}

class MockGoTrueClient extends Mock implements supabase.GoTrueClient {}

class MockAuthResponse extends Mock implements supabase.AuthResponse {}

class MockSession extends Mock implements supabase.Session {}

class MockOAuthResponse extends Mock implements supabase.OAuthResponse {}

void main() {
  late MockApiClient mockApiClient;
  late MockTokenStore mockTokenStore;
  late MockSecureStorage mockSecureStorage;
  late MockGoogleSignIn mockGoogleSignIn;
  late MockGoogleSignInAccount mockGoogleSignInAccount;
  late MockGoogleSignInAuthentication mockGoogleSignInAuth;
  late MockSupabaseClient mockSupabaseClient;
  late MockGoTrueClient mockGoTrueClient;
  late MockAuthResponse mockAuthResponse;
  late MockOAuthResponse mockOAuthResponse;
  late MockSession mockSession;
  late AuthRepository repository;

  setUpAll(() {
    registerFallbackValue(supabase.OAuthProvider.google);
  });

  setUp(() {
    mockApiClient = MockApiClient();
    mockTokenStore = MockTokenStore();
    mockSecureStorage = MockSecureStorage();
    mockGoogleSignIn = MockGoogleSignIn();
    mockGoogleSignInAccount = MockGoogleSignInAccount();
    mockGoogleSignInAuth = MockGoogleSignInAuthentication();
    mockSupabaseClient = MockSupabaseClient();
    mockGoTrueClient = MockGoTrueClient();
    mockAuthResponse = MockAuthResponse();
    mockOAuthResponse = MockOAuthResponse();
    mockSession = MockSession();

    when(() => mockOAuthResponse.url).thenReturn('https://example.com/oauth');

    repository = AuthRepository(
      client: mockApiClient,
      tokenStore: mockTokenStore,
      secureStorage: mockSecureStorage,
      googleSignIn: mockGoogleSignIn,
      supabaseClient: mockSupabaseClient,
    );

    // Default wiring for successful Google + Supabase flow
    when(
      () => mockGoogleSignIn.signIn(),
    ).thenAnswer((_) async => mockGoogleSignInAccount);
    when(
      () => mockGoogleSignInAccount.authentication,
    ).thenAnswer((_) async => mockGoogleSignInAuth);
    when(() => mockGoogleSignInAuth.idToken).thenReturn('google-id-token');
    when(
      () => mockGoogleSignInAuth.accessToken,
    ).thenReturn('google-access-token');

    when(() => mockSupabaseClient.auth).thenReturn(mockGoTrueClient);
    when(
      () => mockGoTrueClient.signInWithIdToken(
        provider: any(named: 'provider'),
        idToken: any(named: 'idToken'),
        accessToken: any(named: 'accessToken'),
      ),
    ).thenAnswer((_) async => mockAuthResponse);
    when(() => mockAuthResponse.session).thenReturn(mockSession);
    when(() => mockSession.accessToken).thenReturn('supabase-access-token');

    when(() => mockGoTrueClient.signOut()).thenAnswer((_) async => {});
    when(
      () => mockGoogleSignIn.signOut(),
    ).thenAnswer((_) async => mockGoogleSignInAccount);
  });

  group('loginWithGoogle & verifyOAuthSession', () {
    test('loginWithGoogle triggers Supabase signInWithOAuth', () async {
      when(
        () => mockGoTrueClient.getOAuthSignInUrl(
          provider: any(named: 'provider'),
          redirectTo: any(named: 'redirectTo'),
          scopes: any(named: 'scopes'),
          queryParams: any(named: 'queryParams'),
        ),
      ).thenAnswer((_) async => mockOAuthResponse);

      try {
        await repository.loginWithGoogle();
      } catch (_) {}

      verify(
        () => mockGoTrueClient.getOAuthSignInUrl(
          provider: supabase.OAuthProvider.google,
          redirectTo: any(named: 'redirectTo'),
        ),
      ).called(1);
    });

    test(
      'verifyOAuthSession calls backend verify endpoint and returns AuthUser',
      () async {
        when(
          () => mockApiClient.get<AuthUser>(
            ApiPaths.oauthVerify,
            headers: any(named: 'headers'),
            decode: any(named: 'decode'),
          ),
        ).thenAnswer((invocation) async {
          final decode =
              invocation.namedArguments[#decode] as AuthUser Function(Object?);
          return decode({
            'id': '1',
            'email': 'test@leadora.vn',
            'name': 'John Doe',
            'roles': ['SALES'],
            'permissions': ['LEAD_VIEW'],
          });
        });

        final result = await repository.verifyOAuthSession(
          'supabase-access-token',
        );

        expect(result.id, '1');
        expect(result.email, 'test@leadora.vn');

        verify(
          () => mockApiClient.get<AuthUser>(
            ApiPaths.oauthVerify,
            headers: {'Authorization': 'Bearer supabase-access-token'},
            decode: any(named: 'decode'),
          ),
        ).called(1);
      },
    );
  });

  group('logout', () {
    test(
      'clears everything and signs out Google/Supabase even if backend logout fails',
      () async {
        when(
          () => mockApiClient.post<void>(
            ApiPaths.logout,
            decode: any(named: 'decode'),
          ),
        ).thenThrow(Exception('Server unreachable'));

        when(() => mockTokenStore.clear()).thenAnswer((_) async => {});
        when(() => mockSecureStorage.delete(any())).thenAnswer((_) async => {});

        await repository.logout();

        verify(() => mockGoTrueClient.signOut()).called(1);
        verify(() => mockGoogleSignIn.signOut()).called(1);
        verify(() => mockTokenStore.clear()).called(1);
        verify(() => mockSecureStorage.delete(any())).called(1);
      },
    );
  });

  group('forgotPassword and resetPassword', () {
    test(
      'forgotPassword calls forgotPassword endpoint with email and clientType',
      () async {
        when(
          () => mockApiClient.post<void>(
            ApiPaths.forgotPassword,
            data: any(named: 'data'),
            decode: any(named: 'decode'),
          ),
        ).thenAnswer((_) async {});

        await repository.forgotPassword('test@leadora.vn');

        verify(
          () => mockApiClient.post<void>(
            ApiPaths.forgotPassword,
            data: {'email': 'test@leadora.vn', 'clientType': 'mobile'},
            decode: any(named: 'decode'),
          ),
        ).called(1);
      },
    );

    test(
      'resetPassword calls resetPassword endpoint with token and new password',
      () async {
        when(
          () => mockApiClient.post<void>(
            ApiPaths.resetPassword,
            data: any(named: 'data'),
            decode: any(named: 'decode'),
          ),
        ).thenAnswer((_) async {});

        await repository.resetPassword(
          token: 'my-reset-token',
          password: 'NewPassword123!',
        );

        verify(
          () => mockApiClient.post<void>(
            ApiPaths.resetPassword,
            data: {'token': 'my-reset-token', 'password': 'NewPassword123!'},
            decode: any(named: 'decode'),
          ),
        ).called(1);
      },
    );
  });
}
