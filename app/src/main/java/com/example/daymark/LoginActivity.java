package com.example.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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
    private static final int REQUEST_IMPORT_BACKUP = 200;

    private TextInputEditText usernameEdit;
    private TextInputEditText passwordEdit;
    private CheckBox rememberCheck;
    private DayMarkDbHelper dbHelper;
    private SharedPreferences preferences;
    private SharedPreferences securePreferences;
    private boolean forceBlankLoginForm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbHelper = new DayMarkDbHelper(this);
        preferences = getSharedPreferences("login", MODE_PRIVATE);
        securePreferences = createSecurePreferences();
        forceBlankLoginForm = getIntent().getBooleanExtra("clear_login_fields", false);
        setContentView(R.layout.activity_login);
        if (securePreferences == null) {
            securePreferences = createSecurePreferences();
        }

        usernameEdit = findViewById(R.id.usernameEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        rememberCheck = findViewById(R.id.rememberCheck);
        MaterialButton loginButton = findViewById(R.id.loginButton);
        MaterialButton registerButton = findViewById(R.id.registerButton);
        MaterialButton importBackupButton = findViewById(R.id.importBackupButton);

        loadRememberedAccount();
        loginButton.setOnClickListener(v -> doLogin());
        registerButton.setOnClickListener(v -> doRegister());
        importBackupButton.setOnClickListener(v -> showImportBackupDialog());
        restoreSessionIfNeeded();
    }

    private void restoreSessionIfNeeded() {
        boolean autoLoginEnabled = preferences.getBoolean("remember", false);
        if (securePreferences == null) {
            return;
        }

        long sessionUserId = DayMarkDbHelper.NO_USER;
        String sessionUsername = null;
        try {
            sessionUserId = securePreferences.getLong("session_user_id", DayMarkDbHelper.NO_USER);
            sessionUsername = securePreferences.getString("session_username", null);
        } catch (Exception e) {
            Logger.securityError("Failed to read encrypted session", e);
        }

        if (sessionUserId == DayMarkDbHelper.NO_USER) {
            return;
        }
        if (!autoLoginEnabled) {
            clearLocalAuthState(false);
            return;
        }

        final long finalSessionUserId = sessionUserId;
        final String finalSessionUsername = sessionUsername;
        AppExecutors.io().execute(() -> {
            boolean exists = dbHelper.userExists(finalSessionUserId);
            AppExecutors.main().execute(() -> {
                if (isFinishing()) {
                    return;
                }
                if (exists) {
                    goToMain(finalSessionUserId, finalSessionUsername);
                } else {
                    clearLocalAuthState(true);
                    forceBlankLoginForm = true;
                    loadRememberedAccount();
                }
            });
        });
    }

    private void loadRememberedAccount() {
        if (forceBlankLoginForm) {
            usernameEdit.setText("");
            passwordEdit.setText("");
            rememberCheck.setChecked(false);
            return;
        }
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
            usernameEdit.setText(R.string.login_demo_username);
            passwordEdit.setText(R.string.login_demo_password);
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
                    if (rememberCheck.isChecked() && securePreferences != null) {
                        try {
                            securePreferences.edit()
                                    .putLong("session_user_id", userId)
                                    .putString("session_username", username)
                                    .apply();
                            Logger.d(getString(R.string.login_session_save_log, username));
                        } catch (Exception e) {
                            Logger.securityError("Failed to save encrypted session", e);
                        }
                    } else {
                        clearLocalAuthState(false);
                    }
                    preferences.edit()
                            .remove("session_user_id")
                            .remove("session_username")
                            .apply();
                    goToMain(userId, username);
                } else {
                    Toast.makeText(this, R.string.login_error_invalid_credentials,
                            Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, R.string.login_register_success, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.login_register_error_exists, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private boolean validate(String username, String password) {
        if (TextUtils.isEmpty(username)) {
            usernameEdit.setError(getString(R.string.login_error_empty_username));
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEdit.setError(getString(R.string.login_error_empty_password));
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

    private void showImportBackupDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.login_import_backup_title)
                .setMessage(R.string.login_import_backup_message)
                .setPositiveButton(R.string.login_import_backup_action, (dialog, which) -> selectBackupFile())
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void selectBackupFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent,
                    getString(R.string.login_file_chooser_title)), REQUEST_IMPORT_BACKUP);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.login_file_manager_missing, Toast.LENGTH_SHORT).show();
        }
    }

    private void importBackup(Uri uri) {
        AppExecutors.io().execute(() -> {
            try {
                java.io.File tempFile = new java.io.File(getCacheDir(), "login_import_temp.json");
                try (java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                     java.io.OutputStream outputStream = new java.io.FileOutputStream(tempFile)) {
                    if (inputStream == null) {
                        throw new java.io.IOException("无法读取文件");
                    }
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                boolean success = dbHelper.restoreFromJson(tempFile.getAbsolutePath());
                tempFile.delete();
                AppExecutors.main().execute(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    Toast.makeText(this,
                            success ? R.string.login_import_success : R.string.login_import_failed,
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Logger.e("Login import backup failed", e);
                AppExecutors.main().execute(() ->
                        Toast.makeText(this, R.string.login_import_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_BACKUP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importBackup(data.getData());
        }
    }

    private void clearLocalAuthState(boolean clearRememberedAccount) {
        SharedPreferences.Editor editor = preferences.edit()
                .remove("session_user_id")
                .remove("session_username");
        if (clearRememberedAccount) {
            editor.remove("remember")
                    .remove("username")
                    .remove("password");
        }
        editor.apply();

        if (securePreferences != null) {
            SharedPreferences.Editor secureEditor = securePreferences.edit()
                    .remove("session_user_id")
                    .remove("session_username");
            if (clearRememberedAccount) {
                secureEditor.remove("password");
            }
            secureEditor.apply();
        }
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
