package com.example.droneframes;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.media.MediaMetadataRetriever;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 100, SAVE_ZIP = 101;
    private Uri videoUri;
    private TextView status;
    private Spinner interval, format;
    private EditText maxFrames;
    private Button select, export, cancel;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean working = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 40, 32, 24);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        TextView heading = new TextView(this);
        heading.setText("Drone Frame Extractor"); heading.setTextSize(24); heading.setTextColor(Color.BLACK);
        root.addView(heading);
        TextView note = new TextView(this);
        note.setText("Export original-resolution video frames with timestamps to a ZIP. Processing happens on your phone.");
        root.addView(note);
        select = new Button(this); select.setText("1. Choose video"); root.addView(select);
        select.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("video/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, PICK_VIDEO); });
        TextView intervalLabel = new TextView(this); intervalLabel.setText("Frame interval"); root.addView(intervalLabel);
        interval = new Spinner(this);
        String[] intervals = {"0.5 seconds", "1 second", "2 seconds", "5 seconds", "10 seconds"};
        interval.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, intervals)); interval.setSelection(2); root.addView(interval);
        TextView formatLabel = new TextView(this); formatLabel.setText("Image format"); root.addView(formatLabel);
        format = new Spinner(this); format.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"JPG (quality 95)", "PNG (lossless export)"})); root.addView(format);
        TextView limitLabel = new TextView(this); limitLabel.setText("Maximum frames (safety limit)"); root.addView(limitLabel);
        maxFrames = new EditText(this); maxFrames.setInputType(2); maxFrames.setText("300"); root.addView(maxFrames);
        export = new Button(this); export.setText("2. Export ZIP"); export.setEnabled(false); root.addView(export);
        export.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("application/zip"); i.addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_TITLE, "drone_frames.zip"); startActivityForResult(i, SAVE_ZIP); });
        cancel = new Button(this); cancel.setText("Cancel export"); cancel.setEnabled(false); root.addView(cancel);
        cancel.setOnClickListener(v -> { cancelled.set(true); status.setText("Stopping after current frame..."); });
        status = new TextView(this); status.setText("Choose a video to begin."); root.addView(status);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == PICK_VIDEO && !working) {
            videoUri = data.getData();
            export.setEnabled(true);
            status.setText("Video selected. Ready to export.");
        } else if (requestCode == SAVE_ZIP && videoUri != null && !working) {
            Uri outputUri = data.getData();
            double[] intervals = {0.5, 1, 2, 5, 10};
            double step = intervals[interval.getSelectedItemPosition()];
            boolean png = format.getSelectedItemPosition() == 1;
            int limit;
            try { limit = Integer.parseInt(maxFrames.getText().toString()); } catch (Exception e) { limit = 300; }
            limit = Math.max(1, Math.min(2000, limit));
            startExport(outputUri, step, png, limit);
        }
    }

    private void startExport(Uri destination, double stepSeconds, boolean png, int limit) {
        cancelled.set(false); working = true;
        select.setEnabled(false); export.setEnabled(false); cancel.setEnabled(true);
        status.setText("Starting extraction...");
        new Thread(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            int saved = 0;
            try {
                retriever.setDataSource(this, videoUri);
                String durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durationString == null) throw new IOException("Cannot read video duration");
                long durationUs = Long.parseLong(durationString) * 1000L;
                long stepUs = Math.max(1L, Math.round(stepSeconds * 1_000_000));
                int estimated = (int)Math.min(limit, 1L + (durationUs > 0 ? (durationUs - 1) / stepUs : 0));
                try (OutputStream raw = getContentResolver().openOutputStream(destination)) {
                    if (raw == null) throw new IOException("Cannot open destination");
                    try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw))) {
                        StringBuilder csv = new StringBuilder("filename,timestamp_ms,timestamp_seconds,width,height\n");
                        for (int i = 0; i < estimated && !cancelled.get(); i++) {
                            long timeUs = i * stepUs;
                            if (timeUs >= durationUs) break;
                            Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
                            if (frame == null) continue;
                            String extension = png ? "png" : "jpg";
                            String filename = String.format(Locale.US, "frame_%05d_%010dms.%s", i, timeUs / 1000, extension);
                            zip.putNextEntry(new ZipEntry("frames/" + filename));
                            boolean ok = frame.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, png ? 100 : 95, zip);
                            zip.closeEntry();
                            if (!ok) throw new IOException("Image encoder failed");
                            csv.append(String.format(Locale.US, "%s,%d,%.3f,%d,%d\n", filename, timeUs / 1000, timeUs / 1_000_000.0, frame.getWidth(), frame.getHeight()));
                            frame.recycle(); saved++;
                            int count = saved;
                            runOnUiThread(() -> status.setText("Exported " + count + " / " + estimated + " frames"));
                        }
                        zip.putNextEntry(new ZipEntry("frames.csv"));
                        zip.write(csv.toString().getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                        String readme = "Drone Frame Extractor\nTimestamps are offsets from the start of the selected video, not wall-clock timestamps.\nFrames are decoded from the source video; PNG does not restore original camera sensor data.\nRequested sampling interval (seconds): " + stepSeconds + "\n";
                        zip.putNextEntry(new ZipEntry("README.txt"));
                        zip.write(readme.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                    }
                }
                int count = saved;
                runOnUiThread(() -> status.setText((cancelled.get() ? "Cancelled; partial ZIP may exist. " : "Complete! ") + count + " frames saved."));
            } catch (Exception e) {
                String msg = e.getMessage();
                runOnUiThread(() -> status.setText("Export failed: " + (msg == null ? e.getClass().getSimpleName() : msg)));
            } finally {
                try {
                    retriever.release();
                } catch (IOException e) {
                    android.util.Log.e("DroneFrames", "Failed to release video retriever", e);
                }
                working = false;
                runOnUiThread(() -> { select.setEnabled(true); export.setEnabled(videoUri != null); cancel.setEnabled(false); });
            }
        }).start();
    }
}
