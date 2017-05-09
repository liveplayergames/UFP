package com.liveplayergames.finneypoker;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;


/**
 * Created by dbrosen on 2/4/17.
 */

public class PurchaseActivity extends AppCompatActivity {

    private SharedPreferences preferences;
    private FrameLayout overlay_frame_layout;
    private PurchaseActivity context;
    private android.support.v7.app.AlertDialog dialog = null;
    private String acct_addr = "";
    private String my_id = "";
    private Toast toast = null;
    private boolean purchase_sent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overlay_frame_layout = new FrameLayout(getApplicationContext());
        setContentView(overlay_frame_layout);
        View activity_send_view = getLayoutInflater().inflate(R.layout.activity_purchase, overlay_frame_layout, false);
        setContentView(activity_send_view);
        //
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setBackgroundResource(R.color.color_toolbar);
        String app_name = getResources().getString(R.string.app_name);
        toolbar.setTitle(app_name);
        toolbar.setSubtitle(getResources().getString(R.string.purchase_subtitle));
        setSupportActionBar(toolbar);
        context = this;
        String app_uri = getResources().getString(R.string.app_uri);
        preferences = getSharedPreferences(app_uri, MODE_PRIVATE);
        my_id = preferences.getString("device_id", "");
        acct_addr = preferences.getString("acct_addr", "");
        //
    }


    //returns false => no options menu
    public boolean onCreateOptionsMenu(Menu menu) {
        return (false);
    }

    public void onResume() {
        super.onResume();  // Always call the superclass method first
        String title = getResources().getString(R.string.purchase_not_sup_title);
        String msg = getResources().getString(R.string.purchase_not_sup_msg);
        show_err_and_exit_dialog(title, msg, true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    private void show_err_and_exit_dialog(String title, String msg, final boolean do_exit) {
        android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
        alert_dialog_builder.setTitle(title);
        alert_dialog_builder.setMessage(msg);
        alert_dialog_builder.setCancelable(false);
        alert_dialog_builder.setNeutralButton(getResources().getString(R.string.OK),
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    if (do_exit)
                        finish();
                }
            });
            dialog = alert_dialog_builder.create();
            dialog.show();
    }
}

