package com.sharkcontrol.model;

public class MapData {
    public static final int CELL_UNKNOWN = 0;
    public static final int CELL_FLOOR   = 1;
    public static final int CELL_WALL    = 2;
    public static final int CELL_ROBOT   = 255;

    private int[][] grid;
    private int robotX = -1, robotY = -1;
    private float robotAngle = 0f;
    private int chargeX = -1, chargeY = -1;
    private int cleanedCells = 0;
    private int totalCells = 0;

    public int[][] getGrid() { return grid; }
    public void setGrid(int[][] grid) { this.grid = grid; }

    public int getRobotX() { return robotX; }
    public void setRobotX(int robotX) { this.robotX = robotX; }

    public int getRobotY() { return robotY; }
    public void setRobotY(int robotY) { this.robotY = robotY; }

    public float getRobotAngle() { return robotAngle; }
    public void setRobotAngle(float robotAngle) { this.robotAngle = robotAngle; }

    public int getChargeX() { return chargeX; }
    public void setChargeX(int chargeX) { this.chargeX = chargeX; }

    public int getChargeY() { return chargeY; }
    public void setChargeY(int chargeY) { this.chargeY = chargeY; }

    public int getCleanedCells() { return cleanedCells; }
    public void setCleanedCells(int cleanedCells) { this.cleanedCells = cleanedCells; }

    public int getTotalCells() { return totalCells; }
    public void setTotalCells(int totalCells) { this.totalCells = totalCells; }

    public boolean hasData() {
        return grid != null && grid.length > 0;
    }

    /** Returns cleaned area in square meters (each cell = 50x50mm = 0.0025 m²) */
    public float getCleanedAreaSqm() {
        return cleanedCells * 0.0025f;
    }
}
