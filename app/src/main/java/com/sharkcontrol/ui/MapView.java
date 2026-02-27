package com.sharkcontrol.ui;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import com.sharkcontrol.model.MapData;

/**
 * Custom View that renders the Shark robot cleaning map.
 * Supports pinch-to-zoom and pan gestures.
 *
 * The Shark RV2000 map is encoded as a grid where each cell represents
 * a 50x50mm area. Values:
 *   0   = unknown/unexplored
 *   1   = free space (cleaned)
 *   2   = obstacle/wall
 *   255 = robot position
 */
public class MapView extends View {

    private MapData mapData;
    private Paint floorPaint, wallPaint, unknownPaint, robotPaint, chargePaint, gridPaint;
    private Matrix matrix = new Matrix();
    private ScaleGestureDetector scaleDetector;

    private float scaleFactor = 1f;
    private float translateX = 0f, translateY = 0f;
    private float lastTouchX, lastTouchY;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 8f;
    private static final int CELL_SIZE_PX = 8; // pixels per grid cell

    public MapView(Context context) { this(context, null); }
    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        floorPaint = new Paint();
        floorPaint.setColor(Color.parseColor("#E3F2FD")); // light blue - cleaned
        floorPaint.setStyle(Paint.Style.FILL);

        wallPaint = new Paint();
        wallPaint.setColor(Color.parseColor("#37474F")); // dark - walls
        wallPaint.setStyle(Paint.Style.FILL);

        unknownPaint = new Paint();
        unknownPaint.setColor(Color.parseColor("#1A1A2E")); // background - unexplored
        unknownPaint.setStyle(Paint.Style.FILL);

        robotPaint = new Paint();
        robotPaint.setColor(Color.parseColor("#4FC3F7")); // cyan - robot
        robotPaint.setStyle(Paint.Style.FILL);
        robotPaint.setAntiAlias(true);

        chargePaint = new Paint();
        chargePaint.setColor(Color.parseColor("#66BB6A")); // green - charging station
        chargePaint.setStyle(Paint.Style.FILL);
        chargePaint.setAntiAlias(true);

        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#0F3460"));
        gridPaint.setStrokeWidth(0.5f);
        gridPaint.setStyle(Paint.Style.STROKE);

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(MIN_SCALE, Math.min(scaleFactor, MAX_SCALE));
                invalidate();
                return true;
            }
        });
    }

    public void setMapData(MapData mapData) {
        this.mapData = mapData;
        // Center the map initially
        post(() -> {
            if (mapData != null && mapData.getGrid() != null) {
                int gridW = mapData.getGrid()[0].length * CELL_SIZE_PX;
                int gridH = mapData.getGrid().length * CELL_SIZE_PX;
                translateX = (getWidth() - gridW) / 2f;
                translateY = (getHeight() - gridH) / 2f;
                // Auto-scale to fit
                float scaleX = (float) getWidth() / gridW;
                float scaleY = (float) getHeight() / gridH;
                scaleFactor = Math.min(scaleX, scaleY) * 0.9f;
                translateX = (getWidth() - gridW * scaleFactor) / 2f;
                translateY = (getHeight() - gridH * scaleFactor) / 2f;
            }
            invalidate();
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mapData == null || mapData.getGrid() == null) {
            // Draw placeholder
            Paint p = new Paint();
            p.setColor(Color.GRAY);
            p.setTextSize(40f);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Keine Karte", getWidth() / 2f, getHeight() / 2f, p);
            return;
        }

        canvas.save();
        canvas.translate(translateX, translateY);
        canvas.scale(scaleFactor, scaleFactor);

        int[][] grid = mapData.getGrid();
        int rows = grid.length;
        int cols = grid[0].length;

        // Draw background
        canvas.drawRect(0, 0, cols * CELL_SIZE_PX, rows * CELL_SIZE_PX, unknownPaint);

        // Draw cells
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int val = grid[r][c];
                Paint paint = null;
                switch (val) {
                    case MapData.CELL_FLOOR: paint = floorPaint; break;
                    case MapData.CELL_WALL:  paint = wallPaint;  break;
                    default: continue; // skip unknown cells (keep background)
                }
                float x = c * CELL_SIZE_PX;
                float y = r * CELL_SIZE_PX;
                canvas.drawRect(x, y, x + CELL_SIZE_PX, y + CELL_SIZE_PX, paint);
            }
        }

        // Draw charging station
        if (mapData.getChargeX() >= 0) {
            float cx = mapData.getChargeX() * CELL_SIZE_PX + CELL_SIZE_PX / 2f;
            float cy = mapData.getChargeY() * CELL_SIZE_PX + CELL_SIZE_PX / 2f;
            canvas.drawCircle(cx, cy, CELL_SIZE_PX * 1.5f, chargePaint);
        }

        // Draw robot position
        if (mapData.getRobotX() >= 0) {
            float rx = mapData.getRobotX() * CELL_SIZE_PX + CELL_SIZE_PX / 2f;
            float ry = mapData.getRobotY() * CELL_SIZE_PX + CELL_SIZE_PX / 2f;
            canvas.drawCircle(rx, ry, CELL_SIZE_PX * 2f, robotPaint);
            // Direction indicator
            double angle = Math.toRadians(mapData.getRobotAngle());
            float arrowLen = CELL_SIZE_PX * 2.5f;
            float ax = rx + (float)(Math.cos(angle) * arrowLen);
            float ay = ry + (float)(Math.sin(angle) * arrowLen);
            Paint arrowPaint = new Paint();
            arrowPaint.setColor(Color.WHITE);
            arrowPaint.setStrokeWidth(2f);
            canvas.drawLine(rx, ry, ax, ay, arrowPaint);
        }

        canvas.restore();

        // Draw legend
        drawLegend(canvas);
    }

    private void drawLegend(Canvas canvas) {
        float y = getHeight() - 120f;
        float x = 20f;

        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setAntiAlias(true);

        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#AA16213E"));
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(x - 10, y - 40, x + 300, y + 90, 12, 12, bgPaint);

        // Floor
        floorPaint.setAlpha(255);
        canvas.drawRect(x, y, x + 20, y + 20, floorPaint);
        canvas.drawText("Gereinigt", x + 28, y + 16, textPaint);

        // Wall
        canvas.drawRect(x, y + 30, x + 20, y + 50, wallPaint);
        canvas.drawText("Wand", x + 28, y + 46, textPaint);

        // Robot
        canvas.drawCircle(x + 10, y + 70, 10, robotPaint);
        canvas.drawText("Roboter", x + 28, y + 76, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    translateX += event.getX() - lastTouchX;
                    translateY += event.getY() - lastTouchY;
                    invalidate();
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;
        }
        return true;
    }
}
