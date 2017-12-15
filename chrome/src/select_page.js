
var strings = require('./strings');
var bg = require('./background');
var util = require('./util');
var jQuery = global.$ = window.jQuery = require('jquery');
//var bootstrap = require('bootstrap/dist/js/bootstrap');
//var popper = window.Popper = require('../bootstrap/js/popper.min');
//window.Popper = require('popper.js').default;
var popper = window.Popper = require('../bootstrap/js/popper.min');
var bootstrap = require('../bootstrap/js/bootstrap')

//var popper = require('popper.min');
//    <script src="bootstrap/js/popper.min.js"></script>
//    <script src="bootstrap/js/bootstrap.min.js"></script>

var username = "";
var acct_addr = "";
var private_key = "";
//
//why does this need to be global? if it is not then the value of the pointer disappears outside of the context of
//show_opponents. why is that?
select_main_callback = null;
select_pick_opponent_callback_fcn = null;
select_ok_continue_callback_fcn = null;
select_cancel_continue_callback_fcn = null;
select_accept_continue_callback_fcn = null;
select_decline_continue_callback_fcn = null;
select_opponents = [];
select_opponents_cookie = null;
//
var main_div = null;
var opponents_div = null;
var select_page = module.exports = {


    //modal endpoints
    ok_continue: function() {
	bg.bglog('select_page.ok_continue');
	if (!!select_ok_continue_callback_fcn)
	    select_ok_continue_callback_fcn();
    },
    accept_continue: function() {
	bg.bglog('select_page.acceot_continue');
	if (!!select_accept_continue_callback_fcn)
	    select_accept_continue_callback_fcn(select_page.Challenge_Response.ACCEPTED);
    },
    decline_continue: function() {
	bg.bglog('select_page.decline_continue');
	if (!!select_decline_continue_callback_fcn)
	    select_decline_continue_callback_fcn(select_page.Challenge_Response.DECLINED);
    },
    cancel_continue: function() {
	bg.bglog('select_page.cancel_continue');
	if (!!select_cancel_continue_callback_fcn)
	    select_cancel_continue_callback_fcn();
    },

    exit: function() {
	//callback parm could be game results; calling the callback with null indicates that no game was played.
	if (!!select_main_callback)
	    select_main_callback(null);
    },

    hide: function() {
	$("#selectShowMessage").modal('hide');
	$("#waitForAccept").modal('hide');
	$("#showChallenge").modal('hide');
        $('#selectStatusTyper').typer('status', '');
	var pageDiv = document.getElementById('select_page');
	if (pageDiv.className.indexOf('ufpvisible') >= 0)
	    pageDiv.className = (pageDiv.className).replace('ufpvisible', 'ufphidden');
	else if (pageDiv.className.indexOf('ufphidden') < 0)
	    pageDiv.className += 'ufphidden';
	//document.body.removeChild(main_div);
    },

    //the callback is only called when the user wants to exit the select screen (not to play)
    show: function(table_idx, max_raise, callback) {
	bg.bglog('in select_page.show: table idx = ' + table_idx + ', max-raise = ' + max_raise);
	select_main_callback = callback;
	var pageDiv = document.getElementById('select_page');
	if (pageDiv.className.indexOf('ufphidden') >= 0)
	    pageDiv.className = (pageDiv.className).replace('ufphidden', 'ufpvisible');
	else if (pageDiv.className.indexOf('ufpvisible') < 0)
	    pageDiv.className += 'ufpvisible';
	var table_title = strings.Play_Level_Label_Msgs[table_idx];
	table_title = table_title.replace('WAGER', (max_raise.toString() + ' F'));
	$("#selectTitle").html(table_title);
    },


    Opponent: function(id, username, level) {
	this.id = id;
	this.username = username;
	this.level = level;
    },


    select_opponent: function(idx) {
	var opponent = select_opponents[idx];
	bg.bglog('select_page.select_opponent: idx = ' + idx + ', name = ' + opponent.username);
	select_pick_opponent_callback_fcn(opponent);
	$("#ulPlayers").empty();
    },

    show_opponents: function(xopponents, callback) {
	bg.bglog('in select_page.show_opponents. have ' + xopponents.length + ' opponents; select_opponents_cookie = ' + select_opponents_cookie);
	select_pick_opponent_callback_fcn = callback;
	$("#ulPlayers").empty();
	select_opponents = [];
	for (var i = 0; i < xopponents.length; i++) {
	    var opponent = xopponents[i];
	    select_opponents.push(opponent);
	$("#ulPlayers").append('<li class="TwoColLI" onclick="select_page.select_opponent(' + i + ')" name="' + opponent.username + '"  ><img src="images/opponentx.jpg" style="width:60;height:35px;">&nbsp;' + opponent.username + '</li>');


//	    $("#ulPlayers").append('<li class="w3-bar" onclick="select_page.select_opponent(' + i + ')" name="' + opponent.username + '" >' +
//				   '<img src="images/opponentx.jpg" class="w3-bar-item w3-circle w3-hide-small" style="width:85px">' +
//				   '<div class="w3-bar-item"  >' +
//				   '<span class="w3-large">' + opponent.username + '</span><br> </div>  </li>');

	}
	if (xopponents.length == 0)
	    select_opponents_cookie = select_page.show_status_msg(strings.No_Opponents_Str);
	else
	    select_opponents_cookie = select_page.show_status_msg(strings.Select_A_Player_Str);
	//
	/*
	if (!!opponents_cookie) {
	    select_page.clear_msg(opponents_cookie);
	    opponents_cookie = null;
	}
	if (opponents_div != null) {
	    bg.bglog('opponents_div is not null');
	    main_div.removeChild(opponents_div);
	} else {
	    bg.bglog('opponents_div is null');
	}
	opponents_div = document.createElement("div");
	main_div.appendChild(opponents_div);
	for (var i = 0; i < opponents.length; i++) {
	    var para = document.createElement("p");
	    var button = document.createElement('button');
	    var opponent = opponents[i];
	    var button_text = document.createTextNode(opponent.username);
	    button.appendChild(button_text);
	    para.appendChild(button);
	    opponents_div.appendChild(para);
	    button.addEventListener('click', function() {
		bg.bglog('select_page.show_show_opponents: got click... opponent = ' + opponent);
		main_div.removeChild(opponents_div);
		opponents_div = null;
		callback(opponent);
	    });
	}
	if (opponents.length == 0)
	    opponents_cookie = select_page.show_status_msg(strings.No_Opponents_Str);
	*/
    },


    Challenge_Response: {
	ACCEPTED   : { text: "accepted"  },
	DECLINED   : { text: "declined"  },
	COUNTER    : { text: "counter"   }
    },

    show_you_are_challenged: function(challenger_name, wager, callback) {
	bg.bglog('in select_page.show_you_are_challenged. challenger = ' + challenger_name);
	select_accept_continue_callback_fcn = select_decline_continue_callback_fcn = callback;
	$("#selectShowMessage").modal('hide');
	$("#waitForAccept").modal('hide');
	$("#scName").html(challenger_name);
	$("#scWager").html(wager.toString());
	$("#showChallenge").modal();
    },

    show_waiting_for_response: function(opponent_name, seconds, callback) {
	select_cancel_continue_callback_fcn = callback;
	$("#selectShowMessage").modal('hide');
	$("#wfaName").html(opponent_name);
	$("#waitForAccept").modal();
	bg.bglog('select_page.show_waiting_for_response: back from modal...');
    },

    show_opponent_declined_your_challenge: function(opponent_name, callback) {
	select_ok_continue_callback_fcn = callback;
	$("#selectShowMessage").modal('hide');
	$("#waitForAccept").modal('hide');
	var title = opponent_name + " Declined";
    	var msg = opponent_name + " " + strings.Declined_Your_Challenge_Str;
	$("#ssmsgTitle").html(title);
	$("#ssmsgMessage").html(msg);
	$("#selectShowMessage").modal();
	bg.bglog('select_page.show_opponent_declined_your_challenge: back from modal...');
    },

    show_opponent_backed_out: function(opponent_name, callback) {
	$("#selectShowMessage").modal('hide');
	$("#showChallenge").modal('hide');
	select_ok_continue_callback_fcn = callback;
	var title = opponent_name + " " + strings.Chickened_Out_Str;
	var msg = strings.Opponent_Backed_Out_Msg;
	msg.replace("OPPONENT", opponent_name);
	$("#ssmsgTitle").html(title);
	$("#ssmsgMessage").html(msg);
	$("#selectShowMessage").modal();
    },

    //
    //display a message for a few moments
    //
    show_msg: function(msg) {
	bg.bglog('select_page.show_msg: ' + msg);
	select_ok_continue_callback_fcn = null;
	$("#ssmsgTitle").html("");
	$("#ssmsgMessage").html(msg);
	$("#selectShowMessage").modal();
    },


    //
    //clear a message before it would otherwise disappear
    //
    clear_msg: function() {
	bg.bglog('select_page.clear_msg');
	$("#selectShowMessage").modal('hide');
    },


    //
    //display a status message (eg. at the bottom of the screen...)
    //
    show_status_msg: function(msg, seconds = 0) {
	bg.bglog('select_page.show_status_msg: ' + msg);
        $('#selectStatusTyper').typer('status', msg);
	var msg_cookie = { timer: null, msg: msg };
	if (seconds != 0) {
	    var msg_timer = setTimeout(function() {
		$('#selectStatusTyper').typer('status', '');
	    }, seconds * 1000);
	    msg_cookie.timer = msg_timer;
	}
	return(msg_cookie);
    },


    //
    //clear a message before it would otherwise disappear
    //
    clear_status_msg: function(msg_cookie) {
	bg.bglog('select_page.clear_status_msg');
	if (!!msg_cookie.timer)
	    clearTimeout(msg_cookie.timer);
        $('#selectStatusTyper').typer('status', '');
    },

    //
    //just like show_msg.... but require users to ackknowledge
    //
    show_err: function(title, msg, callback) {
	bg.bglog('select_page.show_err: ' + msg);
	select_ok_continue_callback_fcn = callback;
	$("#ssmsgTitle").html(title);
	$("#ssmsgMessage").html(msg);
	$("#selectShowMessage").modal();
    },

    clear_err: function() {
	bg.bglog('select_page.clear_err');
	$("#selectShowMessage").modal('hide');
    },

};
