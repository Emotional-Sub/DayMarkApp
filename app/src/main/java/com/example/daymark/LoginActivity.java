package com.example.daymark;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import android.app.Activity;

public class LoginActivity extends Activity {
    private EditText usernameEdit;
    private EditText passwordEdit;
    private CheckBox rememberCheck;
    private DayMarkDbHelper dbHelper;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        dbHelper = new DayMarkDbHelper(this);
        preferences = getSharedPreferences("login", MODE_PRIVATE);

        usernameEdit = findViewById(R.id.usernameEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        rememberCheck = findViewById(R.id.rememberCheck);
        Button loginButton = findViewById(R.id.loginButton);
        Button registerButton = findViewById(R.id.registerButton);

        loadRememberedAccount();
        loginButton.setOnClickListener(v -> doLogin());
        registerButton.setOnClickListener(v -> doRegister());
    }

    private void loadRememberedAccount() {
        // Older versions also persisted the plaintext password here; drop it if present so a
        // previously-stored password never lingers in SharedPreferences.
        if (preferences.contains("password")) {
            preferences.edit().remove("password").apply();
        }
        boolean remembered = preferences.getBoolean("remember", false);
        rememberCheck.setChecked(remembered);
        if (remembered) {
            usernameEdit.setText(preferences.getString("username", ""));
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
        long userId = dbHelper.login(username, password);
        if (userId != DayMarkDbHelper.NO_USER) {
            saveRememberState(username);
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("user_id", userId);
            intent.putExtra("username", username);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "账号或密码不正确", Toast.LENGTH_SHORT).show();
        }
    }

    private void doRegister() {
        String username = usernameEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString();
        if (!validate(username, password)) {
            return;
        }
        if (dbHelper.register(username, password) != DayMarkDbHelper.NO_USER) {
            Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "用户名已存在", Toast.LENGTH_SHORT).show();
        }
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

    private void saveRememberState(String username) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("remember", rememberCheck.isChecked());
        if (rememberCheck.isChecked()) {
            // Only the username is remembered; the password is never persisted.
            editor.putString("username", username);
        } else {
            editor.remove("username");
        }
        // Clear any plaintext password left by an older version regardless of the checkbox.
        editor.remove("password");
        editor.apply();
    }
}
