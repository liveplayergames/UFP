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

import com.paypal.android.sdk.payments.PayPalConfiguration;
import com.paypal.android.sdk.payments.PayPalPayment;
import com.paypal.android.sdk.payments.PayPalService;
import com.paypal.android.sdk.payments.PaymentActivity;
import com.paypal.android.sdk.payments.PaymentConfirmation;

import org.json.JSONException;

import java.math.BigDecimal;

/**
 * Created by kaandoit on 2/4/17.
 */

public class PurchaseActivity extends AppCompatActivity implements HTTP_Query_Client {

    private SharedPreferences preferences;
    private FrameLayout overlay_frame_layout;
    private PurchaseActivity context;
    private android.support.v7.app.AlertDialog dialog = null;
    private String acct_addr = "";
    private String my_id = "";
    private Toast toast = null;
    private boolean purchase_sent = false;
    private class Purchase_Option {
        int price;
        int amount;
        float usd_price;
        public Purchase_Option(int price, int amount) {
            this.price = price;
            this.amount = amount;
            usd_price = (float)price / 100;
        }
    };
    private Purchase_Option purchase_options[] = null;
    //paypal related vars, defines
    /**
     * - Set to PayPalConfiguration.ENVIRONMENT_PRODUCTION to move real money.
     *
     * - Set to PayPalConfiguration.ENVIRONMENT_SANDBOX to use your test credentials
     * from https://developer.paypal.com
     *
     * - Set to PayPalConfiguration.ENVIRONMENT_NO_NETWORK to kick the tires
     * without communicating to PayPal's servers.
     */
    private static final String CONFIG_ENVIRONMENT = PayPalConfiguration.ENVIRONMENT_PRODUCTION;
    // note that these credentials will differ between live & sandbox environments.
    private static final String CONFIG_CLIENT_ID = "Ad2iDp5GN3NhptJS8tV6BOfa_Hvcz98G0uDx9hGK4lkTXly1ic8wlU-hIY2lVJsrUwlv74jdYyIrTKAV";
    private static final int REQUEST_CODE_PAYMENT = 1;
    private static PayPalConfiguration config = new PayPalConfiguration().environment(CONFIG_ENVIRONMENT).clientId(CONFIG_CLIENT_ID);

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
        purchase_sent = false;
        //
        //init paypal
        Intent intent = new Intent(this, PayPalService.class);
        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config);
        startService(intent);
    }


    //returns false => no options menu
    public boolean onCreateOptionsMenu(Menu menu) {
        return(false);
    }

    public void onResume() {
        super.onResume();  // Always call the superclass method first
        //only need to do this until the purchase has been sent. once we get back the
        //purchase response we will exit. but avoid re-retreiving the pricelist in case
        //we redraw the screen before we exit.
        if (!purchase_sent) {
            Button purchase_button0_view = (Button) findViewById(R.id.purchase_button0);
            purchase_button0_view.setVisibility(View.INVISIBLE);
            Button purchase_button1_view = (Button) findViewById(R.id.purchase_button1);
            purchase_button1_view.setVisibility(View.INVISIBLE);
            Button purchase_button2_view = (Button) findViewById(R.id.purchase_button2);
            purchase_button2_view.setVisibility(View.INVISIBLE);
            if (toast != null)
                toast.cancel();
            (toast = Toast.makeText(context, getResources().getString(R.string.retrieving_price_list), Toast.LENGTH_LONG)).show();
            String server = getResources().getString(R.string.player_server);
            String register_URL = server + "/pricelist" + "?id=" + my_id;
            String parms[] = new String[2];
            parms[0] = register_URL;
            parms[1] = "pricelist";
            new HTTP_Query_Task(this, context).execute(parms);
        }
    }

    @Override
    public void onDestroy() {
        // Stop service when done
        stopService(new Intent(this, PayPalService.class));
        super.onDestroy();
    }

    public void do_purchase0(View pressed) {
        do_purchase_common(purchase_options[0].usd_price, purchase_options[0].amount);
    }
    public void do_purchase1(View pressed) {
        do_purchase_common(purchase_options[1].usd_price, purchase_options[1].amount);
    }
    public void do_purchase2(View pressed) {
        do_purchase_common(purchase_options[2].usd_price, purchase_options[2].amount);
    }
    public void do_purchase_common(float price, int amount) {
        /*
         * PAYMENT_INTENT_SALE will cause the payment to complete immediately.
         * to include additional payment details and an item list, see getStuffToBuy() below.
         */
        PayPalPayment thingToBuy = new PayPalPayment(new BigDecimal(price), "USD", amount + " Finney", PayPalPayment.PAYMENT_INTENT_SALE);
        Intent intent = new Intent(context, com.paypal.android.sdk.payments.PaymentActivity.class);
        // send the same configuration for restart resiliency
        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config);
        intent.putExtra(PaymentActivity.EXTRA_PAYMENT, thingToBuy);
        startActivityForResult(intent, REQUEST_CODE_PAYMENT);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (toast != null)
            toast.cancel();
        if (requestCode == REQUEST_CODE_PAYMENT) {
            if (resultCode == Activity.RESULT_OK) {
                PaymentConfirmation confirm = data.getParcelableExtra(PaymentActivity.EXTRA_RESULT_CONFIRMATION);
                String state = "";
                String price = "";
                String desc = "";
                String id = "";
                if (confirm != null) {
                    try {
                        String confirmation = confirm.toJSONObject().toString(4);
                        //{ "client": { "environment": "live", "paypal_sdk_version": "2.15.2", "platform": "Android", product_name": "PayPal-Android-SDK" },
                        //  "response": { "create_time": "2017-02-04T23:16:11Z", "id": "PAY-3S891658KJ437352HLCLGBIQ", "intent": "sale", "state": "approved" },
                        // "response_type": "payment"
                        // }
                        state = Util.json_parse(confirmation, "state");
                        id = Util.json_parse(confirmation, "id");
                        String payment = confirm.getPayment().toJSONObject().toString(4);
                        //{ "amount": "1.9900000095367431640625", "currency_code": "USD", "short_description": "125 Finney", "intent": "sale" }
                        price = Util.json_parse(payment, "amount");
                        desc = Util.json_parse(payment, "short_description").replace(' ', '_');
                    } catch (JSONException e) {
                        System.out.println("an extremely unlikely failure occurred: " + e.toString());
                    }
                }
                if (!state.equals("approved")) {
                    (toast = Toast.makeText(context, getResources().getString(R.string.payment_declined), Toast.LENGTH_LONG)).show();
                    return;
                }
                purchase_sent = true;
                (toast = Toast.makeText(context, getResources().getString(R.string.forwarding_payment_confirmation), Toast.LENGTH_LONG)).show();
                String server = getResources().getString(R.string.player_server);
                String purchase_URL = server + "/paypal_purchase?paypal_id=" + id + "&addr=" + acct_addr + "&price=" + price + "&desc=" + desc + "&id=" + my_id;
                String parms[] = new String[2];
                parms[0] = purchase_URL;
                parms[1] = "purchase";
                new HTTP_Query_Task(this, context).execute(parms);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                (toast = Toast.makeText(context, getResources().getString(R.string.payment_cancelled), Toast.LENGTH_LONG)).show();
            } else if (resultCode == PaymentActivity.RESULT_EXTRAS_INVALID) {
                (toast = Toast.makeText(context, getResources().getString(R.string.payment_not_valid), Toast.LENGTH_LONG)).show();
            }
        }
    }

    public void handle_http_rsp(String callback, String rsp) {
        if (callback.equals("pricelist")) {
            if (toast != null)
                toast.cancel();
            if (rsp.isEmpty()) {
                (toast = Toast.makeText(context, getResources().getString(R.string.error_check_connection), Toast.LENGTH_LONG)).show();
                String title = getResources().getString(R.string.no_internet_title);
                String msg = getResources().getString(R.string.no_internet);
                String error = getResources().getString(R.string.failed_to_retrieve_price_list);
                msg = msg.replace("ERROR", error);
                show_err_and_exit_dialog(title, msg, true);
            } else {
                int idx = 0;
                purchase_options = new Purchase_Option[3];
                purchase_options[0] = purchase_options[1] = purchase_options[2] = null;
                String status = Util.json_parse(rsp, "status");
                String msg = Util.json_parse(rsp, "msg");
                String title = Util.json_parse(rsp, "title");
                for (int i = 0; i < 3; ++i) {
                    if (rsp.contains("option" + i)) {
                        idx = rsp.indexOf("option") + 7;
                        rsp = rsp.substring(idx);
                    } else {
                        System.out.println("error parsing pricelist, looking for option " + i + ": " + rsp);
                        break;
                    }
                    int price = 0;
                    int amount = 0;
                    String price_str = Util.json_parse(rsp, "price");
                    String amount_str = Util.json_parse(rsp, "amount");
                    try {
                        price = Integer.valueOf(price_str);
                        amount = Integer.valueOf(amount_str);
                    } catch (NumberFormatException e) {
                        System.out.println("error parsing pricelist, option " + i + ": " + rsp);
                    }
                    purchase_options[i] = new Purchase_Option(price, amount);
                }
                if (purchase_options[0] == null ||  purchase_options[1] == null || purchase_options[2] == null) {
                    if (msg.isEmpty())
                        msg = getResources().getString(R.string.error_parsing_price_list);
                    if (title.isEmpty())
                        title = getResources().getString(R.string.Server_Error);
                    show_err_and_exit_dialog(title, msg, true);
                } else {
                    String for_ = getResources().getString(R.string.for_price);
                    Button purchase_button0_view = (Button) findViewById(R.id.purchase_button0);
                    purchase_button0_view.setText(purchase_options[0].amount + " Finney " + for_ + " $" + String.format("%01.2f", purchase_options[0].usd_price));
                    purchase_button0_view.setVisibility(View.VISIBLE);
                    Button purchase_button1_view = (Button) findViewById(R.id.purchase_button1);
                    purchase_button1_view.setText(purchase_options[1].amount + " Finney " + for_ + " $" + String.format("%01.2f", purchase_options[1].usd_price));
                    purchase_button1_view.setVisibility(View.VISIBLE);
                    Button purchase_button2_view = (Button) findViewById(R.id.purchase_button2);
                    purchase_button2_view.setText(purchase_options[2].amount + " Finney " + for_ + " $" + String.format("%01.2f", purchase_options[2].usd_price));
                    purchase_button2_view.setVisibility(View.VISIBLE);
                    if (!title.isEmpty() && !msg.isEmpty())
                        show_err_and_exit_dialog(title, msg, !status.equals("ok"));
                }
            }
            return;
        }
        if (callback.equals("purchase")) {
            String title = "";
            String msg = "";
            String status = Util.json_parse(rsp, "status");
            if (status.equals("purchase complete")) {
                title = getResources().getString(R.string.purchase_complete_title);
                msg = getResources().getString(R.string.purchase_complete_msg);
            } else {
                title = getResources().getString(R.string.purchase_err_title);
                msg = getResources().getString(R.string.purchase_err_msg);
                msg = msg.replace("STATUS", status);
            }
            show_err_and_exit_dialog(title, msg, true);
        }
        return;
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
        dialog = alert_dialog_builder.create();
        dialog.show();
    }


}
