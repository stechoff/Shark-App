package com.sharkcontrol.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.sharkcontrol.R;
import com.sharkcontrol.api.AylaApiClient;
import com.sharkcontrol.model.Schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScheduleActivity extends AppCompatActivity {

    private AylaApiClient apiClient;
    private ScheduleAdapter adapter;
    private List<Schedule> schedules = new ArrayList<>();
    private ProgressBar loadingProgress;
    private TextView emptyText;
    private String dsn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Zeitpläne");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        dsn = getIntent().getStringExtra("dsn");
        apiClient = new AylaApiClient(this);
        loadingProgress = findViewById(R.id.loading_progress);
        emptyText = findViewById(R.id.empty_text);

        RecyclerView recyclerView = findViewById(R.id.schedule_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScheduleAdapter(schedules, this::onScheduleToggled, this::onScheduleDeleted);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btn_add_schedule).setOnClickListener(v -> showAddScheduleDialog());
        loadSchedules();
    }

    private void loadSchedules() {
        loadingProgress.setVisibility(View.VISIBLE);
        apiClient.getSchedules(dsn, new AylaApiClient.Callback<List<Schedule>>() {
            @Override
            public void onSuccess(List<Schedule> result) {
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    schedules.clear();
                    schedules.addAll(result);
                    adapter.notifyDataSetChanged();
                    emptyText.setVisibility(schedules.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    Toast.makeText(ScheduleActivity.this, "Fehler: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showAddScheduleDialog() {
        // Days picker
        String[] dayNames = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        boolean[] selectedDays = new boolean[7];
        final int[] selectedHour = {8};
        final int[] selectedMinute = {0};

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_schedule, null);
        CheckBox[] dayBoxes = {
            dialogView.findViewById(R.id.cb_mon),
            dialogView.findViewById(R.id.cb_tue),
            dialogView.findViewById(R.id.cb_wed),
            dialogView.findViewById(R.id.cb_thu),
            dialogView.findViewById(R.id.cb_fri),
            dialogView.findViewById(R.id.cb_sat),
            dialogView.findViewById(R.id.cb_sun)
        };
        TextView timeText = dialogView.findViewById(R.id.time_text);
        timeText.setText("08:00 Uhr");

        dialogView.findViewById(R.id.btn_pick_time).setOnClickListener(v -> {
            new TimePickerDialog(this, (tp, h, m) -> {
                selectedHour[0] = h;
                selectedMinute[0] = m;
                timeText.setText(String.format(Locale.getDefault(), "%02d:%02d Uhr", h, m));
            }, selectedHour[0], selectedMinute[0], true).show();
        });

        new AlertDialog.Builder(this)
            .setTitle("Zeitplan hinzufügen")
            .setView(dialogView)
            .setPositiveButton("Speichern", (dialog, which) -> {
                List<Integer> days = new ArrayList<>();
                for (int i = 0; i < dayBoxes.length; i++) {
                    if (dayBoxes[i].isChecked()) days.add(i);
                }
                if (days.isEmpty()) {
                    Toast.makeText(this, "Bitte mindestens einen Tag wählen", Toast.LENGTH_SHORT).show();
                    return;
                }
                Schedule schedule = new Schedule();
                schedule.setDays(days);
                schedule.setHour(selectedHour[0]);
                schedule.setMinute(selectedMinute[0]);
                schedule.setEnabled(true);
                saveSchedule(schedule);
            })
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    private void saveSchedule(Schedule schedule) {
        loadingProgress.setVisibility(View.VISIBLE);
        apiClient.addSchedule(dsn, schedule, new AylaApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void v) {
                runOnUiThread(() -> {
                    Toast.makeText(ScheduleActivity.this, "Zeitplan gespeichert", Toast.LENGTH_SHORT).show();
                    loadSchedules();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    Toast.makeText(ScheduleActivity.this, "Fehler: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void onScheduleToggled(Schedule schedule, boolean enabled) {
        schedule.setEnabled(enabled);
        apiClient.updateSchedule(dsn, schedule, new AylaApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void v) {
                runOnUiThread(() -> Toast.makeText(ScheduleActivity.this,
                    enabled ? "Aktiviert" : "Deaktiviert", Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    schedule.setEnabled(!enabled); // revert
                    adapter.notifyDataSetChanged();
                    Toast.makeText(ScheduleActivity.this, "Fehler: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void onScheduleDeleted(Schedule schedule, int position) {
        new AlertDialog.Builder(this)
            .setTitle("Zeitplan löschen")
            .setMessage("Diesen Zeitplan wirklich löschen?")
            .setPositiveButton("Löschen", (d, w) -> {
                apiClient.deleteSchedule(dsn, schedule, new AylaApiClient.Callback<Void>() {
                    @Override
                    public void onSuccess(Void v) {
                        runOnUiThread(() -> {
                            schedules.remove(position);
                            adapter.notifyItemRemoved(position);
                            emptyText.setVisibility(schedules.isEmpty() ? View.VISIBLE : View.GONE);
                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(ScheduleActivity.this, "Fehler: " + error, Toast.LENGTH_SHORT).show());
                    }
                });
            })
            .setNegativeButton("Abbrechen", null).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    // ---- Inner Adapter ----
    interface OnScheduleToggle { void onToggle(Schedule s, boolean enabled); }
    interface OnScheduleDelete { void onDelete(Schedule s, int position); }

    static class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.VH> {
        private final List<Schedule> schedules;
        private final OnScheduleToggle toggleListener;
        private final OnScheduleDelete deleteListener;

        ScheduleAdapter(List<Schedule> s, OnScheduleToggle t, OnScheduleDelete d) {
            schedules = s; toggleListener = t; deleteListener = d;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Schedule s = schedules.get(position);
            holder.timeText.setText(String.format(Locale.getDefault(), "%02d:%02d Uhr", s.getHour(), s.getMinute()));
            holder.daysText.setText(s.getDaysLabel());
            holder.enableSwitch.setChecked(s.isEnabled());
            holder.enableSwitch.setOnCheckedChangeListener((b, checked) -> toggleListener.onToggle(s, checked));
            holder.deleteBtn.setOnClickListener(v -> deleteListener.onDelete(s, holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() { return schedules.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView timeText, daysText;
            Switch enableSwitch;
            ImageButton deleteBtn;
            VH(View v) {
                super(v);
                timeText = v.findViewById(R.id.time_text);
                daysText = v.findViewById(R.id.days_text);
                enableSwitch = v.findViewById(R.id.enable_switch);
                deleteBtn = v.findViewById(R.id.delete_btn);
            }
        }
    }
}
