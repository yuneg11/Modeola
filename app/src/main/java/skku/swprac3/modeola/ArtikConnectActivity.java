package skku.swprac3.modeola;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenResponse;

public class ArtikConnectActivity extends Activity {
    static final String TAG = "ArtikConnectActivity";
    private static final String ARTIKCLOUD_AUTHORIZE_URI = "https://accounts.artik.cloud/signin";
    private static final String ARTIKCLOUD_TOKEN_URI = "https://accounts.artik.cloud/token";
    static final String INTENT_ARTIKCLOUD_AUTHORIZATION_RESPONSE = "skku.swprac3.modeola.ARTIKCLOUD_AUTHORIZATION_RESPONSE";
    boolean connectedToArtikMessageShow = false;

    AuthorizationService mAuthorizationService;
    AuthStateDAL mAuthStateDAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artik_connect);

        mAuthorizationService = new AuthorizationService(this);
        mAuthStateDAL = new AuthStateDAL(this);
        SharedPreferences sharedPreferences = getSharedPreferences(AuthStateDAL.AUTH_PREFERENCES_NAME, Context.MODE_PRIVATE);
        String stateStr = sharedPreferences.getString(AuthStateDAL.AUTH_STATE, null);

        if (!TextUtils.isEmpty(stateStr)) {
            Toast.makeText(this, "Already connected to ARTIK", Toast.LENGTH_SHORT).show();
            finish();
        }

        Button connectArtikButton = findViewById(R.id.connectArtikButton);
        connectArtikButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    doAuth();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void doAuth() {
        AuthorizationRequest authorizationRequest = createAuthorizationRequest();
        PendingIntent authorizationIntent = PendingIntent.getActivity(
                this,
                authorizationRequest.hashCode(),
                new Intent(INTENT_ARTIKCLOUD_AUTHORIZATION_RESPONSE, null, this, ArtikConnectActivity.class),
                0);

        /* request sample with custom tabs */
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();

        mAuthorizationService.performAuthorizationRequest(authorizationRequest, authorizationIntent, customTabsIntent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        checkIntent(intent);
    }

    private void checkIntent(@Nullable Intent intent) {
        Log.d(TAG, "Entering checkIntent ...");
        if (intent != null) {
            String action = intent.getAction();
            if(action != null) {
                switch (action) {
                    case INTENT_ARTIKCLOUD_AUTHORIZATION_RESPONSE:
                        Log.d(TAG, "checkIntent action = " + action);
                        handleAuthorizationResponse(intent);
                        break;
                    default:
                        Log.w(TAG, "checkIntent action = " + action);
                }
            } else {
                Log.w(TAG, "checkIntent intent's action is null!");
            }
        } else {
            Log.w(TAG, "checkIntent intent is null!");
        }
    }

    private void handleAuthorizationResponse(@NonNull Intent intent) {
        AuthorizationResponse response = AuthorizationResponse.fromIntent(intent);
        AuthorizationException error = AuthorizationException.fromIntent(intent);
        Log.i(TAG, "Entering handleAuthorizationResponse with response from Intent = " + response.jsonSerialize().toString());

        if (response != null) {
            if (response.authorizationCode != null ) { // Authorization Code method: succeeded to get code
                final AuthState authState = new AuthState(response, error);
                Log.i(TAG, "Received code = " + response.authorizationCode + "\n make another call to get token ...");

                // File 2nd call to get the token
                mAuthorizationService.performTokenRequest(response.createTokenExchangeRequest(), new AuthorizationService.TokenResponseCallback() {
                    @Override
                    public void onTokenRequestCompleted(@Nullable TokenResponse tokenResponse, @Nullable AuthorizationException exception) {
                        if (tokenResponse != null) {
                            authState.update(tokenResponse, exception);
                            mAuthStateDAL.writeAuthState(authState);
                            String text = String.format("Received token response [%s]", tokenResponse.jsonSerializeString());
                            Log.i(TAG, text);
                            onResume();
                        } else {
                            Log.w(TAG, "Token Exchange failed", exception);
                            Toast.makeText(getApplicationContext(), "Token Exchange failed", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } else { // come here w/o authorization code. For example, signup finish and user clicks "Back to login"
                Log.d(TAG, "additionalParameter = " + response.additionalParameters.toString());

                if (response.additionalParameters.get("status").equalsIgnoreCase("login_request")) {
                    // ARTIK Cloud instructs the app to display a sign-in form
                    //doAuth();
                } else {
                    Log.d(TAG, response.jsonSerialize().toString());
                }
            }

        } else {
            Log.w(TAG, "Authorization Response is null ");
            Log.d(TAG, "Authorization Exception = " + error);
        }
    }

    static AuthorizationRequest createAuthorizationRequest() {

        AuthorizationServiceConfiguration serviceConfiguration = new AuthorizationServiceConfiguration(
                Uri.parse(ARTIKCLOUD_AUTHORIZE_URI),
                Uri.parse(ARTIKCLOUD_TOKEN_URI),
                null
        );

        AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(
                serviceConfiguration,
                ArtikConfig.CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse(ArtikConfig.REDIRECT_URI)
        );

        return builder.build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAuthorizationService = new AuthorizationService(this);
        mAuthStateDAL = new AuthStateDAL(this);
        SharedPreferences sharedPreferences = getSharedPreferences(AuthStateDAL.AUTH_PREFERENCES_NAME, Context.MODE_PRIVATE);
        String stateStr = sharedPreferences.getString(AuthStateDAL.AUTH_STATE, null);

        if (!TextUtils.isEmpty(stateStr)) {
            if(!connectedToArtikMessageShow) {
                Toast.makeText(this, "Connected to ARTIK", Toast.LENGTH_SHORT).show();
                connectedToArtikMessageShow = true;
                Intent serviceStart = new Intent(getApplicationContext(), ArtikNotificationService.class);
                startService(serviceStart);
            }
            finish();
        }
    }
}