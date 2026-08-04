package com.better.nothing.music.vizualizer.service;

import com.better.nothing.music.vizualizer.R;
import com.better.nothing.music.vizualizer.ui.MainActivity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

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
            if ("INTERNAL".equals(sourceStr)) {
                unlockAndRun(() -> {
                    Intent i = new Intent(this, MainActivity.class);
                    i.putExtra("request_start", true);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    PendingIntent pendingIntent = PendingIntent.getActivity(
                            this,
                            3,
                            i,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );
                    startActivityAndCollapse(pendingIntent);
                });
            } else {
                Intent startIntent = new Intent(this, AudioCaptureService.class);
                startIntent.setAction(AudioCaptureService.ACTION_START);
                startForegroundService(startIntent);
            }
        }
    }
    private void refresh() { refresh(AudioCaptureService.isRunning()); }
    private void refresh(boolean on) {
        Tile t=getQsTile(); if(t==null) return;
        t.setState(on?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
        t.setLabel("BNMV");
        t.setSubtitle(on?"Running":"Better Nothing Music Vizualiser");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_launcher_monochrome));
        t.updateTile();
    }
}
