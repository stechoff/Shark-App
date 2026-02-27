package com.sharkcontrol.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.sharkcontrol.R;
import com.sharkcontrol.api.AylaApiClient;
import com.sharkcontrol.model.MapData;

public class MapActivity extends AppCompatActivity {

    private AylaApiClient apiClient;
    private MapView mapView;
    private ProgressBar loadingProgress;
    private TextView statusText;
    private String dsn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Reinigungskarte");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        dsn = getIntent().getStringExtra("dsn");
        apiClient = new AylaApiClient(this);

        mapView = findViewById(R.id.map_view);
        loadingProgress = findViewById(R.id.loading_progress);
        statusText = findViewById(R.id.status_text);

        findViewById(R.id.btn_refresh).setOnClickListener(v -> loadMap());
        loadMap();
    }

    private void loadMap() {
        loadingProgress.setVisibility(View.VISIBLE);
        statusText.setText("Karte wird geladen...");
        mapView.setVisibility(View.GONE);

        apiClient.getMapData(dsn, new AylaApiClient.Callback<MapData>() {
            @Override
            public void onSuccess(MapData mapData) {
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    if (mapData == null || !mapData.hasData()) {
                        statusText.setText("Keine Kartendaten verfügbar.\nStarte eine Reinigung, um eine Karte zu erstellen.");
                        statusText.setVisibility(View.VISIBLE);
                    } else {
                        statusText.setVisibility(View.GONE);
                        mapView.setVisibility(View.VISIBLE);
                        mapView.setMapData(mapData);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    statusText.setText("Fehler beim Laden der Karte:\n" + error);
                    Toast.makeText(MapActivity.this, "Kartenfehler: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
