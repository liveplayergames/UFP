package com.liveplayergames.finneypoker;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutCompat;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static android.content.DialogInterface.BUTTON_NEUTRAL;
import static android.content.DialogInterface.BUTTON_POSITIVE;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SelectOpponentActivity extends AppCompatActivity implements HTTP_Query_Client, AdapterView.OnItemClickListener {
  private FrameLayout overlay_frame_layout;
  private SelectOpponentActivity context;
  private SharedPreferences preferences;
  private ListView list_view;
  List<Opponent_Info> opponent_list = null;
  private Toast toast = null;
  private Socket socket = null;
  Opponent_Info values[] = null;
  private String my_id = "";
  private String my_username = "";
  private long wager = -1;
  private Handler socket_emitter_handler = null;
  private String opponent_id = "";
  private String opponent_username = "";
  private String playing_msg = "";
  private long finney_balance = 0;
  private long min_wager = 0;
  private long max_wager = 0;
  private long reduced_max_wager = 0;
  private boolean refresh_is_in_process = false;
  private boolean is_finished = false;
  private android.support.v7.app.AlertDialog persistent_dialog = null;
  private static CountDownTimer challenge_timer = null;


  //these defines are for our handle_message fcn, which displays dialogs on behalf of socket enitter listeners
  private static final int LOGIN_ACK                = 1;
  private static final int SHOW_YOU_ARE_CHALLENGED  = 2;
  private static final int SHOW_CHALLENGE_DECLINED  = 3;
  private static final int SHOW_OPPONENT_BACKED_OUT = 4;
  private static final int SHOW_COUNTER_WAGER       = 5;
  private static final int CHALLENGE_ACCEPTED       = 6;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    //System.out.println("SelectOpponentActivity::onCreate: enter");
    super.onCreate(savedInstanceState);
    overlay_frame_layout = new FrameLayout(getApplicationContext());
    setContentView(overlay_frame_layout);
    View activity_select_opponent_view = getLayoutInflater().inflate(R.layout.activity_select_opponent, overlay_frame_layout, false);
    setContentView(activity_select_opponent_view);
    Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
    //The internal implementation of the support library just checks if the Toolbar has a title (not null) at the moment the SupportActionBar is
    //set up. If there is, then this title will be used instead of the window title. You can then set a dummy title while you load the real title.
    toolbar.setTitle("");
    toolbar.setBackgroundResource(R.color.color_toolbar);
    setSupportActionBar(toolbar);
    String app_name = getResources().getString(R.string.app_name);
    toolbar.setTitle(app_name);
    toolbar.setSubtitle(getResources().getString(R.string.find_an_opponent));
    context = this;

    //
    //show_you_are_challenged is a dialog.... and we need to be on the ui thread to show a dialog. but the socket emitter listeners don't run on the
    //ui thread. so they'll send a message to this handler, which can display the dialog
    socket_emitter_handler = new Handler(Looper.getMainLooper()) {
      @Override
      public void handleMessage(Message message) {
        if (toast != null)
          toast.cancel();
        switch (message.what) {
          case LOGIN_ACK: {
            String status_str = (String) message.obj;
            if (status_str.equals("ok")) {
              get_opponents();
            } else {
              Util.show_err(getBaseContext(), getResources().getString(R.string.login_err) + ": " + status_str, 3);
              System.out.println("login error = " + status_str);
            }
            break;
          }
          case SHOW_YOU_ARE_CHALLENGED:
            if (challenge_timer != null)
              cancel_challenge_timer();
            String challenge_msg = (String) message.obj;
            String challenger_id = Util.json_parse(challenge_msg, "id");
            String challenger_username = Util.json_parse(challenge_msg, "username");
            String challenge_wager = Util.json_parse(challenge_msg, "wager");
            show_you_are_challenged(challenger_id, challenger_username, challenge_wager);
            break;
          case SHOW_CHALLENGE_DECLINED:
            if (challenge_timer != null)
              cancel_challenge_timer();
            show_backed_out_msg(false);
            break;
          case SHOW_OPPONENT_BACKED_OUT:
            if (challenge_timer != null)
              cancel_challenge_timer();
            show_backed_out_msg(true);
            break;
          case SHOW_COUNTER_WAGER:
            if (challenge_timer != null)
              cancel_challenge_timer();
            String counter_wager_msg = (String) message.obj;
            String counter_wager_id = Util.json_parse(counter_wager_msg, "id");
            String counter_wager_username = Util.json_parse(counter_wager_msg, "username");
            String counter_wager = Util.json_parse(counter_wager_msg, "wager");
            show_counter_wager(counter_wager_id, counter_wager_username, counter_wager);
            break;
          case CHALLENGE_ACCEPTED:
            if (challenge_timer != null)
              cancel_challenge_timer();
            start_the_game(true);
            break;
        }
      }
    };
    //
    String app_uri = getResources().getString(R.string.app_uri);
    preferences = getSharedPreferences(app_uri, MODE_PRIVATE);
    my_id = preferences.getString("device_id", "");
    my_username = preferences.getString("username", "");
    long szabo_balance = preferences.getLong("balance", 0);
    playing_msg = getIntent().getStringExtra("PLAYING_MSG");
    min_wager = getIntent().getLongExtra("MIN_WAGER", 0);
    max_wager = getIntent().getLongExtra("MAX_WAGER", 0);
    finney_balance = (szabo_balance + Util.SZABO_PER_FINNEY/2) / Util.SZABO_PER_FINNEY;
    reduced_max_wager = finney_balance / 5;
    (toast = Toast.makeText(context, getResources().getString(R.string.looking_for_players), Toast.LENGTH_LONG)).show();
    socket = Util.get_new_player_socket(context);
    login();
  }


  //returns false => no options menu
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.select_opponent, menu);
    return(true);
  }

  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.help:
        do_help(null);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }


  //when the phone goies to sleep/wakes, the system calls onPause/onResume. at those times we want to close
  //the socket / recreate the socket. that way the backend server will know that we were unavailable for a
  //time.
  @Override
  public void onPause() {
    super.onPause();  // Always call the superclass method first
    //start_the_game fcn sets socket to null to prevent us from releasing the socket before calling the pokeractivity
    if (socket != null) {
      System.out.println("SelectOpponentActivity::onPause: socket is not null");
      socket = Util.release_player_socket();
    }
  }

  @Override
  public void onResume() {
    //System.out.println("SelectOpponentActivity::onResume: enter");
    super.onResume();  // Always call the superclass method first
    is_finished = false;
    //we need to login again if we create a new socket
    if ((socket = Util.get_old_player_socket(context)) == null) {
      socket = Util.get_player_socket(context);
      login();
    }
  }

  public void onStop() {
    //System.out.println("SelectOpponentActivity: onStop");
    is_finished = true;
    if (toast != null)
      toast.cancel();
    if (persistent_dialog != null)
     persistent_dialog.cancel();
    super.onStop();
  }

  public void do_help(View view) {
    android.support.v7.app.AlertDialog.Builder alertDialogBuilder = new android.support.v7.app.AlertDialog.Builder(context);
    alertDialogBuilder.setTitle(getResources().getString(R.string.find_an_opponent));
    String msg;
    if (min_wager == max_wager) {
      msg = getResources().getString(R.string.select_opponent_help_fixed_wager);
      msg = msg.replace("PLAYING_MSG", playing_msg);
      msg = msg.replace("WAGER", String.valueOf(min_wager));
    } else {
      msg = getResources().getString(R.string.select_opponent_help_wager_range);
      msg = msg.replace("PLAYING_MSG", playing_msg);
      msg = msg.replace("MIN_WAGER", String.valueOf(min_wager));
      msg = msg.replace("MAX_WAGER", String.valueOf(max_wager));
    }
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


  private void login() {
    System.out.println("SelectOpponentActivity::login: enter");
    String level = playing_msg.replace(' ', '-');
    socket.on("login-ack", on_login_ack);
    String login_msg = "my-id: " + my_id + " username: " + my_username + " level: " + level + " version: " + BuildConfig.VERSION_NAME;
    String event = "login";
    socket.emit(event, login_msg);
    socket.on("refresh", on_refresh);
    socket.on("challenge", on_challenge);
    socket.on("set-wager", on_set_wager);
    socket.on("challenge-accepted", on_challenge_accepted);
  }


  private Emitter.Listener on_login_ack = new Emitter.Listener() {
    @Override
    public void call(Object... args) {
      System.out.println("got login-ack: args[0] = " + args[0]);
      String login_ack = (String)args[0];
      String status_str = Util.json_parse(login_ack, "status");
      Message message = socket_emitter_handler.obtainMessage(LOGIN_ACK, 0, 0, status_str);
      message.sendToTarget();
    }
  };


  private Emitter.Listener on_refresh = new Emitter.Listener() {
    @Override
    public void call(Object... args) {
      System.out.println("got refresh args[0] = " + args[0]);
      String refresh_msg = (String)args[0];
      if (refresh_msg.equals("decline")) {
        Message message = socket_emitter_handler.obtainMessage(SHOW_CHALLENGE_DECLINED, 0, 0);
        message.sendToTarget();
      } else if (refresh_msg.equals("backout")) {
        Message message = socket_emitter_handler.obtainMessage(SHOW_OPPONENT_BACKED_OUT, 0, 0);
        message.sendToTarget();
      } else
        get_opponents();
    }
  };


  private Emitter.Listener on_challenge = new Emitter.Listener() {
    @Override
    public void call(Object... args) {
      System.out.println("got challenge args[0] = " + args[0]);
      String challenge_msg = (String)args[0];
      Message challenge_message = socket_emitter_handler.obtainMessage(SHOW_YOU_ARE_CHALLENGED, 0, 0, challenge_msg);
      challenge_message.sendToTarget();
    }
  };

  private Emitter.Listener on_set_wager = new Emitter.Listener() {
    @Override
    public void call(Object... args) {
      System.out.println("got set-wager args[0] = " + args[0]);
      String set_wager_msg = (String)args[0];
      Message set_wager_message = socket_emitter_handler.obtainMessage(SHOW_COUNTER_WAGER, 0, 0, set_wager_msg);
      set_wager_message.sendToTarget();
    }
  };

  private Emitter.Listener on_challenge_accepted = new Emitter.Listener() {
    @Override
    public void call(Object... args) {
      System.out.println("got challenge-accepted args[0] = " + args[0]);
      Message challenge_accepted_message = socket_emitter_handler.obtainMessage(CHALLENGE_ACCEPTED, 0, 0, 0);
      challenge_accepted_message.sendToTarget();
    }
  };


  private void get_opponents() {
    if (!refresh_is_in_process) {
      refresh_is_in_process = true;
      opponent_list = new ArrayList<Opponent_Info>();
      String level = playing_msg.replace(' ', '-');
      String server = getResources().getString(R.string.player_server);
      String opponents_URL = server + "/random_players" + "?id=" + my_id + "&username=" + my_username + "&level=" + level;
      String parms[] = new String[2];
      parms[0] = opponents_URL;
      parms[1] = "opponents";
      new HTTP_Query_Task(this, context).execute(parms);
    }
  }


  public void handle_http_rsp(String callback, String rsp) {
    if (callback.equals("opponents")) {
      if (toast != null)
        toast.cancel();
      //must clear opponent list here, as there is a possiblity that handle_http_response will be called multiple times
      //in succession when there are several regreshes outstanding. this was the source of the dupplicate entries bug that
      //plaged the select opponenet page for so long...
      opponent_list.clear();
      set_opponents(rsp);
      values = opponent_list.toArray(new Opponent_Info[opponent_list.size()]);
      list_view = (ListView) findViewById(R.id.listview);
      Opponent_Array_Adapter opponent_array_adapter = new Opponent_Array_Adapter(context, values);
      list_view.setAdapter(opponent_array_adapter);
      list_view.setOnItemClickListener(this);
      refresh_is_in_process = false;
      if (opponent_list.size() == 0) {
        if (toast != null)
          toast.cancel();
        //don't display the toast if we already have an opponent. you see, once a challenge is accepted both players become unavailable for
        //further challenges. so we might receive a refresh message when the opposing player becomes "unavailable". but if we are already
        //engaged to play with him, then we don't want to display "no players are available."
        if (opponent_id.isEmpty()) {
          no_opponents_online();
          (toast = Toast.makeText(context, getResources().getString(R.string.no_opponents), Toast.LENGTH_LONG)).show();
        }
      } else {
        if (persistent_dialog != null) {
          persistent_dialog.cancel();
          persistent_dialog = null;
        }
      }
      return;
    }
  }


   //returns number of opponents processed
  private int set_opponents(String rsp) {
    //typical response id:
    //{
    //  "status": 1,
    //  "xxx": [
    //	 {
    //	   "id": "1067",
    //     "level": "expert",
    //	   "username": "Bob Dobalina",
    //	   "address": "0x85d9147b0ec6d60390c8897244d039fb55b087c6",
    //	   "online": "true"
    //	   },
    //	   .....
    //   }
    int idx = 0;
    int no_opponents = 0;
    String status = Util.json_parse(rsp, "status");
    if (!status.equals("ok")) {
      Util.show_err(getBaseContext(), getResources().getString(R.string.err_getting_opponent_list), 3);
      return(0);
    }
    for (int i = 0; i < 30; ++i) {
      if (rsp.contains("{")) {
        idx = rsp.indexOf('{') + 1;
        rsp = rsp.substring(idx);
      } else {
        break;
      }
      String username = Util.json_parse(rsp, "username");
      String level = Util.json_parse(rsp, "level");
      String id = Util.json_parse(rsp, "id");
      if (BuildConfig.DEBUG)
        System.out.println("id: " + id + ", username: " + username + ", level: " + level);
      if (username.isEmpty() || level.isEmpty() || id.isEmpty())
        break;
      ++no_opponents;
      Opponent_Info opponent_info = new Opponent_Info(id, username, level);
      opponent_list.add(opponent_info);
      if (rsp.contains("}")) {
        idx = rsp.indexOf('}') + 1;
        rsp = rsp.substring(idx);
      } else {
        break;
      }
    }
    return(no_opponents);
  }

  //to fullful contract as AdapterView.OnItemClickListener
  @Override
  public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
    if (!opponent_id.isEmpty() || !opponent_username.isEmpty()) {
      ask_wait_or_cancel(opponent_id, opponent_username);
    } else {
      //args2 is the listViews Selected index
      Opponent_Info opponent_info = values[arg2];
      String id = opponent_info.id;
      String username = opponent_info.username;
      if (min_wager != max_wager)
        ask_proposed_wager(true, id, username);
      else {
        wager = min_wager;
        send_wager_and_wait_for_opposite_ok(true, id, username);
      }
    }
  }

  //this also handles show_counter_wager
  private class Handle_You_Are_Challanged implements DialogInterface.OnClickListener {
    Handle_You_Are_Challanged(String challenger_id, String challenger_username, String challenger_wager) {
      //need to set these now, so that we can generate the proper message if the challenger backs out
      opponent_id = challenger_id;
      opponent_username = challenger_username;
      try {
        wager = Long.valueOf(challenger_wager);
      } catch (NumberFormatException e) {
        System.out.println("show_you_are_challenged: " + e.toString());
        e.printStackTrace();
        wager = 0;
      }
    }
    public void onClick(DialogInterface dialog, int id) {
      switch (id) {
        case BUTTON_NEGATIVE:
          //negative: decline the challenge
          String decline_msg = "";
          String decline_event = "decline";
          socket.emit(decline_event, decline_msg);
          opponent_id = "";
          opponent_username = "";
          dialog.cancel();
          break;
        case BUTTON_NEUTRAL:
          //neutral: propose a modified wager
          dialog.cancel();
          ask_proposed_wager(false, opponent_id, opponent_username);
          break;
        //positive: accept the challenge!
        case BUTTON_POSITIVE:
          long wager_limit = Math.min(max_wager, reduced_max_wager);
          if (min_wager <= wager && wager <= wager_limit) {
            String accept_msg = "opponent-id: " + opponent_id + " wager: " + wager;
            String accept_event = "challenge-accepted";
            socket.emit(accept_event, accept_msg);
            dialog.cancel();
            if (challenge_timer != null)
              cancel_challenge_timer();
            start_the_game(false);
          } else {
            ask_proposed_wager(false, opponent_id, opponent_username);
          }
          break;
      }
    }
  }


  private class Handle_Ask_Proposed_Wager implements DialogInterface.OnClickListener {
    final EditText input;
    boolean is_challenge;
    String id;
    String username;
    Handle_Ask_Proposed_Wager(final EditText input, boolean is_challenge, String id, String username) {
      this.input = input;
      this.is_challenge = is_challenge;
      this.id = id;
      this.username = username;
    }
    public void onClick(DialogInterface dialog, int button_id) {
      switch (button_id) {
        case BUTTON_NEGATIVE:
          if (!opponent_id.isEmpty() || !opponent_username.isEmpty()) {
            String decline_msg = "";
            String event = "backout";
            socket.emit(event, decline_msg);
            opponent_id = "";
            opponent_username = "";
          }
          dialog.cancel();
          break;
        case BUTTON_POSITIVE:
          dialog.cancel();
          String wager_str = input.getText().toString();
          try {
            wager = Long.valueOf(wager_str);
          } catch (NumberFormatException e) {
            wager = -1;
          }
          System.out.println("wager = " + wager);
          long wager_limit = Math.min(max_wager, reduced_max_wager);
          if (min_wager <= wager && wager <= wager_limit)
	        send_wager_and_wait_for_opposite_ok(is_challenge, id, username);
	      else
	        ask_proposed_wager(is_challenge, id, username);
          break;
      }
    }
  }

  private class Handle_Ask_Wait_Or_Cancel implements DialogInterface.OnClickListener {
    Handle_Ask_Wait_Or_Cancel() {
    }
    public void onClick(DialogInterface dialog, int id) {
      switch (id) {
        case BUTTON_NEGATIVE:
          String decline_msg = "";
          String event = "backout";
          socket.emit(event, decline_msg);
          opponent_id = "";
          opponent_username = "";
          dialog.cancel();
          break;
        case BUTTON_POSITIVE:
          dialog.cancel();
          //restart challenge timer
          String waiting_msg = getResources().getString(R.string.waiting_for_challenger);
          waiting_msg = waiting_msg.replace("CHALLENGER", opponent_username);
          show_challenge_timer(waiting_msg, 30);
          break;
      }
    }
  }

  private class Handle_Backed_Out_Msg implements DialogInterface.OnClickListener {
    Handle_Backed_Out_Msg() {
    }
    public void onClick(DialogInterface dialog, int id) {
      opponent_id = "";
      opponent_username = "";
      dialog.cancel();
      get_opponents();
    }
  }


  private void no_opponents_online() {
    android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
    String title = getResources().getString(R.string.no_opponents_online_title);
    alert_dialog_builder.setTitle(title);
    String msg = getResources().getString(R.string.no_opponents_online);
    msg = msg.replace("PLAY_GROUP_PHRASE", playing_msg);
    alert_dialog_builder.setMessage(msg);
    alert_dialog_builder.setNegativeButton(getResources().getString(R.string.Cancel),
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
                finish();
              }
            }
    );
    persistent_dialog = alert_dialog_builder.create();
    persistent_dialog.show();
  }


  private void show_you_are_challenged(String challenger_id, String challenger_username, String challenger_wager) {
    if (persistent_dialog != null) {
      persistent_dialog.cancel();
      persistent_dialog = null;
    }
    Handle_You_Are_Challanged handle_you_are_challanged = new Handle_You_Are_Challanged(challenger_id, challenger_username, challenger_wager);
    android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
    String challenge_str;
    if (min_wager != max_wager)
      challenge_str = getResources().getString(R.string.you_are_challenged);
    else
      challenge_str = getResources().getString(R.string.you_are_challenged_fixed_wager);
    challenge_str = challenge_str.replace("CHALLENGER", challenger_username);
    challenge_str = challenge_str.replace("WAGER", challenger_wager);
    alert_dialog_builder.setTitle(getResources().getString(R.string.You_Are_Challenged));
    alert_dialog_builder.setMessage(challenge_str);
    alert_dialog_builder.setPositiveButton(getResources().getString(R.string.Accept), handle_you_are_challanged);
    if (min_wager != max_wager)
      alert_dialog_builder.setNeutralButton(getResources().getString(R.string.accept_but_new_wager), handle_you_are_challanged);
    alert_dialog_builder.setNegativeButton(getResources().getString(R.string.decline_challenge), handle_you_are_challanged);
    persistent_dialog = alert_dialog_builder.create();
    persistent_dialog.show();
  }


  private void show_counter_wager(String counter_wager_id, String counter_wager_username, String counter_wager) {
    if (persistent_dialog != null) {
      persistent_dialog.cancel();
      persistent_dialog = null;
    }
    Handle_You_Are_Challanged handle_you_are_challanged = new Handle_You_Are_Challanged(counter_wager_id, counter_wager_username, counter_wager);
    android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
    String challenge_str = getResources().getString(R.string.counter_wager);
    challenge_str = challenge_str.replace("CHALLENGER", counter_wager_username);
    challenge_str = challenge_str.replace("WAGER", counter_wager);
    alert_dialog_builder.setTitle(counter_wager_username + " " + getResources().getString(R.string.Has_Countered));
    alert_dialog_builder.setMessage(challenge_str);
    alert_dialog_builder.setPositiveButton(getResources().getString(R.string.Accept), handle_you_are_challanged);
    alert_dialog_builder.setNeutralButton(getResources().getString(R.string.accept_but_new_wager), handle_you_are_challanged);
    alert_dialog_builder.setNegativeButton(getResources().getString(R.string.decline_challenge), handle_you_are_challanged);
    persistent_dialog = alert_dialog_builder.create();
    persistent_dialog.show();
  }

  private void show_backed_out_msg(boolean backed_out) {
    if (persistent_dialog != null) {
      persistent_dialog.cancel();
      persistent_dialog = null;
    }
    Handle_Backed_Out_Msg handle_backed_out_msg = new Handle_Backed_Out_Msg();
    android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
    String title = backed_out ? opponent_username + " " + getResources().getString(R.string.Has_Chickened_Out) :
                                opponent_username + " " + getResources().getString(R.string.Declined_Your_Challenge);
    alert_dialog_builder.setTitle(title);
    if (backed_out) {
      String opponent_backed_out_msg = getResources().getString(R.string.opponent_backed_out);
      opponent_backed_out_msg = opponent_backed_out_msg.replace("CHALLENGER", opponent_username);
      alert_dialog_builder.setMessage(opponent_backed_out_msg);
    }
    alert_dialog_builder.setPositiveButton(getResources().getString(R.string.OK), handle_backed_out_msg);
    persistent_dialog = alert_dialog_builder.create();
    persistent_dialog.show();
  }

  private void ask_proposed_wager(boolean is_challenge, String opponent_id, String opponent_username) {
    final EditText input = new EditText(this);
    // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
    input.setInputType(InputType.TYPE_CLASS_NUMBER);
    Handle_Ask_Proposed_Wager handle_ask_proposed_wager = new Handle_Ask_Proposed_Wager(input, is_challenge, opponent_id, opponent_username);
    android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
    alert_dialog_builder.setTitle(getResources().getString(R.string.ask_wager_title));
    String msg = getResources().getString(R.string.You_are_playing) + " " + playing_msg + ".\n\n" +
            getResources().getString(R.string.The_minimum_wager_is) + " " + min_wager + " Finney; " +
            getResources().getString(R.string.the_maximum_wager_is) + " " + max_wager + " Finney.";
    if (reduced_max_wager < max_wager) {
      msg += "\n\n" +
              getResources().getString(R.string.but_cuz_your_bal_is_only) + " " + finney_balance + " Finney," +
              getResources().getString(R.string.now_max_wager_is) + " " + reduced_max_wager + " Finney.";
    }
    alert_dialog_builder.setMessage(msg);
    alert_dialog_builder.setPositiveButton(getResources().getString(R.string.OK), handle_ask_proposed_wager);
    alert_dialog_builder.setNegativeButton(getResources().getString(R.string.Cancel), handle_ask_proposed_wager);
    //
    LinearLayout ll = new LinearLayout(this);
    ll.setOrientation(LinearLayout.HORIZONTAL);
    ll.setLayoutParams(new LinearLayoutCompat.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
    ll.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);
    TextView prompt = new TextView(this);
    prompt.setText(getResources().getString(R.string.enter_raise_prompt) + ": ");
    ll.addView(prompt);
    input.setMinEms(7);
    ll.addView(input);
    alert_dialog_builder.setView(ll);
    //alert_dialog_builder.setView(input);
    persistent_dialog = alert_dialog_builder.create();
    persistent_dialog.show();
  }

  private void ask_wait_or_cancel(String id, String username) {
    //it's likely that the user pressed the back button already if he didn't want to wait.
    if (is_finished)
      return;
    if (persistent_dialog != null) {
      persistent_dialog.cancel();
      persistent_dialog = null;
    }
    Handle_Ask_Wait_Or_Cancel handle_ask_wait_or_cancel = new Handle_Ask_Wait_Or_Cancel();
    android.support.v7.app.AlertDialog.Builder alert_dialog_builder = new android.support.v7.app.AlertDialog.Builder(context);
    alert_dialog_builder.setTitle(getResources().getString(R.string.Waiting_for_Response_from) + " " + username);
    String wait_or_cancel_msg = getResources().getString(R.string.wait_or_cancel);
    wait_or_cancel_msg = wait_or_cancel_msg.replace("CHALLENGER", username);
    alert_dialog_builder.setMessage(wait_or_cancel_msg);
    alert_dialog_builder.setPositiveButton(getResources().getString(R.string.OK), handle_ask_wait_or_cancel);
    alert_dialog_builder.setNegativeButton(getResources().getString(R.string.Cancel), handle_ask_wait_or_cancel);
    persistent_dialog = alert_dialog_builder.create();
    persistent_dialog.show();
  }

  private void send_wager_and_wait_for_opposite_ok(boolean challenge, String id, String username) {
    String wager_msg = "my-id: " + my_id + " opponent-id: " + id + " wager: " + wager;
    String event = challenge ? "challenge" : "set-wager";
    socket.emit(event, wager_msg);
    String waiting_msg = getResources().getString(R.string.waiting_for_challenger);
    waiting_msg = waiting_msg.replace("CHALLENGER", username);
    show_challenge_timer(waiting_msg, 30);
    this.opponent_id = id;
    this.opponent_username = username;
  }


  void show_challenge_timer(final String original_msg, final int seconds) {
    if (toast != null)
      toast.cancel();
    if (challenge_timer != null)
      challenge_timer.cancel();
    final TextView message_view = (TextView) findViewById(R.id.message);
    final boolean replace_in_msg = original_msg.contains("SECONDS");
    String msg = original_msg;
    Circle circle = (Circle) findViewById(R.id.circle);
    circle.setAngle(Circle.START_ANGLE);
    circle.setVisibility(View.VISIBLE);
    Circle_Angle_Animation animation = new Circle_Angle_Animation(circle, 0);
    animation.setDuration(seconds * 1000);
    circle.startAnimation(animation);
    String sec_str = String.format("%2d", seconds);
    TextView countdown_view = (TextView) findViewById(R.id.countdown);
    countdown_view.setText(sec_str);
    countdown_view.setVisibility(View.VISIBLE);
    if (replace_in_msg) {
      msg = original_msg.replace("SECONDS", sec_str);
    }
    message_view.setText(msg);
    message_view.setVisibility(View.VISIBLE);
    if (seconds > 0) {
        challenge_timer = new CountDownTimer(1000 * seconds, 1000) {
        int count = 0;
        public void onTick(long millisUntilFinished) {
          ++count;
          String sec_str = String.format("%2d", seconds - count);
          TextView countdown_view = (TextView) findViewById(R.id.countdown);
          countdown_view.setText(sec_str);
          if (replace_in_msg) {
            String msg = original_msg.replace("SECONDS", sec_str);
            message_view.setText(msg);
          }
        }
        public void onFinish() {
          message_view.setText("");
          message_view.setVisibility(View.INVISIBLE);
          Circle circle = (Circle) findViewById(R.id.circle);
          circle.setVisibility(View.INVISIBLE);
          circle.clearAnimation();
          TextView countdown_view = (TextView) findViewById(R.id.countdown);
          countdown_view.setVisibility(View.INVISIBLE);
          System.out.println("show_challenge_msg::onFinish -- complete");
          ask_wait_or_cancel(opponent_id, opponent_username);
        }
      }.start();
    }
  }

  private void cancel_challenge_timer() {
    if (toast != null)
      toast.cancel();
    if (challenge_timer != null)
      challenge_timer.cancel();
    TextView message_view = (TextView) findViewById(R.id.message);
    message_view.setText("");
    message_view.setVisibility(View.INVISIBLE);
    Circle circle = (Circle) findViewById(R.id.circle);
    circle.setVisibility(View.INVISIBLE);
    circle.clearAnimation();
    TextView countdown_view = (TextView) findViewById(R.id.countdown);
    countdown_view.setVisibility(View.INVISIBLE);
    challenge_timer = null;
  }


  private void start_the_game(boolean i_am_challenger) {
    if (persistent_dialog != null) {
      persistent_dialog.cancel();
      persistent_dialog = null;
    }
    if (challenge_timer != null)
      cancel_challenge_timer();
    Intent intent = new Intent(this, PokerActivity.class);
    intent.putExtra("OPPONENT_ID", opponent_id);
    intent.putExtra("OPPONENT_USERNAME", opponent_username);
    intent.putExtra("WAGER", wager);
    intent.putExtra("IAMCHALLENGER", i_am_challenger);
    intent.putExtra("STARTGAME", true);
    //to prevent on_destroy from releasing the socket
    socket.off("login-ack", on_login_ack);
    socket.off("refresh", on_refresh);
    socket.off("challenge", on_challenge);
    socket.off("set-wager", on_set_wager);
    socket.off("challenge-accepted", on_challenge_accepted);
    socket = null;
    finish();
    startActivity(intent);
  }

}
