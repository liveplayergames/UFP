//
// background contains fcn
// console.log in the background page
// also contains most basic level fcns and definitions; that is fcns that do not require any other packages.
// this module can be required by everyone

var strings = require('./strings');
var socket = require('socket.io-client');


//
// glabal vars
//
is_chrome_extension = false;
//popup_window = null;


//
// these are the possible states of poker play. the current state is available from util.retrieve_poker_state()
//
var Poker_State = {
    STARTING                         : { name: "starting"                         },
    WAIT_FOR_READY                   : { name: "wait_for_ready"                   },
    WAIT_FOR_DO_CUT                  : { name: "wait_challenger_ante"             },
    WAIT_FOR_DO_ANTE                 : { name: "wait_challenger_ante"             },
    WAIT_FOR_OPPONENT_ANTE           : { name: "wait_challenger_ante"             },
    WAIT_FOR_DEAL                    : { name: "wait_for_deal"                    },
    WAIT_OPPONENT_PRE_DISCARD_BET    : { name: "wait_challenger_pre_discard_bet"  },
    WAIT_FOR_DO_PRE_DISCARD_BET      : { name: "wait_challenger_ante"             },
    WAIT_FOR_OPPONENT_DISCARDS       : { name: "wait_challenger_discards"         },
    WAIT_FOR_DO_DISCARDS             : { name: "wait_challenger_discards"         },
    WAIT_OPPONENT_POST_DISCARD_BET   : { name: "wait_challenger_pre_discard_bet"  },
    WAIT_FOR_DO_POST_DISCARD_BET     : { name: "wait_challenger_ante"             },
    WAIT_FOR_REVEAL                  : { name: "wait_for_reveal"                  },
    OVER                             : { name: "over"                             }
};


//
// these are all the recorded events in a game. events are cumulative; that is, no bits are ever cleared. the current
// event set is available from util.retrieve_poker_events()
//
var Event = {
    CHALLENGER_EVENT_SHIFT           : 0,
    CHALLENGEE_EVENT_SHIFT           : 8,
    NOTHING                          : 0x0000,
    READY                            : 0x0001,
    DID_CUT                          : 0x0002,
    DID_ANTE                         : 0x0004,
    DID_PRE_DISCARD_DEAL             : 0x0008,
    DID_DISCARDS                     : 0x0010,
    DID_POST_DISCARD_DEAL            : 0x0020,
    DID_REVEAL                       : 0x0040
};
Event.CHALLENGER_READY                 = (Event.READY                            << Event.CHALLENGER_EVENT_SHIFT);
Event.CHALLENGEE_READY                 = (Event.READY                            << Event.CHALLENGEE_EVENT_SHIFT);
Event.BOTH_READY                       = (Event.CHALLENGER_READY                  | Event.CHALLENGEE_READY);
Event.CHALLENGER_DID_CUT               = (Event.DID_CUT                          << Event.CHALLENGER_EVENT_SHIFT);
Event.CHALLENGEE_DID_CUT               = (Event.DID_CUT                          << Event.CHALLENGEE_EVENT_SHIFT);
Event.BOTH_DID_CUT                     = (Event.CHALLENGER_DID_CUT                | Event.CHALLENGEE_DID_CUT);
Event.CHALLENGER_DID_ANTE              = (Event.DID_ANTE                         << Event.CHALLENGER_EVENT_SHIFT);
Event.CHALLENGEE_DID_ANTE              = (Event.DID_ANTE                         << Event.CHALLENGEE_EVENT_SHIFT);
Event.BOTH_DID_ANTE                    = (Event.CHALLENGER_DID_ANTE               | Event.CHALLENGEE_DID_ANTE);
Event.CHALLENGER_DID_PRE_DISCARD_DEAL  = (Event.DID_PRE_DISCARD_DEAL             << Event.CHALLENGER_EVENT_SHIFT);
Event.CHALLENGEE_DID_PRE_DISCARD_DEAL  = (Event.DID_PRE_DISCARD_DEAL             << Event.CHALLENGEE_EVENT_SHIFT);
Event.BOTH_DID_PRE_DISCARD_DEAL        = (Event.CHALLENGER_DID_PRE_DISCARD_DEAL   | Event.CHALLENGEE_DID_PRE_DISCARD_DEAL);
Event.CHALLENGER_DID_DISCARDS          = (Event.DID_DISCARDS                     << Event.CHALLENGER_EVENT_SHIFT);
Event.CHALLENGEE_DID_DISCARDS          = (Event.DID_DISCARDS                     << Event.CHALLENGEE_EVENT_SHIFT);
Event.BOTH_DID_DISCARDS                = (Event.CHALLENGER_DID_DISCARDS           | Event.CHALLENGEE_DID_DISCARDS);
Event.CHALLENGER_DID_POST_DISCARD_DEAL = (Event.DID_POST_DISCARD_DEAL            << Event.CHALLENGER_EVENT_SHIFT);
Event.CHALLENGEE_DID_POST_DISCARD_DEAL = (Event.DID_POST_DISCARD_DEAL            << Event.CHALLENGEE_EVENT_SHIFT);
Event.BOTH_DID_POST_DISCARD_DEAL       = (Event.CHALLENGER_DID_POST_DISCARD_DEAL  | Event.CHALLENGEE_DID_POST_DISCARD_DEAL);
Event.CHALLENGER_DID_REVEAL            = (Event.DID_REVEAL                       << Event.CHALLENGER_EVENT_SHIFT);
Event.CHALLENGEE_DID_REVEAL            = (Event.DID_REVEAL                       << Event.CHALLENGEE_EVENT_SHIFT);
Event.BOTH_DID_REVEAL                  = (Event.CHALLENGER_DID_REVEAL             | Event.CHALLENGEE_DID_REVEAL);



//
// start of background module
// ==========================
//

var bg = module.exports = {

    bglog: function(obj) {
	if (is_chrome_extension && chrome && chrome.runtime) {
	    chrome.runtime.sendMessage({type: "bglog", obj: obj});
	} else {
	    console.log(obj);
	}
    },

    master_iv_from_id: function(id) {
	return(id.substring(2, 34));
    },


    //
    // simple local storage access
    //
    get_aes_key: function() {
	var aesKey = localStorage["aesKey"];
	return(aesKey);
    },

    save_aes_key: function(aesKey) {
	localStorage["aesKey"] = aesKey;
    },

    retrieve_username: function(callback) {
	bg.bglog('in retrieve_username... localStorage["username"] = ' + localStorage["username"]);
	/*
	if (localStorage["username"] == null)
	    bg.bglog('is null');
	else
	    bg.bglog('is not null');
	if (!!localStorage["username"])
	    bg.bglog('!!localStorage["username"] is true');
	else
	    bg.bglog('!!localStorage["username"] is false');
	*/
	callback((!localStorage["username"]) ? "" : localStorage["username"]);
    },

    save_username: function(username) {
	localStorage["username"] = username;
    },

    get_estimated_balance: function(acct) {
	var estimatedBalanceAcct = localStorage["estimatedBalanceAcct"];
	if (acct !== estimatedBalanceAcct) {
	    localStorage["estimatedBalanceAcct"] = acct;
	    localStorage["estimatedBalance"] = -1;
	    localStorage["estimatedBalanceSec"] = 0;
	}
	var estimatedBalance = localStorage["estimatedBalance"];
	if (isNaN(estimatedBalance)) {
	    estimatedBalance = -1;
	    localStorage["estimatedBalanceSec"] = 0;
	}
	bg.bglog('get_estimated_balance: got ' + estimatedBalance);
	return(parseInt(estimatedBalance));
    },

    get_estimated_balance_sec: function(acct) {
	var estimatedBalanceAcct = localStorage["estimatedBalanceAcct"];
	if (acct !== estimatedBalanceAcct) {
	    localStorage["estimatedBalanceAcct"] = acct;
	    localStorage["estimatedBalance"] = -1;
	    localStorage["estimatedBalanceSec"] = 0;
	}
	var estimatedBalanceSec = localStorage["estimatedBalanceSec"];
	if (isNaN(estimatedBalanceSec)) {
	    estimatedBalanceSec = 0;
	    localStorage["estimatedBalanceSec"] = 0;
	}
	bg.bglog('get_estimated_balance_sec: got ' + estimatedBalanceSec);
	return(parseInt(estimatedBalanceSec));
    },

    save_estimated_balance: function(acct, estimatedBalance) {
	var oldValue = localStorage["estimatedBalance"];
	var estimatedBalanceAcct = localStorage["estimatedBalanceAcct"];
	if (acct !== estimatedBalanceAcct) {
	    localStorage["estimatedBalanceAcct"] = acct;
	    oldValue = -1;
	}
	//record timestamp whenever estimated balance changes
	if (estimatedBalance != oldValue) {
	    localStorage["estimatedBalance"] = parseInt(estimatedBalance);
	    var nowSec = Math.floor(Date.now() / 1000);
	    localStorage["estimatedBalanceSec"] = nowSec;
	}
    },

    get_tx_nonce: function(acct) {
	var txNonceAcct = localStorage["txNonceAcct"];
	if (acct !== txNonceAcct) {
	    localStorage["txNonceAcct"] = acct;
	    localStorage["txNonce"] = -1;
	}
	var nonce = localStorage["txNonce"];
	if (isNaN(nonce)) {
	    nonce = -1;
	}
	bg.bglog('background.get_tx_nonce: got ' + nonce);
	return(parseInt(nonce));
    },

    save_tx_nonce: function(acct, nonce) {
	localStorage["txNonceAcct"] = acct;
	var prevTxNonce = localStorage["txNonce"];
	//if recorded nonce is NaN or lte
	if (!(prevTxNonce > nonce)) {
	    //here we play in interesting trick: if the old tx nonce was the same as the validated nonce, then we
	    //reset validateNonceSec. this is cuz the point of validateNonceSec is to know how old (stale) is the
	    //validatedNonce -- and we don't care about any history prior to the time that tx-nonce advanced
	    //beyond the validatedNonce.
	    var validatedNonceAcct = localStorage["validatedNonceAcct"];
	    var validatedNonce = localStorage["validatedNonce"];
	    if (validatedNonceAcct !== acct || isNaN(validatedNonce) || isNaN(prevTxNonce) || validatedNonce == prevTxNonce) {
		var now_sec = Math.floor(Date.now() / 1000);
		localStorage["validatedNonceSec"] = now_sec;
	    }
	    localStorage["txNonce"] = parseInt(nonce);
	}
    },

    get_validated_nonce: function(acct) {
	var validatedNonceAcct = localStorage["validatedNonceAcct"];
	if (acct !== validatedNonceAcct) {
	    var now_sec = Math.floor(Date.now() / 1000);
	    localStorage["validatedNonceSec"] = now_sec;
	    localStorage["validatedNonceAcct"] = acct;
	    localStorage["validatedNonce"] = -1;
	}
	var nonce = localStorage["validatedNonce"];
	if (isNaN(nonce))
	    nonce = -1;
	bg.bglog('background.get_validated_nonce: got ' + nonce);
	return(parseInt(nonce));
    },

    //
    // save the validatedNonce and update validatedNonceSec
    // validatedNonceSec is used to measure how stale the validatedNonce is. that is, if the tx-nonce has been advancing, but
    // validatedNonce has not changed in a long time, then we'll revert back to the validatedNonce. note that if the tx-nonce
    // and validatedNonce are in sync, then validatedNonceSec has no point. we start timing validatedNonceSec from the time
    // that tx-nonce and validatedNonce diverge. you can see this in the save_tx_nonce fcn.
    //
    save_validated_nonce: function(acct, nonce) {
	var now_sec = Math.floor(Date.now() / 1000);
	localStorage["validatedNonceSec"] = now_sec;
	localStorage["validatedNonceAcct"] = acct;
	localStorage["validatedNonce"] = parseInt(nonce);
    },

    get_validated_nonce_sec: function() {
	var sec = localStorage["validatedNonceSec"];
	return(sec);
    },

    //this just gets a chached version of the acct. use util.retrieve_acct to compute
    get_acct: function(callback) {
	var acct = localStorage["acct"];
	return(acct);
    },

    save_acct: function(acct) {
	localStorage["acct"] = acct;
    },

    sock: null,

    get_socket: function(close) {
	bg.bglog('bg.get_socket: close = ' + close + ', socket_url = ' + strings.socket_url);
	if (close == true && bg.sock != null) {
	    bg.sock.close();
	    bg.sock = null;
	}
	if (bg.sock == null)
	    bg.sock = socket.connect(strings.socket_url);
	return(bg.sock);
    },

    close_socket: function() {
	bg.bglog('bg.close_socket');
	if (bg.sock != null) {
	    bg.sock.close();
	    bg.sock = null;
	}
	return(bg.sock);
    },

    better_fetch: function(url, callback) {
        var timeout = false;
	var complete = false;
	var fetch_timer = setTimeout(function() {
	    timeout = true;
	    if (complete == true) {
		return;
	    } else {
		bg.bglog("better_fetch: timeout retrieving " + url);
		callback("", "timeout");
	    }
	}, 10000);
	bg.bglog('better_fetch: fetching ' + url);
	var request = new Request(url);
	fetch(request, { mode: 'cors'} ).then(function(resp) {
	    bg.bglog('better_fetch: got resp = ' + resp + ', status = ' + resp.status + ', (' + resp.statusText + ')');
	    clearTimeout(fetch_timer);
	    complete = true;
	    if (timeout == true) {
		bg.bglog("better_fetch: fetch returned after timeout! url = " + url);
		return;
	    }
	    if (resp.ok) {
		resp.text().then(function(str) {
		    callback(str, "");
		});
	    } else {
		bg.bglog("better_fetch: got err = " + resp.blob());
		callback("", "unknown");
	    }
	}).catch(function(error) {
	    bg.bglog("better_fetch: exeption = " + error);
	    complete = true;
	    callback("", error);
	});
    },


    /* --------------------------------------------------------------------------------------------------------------------------------------------------------------------------
       extract a field from json input. assumes that the field identifier is unique in the passed message.
       -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- */
    //commas not allowed in fields...
    extract_json_field: function (msg, field) {
	var value = "";
	var extra_quote = 0;
	var start_idx = msg.indexOf(field + ":");
	if (start_idx < 0) {
	    var start_idx = msg.indexOf(field + "\":");
	    ++extra_quote;
	}
	if (start_idx >= 0) {
	    start_idx += field.length + extra_quote + 1;
	    value = msg.substring(start_idx).trim();
	    if (value.startsWith('"')) {
		value = value.substring(1);
		var match_quote_idx = value.indexOf('"');
		if (match_quote_idx >= 0)
		    value = value.substring(0, match_quote_idx);
	    } else {
		var re = /[ ,}]/;
		var match = re.exec(value);
		if (match != null)
		    value = value.substring(0, match.index);
		value = value.replace(/"/g, "");
	    }
	    //console.log("field: " + field + ", value: " + value);
	}
	return(value);
    },

}

//---------------------------------------------------------------------------------
//local fcns and vars
//---------------------------------------------------------------------------------

//function to log debug messages to the background page console
var onMessageListener = function(message, sender, sendResponse) {
    if (chrome && chrome.runtime) {
	switch(message.type) {
	case "bglog":
	    console.log(message.obj);
	    break;
	}
    }
    return true;
}
if (is_chrome_extension && chrome && chrome.runtime) {
    var onm = chrome.runtime.onMessage;
    if (onm)
	onm.addListener(onMessageListener);
}
