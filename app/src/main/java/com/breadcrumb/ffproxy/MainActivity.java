package com.breadcrumb.ffproxy;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final int MEDIA_PROJECTION_REQUEST = 100;
    private static final int OVERLAY_REQUEST = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 80, 40, 80);

        TextView title = new TextView(this);
        title.setText("JUNIN MODS");
        title.setTextSize(32);
        title.setTextColor(0xFFFFD700);
        title.setGravity(View.TEXT_ALIGNMENT_CENTER);
        layout.addView(title);

        TextView info = new TextView(this);
        info.setText("Método: Externo (MediaProjection + Accessibility)\nRequisitos:\n1. Permissão de Sobreposição\n2. Permissão de Captura de Tela\n3. Serviço de Acessibilidade");
        info.setTextSize(14);
        info.setPadding(0, 30, 0, 40);
        layout.addView(info);

        Button btnStart = new Button(this);
        btnStart.setText("INICIAR ENGINE");
        btnStart.setTextSize(18);
        btnStart.setOnClickListener(v -> checkPermissionsAndStart());
        layout.addView(btnStart);

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private void checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
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
            if (Settings.canDrawOverlays(this)) {
                checkPermissionsAndStart();
            }
            return;
        }

        if (requestCode == MEDIA_PROJECTION_REQUEST && resultCode == RESULT_OK) {
            Intent captureService = new Intent(this, ScreenCaptureService.class);
            captureService.putExtra("CODE", resultCode);
            captureService.putExtra("DATA", data);
            startForegroundService(captureService);

            startForegroundService(new Intent(this, OverlayService.class));
            
            // Redireciona para configurações de acessibilidade para ativar o Aimbot
            Toast.makeText(this, "ATIVE O 'Junin Mods' NA ACESSIBILIDADE", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            finish();
        }
    }
}
