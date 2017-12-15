

var bg = require('./background');
var util = require('./util');
var strings = require('./strings');
var ether = require('./ether');
var jQuery = global.$ = window.jQuery = require('jquery');
//var popper = window.Popper = require('../bootstrap/js/popper.min');
//window.Popper = require('popper.js').default;
var popper = window.Popper = require('../bootstrap/js/popper.min');
var bootstrap = require('../bootstrap/js/bootstrap')


var nameInput        = null;
var acctInput        = null;
var balanceInput     = null;
//why does this need to be global? if it is not then the value of the pointer disappears outside of the context of
//show_complete. why is that?
welcome_select_table_fcn              = null;
welcome_new_username_fcn              = null;
welcome_new_acct_fcn                  = null;
welcome_refresh_balance_fcn           = null;
welcome_ok_continue_callback_fcn      = null;
welcome_cancel_continue_callback_fcn  = null;
welcome_accept_continue_callback_fcn  = null;
welcome_decline_continue_callback_fcn = null;

var welcome_page = module.exports = {

    //modal endpoints
    ok_continue: function() {
	bg.bglog('welcome_page.ok_continue');
	if (!!welcome_ok_continue_callback_fcn)
	    welcome_ok_continue_callback_fcn();
    },
    accept_continue: function() {
	bg.bglog('welcome_page.accept_continue');
	if (!!welcome_accept_continue_callback_fcn)
	    welcome_accept_continue_callback_fcn();
    },
    decline_continue: function() {
	bg.bglog('welcome_page.decline_continue');
	if (!!welcome_decline_continue_callback_fcn)
	    welcome_decline_continue_callback_fcn();
    },
    cancel_continue: function() {
	bg.bglog('welcome_page.cancel_continue');
	if (!!welcome_cancel_continue_callback_fcn)
	    welcome_cancel_continue_callback_fcn();
    },

    tableClicked: function(qtype) {
	bg.bglog('in welcome_page.tableClicked: select_table_fcn = ' + welcome_select_table_fcn);
	if (!!welcome_select_table_fcn)
	    welcome_select_table_fcn(qtype);

    },

    changeUserName: function(qbutt, editField) {
	$("#btUNSave").click(function() {
	    var ntext  = $("#edDlgUserName").val();
	    ntext = ntext.trim();
	    //$("#edUserName").val(ntext);
	    welcome_new_username_fcn(ntext);
	});
	$("#getNameText").html(strings.Enter_Username_Str);
	$("#dlgGetUserName").modal();
    },

    changeAccount: function(qbutt, editField) {
	$("#btPKSave").click(function() {
	    var ntext  = $("#edDlgPrivateKey").val();
	    //just to be sure...
	    $("#dlgGetAccount").modal('hide');
	    welcome_new_acct_fcn(ntext);
	});
	$("#getAcctText").html(strings.Enter_Private_Key_Str);
	$("#dlgGetAccount").modal();
    },

    refreshBalance: function() {
	if (!!welcome_refresh_balance_fcn)
	    welcome_refresh_balance_fcn();
    },

    show_big_dialog: function(qtitle, qcontent, callback) {
	bg.bglog('welcome_page.show_big_dialog');
	$("#bigDlgText").html(qcontent);
	$("#bigDlgTitle").html(qtitle);
	$("#dbmOk").on("click", function() {
	    $("#dlgBigMessage").modal().hide();
	    //$('#dlgBigMessage').on('hidden.bs.modal', function () {
	    //    _this.render();
	    //})
	    if (!!callback)
		callback();
	});
	$("#dlgBigMessage").modal();
    },


    hide: function(callback) {
	bg.bglog('welcome_page.hide');
        $('#welcomeStatusTyper').typer('welcome', '');
	var pageDiv = document.getElementById('welcome_page');
	if (pageDiv.className.indexOf('ufpvisible') >= 0)
	    pageDiv.className = (pageDiv.className).replace('ufpvisible', 'ufphidden');
	else if (pageDiv.className.indexOf('ufphidden') < 0)
	    pageDiv.className += 'ufphidden';
	$("#welcomeShowMessage").modal('hide');
	bg.bglog('welcome_page.hide: pageDiv.className = ' + pageDiv.className);
	//$(".welcome_page").hide();
    },


    //
    // show_preliminary just shows the username and account addr (if they are not blank)
    // it can show other items on the page.... but nothing is active.
    //
    show_preliminary: function(username, acct) {
	var screenHeight = window.innerHeight;
	bg.bglog('in welcome_page.show: screenHeight = ' + screenHeight);
	bg.bglog('in welcome_page.show_preliminary');
	var pageDiv = document.getElementById('welcome_page');
	if (pageDiv.className.indexOf('ufphidden') >= 0)
	    pageDiv.className = (pageDiv.className).replace('ufphidden', 'ufpvisible');
	else if (pageDiv.className.indexOf('ufpvisible') < 0)
	    pageDiv.className += 'ufpvisible';

	//
	nameInput = document.getElementById('edUserName');
	nameInput.value = username;
	acctInput = document.getElementById('edAccount');
	acctInput.value = acct;
	balanceInput = document.getElementById('nedBalance');
	balanceInput.value = "Balance: not-synced";
	var refreshBalanceButton = document.getElementById('btRefreshBalance');
	refreshBalanceButton.disabled = true;
	//in case the dialog is shown
	if (!!username)
	    $("#edDlgUserName").html(username);
	$("#selectTableDiv").hide();
    },


    //
    // show_complete shows all the page items. but if a callback function is null, then the corresponding
    // page item either should not be displayed, or should be disabled.
    //
    show_complete: function(username, acct, balance,
			    _new_username_fcn, _new_acct_fcn, _refresh_balance_fcn,
			    _select_table_fcn, max_raises) {
	bg.bglog('in welcome_page.show_complete: acct = ' + acct + ", name = " + username);
	nameInput.value = username;
	acctInput.value = acct;
	balanceInput.value = balance.toString(10) + ' F';
	welcome_new_username_fcn = _new_username_fcn;
	welcome_new_acct_fcn = _new_acct_fcn;
	welcome_refresh_balance_fcn = _refresh_balance_fcn;
	welcome_select_table_fcn = _select_table_fcn;
	welcome_new_username_fcn = _new_username_fcn;
	welcome_new_acct_fcn = _new_acct_fcn;
	//
	var refreshBalanceButton = document.getElementById('btRefreshBalance');
	if (!!welcome_refresh_balance_fcn)
	    refreshBalanceButton.disabled = false;
	else
	    refreshBalanceButton.disabled = true;
	//
	$("#edDlgUserName").html(username);
	if (!!_select_table_fcn && !!max_raises) {
	    var label0 = strings.Play_Level_Label_Msgs[0].replace("WAGER", max_raises[0] + " Finney");
	    var label1 = strings.Play_Level_Label_Msgs[1].replace("WAGER", max_raises[1] + " Finney");
	    var label2 = strings.Play_Level_Label_Msgs[2].replace("WAGER", max_raises[2] + " Finney");
	    $("#startTableButton").html(label0);
	    $("#intermediateTableButton").html(label1);
	    $("#highRollerTableButton").html(label2);
	    $("#selectTableDiv").show();
	} else {
	    $("#selectTableDiv").hide();
	}
	bg.bglog('welcome_page.show_complete: exit');
    },


    show_balance: function(msg, expectedBalance, verifiedBalance) {
	bg.bglog('welcome_page.show_balance: msg = ' + msg + ', expected = ' + expectedBalance + ', verified = ' + verifiedBalance);
	if (!!msg)
	    balanceInput.value = msg;
	else if (expectedBalance == verifiedBalance)
	    balanceInput.value = expectedBalance.toString(10) + ' F';
	else
	    balanceInput.value = expectedBalance.toString(10) + ' F ... (not verified on blockchain)';
    },


    //
    //display a message for a few moments
    //
    show_msg: function(msg) {
	bg.bglog('welcome_page.show_msg: ' + msg);
	welcome_ok_continue_callback_fcn = null;
	$("#wsmsgTitle").html("");
	$("#wsmsgMessage").html(msg);
	$("#welcomeShowMessage").modal();
    },


    //
    //clear a message before it would otherwise disappear
    //
    clear_msg: function() {
	bg.bglog('welcome_page.clear_msg');
	$("#welcomeShowMessage").modal('hide');
    },


    //
    //display a status message (eg. at the bottom of the screen...)
    //
    show_status_msg: function(msg, seconds = 0) {
	bg.bglog('welcome_page.show_status_msg: ' + msg);
        $('#welcomeStatusTyper').typer('welcome', msg);
	var msg_cookie = { timer: null, msg: msg };
	if (seconds != 0) {
	    var msg_timer = setTimeout(function() {
		$('#welcomeStatusTyper').typer('welcome', '');
	    }, seconds * 1000);
	    msg_cookie.timer = msg_timer;
	}
	return(msg_cookie);
    },


    //
    //clear a message before it would otherwise disappear
    //
    clear_status_msg: function(msg_cookie) {
	bg.bglog('welcome_page.clear_status_msg');
	if (!!msg_cookie.timer)
	    clearTimeout(msg_cookie.timer);
        $('#welcomeStatusTyper').typer('welcome', '');
    },


// 
// use the qrcode plugin to show a qrcode on a canvas.
// since this creates new ones, first empty all child elements
// of the parent which happens to be "qrcodeCanvas". 
//
	show_qr: function()
	{
		var addr = $('#edAccount').val();
		$('#dsqrAddr').text(addr);

		$('#qrcodeCanvas').empty();

		$('#qrcodeCanvas').qrcode({
			text	: "" + addr + ""
		});	
		$("#dlgShowQR").modal();

	}, 
    //
    //just like show_msg.... but require users to ackknowledge
    //
    show_err: function(title, msg, callback) {
	bg.bglog('welcome_page.show_err: ' + msg);
	welcome_ok_continue_callback_fcn = callback;
	$("#wsmsgTitle").html(title);
	$("#wsmsgMessage").html(msg);
	$("#welcomeShowMessage").modal();
    },
};
