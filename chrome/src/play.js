
var bg = require('./background');
var strings = require('./strings');
var util = require('./util');
var play_page = require('./play_page');
var ether = require('./ether');
var socket = require('socket.io-client');

var sock = null;
var game_over = false;
var play_parms = null;
var top_level_callback = null;
//{my,opponent}_card_strs is an array of short strings. each string represents a card. my_{beg,end}_string_verify is just the
//concatination of card_str's (with a hyphin between card_str's) for the initial deal and post-replaced hands.
var my_card_strs = [ "", "", "", "", "" ];
var opponent_card_strs = [ "", "", "", "", "" ];
var my_beg_string_verify = "";
var my_end_string_verify = "";
var card_strings_a = null;
var card_strings_b = null;
var card_strings_c = null;
var my_cut_select = "";
var opponent_cut_select = "";
var my_strings_idx = 0;
var opponent_strings_idx = 0;
var last_msg_was_payment = false;
var opponent_is_betting = false;
//this is the default delay that is allowed for various responses, in particular it is the extra delay time that is allocated
//when the server sends us a delay message. it can be set from all ante and wait_opponent_ante messages. in general we don't enforce
//the timeouts; but instead we let the server tell us when a timeout has occurred.
var max_processing_delay_sec = 30;
var my_bet_cnt = 0;
var my_tx_cnt = 0;
var my_tx_total = 0;
var pot_balance = 0;
var did_discard = false;
var start_balance = 0;
var game_id = null;
var msg_cookie = null;
var countdown_cookie = null;
//
var WEI_PER_FINNEY = 1000000000000000;
var POKER_BET_NOMINAL_GAS = 50000;
var POKER_BET_GAS_LIMIT =  100000;



var play = module.exports = {

    Play_Parameters: function(my_id, my_username, acct_addr, private_key, my_balance, op_id, op_name, wager, am_challenger) {
	this.my_id = my_id;
	this.my_username = my_username;
	this.acct_addr = acct_addr;
	this.private_key = private_key;
	this.my_balance = parseInt(my_balance);
	this.opponent_id = op_id;
	this.opponent_username = op_name;
	this.wager = parseInt(wager);
	this.iamchallenger = am_challenger;
	start_balance = my_balance;
    },


    run: function(play_parameters, callback) {
	bg.bglog('play.run');
	play_parms = play_parameters;
	top_level_callback = callback;
	my_tx_cnt = 0;
	my_tx_total = 0;
	pot_balance = 0;
	my_strings_idx = 0;
	opponent_strings_idx = 0;
	game_over = false;
	last_msg_was_payment = false;
	sock = bg.get_socket(false);
	play_page.show(function() {
            play_page.show_balance(play_parms.my_balance);
	    var card_strings_URL = strings.server_url + '/card_strings' + '?id=' + play_parms.my_id;
	    bg.better_fetch(card_strings_URL, function(str, err) {
		bg.bglog('play.run: card_strings...');
		if (str === "" || err !== "") {
		    bg.bglog('play.run: card_strings err = ' + err);
		    play_page.show_err('Error!', 'error retrieving opponent encrypted card strings from server: ' + err, function() {
			play_page.hide();
			top_level_callback(null);
		    });
		    return;
		}
		var status = bg.extract_json_field(str, 'status');
		bg.bglog('play.run: card_strings: status = ' + status);
		if (status == 'ok') {
		    card_strings_a = save_card_strings('card_strings_a', str);
		    card_strings_b = save_card_strings('card_strings_b', str);
		    card_strings_c = save_card_strings('card_strings_c', str);
		} else {
		    bg.bglog('play.run: card_strings err status = ' + err);
		    play_page.show_err('Error!', 'error retrieving opponent encrypted card strings from server: ' + status, function() {
			play_page.hide();
			top_level_callback(null);
		    });
		    return;
		}
	    });
	    var ready_msg = 'my-id: ' + play_parms.my_id + ' opponent-id: ' + play_parms.opponent_id;
	    bg.bglog('play.run: sending ready-to-play: ' + ready_msg);
	    sock.emit('ready-to-play', ready_msg);
	    //String user_msg = getResources().getString(R.string.game_start);
	    //show_user_msg(user_msg, 2, COUNTDOWN.NO_COUNTDOWN);
	    top_level_listen();
	});
    },
}


function top_level_listen() {
    bg.bglog('in play.top_level_listen');
    sock.on('ante', on_ante);
    sock.on('bet', on_bet);
    sock.on('forfeit', on_forfeit);
    sock.on('deal', on_deal);
    sock.on('discard', on_discard);
    sock.on('reveal', on_reveal);
    sock.on('result', on_result);
    //
    sock.on('wait-opponent-ante', function(msg) {
	bg.bglog('play.top_level_listen: got wait-opponent-ante');
        last_msg_was_payment = false;
        var max_delay_str = bg.extract_json_field(msg, 'max_delay');
	if (!!max_delay_str)
            max_processing_delay_sec = parseInt(max_delay_str, 10);
	var wait_msg = strings.Wait_For_Opponent_Ante_Msg;
	wait_msg = wait_msg.replace('OPPONENT', play_parms.opponent_username);
	msg_cookie = play_page.show_status_msg(wait_msg, 60);
    });
    sock.on('wait-opponent-bet', function(msg) {
	bg.bglog('play.top_level_listen: got wait-opponent-bet');
        last_msg_was_payment = false;
	opponent_is_betting = true;
	var timeout_sec = 0;
        var timeout_str = bg.extract_json_field(msg, 'timeout');
	if (!!timeout_str)
            timeout_sec = parseInt(timeout_str, 10);
	var wait_msg = strings.Wait_For_Opponent_Bet_Msg;
	wait_msg = wait_msg.replace('OPPONENT', play_parms.opponent_username);
	msg_cookie = play_page.show_status_msg(wait_msg, timeout_sec);
	countdown_cookie = play_page.show_countdown(play_page.who.OPPONENT, timeout_sec);
    });
    sock.on('wait-opponent-discard', function(msg) {
	bg.bglog('play.top_level_listen: got wait-opponent-discard');
        last_msg_was_payment = false;
	var timeout_sec = 0;
        var timeout_str = bg.extract_json_field(msg, 'timeout');
	if (!!timeout_str)
            timeout_sec = parseInt(timeout_str, 10);
	var wait_msg = strings.Wait_Opponent_Discard_Msg;
	wait_msg = wait_msg.replace('OPPONENT', play_parms.opponent_username);
	msg_cookie = play_page.show_status_msg(wait_msg, timeout_sec);
	countdown_cookie = play_page.show_countdown(play_page.who.OPPONENT, timeout_sec);
    });
    sock.on('delay', function(msg) {
	bg.bglog('play.top_level_listen: got delay');
        last_msg_was_payment = false;
	var timeout_sec = max_processing_delay_sec;
        var timeout_str = bg.extract_json_field(msg, 'timeout');
	if (!!timeout_str)
            timeout_sec = parseInt(timeout_str, 10);
	var wait_msg = strings.Opponent_Deposit_Difficulty_Msg;
	wait_msg = wait_msg.replace('OPPONENT', play_parms.opponent_username);
	wait_msg = wait_msg.replace('SECONDS', timeout_sec);
	msg_cookie = play_page.show_status_msg(wait_msg, timeout_sec);
    });
}

function on_ante(msg) {
    bg.bglog('play.on_ante: ' + msg);
    last_msg_was_payment = false;
    var do_action = bg.extract_json_field(msg, 'do');
    var opponent_did = bg.extract_json_field(msg, 'opponent-did');
    var size_str = bg.extract_json_field(msg, 'size');
    var game_id_hex = bg.extract_json_field(msg, 'game_id');
    var max_delay_str = bg.extract_json_field(msg, 'max_delay');
    var my_cut_str = bg.extract_json_field(msg, 'cut');
    var opponent_cut_str = bg.extract_json_field(msg, 'opponent-cut');
    var ante_size = 0;
    var compound_msg = "";
    if (!!game_id_hex)
        game_id = util.hex_str_to_bytes(game_id_hex);
    if (!!max_delay_str) {
        var max_delay_int = parseInt(max_delay_str, 10);
	if (!isNaN(max_delay_int)) {
	    max_processing_delay_sec = max_delay_int;
	    bg.bglog('play.on_ante: max_processing_delay_sec = ' + max_processing_delay_sec);
	}
    }
    var ante_size = parseInt(size_str, 10);
    if (isNaN(ante_size)) {
	//this is fatal
	bg.bglog('play.on_ante: failed to parse ante size');
	play_page.show_err('Error!', 'error parsing ante directive from server: ' + msg, function() {
	    //abort this game
	    play_page.hide();
	    top_level_callback(null);
	});
    }
    if (opponent_did === 'true') {
	var ante_msg = strings.Opponent_Deposited_Ante_Msg;
	ante_msg = ante_msg.replace('OPPONENT', play_parms.opponent_username);
    	msg_cookie = play_page.show_status_msg(ante_msg, 10);
        play_page.move_funds(play_page.who.OPPONENT, ante_size,  function() {
	    play_page.show_pot();
	    pot_balance += ante_size;
	    play_page.set_pot_balance(pot_balance);
	});
    }
    if (do_action === 'true') {
        if (my_cut_str === 'abc' || my_cut_str === 'ab' || my_cut_str === 'ac' || my_cut_str === 'bc') {
    	    msg_cookie = play_page.show_status_msg(strings.Cutting_the_deck_Msg, 10);
            var selector = Date.now() % my_cut_str.length;
            my_cut_select = my_cut_str.charAt(selector);
            var cut_msg = 'select: ' + my_cut_select;
            bg.bglog('play.on_ante: sending did-cut msg = ' + cut_msg);
            sock.emit('did-cut', cut_msg);
        } else {
            //keep the backend server honest! the challenger should do the first cut and then deposit his ante. if we get an ante message without a cut, then
	    //we must be the challengee, and it should just be to inform us that the challenger has already deposited his ante. that is, it should have no do_action.
            var fairness_title = strings.Fairness_Bug_Title_Str;
            var fairness_str = strings.Fairness_Bug_Message_Str;
	    play_page.show_err(fairness_title, fairness_str, function() {
		//abort this game
		play_page.hide();
		top_level_callback(null);
	    });
        }
	var ante_msg = strings.Depositing_Ante_Msg;
	ante_msg = ante_msg.replace('SIZE', ante_size.toString());
    	msg_cookie = play_page.show_status_msg(ante_msg, 10);
        play_page.move_funds(play_page.who.ME, ante_size, function() {
	    play_page.show_pot();
	    pot_balance += ante_size;
	    play_page.set_pot_balance(pot_balance);
	});
	play_parms.my_balance -= ante_size;
        play_page.show_balance(play_parms.my_balance);
	bg.save_estimated_balance(play_parms.acct_addr, play_parms.my_balance);
        send_funds('did-ante', ante_size);
    }
    if (!!opponent_cut_str) {
        opponent_cut_select = opponent_cut_str.charAt(0);
	bg.bglog('play.on_ante: opponent_cut_select = ' + opponent_cut_select);
	var cut_msg = strings.Opponent_Cut_Deck_Msg;
	cut_msg = cut_msg.replace('OPPONENT', play_parms.opponent_username);
    	msg_cookie = play_page.show_status_msg(cut_msg, 10);
    }
}


function on_bet(msg) {
    bg.bglog('play.on_bet: ' + msg);
    last_msg_was_payment = false;
    var do_action = bg.extract_json_field(msg, 'do');
    var opponent_bet = bg.extract_json_field(msg, 'opponent-bet');
    var opponent_raise = bg.extract_json_field(msg, 'opponent-raise');
    var timeout = bg.extract_json_field(msg, 'timeout');
    var opponent_bet_size = 0;
    var opponent_raise_size = 0;
    var timeout_sec = 0;
    if (!!opponent_bet)
        opponent_bet_size = parseInt(opponent_bet, 10);
    if (!!opponent_raise)
        opponent_raise_size = parseInt(opponent_raise, 10);
    if (!!timeout)
        timeout_sec = parseInt(timeout, 10);
    bg.bglog('play.on_bet: do = ' + do_action + ', opponent_bet_size = ' + opponent_bet_size + ', opponent_raise_size = ' + opponent_raise_size + ', timeout_sec = ' + timeout_sec);
    //
    //figure out what prompt should be
    //
    var bet_prompt = "";
    if (do_action == 'true') {
	if (opponent_bet_size == 0 && opponent_raise_size == 0) {
	    if (opponent_is_betting) {
		bet_prompt = strings.Opponent_Checks_Msg;
		bet_prompt = bet_prompt.replace('OPPONENT', play_parms.opponent_username);
	    }
	} else if (opponent_raise_size > 0 && my_bet_cnt > 0) {
            bet_prompt = strings.Opponent_Has_Raised_You_Msg;
	    bet_prompt = bet_prompt.replace('OPPONENT', play_parms.opponent_username);
	    bet_prompt = bet_prompt.replace('SIZE', opponent_raise_size);
	} else if (opponent_bet_size > 0) {
	    bet_prompt = strings.Opponent_Has_Bet_Msg;
	    bet_prompt = bet_prompt.replace('OPPONENT', play_parms.opponent_username);
	    bet_prompt = bet_prompt.replace('SIZE', opponent_bet_size);
	}
        bet_prompt += ((!!bet_prompt) ? ' ' : '') + strings.Place_Your_Bet_Str;
    } else if (opponent_bet_size > 0) {
        //server is not asking us to bet, but informing us that the opponennt bet.... he must have called our bet
        if (opponent_raise_size != 0)
            bg.bglog('play.bet: hey! raise is nz, ' + opponent_raise_size + ', why is do = ' + do_action + '?');
        bet_prompt = strings.Opponent_Calls_Msg;
	bet_prompt = bet_prompt.replace('OPPONENT', play_parms.opponent_username);
    } else {
        //server is not asking us to bet, and informing us that either the opponent bet nothing (ie. he checked our check), or perhaps we just
	//called (or checked) and the server is telling us that betting is done.
        bet_prompt = strings.Betting_Is_Complete_Str;
    }
    if (opponent_bet_size > 0) {
        play_page.move_funds(play_page.who.OPPONENT, opponent_bet_size, function() {
	    pot_balance += opponent_bet_size;
	    play_page.set_pot_balance(pot_balance);
	});
    }
    bg.bglog('play.on_bet: msg = ' + bet_prompt);
    opponent_is_betting = false;
    //
    //now get user's bet, or just display message if we are done betting
    if (do_action != 'true') {
	msg_cookie = play_page.show_status_msg(bet_prompt, 60);
    } else {
        //after this bet the opponent can raise us to wager. so if this is the last round of betting, (after discards), then
        //we need to keep wager on hand (so we can call). that means the max amount we can spend now is balance - wager
        //btw, the extra 5 finney is to cover gas.
        var opponent_wager_opportunity = did_discard ? play_parms.wager : play_parms.wager * 2;
	var min_bet = opponent_raise_size;
	var max_bet = Math.min(min_bet + play_parms.wager, play_parms.my_balance - opponent_wager_opportunity - my_tx_cnt - 5);
        if (max_bet < min_bet + play_parms.wager) {
	    bg.bglog('play.on_bet: limiting -- opponent_opportunity = ' + opponent_wager_opportunity + ', min = ' + min_bet +
		     ', wager = ' + play_parms.wager + ', max = ' + max_bet);
	    bet_prompt += strings.Bet_Limited_Str;
	}
	msg_cookie = play_page.show_status_msg(bet_prompt, 60);
	countdown_cookie = play_page.show_countdown(play_page.who.ME, timeout_sec);
	play_page.get_bet_or_fold(min_bet, max_bet, function(bet_size, fold) {
	    clear_messages();
	    if (fold) {
		game_over = true;
		sock = bg.close_socket();
		show_forfeit_msg(true, false, null, function() {
		    play_page.hide();
		    top_level_callback(null);
		});
	    } else {
                ++my_bet_cnt;
		if (bet_size > 0) {
		    var bet_msg = strings.Placing_Bet_Msg;
		    bet_msg = bet_msg.replace('SIZE', bet_size.toString());
		    msg_cookie = play_page.show_status_msg(bet_msg, 60);
		    play_page.move_funds(play_page.who.ME, bet_size, function() {
			pot_balance += bet_size;
			play_page.set_pot_balance(pot_balance);
		    });
		    play_parms.my_balance -= bet_size;
		    play_page.show_balance(play_parms.my_balance);
		    bg.save_estimated_balance(play_parms.acct_addr, play_parms.my_balance);
		}
		send_funds('did-bet', bet_size);
	    }
	});
    }
}


function on_deal(msg) {
    bg.bglog('play.on_deal: ' + msg);
    clear_messages();
    last_msg_was_payment = false;
    msg_cookie = play_page.show_status_msg('dealing...', 60);
    var decrypted_msg = msg;
    var iv = bg.extract_json_field(msg, 'iv');
    var encrypted_msg = bg.extract_json_field(msg, 'msg');
    if (!!iv && !!encrypted_msg) {
	var key = bg.get_aes_key();
        decrypted_msg = util.aes_decrypt(encrypted_msg, iv, key);
	bg.bglog('play.on_deal: decrypted = ' + decrypted_msg);
    }
    process_card_msg(decrypted_msg, function(my_cards_to_process, my_card_strs, opponent_cards_to_process, opponent_card_strs) {
	deal_cards_serially(my_cards_to_process, 0, my_card_strs, opponent_cards_to_process, 0, opponent_card_strs, function() {
	    var did_deal_msg = "opponent-id: " + play_parms.opponent_id;
	    bg.bglog('play.on_deal: sending did-deal msg = ' + did_deal_msg);
	    sock.emit("did-deal", did_deal_msg);
	});
    });
}



function on_reveal(msg) {
    bg.bglog('play.on_reveal: ' + msg);
    clear_messages();
    last_msg_was_payment = false;
    process_card_msg(msg, function(my_cards_to_process, my_card_strs, opponent_cards_to_process, opponent_card_strs) {
	reveal_cards_serially(my_cards_to_process, 0, my_card_strs, opponent_cards_to_process, 0, opponent_card_strs, function() {
	    var did_reveal_msg = "opponent-id: " + play_parms.opponent_id;
	    bg.bglog('play.on_reveal: sending did-reveal msg = ' + did_reveal_msg);
	    sock.emit("did-reveal", did_reveal_msg);
	});
    });
}


//
//process deal or reveal message
//callback parms:
// my_cards_to_process:         array of int indicies of my cards that are specified in this message
// my_card_strs:                array of card specs. index into this array via my_cards_to_process[i]
// opponent_cards_to_process:   array of int indicies of opponent cards that are specified in this message
// opponent_card_strs:          array of card specs. index into this array via opponent_cards_to_process[i]
//
function process_card_msg(msg, callback) {
    //typical deal:
    //{ my-card0: 7c, opponent-card0: true, my-card1: 6h, opponent-card1: true, my-card2: 9s, opponent-card2: true,
    //  my-card3: 4d, opponent-card3: true, my-card4: 8d, opponent-card4: true }
    //typical reveal:
    //{ opponent-card0: 6h, opponent-card1: 9s, opponent-card2: 4d, opponent-card3: 8d, opponent-card4: 3s }
    var my_cards_to_process = [];
    var opponent_cards_to_process = [];
    for (var i = 0; i < 5; ++i) {
	var my_card_id = 'my-card' + i;
	var opponent_card_id = 'opponent-card' + i;
        var my_card_str = bg.extract_json_field(msg, my_card_id);
        var opponent_card_str = bg.extract_json_field(msg, opponent_card_id);
	if (!!my_card_str) {
	    my_cards_to_process.push(i);
            my_card_strs[i] = my_card_str;
	}
	if (!!opponent_card_str) {
	    opponent_cards_to_process.push(i);
	    opponent_card_strs[i] = (opponent_card_str == 'true') ? "" : opponent_card_str;
	}
    }
    if (my_cards_to_process.length == 5) {
	//initial deal; save card string for later verification
        my_beg_string_verify = "";
        for (var i = 0; i < my_card_strs.length; ++i) {
            if (i > 0)
                my_beg_string_verify += "-";
            my_beg_string_verify += my_card_strs[i];
        }
        //in case we don't discard at all
        my_end_string_verify = my_beg_string_verify;
    } else if (my_cards_to_process.length > 0) {
	//replacement deal; save card string for later verification
        my_end_string_verify = "";
        for (var i = 0; i < my_card_strs.length; ++i) {
            if (i > 0)
                my_end_string_verify += "-";
            my_end_string_verify += my_card_strs[i];
        }
    }
    callback(my_cards_to_process, my_card_strs, opponent_cards_to_process, opponent_card_strs);
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


function reveal_cards_serially(my_cards_to_process, my_idx, my_card_strs, opponent_cards_to_process, opponent_idx, opponent_card_strs, callback) {
    if (opponent_idx < opponent_cards_to_process.length) {
	var card_idx = opponent_cards_to_process[opponent_idx];
	bg.bglog('play.reveal_cards_serially: revealing opponent card ' + opponent_idx + ', idx = ' + card_idx + ', str = ' + opponent_card_strs[card_idx]);
	play_page.reveal_opponent_card(/*play_page.who.OPPONENT, */card_idx, opponent_card_strs[card_idx], function() {
	    reveal_cards_serially(my_cards_to_process, my_idx, my_card_strs, opponent_cards_to_process, opponent_idx + 1, opponent_card_strs, callback);
	});
	//return here in order to complete opponent deal before beginning my deal. if we wanted to do both simultaniously,
	//then we could remove this return. in that case we would need to pass my_cards_to_process.length in place of my_idx
	//in the deal_cards_serially recursive call above, to avoid double-dealing my cards.
	return;
    }
    if (my_idx < my_cards_to_process.length) {
	bg.bglog('play.reveal_cards_serially: HEY! HEY! HEY! we should not be doing this!');
	var card_idx = my_cards_to_process[my_idx];
	bg.bglog('play.reveal_cards_serially: revealing my card ' + my_idx + ', idx = ' + card_idx + ', str = ' + my_card_strs[card_idx]);
	play_page.deal(play_page.who.ME, card_idx, my_card_strs[card_idx], function() {
	    reveal_cards_serially(my_cards_to_process, my_idx + 1, my_card_strs, opponent_cards_to_process, opponent_idx, opponent_card_strs, callback);
	});
	return;
    }
    if (opponent_idx >= opponent_cards_to_process.length && my_idx >= my_cards_to_process.length)
	callback();
}


function on_discard(msg) {
    bg.bglog('play.on_discard: ' + msg);
    last_msg_was_payment = false;
    //note: we don't clear the "waiting for opponent to discard" message until he is completely done with all discards
    //at that time we get a do: true message, (if we are challengee, who discards second), or we will clean up the status
    //messages when we get a deal meaasge to replace the discarded cards (if we are challenger).
    var do_action = bg.extract_json_field(msg, 'do');
    var opponent_card = bg.extract_json_field(msg, 'card');
    var timeout = bg.extract_json_field(msg, 'timeout');
    var opponent_card_idx = -1;
    var timeout_sec = 0;
    if (opponent_card != 'none')
        opponent_card_idx = parseInt(opponent_card, 10);
    if (!!timeout)
        timeout_sec = parseInt(timeout, 10);
    bg.bglog('play.on_discard: do = ' + do_action + ', opponent_card_idx = ' + opponent_card_idx + ', timeout_sec = ' + timeout_sec);
    if (opponent_card_idx >= 0) {
        opponent_strings_idx |= (1 << opponent_card_idx);
	bg.bglog('play.on_discard: opponent_strings_idx = ' + opponent_strings_idx.toString(16));
        play_page.discard_opponent_card(opponent_card_idx, function() {
	});
    }
    if (do_action == 'true') {
	msg_cookie = play_page.show_status_msg(strings.Select_Cards_To_Replace_Str, 60);
	countdown_cookie = play_page.show_countdown(play_page.who.ME, timeout_sec);
        play_page.get_discards_or_fold(function(card_idx, done, fold) {
	    did_discard = true;
	    if (done || fold)
		clear_messages();
	    if (fold) {
		game_over = true;
		sock = bg.close_socket();
		show_forfeit_msg(true, false, null, function() {
		    play_page.hide();
		    top_level_callback(null);
		});
	    } else {
		if (card_idx >= 0) {
		    my_strings_idx |= (1 << card_idx);
		    bg.bglog('play.on_discard: my_strings_idx = ' + my_strings_idx.toString(16));
		}
		var done_spec = (done) ? 'true' : 'false';
		var card_spec = (card_idx >= 0) ? card_idx.toString() : 'none';
		var discard_msg = 'opponent-id: ' + play_parms.opponent_id + ' card: ' + card_spec + ' done: ' + done_spec;
		bg.bglog('play.on_discard: sending did-discard; msg = ' + discard_msg);
		sock.emit('did-discard', discard_msg);
	    }
	});
    }
}


function on_result(msg) {
    bg.bglog('play.on_result: ' + msg);
    clear_messages();
    last_msg_was_payment = false;
    var my_hand = bg.extract_json_field(msg, 'my-hand');
    var opponent_hand = bg.extract_json_field(msg, 'opponent-hand');
    var winner = bg.extract_json_field(msg, 'winner');
    var my_beg_cards_key = bg.extract_json_field(msg, 'my-beg-cards');
    var my_end_cards_key = bg.extract_json_field(msg, 'my-end-cards');
    var opponent_end_cards_key = bg.extract_json_field(msg, 'opponent-end-cards');
    //
    //here is where we verify that the game was kosher.
    //first verify opponent's end cards
    //
    var opponent_end_str_verify = "";
    for (var i = 0; i < opponent_card_strs.length; ++i) {
        if (i > 0)
            opponent_end_str_verify += "-";
        opponent_end_str_verify += opponent_card_strs[i];
    }
    var opponent_card_strings = (opponent_cut_select == 'a') ? card_strings_a : (opponent_cut_select == 'b') ? card_strings_b : card_strings_c;
    var encrypted_card_string = opponent_card_strings[opponent_strings_idx];
    var iv = bg.extract_json_field(encrypted_card_string, 'iv');
    var card_string_msg = bg.extract_json_field(encrypted_card_string, 'msg');
    var decrypted_card_string = util.aes_decrypt(card_string_msg, iv, opponent_end_cards_key);
    if (opponent_end_str_verify === decrypted_card_string.trim()) {
        bg.bglog('play.on_result: opponent end cards verified fair');
    } else {
        var fairness_title = strings.Fairness_Bug_Title_Str;
        var fairness_str = strings.Fairness_Bug_Message_Str;
	fairness_str += '\ndebug messages:' +
	    '\nopponent_strings_idx = ' + opponent_strings_idx.toString(16) +
	    '\nencrypted_card_string = ' + encrypted_card_string +
	    '\nopponent_end_str_verify = ' + opponent_end_str_verify +
	    '\nopponent_end_cards_key = ' + opponent_end_cards_key +
	    '\ndecrypted_card_string = ' + decrypted_card_string;
	play_page.show_err(fairness_title, fairness_str, function() {
	    play_page.hide();
	    top_level_callback(null);
	});
        bg.bglog('play.on_result: fairness bug: key = ' + bg.get_aes_key());
        bg.bglog('play.on_result: opponent_strings_idx = ' + opponent_strings_idx.toString(16));
        bg.bglog('play.on_result: encrypted_card_string = ' + encrypted_card_string);
        bg.bglog('play.on_result: opponent_end_str_verify = ' + opponent_end_str_verify);
        bg.bglog('play.on_result: opponent_end_cards_key = ' + opponent_end_cards_key);
        bg.bglog('play.on_result: decrypted_card_string = ' + decrypted_card_string);
	return;
    }
    //
    //now verify my beginning cards
    //
    var my_card_strings = (my_cut_select == 'a') ? card_strings_a : (my_cut_select == 'b') ? card_strings_b : card_strings_c;
    encrypted_card_string = my_card_strings[0];
    iv = bg.extract_json_field(encrypted_card_string, "iv");
    card_string_msg = bg.extract_json_field(encrypted_card_string, "msg");
    decrypted_card_string = util.aes_decrypt(card_string_msg, iv, my_beg_cards_key);
    if (my_beg_string_verify === decrypted_card_string.trim()) {
        bg.bglog('play.on_result: my beg cards verified fair');
    } else {
        var fairness_title = strings.Fairness_Bug_Title_Str;
        var fairness_str = strings.Fairness_Bug_Message_Str;
	fairness_str += '\ndebug messages:' +
	    '\nmy_beg_str_verify = ' + my_beg_string_verify +
	    '\nmy_beg_cards_key = ' + my_beg_cards_key +
	    '\ndecrypted_card_string = ' + decrypted_card_string;
	play_page.show_err(fairness_title, fairness_str, function() {
	    play_page.hide();
	    top_level_callback(null);
	});
        bg.bglog('play.on_result: fairness bug: key = ' + bg.get_aes_key());
        bg.bglog('play.on_result: my_beg_str_verify = ' + my_beg_string_verify);
        bg.bglog('play.on_result: my_beg_cards_key = ' + my_beg_cards_key);
        bg.bglog('play.on_result: decrypted_card_string = ' + decrypted_card_string);
	return;
    }
    //
    //now verify my end cards
    //
    encrypted_card_string = my_card_strings[my_strings_idx];
    iv = bg.extract_json_field(encrypted_card_string, 'iv');
    card_string_msg = bg.extract_json_field(encrypted_card_string, 'msg');
    decrypted_card_string = util.aes_decrypt(card_string_msg, iv, my_end_cards_key);
    if (my_end_string_verify === decrypted_card_string.trim()) {
        bg.bglog('play.on_result: my end cards verified fair');
    } else {
        var fairness_title = strings.Fairness_Bug_Title_Str;
        var fairness_str = strings.Fairness_Bug_Message_Str;
	fairness_str += '\ndebug messages:' +
	    '\nmy_strings_idx = ' + my_strings_idx.toString(16) +
	    '\nencrypted_card_string = ' + encrypted_card_string +
	    '\nmy_end_str_verify = ' + my_end_string_verify +
	    '\nmy_end_cards_key = ' + my_end_cards_key +
	    '\ndecrypted_card_string = ' + decrypted_card_string;
	play_page.show_err(fairness_title, fairness_str, function() {
	    play_page.hide();
	    top_level_callback(null);
	});
        bg.bglog('play.on_result: fairness bug: key = ' + bg.get_aes_key());
        bg.bglog('play.on_result: my_strings_idx = ' + my_strings_idx.toString(16));
        bg.bglog('play.on_result: encrypted_card_string = ' + encrypted_card_string);
        bg.bglog('play.on_result: my_end_str_verify = ' + my_end_string_verify);
        bg.bglog('play.on_result: my_end_cards_key = ' + my_end_cards_key);
        bg.bglog('play.on_result: decrypted_card_string = ' + decrypted_card_string);
	return;
    }
    //
    //show game results to the user
    //
    var title = strings.You_Win_Title_Str;
    if (winner !== 'me') {
        title = strings.Opponent_Wins_Title_Msg
        title = title.replace("OPPONENT", play_parms.opponent_username);
    }
    var opponent_has = strings.Opponent_Has_Msg;
    opponent_has = opponent_has.replace("OPPONENT", play_parms.opponent_username);
    var msg = strings.You_Have_Str + ": " + my_hand + "\n" +
    	"(" + my_card_strs[0] +       ", " + my_card_strs[1]       + ", " + my_card_strs[2] + ", "
            + my_card_strs[3] +       ", " + my_card_strs[4] + ")\n\n"
	    + opponent_has +          ": " + opponent_hand + "\n" +
        "(" + opponent_card_strs[0] + ", " + opponent_card_strs[1] + ", " + opponent_card_strs[2] + ", "
            + opponent_card_strs[3] + ", " + opponent_card_strs[4] + ")\n\n";
    if (winner == 'me') {
        var credited_msg = strings.Funds_Will_Be_Credited_Str;
        msg += strings.You_Win_The_Pot_Str + "\n\n" + credited_msg;
	play_page.show_err(title, msg, function() {
	    play_page.hide();
	    make_advistory_msg(my_tx_cnt, my_tx_total, start_balance, pot_balance, top_level_callback);
	});
    } else {
        msg += strings.Opponent_Wins_The_Pot_Msg;
        msg = msg.replace("OPPONENT", play_parms.opponent_username);
	play_page.show_err(title, msg, function() {
	    play_page.hide();
	    top_level_callback(null);
	});
    }
    game_over = true;
}


function on_forfeit(msg) {
    bg.bglog('play.on_forfeit: ' + msg);
    clear_messages();
    last_msg_was_payment = false;
    var do_forfeit = bg.extract_json_field(msg, 'do');
    var opponent_forfeit = bg.extract_json_field(msg, 'opponent-forfeit');
    var txerr = bg.extract_json_field(msg, 'txerr');
    var why = bg.extract_json_field(msg, 'why');
    if (txerr == 'true') {
	bg.bglog('play.on_forfeit: got txerr. last_msg_was_payment = ' + last_msg_was_payment);
	//THIS NEEDS TO BE RECONSIDERED...
	/*
        preferences_editor.putBoolean("tx_err_occurred", true);
        preferences_editor.apply();
        //in case of a forfeit with a tx err, and the last msg we sent was a tx, then we can walk back the nonce.
        //that's cuz in that case we're pretty much assured that the tx was never broadcast at all. if there have been
        //any intervening messages, then the tx might have been broadcast; that is, the err might have been not enough gas,
        //or nonce too low... either way we would not want to walk back the nonce.
        if (last_msg_was_payment) {
            bg.bglog("last msg was payment... can we walk back the nonce?");
            long last_tx_nonce = preferences.getLong("last_tx_nonce", 0);
            long verified_nonce = preferences.getLong("verified_nonce", 0);
            if (last_tx_nonce > verified_nonce) {
                --last_tx_nonce;
                preferences_editor.putLong("last_tx_nonce", last_tx_nonce);
                bg.bglog("yup! nonce is now " + last_tx_nonce);
            }
            //just once
            last_msg_was_payment = false;
        }
	*/
    }
    if (!game_over) {
        //only show message if we haven't already processed a forfiet
        game_over = true;
        sock = bg.close_socket();
        show_forfeit_msg(do_forfeit == 'true', opponent_forfeit == 'true', why, function() {
	    play_page.hide();
	    if (opponent_forfeit == 'true')
		make_advistory_msg(my_tx_cnt, my_tx_total, start_balance, pot_balance, top_level_callback);
	    else
		top_level_callback(null);
	});
    }
}

//
// craft a payout advisory message
//
function make_advistory_msg(tx_cnt, tx_total, start_balance, pot_balance, callback) {
    ether.get_gas_price(function(gas_price) {
	var tx_fees = util.at_most_x_decimals((POKER_BET_NOMINAL_GAS * gas_price * tx_cnt) / WEI_PER_FINNEY);
	var deducted_bal = util.at_most_x_decimals(start_balance - tx_total - tx_fees);
	var escrow_fee = util.at_most_x_decimals(pot_balance * 0.02 + 0.5, 2);
	bg.bglog('play.make_advistory_msg: deducted_bal = ' + deducted_bal + ', pot_balance = ' + pot_balance + ', escrow_fee = ' + escrow_fee);
	var final_bal = Math.round(deducted_bal + pot_balance - escrow_fee);
	bg.bglog('play.make_advistory_msg: final_bal = ' + final_bal);
	var msg0 = strings.payout_advisory_msg0;
	msg0 = msg0.replace("START_BAL",    util.left_pad(start_balance, 6));
	msg0 = msg0.replace("TX_TOTAL",     util.left_pad(tx_total, 6));
	msg0 = msg0.replace("TX_CNT",       tx_cnt.toString());
	msg0 = msg0.replace("TX_FEES",      util.left_pad(tx_fees, 6));
	msg0 = msg0.replace("DEDUCTED_BAL", util.left_pad(deducted_bal, 6));
	msg0 = msg0.replace("POT",          util.left_pad(pot_balance, 6));
	msg0 = msg0.replace("ESCROW_FEE",   util.left_pad(escrow_fee, 6));
	msg0 = msg0.replace("FINAL_BAL",    util.left_pad(final_bal, 6));
	var msg1 = strings.payout_advisory_msg1;
	msg1 = msg1.replace("DEDUCTED_BAL", Math.round(deducted_bal).toString());
	msg1 = msg1.replace("POT",          pot_balance);
	msg1 = msg1.replace("FINAL_BAL",    Math.round(final_bal).toString());
	callback(msg0 + msg1);
    });
}


function show_forfeit_msg(i_forfeit, opponent_forfeits, extra_msg, callback) {
    bg.bglog('play.show_forfeit_msg');
    var title = strings.Abandoned_Game_Title_Str;
    var msg = strings.Abandoned_Game_Msg;
    if (opponent_forfeits) {
	title = strings.You_Win_Title_Str;
	msg = strings.Opponent_Forfeited_Msg;
    } else if (i_forfeit) {
        title = strings.Opponent_Wins_Title_Msg;
        msg = strings.You_Forfeited_Str;
    }
    title = title.replace("OPPONENT", play_parms.opponent_username);
    msg = msg.replace("OPPONENT", play_parms.opponent_username);
    if (!!extra_msg)
        msg += "\n\n\n" + strings.Forfeited_Msg_Prompt_Str + "\n" + extra_msg;
    if (opponent_forfeits)
        msg += "\n\n" + strings.Funds_Will_Be_Credited_Str;
    play_page.show_err(title, msg, function() {
	callback();
    });
}


function send_funds(sock_event, size) {
    bg.bglog('play.send_funds');
    if (size == 0) {
	bg.bglog('play.send_funds: sending event = ' + sock_event + ' msg = ' + 'size: 0');
        sock.emit(sock_event, 'size: 0');
    } else {
        ++my_tx_cnt;
        my_tx_total += size;
	var SIZE_IS_FINNEY = true;
	bg.bglog('play.send_funds: about to call ether.raw_send...');
	ether.raw_send(play_parms.acct_addr, play_parms.private_key, strings.lava_contract_addr, size, SIZE_IS_FINNEY, POKER_BET_GAS_LIMIT, game_id,
		       function(tx, callback) {
			   if (sock == null || game_over) {
			       bg.bglog('play.send_funds: game is aborted');
			       callback('Game is aborted', '');
			   } else {
			       last_msg_was_payment = true;
			       var payment_msg = "tx: " + tx + " size: " + size;
			       bg.bglog('play.send_funds: sending event = ' + sock_event + ' msg = ' + payment_msg);
			       sock.emit(sock_event, payment_msg);
			       //could we wait for a confirmation msg from backend? then return the real txid?
			       callback(null, 'fake txid');
			   }
		       },
		       function(err, txid) {
			   bg.bglog('play.send_funds: send is complete, status = ' + ((!!err) ? 'ok' : err));
		       });
    }
}



function save_card_strings(label, str) {
    bg.bglog('in play.save_card_strings');
    var NO_CARD_STRINGS = 29;
    var card_strings = [];
    var idx = str.indexOf(label) + label.length;
    idx = str.indexOf('[', idx) + 1;
    str = str.substring(idx);
    idx = 0;
    for (var i = 0; i < NO_CARD_STRINGS; ++i) {
        if (str.indexOf('"') !== -1) {
            var beg_idx = str.indexOf('"', idx) + 1;
            var end_idx = str.indexOf('"', beg_idx);
            var hash_string = str.substring(beg_idx, end_idx);
            card_strings.push(hash_string);
            idx = str.indexOf(',', end_idx) + 1;
            str = str.substring(idx);
            idx = 0;
        } else {
	    bg.bglog('play.save_card_strings(' + label + '): error searching for string ' + i);
            //Util.show_err(getBaseContext(), getResources().getString(R.string.card_strings_err), 3);
            return;
        }
    }
    //for (var i = 0; i < NO_CARD_STRINGS; ++i)
    //	bg.bglog('play.save_card_strings(' + label + '): cards[' + i + '] = ' + card_strings[i]);
    return card_strings;
}

function clear_messages() {
    if (!!msg_cookie) {
        play_page.clear_status_msg(msg_cookie);
	msg_cookie = null;
    }
    if (!!countdown_cookie) {
        play_page.clear_countdown(countdown_cookie);
	countdown_cookie = null;
    }
}
