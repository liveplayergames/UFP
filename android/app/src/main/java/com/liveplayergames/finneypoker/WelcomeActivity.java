    package com.liveplayergames.finneypoker;

    import android.Manifest;
    import android.content.Context;
    import android.content.DialogInterface;
    import android.content.Intent;
    import android.content.SharedPreferences;
    import android.content.pm.PackageManager;
    import android.os.Bundle;
    import android.os.CountDownTimer;
    import android.os.Handler;
    import android.os.Looper;
    import android.os.Message;
    import android.support.v4.app.ActivityCompat;
    import android.support.v4.content.ContextCompat;
    import android.support.v4.util.Pair;
    import android.support.v7.app.AlertDialog;
    import android.support.v7.app.AppCompatActivity;
    import android.support.v7.widget.Toolbar;
    import android.telephony.TelephonyManager;
    import android.text.InputFilter;
    import android.text.InputType;
    import android.text.Spanned;
    import android.util.DisplayMetrics;
    import android.view.Menu;
    import android.view.MenuInflater;
    import android.view.MenuItem;
    import android.view.View;
    import android.view.animation.AlphaAnimation;
    import android.view.animation.Animation;
    import android.view.animation.AnimationUtils;
    import android.view.inputmethod.InputMethodManager;
    import android.widget.EditText;
    import android.widget.FrameLayout;
    import android.widget.ImageButton;
    import android.widget.ImageView;
    import android.widget.TextView;
    import android.widget.Toast;

    import org.ethereum.crypto.ECKey;
    import org.spongycastle.util.encoders.Hex;

    import java.util.UUID;

    import static android.content.DialogInterface.BUTTON_POSITIVE;

    /**
     * Created by dbrosen on 12/14/16.
     */

    public class WelcomeActivity extends AppCompatActivity implements HTTP_Query_Client, Payment_Processor_Client {

        private String acct_addr = "";
        private String private_key = "";
        private SharedPreferences preferences;
        private FrameLayout overlay_frame_layout;
        private WelcomeActivity context;
        private String username = null;
        private String decrypt_key = null;
        private String device_id = null;
        private Toast toast = null;
        private long got_balance_sec = 0;
        private boolean we_are_dead = false;
        private static final int REQUEST_CODE_ASK_PERMISSIONS = 123;
        private android.support.v7.app.AlertDialog dialog = null;
        private Handler message_handler = null;
        private long splash_graphic_sec;
        private long etherscan_io_block_no = -1;
        private long etherchain_org_block_no = -2;

        //these defines are for our handle_message fcn, which displays dialogs on behalf of other threads (payment_processor)
        private static final int HANDLE_BALANCE_CALLBACK = 1;
        private static final int HANDLE_INTERIM_CALLBACK = 2;
        private static final int SHOW_ERR_AND_EXIT = 3;
        private static final int SHOW_MOTD = 4;
        private static final int SHOW_FINNEY_GIVEAWAY = 5;
        private static final int ASK_USERNAME = 6;
        private static final int SHOW_WELCOME_DIALOG = 7;
        private static final int SHOW_ETH_SITES_DIALOG = 8;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            overlay_frame_layout = new FrameLayout(getApplicationContext());
            setContentView(overlay_frame_layout);
            View activity_welcome_view = getLayoutInflater().inflate(R.layout.activity_welcome, overlay_frame_layout, false);
            setContentView(activity_welcome_view);
            Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
            String app_name = getResources().getString(R.string.app_name);
            toolbar.setTitle(app_name);
            toolbar.setBackgroundResource(R.color.color_toolbar);
            setSupportActionBar(toolbar);
            context = this;
            String app_uri = getResources().getString(R.string.app_uri);
            preferences = getSharedPreferences(app_uri, MODE_PRIVATE);
            username = preferences.getString("username", "");
            if (!username.isEmpty())
                toolbar.setSubtitle(username);
	        show_splash_graphic();

            if (BuildConfig.DEBUG) {
	            DisplayMetrics metrics = new DisplayMetrics();
	            getWindowManager().getDefaultDisplay().getMetrics(metrics);
	            int width =  metrics.widthPixels;
	            int height = metrics.heightPixels;
	            int dpi = metrics.densityDpi;
	            System.out.println("width = " + width * 160 / dpi + " dip");
	            System.out.println("height = " + height * 160 / dpi + " dip");
	        }
	    
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
                                TextView balance_view = (TextView) findViewById(R.id.balance);
                                balance_view.clearAnimation();
                				hide_splash_graphic();
                                SharedPreferences.Editor preferences_editor = preferences.edit();
                                preferences_editor.putBoolean("tx_err_occurred", false);
                                preferences_editor.apply();
                                if (toast != null)
                                    toast.cancel();
                                String msg = getResources().getString(R.string.acct_up_to_date);
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                            } else {
			                    String title = getResources().getString(R.string.no_internet_title);
				                String msg = getResources().getString(R.string.no_internet);
				                String error = getResources().getString(R.string.cant_read_balance);
				                msg = msg.replace("ERROR", error);
				                show_err_and_exit_dialog(title, msg);
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
                        //
                        case SHOW_ERR_AND_EXIT: {
                            Pair<String, String> tandm = (Pair<String, String>) message.obj;
                            if (toast != null)
                                toast.cancel();
                            show_err_and_exit_dialog(tandm.first, tandm.second);
                            break;
                        }
                        //
                        case SHOW_MOTD: {
                            String msg = (String) message.obj;
                            show_motd(msg);
                            break;
                        }
                        //
                        case SHOW_FINNEY_GIVEAWAY: {
                            Pair<String, String> tandm = (Pair<String, String>) message.obj;
                            if (toast != null)
                                toast.cancel();
                            show_finney_giveaway(tandm.first, tandm.second);
                            break;
                        }
                        //
                        case ASK_USERNAME: {
                            String msg = (String) message.obj;
                            ask_username(msg);
                            break;
                        }
                        //
                        case SHOW_WELCOME_DIALOG: {
                            show_welcome_dialog();
                            break;
                        }
                        //
                        case SHOW_ETH_SITES_DIALOG: {
                            if (!we_are_dead) {
                                Pair<Long, Long> scanandchain = (Pair<Long, Long>) message.obj;
                                long etherscan_io_block_no = scanandchain.first.longValue();
                                long etherchain_org_block_no = scanandchain.second.longValue();
                                if (toast != null)
                                    toast.cancel();
                                String toast_msg_pre = getResources().getString(R.string.partner_sites_down);
                                (toast = Toast.makeText(context, toast_msg_pre +
                                        "\n" + etherscan_io_block_no + " != " + etherchain_org_block_no, Toast.LENGTH_LONG)).show();
                                String title = getResources().getString(R.string.partner_sites_title);
                                String msg = getResources().getString(R.string.partner_sites_w_retry);
                                String error = "etherscan.io block number, " + etherscan_io_block_no + " != etherchain.org block number, " + etherchain_org_block_no;
                                msg = msg.replace("ERROR", error);
                                android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
                                alert_dialog_builder.setTitle(title);
                                alert_dialog_builder.setMessage(msg);
                                alert_dialog_builder.setCancelable(false);
                                String try_again = getResources().getString(R.string.TRY_AGAIN);
                                alert_dialog_builder.setPositiveButton(try_again, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,int id) {
                                        dialog.cancel();
                                        chk_eth_sites_are_ok();
                                    }
                                });
                                String exit = getResources().getString(R.string.EXIT);
                                alert_dialog_builder.setNeutralButton(exit,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog,int id) {
                                                dialog.cancel();
                                                //ensure that this is the last dialog we ever (attempt to) show
                                                we_are_dead = true;
                                                finish();
                                            }
                                        });
                                dialog = alert_dialog_builder.create();
                                dialog.show();
                            }
                            break;
                        }
                    }
                }
            };
        }

        //returns false => no options menu
        public boolean onCreateOptionsMenu(Menu menu) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.welcome_options, menu);
            return(true);
        }

        //returns false => no options menu
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.new_username:
                    ask_username(getResources().getString(R.string.enter_username));
                    return true;
                case R.id.welcome:
                    show_welcome_dialog();
                    return true;
                case R.id.about:
                    show_about_dialog();
                    return true;
                case R.id.help: {
                    do_help(null);
                    return true;
                }
                default:
                    return super.onOptionsItemSelected(item);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (dialog != null && dialog.isShowing()) {
                dialog.cancel();
            }
        }

        public void onStop() {
            if (toast != null)
                toast.cancel();
            //if we were waiting to update balance, no need to keep that request queued anymore
            Payment_Processor.cancel_messages(this);
            super.onStop();
        }


        /* ======================================================================================================================
        initialization sequence;
            onResume: if
                    got balance recently          never got balance (or long ago)
                    ---------------------   -     -----------------
                    => check_registration   =>    eth_sites_are_ok

            eth_sites_are_ok => handle_http_rsp => verify_eth_sites_are_ok =>

            check_registration: if
                    have device id           don't have device id
                    --------------------     ----------------------
                    => register_username     => show_welcome_dialog

            show_welcome_dialog => inline handler =>

            get_device_id_and_username =>

            ask_username => Handle_Ask_Username =>

            register_username: if
                    registered recently       have not registered recently
                    ---------------------     ---------------------
                    => last_part_of_init      register => handle_http_rsp:
                                                          if
                                                          giveaway requested        no giveaway requested
                                                         ----------------------     ---------------------
                                                         =>show_finney_giveaway     => get_motd

            show_finney_giveaway => inline handler =>

            get_motd: if
                    got balance recently  got_balance_sec < 2hrs ago
                    ----------------      ------------------------------
                    last_part_of_init     get_motd => handle_http_rsp =>

            last_part_of_init
        ====================================================================================================================== */

        public void onResume() {
            super.onResume();  // Always call the superclass method first
            device_id = preferences.getString("device_id", "");
            username = preferences.getString("username", "");
            acct_addr = preferences.getString("acct_addr", "");
            private_key = preferences.getString("key", "");
            long now_sec = System.currentTimeMillis() / 1000;
            if (got_balance_sec > now_sec - 7200)
                check_registration();
            else
                chk_eth_sites_are_ok();
        }

        //verifiy that etherchain.org and etherscan.io are up, and in agreement about the current block number.
        //i added this after experiencing that etherchain.org was up (responding), but not updating. after wasting
        //several hours debugging my code, i decided to protect from such an occurrance...
        private void chk_eth_sites_are_ok() {
            System.out.println("chk_eth_sites_are_ok");
            etherscan_io_block_no = -1;
            etherchain_org_block_no = -2;
            String etherchain_org_URL = "https://etherchain.org/api/blocks/count";
            String etherchain_org_parms[] = new String[2];
            etherchain_org_parms[0] = etherchain_org_URL;
            etherchain_org_parms[1] = "etherchain_org_block_count";
            System.out.println("chk_eth_sites_are_ok: initiate read from etherchain.org");
            new HTTP_Query_Task(this, context).execute(etherchain_org_parms);
            String etherscan_io_URL = "https://api.etherscan.io/api?module=proxy&action=eth_blockNumber";
            String etherscan_io_parms[] = new String[2];
            etherscan_io_parms[0] = etherscan_io_URL;
            etherscan_io_parms[1] = "etherscan_io_block_count";
            System.out.println("chk_eth_sites_are_ok: initiate read from etherscan.io");
            new HTTP_Query_Task(this, context).execute(etherscan_io_parms);
        }
        //note: we are called from handle_http_response, so we go to the message looper to display dialogs
        private void verify_eth_sites_are_ok() {
            System.out.println("verify_eth_sites_are_ok: have " + etherscan_io_block_no + " and " + etherchain_org_block_no);
            if (etherscan_io_block_no == 0 || etherchain_org_block_no == 0) {
                if (toast != null)
                    toast.cancel();
                String toast_msg = getResources().getString(R.string.network_err);
                (toast = Toast.makeText(context, toast_msg, Toast.LENGTH_LONG)).show();
		        String title = getResources().getString(R.string.no_internet_title);
                String msg = getResources().getString(R.string.no_internet);
                String error = getResources().getString(R.string.block_nos_err);
                msg = msg.replace("ERROR", error);
                System.out.println("calling show_err_and_exit_dialog ...");
                Pair<String, String> tandm = new Pair<>(title, msg);
                Message message = message_handler.obtainMessage(SHOW_ERR_AND_EXIT, 0, 0, tandm);
                message.sendToTarget();
                return;
            }
            if (etherscan_io_block_no < 0 || etherchain_org_block_no < 0) {
                //wait until we get results from both sites
                System.out.println("waiting for the other of etherscan.io or etherchain.org...");
                return;
            }
            if (Math.abs(etherscan_io_block_no - etherchain_org_block_no) > 5) {
                System.out.println("verify_eth_sites_are_ok: calling SHOW_ETH_SITES_DIALOG");
                Pair<Long, Long> scanandchain = new Pair<>(new Long(etherscan_io_block_no), new Long(etherchain_org_block_no));
                Message message = message_handler.obtainMessage(SHOW_ETH_SITES_DIALOG, 0, 0, scanandchain);
                message.sendToTarget();
                return;
            }
            check_registration();
        }

        //note: we can be called from handle_http_response, so we go to the message looper to display dialogs
        private void check_registration() {
            System.out.println("check_registration");
            if (device_id.isEmpty() || username.isEmpty() || private_key.isEmpty() || acct_addr.isEmpty()) {
                Message message = message_handler.obtainMessage(SHOW_WELCOME_DIALOG, 0, 0, null);
                message.sendToTarget();
            } else {
                String msg = getResources().getString(R.string.contacting_server);
    	        register_username(false, msg);
            }
        }

        private void show_welcome_dialog() {
            android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new AlertDialog.Builder(context);
            String app_name = getResources().getString(R.string.app_name);
            alert_dialog_builder.setTitle(app_name);
            alert_dialog_builder.setMessage(getResources().getString(R.string.welcome));
            alert_dialog_builder.setCancelable(false);
            String ok = getResources().getString(R.string.OK);
            alert_dialog_builder.setNeutralButton(ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            dialog.cancel();
                            get_device_id_and_username();
                        }
                    });
            dialog = alert_dialog_builder.create();
            dialog.show();
        }

        //this fcn gets the device-id and the username. the two are combineed, cuz in order to get the device id it might be necessary
        //to ask the user for permission. in that case there will be a handler that will call the fcn again.
        private void get_device_id_and_username() {
            device_id = preferences.getString("device_id", "");
            if (device_id.isEmpty()) {
                int has_write_permission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
                if (has_write_permission != PackageManager.PERMISSION_GRANTED) {
                    if (true || ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.READ_PHONE_STATE)) {
                        show_phone_state_permission_rationale();
                    } else {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_CODE_ASK_PERMISSIONS);
                    }
                    return;
                }
                get_device_id_guts();
            }
            acct_addr = preferences.getString("acct_addr", "");
            private_key = preferences.getString("key", "");
            if (private_key.isEmpty() || acct_addr.isEmpty()) {
                ECKey key = new ECKey();
                byte[] addr = key.getAddress();
                byte[] priv = key.getPrivKeyBytes();
                acct_addr = "0x" + Hex.toHexString(addr);
                private_key = Hex.toHexString(priv);
                System.out.println("Address     : " + acct_addr);
                System.out.println("Private Key : " + private_key);
                if (private_key.length() != 64) {
                    String msg_pre = getResources().getString(R.string.bad_private_key);
                    Util.show_err(getBaseContext(), msg_pre + " " + private_key.length(), 5);
                } else {
                    SharedPreferences.Editor preferences_editor = preferences.edit();
                    preferences_editor.putString("key", private_key);
                    preferences_editor.putString("acct_addr", acct_addr);
                    preferences_editor.putBoolean("acct_has_no_txs", true);
                    preferences_editor.apply();
                }
            }
            username = preferences.getString("username", "");
            if (username.isEmpty()) {
                ask_username(getResources().getString(R.string.enter_username));
            }
        }

        private void show_phone_state_permission_rationale() {
            WelcomeActivity.Handle_Show_Permission_Rationale handle_show_permission_rationale = new WelcomeActivity.Handle_Show_Permission_Rationale();
            android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
            alert_dialog_builder.setTitle(getResources().getString(R.string.device_id_title));
            alert_dialog_builder.setMessage(getResources().getString(R.string.device_id_rationale));
            String ok = getResources().getString(R.string.OK);
            alert_dialog_builder.setPositiveButton(ok, handle_show_permission_rationale);
            dialog = alert_dialog_builder.create();
            dialog.show();
        }

        private class Handle_Show_Permission_Rationale implements DialogInterface.OnClickListener {
            public void onClick(DialogInterface dialog, int id) {
                switch (id) {
                    case BUTTON_POSITIVE:
                        dialog.cancel();
                        ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_CODE_ASK_PERMISSIONS);
                        break;
                }
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            switch (requestCode) {
                case REQUEST_CODE_ASK_PERMISSIONS:
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        //call the wrapper fcn again, so it can get the device-id and the username
                        //this is not necessary, cuz the permission handler will call resume by itself...
                        // get_device_id_and_username();
                    } else {
                        String msg = getResources().getString(R.string.cant_reg_wo_device_id);
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        //nothing to do. user can just sit here, stuck on this screen...
                    }
                    break;
                default:
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }

        private void get_device_id_guts() {
            //generate unique id for this device / user
            final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
            final String tmDevice = "" + tm.getDeviceId();
            final String tmSerial = "" + tm.getSimSerialNumber();
            final String androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            UUID device_uuid = new UUID(androidId.hashCode(), ((long) tmDevice.hashCode() << 32) | tmSerial.hashCode());
            device_id = device_uuid.toString();
            SharedPreferences.Editor preferences_editor = preferences.edit();
            preferences_editor.putString("device_id", device_id);
            preferences_editor.apply();
        }

        private void ask_username(String msg) {
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
            WelcomeActivity.Handle_Ask_Username handle_ask_username = new WelcomeActivity.Handle_Ask_Username(input);
            android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
            alert_dialog_builder.setTitle(getResources().getString(R.string.enter_username_title));
            alert_dialog_builder.setMessage(msg);
            alert_dialog_builder.setPositiveButton(getResources().getString(R.string.OK), handle_ask_username);
            alert_dialog_builder.setView(input);
            dialog = alert_dialog_builder.create();
            dialog.show();
        }

        private class Handle_Ask_Username implements DialogInterface.OnClickListener {
            final EditText input;
            Handle_Ask_Username(final EditText input) {
                this.input = input;
            }
            public void onClick(DialogInterface dialog, int id) {
                switch (id) {
                    case BUTTON_POSITIVE:
                        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                        username = input.getText().toString();
                        dialog.cancel();
                        System.out.println("username = " + username + ", decrypt_key = " + decrypt_key);
                        register_username(true, getResources().getString(R.string.registering_username));
                        break;
                }
            }
        }

        private void register_username(boolean force, String msg) {
            System.out.println("register_username");
            long now_sec = System.currentTimeMillis() / 1000;
            long registered_sec = preferences.getLong("registered_sec", 0);
            if (!force && now_sec - registered_sec < 120) {
                last_part_of_init();
            } else {
                if (toast != null)
                    toast.cancel();
                (toast = Toast.makeText(context, msg, Toast.LENGTH_LONG)).show();
                String key_parm = "";
                decrypt_key = preferences.getString("decrypt_key", "");
                if (decrypt_key.isEmpty()) {
		            decrypt_key = Util.aes_key_generator();
                    if (!decrypt_key.isEmpty())
                        key_parm = "&key=" + decrypt_key;
                }
                String server = getResources().getString(R.string.player_server);
                String register_URL = server + "/register?id=" + device_id + "&username=" + username + "&addr=" + acct_addr + key_parm;
                String parms[] = new String[2];
                parms[0] = register_URL;
                parms[1] = "register";
                System.out.println("creating HTTP_Query_Task for register");
                new HTTP_Query_Task(this, context).execute(parms);
            }
        }

        private void get_motd() {
            long now_sec = System.currentTimeMillis() / 1000;
            if (got_balance_sec > now_sec - 7200) {
                last_part_of_init();
            } else {
                String server = getResources().getString(R.string.player_server);
                String register_URL = server + "/motd?id=" + device_id + "&version=" + BuildConfig.VERSION_NAME;
                String parms[] = new String[2];
                parms[0] = register_URL;
                parms[1] = "motd";
                System.out.println("creating HTTP_Query_Task for motd");
                new HTTP_Query_Task(this, context).execute(parms);
            }
        }

        private void show_motd(String msg) {
            android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
            String title = getResources().getString(R.string.motd_title);
            alert_dialog_builder.setTitle(title);
            alert_dialog_builder.setMessage(msg);
            alert_dialog_builder.setCancelable(false);
            alert_dialog_builder.setNeutralButton(getResources().getString(R.string.OK),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            dialog.cancel();
                            last_part_of_init();
                        }
                    });
            dialog = alert_dialog_builder.create();
            dialog.show();
        }


        public void handle_http_rsp(String callback, String rsp) {
          if (callback.equals("motd")) {
              if (toast != null)
                  toast.cancel();
              if (rsp.isEmpty()) {
                  String toast_msg = context.getResources().getString(R.string.error_check_connection);
                  (toast = Toast.makeText(context, toast_msg, Toast.LENGTH_LONG)).show();
                  String title = getResources().getString(R.string.no_internet_title);
                  String msg = getResources().getString(R.string.no_internet);
                  String error = "communication with game server failed";
                  msg = msg.replace("ERROR", error);
                  Pair<String, String> tandm = new Pair<>(title, msg);
                  Message message = message_handler.obtainMessage(SHOW_ERR_AND_EXIT, 0, 0, tandm);
                  message.sendToTarget();
              } else if (rsp.equals("no msg")) {
                  last_part_of_init();
              } else {
                  Message message = message_handler.obtainMessage(SHOW_MOTD, 0, 0, rsp);
                  message.sendToTarget();
              }
              return;
          }
          if (callback.equals("register")) {
              if (toast != null)
                    toast.cancel();
              if (rsp.isEmpty()) {
                    String toast_msg = context.getResources().getString(R.string.error_check_connection);
                    (toast = Toast.makeText(context, toast_msg, Toast.LENGTH_LONG)).show();
        		    String title = getResources().getString(R.string.no_internet_title);
		            String msg = getResources().getString(R.string.no_internet);
                    String error = context.getResources().getString(R.string.failed_to_register);
                    msg = msg.replace("ERROR", error);
                    Pair<String, String> tandm = new Pair<>(title, msg);
                    Message message = message_handler.obtainMessage(SHOW_ERR_AND_EXIT, 0, 0, tandm);
                    message.sendToTarget();
              } else if (rsp.contains("status")) {
                    System.out.println("rsp: " + rsp);
                    String status_str = Util.json_parse(rsp, "status");
                    String free_finney_amount = Util.json_parse(rsp, "free_finney_amount");
                    String free_finney_reason = Util.json_parse(rsp, "free_finney_reason");
                    String have_key = Util.json_parse(rsp, "have_key");
                    if (status_str.equals("ok")) {
                        long now_sec = System.currentTimeMillis() / 1000;
			            String old_decrypt_key = preferences.getString("decrypt_key", "");
                        SharedPreferences.Editor preferences_editor = preferences.edit();
                        if (!have_key.equals("true")) {
                            preferences_editor.putString("decrypt_key", "");
                        } else {
                            if (old_decrypt_key.isEmpty())
                                preferences_editor.putString("decrypt_key", decrypt_key);
                        }
                        preferences_editor.putString("username", username);
                        preferences_editor.putLong("registered_sec", now_sec);
                        preferences_editor.apply();
                        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
                        toolbar.setSubtitle(username);
                        if (!free_finney_amount.isEmpty()) {
                            Pair<String, String> tandm = new Pair<>(free_finney_amount, free_finney_reason);
                            Message message = message_handler.obtainMessage(SHOW_FINNEY_GIVEAWAY, 0, 0, tandm);
                            message.sendToTarget();
                        } else {
                            get_motd();
                        }
                        return;
                    } else {
                        String msg = getResources().getString(R.string.username_taken);
                        Message message = message_handler.obtainMessage(ASK_USERNAME, 0, 0, msg);
                        message.sendToTarget();
                    }
              }
              return;
            }
            //
            if (callback.equals("etherchain_org_block_count")) {
                //{"status": 1, "data": [ { "count": 3108899 } ] }
                etherchain_org_block_no = 0;
                String count_str = Util.json_parse(rsp, "count");
                if (!count_str.isEmpty()) {
                    try {
                        etherchain_org_block_no = Long.parseLong(count_str);
                    } catch (Exception e) {
                        System.out.println(e.toString() + " (count_str = " + count_str + ")");
                    }
                }
                //will continue to next step of initialization if we have results from both etherchain.org & etherscan.io
                verify_eth_sites_are_ok();
                return;
            }
            if (callback.equals("etherscan_io_block_count")) {
                //{"jsonrpc":"2.0","result":"0x2f796a","id":83}
                etherscan_io_block_no = 0;
                String count_str = Util.json_parse(rsp, "result");
                if (!count_str.isEmpty()) {
                    if (count_str.startsWith(("0x")))
                        count_str = count_str.substring(2);
                    try {
                        etherscan_io_block_no = Long.parseLong(count_str, 16);
                    } catch (Exception e) {
                        System.out.println(e.toString() + " (count_str = " + count_str + ")");
                    }
                }
                //will continue to next step of initialization if we have results from both etherchain.org & etherscan.io
                verify_eth_sites_are_ok();
            }
            return;
        }

        private void show_finney_giveaway(String free_finney_amount, String free_finney_reason) {
            android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
            String title = getResources().getString(R.string.giveaway_title);
            String msg = getResources().getString(R.string.giveaway);
            msg = msg.replace("FINNEY_AMOUNT", free_finney_amount).replace("REASON", free_finney_reason);
            alert_dialog_builder.setTitle(title);
            alert_dialog_builder.setMessage(msg);
            alert_dialog_builder.setCancelable(false);
            alert_dialog_builder.setNeutralButton(getResources().getString(R.string.OK),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            dialog.cancel();
                            get_motd();
                        }
                    });
            dialog = alert_dialog_builder.create();
            dialog.show();
        }

        //call this fcn after we have shown the welcome dialog, and ensured that we have a devoce id, username, private key, and addr.
        private void last_part_of_init() {
            System.out.println("last_part_of_init");
            if (toast != null)
                toast.cancel();
            long now_sec = System.currentTimeMillis() / 1000;
            long balance = preferences.getLong("balance", 0);
            long verified_balance = preferences.getLong("verified_balance", 0);
            long balance_refresh_sec = preferences.getLong("balance_refresh_sec", 0);
            System.out.println("balance = " + balance + "; verified_balance = " + verified_balance + "; now = " + now_sec + "; balance_refresh_sec = " + balance_refresh_sec);
            dsp_balance();
            if (balance == verified_balance && now_sec - balance_refresh_sec < 60) {
                got_balance_sec = now_sec;
                System.out.println("last_part_of_init: balance up-to-date");
                ImageButton refresh_button = (ImageButton) findViewById(R.id.refresh_button);
                refresh_button.clearAnimation();
                TextView balance_view = (TextView) findViewById(R.id.balance);
                balance_view.clearAnimation();
        		//we can clear the splash graphic as soon as the min display time is past
		        if (splash_graphic_sec != 0) {
                    System.out.println("last_part_of_init: balance up-to-date -- calling hide_splash_graphic");
                    hide_splash_graphic();
                }
            } else {
		        //we will clear the splash graphic when we get the refresh_balance callback
	            Animation anim = new AlphaAnimation(0.0f, 1.0f);
		        anim.setDuration(150);
		        anim.setStartOffset(20);
		        anim.setRepeatMode(Animation.REVERSE);
		        anim.setRepeatCount(Animation.INFINITE);
		        TextView balance_view = (TextView) findViewById(R.id.balance);
		        balance_view.startAnimation(anim);
                System.out.println("last_part_of_init: balance not up-to-date; refresh balance");
                Payment_Processor.refresh_balance(this, context);
            }
        }


        private void show_splash_graphic() {
            ImageView splash_graphic_view = (ImageView) findViewById(R.id.splash_graphic);
            splash_graphic_view.setVisibility(View.VISIBLE);
            ImageView refresh_view = (ImageView) findViewById(R.id.refresh);
            refresh_view.setVisibility(View.VISIBLE);
            refresh_view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_forever));
    	    splash_graphic_sec = System.currentTimeMillis() / 1000;
        }

        private void hide_splash_graphic() {
	        //we clear the splash graphic as soon as the min display time is past
	        if (splash_graphic_sec != 0) {
                long min_splash_graphic_duration = getResources().getInteger(R.integer.min_splash_graphic_duration);
                long now_sec = System.currentTimeMillis() / 1000;
                long remain_sec = Math.max(0, min_splash_graphic_duration - (now_sec - splash_graphic_sec));
	            splash_graphic_sec = 0;
	            if (remain_sec < 1) {
	                ImageView refresh_view = (ImageView) findViewById(R.id.refresh);
                    refresh_view.clearAnimation();
	                refresh_view.setVisibility(View.INVISIBLE);
            	    ImageView splash_graphic_view = (ImageView) findViewById(R.id.splash_graphic);
	                splash_graphic_view.setVisibility(View.INVISIBLE);
	            } else {
                    System.out.println("hide_splash_graphic: clear in " + remain_sec + " secs");
	                new CountDownTimer(remain_sec * 1000, 1000) {
		                public void onTick(long millisUntilFinished) {
		                }
		                public void onFinish() {
		                    ImageView refresh_view = (ImageView) findViewById(R.id.refresh);
                            refresh_view.clearAnimation();
		                    refresh_view.setVisibility(View.INVISIBLE);
		                    ImageView splash_graphic_view = (ImageView) findViewById(R.id.splash_graphic);
		                    splash_graphic_view.setVisibility(View.INVISIBLE);
		                }
		            }.start();
	            }
	        }
        }

        //refresh acct balance
        public void do_refresh(View view) {
            ImageButton refresh_button = (ImageButton) findViewById(R.id.refresh_button);
            refresh_button.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_forever));
            //
            TextView balance_view = (TextView) findViewById(R.id.balance);
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

        public void do_purchase(View view) {
            if (got_balance_sec > 0) {
                Intent intent = new Intent(this, PurchaseActivity.class);
                startActivity(intent);
            } else {
                String wait_msg = getResources().getString(R.string.wait_frefresh_bal);
                (toast = Toast.makeText(context, wait_msg, Toast.LENGTH_LONG)).show();
            }
        }

        public void do_play(View view) {
            if (got_balance_sec > 0) {
                Intent intent = new Intent(this, NewGameActivity.class);
                startActivity(intent);
            } else {
                String wait_msg = getResources().getString(R.string.wait_frefresh_bal);
                (toast = Toast.makeText(context, wait_msg, Toast.LENGTH_LONG)).show();
            }
        }

        public void do_share(View view) {
            if (got_balance_sec > 0) {
                Intent intent = new Intent(this, ShareActivity.class);
                startActivity(intent);
            } else {
                String wait_msg = getResources().getString(R.string.wait_frefresh_bal);
                (toast = Toast.makeText(context, wait_msg, Toast.LENGTH_LONG)).show();
            }
        }

        private void show_err_and_exit_dialog(String title, String msg) {
            if (we_are_dead)
                return;
            android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
            alert_dialog_builder.setTitle(title);
            alert_dialog_builder.setMessage(msg);
            alert_dialog_builder.setCancelable(false);
            alert_dialog_builder.setNeutralButton(getResources().getString(R.string.OK),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            dialog.cancel();
                            finish();
                        }
                    });
            //ensure that this is the last dialog we ever (attempt to) show
            we_are_dead = true;
            dialog = alert_dialog_builder.create();
            dialog.show();
        }

        private void show_about_dialog() {
            String app_name = getResources().getString(R.string.app_name);
            String msg = getResources().getString(R.string.about_ufp);
            msg = msg.replace("VERSION", BuildConfig.VERSION_NAME);
            String about = getResources().getString(R.string.About);
            String title = about + " " + app_name;
            show_ok_dialog(title, msg);
        }
        public void do_help(View view) {
            show_ok_dialog(R.string.welcome_help_title, R.string.welcome_help);
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


        public boolean payment_result(boolean ok, String txid, long size_wei, String client_data, String error) {
            System.out.println("Hey! we should never be here!");
            return true;
        }
        public void interim_payment_result(long size_wei, String client_data, String msg) {
            System.out.println("Hey! we should never be here!");
        }
        public void balance_result(boolean ok, long balance, String error) {
            got_balance_sec = System.currentTimeMillis() / 1000;
            System.out.println("payment processor completed. balance = " + balance);
            Message message = message_handler.obtainMessage(HANDLE_BALANCE_CALLBACK, ok ? 1 : 0, 0);
            message.sendToTarget();
        }
        public void interim_balance_result(String msg) {
            Message message = message_handler.obtainMessage(HANDLE_INTERIM_CALLBACK, 0, 0, msg);
            message.sendToTarget();
        }


        private void dsp_balance() {
            long szabo_balance = preferences.getLong("balance", 0);
            long finney_balance = (szabo_balance + Util.SZABO_PER_FINNEY/2) / Util.SZABO_PER_FINNEY;
            TextView balance_view = (TextView) findViewById(R.id.balance);
            String balance_str = String.format("%7d", finney_balance) + " Finney";
            balance_view.setText(String.valueOf(balance_str));
        }


    }
