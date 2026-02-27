package com.sharkcontrol.model;

public class RobotStatus {
    private String operatingMode;
    private int batteryCapacity;
    private String powerMode;
    private int cleaningTime;
    private int errorCode;
    private int volume;
    private boolean charging;
    private int rssi;

    public String getOperatingMode() { return operatingMode; }
    public void setOperatingMode(String operatingMode) { this.operatingMode = operatingMode; }

    public int getBatteryCapacity() { return batteryCapacity; }
    public void setBatteryCapacity(int batteryCapacity) { this.batteryCapacity = batteryCapacity; }

    public String getPowerMode() { return powerMode; }
    public void setPowerMode(String powerMode) { this.powerMode = powerMode; }

    public int getCleaningTime() { return cleaningTime; }
    public void setCleaningTime(int cleaningTime) { this.cleaningTime = cleaningTime; }

    public int getErrorCode() { return errorCode; }
    public void setErrorCode(int errorCode) { this.errorCode = errorCode; }

    public int getVolume() { return volume; }
    public void setVolume(int volume) { this.volume = volume; }

    public boolean isCharging() { return charging; }
    public void setCharging(boolean charging) { this.charging = charging; }

    public int getRssi() { return rssi; }
    public void setRssi(int rssi) { this.rssi = rssi; }

    public boolean hasError() { return errorCode > 0; }
}
