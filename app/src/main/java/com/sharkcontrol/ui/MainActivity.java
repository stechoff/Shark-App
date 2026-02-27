package com.sharkcontrol.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.sharkcontrol.R;
import com.sharkcontrol.api.AylaApiClient;
import com.sharkcontrol.model.SharkDevice;
import com.sharkcontrol.model.RobotStatus;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AylaApiClient apiClient;
    private SharkDevice currentDevice;

    // UI Elements
    private TextView deviceNameText, statusText, batteryText, powerModeText;
    private TextView cleaningTimeText, areaCleanedText;
    private ProgressBar batteryProgress, loadingProgress;
    private Button btnStart, btnStop, btnDock, btnPause;
    private CardView statusCard;
    private Spinner deviceSpinner;
    private LinearLayout controlsLayout;
    private ImageView robotIcon;
    private TextView connectionStatus;

    private Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;
    private static final int REFRESH_INTERVAL_MS = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Shark Control");
        }

        apiClient = new AylaApiClient(this);
        initViews();
        loadDevices();
    }

    private void initViews() {
        deviceNameText = findViewById(R.id.device_name_text);
        statusText = findViewById(R.id.status_text);
        batteryText = findViewById(R.id.battery_text);
        powerModeText = findViewById(R.id.power_mode_text);
        cleaningTimeText = findViewById(R.id.cleaning_time_text);
        areaCleanedText = findViewById(R.id.area_cleaned_text);
        batteryProgress = findViewById(R.id.battery_progress);
        loadingProgress = findViewById(R.id.loading_progress);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnDock = findViewById(R.id.btn_dock);
        btnPause = findViewById(R.id.btn_pause);
        statusCard = findViewById(R.id.status_card);
        deviceSpinner = findViewById(R.id.device_spinner);
        controlsLayout = findViewById(R.id.controls_layout);
        robotIcon = findViewById(R.id.robot_icon);
        connectionStatus = findViewById(R.id.connection_status);

        btnStart.setOnClickListener(v -> sendCommand("start"));
        btnStop.setOnClickListener(v -> sendCommand("stop"));
        btnDock.setOnClickListener(v -> sendCommand("dock"));
        btnPause.setOnClickListener(v -> sendCommand("pause"));

        Button btnMap = findViewById(R.id.btn_map);
        Button btnSchedule = findViewById(R.id.btn_schedule);
        if (btnMap != null) btnMap.setOnClickListener(v -> openMap());
        if (btnSchedule != null) btnSchedule.setOnClickListener(v -> openSchedule());

        controlsLayout.setVisibility(View.GONE);
    }

    private void loadDevices() {
        setLoading(true);
        statusText.setText("Geräte werden geladen...");

        apiClient.getDevices(new AylaApiClient.Callback<List<SharkDevice>>() {
            @Override
            public void onSuccess(List<SharkDevice> devices) {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (devices.isEmpty()) {
                        statusText.setText("Keine Geräte gefunden.\nBitte zuerst in der SharkClean App einrichten.");
                        return;
                    }

                    String[] deviceNames = new String[devices.size()];
                    for (int i = 0; i < devices.size(); i++) {
                        deviceNames[i] = devices.get(i).getProductName();
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this,
                            android.R.layout.simple_spinner_item, deviceNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    deviceSpinner.setAdapter(adapter);

                    deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            currentDevice = devices.get(position);
                            deviceNameText.setText(currentDevice.getProductName());
                            controlsLayout.setVisibility(View.VISIBLE);
                            startStatusRefresh();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });

                    currentDevice = devices.get(0);
                    deviceNameText.setText(currentDevice.getProductName());
                    controlsLayout.setVisibility(View.VISIBLE);
                    startStatusRefresh();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    statusText.setText("Fehler beim Laden: " + error);
                    if (error.contains("401") || error.contains("auth")) {
                        logout();
                    }
                });
            }
        });
    }

    private void refreshStatus() {
        if (currentDevice == null) return;

        apiClient.getDeviceStatus(currentDevice.getDsn(), new AylaApiClient.Callback<RobotStatus>() {
            @Override
            public void onSuccess(RobotStatus status) {
                runOnUiThread(() -> updateUI(status));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    connectionStatus.setText("⚠ Offline");
                    connectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark));
                });
            }
        });
    }

    private void updateUI(RobotStatus status) {
        connectionStatus.setText("● Online");
        connectionStatus.setTextColor(getColor(android.R.color.holo_green_dark));

        // Battery
        int battery = status.getBatteryCapacity();
        batteryProgress.setProgress(battery);
        batteryText.setText("🔋 " + battery + "%");

        // Status
        String operatingMode = status.getOperatingMode();
        String statusEmoji = getStatusEmoji(operatingMode);
        statusText.setText(statusEmoji + " " + getStatusLabel(operatingMode));

        // Power Mode
        String powerMode = status.getPowerMode();
        powerModeText.setText("⚡ " + getPowerModeLabel(powerMode));

        // Cleaning stats
        int cleanTime = status.getCleaningTime();
        if (cleanTime > 0) {
            cleaningTimeText.setText("⏱ " + formatTime(cleanTime));
            cleaningTimeText.setVisibility(View.VISIBLE);
        } else {
            cleaningTimeText.setVisibility(View.GONE);
        }

        // Update button states
        updateButtonStates(operatingMode);
    }

    private void updateButtonStates(String mode) {
        boolean isRunning = "start".equals(mode);
        boolean isPaused = "pause".equals(mode);
        boolean isDocked = "stop".equals(mode) || mode == null;

        btnStart.setEnabled(!isRunning);
        btnPause.setEnabled(isRunning);
        btnStop.setEnabled(isRunning || isPaused);
        btnDock.setEnabled(!isDocked);
    }

    private void sendCommand(String command) {
        if (currentDevice == null) return;

        setLoading(true);

        apiClient.sendCommand(currentDevice.getDsn(), command, new AylaApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(MainActivity.this, "Befehl gesendet: " + getCommandLabel(command), Toast.LENGTH_SHORT).show();
                    // Refresh status after short delay
                    refreshHandler.postDelayed(() -> refreshStatus(), 2000);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(MainActivity.this, "Fehler: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void startStatusRefresh() {
        stopStatusRefresh();
        refreshStatus();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                refreshStatus();
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    private void stopStatusRefresh() {
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentDevice != null) startStatusRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopStatusRefresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Abmelden").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, 2, 1, "Aktualisieren").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            new AlertDialog.Builder(this)
                    .setTitle("Abmelden")
                    .setMessage("Möchten Sie sich wirklich abmelden?")
                    .setPositiveButton("Ja", (d, w) -> logout())
                    .setNegativeButton("Nein", null)
                    .show();
        } else if (item.getItemId() == 2) {
            refreshStatus();
        }
        return super.onOptionsItemSelected(item);
    }

    private void openMap() {
        if (currentDevice == null) return;
        android.content.Intent intent = new android.content.Intent(this, MapActivity.class);
        intent.putExtra("dsn", currentDevice.getDsn());
        startActivity(intent);
    }

    private void openSchedule() {
        if (currentDevice == null) return;
        android.content.Intent intent = new android.content.Intent(this, ScheduleActivity.class);
        intent.putExtra("dsn", currentDevice.getDsn());
        startActivity(intent);
    }

    private void logout() {
        stopStatusRefresh();
        getSharedPreferences("SharkControl", MODE_PRIVATE).edit().clear().apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void setLoading(boolean loading) {
        loadingProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String getStatusEmoji(String mode) {
        if (mode == null) return "🏠";
        switch (mode.toLowerCase()) {
            case "start": return "🔄";
            case "stop": return "🏠";
            case "pause": return "⏸";
            case "return": return "↩";
            default: return "💤";
        }
    }

    private String getStatusLabel(String mode) {
        if (mode == null) return "Geparkt / Unbekannt";
        switch (mode.toLowerCase()) {
            case "start": return "Saugt";
            case "stop": return "Gestoppt / Docking";
            case "pause": return "Pausiert";
            case "return": return "Kehrt zur Basis zurück";
            default: return mode;
        }
    }

    private String getPowerModeLabel(String mode) {
        if (mode == null) return "Normal";
        switch (mode.toLowerCase()) {
            case "eco": return "Eco (Leise)";
            case "normal": return "Normal";
            case "max": return "Max (Stark)";
            case "max2": return "Max+ (Extra Stark)";
            default: return mode;
        }
    }

    private String getCommandLabel(String command) {
        switch (command) {
            case "start": return "Starten";
            case "stop": return "Stoppen";
            case "dock": return "Zur Basis";
            case "pause": return "Pause";
            default: return command;
        }
    }

    private String formatTime(int minutes) {
        if (minutes < 60) return minutes + " Min";
        return (minutes / 60) + "h " + (minutes % 60) + "min";
    }
}
