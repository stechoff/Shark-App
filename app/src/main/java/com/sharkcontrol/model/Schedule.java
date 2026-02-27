package com.sharkcontrol.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Schedule {
    private String id;
    private List<Integer> days; // 0=Mon ... 6=Sun
    private int hour;
    private int minute;
    private boolean enabled;
    private String powerMode = "normal";

    private static final String[] DAY_LABELS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<Integer> getDays() { return days; }
    public void setDays(List<Integer> days) { this.days = days; }

    public int getHour() { return hour; }
    public void setHour(int hour) { this.hour = hour; }

    public int getMinute() { return minute; }
    public void setMinute(int minute) { this.minute = minute; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getPowerMode() { return powerMode; }
    public void setPowerMode(String powerMode) { this.powerMode = powerMode; }

    public String getDaysLabel() {
        if (days == null || days.isEmpty()) return "Kein Tag";
        if (days.size() == 7) return "Täglich";
        if (days.equals(Arrays.asList(0, 1, 2, 3, 4))) return "Werktags";
        if (days.equals(Arrays.asList(5, 6))) return "Wochenende";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < days.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(DAY_LABELS[days.get(i)]);
        }
        return sb.toString();
    }

    public String getTimeLabel() {
        return String.format(Locale.getDefault(), "%02d:%02d Uhr", hour, minute);
    }
}
