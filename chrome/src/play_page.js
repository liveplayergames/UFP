
var strings = require('./strings');
var bg = require('./background');
var util = require('./util');
var jQuery = global.$ = window.jQuery = require('jquery');
var popper = window.Popper = require('../bootstrap/js/popper.min');
var bootstrap = require('../bootstrap/js/bootstrap')
var bootstrap = require('../bootstrap/js/bootstrap-slider')

//
var opponent_cards_div = null;
var bet_div = null;
var msg_para = null;
var msg_line = null;

var screenWidth = window.innerWidth;
var screenHeight = window.innerHeight;

var minScreenHeight = 640;
var maxTopScreenHeight = 900;
//these values are for minScreenHeight; they will be adjusted by extra * ((screenHeight - minScreenHeight) / (maxScreenHeight - minScreenHeight))
var localCardsYMin = 214;
var remoteCardsYMin = 60;
var deckCardsYMin = 140;
var betYMin = 344;
var localPouchYMin = 296;
var remotePouchYMin = 0;
var statusMsgYMin = 390;
var cardWidthMin = 45;
var cardHeightMin = 65;

//
var localCardsYExtra = 350 - 224;
var remoteCardsYExtra = 70 - 60;
var deckCardsYExtra = 250 - 145;
var betYExtra = 240;
var localPouchYExtra = 500 - 308;
var remotePouchYExtra = 0;
var statusMsgYExtra = 268;
var cardWidthExtra = 90 - 45;
var cardHeightExtra = 130 - 65;
//
//note: those referenced in discard_card fcn, from html -- must be global
discards = [];
localCardsY = 0;
var remoteCardsY = 0;
deckCardsY = 0;
var betY = 0;
var localPouchY = 0;
var remotePouchY = 0;
var statusMsgY = 0;
cardWidth = 0;
var cardHeight = 0;

//
var orgCardXs = new Array(100,250,400,550,700);
var cardXs = new Array(100,250,400,550,700);


//why does this need to be global? if it is not then the value of the pointer disappears...
play_ok_continue_callback_fcn = null;
play_cancel_continue_callback_fcn = null;
play_bet_callback_fcn = null;
play_discards_callback_fcn = null;

// FOR THE MOVE TIMER
var moveTimerId = 0;
var moveTimerVal = 15;
var moveTimerLocal = true; // local or remote
var radius = 12;
var timerW = 40;
var timerH = 40;
var cardW = 50;



var play_page = module.exports = {

    //modal endpoints
    ok_continue: function() {
	bg.bglog('play_page.ok_continue');
	if (!!play_ok_continue_callback_fcn)
	    play_ok_continue_callback_fcn();
    },

    cancel_continue: function() {
	bg.bglog('play_page.cancel_continue');
	if (!!play_cancel_continue_callback_fcn)
	    play_cancel_continue_callback_fcn();
    },

    who: {
	ME:       "me",
	OPPONENT: "opponent"
    },

    hide: function() {
        $('#playStatusTyper').typer('play', '');
	hide_images();
	var pageDiv = document.getElementById('play_page');
	if (pageDiv.className.indexOf('ufpvisible') >= 0)
	    pageDiv.className = (pageDiv.className).replace('ufpvisible', 'ufphidden');
	else if (pageDiv.className.indexOf('ufphidden') < 0)
	    pageDiv.className += 'ufphidden';
    },

    //the callback is only called when the user wants to exit the select screen (not to play)
    show: function(callback) {
	bg.bglog('in play_page.show: screenHeight = ' + screenHeight);
	//ensure that we start with no image artifacts
	hide_images();
	//we only adjust top screen within these limits
	var topScreenHeight = screenHeight;
	if (topScreenHeight < minScreenHeight)
	    topScreenHeight = minScreenHeight;
	if (topScreenHeight > maxTopScreenHeight)
	    topScreenHeight = maxTopScreenHeight;
	//adjust for screen height
	var topFactor = (topScreenHeight - minScreenHeight) / (maxTopScreenHeight - minScreenHeight);
	bg.bglog('in play_page.show: screenHeight = ' + screenHeight + ', topFactor = ' + topFactor);
	localCardsY = localCardsYMin + localCardsYExtra * topFactor;
	bg.bglog('in play_page.show: localCardsY = ' + localCardsY);
	remoteCardsY = remoteCardsYMin + remoteCardsYExtra * topFactor;
	deckCardsY = deckCardsYMin + deckCardsYExtra * topFactor;
	localPouchY = localPouchYMin + localPouchYExtra * topFactor;
	remotePouchY = remotePouchYMin + remotePouchYExtra * topFactor;
	cardWidth = cardWidthMin + cardWidthExtra * topFactor;
	cardHeight = cardHeightMin + cardHeightExtra * topFactor;
	//these items can move to the bottom part of the screen... that is, factor can be gt 1
	var botFactor = (screenHeight - minScreenHeight) / (maxTopScreenHeight - minScreenHeight);
	betY = betYMin + betYExtra * botFactor;
	statusMsgY = statusMsgYMin + statusMsgYExtra * botFactor;
	//
	//set up card positions based on screen width
	var xoffset = (screenWidth / 2) - 500;
	//also hide any cards from previous play
	for (var i = 0; i < 5; ++i) {
	    cardXs[i] = orgCardXs[i]+xoffset;
	    var locElem = document.getElementById('iL' + i);
	    var remElem = document.getElementById('iR' + i);
	    locElem.src = null;
	    remElem.src = null;
	    locElem.style.left = "-70px";
	    remElem.style.left = "-70px";
	    locElem.style.top = "" + deckCardsY + "px";
	    remElem.style.top = "" + deckCardsY + "px";
	}
	//
	var pageDiv = document.getElementById('play_page');
	if (pageDiv.className.indexOf('ufphidden') >= 0)
	    pageDiv.className = (pageDiv.className).replace('ufphidden', 'ufpvisible');
	else if (pageDiv.className.indexOf('ufpvisible') < 0)
	    pageDiv.className += 'ufpvisible';

	//this was in doc.ready...
	$("#betPanel").hide();
	$("#discardPanel").hide();
	$("#btAccept").on("click", function(){
		console.log("ACCEPT");
	       alert("accept");
	});

	$("#btDecline").click(function(){
		console.log("DECLINE");
	    alert("decline");
	});
	showPouches(callback);
    },


    //
    //get bet from user, or fold.
    //bet must be between min_bet and max_bet
    //callback fcn parms are: bet_size, fold
    //
    get_bet_or_fold: function(min_bet, max_bet, callback) {
	bg.bglog('in play_page.get_bet_or_fold: min = ' + min_bet + ', max = ' + max_bet);
	play_bet_callback_fcn = callback;
	var x = cardXs[1];
	var y = betY;
	$('#betPanel').css({'top' : y + 'px'});
	$('#betPanel').css({'left' : x + 'px'});

	$('#betSlider').slider({
	    tooltip: 'show',
	    tooltip_position:'top',
	    min: min_bet,
	    max: max_bet,
	    value: min_bet,
	    orientation: 'horizontal',
	    formatter: function(value) {
		return value;
	    }
	});
	$("#betSlider").on("change", function(slideEvt) {
	    var value = $("#betSlider").data('slider').getValue();
	    //console.log('change: ' + value);
	    if (value == 0)
		$("#place").text("Check (0 Finney)");
	    else if (value == min_bet)
		$("#place").text("Call (" + value + " Finney)");
	    else
		$("#place").text("Place Bet ("+ value +" Finney)");
	});
	$("#betSlider").slider('setValue', min_bet);
	var msg = (min_bet == 0) ? "Check (no bet)" : "Call (" + min_bet + " Finney)";
	$("#place").text(msg);
	$('#betPanel').show();

    },
    bet_place_bet: function() {
	var bet = $("#betSlider").slider('getValue');
	bg.bglog('play_page.bet_place_bet: bet = ' + bet);
	if (!!play_bet_callback_fcn)
	    play_bet_callback_fcn(bet, false);
	$('#betPanel').hide();
	play_bet_callback_fcn = null;
    },

    bet_fold: function() {
	bg.bglog('play_page.bet_fold');
	if (!!play_bet_callback_fcn)
	    play_bet_callback_fcn(0, true);
	$('#betPanel').hide();
	play_bet_callback_fcn = null;
    },


    //
    //deal a card
    //
    deal: function(who, card_idx, card_str, callback) {
	var elemPrefix = (who == play_page.who.ME) ? "iL" : "iR";
	var imgElem = document.getElementById(elemPrefix + card_idx);
	imgElem.src = card_str_to_image_url(card_str);
	imgElem.style.display = "block";
	imgElem.style.height = cardHeight.toString() + 'px';
	imgElem.style.width = cardWidth.toString() + 'px';
	var startx = -(cardWidth + 10);
	var destX = cardXs[card_idx];
	var destY = (who == play_page.who.ME) ? localCardsY : remoteCardsY;
	var steps = 10 - (4 - card_idx);
	bg.bglog('play_page.deal: animating ' + elemPrefix + card_idx);
	animateToPos(imgElem, startx, deckCardsY, destX, destY, steps, callback);
    },


    //
    //get discards, callback when complete
    //callback parm is index of card (or -1 for no card), bool done, bool fold
    //cards should disappear when discarded
    //
    get_discards_or_fold: function(callback) {
	discards = [];
	bg.bglog('play_page.get_discards_or_fold');
	play_discards_callback_fcn = callback;
	var elem0 = document.getElementById('iL0');
	var elem1 = document.getElementById('iL1');
	var elem2 = document.getElementById('iL2');
	var elem3 = document.getElementById('iL3');
	var elem4 = document.getElementById('iL4');
	elem0.onClick="play_page.discard_card(0)";
	elem1.onClick="play_page.discard_card(1)";
	elem2.onClick="play_page.discard_card(2)";
	elem3.onClick="play_page.discard_card(3)";
	elem4.onClick="play_page.discard_card(4)";
	//for some reason this doesn't work. no problem though -- we just ignore discards
	//when play_discards_callback_fcn is null
	//elem0.disabled = false;
	//elem1.disabled = false;
	//elem2.disabled = false;
	//elem3.disabled = false;
	//elem4.disabled = false;
	var x = cardXs[1];
	var y = betY;
      	$('#discardPanel').css({'top' : y + 'px'});
      	$('#discardPanel').css({'left' : x + 'px'});
	$('#discardPanel').show();
    },


    //
    // this is the function that will be called when the user discards a card by cliking on it
    //
    discard_card: function(card_idx) {
	if (!!play_discards_callback_fcn) {
	    bg.bglog('play_page.discard_card: idx ' + card_idx + ', localCardsY = ' + localCardsY + ', cardWidth = ' + cardWidth +
		    ', deckCardsY = ' + deckCardsY + ', discards.length = ' + discards.length);
	    var elemPrefix = "iL";
	    var imgElem = document.getElementById(elemPrefix + card_idx);
	    var startx = cardXs[card_idx];
	    var destX = -(cardWidth + 10);
	    var steps = 10;
	    animateToPos(imgElem, startx, localCardsY, destX, deckCardsY, steps, null);
	    discards.push(card_idx);
	    return_discard(card_idx, ((discards.length < 3) ? false : true), false);
	}
    },
    discard_done: function() {
	bg.bglog('play_page.discard_done');
	return_discard(-1, true, false);
    },
    discard_fold: function() {
	bg.bglog('play_page.discard_fold');
	return_discard(-1, false, true);
    },

    //
    //make an opponent card disappear
    //
    discard_opponent_card: function(card_idx, callback) {
	var elemPrefix = "iR";
	var imgElem = document.getElementById(elemPrefix + card_idx);
	var startx = cardXs[card_idx];
	var destX = -(cardWidth + 10);
	var steps = 10;
	bg.bglog('play_page.discard_opponent_card: animating ' + elemPrefix + card_idx);
	animateToPos(imgElem, startx, remoteCardsY, destX, deckCardsY, steps, callback);
    },


    //
    //reveal an opponent card
    //
    reveal_opponent_card: function(card_idx, card_str, callback) {
	bg.bglog('play_page.reveal_opponent_card: idx = ' + card_idx + ', str = ' + card_str);
	var elemPrefix = "iR";
	var imgElem = document.getElementById(elemPrefix + card_idx);
	imgElem.src = card_str_to_image_url(card_str);
	imgElem.style.height = cardHeight.toString() + 'px';
	imgElem.style.width = cardWidth.toString() + 'px';
	callback();
    },


    //
    //show_balance
    //
    show_balance: function(balance) {
	var spanAmount;
	bg.bglog("play_page.show_balance: new balance is " + balance);
	spanAmount = document.getElementById("lpouchAmount");
	spanAmount.innerHTML = "" + balance;
    },

    //
    //show_pot
    //
    show_pot: function(balance) {
	var icoin, divCoin;
	bg.bglog("play_page.show_pot");
	divCoin = document.getElementById("coinPile");
	icoin = document.getElementById("iPile");
	icoin.src= "images/coin-pile2.png";
	divCoin.style.left = cardXs[2] + "px";
	divCoin.style.top = deckCardsY + "px";
	divCoin.style.display = "block";
	icoin.style.left = "0px";
	icoin.style.top = "0px";
    },


    //
    //set_pot_balance
    //
    set_pot_balance: function(balance) {
	var spanAmount;
	bg.bglog("play_page.show_pot: new pot balance is " + balance);
	spanAmount = document.getElementById("coinPileAmount");
	spanAmount.innerHTML = "" + balance;
    },


    //
    //move funds
    //note: this fcn only does an funds animation. it does not update any displayed balances.
    //
    move_funds: function(who, size, callback) {
	if (who == play_page.who.ME)
	    bg.bglog("play_page.move_funds: moving my funds; " + size + " finney...");
	else
	    bg.bglog("play_page.move_funds: moving opponent funds; " + size + " finney...");
	var imgElem = document.getElementById((who == play_page.who.ME) ? "ilcoin" : "ircoin");
	imgElem.src= "images/coin-pile2.png";
	imgElem.style.display = "block";
	var startx = cardXs[2];
	var starty = (who == play_page.who.ME) ? localPouchY : remotePouchY;
	var destX = cardXs[2];
	var destY = deckCardsY;
	var steps = 20;
	animateToPos(imgElem, startx, starty, destX, destY, steps, callback);
    },


    //
    //display a message for a few moments
    //
    show_msg: function(msg) {
	bg.bglog('play_page.show_msg: ' + msg);
	play_ok_continue_callback_fcn = null;
	$("#ssmsgTitle").html("");
	$("#ssmsgMessage").html(msg);
	$("#selectShowMessage").modal();
    },

    //
    //clear a message before it would otherwise disappear
    //
    clear_msg: function() {
	bg.bglog('play_page.clear_msg');
	$("#selectShowMessage").modal('hide');
    },

    //
    //display a status message (eg. at the bottom of the screen...)
    //
    show_status_msg: function(msg, seconds = 0) {
	bg.bglog('play_page.show_status_msg: ' + msg);
	$('#playStatusTyper').css({'top' : statusMsgY.toString() + 'px'});
        $('#playStatusTyper').typer('play', msg);
	var msg_cookie = { timer: null, msg: msg };
	if (seconds != 0) {
	    var msg_timer = setTimeout(function() {
		$('#playStatusTyper').typer('play', '');
	    }, seconds * 1000);
	    msg_cookie.timer = msg_timer;
	}
	return(msg_cookie);
    },


    //
    //clear a message before it would otherwise disappear
    //
    clear_status_msg: function(msg_cookie) {
	bg.bglog('play_page.clear_status_msg');
	if (!!msg_cookie.timer) {
	    clearTimeout(msg_cookie.timer);
	}
        $('#playStatusTyper').typer('play', '');
    },

    //
    //just like show_msg.... but require users to ackknowledge
    //
    show_err: function(title, msg, callback) {
	bg.bglog('play_page.show_err: ' + msg);
	play_ok_continue_callback_fcn = callback;
	$("#psmsgTitle").html(title);
	$("#psmsgMessage").html(msg);
	$("#playShowMessage").modal();
    },

    // show the countdown timer
    show_countdown: function(who, seconds) {
	bg.bglog('play_page.show_countdown: who = ' +
		 ((who == play_page.who.ME)       ? 'me' :
		  (who == play_page.who.OPPONENT) ? 'opponent' : 'ERROR!'));
	if (moveTimerId !=  0)
		play_page.clear_countdown();
	bg.bglog("setting divtimer: " + seconds);
	moveTimerLocal = (who == play_page.who.ME);
	moveTimerVal = seconds;
	moveTimerProc();
	return(moveTimerId);
    },

    //
    //clear a countdown teimer before it would otherwise disappear
    //
    clear_countdown: function(countdown_cookie) {
	bg.bglog('play_page.clear_countdown');
	clearTimeout(moveTimerId);
	moveTimerId = 0;
	var divTimer = document.getElementById("divTimer");
	divTimer.style.display = "none";
    },

}

//
// inform game logic that user dicarded a card (or finished)
//
function return_discard(card_idx, done, fold) {
    bg.bglog('play_page.return_discard: card_idx ' + card_idx + ', done = ' + done + ', fold = ' + fold);
    if (done || fold) {
	//ensure that the cards are no longer click-able
	var elem0 = document.getElementById('iL0');
	var elem1 = document.getElementById('iL1');
	var elem2 = document.getElementById('iL2');
	var elem3 = document.getElementById('iL3');
	var elem4 = document.getElementById('iL4');
	//for some reason this doesn't work. no problem though -- we just ignore discards
	//when play_discards_callback_fcn is null
	//elem0.disabled = true;
	//elem1.disabled = true;
	//elem2.disabled = true;
	//elem3.disabled = true;
	//elem4.disabled = true;
	$('#discardPanel').hide();
    }
    play_discards_callback_fcn(card_idx, done, fold);
    if (done || fold)
	play_discards_callback_fcn = null;
}


//
// hide images to avoid artifacts
//
function hide_images() {
    //even though we hide the card img, ensure that the source refers to
    //the back-of-card image... extra insurance that we won't display a card
    //artifact.
    var card_back_src = card_str_to_image_url('');
    for (var i = 0; i < 5; ++i) {
	var limgElem = document.getElementById('iL' + i);
	limgElem.src = card_back_src;
	limgElem.style.display = "none";
	var rimgElem = document.getElementById('iR' + i);
	rimgElem.src = card_back_src;
	rimgElem.style.display = "none";
    }
    var divCoin = document.getElementById("coinPile");
    divCoin.style.display = "none";
    var lcoinImgElem = document.getElementById("ilcoin");
    lcoinImgElem.style.display = "none";
    var rcoinImgElem = document.getElementById("ircoin");
    rcoinImgElem.style.display = "none";
    //
    var irpouch = document.getElementById("irpouch");
    irpouch.style.display = "none";
    //we don't mess with this cuz it's encapsulated by localCoind
    //var ilpouch = document.getElementById("ilpouch");
    //ilpouch.style.display = "none";
    var localCoins = document.getElementById("localCoins");
    localCoins.style.display = "none";
    //
    var divTimer = document.getElementById("divTimer");
    divTimer.style.display = "none";
}

//
// card_str is [2-9][csdh] or [TJQKA][csdh] or falsey for card back image
//
function card_str_to_image_url(card_str) {
    bg.bglog('play_page.card_str_to_image_url: card_str = ' + card_str);
    var image_name = (!!card_str) ? ('card' + card_str + '.png') : 'card_back.png';
    //var url = chrome.extension.getURL('images/' + image_name);
    var url = 'images/' + image_name;
    return(url);
}



//
//etansky fcns
//

// 0 = remote
// 1 = local
//first bring out the remote pouch, then the local pouch, then call callback
function showPouches(callback) {
    var irpouch = document.getElementById("irpouch");
    irpouch.src = "images/pouch2.png";
    irpouch.style.display = "block";
    bg.bglog('play_page.showPouches: animating opponent pouch');
    animateToPos(irpouch, -100, remotePouchY - 10, cardXs[2], remotePouchY, 10, function() {
	//lcoins encompasses the lpouch img and also the balance span
	var lcoins = document.getElementById("localCoins");
	var ilpouch = document.getElementById("ilpouch");
	ilpouch.src = "images/pouch2.png";
	bg.bglog('play_page.showPouches: animating local pouch');
	lcoins.style.left = -70;
	lcoins.style.top = localPouchY;
	lcoins.style.display = "block";
	animateToPos(lcoins, -100, localPouchY + 10, cardXs[2], localPouchY, 10, callback);
    });
}



//
// animation functions
//
var curAnimX,     curAnimY;
var curAnimOrgX,  curAnimOrgY;
var curAnimDestX, curAnimDestY;
var curAnimElem,  animInterval;
var animStepX,    animStepY;

//
// more or less generic function to move an element
// calls callback (via myMove) when the animation is finished
//
function animateToPos(qelem, qfromX, qfromY, qtoX, qtoY, numSteps, callback) {
    bg.bglog('play_page.animateToPos: from x/y = ' + qfromX + '/' + qfromY + ' to ' + qtoX + '/' + qtoY);
    var x;
    curAnimX = qfromX;
    curAnimY = qfromY;
    curAnimElem =  qelem;
    curAnimOrgX = qfromX;
    curAnimOrgY = qfromY;
    curAnimDestX = qtoX;
    curAnimDestY = qtoY;
    curAnimElem.style.top = "" + qfromY + "px";
    curAnimElem.style.left = "" + qfromX + "px";
    animInterval = 50;
    animStepX = (Math.abs(qtoX - curAnimX)) / numSteps;
    animStepY = (Math.abs(qtoY - curAnimY)) / numSteps;
    setTimeout(myMove, animInterval, callback);
}

function myMove(callback) {
    //bg.bglog('play_page.myMove: x/y = ' + curAnimX + '/' + curAnimY);
    if (curAnimOrgX < curAnimDestX) { // direction left to right
	//console.log("blur: " + animStep);
	if (curAnimX < curAnimDestX) {
	    curAnimX = curAnimX + animStepX;
	    if (curAnimX > curAnimDestX) {
		curAnimX = curAnimDestX;
		//console.log("SATX");
	    }
	}
    } else if (curAnimOrgX > curAnimDestX) {
	if (curAnimX > curAnimDestX) {
	    curAnimX = curAnimX - animStepX;
	    if (curAnimX < curAnimDestX) {
		curAnimX = curAnimDestX;
	    }
	}
    }
    if (curAnimOrgY < curAnimDestY) {
	//console.log("blor:" + curAnimY);
	if (curAnimY < curAnimDestY) {
	    curAnimY = curAnimY + animStepY;
	    //console.log("YY:" + curAnimY);
	    if (curAnimY > curAnimDestY) {
		curAnimY = curAnimDestY;
	    }
	}
    } else if (curAnimOrgY >  curAnimDestY)  {
	if (curAnimY > curAnimDestY) {
	    curAnimY = curAnimY - animStepY;
	    if (curAnimY < curAnimDestY) {
		curAnimY = curAnimDestY;
	    }
	}
    }
    //console.log("mymove:"+curAnimX);
    curAnimElem.style.left = "" + curAnimX + "px";
    curAnimElem.style.top = "" + curAnimY + "px";
    if ((curAnimX != curAnimDestX) || (curAnimY != curAnimDestY)) {
	setTimeout(myMove, animInterval, callback);
    } else {
	bg.bglog('myMove: animation is complete');
	if (!!callback)
	    callback();
    }
}


/**
 * Draws a rounded rectangle using the current state of the canvas.
 * If you omit the last three params, it will draw a rectangle
 * outline with a 5 pixel border radius
 * @param {CanvasRenderingContext2D} ctx
 * @param {Number} x The top left x coordinate
 * @param {Number} y The top left y coordinate
 * @param {Number} width The width of the rectangle
 * @param {Number} height The height of the rectangle
 * @param {Number} radius The corner radius. Defaults to 5;
 * @param {Boolean} fill Whether to fill the rectangle. Defaults to false.
 * @param {Boolean} stroke Whether to stroke the rectangle. Defaults to true.
 */
function roundRect(ctx, x, y, width, height, radius, fill, stroke) {
  if (typeof stroke == "undefined" ) {
    stroke = true;
  }
  if (typeof radius === "undefined") {
    radius = 5;
  }
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.lineTo(x + width - radius, y);
  ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
  ctx.lineTo(x + width, y + height - radius);
  ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
  ctx.lineTo(x + radius, y + height);
  ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
  ctx.lineTo(x, y + radius);
  ctx.quadraticCurveTo(x, y, x + radius, y);
  ctx.closePath();
  if (stroke) {
    ctx.stroke();
  }
  if (fill) {
    ctx.fill();
  }
}


function showMoveTimer()
{
	bg.bglog("MoveTimerProc");
        var canvas1 = document.getElementById("canvasTimer");
        var ctx = canvas1.getContext("2d");
	var divTimer = document.getElementById("divTimer");
	divTimer.style.left = cardXs[4] + (cardW*3) + "px";
	if (moveTimerLocal)
		divTimer.style.top = (localCardsY+25) + "px";
	else
		divTimer.style.top = (remoteCardsY+25) +  "px";
	divTimer.style.display = "block";
	ctx.lineWidth = 4;
	ctx.strokeStyle = "#000000";
	ctx.fillStyle = "brown"; "#abc";
	roundRect(ctx, 5, 5, timerW, timerH, radius, true);
	ctx.font="20px Georgia";
	ctx.textAlign="center";
	ctx.textBaseline = "middle";
	ctx.fillStyle = "yellow"; // "#000000";
	var rectHeight = timerH;
	var rectWidth = timerW;
	var rectX = 5;
	var rectY = 4;
	ctx.fillText(moveTimerVal,rectX+(rectWidth/2),rectY+(rectHeight/2));


}


function moveTimerProc()
{
//	console.log("tick..." + moveTimerVal);
	showMoveTimer();
	moveTimerVal--;
	if (moveTimerVal >= 0)
		moveTimerId = setTimeout(moveTimerProc , 1000);
	else {
		var divTimer = document.getElementById("divTimer");
		divTimer.style.display = "none";
	}

}
