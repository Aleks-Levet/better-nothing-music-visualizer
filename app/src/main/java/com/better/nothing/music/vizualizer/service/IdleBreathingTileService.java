package com.better.nothing.music.vizualizer.service;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.better.nothing.music.vizualizer.R;
import com.better.nothing.music.vizualizer.model.DeviceProfile;

public class IdleBreathingTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isCaptureRunning()) {
            refresh();
            return;
        }

        if (IdleBreathingService.isActive(this)) {
            stopService(new Intent(this, IdleBreathingService.class));
            getSharedPreferences("viz_prefs", MODE_PRIVATE).edit()
                    .putBoolean("idle_breathing_tile_active", false)
                    .apply();
            refresh(false);
        } else {
            Intent startIntent = new Intent(this, IdleBreathingService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent);
            } else {
                startService(startIntent);
            }
            refresh(true);
        }
    }

    private void refresh() {
        boolean captureRunning = isCaptureRunning();
        boolean glyphAvailable = DeviceProfile.detectDevice() != DeviceProfile.DEVICE_UNKNOWN
                && getSharedPreferences("viz_prefs", MODE_PRIVATE).getInt("max_brightness", 4095) > 0;
        boolean active = IdleBreathingService.isActive(this) && !captureRunning;
        refresh(active, captureRunning, glyphAvailable);
    }

    private void refresh(boolean active) {
        boolean captureRunning = isCaptureRunning();
        boolean glyphAvailable = DeviceProfile.detectDevice() != DeviceProfile.DEVICE_UNKNOWN
                && getSharedPreferences("viz_prefs", MODE_PRIVATE).getInt("max_brightness", 4095) > 0;
        refresh(active, captureRunning, glyphAvailable);
    }

    private void refresh(boolean active, boolean captureRunning, boolean glyphAvailable) {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean activeState = active && !captureRunning && glyphAvailable;
        tile.setState(captureRunning || !glyphAvailable
                ? Tile.STATE_UNAVAILABLE
                : activeState ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.idle_breathing_title));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(activeState
                    ? getString(R.string.tile_running)
                    : getString(R.string.tile_subtitle_default));
        }
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_tile_idle));
        tile.updateTile();
    }

    private boolean isCaptureRunning() {
        return AudioCaptureService.isRunning()
                || getSharedPreferences("viz_prefs", MODE_PRIVATE).getBoolean("should_be_running", false);
    }
}
