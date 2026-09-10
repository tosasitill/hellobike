package dev.local.ridecompact;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageButton;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.camera.CameraSettings;

public final class PortraitScanActivity extends CaptureActivity {
    private ImageButton torch;
    private boolean lit;
    @Override protected void onCreate(Bundle state) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        super.onCreate(state);
        getWindow().setGravity(android.view.Gravity.BOTTOM);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams params=getWindow().getAttributes(); params.dimAmount=0.38f; getWindow().setAttributes(params);
        getWindow().setLayout(-1, (int)(getResources().getDisplayMetrics().heightPixels * 0.62f));
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus) getWindow().setLayout(-1, (int)(getResources().getDisplayMetrics().heightPixels * 0.62f));
    }

    @Override protected DecoratedBarcodeView initializeContent() {
        setContentView(R.layout.scan_portrait);
        DecoratedBarcodeView scanner=findViewById(R.id.zxing_barcode_scanner);
        CameraSettings settings=scanner.getBarcodeView().getCameraSettings();
        settings.setFocusMode(CameraSettings.FocusMode.CONTINUOUS);
        scanner.getBarcodeView().setCameraSettings(settings);
        torch=findViewById(R.id.scan_torch);
        torch.setEnabled(getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH));
        torch.setOnClickListener(v->{ if(lit) scanner.setTorchOff(); else scanner.setTorchOn(); });
        scanner.setTorchListener(new DecoratedBarcodeView.TorchListener() {
            public void onTorchOn() { lit=true; torch.setSelected(true); torch.setAlpha(1f); torch.setContentDescription("关闭手电筒"); torch.setTooltipText("关闭手电筒"); }
            public void onTorchOff() { lit=false; torch.setSelected(false); torch.setAlpha(.65f); torch.setContentDescription("打开手电筒"); torch.setTooltipText("打开手电筒"); }
        });
        findViewById(R.id.scan_back).setOnClickListener(v->finish());
        return scanner;
    }
}
