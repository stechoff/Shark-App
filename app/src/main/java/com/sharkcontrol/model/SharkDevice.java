package com.sharkcontrol.model;

public class SharkDevice {
    private String dsn;
    private String productName;
    private String model;
    private boolean connected;

    public String getDsn() { return dsn; }
    public void setDsn(String dsn) { this.dsn = dsn; }

    public String getProductName() { return productName != null ? productName : "Shark Robot"; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    @Override
    public String toString() { return getProductName(); }
}
