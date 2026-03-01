package com.sharkcontrol.api;

import android.content.Context;
import android.content.SharedPreferences;
import com.sharkcontrol.model.MapData;
import com.sharkcontrol.model.RobotStatus;
import com.sharkcontrol.model.Schedule;
import com.sharkcontrol.model.SharkDevice;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AylaApiClient {

    private static final String AUTH_BASE    = "https://logineu.sharkninja.com";
    private static final String API_BASE     = "https://ads-field-eu.aylanetworks.com";
    private static final String CLIENT_ID    = "rKDx9O18dBrY3eoJMTkRiBZHDvd9Mx1I";
    private static final String REDIRECT_URI = "com.sharkninja.shark://com.sharkninja.shark/callback";
    private static final String SCOPE        = "openid profile email offline_access read:users read:current_user read:user_idp_tokens";

    private static final String PREFS_NAME        = "SharkControl";
    private static final String KEY_ACCESS_TOKEN  = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private final Context context;
    private final ExecutorService executor;
    private String accessToken;

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public AylaApiClient(Context context) {
        this.context = context;
        this.executor = Executors.newCachedThreadPool();
        this.accessToken = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACCESS_TOKEN, null);
    }

    public void signIn(String email, String password, Callback<String> callback) {
        executor.execute(() -> {
            try {
                String body = "grant_type=password"
                        + "&client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8")
                        + "&username=" + URLEncoder.encode(email, "UTF-8")
                        + "&password=" + URLEncoder.encode(password, "UTF-8")
                        + "&scope=" + URLEncoder.encode(SCOPE, "UTF-8");

                URL url = new URL(AUTH_BASE + "/oauth/token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

                int code = conn.getResponseCode();
                String response = readStream(code < 400 ? conn.getInputStream() : conn.getErrorStream());

                if (code == 200) {
                    JSONObject json = new JSONObject(response);
                    String token = json.getString("access_token");
                    this.accessToken = token;
                    SharedPreferences.Editor ed = context
                            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                    ed.putString(KEY_ACCESS_TOKEN, token);
                    ed.putString(KEY_REFRESH_TOKEN, json.optString("refresh_token", ""));
                    ed.apply();
                    callback.onSuccess(token);
                } else {
                    try {
                        JSONObject err = new JSONObject(response);
                        callback.onError(err.optString("error_description",
                                         err.optString("error", "HTTP " + code)));
                    } catch (Exception e) {
                        callback.onError("HTTP " + code + ": " + response);
                    }
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public void getDevices(Callback<List<SharkDevice>> callback) {
        executor.execute(() -> {
            try {
                String response = get(API_BASE + "/v1/devices");
                JSONArray devicesJson;
                try {
                    devicesJson = new JSONArray(response);
                } catch (Exception e) {
                    devicesJson = new JSONObject(response).optJSONArray("devices");
                    if (devicesJson == null) devicesJson = new JSONArray();
                }

                List<SharkDevice> devices = new ArrayList<>();
                for (int i = 0; i < devicesJson.length(); i++) {
                    JSONObject d = devicesJson.getJSONObject(i);
                    if (d.has("device")) d = d.getJSONObject("device");
                    SharkDevice device = new SharkDevice();
                    device.setDsn(d.optString("dsn", d.optString("serial_number")));
                    device.setProductName(d.optString("product_name", d.optString("name", "Shark Robot")));
                    device.setModel(d.optString("oem_model", d.optString("model")));
                    device.setConnected("Online".equalsIgnoreCase(d.optString("connection_status")) || d.optBoolean("connected"));
                    devices.add(device);
                }
                callback.onSuccess(devices);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public void getDeviceStatus(String dsn, Callback<RobotStatus> callback) {
        executor.execute(() -> {
            try {
                String response = get(API_BASE + "/v1/devices/" + dsn + "/properties");
                JSONArray props;
                try {
                    props = new JSONArray(response);
                } catch (Exception e) {
                    props = new JSONObject(response).optJSONArray("properties");
                    if (props == null) props = new JSONArray();
                }

                RobotStatus status = new RobotStatus();
                for (int i = 0; i < props.length(); i++) {
                    JSONObject p = props.getJSONObject(i);
                    if (p.has("property")) p = p.getJSONObject("property");
                    String name = p.optString("name");
                    Object value = p.opt("value");
                    switch (name) {
                        case "GET_Operating_Mode":              status.setOperatingMode(String.valueOf(value)); break;
                        case "GET_Battery_Capacity":            status.setBatteryCapacity(parseIntSafe(value)); break;
                        case "GET_Power_Mode":                  status.setPowerMode(String.valueOf(value));     break;
                        case "GET_Cleaning_Statistics_Minutes": status.setCleaningTime(parseIntSafe(value));    break;
                        case "GET_Error_Code":                  status.setErrorCode(parseIntSafe(value));       break;
                        case "GET_Charging_Status":             status.setCharging(parseIntSafe(value) > 0);    break;
                    }
                }
                callback.onSuccess(status);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public void sendCommand(String dsn, String command, Callback<Void> callback) {
        executor.execute(() -> {
            try {
                String val;
                switch (command) {
                    case "start": val = "start";  break;
                    case "stop":  val = "stop";   break;
                    case "pause": val = "pause";  break;
                    case "dock":  val = "return"; break;
                    default: callback.onError("Unbekannter Befehl"); return;
                }
                JSONObject body = new JSONObject();
                body.put("datapoint", new JSONObject().put("value", val));
                postJson(API_BASE + "/v1/devices/" + dsn + "/properties/SET_Operating_Mode/datapoints", body);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public void setPowerMode(String dsn, String mode, Callback<Void> callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("datapoint", new JSONObject().put("value", mode));
                postJson(API_BASE + "/v1/devices/" + dsn + "/properties/SET_Power_Mode/datapoints", body);
                callback.onSuccess(null);
            } catch (Exception e) { callback.onError(e.getMessage()); }
        });
    }

    public void getMapData(String dsn, Callback<MapData> callback) {
        executor.execute(() -> {
            try {
                String response = get(API_BASE + "/v1/devices/" + dsn + "/properties"
                        + "?names[]=GET_Robot_Map_Data&names[]=GET_Robot_Position&names[]=GET_Charging_Station_Position");
                JSONArray props = new JSONArray(response);
                String mapRaw = null, robotPos = null, chargePos = null;
                for (int i = 0; i < props.length(); i++) {
                    JSONObject p = props.getJSONObject(i);
                    if (p.has("property")) p = p.getJSONObject("property");
                    switch (p.optString("name")) {
                        case "GET_Robot_Map_Data":            mapRaw   = p.optString("value"); break;
                        case "GET_Robot_Position":            robotPos = p.optString("value"); break;
                        case "GET_Charging_Station_Position": chargePos = p.optString("value"); break;
                    }
                }
                callback.onSuccess(parseMapData(mapRaw, robotPos, chargePos));
            } catch (Exception e) { callback.onError(e.getMessage()); }
        });
    }

    private MapData parseMapData(String mapRaw, String robotPos, String chargePos) throws Exception {
        MapData m = new MapData();
        if (mapRaw == null || mapRaw.isEmpty() || "null".equals(mapRaw)) return m;
        JSONObject mj = new JSONObject(mapRaw);
        int w = mj.optInt("width", 64), h = mj.optInt("height", 64);
        String b64 = mj.optString("grid", "");
        int[][] grid = new int[h][w];
        if (!b64.isEmpty()) {
            byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            int cleaned = 0;
            for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) {
                if (r*w+c >= bytes.length) break;
                int v = bytes[r*w+c] & 0xFF;
                grid[r][c] = v==0 ? MapData.CELL_UNKNOWN : v==1 ? MapData.CELL_FLOOR : MapData.CELL_WALL;
                if (v==1) cleaned++;
            }
            m.setCleanedCells(cleaned);
        }
        m.setGrid(grid);
        if (robotPos != null && !robotPos.isEmpty() && !"null".equals(robotPos)) {
            String[] p = robotPos.split(",");
            if (p.length >= 2) { m.setRobotX(Integer.parseInt(p[0].trim())); m.setRobotY(Integer.parseInt(p[1].trim())); }
            if (p.length >= 3) m.setRobotAngle(Float.parseFloat(p[2].trim()));
        }
        if (chargePos != null && !chargePos.isEmpty() && !"null".equals(chargePos)) {
            String[] p = chargePos.split(",");
            if (p.length >= 2) { m.setChargeX(Integer.parseInt(p[0].trim())); m.setChargeY(Integer.parseInt(p[1].trim())); }
        }
        return m;
    }

    public void getSchedules(String dsn, Callback<List<Schedule>> callback) {
        executor.execute(() -> {
            try {
                String response = get(API_BASE + "/v1/devices/" + dsn + "/properties/GET_Schedule_Data/datapoints?limit=1");
                List<Schedule> schedules = new ArrayList<>();
                JSONArray dps = new JSONArray(response);
                if (dps.length() > 0) {
                    JSONObject dp = dps.getJSONObject(0);
                    if (dp.has("datapoint")) dp = dp.getJSONObject("datapoint");
                    JSONArray arr = new JSONArray(dp.optString("value", "[]"));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject sj = arr.getJSONObject(i);
                        Schedule s = new Schedule();
                        s.setId(sj.optString("id", String.valueOf(i)));
                        s.setHour(sj.optInt("hour", 8));
                        s.setMinute(sj.optInt("minute", 0));
                        s.setEnabled(sj.optBoolean("enabled", true));
                        s.setPowerMode(sj.optString("power_mode", "normal"));
                        JSONArray daysJson = sj.optJSONArray("days");
                        List<Integer> days = new ArrayList<>();
                        if (daysJson != null) for (int d = 0; d < daysJson.length(); d++) days.add(daysJson.getInt(d));
                        s.setDays(days);
                        schedules.add(s);
                    }
                }
                callback.onSuccess(schedules);
            } catch (Exception e) { callback.onError(e.getMessage()); }
        });
    }

    public void addSchedule(String dsn, Schedule s, Callback<Void> cb)    { modifySchedules(dsn, s, false, cb); }
    public void updateSchedule(String dsn, Schedule s, Callback<Void> cb) { modifySchedules(dsn, s, true,  cb); }
    public void deleteSchedule(String dsn, Schedule s, Callback<Void> cb) {
        getSchedules(dsn, new Callback<List<Schedule>>() {
            public void onSuccess(List<Schedule> list) { list.removeIf(x -> x.getId().equals(s.getId())); pushSchedules(dsn, list, cb); }
            public void onError(String e) { cb.onError(e); }
        });
    }

    private void modifySchedules(String dsn, Schedule s, boolean update, Callback<Void> cb) {
        getSchedules(dsn, new Callback<List<Schedule>>() {
            public void onSuccess(List<Schedule> list) {
                if (update) { for (int i=0;i<list.size();i++) if (list.get(i).getId().equals(s.getId())) { list.set(i,s); break; } }
                else { s.setId(String.valueOf(System.currentTimeMillis())); list.add(s); }
                pushSchedules(dsn, list, cb);
            }
            public void onError(String e) { cb.onError(e); }
        });
    }

    private void pushSchedules(String dsn, List<Schedule> schedules, Callback<Void> callback) {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                for (Schedule s : schedules) {
                    JSONObject sj = new JSONObject();
                    sj.put("id", s.getId()); sj.put("hour", s.getHour()); sj.put("minute", s.getMinute());
                    sj.put("enabled", s.isEnabled()); sj.put("power_mode", s.getPowerMode());
                    JSONArray da = new JSONArray(); for (int d : s.getDays()) da.put(d); sj.put("days", da);
                    arr.put(sj);
                }
                JSONObject body = new JSONObject();
                body.put("datapoint", new JSONObject().put("value", arr.toString()));
                postJson(API_BASE + "/v1/devices/" + dsn + "/properties/SET_Schedule_Data/datapoints", body);
                callback.onSuccess(null);
            } catch (Exception e) { callback.onError(e.getMessage()); }
        });
    }

    private String get(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        if (accessToken != null) conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) return readStream(conn.getInputStream());
        throw new IOException("HTTP " + code + ": " + readStream(conn.getErrorStream()));
    }

    private void postJson(String urlString, JSONObject body) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (accessToken != null) conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setDoOutput(true); conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
        conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + readStream(conn.getErrorStream()));
    }

    private String readStream(InputStream s) throws IOException {
        if (s == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(s, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private int parseIntSafe(Object v) {
        if (v == null) return 0;
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
}
