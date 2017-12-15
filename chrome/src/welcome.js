
//var $ = require('jquery')
var bg = require('./background');
var util = require('./util');
var strings = require('./strings');
var welcome_page = require('./welcome_page');
var ether = require('./ether');
var select = require('./select');

var play_page = require('./play_page');

//
var VERSION_NAME = "TEST-XXX";
var top_level_callback = null;
var registered = false;
var max_raises = [0, 0, 0];

var my_id = "";
var my_username = "";
var acct_addr = "";
var private_key = "";
var expectedBalance = 0;
var verifiedBalance = 0;
var welcome_has_been_shown = false;
var welcome = module.exports = {

    //
    // these are the callvack values for welcome.run()
    //
    Welcome_Status: {
	SHOW_AGAIN                       : { text: "show again"                       },
	EXIT                             : { text: "exit"                             }
    },


    run: function(callback) {

	// this is for testing/tweaking positions
	if (false) {
	    welcome_page.hide();
	    play_page.show(function() {
		play_page.show_balance(19);
		play_page.show_pot();
		msg_cookie = play_page.show_status_msg('this is a long long long long long long long long long status message');
		play_page.get_bet_or_fold(0, 10, function(bet_size, fold) {
		});

		var my_cards_to_process = [ 0, 1, 2, 3, 4 ];
		var opponent_cards_to_process = [ 0, 1, 2, 3, 4 ];
		var my_card_strs = [ "", "", "", "", "" ];
		var opponent_card_strs = [ "", "", "", "", "" ];
		deal_cards_serially(my_cards_to_process, 0, my_card_strs, opponent_cards_to_process, 0, opponent_card_strs, function() {
		});
	    });
	    return;
	}

	bg.bglog('in welcome.run');
	util.find_unique_id(function(id) {
	    my_id = id;
	    bg.retrieve_username(function(name) {
		my_username = name;
		util.retrieve_acct(my_id, function(acct) {
		    acct_addr = acct;
		    private_key = util.get_private_key(my_id);
		    top_level_callback = callback;
		    welcome_page.show_preliminary(my_username, acct_addr);
		    if (!!my_username && !!acct_addr) {
			if (registered == false) {
			    var aes_key = bg.get_aes_key();
			    bg.bglog('welcome.run: aes_key = ' + aes_key);
			    var with_key =  (!aes_key) ? true : false;
			    //if we don't have a key then we know we'll need to generate a new one. otherwaise try first w/o a key
			    register(with_key);
			} else if (max_raises[0] <= 0 || max_raises[1] <= 0 || max_raises[2] <= 0) {
			    get_table_parms();
			} else {
			    welcome_page.show_complete(my_username, acct_addr, expectedBalance,
						       new_username_fcn, new_acct_fcn, refresh_balance_fcn,
						       select_table_fcn, max_raises);
			    refresh_balance_fcn();
			}
		    } else {
			bg.bglog('welcome.run: something is missing....  my_username = ' + my_username + ', acct_addr = ' + acct_addr);
			welcome_page.show_complete(my_username, acct_addr, 0,
						   new_username_fcn, new_acct_fcn, refresh_balance_fcn,
						   null, null);
			if (!welcome_has_been_shown) {
			    welcome_has_been_shown = true;
			    welcome_page.show_big_dialog(strings.welcome_msg_title, strings.welcome_msg0 + strings.welcome_msg1 + strings.welcome_msg2, function() {
				bg.bglog('welcome.run: back from show_big_dialog');
				if (!my_username)
				    welcome_page.changeUserName();
				else
				    welcome_page.changeAccount();
			    });
			} else {
			    if (!my_username)
				welcome_page.changeUserName();
			    else if (!acct_addr)
				welcome_page.changeAccount();
			}
		    }
		});
	    });
	});
    },
};


//if you don't know whether we have registered a decryption key before, then set with_key=false. then we will attempt
//to register w/o a key. if the server says we need a key then we'll try again with a new key.
function register(with_key) {
    bg.bglog('in welcome.register: with_key = ' + with_key);
    var ok = false;
    var status_msg_cookie = welcome_page.show_status_msg("registering... server: " + strings.server_url);
    bg.bglog('welcome.run: register: addr = ' + acct_addr + ', id = ' + my_id + ', name = ' + my_username);
    var key_parm = "";
    if (with_key) {
	var old_key = bg.get_aes_key();
	var new_key = (!!old_key) ? old_key : util.aes_key_generator();
	bg.bglog('welcome.register: aes key = ' + new_key + '; length = ' + new_key.length + '; type = ' + typeof(new_key));
	key_parm = "&key=" + new_key;
    }
    var register_URL = strings.server_url + "/register?id=" + my_id + "&username=" + my_username + "&addr=" + acct_addr + key_parm;
    bg.bglog('welcome.register: url = ' + register_URL);
    var timeout_occured = false;
    var register_timer = setTimeout(function() {
	timeout_occured = true;
	welcome_page.clear_status_msg(status_msg_cookie);
	welcome_page.show_err("Error!", "registration error: timeout -- no response from server", function() {
	    setTimeout(welcome.run, 45 * 1000, top_level_callback);
	});
    }, 10000);
    bg.better_fetch(register_URL, function(str, err) {
	if (timeout_occured)
	    return;
	clearTimeout(register_timer);
	welcome_page.clear_status_msg(status_msg_cookie);
	bg.bglog('welcome.register: resp = ' + str);
	if (str === "" || err !== "") {
	    bg.bglog("welcome.register: err = " + err);
	    welcome_page.show_err("Error!", "error registering with server: " + err, function() {
		welcome.run(top_level_callback);
	    });
	} else {
	    //typical response is:
	    // {"status":"ok","giveaway":null,"have_key":"false"}
	    var status = bg.extract_json_field(str, "status");
            var have_key = bg.extract_json_field(str, "have_key");
	    //note: these two messages must match the messages from the server
	    if (status == "username is taken" || status == "account is already in use") {
		status_msg_cookie = welcome_page.show_status_msg("error registering with server: " + status);
		welcome_page.show_err("Error!", status, function() {
		    if (status == "username is taken")
			bg.save_username("");
		    else
			util.save_private_key(my_id, "");
		    welcome.run(top_level_callback);
		});
	    } else if (status === "ok") {
		bg.bglog("welcome.register: status is \"ok\"");
                if (have_key == 'true') {
                    if (new_key != old_key)
			bg.save_aes_key(new_key);
		    registered = true;
		    welcome.run(top_level_callback);
		} else if (!with_key) {
		    //if we tried just now w/o a key, then just try again with a key
		    register(true);
		    return;
		} else {
		    //we tried with a key, but the server didn'y like it. so delete it.
		    bg.save_aes_key('');
		    registered = false;
		    welcome.run(top_level_callback);
		}
	    } else {
		bg.bglog("welcome.register: status = X" + status + "X");
		registered = false;
		status_msg_cookie = welcome_page.show_status_msg("error registering with server: " + status);
		welcome_page.show_err("Error!", "error registering with server: " + status, function() {
		    welcome.run(top_level_callback);
		});
	    }
	}
    });
}


function get_table_parms() {
    bg.bglog('in welcome.get_table_parms');
    var status_msg_cookie = welcome_page.show_status_msg("getting table parameters...");
    var table_parms_URL = strings.server_url + "/table_parms";
    var timeout_occured = false;
    var table_parms_timer = setTimeout(function() {
	timeout_occured = true;
	welcome_page.clear_status_msg(status_msg_cookie);
	welcome_page.show_err("Error!", "unable to retrieve table parms: timeout -- no response from server", function() {
	    welcome.run(top_level_callback);
	});
    }, 10000);
    bg.better_fetch(table_parms_URL, function(str, err) {
	if (timeout_occured)
	    return;
	clearTimeout(table_parms_timer);
	welcome_page.clear_status_msg(status_msg_cookie);
	bg.bglog('welcome.get_table_parms: resp = ' + str);
	if (str === "" || err !== "") {
	    bg.bglog("welcome.get_table_parms: err = " + err);
	    welcome_page.show_err("Error!", "error retrieving table parms from server: " + err, function() {
		welcome.run(top_level_callback);
	    });
	} else {
	    var status = bg.extract_json_field(str, "status");
	    if (status != "ok") {
		welcome_page.show_err("Error!", "error in table parms from server: " + status, function() {
		    welcome.run(top_level_callback);
		});
	    } else {
                var idx = 0;
                var msg = bg.extract_json_field(str, "msg");
                var title = bg.extract_json_field(str, "title");
                for (var i = 0; i < 3; ++i) {
                    idx = str.indexOf("table" + i);
                    if (idx < 0) {
			bg.bglog("welcome.get_table_parms: error parsing table parms, looking for table " + i + ": " + str);
			welcome_page.show_err("Error!", "error retrieving table parms from server: parse error", function() {
			    welcome.run(top_level_callback);
			});
                        break;
                    } else {
                        idx += 6;
			str = str.substring(idx);
			var maxraise_str = bg.extract_json_field(str, "maxraise");
                        var max_raise = parseInt(maxraise_str, 10);
			if (isNaN(max_raise)) {
                            bg.bglog("welcome.get_table_parms: error parsing table parms, table " + i + ": " + str);
                            break;
			}
			max_raises[i] = max_raise;
                    }
                }
                if (max_raises[0] > 0 && max_raises[1] > 0 && max_raises[2] > 0) {
		    welcome.run(top_level_callback);
		} else {
                    if (msg.isEmpty())
                        msg = "error retrieving table parms from server: parse error";
                    if (title.isEmpty())
                        title = "Server Error";
		    welcome_page.show_err(title, msg, function() {
			welcome.run(top_level_callback);
		    });
                }
            }
	}
    });
}


function new_username_fcn(username) {
    var badCharRE = /[ \]\[\)\(&?'":\/\\%,|*^]/g;
    username = username.replace(badCharRE, '');
    bg.save_username(username);
    //generate a new aes key any time the user changes his name
    bg.save_aes_key('');
    bg.bglog('welcome.new_username_fcn got username...' + username);
    registered = false;
    welcome.run(top_level_callback);
}

function new_acct_fcn(private_key) {
    if (private_key.startsWith('0x'))
	private_key = private_key.substring(2);
    var badCharRE = /[ \]\[\)\(&?'":\/\\%,|*^]/g;
    private_key = private_key.replace(badCharRE, '');
    var addr = ether.private_key_to_addr(private_key, function(err, acct) {
	if (!!err) {
	    welcome_page.show_err('Private Key is Not Valid', err, function() {
		registered = false;
		welcome.run(top_level_callback);
	    });
	} else {
	    util.save_private_key(my_id, private_key);
	    bg.bglog('welcome.new_acct_fcn: got private key...' + private_key);
	    registered = false;
	    welcome.run(top_level_callback);
	}
    });
}

function refresh_balance_fcn() {
    bg.bglog('welcome.refresh_balance_fcn: enter');
    var nowMS = Math.floor(Date.now());
    if (!!acct_addr && !SBA1S_timer) {
	welcome_page.show_balance('refreshing balance...', 0);
	ether.refresh_balance(acct_addr,
			  function(expected) {
			      bg.bglog('welcome.refresh_balance_fcn: got expected = ' + expected);
			      expectedBalance = expected;
			      show_balance_after_1sec(nowMS);
			  },
			  function(verified) {
			      bg.bglog('welcome.refresh_balance_fcn: got verified = ' + verified);
			      expectedBalance = verifiedBalance = verified;
			      nowMS = Math.floor(Date.now());
			      show_balance_after_1sec(nowMS);
			  });
    }
}


var SBA1S_timer = null;
function show_balance_after_1sec(startMS) {
    bg.bglog('welcome.show_balance_after_1sec: expected = ' + expectedBalance + ', verified = ' + verifiedBalance);
    var nowMS = Math.floor(Date.now());
    if (nowMS - startMS > 800) {
	if (!!SBA1S_timer)
	    clearTimeout(SBA1S_timer);
	SBA1S_timer = null;
	welcome_page.show_balance(null, expectedBalance, verifiedBalance);
    } else {
	SBA1S_timer = setTimeout(show_balance_after_1sec, 900, startMS);
    }
}

function select_table_fcn(table_idx) {
    bg.bglog('welcome.select_table_fcn: table_idx = ' + table_idx);
    var min_bal_multiple = 5;
    if (expectedBalance < min_bal_multiple * max_raises[table_idx]) {
	var msg = strings.Balance_Too_Low_Msg;
	msg = msg.replace("BAL_MULTIPLE", min_bal_multiple.toString());
        msg = msg.replace("MIN_WAGER", max_raises[table_idx].toString());
	msg = msg.replace("WHAT_TO_DO", strings.Get_More_Finney_Str);
	welcome_page.show_err(strings.Balance_Too_Low_Title_Str, msg, function() {
	    welcome.run(top_level_callback);
	});
	return;
    }
    ether.get_block_count(function(block_count) {
	bg.bglog(block_count.toString(10));
	bg.bglog('welcome.select_table_fcn: block_cnt = ' + block_count);
	welcome_page.hide();
	//select will go directly to play if a game is initiated; else it will return to here with a null parameter.
	//if a game completes, then we'll get the callback at the end of the game with the payout info.
	var select_parms = new select.Select_Paramters(my_id, my_username, acct_addr, private_key, expectedBalance,
						       table_idx, max_raises[table_idx], null/*groups*/);
	bg.bglog('calling select.run...');
	select.run(select_parms, function(payout_info) {
	    bg.bglog('welcome.select_table_fcn: back from select.run. payout_info = ' + payout_info);
	    if (!!payout_info) {
		welcome_page.show_big_dialog(strings.payout_advisory_title, payout_info, function() {
		    welcome.run(top_level_callback);
		});
	    } else {
		welcome.run(top_level_callback);
	    }
	});
    });
}


function deal_cards_serially(my_cards_to_process, my_idx, my_card_strs, opponent_cards_to_process, opponent_idx, opponent_card_strs, callback) {
    if (opponent_idx < opponent_cards_to_process.length) {
	var card_idx = opponent_cards_to_process[opponent_idx];
	bg.bglog('play.deal_cards_serially: dealing opponent card ' + opponent_idx + ', idx = ' + card_idx + ', str = ' + opponent_card_strs[card_idx]);
	play_page.deal(play_page.who.OPPONENT, card_idx, opponent_card_strs[card_idx], function() {
	    deal_cards_serially(my_cards_to_process, my_idx, my_card_strs, opponent_cards_to_process, opponent_idx + 1, opponent_card_strs, callback);
	});
	//return here in order to complete opponent deal before beginning my deal. if we wanted to do both simultaniously,
	//then we could remove this return. in that case we would need to pass my_cards_to_process.length in place of my_idx
	//in the deal_cards_serially recursive call above, to avoid double-dealing my cards.
	return;
    }
    if (my_idx < my_cards_to_process.length) {
	var card_idx = my_cards_to_process[my_idx];
	bg.bglog('play.deal_cards_serially: dealing my card ' + my_idx + ', idx = ' + card_idx + ', str = ' + my_card_strs[card_idx]);
	play_page.deal(play_page.who.ME, card_idx, my_card_strs[card_idx], function() {
	    deal_cards_serially(my_cards_to_process, my_idx + 1, my_card_strs, opponent_cards_to_process, opponent_idx, opponent_card_strs, callback);
	});
	return;
    }
    if (opponent_idx >= opponent_cards_to_process.length && my_idx >= my_cards_to_process.length)
	callback();
}
