
var bg = require('./background');
var strings = require('./strings');
var util = require('./util');
var play = require('./play');
var socket = require('socket.io-client');
var select_page = require('./select_page');

var select_parms = null;
var top_level_callback = null;
var activity_timer = null;
var got_activity = false;
var sock = null;
var levels = [
    "at-the-Starter-table",
    "at-the-Intermediate-table",
    "at-the-High-Roller-table"
];
var current_level = "";

//
var VERSION_NAME = "CHROME_2.07";

var select = module.exports = {

    //table_raises is an array[3] of max raise for each standard table.
    Select_Paramters: function(my_id, my_username, acct_addr, private_key, my_balance, table_idx, max_raise, groups) {
        this.my_id = my_id;
        this.my_username = my_username;
        this.acct_addr = acct_addr;
        this.private_key = private_key;
        this.my_balance = my_balance;
        this.table_idx = table_idx;
        this.max_raise = max_raise;
        this.groups = groups;

        /*
        function show() {
            bg.bglog('select_parms: my_id = ' + my_id);
            bg.bglog('select_parms: my_username = ' + my_username);
            bg.bglog('select_parms: acct_addr = ' + acct_addr);
            bg.bglog('select_parms: private_key = ' + private_key);
            bg.bglog('select_parms: my_balance = ' + my_balance);
            bg.bglog('select_parms: table_idx = ' + table_idx);
            bg.bglog('select_parms: max_raises[table_idx] = ' + max_raises[table_idx]);
        }
        */
    },

    run: function(select_parameters, callback) {
        bg.bglog('select.run');
        select_parms = select_parameters;
        current_level = levels[select_parms.table_idx];
        top_level_callback = callback;
        select_page.show(select_parms.table_idx, select_parms.max_raise, function() {
            select_page.hide();
            bg.close_socket();
            top_level_callback(null);
        });
        //
        sock = bg.get_socket(false);
        bg.bglog('sock.id = ' + sock.id);
        sock.on('connect', function() {
            bg.bglog('connected. sock.id = ' + sock.id);
            var login_msg = "my-id: " + select_parms.my_id + " username: " + select_parms.my_username + " level: " + current_level + " version: " + VERSION_NAME;
            bg.bglog("select.run: login_msg = " + login_msg);
            sock.emit('login', login_msg);
            var login_timer = setTimeout(function() {
                sock.off('login-ack');
                select_page.show_err("Error!", "login error: timeout -- no response from server", function() {
		    select_page.hide();
		    bg.close_socket();
                    top_level_callback(null);
                });
            }, 6000);
            sock.on('login-ack', function(str) {
                bg.bglog("select.run: got login-ack: msg = " + str);
                clearTimeout(login_timer);
                var status = bg.extract_json_field(str, "status");
                if (status !== 'ok') {
                    select_page.show_err("Error!", "login error: " + status, function() {
			select_page.hide();
			bg.close_socket();
                        top_level_callback(null);
                    });
		    return;
                }
                top_level_listen();
                bg.bglog('select.run: back from top_level_listen');
		ensure_activity();
            });
        });
    },
};


// don't let user stay on this page with no activity for a very long time
function ensure_activity() {
    got_activity = false;
    if (!!activity_timer)
	clearTimeout(activity_timer);
    activity_timer = setTimeout(function() {
	if (got_activity) {
	    ensure_activity();
	    return;
	}
	var gotOK = false;
	var timer_10sec = setTimeout(function() {
	    bg.bglog('select.top_level_listen: got 10 sec timeout. gotOK = ' + gotOK);
	    select_page.clear_err();
	    if (!gotOK) {
		select_page.hide();
		bg.close_socket();
		top_level_callback(null);
	    }
	}, 10 * 1000);
	select_page.show_err("Are You Still There?", "Press OK to stay on this page, or else you will be logged-out in 10 seconds...", function() {
	    bg.bglog('select.top_level_listen: back from are-you-there');
	    gotOK = true;
	    clearTimeout(timer_10sec);
	    ensure_activity();
	});
    }, 5 * 60 * 1000);
}


function top_level_listen() {
    bg.bglog('in select.top_level_listen');
    get_opponents(function(opponents) {
        select_page.show_opponents(opponents, function(opponent) {
            sock.off('refresh');
            sock.off('challenge');
	    got_activity = true;
            send_challenge(opponent);
        });
    });
    //
    //refesh
    //we expect a simple refresh here -- not a decline or backout
    sock.on('refresh', function(msg) {
        bg.bglog('select.top_level_listen: got refresh. msg = ' + msg);
        sock.off('challenge');
        top_level_listen();
    });
    //
    //challenge
    sock.on('challenge', function(msg) {
        bg.bglog('select.top_level_listen: got challenge...');
        sock.off('refresh');
        //typical msg is:
        // { id: " + id + ", username: " + username + ", wager: " + wager + " }
        var challenger_id = bg.extract_json_field(msg, "id");
        var challenger_name = bg.extract_json_field(msg, "username");
        var wager = bg.extract_json_field(msg, "wager");
        bg.bglog('select.listen: got challenge. challenger id/name/wager = ' + challenger_id + "/" + challenger_name + "/" + wager);
        handle_incoming_challenge(challenger_id, challenger_name, wager);
    });

}


function handle_incoming_challenge(challenger_id, challenger_name, wager) {
    select_page.show_you_are_challenged(challenger_name, wager, function(response) {
        sock.off('refresh');
	got_activity = true;
        if (response      == select_page.Challenge_Response.ACCEPTED    ||
            response.text == select_page.Challenge_Response.ACCEPTED.text) {
            bg.bglog('select.handle_incoming_challenge: challenge is accepted!');
            select_page.hide();
	    if (!!activity_timer)
		clearTimeout(activity_timer);
            var accept_msg = "opponent-id: " + challenger_id + " wager: " + wager;
            sock.emit('challenge-accepted', accept_msg);
            //set up the game
            var play_parameters = new play.Play_Parameters(select_parms.my_id, select_parms.my_username, select_parms.acct_addr,
                                                           select_parms.private_key, select_parms.my_balance, challenger_id, challenger_name, wager, false);
            play.run(play_parameters, top_level_callback);
            return;
        }
        if (response      == select_page.Challenge_Response.DECLINED    ||
            response.text == select_page.Challenge_Response.DECLINED.text) {
            bg.bglog('select.handle_incoming_challenge: challenge is declined!');
            sock.emit('decline', '');
            top_level_listen();
            return;
        }
        bg.bglog('select.handle_incoming_challenge: unknown response: ' + response + ' (' + response.text + ')');
    });
    sock.on('refresh', function(msg) {
        bg.bglog('select.top_level_listen: got refresh. msg = ' + msg);
        if (msg == "backout") {
            select_page.show_opponent_backed_out(challenger_name, function() {
                top_level_listen();
            });
        } else {
            top_level_listen();
        }
    });
}


function send_challenge(opponent) {
    bg.bglog("select.send_challenge: opponent.id = " + opponent.id);
    var wager = 15;
    var challenge_msg = "my-id: " + select_parms.my_id + " opponent-id: " + opponent.id + " wager: " + select_parms.max_raise;
    bg.bglog("select.send_challenge: challenge_msg = " + challenge_msg);
    sock.emit('challenge', challenge_msg);
    select_page.show_waiting_for_response(opponent.username, 30, function() {
        //if we get this callback, it means that we backed out...
	got_activity = true;
        sock.off('refresh');
        sock.off('challenge-accepted');
        sock.emit('backout');
        top_level_listen();
    });
    //wait for response
    //refesh -- we expect refresh/decline. if we get any other refresh then abort the challenge
    sock.on('refresh', function(msg) {
        bg.bglog('select.send_challenge: got refresh. msg = ' + msg);
        sock.off('challenge-accepted');
        if (msg == "decline") {
            select_page.show_opponent_declined_your_challenge(opponent.username, function() {
                top_level_listen();
            });
        } else {
            top_level_listen();
        }
    });
    sock.on('challenge-accepted', function(msg) {
        bg.bglog('send_challenge: got challenge-accepted...');
        sock.off('refresh');
        select_page.hide();
	if (!!activity_timer)
	    clearTimeout(activity_timer);
        //set up the game
        var play_parameters = new play.Play_Parameters(select_parms.my_id, select_parms.my_username, select_parms.acct_addr,
                                                       select_parms.private_key, select_parms.my_balance, opponent.id, opponent.username, select_parms.max_raise, true);
        play.run(play_parameters, top_level_callback);
    });
    //var set_wager_msg = "{ id: " + my_id + ", username: " + my_username + ", wager: " + wager + " }";
    //opposing_socket.emit('set-wager', set_wager_msg);
}


var refresh_is_in_process = false;
function get_opponents(callback) {
    bg.bglog('select.get_opponents');
    if (refresh_is_in_process) {
        return;
    }
    refresh_is_in_process = true;
    var opponents_URL = strings.server_url + '/random_players' + '?id=' + select_parms.my_id + '&username=' + select_parms.my_username + '&level=' + current_level;
    bg.better_fetch(opponents_URL, function(str, err) {
        bg.bglog('select.get_opponents: resp = ' + str);
        if (!str || !!err) {
            bg.bglog('select.get_opponents: err = ' + err);
            select_page.show_err("Error!", 'error retrieving opponent list from server: ' + err, function() {
                refresh_is_in_process = false;
                callback([]);
            });
            return;
        }
        //typical response id:
        //{
        //  "status": 1,
        //  "xxx": [
        //       {
        //         "id": "1067",
        //         "level": "expert",
        //         "username": "Bob Dobalina",
        //         "address": "0x85d9147b0ec6d60390c8897244d039fb55b087c6",
        //         "online": "true"
        //       },
        //       .....
        //      ]
        //   }
        var status = bg.extract_json_field(str, 'status');
        if (status !== 'ok') {
            select_page.show_err("Error!", 'error in opponent list from server: ' + status, function() {
                refresh_is_in_process = false;
                callback([]);
            });
            return;
        }
        var idx = 0;
        var opponents = [];
        for (var i = 0; i < 30; i++) {
            idx = str.indexOf('{');
            if (idx === -1)
                break;
            str = str.substring(idx + 1);
            var username = bg.extract_json_field(str, 'username');
            var level = bg.extract_json_field(str, 'level');
            var id = bg.extract_json_field(str, 'id');
            if (username == "" || level == "" || id == "")
                break;
            var opponent = new select_page.Opponent(id, username, level);
            opponents.push(opponent);
            bg.bglog('select.get_opponents[' + opponents.length + ']: id = ' + id + ', username = ' + username + ', level = ' + level);
            idx = str.indexOf('}');
            if (idx === -1)
                break;
            str = str.substring(idx + 1);
        }
        refresh_is_in_process = false;
        callback(opponents);
        return;
    });
}
