package com.liveplayergames.finneypoker;


//import net.sourceforge.zbar.android.CameraTest.CameraPreview;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.Log;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Button;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;

import android.widget.TextView;
import android.graphics.ImageFormat;
import android.widget.Toast;

/* Import ZBar Class files */
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;
import net.sourceforge.zbar.Config;

import static android.content.DialogInterface.BUTTON_POSITIVE;

public class ScanActivity
        //extends Activity
        extends AppCompatActivity
        implements PreviewCallback
{
    private FrameLayout overlay_frame_layout;
    private String target_activity;
    private Camera mCamera;
    private CameraPreview mPreview;
    private Handler autoFocusHandler;
    private ScanActivity context;
    TextView instructions_view;
    String help_text;
    String help_title;
    private static final int REQUEST_CODE_ASK_PERMISSIONS = 123;

    //Button scanButton;

    ImageScanner scanner;

    private boolean barcodeScanned = false;
    private boolean previewing = true;


    static {
        System.loadLibrary("iconv");
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        overlay_frame_layout = new FrameLayout(getApplicationContext());
        setContentView(overlay_frame_layout);
        View activity_scan_view = getLayoutInflater().inflate(R.layout.activity_scan, overlay_frame_layout, false);
        setContentView(activity_scan_view);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        //The internal implementation of the support library just checks if the Toolbar has a title (not null) at the moment the SupportActionBar is
        //set up. If there is, then this title will be used instead of the window title. You can then set a dummy title while you load the real title.
        toolbar.setTitle("");
        toolbar.setBackgroundResource(R.color.color_toolbar);
        setSupportActionBar(toolbar);
        //
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        target_activity = getIntent().getStringExtra("TARGET_ACTIVITY");
        String scan_prompt = getIntent().getStringExtra("SCAN_PROMPT");
        help_text = scan_prompt;
        autoFocusHandler = new Handler();
        //Instance barcode scanner
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);
        //
        int subtitle_R = target_activity.equals("SendActivity") ? R.string.subtitle_send : R.string.subtitle_import;
        String subtitle = getResources().getString(subtitle_R);
        String app_name = getResources().getString(R.string.app_name);
        toolbar.setTitle(app_name);
        toolbar.setSubtitle(subtitle);
        help_title = subtitle;
        instructions_view = (TextView)findViewById(R.id.instructions);
        instructions_view.setText(scan_prompt);
        barcodeScanned = false;
    }


    //returns false => no options menu
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.scan_options, menu);
        return(true);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.manual_entry:
                int title_R = target_activity.equals("SendActivity") ? R.string.enter_addr_title : R.string.enter_key_title;
                int msg_R = target_activity.equals("SendActivity") ? R.string.enter_addr_prompt : R.string.enter_key_prompt;
                String title = getResources().getString(title_R);
                String msg = getResources().getString(msg_R);
                do_manual_entry(title, msg);
                return true;
            case R.id.help:
                do_help(null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override protected void onStart() {
        super.onStart();
        do_scan_wrapper();
    }

    public void onPause() {
        super.onPause();
        System.out.println("ScanActivity: in onPause");
        releaseCamera();
    }


    public void do_help(View view) {
        android.support.v7.app.AlertDialog.Builder alertDialogBuilder = new android.support.v7.app.AlertDialog.Builder(context);
        alertDialogBuilder.setTitle(help_title);
        alertDialogBuilder.setMessage(help_text);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setNeutralButton(getResources().getString(R.string.OK),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                });
        android.support.v7.app.AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void do_manual_entry(String title, String msg) {
        EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        InputFilter filter = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                String filtered = "";
                boolean do_replace = false;
                for (int i = start; i < end; i++) {
                    char character = source.charAt(i);
                    if ((character == 'x' || character == 'X') ||
                        (character >= '0' && character <= '9') ||
                        (character >= 'a' && character <= 'f') || (character >= 'A' && character <= 'F')) {
                        filtered += character;
                    } else {
                        do_replace = true;
                    }
                }
                return(do_replace ? filtered : null);
            }

        };
        input.setFilters(new InputFilter[] { filter });
        ScanActivity.Handle_Manual_Entry handle_manual_entry = new ScanActivity.Handle_Manual_Entry(input);
        android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
        alert_dialog_builder.setTitle(title);
        alert_dialog_builder.setMessage(msg);
        alert_dialog_builder.setPositiveButton(getResources().getString(R.string.OK), handle_manual_entry);
        alert_dialog_builder.setView(input);
        android.support.v7.app.AlertDialog dialog;
        dialog = alert_dialog_builder.create();
        dialog.show();
    }

    private class Handle_Manual_Entry implements DialogInterface.OnClickListener {
        final EditText input;
        Handle_Manual_Entry(final EditText input) {
            this.input = input;
        }
        public void onClick(DialogInterface dialog, int id) {
            switch (id) {
                case BUTTON_POSITIVE:
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    String data = input.getText().toString();
                    dialog.cancel();
                    System.out.println("manual entry data = " + data);
                    if (handle_scanned_data(context, target_activity, data));
                        finish();
                    break;
            }
        }
    }


    private void do_scan_wrapper() {
        int has_write_permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (has_write_permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_ASK_PERMISSIONS);
            return;
        }
        do_scan_guts();
    }

    private void do_scan_guts() {
        mCamera = getCameraInstance();
        mPreview = new CameraPreview(this, mCamera, this, autoFocusCB);
        FrameLayout preview = (FrameLayout)findViewById(R.id.cameraPreview);
        preview.addView(mPreview);
        mCamera.setPreviewCallback(this);
        mCamera.startPreview();
        previewing = true;
        try {
            mCamera.autoFocus(autoFocusCB);
        } catch (Exception e) {
            System.out.println("autofocus failed: " + e.toString());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    do_scan_guts();
                else {
                    String msg = getResources().getString(R.string.cant_scan_cuz_no_access_msg);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open();
        } catch (Exception e){
            System.out.println(e.toString());
        }
        return c;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            previewing = false;
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    // Mimic continuous auto-focusing
     // Mimic continuous auto-focusing
    AutoFocusCallback autoFocusCB = new AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                if (previewing && mCamera != null)
                    autoFocusHandler.postDelayed(doAutoFocus, 1000);
            }
    };


    private Runnable doAutoFocus = new Runnable() {
            public void run() {
                if (previewing && mCamera != null)
                    mCamera.autoFocus(autoFocusCB);
            }
        };

    //to fullfill contract as a PreviewCallback
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (!previewing || camera == null || mCamera == null)
            return;
        Camera.Parameters parameters = camera.getParameters();
        Size size = parameters.getPreviewSize();
        Image barcode = new Image(size.width, size.height, "Y800");
        barcode.setData(data);

        int result = scanner.scanImage(barcode);
        if (result != 0) {
           previewing = false;
           mCamera.setPreviewCallback(null);
           mCamera.stopPreview();
           String scanned_data = "no data";
           SymbolSet syms = scanner.getResults();
           for (Symbol sym : syms) {
               //scanText.setText("barcode result " + sym.getData());
               scanned_data = sym.getData();
               barcodeScanned = true;
           }
           if (handle_scanned_data(context, target_activity, scanned_data));
            this.finish();
        }
    }

    static public boolean handle_scanned_data(Activity context, String target_activity, String scanned_data) {
        switch (target_activity) {
            case "SendActivity": {
                Intent intent = new Intent(context, SendActivity.class);
                //many QR codes simple contain the address, (eg 0x....); but i've seen this format also:
                //ethereum:<address>[?value=<value>][?gas=<suggestedGas>]
                String to_addr = scanned_data;
                if (scanned_data.contains(":0x")) {
                    int addr_idx = scanned_data.indexOf(':') + 1;
                    int end_idx = scanned_data.indexOf('?', addr_idx);
                    to_addr = (end_idx < 0) ? scanned_data.substring(addr_idx) : scanned_data.substring(addr_idx, end_idx);
                }
                String size_str = "0";
                if (scanned_data.contains("value=")) {
                    int size_idx = scanned_data.indexOf("value=") + "value=".length() + 1;
                    int end_idx = scanned_data.indexOf('?', size_idx);
                    size_str = (end_idx < 0) ? scanned_data.substring(size_idx) : scanned_data.substring(size_idx, end_idx);
                }
                intent.putExtra("TO_ADDR", to_addr);
                intent.putExtra("SIZE", size_str);
                intent.putExtra("DATA", "");
                intent.putExtra("AUTO_PAY", "");
                //finish scanactivity so back key doesn't bring us back here
                System.out.println("ScanACtivity::onPreviewFrame -- starting SendActivity");
                context.startActivity(intent);
                return(true);
            }
            case "ShareActivity": {
                String app_uri = context.getResources().getString(R.string.app_uri);
                SharedPreferences preferences = context.getSharedPreferences(app_uri, MODE_PRIVATE);
                SharedPreferences.Editor preferences_editor = preferences.edit();
                preferences_editor.putString("key", scanned_data);
                preferences_editor.commit();
                NavUtils.navigateUpFromSameTask(context);
                return(true);
            }
            default: {
                System.out.println("TARGET_ACTIVITY not set in ScanActivity");
                return(false);
            }
        }
    }

}
