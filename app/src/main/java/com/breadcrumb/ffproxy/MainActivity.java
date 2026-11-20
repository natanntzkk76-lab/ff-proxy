package com.breadcrumb.ffproxy;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity implements Shizuku.OnRequestPermissionResultListener {
    private static final int MEDIA_PROJECTION_REQUEST = 100;
    private static final int OVERLAY_REQUEST = 101;
    private static final int SHIZUKU_REQUEST = 102;

    private TextView statusText;
    private Button btnShizuku, btnInject, btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupUI();
        Shizuku.addRequestPermissionResultListener(this);
        checkShizukuStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(this);
    }

    private void setupUI() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 60);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        // Logo
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_junin_logo);
        logo.setLayoutParams(new LinearLayout.LayoutParams(200, 200));
        layout.addView(logo);

        // Título
        TextView title = new TextView(this);
        title.setText("JUNIN MODS");
        title.setTextSize(32);
        title.setTextColor(0xFFFFD700);
        title.setGravity(View.TEXT_ALIGNMENT_CENTER);
        title.setPadding(0, 20, 0, 10);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("v4.0 — External Engine + Memory Injection");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFFAAAAAA);
        subtitle.setGravity(View.TEXT_ALIGNMENT_CENTER);
        subtitle.setPadding(0, 0, 0, 30);
        layout.addView(subtitle);

        // Status
        statusText = new TextView(this);
        statusText.setText("Shizuku: Verificando...");
        statusText.setTextSize(16);
        statusText.setPadding(0, 20, 0, 20);
        layout.addView(statusText);

        // Botão Shizuku
        btnShizuku = new Button(this);
        btnShizuku.setText("1. Autorizar Shizuku");
        btnShizuku.setOnClickListener(v -> requestShizukuPermission());
        layout.addView(btnShizuku);

        // Botão Injetar
        btnInject = new Button(this);
        btnInject.setText("2. Injetar Engine no FF");
        btnInject.setEnabled(false);
        btnInject.setOnClickListener(v -> startInjection());
        layout.addView(btnInject);

        // Botão Iniciar
        btnStart = new Button(this);
        btnStart.setText("3. Iniciar ESP/Aimbot");
        btnStart.setEnabled(false);
        btnStart.setOnClickListener(v -> checkPermissionsAndStart());
        layout.addView(btnStart);

        // Info
        TextView info = new TextView(this);
        info.setText("Fluxo:\n1. Autorize Shizuku\n2. Abra o Free Fire\n3. Injete a engine\n4. Ative o overlay");
        info.setTextSize(13);
        info.setPadding(0, 30, 0, 0);
        info.setGravity(View.TEXT_ALIGNMENT_CENTER);
        layout.addView(info);

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private void checkShizukuStatus() {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            statusText.setText("Shizuku: Não instalado");
            statusText.setTextColor(0xFFFF0000);
            return;
        }
        if (Shizuku.checkSelfPermission() == 0) {
            statusText.setText("Shizuku: Autorizado ✓");
            statusText.setTextColor(0xFF00FF00);
            btnShizuku.setText("Shizuku Conectado");
            btnShizuku.setEnabled(false);
            btnInject.setEnabled(true);
        } else {
            statusText.setText("Shizuku: Pendente");
            statusText.setTextColor(0xFFFFAA00);
        }
    }

    private void requestShizukuPermission() {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            Toast.makeText(this, "Instale o app Shizuku da Play Store", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Intent.ACTION_VIEW, 
                Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")));
            return;
        }
        Shizuku.requestPermission(SHIZUKU_REQUEST);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, int grantResult) {
        if (requestCode == SHIZUKU_REQUEST) {
            checkShizukuStatus();
        }
    }

    private void startInjection() {
        Toast.makeText(this, "Injetando engine via Shizuku...", Toast.LENGTH_SHORT).show();
        Intent injectIntent = new Intent(this, InjectorService.class);
        injectIntent.setAction("INJECT");
        startForegroundService(injectIntent);
        
        // Aguarda 3 segundos e libera o botão de iniciar
        btnInject.setText("Injetando...");
        btnInject.setEnabled(false);
        new android.os.Handler().postDelayed(() -> {
            btnInject.setText("Engine Injetada ✓");
            btnStart.setEnabled(true);
        }, 3000);
    }

    private void checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQUEST);
            return;
        }

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = manager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, MEDIA_PROJECTION_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST) {
            if (Settings.canDrawOverlays(this)) checkPermissionsAndStart();
            return;
        }
        if (requestCode == MEDIA_PROJECTION_REQUEST && resultCode == RESULT_OK) {
            Intent captureService = new Intent(this, ScreenCaptureService.class);
            captureService.putExtra("CODE", resultCode);
            captureService.putExtra("DATA", data);
            startForegroundService(captureService);
            startForegroundService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "ESP/Aimbot ATIVO", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            finish();
        }
    }
}
