package com.breadcrumb.ffproxy;
import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = VpnService.prepare(this);
        if (intent != null) startActivityForResult(intent, 0);
        else onActivityResult(0, RESULT_OK, null);
    }
    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (result == RESULT_OK) {
            startService(new Intent(this, FreeFireVpnService.class));
            finish();
        }
    }
}
