package com.liveplayergames.finneypoker;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutCompat;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.liveplayergames.finneypoker.HTTP_Query_Client;
import com.liveplayergames.finneypoker.R;
import com.liveplayergames.finneypoker.ShareActivity;

import java.io.IOException;

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static android.content.DialogInterface.BUTTON_NEUTRAL;
import static android.content.DialogInterface.BUTTON_POSITIVE;

/**
 * Created by dbrosen on 12/10/16.
 */
public class NewGameActivity extends AppCompatActivity implements Payment_Processor_Client, HTTP_Query_Client {

    private static final int  MAX_SAVED_GROUP_NAMES = 3;
    private SharedPreferences preferences;
    private FrameLayout overlay_frame_layout;
    private View activity_main_view;
    private NewGameActivity context;
    private Toast toast = null;
    private String acct_addr = "";
    private Handler message_handler = null;
    private boolean show_fees_advisory = true;
    private boolean show_payout_advisory = true;
    private boolean show_payout_payout_pending_toast = false;
    private boolean requested_table_parms = false;

    int saved_group_cnt = 0;
    String[] saved_group_names = null;
    private static long[] max_raises = null; //practice table, intermediate table, high-roller table
    private static CountDownTimer refresh_winnings_timer = null;
    //these defines are for our handle_message fcn, which displays dialogs on behalf of other threads (payment_processor)
    private static final int HANDLE_BALANCE_CALLBACK = 1;
    private static final int HANDLE_INTERIM_CALLBACK = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overlay_frame_layout = new FrameLayout(getApplicationContext());
        setContentView(overlay_frame_layout);
        View activity_new_game_view = getLayoutInflater().inflate(R.layout.activity_new_game, overlay_frame_layout, false);
        setContentView(activity_new_game_view);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        toolbar.setBackgroundResource(R.color.color_toolbar);
        String app_name = getResources().getString(R.string.app_name);
        toolbar.setTitle(app_name);
        toolbar.setSubtitle(getResources().getString(R.string.newgame_subtitle));
        setSupportActionBar(toolbar);
        context = this;
    	String app_uri = getResources().getString(R.string.app_uri);
        preferences = getSharedPreferences(app_uri, MODE_PRIVATE);
        acct_addr = preferences.getString("acct_addr", "");
        show_fees_advisory = preferences.getBoolean("show_fees_advisory", true);
        show_payout_advisory = preferences.getBoolean("show_payout_advisory", true);
        max_raises = new long[3];
        max_raises[0] = max_raises[1] = max_raises[2] = 0;
        String group_name0 = preferences.getString("saved_group_name0", "");
        String group_name1 = preferences.getString("saved_group_name1", "");
        String group_name2 = preferences.getString("saved_group_name2", "");
        saved_group_cnt = 0;
        if (!group_name0.isEmpty()) {
            ++saved_group_cnt;
            if (!group_name1.isEmpty()) {
                ++saved_group_cnt;
                if (!group_name2.isEmpty())
                    ++saved_group_cnt;
            }
        }
        Button old_group_button = (Button)findViewById(R.id.play_old_group);
        old_group_button.setVisibility(View.INVISIBLE);
        if (saved_group_cnt != 0) {
            saved_group_names = new String[saved_group_cnt];
            saved_group_names[0] = group_name0;
            if (saved_group_cnt > 1) {
                saved_group_names[1] = group_name1;
                if (saved_group_cnt > 2) {
                    saved_group_names[2] = group_name2;
                }
            }
        }

        if (show_fees_advisory)
            show_html_alert(R.string.fees_advisory_title, R.string.fees_advisory);
        //
        message_handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case HANDLE_BALANCE_CALLBACK: {
                        boolean ok = (message.arg1 != 0);
                        if (ok) {
                            dsp_balance();
                            ImageButton refresh_button = (ImageButton) findViewById(R.id.refresh_button);
                            refresh_button.clearAnimation();
                            TextView finney_balance_view = (TextView) findViewById(R.id.finney_balance);
                            finney_balance_view.clearAnimation();
                            if (toast != null)
                                toast.cancel();
                            String msg = getResources().getString(R.string.synced_with_blockchain);
                            if (show_payout_payout_pending_toast) {
                                msg += "\n" + getResources().getString(R.string.transactions_maybe_pending);
                                show_payout_payout_pending_toast = false;
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                        } else {
                            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                            alertDialogBuilder.setTitle(getResources().getString(R.string.uhoh_connectivity_title));
                            String msg = getResources().getString(R.string.uhoh_connectivity_balance_msg);
                            alertDialogBuilder.setMessage(msg);
                            alertDialogBuilder.setCancelable(false);
                            alertDialogBuilder.setNeutralButton(getResources().getString(R.string.OK),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                            finish();
                                        }
                                    });
                            AlertDialog alertDialog = alertDialogBuilder.create();
                            alertDialog.show();
                        }
                        break;
                    }
                    //
                    case HANDLE_INTERIM_CALLBACK: {
                        String msg = (String) message.obj;
                        if (toast != null)
                            toast.cancel();
                        (toast = Toast.makeText(context, msg, Toast.LENGTH_LONG)).show();
                        break;
                    }
                }
            }
        };

    }

    //returns false => no options menu
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.new_game_menu, menu);
        menu.findItem(R.id.show_fees_advisory).setChecked(show_fees_advisory);
        menu.findItem(R.id.show_payout_advisory).setChecked(show_payout_advisory);
        return(true);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.show_fees_advisory: {
                show_fees_advisory = item.isChecked() ? false : true;
                item.setChecked(show_fees_advisory);
                SharedPreferences.Editor preferences_editor = preferences.edit();
                preferences_editor.putBoolean("show_fees_advisory", show_fees_advisory);
                preferences_editor.apply();
                if (show_fees_advisory)
                    recreate();
                return true;
            }
            case R.id.show_payout_advisory: {
                show_payout_advisory = item.isChecked() ? false : true;
                item.setChecked(show_payout_advisory);
                SharedPreferences.Editor preferences_editor = preferences.edit();
                preferences_editor.putBoolean("show_payout_advisory", show_payout_advisory);
                preferences_editor.apply();
                return true;
            }
            case R.id.show_hand_rankings: {
                show_html_alert(R.string.hand_rankings_title, R.string.hand_rankings);
                return true;
            }
            case R.id.help: {
                do_help(null);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    public void onResume() {
        super.onResume();  // Always call the superclass method first
        if (toast != null)
            toast.cancel();
        dsp_balance();
        long balance = preferences.getLong("balance", 0);
        long verified_balance = preferences.getLong("verified_balance", 0);
        boolean tx_err_occurred = preferences.getBoolean("tx_err_occurred", false);
        if (balance != verified_balance || tx_err_occurred)
            do_refresh(null);
    	//get table parms
        //only need to do this until the purchase has been sent. once we get back the
        //purchase response we will exit. but avoid re-retreiving the pricelist in case
        //we redraw the screen before we exit.
        if (!requested_table_parms) {
	        set_button_strings(false);
            if (toast != null)
                toast.cancel();
            (toast = Toast.makeText(context, getResources().getString(R.string.retrieving_table_parms), Toast.LENGTH_LONG)).show();
            String server = getResources().getString(R.string.player_server);
            String table_parms_URL = server + "/table_parms";
            String parms[] = new String[2];
            parms[0] = table_parms_URL;
            parms[1] = "table_parms";
            new HTTP_Query_Task(this, context).execute(parms);
        }
	
	
    	//deal with payout advisory if necessary
        boolean show_payout_advisory = preferences.getBoolean("show_payout_advisory", true);
        boolean need_payout_advisory = preferences.getBoolean("need_payout_advisory", false);
        SharedPreferences.Editor preferences_editor = preferences.edit();
        preferences_editor.putBoolean("need_payout_advisory", false);
        preferences_editor.apply();
        //this will be set whenever we would show the payout advisory. it is cleared after the
        //first "synced with blockchain" message
        show_payout_payout_pending_toast = need_payout_advisory;
        if (need_payout_advisory && show_payout_advisory) {
            int tx_cnt = preferences.getInt("payout_advisory_tx_cnt", 0);
            long tx_total = preferences.getLong("payout_advisory_tx_total", 0);
            long start_balance = preferences.getLong("payout_advisory_start_bal", 0);
            long pot_balance = preferences.getLong("payout_advisory_pot", 0);
            long gas_price = preferences.getLong("gas_price", Util.DEFAULT_GAS_PRICE);
            long tx_fees = (long)((float)(PokerActivity.POKER_BET_NOMINAL_GAS * gas_price) * tx_cnt / Util.WEI_PER_FINNEY);
            long deducted_bal = start_balance - tx_total - tx_fees;
            long escrow_fee = (long)((double)pot_balance * 0.02 + 0.5);
            long final_bal = deducted_bal + pot_balance - escrow_fee;
            String title = getResources().getString(R.string.payout_advisory_title);
            String msg = getResources().getString(R.string.payout_advisory_msg);
            msg = msg.replace("START_BAL", String.format("%06d", start_balance));
            msg = msg.replace("TX_TOTAL", String.format("%06d", tx_total));
            msg = msg.replace("TX_CNT", String.valueOf(tx_cnt));
            msg = msg.replace("TX_FEES", String.format("%06d", tx_fees));
            msg = msg.replace("DEDUCTED_BAL", String.format("%06d", deducted_bal));
            msg = msg.replace("POT", String.format("%06d", pot_balance));
            msg = msg.replace("ESCROW_FEE", String.format("%06d", escrow_fee));
            msg = msg.replace("FINAL_BAL", String.format("%06d", final_bal));
            show_html_alert(title, msg);
            if (refresh_winnings_timer != null)
                refresh_winnings_timer.cancel();
                refresh_winnings_timer = new CountDownTimer(180 * 1000, 15 * 1000) {
                public void onTick(long millisUntilFinished) {
                }
                public void onFinish()                     {
                    do_refresh(null);
                }
            }.start();

        }
    }

    public void onStop() {
        if (toast != null)
            toast.cancel();
        if (refresh_winnings_timer != null) {
            refresh_winnings_timer.cancel();
            refresh_winnings_timer = null;
        }
        //if we were waiting to update balance, no need to keep that request queued anymore
        Payment_Processor.cancel_messages(this);
        super.onStop();
    }

  
    public void do_help(View view) {
        show_ok_dialog(R.string.new_game_help_title, R.string.new_game_help);
    }


    //refresh acct balance
    public void do_refresh(View view) {
        ImageButton refresh_button = (ImageButton) findViewById(R.id.refresh_button);
        refresh_button.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_forever));
        //
        TextView balance_view = (TextView) findViewById(R.id.finney_balance);
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(150);
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        balance_view.startAnimation(anim);
        //
        if (!Payment_Processor.has_message(this))
            Payment_Processor.refresh_balance(this, context);
    }

    private void set_button_strings(boolean show) {
        int vis_flag = show ? View.VISIBLE : View.INVISIBLE;
        Button practice_button = (Button)findViewById(R.id.play_for_practice);
        String practice_str = getResources().getString(R.string.play_for_practice);
        practice_str = practice_str.replace("WAGER", max_raises[0] + " Finney");
        practice_button.setText(practice_str);
        practice_button.setVisibility(vis_flag);
        Button real_button = (Button)findViewById(R.id.play_for_real);
        String real_str = getResources().getString(R.string.play_for_real);
        real_str = real_str.replace("WAGER", max_raises[1] + " Finney");
        real_button.setText(real_str);
        real_button.setVisibility(vis_flag);
        Button pro_button = (Button)findViewById(R.id.play_with_pros);
        String pro_str = getResources().getString(R.string.play_with_pros);
        pro_str = pro_str.replace("WAGER", max_raises[2] + " Finney");
        pro_button.setText(pro_str);
        pro_button.setVisibility(vis_flag);
        Button new_group_button = (Button)findViewById(R.id.play_new_group);
        new_group_button.setVisibility(vis_flag);
        if (saved_group_cnt > 0 || !show) {
            Button old_group_button = (Button) findViewById(R.id.play_old_group);
            old_group_button.setVisibility(vis_flag);
        }
    }

  private void dsp_balance() {
        long szabo_balance = preferences.getLong("balance", 0);
        long finney_balance = (szabo_balance + Util.SZABO_PER_FINNEY/2) / Util.SZABO_PER_FINNEY;
        TextView balance_view = (TextView) findViewById(R.id.finney_balance);
        String balance_str = String.format("%7d", finney_balance) + " Finney";
        balance_view.setText(String.valueOf(balance_str));
        //TextView eth_balance_view = (TextView) findViewById(R.id.eth_balance);
        //eth_balance_view.setVisibility(View.GONE);
        /*
        float usd_balance = balance * price;
        String usd_balance_str = String.format("%2.02f", usd_balance) + " USD";
        usd_balance_view.setText(String.valueOf(usd_balance_str));
        */
    }

  
    public void do_play_for_practice(View view) {
        if (chk_tx_err_abort_play())
            return;
        int bal_min_wager_multiple = (int)getResources().getInteger(R.integer.bal_min_wager_multiple);
        long szabo_balance = preferences.getLong("balance", 0);
        long finney_balance = (szabo_balance + Util.SZABO_PER_FINNEY/2) / Util.SZABO_PER_FINNEY;
        if (finney_balance < bal_min_wager_multiple * max_raises[0]) {
            String msg = getResources().getString(R.string.new_game_bal_too_low);
            msg = msg.replace("BAL_MULTIPLE", String.valueOf(bal_min_wager_multiple));
            msg = msg.replace("MIN_WAGER", String.valueOf(max_raises[0]));
            msg = msg.replace("WHAT_TO_DO", getResources().getString(R.string.get_more_finney));
            show_ok_dialog(getResources().getString(R.string.balance_too_low_title), msg);
            return;
        }
        if (refresh_winnings_timer != null) {
            refresh_winnings_timer.cancel();
            refresh_winnings_timer = null;
        }
        Intent intent = new Intent(this, SelectOpponentActivity.class);
        intent.putExtra("PLAYING_MSG", getResources().getString(R.string.at_the_started_table));
        intent.putExtra("MIN_WAGER", max_raises[0]);
        intent.putExtra("MAX_WAGER", max_raises[0]);
        startActivity(intent);
    }
    public void do_play_for_real(View view) {
        if (chk_tx_err_abort_play())
            return;
        int bal_min_wager_multiple = (int)getResources().getInteger(R.integer.bal_min_wager_multiple);
        long szabo_balance = preferences.getLong("balance", 0);
        long finney_balance = (szabo_balance + Util.SZABO_PER_FINNEY/2) / Util.SZABO_PER_FINNEY;
        if (finney_balance < bal_min_wager_multiple * max_raises[1]) {
            String msg = getResources().getString(R.string.new_game_bal_too_low);
            msg = msg.replace("BAL_MULTIPLE", String.valueOf(bal_min_wager_multiple));
            msg = msg.replace("MIN_WAGER", String.valueOf(max_raises[1]));
            msg = msg.replace("WHAT_TO_DO", getResources().getString(R.string.go_to_a_different_table));
            show_ok_dialog(getResources().getString(R.string.balance_too_low_title), msg);
            return;
        }
        if (refresh_winnings_timer != null) {
            refresh_winnings_timer.cancel();
            refresh_winnings_timer = null;
        }
        Intent intent = new Intent(this, SelectOpponentActivity.class);
        intent.putExtra("PLAYING_MSG", getResources().getString(R.string.at_the_intermediate_table));
        intent.putExtra("MIN_WAGER", max_raises[1]);
        intent.putExtra("MAX_WAGER", max_raises[1]);
        startActivity(intent);
    }
    public void do_play_with_pros(View view) {
        if (chk_tx_err_abort_play())
            return;
        int bal_min_wager_multiple = (int)getResources().getInteger(R.integer.bal_min_wager_multiple);
        long szabo_balance = preferences.getLong("balance", 0);
        long finney_balance = (szabo_balance + Util.SZABO_PER_FINNEY/2) / Util.SZABO_PER_FINNEY;
        if (finney_balance < bal_min_wager_multiple * max_raises[2]) {
            String msg = getResources().getString(R.string.new_game_bal_too_low);
            msg = msg.replace("BAL_MULTIPLE", String.valueOf(bal_min_wager_multiple));
            msg = msg.replace("MIN_WAGER", String.valueOf(max_raises[2]));
            msg = msg.replace("WHAT_TO_DO", getResources().getString(R.string.go_to_a_different_table));
            show_ok_dialog(getResources().getString(R.string.balance_too_low_title), msg);
            return;
        }
        if (refresh_winnings_timer != null) {
            refresh_winnings_timer.cancel();
            refresh_winnings_timer = null;
        }
        Intent intent = new Intent(this, SelectOpponentActivity.class);
        intent.putExtra("PLAYING_MSG", getResources().getString(R.string.at_the_high_roller_table));
        intent.putExtra("MIN_WAGER", max_raises[2]);
        intent.putExtra("MAX_WAGER", max_raises[2]);
        startActivity(intent);
    }

    public void do_play_new_group(View view) {
        if (chk_tx_err_abort_play())
            return;
        int bal_min_wager_multiple = (int)getResources().getInteger(R.integer.bal_min_wager_multiple);
        long szabo_balance = preferences.getLong("balance", 0);
        long finney_balance = (szabo_balance + Util.SZABO_PER_FINNEY/2) / Util.SZABO_PER_FINNEY;
        if (finney_balance < bal_min_wager_multiple * max_raises[0]) {
            String msg = getResources().getString(R.string.new_game_bal_too_low);
            msg = msg.replace("BAL_MULTIPLE", String.valueOf(bal_min_wager_multiple));
            msg = msg.replace("MIN_WAGER", getResources().getString(R.string.minimum_is) + " " + String.valueOf(max_raises[0]));
            msg = msg.replace("WHAT_TO_DO", getResources().getString(R.string.get_more_finney));
            show_ok_dialog(getResources().getString(R.string.balance_too_low_title), msg);
            return;
        }
        EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        InputFilter filter = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                String filtered = "";
                boolean do_replace = false;
                for (int i = start; i < end; i++) {
                    char character = source.charAt(i);
                    if (Character.isWhitespace(character) || character == '&' || character == '?' ||
                            character == '\'' || character == '"' || character == ':' || character == '/' ||
                            character == '\\' || character == '%' || character == ',' || character == '|')
                        do_replace = true;
                    else
                        filtered += character;
                }
                return(do_replace ? filtered : null);
            }

        };
        input.setFilters(new InputFilter[] { filter });
        NewGameActivity.Handle_Ask_Group_Name handle_ask_group_name = new NewGameActivity.Handle_Ask_Group_Name(input);
        android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
	    String title = getResources().getString(R.string.new_game_new_group_title);
	    String msg =  getResources().getString(R.string.new_game_new_group);
        alert_dialog_builder.setTitle(title);
        alert_dialog_builder.setMessage(msg);
        alert_dialog_builder.setPositiveButton(getResources().getString(R.string.OK), handle_ask_group_name);
	    alert_dialog_builder.setNegativeButton(getResources().getString(R.string.Cancel), handle_ask_group_name);
        alert_dialog_builder.setView(input);
        android.support.v7.app.AlertDialog dialog = alert_dialog_builder.create();
        dialog.show();
    }


    public void do_play_old_group(View view) {
        if (chk_tx_err_abort_play())
            return;
        int bal_min_wager_multiple = (int)getResources().getInteger(R.integer.bal_min_wager_multiple);
        long szabo_balance = preferences.getLong("balance", 0);
        long finney_balance = (szabo_balance + Util.SZABO_PER_FINNEY/2) / Util.SZABO_PER_FINNEY;
        if (finney_balance < bal_min_wager_multiple * max_raises[0]) {
            String msg = getResources().getString(R.string.new_game_bal_too_low);
            msg = msg.replace("BAL_MULTIPLE", String.valueOf(bal_min_wager_multiple));
            msg = msg.replace("MIN_WAGER", getResources().getString(R.string.minimum_is) + " " + String.valueOf(max_raises[0]));
            msg = msg.replace("WHAT_TO_DO", getResources().getString(R.string.get_more_finney));
            show_ok_dialog(getResources().getString(R.string.balance_too_low_title), msg);
            return;
        }
        NewGameActivity.Handle_Old_Group_Name handle_old_group_name = new NewGameActivity.Handle_Old_Group_Name();
        android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
        String title = getResources().getString(R.string.new_game_old_group_title);
        String msg =  getResources().getString(R.string.new_game_old_group);
        alert_dialog_builder.setTitle(title);
        alert_dialog_builder.setMessage(msg);
        alert_dialog_builder.setPositiveButton(getResources().getString(R.string.OK), handle_old_group_name);
        alert_dialog_builder.setNegativeButton(getResources().getString(R.string.Cancel), handle_old_group_name);
        //
        for (int i = 0; i < saved_group_names.length - 1; ++i) {
            System.out.println("saved_group_name" + i + " = " + saved_group_names[i]);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, saved_group_names);
        Spinner dropdown = new Spinner(context);
        dropdown.setLayoutParams(new LinearLayout.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
        dropdown.setAdapter(adapter);
        dropdown.setSelection(0);
        dropdown.setOnItemSelectedListener(handle_old_group_name);
        alert_dialog_builder.setView(dropdown);
        //
        android.support.v7.app.AlertDialog dialog = alert_dialog_builder.create();
        dialog.show();
    }



    //abort one of the do_play functions if a tx error occurred recently. if aborting, then displays a little message and kicks off
    //a refresh_balance process, which will clear the error upon completion
    private boolean chk_tx_err_abort_play() {
        boolean tx_err_occurred = preferences.getBoolean("tx_err_occurred", false);
        if (tx_err_occurred) {
            Util.show_err(getBaseContext(), getResources().getString(R.string.recent_err_wait), 5);
            if (!Payment_Processor.has_message(this))
                Payment_Processor.refresh_balance(this, context);
            return (true);
        }
        return (false);
    }

    private class Handle_Ask_Group_Name implements DialogInterface.OnClickListener {
      final EditText input;
      Handle_Ask_Group_Name(final EditText input) {
	this.input = input;
      }
      public void onClick(DialogInterface dialog, int id) {
    	switch (id) {
        case BUTTON_NEGATIVE:
            dialog.cancel();
            break;
	    case BUTTON_POSITIVE:
	        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
	        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
	        String group_name = input.getText().toString().toUpperCase();
	        dialog.cancel();
            //save the new group to the list of saved groups
            boolean found = false;
            if (saved_group_names != null) {
                for (int i = 0; i < saved_group_names.length && !found; ++i) {
                    if (group_name.equals(saved_group_names[i]))
                        found = true;
                }
            }
            if (!found) {
                SharedPreferences.Editor preferences_editor = preferences.edit();
                if (saved_group_names != null) {
                    int no_group_names = Math.min(MAX_SAVED_GROUP_NAMES, saved_group_names.length + 1);
                    for (int i = no_group_names - 2; i >= 0; --i) {
                        String key = "saved_group_name" + (i + 1);
                        preferences_editor.putString(key, saved_group_names[i]);
                    }
                }
                preferences_editor.putString("saved_group_name0", group_name);
                preferences_editor.apply();
                context.recreate();
            }
            //
            if (!group_name.isEmpty()) {
                if (refresh_winnings_timer != null) {
                    refresh_winnings_timer.cancel();
                    refresh_winnings_timer = null;
                }
                Intent intent = new Intent(context, SelectOpponentActivity.class);
                //the playing message will be displayed when asking the user to propose a wager. the message will be:
                //"You are playing PLAYING_MSG"
                intent.putExtra("PLAYING_MSG", getResources().getString(R.string.with_group) + " " + group_name);
                intent.putExtra("MIN_WAGER", max_raises[0]);
                intent.putExtra("MAX_WAGER", max_raises[2]);
                startActivity(intent);
            }
	        break;
	    }
      }
    }

    private class Handle_Old_Group_Name implements DialogInterface.OnClickListener, AdapterView.OnItemSelectedListener {
        int group_selected = 0;
        Handle_Old_Group_Name() { }
        public void onClick(DialogInterface dialog, int id) {
            switch (id) {
                case BUTTON_NEGATIVE:
                    dialog.cancel();
                    break;
                case BUTTON_POSITIVE:
                    String group_name = saved_group_names[group_selected].toUpperCase();
                    dialog.cancel();
                    if (!group_name.isEmpty()) {
                        if (refresh_winnings_timer != null) {
                            refresh_winnings_timer.cancel();
                            refresh_winnings_timer = null;
                        }
                        Intent intent = new Intent(context, SelectOpponentActivity.class);
                        //the playing message will be displayed when asking the user to propose a wager. the message will be:
                        //"You are playing PLAYING_MSG"
                        intent.putExtra("PLAYING_MSG", getResources().getString(R.string.with_group) + " " + group_name);
                        intent.putExtra("MIN_WAGER", max_raises[0]);
                        intent.putExtra("MAX_WAGER", max_raises[2]);
                        startActivity(intent);
                    }
                    break;
            }
        }
        @Override
        public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
            System.out.println("you selected " + position);
            group_selected = position;
        }
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }


        private void show_ok_dialog(int title_resource, int msg_resource) {
            String title = getResources().getString(title_resource);
            String msg = getResources().getString(msg_resource);
            show_ok_dialog(title, msg);
        }
        private void show_ok_dialog(String title, String msg) {
        android.support.v7.app.AlertDialog.Builder alertDialogBuilder = new android.support.v7.app.AlertDialog.Builder(context);
        alertDialogBuilder.setTitle(title);
        alertDialogBuilder.setMessage(msg);
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


    private void show_html_alert(int title_resource, int msg_resource) {
        String title = getResources().getString(title_resource);
        String msg_w_html = getResources().getString(msg_resource);
        show_html_alert(title, msg_w_html);
    }
    private void show_html_alert(String title, String msg_w_html) {
        AlertDialog.Builder alert_dialog_builder = new AlertDialog.Builder(context);
        Spanned msg = Html.fromHtml(msg_w_html);
        alert_dialog_builder.setTitle(title);
        alert_dialog_builder.setMessage(msg);
        alert_dialog_builder.setCancelable(false);
        alert_dialog_builder.setPositiveButton(getResources().getString(R.string.OK),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert_dialog = alert_dialog_builder.create();
        alert_dialog.show();
    }

    private void show_err_and_exit_dialog(String title, String msg, final boolean do_exit) {
        android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
        alert_dialog_builder.setTitle(title);
        alert_dialog_builder.setMessage(msg);
        alert_dialog_builder.setCancelable(false);
        alert_dialog_builder.setNeutralButton(getResources().getString(R.string.OK),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                        if (do_exit)
                            finish();
                    }
                });
        android.support.v7.app.AlertDialog dialog;
        dialog = alert_dialog_builder.create();
        dialog.show();
    }

    
    public void handle_http_rsp(String callback, String rsp) {
        if (callback.equals("table_parms")) {
            if (toast != null)
                toast.cancel();
            if (rsp.isEmpty()) {
                (toast = Toast.makeText(context, getResources().getString(R.string.error_check_connection), Toast.LENGTH_LONG)).show();
                String title = getResources().getString(R.string.no_internet_title);
                String msg = getResources().getString(R.string.no_internet);
                String error = getResources().getString(R.string.failed_to_retrieve_table_parms);
                msg = msg.replace("ERROR", error);
                show_err_and_exit_dialog(title, msg, true);
            } else {
                int idx = 0;
                String status = Util.json_parse(rsp, "status");
                String msg = Util.json_parse(rsp, "msg");
                String title = Util.json_parse(rsp, "title");
                for (int i = 0; i < 3; ++i) {
                    if (rsp.contains("table" + i)) {
                        idx = rsp.indexOf("table") + 6;
                        rsp = rsp.substring(idx);
                    } else {
                        System.out.println("error parsing table parms, looking for table " + i + ": " + rsp);
                        break;
                    }
                    int max_raise = 0;
                    String maxraise_str = Util.json_parse(rsp, "maxraise");
                    try {
                        max_raise = Integer.valueOf(maxraise_str);
                    } catch (NumberFormatException e) {
                        System.out.println("error parsing table parms, table " + i + ": " + rsp);
                    }
                    max_raises[i] = max_raise;
                }
                if (max_raises[0] <= 0 || max_raises[1] <= 0 || max_raises[2] <= 0) {
                    if (msg.isEmpty())
                        msg = getResources().getString(R.string.error_parsing_table_parms);
                    if (title.isEmpty())
                        title = getResources().getString(R.string.Server_Error);
                    show_err_and_exit_dialog(title, msg, true);
                } else {
		            set_button_strings(true);
                }
            }
            return;
        }
        return;
    }


    //this is the callback from Payment_Processor
    public boolean payment_result(boolean ok, String txid, long size_wei, String client_data, String error) {
        System.out.println("NewGameActivity::payment_result: Hey! we should never be here!");
        return true;
    }
    public void interim_payment_result(long size_wei, String client_data, String msg) {
        System.out.println("Hey! we should never be here!");
    }
    public void balance_result(boolean ok, long balance, String error) {
        System.out.println("NewGameActivity::balance_result: completed. balance = " + balance);
        Message message = message_handler.obtainMessage(HANDLE_BALANCE_CALLBACK, ok ? 1 : 0, 0);
        message.sendToTarget();
    }
    public void interim_balance_result(String msg) {
        Message message = message_handler.obtainMessage(HANDLE_INTERIM_CALLBACK, 0, 0, msg);
        message.sendToTarget();
    }
}

