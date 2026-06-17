package com.example.daymark;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class LoginActivity extends Activity {
    private TextInputEditText usernameEdit;
    private TextInputEditText passwordEdit;
    private CheckBox rememberCheck;
    private DayMarkDbHelper dbHelper;
    private SharedPreferences preferences;
    private SharedPreferences securePreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbHelper = new DayMarkDbHelper(this);
        preferences = getSharedPreferences("login", MODE_PRIVATE);
        securePreferences = createSecurePreferences();

        long sessionUserId = DayMarkDbHelper.NO_USER;
        String sessionUsername = null;
        if (securePreferences != null) {
            try {
                sessionUserId = securePreferences.getLong("session_user_id", DayMarkDbHelper.NO_USER);
                sessionUsername = securePreferences.getString("session_username", null);
            } catch (Exception e) {
                Logger.securityError("Failed to read encrypted session", e);
            }
        }
        if (sessionUserId != DayMarkDbHelper.NO_USER) {
            goToMain(sessionUserId, sessionUsername);
            return;
        }

        setContentView(R.layout.activity_login);
        if (securePreferences == null) {
            securePreferences = createSecurePreferences();
        }

        usernameEdit = findViewById(R.id.usernameEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        rememberCheck = findViewById(R.id.rememberCheck);
        MaterialButton loginButton = findViewById(R.id.loginButton);
        MaterialButton registerButton = findViewById(R.id.registerButton);

        loadRememberedAccount();
        loginButton.setOnClickListener(v -> doLogin());
        registerButton.setOnClickListener(v -> doRegister());
    }

    private void loadRememberedAccount() {
        if (preferences.contains("password")) {
            preferences.edit().remove("password").apply();
        }
        boolean remembered = preferences.getBoolean("remember", false);
        rememberCheck.setChecked(remembered);
        if (remembered) {
            usernameEdit.setText(preferences.getString("username", ""));
            if (securePreferences != null) {
                passwordEdit.setText(securePreferences.getString("password", ""));
            }
        } else {
            usernameEdit.setText("demo");
            passwordEdit.setText("123456");
        }
    }

    private void doLogin() {
        String username = usernameEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString();
        if (!validate(username, password)) {
            return;
        }
        AppExecutors.io().execute(() -> {
            long userId = dbHelper.login(username, password);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                if (userId != DayMarkDbHelper.NO_USER) {
                    saveRememberState(username, password);
                    if (securePreferences != null) {
                        try {
                            securePreferences.edit()
                                    .putLong("session_user_id", userId)
                                    .putString("session_username", username)
                                    .apply();
                            Logger.d("Session saved securely for user: " + username);
                        } catch (Exception e) {
                            Logger.securityError("Failed to save encrypted session", e);
                        }
                    }
                    preferences.edit()
                            .remove("session_user_id")
                            .remove("session_username")
                            .apply();
                    goToMain(userId, username);
                } else {
                    Toast.makeText(this, "账号或密码不正确", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void doRegister() {
        String username = usernameEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString();
        if (!validate(username, password)) {
            return;
        }
        AppExecutors.io().execute(() -> {
            long result = dbHelper.register(username, password);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                if (result != DayMarkDbHelper.NO_USER) {
                    Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "用户名已存在", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private boolean validate(String username, String password) {
        if (TextUtils.isEmpty(username)) {
            usernameEdit.setError("请输入用户名");
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEdit.setError("请输入密码");
            return false;
        }
        return true;
    }

    private void goToMain(long userId, String username) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("user_id", userId);
        intent.putExtra("username", username);
        startActivity(intent);
        finish();
    }

    private void saveRememberState(String username, String password) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("remember", rememberCheck.isChecked());
        if (rememberCheck.isChecked()) {
            editor.putString("username", username);
        } else {
            editor.remove("username");
        }
        editor.remove("password");
        editor.apply();

        if (securePreferences != null) {
            SharedPreferences.Editor secureEditor = securePreferences.edit();
            if (rememberCheck.isChecked()) {
                secureEditor.putString("password", password);
            } else {
                secureEditor.remove("password");
            }
            secureEditor.apply();
        }
    }

    private SharedPreferences createSecurePreferences() {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    "secure_login",
                    masterKeyAlias,
                    this,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            Logger.securityError("Failed to create encrypted preferences", e);
            return null;
        }
    }
}
