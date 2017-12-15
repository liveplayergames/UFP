package com.liveplayergames.finneypoker;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class PokerActivity extends AppCompatActivity implements HTTP_Query_Client, SeekBar.OnSeekBarChangeListener, Payment_Processor_Client {

    public static final long POKER_BET_NOMINAL_GAS = 50000;
    public static final long POKER_BET_GAS_LIMIT = 100000;

    private long wager = -1;
    private long my_balance = 0;
    private long my_start_balance = 0;
    private long pot_balance = 0;
    private int my_bet_cnt = 0;
    private int my_tx_cnt = 0;
    private long my_tx_total = 0;
    private String my_id = "";
    private String my_username = "";
    private String opponent_id = "";
    private String opponent_username = "";
    private byte[] game_id = null;
    private boolean i_am_challenger = false;
    private Toast toast = null;
    private PokerActivity context;
    private SharedPreferences preferences;
    private Socket socket = null;
    private Handler socket_emitter_handler = null;
    private boolean did_discard = false;
    private boolean game_over = false;
    private boolean i_am_delayed = false;
    private boolean last_msg_was_payment = false;
    private int max_processing_delay_sec = 30;
    private static CountDownTimer message_timer = null;
    private String my_card_strs[] = {"", "", "", "", "" };
    private String opponent_card_strs[] = {"", "", "", "", "" };
    private String card_strings_a[] = null;
    private String card_strings_b[] = null;
    private String card_strings_c[] = null;
    private String my_beg_string_verify = "";
    private String my_end_string_verify = "";
    private char my_cut_select;
    private char opponent_cut_select;
    private int my_strings_idx = 0;
    private int opponent_strings_idx = 0;
    //these defines are for our handle_message fcn, which displays dialogs on behalf of socket enitter listeners
    private static final int MOVE_MY_FUNDS = 1;
    private static final int MOVE_OPPONENT_FUNDS = 2;
    private static final int MOVE_CARD = 3;
    private static final int SOCKET_EVENT_ANTE = 4;
    private static final int SOCKET_EVENT_DEAL = 5;
    private static final int SOCKET_EVENT_REVEAL = 6;
    private static final int SOCKET_EVENT_RESULT = 7;
    private static final int SOCKET_EVENT_FORFEIT = 8;
    private static final int SOCKET_EVENT_BET = 9;
    private static final int SOCKET_EVENT_DISCARD = 10;
    private static final int SOCKET_EVENT_WAIT_OPPONENT_ANTE = 11;
    private static final int SOCKET_EVENT_WAIT_OPPONENT_BET = 12;
    private static final int SOCKET_EVENT_WAIT_OPPONENT_DISCARD = 13;
    private static final int SOCKET_EVENT_DELAY = 14;
    private static final int BET_FAIL_FORFEIT = 15;

    private enum Card_Source { MY_CARDS, OPPONENT_CARDS, MY_DISCARDS, OPPONENT_DISCARDS, DECK };
    private class Card_Move_Spec {
        public Card_Move_Spec(Card_Source src, int src_idx, Card_Source dst, int dst_idx) {
            this.src = src;
            this.dst = dst;
            this.src_idx = src_idx;
            this.dst_idx = dst_idx;
        }
        Card_Source src, dst;
        int src_idx, dst_idx;
    };

    private enum COUNTDOWN { NO_COUNTDOWN, MY_COUNTDOWN, OPPONENT_COUNTDOWN }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_poker);
        context = this;
        String app_uri = getResources().getString(R.string.app_uri);
        preferences = getSharedPreferences(app_uri, MODE_PRIVATE);
        Intent intent = getIntent();
        wager = intent.getLongExtra("WAGER", 0);
        opponent_id = intent.getStringExtra("OPPONENT_ID");
        opponent_username = intent.getStringExtra("OPPONENT_USERNAME");
        i_am_challenger = intent.getBooleanExtra("IAMCHALLENGER", i_am_challenger);
        //only want this to happen once. note onCreate will be called again after eg a rotate
        boolean start_game = intent.getBooleanExtra("STARTGAME", false);
        intent.putExtra("STARTGAME", false);
        System.out.println("in Oncreate. STARTGAME = " + start_game);
        my_id = preferences.getString("device_id", "");
        my_username = preferences.getString("username", "");
        long szabo_balance = preferences.getLong("balance", 0);
        long finney_balance = (szabo_balance + Util.SZABO_PER_FINNEY/2) / Util.SZABO_PER_FINNEY;
        my_start_balance = my_balance = finney_balance;
        i_am_delayed = false;
        if (start_game)
            game_over = false;
        //show_you_are_challenged is a dialog.... and we need to be on the ui thread to show a dialog. but the socket emitter listeners don't run on the
        //ui thread. so they'll send a message to this handler, which can display the dialog
        socket_emitter_handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MOVE_MY_FUNDS:
                    case MOVE_OPPONENT_FUNDS: {
                        int steps = message.arg1;
                        int step = message.arg2;
                        ImageView moving_funds = (ImageView) findViewById((message.what == MOVE_MY_FUNDS) ? R.id.my_funds_copy : R.id.opponent_funds_copy);
                        if (step > steps) {
                            moving_funds.setVisibility(View.INVISIBLE);
                        } else {
                            ImageView pot = (ImageView) findViewById(R.id.pot);
                            ImageView starting_funds = (ImageView) findViewById((message.what == MOVE_MY_FUNDS) ? R.id.my_funds : R.id.opponent_funds);
                            float x = starting_funds.getX() + step * (pot.getX() - starting_funds.getX()) / steps;
                            float y = starting_funds.getY() + step * (pot.getY() - starting_funds.getY()) / steps;
                            //System.out.println("step " + step + "/" + steps + ", x: " + x + ", y: " + y);
                            moving_funds.setX(x);
                            moving_funds.setY(y);
                            if (step == 0)
                                moving_funds.setVisibility(View.VISIBLE);
                        }
                        break;
                    }
                    case MOVE_CARD: {
                        int steps = message.arg1;
                        int step = message.arg2;
                        Card_Move_Spec card_move_spec = (Card_Move_Spec) message.obj;
                        ImageView moving_card = (ImageView) findViewById(R.id.moving_card);
                        if (step > steps) {
                            moving_card.setVisibility(View.INVISIBLE);
                            ImageView dst_card_view = card_source_to_view(card_move_spec.dst, card_move_spec.dst_idx);
                            //if (dst_card_view == null)
                            //    System.out.println("how did we get a null?");
                            //else
                            dst_card_view.setVisibility(View.VISIBLE);
                        } else {
                            ImageView src_card_view = card_source_to_view(card_move_spec.src, card_move_spec.src_idx);
                            ImageView dst_card_view = card_source_to_view(card_move_spec.dst, card_move_spec.dst_idx);
                            float x = src_card_view.getX() + step * (dst_card_view.getX() - src_card_view.getX()) / steps;
                            float y = src_card_view.getY() + step * (dst_card_view.getY() - src_card_view.getY()) / steps;
                            //System.out.println("step " + step + "/" + steps + ", x: " + x + "/" + dst_card_view.getX() + ", y: " + y + "/" + dst_card_view.getY());
                            moving_card.setX(x);
                            moving_card.setY(y);
                            if (step == 0) {
                                moving_card.setVisibility(View.VISIBLE);
                                src_card_view.setVisibility(View.INVISIBLE);
                            }
                        }
                        break;
                    }

                    case BET_FAIL_FORFEIT: {
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                        alertDialogBuilder.setTitle(getResources().getString(R.string.no_internet_title));
                        String msg = getResources().getString(R.string.internet_bet_err_msg);
                        msg = msg.replace("OPPONENT_USERNAME", opponent_username);
                        alertDialogBuilder.setMessage(msg);
                        alertDialogBuilder.setCancelable(false);
                        alertDialogBuilder.setNeutralButton(getResources().getString(R.string.OK),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                    finish();
                                }
                            });
                        AlertDialog alertDialog = alertDialogBuilder.create();
                        alertDialog.show();
                        break;
                    }


                    /* ---------------------------------------------------------------------------------------------
                    ante event handlers
                    --------------------------------------------------------------------------------------------- */
                    case SOCKET_EVENT_ANTE: {
                        System.out.println("SOCKET_EVENT_ANTE");
                        last_msg_was_payment = false;
                        String socket_ante_msg = (String) message.obj;
                        String do_action = Util.json_parse(socket_ante_msg, "do");
                        String opponent_did = Util.json_parse(socket_ante_msg, "opponent-did");
                        String size_str = Util.json_parse(socket_ante_msg, "size");
                        String game_id_hex = Util.json_parse(socket_ante_msg, "game_id");
                        String max_delay_str = Util.json_parse(socket_ante_msg, "max_delay");
                        String my_cut_str = Util.json_parse(socket_ante_msg, "cut");
                        String opponent_cut_str = Util.json_parse(socket_ante_msg, "opponent-cut");
                        long ante_size = 0;
                        if (!game_id_hex.isEmpty()) {
                            try {
                                game_id = Util.hex_string_to_byte_array(game_id_hex);
                            } catch (Exception e) {
                                System.out.println(e.toString() + " (game_id_hex = " + game_id_hex + ")");
                            }
                        }
                        if (!max_delay_str.isEmpty()) {
                            try {
                                max_processing_delay_sec = Integer.parseInt(max_delay_str);
                            } catch (NumberFormatException e) {
                                System.out.println(e.toString() + " (size_str = " + max_delay_str + ")");
                            }
                        }
                        try {
                            ante_size = Long.parseLong(size_str);
                        } catch (NumberFormatException e) {
                            System.out.println(e.toString() + " (size_str = " + size_str + ")");
                        }
                        if (opponent_did.equals("true")) {
                            String user_msg = opponent_username + " " + getResources().getString(R.string.has_deposited_his_ante);;
                            //if my ante is already done, then only display for 2 seconds... otherwise display until message is erased by my ante message
                            show_user_msg(user_msg, 60, COUNTDOWN.NO_COUNTDOWN);
                            move_ante(MOVE_OPPONENT_FUNDS, ante_size);
                        }
                        if (do_action.equals("true")) {
                            if (my_cut_str.equals("abc") || my_cut_str.equals("ab") || my_cut_str.equals("ac") || my_cut_str.equals("bc")) {
                                int selector = (int)(System.currentTimeMillis() % (long)my_cut_str.length());
                                my_cut_select = my_cut_str.charAt(selector);
                                String cut_msg = "select: " + my_cut_select;
                                System.out.println("sending : cut: " + cut_msg);
                                socket.emit("did-cut", cut_msg);
                            } else {
                                //keep the backend server honest! the challenger gets to do the cut. so we should only be
                                //here after the challenger has already deposited his ante
                                String toast_msg = getResources().getString(R.string.fairness_bug);
                                System.out.println(toast_msg);
                                if (toast != null)
                                    toast.cancel();
                                (toast = Toast.makeText(context, toast_msg, Toast.LENGTH_LONG)).show();
                            }
                            String user_msg = getResources().getString(R.string.Depositing_ante) + "... (" + ante_size + " Finney)";
                            show_user_msg(user_msg, 60, COUNTDOWN.NO_COUNTDOWN);
                            move_ante(MOVE_MY_FUNDS, ante_size);
                            withdraw_from_funds("did-ante", ante_size);
                            /* now we do this in withdraw_from_funds
                            String ante_msg = "opponent-id: " + opponent_id + " size: " + ante_size;
                            System.out.println("sending did-ante: " + ante_msg);
                            socket.emit("did-ante", ante_msg);
                            */
                        } else {
                            if (!opponent_cut_str.isEmpty())
                                opponent_cut_select = opponent_cut_str.charAt(0);
                        }
                        break;
                    }

                    /* ---------------------------------------------------------------------------------------------
                    deal event handler
                    --------------------------------------------------------------------------------------------- */
                    case SOCKET_EVENT_DEAL: {
                        System.out.println("SOCKET_EVENT_DEAL");
                        last_msg_was_payment = false;
                        List<Integer> my_cards = new ArrayList<Integer>();
                        List<Integer> opponent_cards = new ArrayList<Integer>();
                        //get rid of any previous message. eg. "opponent deposited ante"
                        show_user_msg("", 0, COUNTDOWN.NO_COUNTDOWN);
                        //my hand
                        String deal_msg = (String) message.obj;
                        String card0 = Util.json_parse(deal_msg, "my-card0");
                        if (!card0.isEmpty()) {
                            my_card_strs[0] = card0;
                            int card0_front_image = card_to_image_resource(card0);
                            ImageView front_card0_view = (ImageView) findViewById(R.id.my_card0_front);
                            front_card0_view.setImageResource(card0_front_image);
                            my_cards.add(new Integer(0));
                        }
                        String card1 = Util.json_parse(deal_msg, "my-card1");
                        if (!card1.isEmpty()) {
                            my_card_strs[1] = card1;
                            int card1_front_image = card_to_image_resource(card1);
                            ImageView front_card1_view = (ImageView) findViewById(R.id.my_card1_front);
                            front_card1_view.setImageResource(card1_front_image);
                            my_cards.add(new Integer(1));
                        }
                        String card2 = Util.json_parse(deal_msg, "my-card2");
                        if (!card2.isEmpty()) {
                            my_card_strs[2] = card2;
                            int card2_front_image = card_to_image_resource(card2);
                            ImageView front_card2_view = (ImageView) findViewById(R.id.my_card2_front);
                            front_card2_view.setImageResource(card2_front_image);
                            my_cards.add(new Integer(2));
                        }
                        String card3 = Util.json_parse(deal_msg, "my-card3");
                        if (!card3.isEmpty()) {
                            my_card_strs[3] = card3;
                            int card3_front_image = card_to_image_resource(card3);
                            ImageView front_card3_view = (ImageView) findViewById(R.id.my_card3_front);
                            front_card3_view.setImageResource(card3_front_image);
                            my_cards.add(new Integer(3));
                        }
                        String card4 = Util.json_parse(deal_msg, "my-card4");
                        if (!card4.isEmpty()) {
                            my_card_strs[4] = card4;
                            int card4_front_image = card_to_image_resource(card4);
                            ImageView front_card4_view = (ImageView) findViewById(R.id.my_card4_front);
                            front_card4_view.setImageResource(card4_front_image);
                            my_cards.add(new Integer(4));
                        }
                        //save my card string for later verification
                        if (my_cards.size() == 5) {
                            my_beg_string_verify = "";
                            for (int i = 0; i < my_card_strs.length; ++i) {
                                if (i > 0)
                                    my_beg_string_verify += "-";
                                my_beg_string_verify += my_card_strs[i];
                            }
                            //in case we don't discard at all
                            my_end_string_verify = my_beg_string_verify;
                        } else if (my_cards.size() > 0) {
                            my_end_string_verify = "";
                            for (int i = 0; i < my_card_strs.length; ++i) {
                                if (i > 0)
                                    my_end_string_verify += "-";
                                my_end_string_verify += my_card_strs[i];
                            }
                        }
                        //opponent hand
                        String opponent_card0 = Util.json_parse(deal_msg, "opponent-card0");
                        if (!opponent_card0.isEmpty())
                            opponent_cards.add(new Integer(0));
                        String opponent_card1 = Util.json_parse(deal_msg, "opponent-card1");
                        if (!opponent_card1.isEmpty())
                            opponent_cards.add(new Integer(1));
                        String opponent_card2 = Util.json_parse(deal_msg, "opponent-card2");
                        if (!opponent_card2.isEmpty())
                            opponent_cards.add(new Integer(2));
                        String opponent_card3 = Util.json_parse(deal_msg, "opponent-card3");
                        if (!opponent_card3.isEmpty())
                            opponent_cards.add(new Integer(3));
                        String opponent_card4 = Util.json_parse(deal_msg, "opponent-card4");
                        if (!opponent_card4.isEmpty())
                            opponent_cards.add(new Integer(4));
                        deal(my_cards, opponent_cards);
                        break;
                    }

                    /* ---------------------------------------------------------------------------------------------
                    reveal event handler
                    --------------------------------------------------------------------------------------------- */
                    case SOCKET_EVENT_REVEAL: {
                        System.out.println("SOCKET_EVENT_REVEAL");
                        last_msg_was_payment = false;
                        String reveal_msg = (String) message.obj;
                        List<Integer> opponent_cards = new ArrayList<Integer>();
                        //opponent hand
                        String card0 = Util.json_parse(reveal_msg, "opponent-card0");
                        if (!card0.isEmpty()) {
                            opponent_card_strs[0] = card0;
                            int card0_front_image = card_to_image_resource(card0);
                            ImageView front_card0_view = (ImageView) findViewById(R.id.opponent_card0_front);
                            front_card0_view.setImageResource(card0_front_image);
                            opponent_cards.add(new Integer(0));
                        }
                        String card1 = Util.json_parse(reveal_msg, "opponent-card1");
                        if (!card1.isEmpty()) {
                            opponent_card_strs[1] = card1;
                            int card1_front_image = card_to_image_resource(card1);
                            ImageView front_card1_view = (ImageView) findViewById(R.id.opponent_card1_front);
                            front_card1_view.setImageResource(card1_front_image);
                            opponent_cards.add(new Integer(1));
                        }
                        String card2 = Util.json_parse(reveal_msg, "opponent-card2");
                        if (!card2.isEmpty()) {
                            opponent_card_strs[2] = card2;
                            int card2_front_image = card_to_image_resource(card2);
                            ImageView front_card2_view = (ImageView) findViewById(R.id.opponent_card2_front);
                            front_card2_view.setImageResource(card2_front_image);
                            opponent_cards.add(new Integer(2));
                        }
                        String card3 = Util.json_parse(reveal_msg, "opponent-card3");
                        if (!card3.isEmpty()) {
                            opponent_card_strs[3] = card3;
                            int card3_front_image = card_to_image_resource(card3);
                            ImageView front_card3_view = (ImageView) findViewById(R.id.opponent_card3_front);
                            front_card3_view.setImageResource(card3_front_image);
                            opponent_cards.add(new Integer(3));
                        }
                        String card4 = Util.json_parse(reveal_msg, "opponent-card4");
                        if (!card4.isEmpty()) {
                            opponent_card_strs[4] = card4;
                            int card4_front_image = card_to_image_resource(card4);
                            ImageView front_card4_view = (ImageView) findViewById(R.id.opponent_card4_front);
                            front_card4_view.setImageResource(card4_front_image);
                            opponent_cards.add(new Integer(4));
                        }
                        //System.out.println("opponent count = " + opponent_cards.size());
                        flip_all_opponent_cards(opponent_cards);
                        break;
                    }


                    /* ---------------------------------------------------------------------------------------------
                    bet event handler
                    --------------------------------------------------------------------------------------------- */
                    case SOCKET_EVENT_BET: {
                        System.out.println("SOCKET_EVENT_BET");
                        last_msg_was_payment = false;
                        String socket_bet_msg = (String) message.obj;
                        String do_action = Util.json_parse(socket_bet_msg, "do");
                        String opponent_bet = Util.json_parse(socket_bet_msg, "opponent-bet");
                        String opponent_raise = Util.json_parse(socket_bet_msg, "opponent-raise");
                        String timeout = Util.json_parse(socket_bet_msg, "timeout");
                        long opponent_bet_size = 0;
                        long raise_size = 0;
                        int timeout_secs = 0;
                        try {
                            opponent_bet_size = Long.parseLong(opponent_bet);
                            raise_size = Long.parseLong(opponent_raise);
                            if (!timeout.isEmpty())
                                timeout_secs = Integer.parseInt(timeout);
                        } catch (NumberFormatException e) {
                            System.out.println(e.toString() + " (bet = " + opponent_bet + ", raise= " + opponent_raise + ")");
                        }
                        if (opponent_bet_size > 0)
                            move_bet(MOVE_OPPONENT_FUNDS, opponent_bet_size);
                        if (do_action.equals("true")) {
                            String bet_prompt = getResources().getString(R.string.Place_Your_Bet);
                            if (raise_size > 0 && my_bet_cnt > 0) {
                                bet_prompt = opponent_username + " " + getResources().getString(R.string.has_raised_you) + " " + raise_size;
                            } else if (opponent_bet_size > 0) {
                                bet_prompt = opponent_username + " " + getResources().getString(R.string.bet) + " " +
                                        opponent_bet_size + "; " + getResources().getString(R.string.Your_turn) + "!";
                            }
                            get_bet(bet_prompt, raise_size, timeout_secs);
                        } else if (opponent_bet_size > 0) {
                            //server is not asking us to bet, but informing us that the opponennt bet.... he must have called our bet
                            if (raise_size != 0)
                                System.out.println("hey! raise is nz, " + raise_size + ", why is do = " + do_action + "?");
                            show_user_msg(opponent_username + " " + getResources().getString(R.string.calls), 3, COUNTDOWN.NO_COUNTDOWN);
                        } else {
                            //server is not asking us to bet, but informing us that either the opponent bet.... but he bet nothing (ie. he
                            //checked our check), or the opponent didn't bet at all -- in which case we must have just called (or checked)
                            //and the server is telling us that betting is done.
                            show_user_msg(getResources().getString(R.string.Betting_is_complete), 3, COUNTDOWN.NO_COUNTDOWN);
                        }
                        break;
                    }

                    /* ---------------------------------------------------------------------------------------------
                    discard event handler
                    --------------------------------------------------------------------------------------------- */
                    case SOCKET_EVENT_DISCARD: {
                        System.out.println("SOCKET_EVENT_DO_DISCARD");
                        last_msg_was_payment = false;
                        String socket_discard_msg = (String) message.obj;
                        String do_action = Util.json_parse(socket_discard_msg, "do");
                        String opponent_card = Util.json_parse(socket_discard_msg, "card");
                        String timeout = Util.json_parse(socket_discard_msg, "timeout");
                        int idx = 0;
                        int timeout_secs = 0;
                        try {
                            if (!timeout.isEmpty())
                                timeout_secs = Integer.parseInt(timeout);
                            if (!opponent_card.equals("none"))
                                idx = Integer.parseInt(opponent_card);
                        } catch (NumberFormatException e) {
                            System.out.println(e.toString() + " (card_str = " + opponent_card + ")");
                        }
                        if (!opponent_card.equals("none")) {
                            opponent_strings_idx |= (1 << idx);
                            //opponent cards are numbered, 0 to 4, left to right; and discards are on the left. so distance is just equal to the card idx
                            int extra_time = idx * 100;
                            Card_Move_Spec card_move_spec = new Card_Move_Spec(Card_Source.OPPONENT_CARDS, idx, Card_Source.OPPONENT_DISCARDS, no_opponent_discards++);
                            new Card_Mover(card_move_spec, false, 1000 + extra_time);
                        }
                        if (do_action.equals("true"))
                            get_discards(timeout_secs);
                        break;
                    }


                    /* ---------------------------------------------------------------------------------------------
                    result event handler
                    --------------------------------------------------------------------------------------------- */
                    case SOCKET_EVENT_RESULT: {
                        System.out.println("SOCKET_EVENT_RESULT");
                        last_msg_was_payment = false;
                        String socket_result_msg = (String) message.obj;
                        String my_hand = Util.json_parse(socket_result_msg, "my-hand");
                        String opponent_hand = Util.json_parse(socket_result_msg, "opponent-hand");
                        String winner = Util.json_parse(socket_result_msg, "winner");
                        //
                        String my_beg_cards_key = Util.json_parse(socket_result_msg, "my-beg-cards");
                        String my_end_cards_key = Util.json_parse(socket_result_msg, "my-end-cards");
                        String opponent_end_cards_key = Util.json_parse(socket_result_msg, "opponent-end-cards");
                        //
                        show_result(my_hand, opponent_hand, winner.equals("me"));
                        game_over = true;
                        //
                        //here is where we verify that the game was kosher.
                        //first verify opponent's end cards
                        String opponent_end_str_verify = "";
                        for (int i = 0; i < opponent_card_strs.length; ++i) {
                            if (i > 0)
                                opponent_end_str_verify += "-";
                            opponent_end_str_verify += opponent_card_strs[i];
                        }
                        String opponent_card_strings[] = (opponent_cut_select == 'a') ? card_strings_a : (opponent_cut_select == 'b') ? card_strings_b : card_strings_c;
                        String encrypted_card_string = opponent_card_strings[opponent_strings_idx];
                        String iv = Util.json_parse(encrypted_card_string, "iv");
                        String msg = Util.json_parse(encrypted_card_string, "msg");
                        String decrypted_card_string = Util.aes_decrypt(msg, iv, opponent_end_cards_key);
                        if (opponent_end_str_verify.equals(decrypted_card_string.trim())) {
                            System.out.println("opponent end cards verified fair");
                        } else {
                            String toast_msg = getResources().getString(R.string.fairness_bug) + "\n\nmessage B";
                            System.out.println(toast_msg);
                            System.out.println("opponent_end_str_verify = " + opponent_end_str_verify);
                            System.out.println("opponent_end_cards_key = " + opponent_end_cards_key);
                            System.out.println("decrypted_card_string = " + decrypted_card_string);
                            if (toast != null)
                                toast.cancel();
                            (toast = Toast.makeText(context, toast_msg, Toast.LENGTH_LONG)).show();
                        }
                        //
                        //now verify my beginning cards
                        String my_card_strings[] = (my_cut_select == 'a') ? card_strings_a : (my_cut_select == 'b') ? card_strings_b : card_strings_c;
                        encrypted_card_string = my_card_strings[0];
                        iv = Util.json_parse(encrypted_card_string, "iv");
                        msg = Util.json_parse(encrypted_card_string, "msg");
                        decrypted_card_string = Util.aes_decrypt(msg, iv, my_beg_cards_key);
                        if (my_beg_string_verify.equals(decrypted_card_string.trim())) {
                            System.out.println("my beg cards verified fair");
                        } else {
                            String toast_msg = getResources().getString(R.string.fairness_bug) + "\n\nmessage C";
                            System.out.println(toast_msg);
                            System.out.println("my_beg_str_verify = " + my_beg_string_verify);
                            System.out.println("my_beg_cards_key = " + my_beg_cards_key);
                            System.out.println("decrypted_card_string = " + decrypted_card_string);
                            if (toast != null)
                                toast.cancel();
                            (toast = Toast.makeText(context, toast_msg, Toast.LENGTH_LONG)).show();
                        }
                        //
                        //now verify my end cards
                        encrypted_card_string = my_card_strings[my_strings_idx];
                        iv = Util.json_parse(encrypted_card_string, "iv");
                        msg = Util.json_parse(encrypted_card_string, "msg");
                        decrypted_card_string = Util.aes_decrypt(msg, iv, my_end_cards_key);
                        if (my_end_string_verify.equals(decrypted_card_string.trim())) {
                            System.out.println("my end cards verified fair");
                        } else {
                            String toast_msg = getResources().getString(R.string.fairness_bug) + "\n\nmessage D";
                            System.out.println(toast_msg);
                            System.out.println("my_end_str_verify = " + my_end_string_verify);
                            System.out.println("my_end_cards_key = " + my_end_cards_key);
                            System.out.println("decrypted_card_string = " + decrypted_card_string);
                            if (toast != null)
                                toast.cancel();
                            (toast = Toast.makeText(context, toast_msg, Toast.LENGTH_LONG)).show();
                        }
                        break;
                    }
                    case SOCKET_EVENT_FORFEIT: {
                        System.out.println("SOCKET_EVENT_FORFEIT");
                        String socket_forfeit_msg = (String) message.obj;
                        String do_forfeit = Util.json_parse(socket_forfeit_msg, "do");
                        String opponent_forfeit = Util.json_parse(socket_forfeit_msg, "opponent-forfeit");
                        String txerr = Util.json_parse(socket_forfeit_msg, "txerr");
                        String why = Util.json_parse(socket_forfeit_msg, "why");
                        if (txerr.equals("true")) {
                            SharedPreferences.Editor preferences_editor = preferences.edit();
                            preferences_editor.putBoolean("tx_err_occurred", true);
                            preferences_editor.apply();
                            //in case of a forfeit with a tx err, and the last msg we sent was a tx, then we can walk back the nonce.
                            //that's cuz in that case we're pretty much assured that the tx was never broadcast at all. if there have been
                            //any intervening messages, then the tx might have been broadcast; that is, the err might have been not enough gas,
                            //or nonce too low... either way we would not want to walk back the nonce.
                            if (last_msg_was_payment) {
                                System.out.println("last msg was payment... can we walk back the nonce?");
                                long last_tx_nonce = preferences.getLong("last_tx_nonce", 0);
                                long verified_nonce = preferences.getLong("verified_nonce", 0);
                                if (last_tx_nonce > verified_nonce) {
                                    --last_tx_nonce;
                                    preferences_editor.putLong("last_tx_nonce", last_tx_nonce);
                                    System.out.println("yup! nonce is now " + last_tx_nonce);
                                }
                                //just once
                                last_msg_was_payment = false;
                            }

                        }
                        if (!game_over) {
                            //only show message if we hacen't already processed a forfiet
                            game_over = true;
                            socket = Util.release_player_socket();
                            show_forfeit(do_forfeit.equals("true"), opponent_forfeit.equals("true"), why);
                        }
                        break;
                    }

                    /* ---------------------------------------------------------------------------------------------
                    wait-opponent-xxx event handlers
                    --------------------------------------------------------------------------------------------- */
                    case SOCKET_EVENT_WAIT_OPPONENT_ANTE: {
                        System.out.println("SOCKET_EVENT_WAIT_OPPONENT_ANTE");
                        last_msg_was_payment = false;
                        String socket_wait_ante_msg = (String) message.obj;
                        String max_delay_str = Util.json_parse(socket_wait_ante_msg, "max_delay");
                        if (!max_delay_str.isEmpty()) {
                            try {
                                max_processing_delay_sec = Integer.parseInt(max_delay_str);
                            } catch (NumberFormatException e) {
                                System.out.println(e.toString() + " (size_str = " + max_delay_str + ")");
                            }
                        }
                        String msg = getResources().getString(R.string.waiting_for_opponent_ante);
                        msg = msg.replace("OPPONENT", opponent_username);
                        show_user_msg(msg, 60, COUNTDOWN.NO_COUNTDOWN);
                        break;
                    }
                    case SOCKET_EVENT_WAIT_OPPONENT_BET: {
                        System.out.println("SOCKET_EVENT_WAIT_OPPONENT_BET");
                        last_msg_was_payment = false;
                        String socket_wait_msg = (String) message.obj;
                        String timeout = Util.json_parse(socket_wait_msg, "timeout");
                        int timeout_secs = 0;
                        try {
                            timeout_secs = Integer.parseInt(timeout);
                        } catch (NumberFormatException e) {
                            System.out.println(e.toString() + " (timeout_str = " + timeout + ")");
                        }
                        //String msg = "Waiting for " + opponent_username + " to bet --- SECONDS";
                        String msg = getResources().getString(R.string.waiting_for_opponent_bet);
                        msg = msg.replace("OPPONENT", opponent_username);
                        show_user_msg(msg, timeout_secs, COUNTDOWN.OPPONENT_COUNTDOWN);
                        break;

                    }
                    case SOCKET_EVENT_WAIT_OPPONENT_DISCARD: {
                        System.out.println("SOCKET_EVENT_WAIT_OPPONENT_DISCARD");
                        last_msg_was_payment = false;
                        String socket_wait_msg = (String) message.obj;
                        String timeout = Util.json_parse(socket_wait_msg, "timeout");
                        int timeout_secs = 0;
                        try {
                            timeout_secs = Integer.parseInt(timeout);
                        } catch (NumberFormatException e) {
                            System.out.println(e.toString() + " (timeout_str = " + timeout + ")");
                        }
                        //String msg = "Waiting for " + opponent_username + " to discard --- SECONDS";
                        String msg = getResources().getString(R.string.waiting_for_opponent_discard);
                        msg = msg.replace("OPPONENT", opponent_username);
                        show_user_msg(msg, timeout_secs, COUNTDOWN.OPPONENT_COUNTDOWN);
                        break;
                    }
                    case SOCKET_EVENT_DELAY: {
                        System.out.println("SOCKET_EVENT_DELAY");
                        last_msg_was_payment = false;
                        //arg1 nz => we generated the delay; else socket message was received
                        boolean from_me = (message.arg1 != 0);
                        int timeout_secs = max_processing_delay_sec;
                        String msg = getResources().getString(R.string.processing_your_deposit) + " SECONDS";
                        if (!from_me) {
                            String socket_delay_msg = (String) message.obj;
                            String timeout_str = Util.json_parse(socket_delay_msg, "timeout");
                            if (!timeout_str.isEmpty()) {
                                try {
                                    timeout_secs = Integer.parseInt(timeout_str);
                                } catch (NumberFormatException e) {
                                    System.out.println(e.toString() + " (timeout_str = " + timeout_str + ")");
                                }
                            }
                            String toast_msg = getResources().getString(R.string.opponent_difficulty);
                            toast_msg = toast_msg.replace("OPPONENT", opponent_username);
                            toast_msg = toast_msg.replace("SECONDS", String.valueOf(timeout_secs));
                            if (toast != null)
                                toast.cancel();
                            (toast = Toast.makeText(context, toast_msg, Toast.LENGTH_LONG)).show();
                            msg = getResources().getString(R.string.processing_opponent_deposit);
                            msg = msg.replace("OPPONENT", opponent_username);
                        }
                        show_user_msg(msg, timeout_secs, COUNTDOWN.NO_COUNTDOWN);
                        break;
                    }
                }
		    }
        };
        socket = Util.get_player_socket(context);
        socket.on("ante", on_ante);
        socket.on("deal", on_deal);
        socket.on("bet", on_bet);
        socket.on("discard", on_discard);
        socket.on("reveal", on_reveal);
        socket.on("result", on_result);
        socket.on("forfeit", on_forfeit);
        socket.on("wait-opponent-ante", on_wait_opponent_ante);
        socket.on("wait-opponent-bet", on_wait_opponent_bet);
        socket.on("wait-opponent-discard", on_wait_opponent_discard);
        socket.on("delay", on_delay);
        if (start_game) {
            card_strings_a = new String[29];
            card_strings_b = new String[29];
            card_strings_c = new String[29];
            String server = getResources().getString(R.string.player_server);
            String opponents_URL = server + "/card_strings" + "?id=" + my_id;
            String parms[] = new String[2];
            parms[0] = opponents_URL;
            parms[1] = "card_strings";
            new HTTP_Query_Task(this, context).execute(parms);
            //
            System.out.println("sending ready-to-play");
            String ready_msg = "my-id: " + my_id + " opponent-id: " + opponent_id;
            String event = "ready-to-play";
            socket.emit(event, ready_msg);
            String user_msg = getResources().getString(R.string.game_start);
            show_user_msg(user_msg, 2, COUNTDOWN.NO_COUNTDOWN);
        }
    }

    //read card strings into an array
    private void save_card_strings(String label, String dest[], String rsp) {
        int idx = rsp.indexOf(label) + label.length();
        idx = rsp.indexOf('[', idx) + 1;
        rsp = rsp.substring(idx);
        idx = 0;
        for (int i = 0; i < dest.length; ++i) {
            if (rsp.contains("\"")) {
                int beg_idx = rsp.indexOf('"', idx) + 1;
                int end_idx = rsp.indexOf('"', beg_idx);
                String hash_string = rsp.substring(beg_idx, end_idx);
                dest[i] = hash_string;
                idx = rsp.indexOf(',', end_idx) + 1;
                rsp = rsp.substring(idx);
                idx = 0;
            } else {
                Util.show_err(getBaseContext(), getResources().getString(R.string.card_strings_err), 3);
                return;
            }
        }
        //for (int i = 0; i < 26; ++i)
        //    System.out.println("cards[" + i + "] = " + dest[i]);

    }

    public void handle_http_rsp(String callback, String rsp) {
        if (callback.equals("card_strings")) {
            if (toast != null)
                toast.cancel();
            System.out.println(rsp);
            String status = Util.json_parse(rsp, "status");
            if (!status.equals("ok")) {
                Util.show_err(getBaseContext(), getResources().getString(R.string.card_strings_err), 3);
                return;
            }
            save_card_strings("card_strings_a", card_strings_a, rsp);
            save_card_strings("card_strings_b", card_strings_b, rsp);
            save_card_strings("card_strings_c", card_strings_c, rsp);
            return;
        }
    }


    @Override
    public void onBackPressed() {
        AlertDialog.Builder alertdialogbuilder = new AlertDialog.Builder(context);
        alertdialogbuilder.setTitle(getResources().getString(R.string.exit_game_title));
        String msg = getResources().getString(R.string.exit_game_msg);
        msg = msg.replace("OPPONENT", opponent_username);
        alertdialogbuilder.setMessage(msg);
        alertdialogbuilder.setCancelable(true);
        alertdialogbuilder.setPositiveButton(getResources().getString(R.string.Exit),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                        PokerActivity.super.onBackPressed();
                    }
                });
        alertdialogbuilder.setNegativeButton(getResources().getString(R.string.Cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alertDialog = alertdialogbuilder.create();
        alertDialog.show();
    }

    //when the phone goies to sleep/wakes, the system calls onPause/onResume. at those times we do not want to
    //close the socket, as that would forfeit the game. but when the user navigates away, (eg back button) then
    //onFinishing should return true. perhaps at that time it's best to give it up. note: if the user switches apps
    //(ie. via the multitask button), then isFinishing is false.
    public void onStop() {
        System.out.println("PokerActivity::onStop: isFinishing = " + isFinishing());
        if (isFinishing() && socket != null)
            socket = Util.release_player_socket();
        super.onStop();  // Always call the superclass method first
    }
    public void onStart() {
        System.out.println("onStart");
        super.onStart();  // Always call the superclass method first
    }

    public void onPause() {
      System.out.println("onPause: isFinishing = " + isFinishing());
      super.onPause();  // Always call the superclass method first
    }
  
    public void onResume() {
        System.out.println("onResume");
        super.onResume();  // Always call the superclass method first
    }

    /* ---------------------------------------------------------------------------------------------
        socket emitter liseners
     --------------------------------------------------------------------------------------------- */
    private Emitter.Listener on_ante = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            System.out.println("got ante-up: args[0] = " + args[0]);
            if (!game_over) {
                String ante_msg = (String) args[0];
                Message socket_ante_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_ANTE, 0, 0, ante_msg);
                socket_ante_message.sendToTarget();
            }
        }
    };
    private Emitter.Listener on_deal = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (!game_over) {
                System.out.println("got deal: args[0] = " + args[0]);
                String deal_msg = (String) args[0];
                String iv = Util.json_parse(deal_msg, "iv");
                String msg = Util.json_parse(deal_msg, "msg");
                if (!iv.isEmpty() && !msg.isEmpty()) {
                    System.out.println("iv = " + iv + ", msg = " + msg);
                    String decrypt_key = preferences.getString("decrypt_key", "");
                    deal_msg = Util.aes_decrypt(msg, iv, decrypt_key);
                }
                Message socket_deal_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_DEAL, 0, 0, deal_msg);
                socket_deal_message.sendToTarget();
            }
        }
    };
    private Emitter.Listener on_forfeit = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            System.out.println("got forfeit: args[0] = " + args[0]);
            String forfeit_msg = (String) args[0];
            Message socket_forfeit_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_FORFEIT, 0, 0, forfeit_msg);
            socket_forfeit_message.sendToTarget();
        }
    };
    private Emitter.Listener on_result = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (!game_over) {
                System.out.println("got result: args[0] = " + args[0]);
                String result_msg = (String) args[0];
                Message socket_result_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_RESULT, 0, 0, result_msg);
                socket_result_message.sendToTarget();
            }
        }
    };
    private Emitter.Listener on_reveal = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (!game_over) {
                System.out.println("got reveal: args[0] = " + args[0]);
                String reveal_msg = (String) args[0];
                Message socket_reveal_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_REVEAL, 0, 0, reveal_msg);
                socket_reveal_message.sendToTarget();
            }
        }
    };
    private Emitter.Listener on_bet = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (!game_over) {
                System.out.println("got bet: args[0] = " + args[0]);
                String socket_bet_msg = (String) args[0];
                Message initial_bet_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_BET, 0, 0, socket_bet_msg);
                initial_bet_message.sendToTarget();
            }
        }
    };
    private Emitter.Listener on_discard = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (!game_over) {
                System.out.println("got discard: args[0] = " + args[0]);
                String opponent_initial_bet_msg = (String) args[0];
                Message opponent_initial_bet_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_DISCARD, 0, 0, opponent_initial_bet_msg);
                opponent_initial_bet_message.sendToTarget();
            }
        }
    };
    private Emitter.Listener on_wait_opponent_ante = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (!game_over) {
                System.out.println("got wait-opponent-ante: args[0] = " + args[0]);
                String wait_opponent_ante_msg = (String) args[0];
                Message wait_opponent_ante_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_WAIT_OPPONENT_ANTE, 0, 0, wait_opponent_ante_msg);
                wait_opponent_ante_message.sendToTarget();
            }
        }
    };
    private Emitter.Listener on_wait_opponent_bet = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (!game_over) {
                System.out.println("got wait-opponent-bet: args[0] = " + args[0]);
                String wait_opponent_bet_msg = (String) args[0];
                Message wait_opponent_bet_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_WAIT_OPPONENT_BET, 0, 0, wait_opponent_bet_msg);
                wait_opponent_bet_message.sendToTarget();
            }
        }
    };
    private Emitter.Listener on_wait_opponent_discard = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (!game_over) {
                System.out.println("got wait-opponent-discard: args[0] = " + args[0]);
                String wait_opponent_discard_msg = (String) args[0];
                Message wait_opponent_discard_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_WAIT_OPPONENT_DISCARD, 0, 0, wait_opponent_discard_msg);
                wait_opponent_discard_message.sendToTarget();
            }
        }
    };
    private Emitter.Listener on_delay = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (!game_over) {
                System.out.println("got delay: args[0] = " + args[0]);
                String delay_msg = (String) args[0];
                Message delay_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_DELAY, 0, 0, delay_msg);
                delay_message.sendToTarget();
            }
        }
    };

    //do the ante animation for us or the opponent
    private void move_ante(int who_to_move, long ante_size) {
        final int who = who_to_move;
        final long size = ante_size;
        //update this here, in case we get a forfeit before the animation ends
        pot_balance += size;
        new CountDownTimer(1500, 1500 / 15) {
            int count = 0;
            public void onTick(long millisUntilFinished) {
                //args are total-no-steps, current-step-no
                Message move_funds_message = socket_emitter_handler.obtainMessage(who, 15, count++);
                move_funds_message.sendToTarget();
            }
            public void onFinish()                     {
                //current-step-no gt total-no-steps => move is complete
                Message move_funds_message = socket_emitter_handler.obtainMessage(who, 15, 16);
                move_funds_message.sendToTarget();
                visual_deposit_to_pot(who, size);
            }
        }.start();
    }


    /* ---------------------------------------------------------------------------------------------
        get_bet
        prompt user to enter his bet via the slider
     --------------------------------------------------------------------------------------------- */
    private long slider_bet_size;
    private long slider_min_bet;
    private void get_bet(String prompt, long min_bet, int timeout) {
        show_user_msg(prompt, timeout, COUNTDOWN.MY_COUNTDOWN);
        slider_bet_size = slider_min_bet = min_bet;
        SeekBar slider_view = (SeekBar) findViewById(R.id.slider);
        slider_view.setVisibility(View.VISIBLE);
        Button bet_view = (Button) findViewById(R.id.bet);
        bet_view.setText((min_bet > 0) ? getResources().getString(R.string.Call)  + " (" + min_bet + ")"
                                       : getResources().getString(R.string.Check) + " (" + getResources().getString(R.string.No_bet) + ")");
        //System.out.println("init: min_bet = " + min_bet + "; text = " + bet_view.getText());
        bet_view.setVisibility(View.VISIBLE);
        //here user has finalized his bet via bet button
        bet_view.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ++my_bet_cnt;
                String user_msg = getResources().getString(R.string.Placing_bet) + " -- " + slider_bet_size + " Finney";
                show_user_msg(user_msg, 2, COUNTDOWN.NO_COUNTDOWN);
                move_bet(MOVE_MY_FUNDS, slider_bet_size);
                withdraw_from_funds("did-bet", slider_bet_size);
                SeekBar slider_view = (SeekBar) findViewById(R.id.slider);
                slider_view.setVisibility(View.INVISIBLE);
                Button bet_view = (Button) findViewById(R.id.bet);
                bet_view.setVisibility(View.INVISIBLE);
                Button fold_view = (Button) findViewById(R.id.fold);
                fold_view.setVisibility(View.INVISIBLE);
            }
        });
        //there is no setMin for slider :(
        //zero will represent min-bet; wager will represent min-bet+wager

        //after this bet the opponent can raise us to wager. so if this is the last round of betting, (after discards), then
        //we need to keep wager on hand (so we can call). that means the max amount we can spend now is balance - wager
        //btw, the extra 5 finney is to cover gas.
        long opponent_wager_opportunity = did_discard ? wager * 2 : wager;
        long wager_limit = Math.min(wager, (my_balance - min_bet) - opponent_wager_opportunity - my_tx_cnt - 5);
        if (wager_limit < wager) {
            String toast_msg = getResources().getString(R.string.bet_limited);
            if (toast != null)
                toast.cancel();
            (toast = Toast.makeText(context, toast_msg, Toast.LENGTH_LONG)).show();
        }
        slider_view.setMax((int)(wager_limit));
        slider_view.setProgress(0);
        slider_view.setOnSeekBarChangeListener(this);
        Button fold_view = (Button) findViewById(R.id.fold);
        fold_view.setVisibility(View.VISIBLE);
        fold_view.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            String user_msg = getResources().getString(R.string.Folding) + "...";
            show_user_msg(user_msg, 1, COUNTDOWN.NO_COUNTDOWN);
            SeekBar slider_view = (SeekBar) findViewById(R.id.slider);
            slider_view.setVisibility(View.INVISIBLE);
            Button bet_view = (Button) findViewById(R.id.bet);
            bet_view.setVisibility(View.INVISIBLE);
            Button fold_view = (Button) findViewById(R.id.fold);
            fold_view.setVisibility(View.INVISIBLE);
    		socket = Util.release_player_socket();
		    show_forfeit(true, false, null);
            }
        });
     }


    //SeekBar.OnSeekBarChangeListener fcns
    @Override
    public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
        slider_bet_size = progresValue + slider_min_bet;
        String msg = slider_bet_size == 0           ? getResources().getString(R.string.Check) + " (" + getResources().getString(R.string.No_bet) + ")"
                : slider_bet_size == slider_min_bet ? getResources().getString(R.string.Call)  + " (" + slider_min_bet + ")"
                : getResources().getString(R.string.Place_Bet) + " -- " + slider_bet_size + " Finney";
        TextView update_bet_view = (TextView) findViewById(R.id.bet);
        update_bet_view.setText(msg);
        //System.out.println("update: min_bet = " + slider_min_bet + "; text = " + update_bet_view.getText());
    }
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) { }
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) { }


    //do the bet animation for us or the opponent
    private void move_bet(int who_to_move, long bet_size) {
        final int who = who_to_move;
        final long size = bet_size;
        //it seems kind of silly to do the move animation when the size is zero.... unless the max wager is zero; then its' all
        //for practice, so we'll go through the motions.
        if (bet_size == 0 && wager > 0)
            return;
        //update this here, in case we get a forfeit before the animation ends
        pot_balance += size;
        new CountDownTimer(2000, 2000 / 15) {
            int count = 0;
            public void onTick(long millisUntilFinished) {
                //args are total-no-steps, current-step-no
                Message move_funds_message = socket_emitter_handler.obtainMessage(who, 20, count++);
                move_funds_message.sendToTarget();
            }
            public void onFinish()                       {
                //current-step-no gt total-no-steps => move is complete
                Message move_funds_message = socket_emitter_handler.obtainMessage(who, 20, 21);
                move_funds_message.sendToTarget();
                visual_deposit_to_pot(who, size);
            }
        }.start();
    }

    /* ---------------------------------------------------------------------------------------------
        deals & discards (card movements)
     --------------------------------------------------------------------------------------------- */
    private int no_my_discards = 0;
    private int no_opponent_discards = 0;

    // helper fcn to clean up after all discards are done
    private void discard_done_cleanup() {
      Button bet_view = (Button) findViewById(R.id.bet);
      bet_view.setVisibility(View.INVISIBLE);
      Button fold_view = (Button) findViewById(R.id.fold);
      fold_view.setVisibility(View.INVISIBLE);
      //ensure that he doesn't click ant more cards
      ImageView card0_view = (ImageView) findViewById(R.id.my_card0_front);
      ImageView card1_view = (ImageView) findViewById(R.id.my_card1_front);
      ImageView card2_view = (ImageView) findViewById(R.id.my_card2_front);
      ImageView card3_view = (ImageView) findViewById(R.id.my_card3_front);
      ImageView card4_view = (ImageView) findViewById(R.id.my_card4_front);
      card0_view.setClickable(false);
      card1_view.setClickable(false);
      card2_view.setClickable(false);
      card3_view.setClickable(false);
      card4_view.setClickable(false);
    }
  
    private class Card_Mover implements View.OnClickListener {
        Card_Move_Spec card_move_spec;
        int duration;
        int steps;
        Card_Mover(Card_Move_Spec card_move_spec, boolean move_when_clicked, int duration) {
            this.card_move_spec = card_move_spec;
            this.duration = duration;
    	    this.steps = duration / 50;
            if (!move_when_clicked)
                do_move();
        }
        private void do_move() {
            new CountDownTimer(duration, 50) {
                int count = 0;
                public void onTick(long millisUntilFinished) {
                    //args are total-no-steps, current-step-no
                    Message move_card_message = socket_emitter_handler.obtainMessage(MOVE_CARD, steps, count++, card_move_spec);
                    move_card_message.sendToTarget();
                }
                public void onFinish() {
                    //current-step-no gt total-no-steps => move is complete
                    Message move_card_message = socket_emitter_handler.obtainMessage(MOVE_CARD, steps, steps+1, card_move_spec);
                    move_card_message.sendToTarget();
                }
            }.start();
        }
        //@Override
        public void onClick(View view) {
            ImageView front_card_view = (ImageView) view;
            front_card_view.setImageResource(R.drawable.ic_card_back);
            //move_when_clicked is only ever set for my-cards....
            card_move_spec.dst_idx = no_my_discards++;
            String done = "false";
            if (no_my_discards < 3) {
                front_card_view.setClickable(false);
            } else {
                discard_done_cleanup();
                done = "true";
            }
            my_strings_idx |= (1 << card_move_spec.src_idx);
            System.out.println("my_strings_idx = " + my_strings_idx);
            System.out.println("sending did-discard: card: " + card_move_spec.src_idx + " done: " + done);
            String discard_done_msg = "opponent-id: " + opponent_id + " card: " + card_move_spec.src_idx + " done: " + done;
            socket.emit("did-discard", discard_done_msg);
            final int back_view_ids[]  = { R.id.my_card0,       R.id.my_card1,       R.id.my_card2,       R.id.my_card3,       R.id.my_card4 };
            ImageView back_card_view = (ImageView) findViewById(back_view_ids[card_move_spec.src_idx]);
            flip_card(front_card_view, back_card_view);
            new CountDownTimer(500, 500) {
                public void onTick(long millisUntilFinished) { }
                public void onFinish() {
                    do_move();
                }
            }.start();
        }
    }

    private void deal(final List<Integer> my_cards, final List<Integer> opponent_cards) {
        final int CARD_DURATION = 600;
        final int no_my_cards = my_cards.size();
        final int no_opponent_cards = opponent_cards.size();
        final int no_cards = no_my_cards + no_opponent_cards;
        System.out.println("my count = " + no_my_cards + ", opponent count = " + no_opponent_cards);
        new CountDownTimer(CARD_DURATION * (no_cards + 1), CARD_DURATION) {
            int count = 0;
            public void onTick(long millisUntilFinished) {
        		int extra_time = 0;
                Card_Move_Spec card_move_spec = null;
                if (count < no_opponent_cards) {
                    //System.out.println("count = " + count + " => oponent card");
                    //opponent cards are numbered, 0 to 4, left to right; and cards are dealt from the left. so distance is just equal to the card idx
                    //extra_time = count * 100;
                    card_move_spec = new Card_Move_Spec(Card_Source.DECK, 0, Card_Source.OPPONENT_CARDS, opponent_cards.get(count));
                } else if (count < no_cards) {
                    //System.out.println("count = " + count + " => my card");
                    //my cards are numbered, 0 to 4, left to right; and cards are dealt from the left. so distance is just equal to the card idx
                    //extra_time = (count - no_opponent_cards) * 100;
                    card_move_spec = new Card_Move_Spec(Card_Source.DECK, 0, Card_Source.MY_CARDS, my_cards.get(count - no_opponent_cards));
                }
                if (card_move_spec != null)
                    new Card_Mover(card_move_spec, false, CARD_DURATION + extra_time);
                ++count;
            }
            public void onFinish() {
                flip_all_my_cards(my_cards);
            }
        }.start();
    }


    private void flip_all_my_cards(final List<Integer> my_cards) {
        final int CARD_DURATION = 600;
        final int back_view_ids[]  = { R.id.my_card0,       R.id.my_card1,       R.id.my_card2,       R.id.my_card3,       R.id.my_card4 };
        final int front_view_ids[] = { R.id.my_card0_front, R.id.my_card1_front, R.id.my_card2_front, R.id.my_card3_front, R.id.my_card4_front };
        final int no_cards = my_cards.size();
        new CountDownTimer(CARD_DURATION * (no_cards + 1), CARD_DURATION) {
            int count = 0;
            public void onTick(long millisUntilFinished) {
                if (count < no_cards) {
                    ImageView back_card_view = (ImageView) findViewById(back_view_ids[my_cards.get(count)]);
                    ImageView front_card_view = (ImageView) findViewById(front_view_ids[my_cards.get(count)]);
                    flip_card(back_card_view, front_card_view);
                    ++count;
                }
            }
            public void onFinish() {
                if (socket != null) {
                    System.out.println("sending did-deal");
                    String did_deal_msg = "opponent-id: " + opponent_id;
                    socket.emit("did-deal", did_deal_msg);
                }
            }
        }.start();

    }

    private void flip_all_opponent_cards(final List<Integer> opponent_cards) {
        final int CARD_DURATION = 600;
        final int back_view_ids[]  = { R.id.opponent_card0,       R.id.opponent_card1,       R.id.opponent_card2,       R.id.opponent_card3,       R.id.opponent_card4 };
        final int front_view_ids[] = { R.id.opponent_card0_front, R.id.opponent_card1_front, R.id.opponent_card2_front, R.id.opponent_card3_front, R.id.opponent_card4_front };
        final int no_cards = opponent_cards.size();
        new CountDownTimer(CARD_DURATION * (no_cards + 1), CARD_DURATION) {
            int count = 0;
            public void onTick(long millisUntilFinished) {
                if (count < no_cards) {
                    ImageView back_card_view = (ImageView) findViewById(back_view_ids[opponent_cards.get(count)]);
                    ImageView front_card_view = (ImageView) findViewById(front_view_ids[opponent_cards.get(count)]);
                    flip_card(back_card_view, front_card_view);
                    ++count;
                }
            }
            public void onFinish() {
                if (socket != null) {
                    System.out.println("sending did-reveal");
                    String did_reveal_msg = "opponent-id: " + opponent_id;
                    socket.emit("did-reveal", did_reveal_msg);
                }
            }
        }.start();
    }


    private void flip_card(final ImageView beg_view, final ImageView end_view) {
        final AnimatorSet setLeftIn = (AnimatorSet) AnimatorInflater.loadAnimator(getApplicationContext(), R.animator.flip_left_in);
        final AnimatorSet setRightOut = (AnimatorSet) AnimatorInflater.loadAnimator(getApplicationContext(), R.animator.flip_right_out);
        end_view.setVisibility(View.VISIBLE);
        setRightOut.addListener(new AnimatorListenerAdapter() {
                                    public void onAnimationEnd(Animator animation) {
                                        beg_view.setVisibility(View.INVISIBLE);
                                    }
                                });
        setRightOut.setTarget(beg_view);
        setLeftIn.setTarget(end_view);
        setRightOut.start();
        setLeftIn.start();
    }

    private void get_discards(int timeout_secs) {
        String user_msg = getResources().getString(R.string.Select_cards_to_replace);
        show_user_msg(user_msg, timeout_secs, COUNTDOWN.MY_COUNTDOWN);
        Button bet_view = (Button) findViewById(R.id.bet);
        bet_view.setVisibility(View.VISIBLE);
        bet_view.setText(getResources().getString(R.string.Done));
        //here user has finalized his bet via bet button
        bet_view.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                System.out.println("sending did-discard");
                String discard_done_msg = "opponent-id: " + opponent_id + " card: none done: true";
                socket.emit("did-discard", discard_done_msg);
                discard_done_cleanup();
                did_discard = true;
            }
        });
        Button fold_view = (Button) findViewById(R.id.fold);
        fold_view.setVisibility(View.VISIBLE);
        ImageView card0_view = (ImageView) findViewById(R.id.my_card0_front);
        ImageView card1_view = (ImageView) findViewById(R.id.my_card1_front);
        ImageView card2_view = (ImageView) findViewById(R.id.my_card2_front);
        ImageView card3_view = (ImageView) findViewById(R.id.my_card3_front);
        ImageView card4_view = (ImageView) findViewById(R.id.my_card4_front);
        //my cards are numbered, 0 to 4, left to right; and discards are on the right. so distance is equal 4 minus the card idx
        Card_Move_Spec card0_move_spec = new Card_Move_Spec(Card_Source.MY_CARDS, 0, Card_Source.MY_DISCARDS, 0);
        card0_view.setOnClickListener(new Card_Mover(card0_move_spec, true, 1400));
        Card_Move_Spec card1_move_spec = new Card_Move_Spec(Card_Source.MY_CARDS, 1, Card_Source.MY_DISCARDS, 0);
        card1_view.setOnClickListener(new Card_Mover(card1_move_spec, true, 1300));
        Card_Move_Spec card2_move_spec = new Card_Move_Spec(Card_Source.MY_CARDS, 2, Card_Source.MY_DISCARDS, 0);
        card2_view.setOnClickListener(new Card_Mover(card2_move_spec, true, 1200));
        Card_Move_Spec card3_move_spec = new Card_Move_Spec(Card_Source.MY_CARDS, 3, Card_Source.MY_DISCARDS, 0);
        card3_view.setOnClickListener(new Card_Mover(card3_move_spec, true, 1100));
        Card_Move_Spec card4_move_spec = new Card_Move_Spec(Card_Source.MY_CARDS, 4, Card_Source.MY_DISCARDS, 0);
        card4_view.setOnClickListener(new Card_Mover(card4_move_spec, true, 1000));
    }


    //this fcn handles the ui accounting (not the animation) and also initiates the actual payment
    private void withdraw_from_funds(String why_sock_msg, long finneys) {
        my_balance -= finneys;
        TextView my_balance_view = (TextView) findViewById(R.id.my_funds_balance);
        String balance_str = String.format("%4d", my_balance);
        my_balance_view.setText(balance_str);
        if (finneys == 0) {
            System.out.println("sending " + why_sock_msg);
            socket.emit(why_sock_msg, "size: 0");
        } else {
            ++my_tx_cnt;
            my_tx_total += finneys;
            String to_addr = getResources().getString(R.string.lava_contract_addr);
            long size_wei = finneys * Util.WEI_PER_FINNEY;
            long gas_limit = POKER_BET_GAS_LIMIT;
            Payment_Processor.send(this, context, why_sock_msg, to_addr, size_wei, gas_limit, game_id, true);
        }
    }

    //this is the callback from Payment_Processor
    //note: client_data is why_sock_msg
    public boolean payment_result(boolean ok, String tx, long size_wei, String why_sock_msg, String error) {
        i_am_delayed = false;
        if (ok) {
            System.out.println("payment processor completed. signed_tx = " + tx);
            //this was moved from get_bet, since now we want to sent the txid in the did-bet msg
            if (socket == null || game_over)
                return(false);
            last_msg_was_payment = true;
            System.out.println("sending " + why_sock_msg);
            long finneys = (long) (size_wei / Util.WEI_PER_FINNEY + 0.5);
            String payment_done_msg = "tx: " + tx + " size: " + finneys;
            socket.emit(why_sock_msg, payment_done_msg);
            return(true);
        }
        Message forfeit_message = socket_emitter_handler.obtainMessage(BET_FAIL_FORFEIT, 0, 0, null);
        forfeit_message.sendToTarget();
        return(false);
    }
    public void interim_payment_result(long size_wei, String why_sock_msg, String msg) {
        System.out.println("payment processor delayed... (" + why_sock_msg + ": " + msg + ")");
        if (!i_am_delayed && socket != null) {
            long finneys = (long) (size_wei / Util.WEI_PER_FINNEY + 0.5);
            System.out.println("sending delay (while waiting for " + why_sock_msg + ")");
            String payment_delay_msg = "size: " + finneys;
            socket.emit("delay", payment_delay_msg);
            Message delay_message = socket_emitter_handler.obtainMessage(SOCKET_EVENT_DELAY, 1, 0, null);
            delay_message.sendToTarget();
            i_am_delayed = true;
        }
    }
    public void balance_result(boolean ok, long balance, String error) {
        System.out.println("Hey! we should never be here!");
    }
    public void interim_balance_result(String msg) {
        System.out.println("Hey! we should never be here!");
    }


    //pot_balance should have been updated before calling this fcn. call us after the movement of funds animation
    //to visually update the pot balance.
    private void visual_deposit_to_pot(int who_to_move, long finneys) {
        ImageView pot_view = (ImageView) findViewById(R.id.pot);
        pot_view.setVisibility(View.VISIBLE);
        TextView pot_balance_view = (TextView) findViewById(R.id.pot_balance);
        pot_balance_view.setVisibility(View.VISIBLE);
        String balance_str = String.format("%4d", pot_balance);
        pot_balance_view.setText(balance_str);
    }


    private int card_to_image_resource(String card_spec) {
        int image
                = card_spec.equals("Ac") ? R.drawable.ic_cardac
                : card_spec.equals("2c") ? R.drawable.ic_card2c
                : card_spec.equals("3c") ? R.drawable.ic_card3c
                : card_spec.equals("4c") ? R.drawable.ic_card4c
                : card_spec.equals("5c") ? R.drawable.ic_card5c
                : card_spec.equals("6c") ? R.drawable.ic_card6c
                : card_spec.equals("7c") ? R.drawable.ic_card7c
                : card_spec.equals("8c") ? R.drawable.ic_card8c
                : card_spec.equals("9c") ? R.drawable.ic_card9c
                : card_spec.equals("Tc") ? R.drawable.ic_cardtc
                : card_spec.equals("Jc") ? R.drawable.ic_cardjc
                : card_spec.equals("Qc") ? R.drawable.ic_cardqc
                : card_spec.equals("Kc") ? R.drawable.ic_cardkc
                : card_spec.equals("Ad") ? R.drawable.ic_cardad
                : card_spec.equals("2d") ? R.drawable.ic_card2d
                : card_spec.equals("3d") ? R.drawable.ic_card3d
                : card_spec.equals("4d") ? R.drawable.ic_card4d
                : card_spec.equals("5d") ? R.drawable.ic_card5d
                : card_spec.equals("6d") ? R.drawable.ic_card6d
                : card_spec.equals("7d") ? R.drawable.ic_card7d
                : card_spec.equals("8d") ? R.drawable.ic_card8d
                : card_spec.equals("9d") ? R.drawable.ic_card9d
                : card_spec.equals("Td") ? R.drawable.ic_cardtd
                : card_spec.equals("Jd") ? R.drawable.ic_cardjd
                : card_spec.equals("Qd") ? R.drawable.ic_cardqd
                : card_spec.equals("Kd") ? R.drawable.ic_cardkd
                : card_spec.equals("Ah") ? R.drawable.ic_cardah
                : card_spec.equals("2h") ? R.drawable.ic_card2h
                : card_spec.equals("3h") ? R.drawable.ic_card3h
                : card_spec.equals("4h") ? R.drawable.ic_card4h
                : card_spec.equals("5h") ? R.drawable.ic_card5h
                : card_spec.equals("6h") ? R.drawable.ic_card6h
                : card_spec.equals("7h") ? R.drawable.ic_card7h
                : card_spec.equals("8h") ? R.drawable.ic_card8h
                : card_spec.equals("9h") ? R.drawable.ic_card9h
                : card_spec.equals("Th") ? R.drawable.ic_cardth
                : card_spec.equals("Jh") ? R.drawable.ic_cardjh
                : card_spec.equals("Qh") ? R.drawable.ic_cardqh
                : card_spec.equals("Kh") ? R.drawable.ic_cardkh
                : card_spec.equals("As") ? R.drawable.ic_cardas
                : card_spec.equals("2s") ? R.drawable.ic_card2s
                : card_spec.equals("3s") ? R.drawable.ic_card3s
                : card_spec.equals("4s") ? R.drawable.ic_card4s
                : card_spec.equals("5s") ? R.drawable.ic_card5s
                : card_spec.equals("6s") ? R.drawable.ic_card6s
                : card_spec.equals("7s") ? R.drawable.ic_card7s
                : card_spec.equals("8s") ? R.drawable.ic_card8s
                : card_spec.equals("9s") ? R.drawable.ic_card9s
                : card_spec.equals("Ts") ? R.drawable.ic_cardts
                : card_spec.equals("Js") ? R.drawable.ic_cardjs
                : card_spec.equals("Qs") ? R.drawable.ic_cardqs
                : card_spec.equals("Ks") ? R.drawable.ic_cardks
                : R.drawable.ic_joker;
        return(image);
    }


    void show_user_msg(final String original_msg, final int seconds, final COUNTDOWN countdown) {
        final TextView messages_view = (TextView) findViewById(R.id.messages);
        final boolean replace_in_msg = original_msg.contains("SECONDS");
        String msg = original_msg;
        if (message_timer != null)
            message_timer.cancel();
        if (countdown != COUNTDOWN.MY_COUNTDOWN) {
            Circle circle = (Circle) findViewById(R.id.my_circle);
            circle.setVisibility(View.INVISIBLE);
            circle.clearAnimation();
            TextView countdown_view = (TextView) findViewById(R.id.my_countdown);
            countdown_view.setVisibility(View.INVISIBLE);
            System.out.println("countdown = " + countdown + "; hide my_countdown");
        }
        if (countdown != COUNTDOWN.OPPONENT_COUNTDOWN) {
            Circle circle = (Circle) findViewById(R.id.opponent_circle);
            circle.setVisibility(View.INVISIBLE);
            circle.clearAnimation();
            TextView countdown_view = (TextView) findViewById(R.id.opponent_countdown);
            countdown_view.setVisibility(View.INVISIBLE);
            System.out.println("countdown = " + countdown + "; hide opponent_countdown");
        }
	if (countdown != COUNTDOWN.NO_COUNTDOWN) {
            Circle circle = (Circle) findViewById(countdown == COUNTDOWN.MY_COUNTDOWN ? R.id.my_circle : R.id.opponent_circle);
            circle.setAngle(Circle.START_ANGLE);
            circle.setVisibility(View.VISIBLE);
            Circle_Angle_Animation animation = new Circle_Angle_Animation(circle, 0);
            animation.setDuration(seconds * 1000);
            circle.startAnimation(animation);
            String sec_str = String.format("%2d", seconds);
            TextView countdown_view = (TextView) findViewById(countdown == COUNTDOWN.MY_COUNTDOWN ? R.id.my_countdown : R.id.opponent_countdown);
            countdown_view.setText(sec_str);
            countdown_view.setVisibility(View.VISIBLE);
        }
        if (replace_in_msg) {
    	  String sec_str = String.format("%2d", seconds);
	      msg = original_msg.replace("SECONDS", sec_str);
	    }
        messages_view.setText(msg);
        messages_view.setVisibility(View.VISIBLE);
        if (seconds > 0) {
            message_timer = new CountDownTimer(1000 * seconds, 1000) {
                int count = 0;
                public void onTick(long millisUntilFinished) {
                    if (countdown != COUNTDOWN.NO_COUNTDOWN || replace_in_msg) {
                        ++count;
                        String sec_str = String.format("%2d", seconds - count);
			if (countdown != COUNTDOWN.NO_COUNTDOWN) {
			  TextView countdown_view = (TextView) findViewById(countdown == COUNTDOWN.MY_COUNTDOWN ? R.id.my_countdown : R.id.opponent_countdown);
			  countdown_view.setText(sec_str);
			}
                        if (replace_in_msg) {
                            String msg = original_msg.replace("SECONDS", sec_str);
                            messages_view.setText(msg);
                        }
                    }
                }

                public void onFinish() {
                    messages_view.setText("");
                    messages_view.setVisibility(View.INVISIBLE);
                    if (countdown != COUNTDOWN.NO_COUNTDOWN) {
                        Circle circle = (Circle) findViewById(countdown == COUNTDOWN.MY_COUNTDOWN ? R.id.my_circle : R.id.opponent_circle);
                        circle.setVisibility(View.INVISIBLE);
                        circle.clearAnimation();
                        TextView countdown_view = (TextView) findViewById(countdown == COUNTDOWN.MY_COUNTDOWN ? R.id.my_countdown : R.id.opponent_countdown);
                        countdown_view.setVisibility(View.INVISIBLE);
                        System.out.println("show_user_msg::onFinish -- complete");
                    }
                }
            }.start();
        }
    }


    private ImageView card_source_to_view(Card_Source card_source, int idx) {
        //System.out.println("card_source = " + card_source + ", idx = " + idx);
        if (card_source == Card_Source.MY_CARDS) {
            switch (idx) {
                case 0: return (ImageView) findViewById(R.id.my_card0);
                case 1: return (ImageView) findViewById(R.id.my_card1);
                case 2: return (ImageView) findViewById(R.id.my_card2);
                case 3: return (ImageView) findViewById(R.id.my_card3);
                case 4: return (ImageView) findViewById(R.id.my_card4);
            }
        } else if (card_source == Card_Source.OPPONENT_CARDS) {
            switch (idx) {
                case 0: return (ImageView) findViewById(R.id.opponent_card0);
                case 1: return (ImageView) findViewById(R.id.opponent_card1);
                case 2: return (ImageView) findViewById(R.id.opponent_card2);
                case 3: return (ImageView) findViewById(R.id.opponent_card3);
                case 4: return (ImageView) findViewById(R.id.opponent_card4);
            }
        } else if (card_source == Card_Source.MY_DISCARDS) {
            switch (idx) {
                case 0: return (ImageView) findViewById(R.id.my_discard0);
                case 1: return (ImageView) findViewById(R.id.my_discard1);
                case 2: return (ImageView) findViewById(R.id.my_discard2);
            }
        } else if (card_source == Card_Source.OPPONENT_DISCARDS) {
            switch (idx) {
                case 0: return (ImageView) findViewById(R.id.opponent_discard0);
                case 1: return (ImageView) findViewById(R.id.opponent_discard1);
                case 2: return (ImageView) findViewById(R.id.opponent_discard2);
            }
        } else {
            //System.out.println("card_source = " + card_source + ", idx = " + idx);
            return (ImageView) findViewById(R.id.deck);
        }
        System.out.println("unknown card source ??? card_source = " + card_source + ", idx = " + idx);
        return null;
    }


    private void show_result(String my_hand, String opponent_hand, boolean i_won) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        String title = getResources().getString(R.string.you_win_title);
        if (!i_won) {
            title = getResources().getString(R.string.opponent_wins_title);
            title = title.replace("OPPONENT", opponent_username);
        }
        alertDialogBuilder.setTitle(title);
        String opponent_has = getResources().getString(R.string.opponent_has);
        opponent_has = opponent_has.replace("OPPONENT", opponent_username);
        String msg = getResources().getString(R.string.You_have) + ": " + my_hand + "\n" +
    	  "(" + my_card_strs[0] + ", " + my_card_strs[1] + ", " + my_card_strs[2] + ", "
              + my_card_strs[3] + ", " + my_card_strs[4] + ")\n\n"
              + opponent_has + ": " + opponent_hand + "\n" +
          "(" + opponent_card_strs[0] + ", " + opponent_card_strs[1] + ", " + opponent_card_strs[2] + ", "
              + opponent_card_strs[3] + ", " + opponent_card_strs[4] + ")\n\n";
        if (i_won) {
            String credited_msg = getResources().getString(R.string.funds_will_be_credited);
            msg += getResources().getString(R.string.You_win_the_pot) + "\n\n" + credited_msg;
            SharedPreferences.Editor preferences_editor = preferences.edit();
            preferences_editor.putBoolean("need_payout_advisory", true);
            preferences_editor.putInt("payout_advisory_tx_cnt", my_tx_cnt);
            preferences_editor.putLong("payout_advisory_tx_total", my_tx_total);
            preferences_editor.putLong("payout_advisory_start_bal", my_start_balance);
            preferences_editor.putLong("payout_advisory_pot", pot_balance);
            preferences_editor.apply();
        } else {
            msg += getResources().getString(R.string.opponent_wins_the_pot);
            msg = msg.replace("OPPONENT", opponent_username);
        }
        alertDialogBuilder.setMessage(msg);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setNeutralButton(getResources().getString(R.string.OK),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                        finish();
                    }
                });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }



    private void show_forfeit(boolean i_forfeit, boolean opponent_forfeits, String extra_msg) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        String title = getResources().getString(R.string.abandoned_game_title);
        String msg = getResources().getString(R.string.abandoned_msg);
        if (opponent_forfeits) {
            title = getResources().getString(R.string.you_win_title);
            msg = getResources().getString(R.string.opponent_has) + " " + getResources().getString(R.string.forfeited_the_game);
            msg = msg.replace("OPPONENT", opponent_username);
        } else if (i_forfeit) {
            title = getResources().getString(R.string.opponent_wins_title);
            title = title.replace("OPPONENT", opponent_username);
            msg = getResources().getString(R.string.You_have) + " " + getResources().getString(R.string.forfeited_the_game);
        }
        alertDialogBuilder.setTitle(title);
        if (extra_msg != null && !extra_msg.isEmpty())
            msg += "\n\n\n" + getResources().getString(R.string.forfeited_msg_prompt) + "\n" + extra_msg;
        if (opponent_forfeits) {
            String credited_msg = getResources().getString(R.string.funds_will_be_credited);
            msg += "\n\n" + credited_msg;
            SharedPreferences.Editor preferences_editor = preferences.edit();
            preferences_editor.putBoolean("need_payout_advisory", true);
            preferences_editor.putInt("payout_advisory_tx_cnt", my_tx_cnt);
            preferences_editor.putLong("payout_advisory_tx_total", my_tx_total);
            preferences_editor.putLong("payout_advisory_start_bal", my_start_balance);
            preferences_editor.putLong("payout_advisory_pot", pot_balance);
            preferences_editor.apply();
        }
        alertDialogBuilder.setMessage(msg);
        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.setNeutralButton(getResources().getString(R.string.OK),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                        finish();
                    }
                });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

}
