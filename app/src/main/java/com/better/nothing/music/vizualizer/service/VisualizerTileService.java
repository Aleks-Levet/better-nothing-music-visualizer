package com.better.nothing.music.vizualizer.service;

import com.better.nothing.music.vizualizer.R;
import com.better.nothing.music.vizualizer.ui.MainActivity;
import com.better.nothing.music.vizualizer.ui.TrampolineActivity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.Manifest;

public class VisualizerTileService extends TileService {
    @Override public void onStartListening() { super.onStartListening(); refresh(); }
    @Override public void onClick() {
        super.onClick();
        if (AudioCaptureService.isRunning()) {
            Intent stopIntent = AudioCaptureService.createStopIntent(this);
            startService(stopIntent);
            refresh(false);
        } else {
            refresh(true); // Immediate UI feedback
            String sourceStr = getSharedPreferences("viz_prefs", MODE_PRIVATE).getString("capture_source", "INTERNAL");
            boolean needsMic = "MIC".equals(sourceStr) || "VIZUALIZER".equals(sourceStr);
            boolean hasMicPerm = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            boolean needsTrampoline = "INTERNAL".equals(sourceStr) || (needsMic && (!hasMicPerm || Build.VERSION.SDK_INT >= 34));

            if (needsTrampoline) {
                unlockAndRun(() -> {
                    Intent i = new Intent(this, TrampolineActivity.class);
                    i.putExtra(AudioCaptureService.EXTRA_START_SOURCE, "viz_started_qs_tile");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    PendingIntent pendingIntent = PendingIntent.getActivity(
                            this,
                            3,
                            i,
                            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                    );
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startActivityAndCollapse(pendingIntent);
                    } else {
                        startActivityAndCollapse(i);
                    }
                });
            } else {
                Intent startIntent = new Intent(this, AudioCaptureService.class);
                startIntent.setAction(AudioCaptureService.ACTION_START);
                startIntent.putExtra(AudioCaptureService.EXTRA_START_SOURCE, "viz_started_qs_tile");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(startIntent);
                } else {
                    startService(startIntent);
                }
            }
        }
    }
    private void refresh() { refresh(AudioCaptureService.isRunning()); }
    private void refresh(boolean on) {
        Tile t=getQsTile(); if(t==null) return;
        t.setState(on?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
        t.setLabel(getString(R.string.app_name));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            t.setSubtitle(on ? getString(R.string.tile_running) : getString(R.string.tile_subtitle_default));
        }
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_launcher_monochrome));
        t.updateTile();
    }
}
