var bg = require('./background');
var ether = require('./ether');
var aesjs = require('aes-js');
var CryptoJS = require('crypto-js');
var jQuery = global.$ = window.jQuery = require('jquery');
var strings = require('./strings');

var util = module.exports = {

    find_unique_id: function(callback) {
	bg.bglog('in unique_id...');
	var id = localStorage["uniqueID"];
	if (!id) {
	    id = util.gen_random_token();
	    localStorage["uniqueID"] = id;
	}
	callback(id);
    },

    gen_random_token: function() {
	//e.g. 8 * 32 = 256 bits token
	var randomPool = new Uint8Array(32);
	crypto.getRandomValues(randomPool);
	var hex = '';
	for (var i = 0; i < randomPool.length; ++i) {
	    hex += randomPool[i].toString(16);
	}
	//e.g. db18458e2782b2b77e36769c569e263a53885a9944dd0a861e5064eac16f1a
	return hex;
    },

    hex_str_to_bytes: function(hex) {
	for (var bytes = [], c = 0; c < hex.length; c += 2)
	    bytes.push(parseInt(hex.substr(c, 2), 16));
	return bytes;
    },

    aes_decrypt: function(msg, iv, key) {
	var iv_bytes = aesjs.utils.hex.toBytes(iv);
	var key_bytes = aesjs.utils.hex.toBytes(key);
	var aes_cbc = new aesjs.ModeOfOperation.cbc(key_bytes, iv_bytes);
	var msg_bytes = aesjs.utils.hex.toBytes(msg);
	var decrypted_bytes = aes_cbc.decrypt(msg_bytes);
	var decrypted_msg = aesjs.utils.utf8.fromBytes(decrypted_bytes);
	return(decrypted_msg);
    },

    aes_encrypt: function(text, iv, key) {
	if ((text.length % 16) != 0) {
	    bg.bglog('util.aes_encrypt: text length = ' + text.length + '. padding.');
	    var pad = '                 ';
	    var padLen = 16 - (text.length % 16);
	    text += pad.slice(padLen);
	    bg.bglog('util.aes_encrypt: now text length = ' + text.length);
	}
	var iv_bytes = aesjs.utils.hex.toBytes(iv);
	var key_bytes = aesjs.utils.hex.toBytes(key);
	var text_bytes = aesjs.utils.utf8.toBytes(text);
	var aes_cbc = new aesjs.ModeOfOperation.cbc(key_bytes, iv_bytes);
	var encryptedBytes = aes_cbc.encrypt(text_bytes);
	var encryptedHex = aesjs.utils.hex.fromBytes(encryptedBytes);
	return(encryptedHex);
    },

    aes_key_generator: function() {
	var key = CryptoJS.lib.WordArray.random(24);
	var hex = CryptoJS.enc.Hex.stringify(key);
	return(hex);
    },

    retrieve_acct: function(id, callback) {
	bg.bglog('in retrieve_acct...');
	var acct = bg.get_acct();
	if (!!acct) {
	    callback(acct);
	    return;
	}
	var key = util.get_private_key(id);
	if (!!key) {
	    bg.bglog('retrieve_acct: key = ' + key);
	    ether.private_key_to_addr(key, function(err, addr) {
		bg.bglog('retrieve_acct: addr = ' + addr);
		if (!!addr)
		    bg.save_acct(addr);
		callback(addr);
	    });
	    return;
	}
	callback('');
    },

    get_private_key: function(id) {
	var privatekey = "";
	//note that undefined == null
	var encryptedPrivateKey = localStorage["privateKey"];
	if (!!encryptedPrivateKey) {
	    var iv = bg.master_iv_from_id(id);
	    var aesKey = strings.local_key;
	    privatekey = util.aes_decrypt(encryptedPrivateKey, iv, aesKey);
	}
	return(privatekey);
    },

    save_private_key: function(id, privateKey) {
	var iv = bg.master_iv_from_id(id);
	var aesKey = strings.local_key;
	var encryptedPrivateKey = util.aes_encrypt(privateKey, iv, aesKey);
	localStorage["privateKey"] = encryptedPrivateKey;
	//acct will be re-computed from private key
	localStorage["acct"] = "";
    },

    at_most_x_decimals: function(number, decimals = 1) {
	var tens = Math.pow(10, decimals);
	return(Math.round(number * tens) / tens);
    },

    left_pad: function(number, pad_to, ch) {
	var pad_char = (typeof ch !== 'undefined') ? ch : '0';
	var pad = new Array(1 + pad_to).join(pad_char);
	var padded = (pad + number.toString()).slice(-pad_to);
	return padded;
    },

    typer_init: function() {
	typerInit();
    },

};


function Typer_Tool() {
    this.timer = null;
    this.msgs = [];
}

var typer_tools = {};
function typerInit() {
    $.fn.typer = function(id, text, options) {
	options = $.extend({}, {
            ch: ' ',
            newMsgDelay: 1000,
            duration: 60,
            endless: true,
	}, options || text);
	text = $.isPlainObject(text) ? options.text : text;

	var elem = $(this),
            isTag = false,
            c = 0;
	var typer_tool = typer_tools[id];
	if (!typer_tool)
	    typer_tools[id] = typer_tool = new Typer_Tool();
	typer_tool.msgs.push(text);
	//console.log('typer: got ' + text + '; typer_msgs.length = ' + typer_msgs.length);
	//if there's already a message, then typer_msgs will shift in the new msg after the old one completes
	if (typer_tool.msgs.length == 1) {
	    (function typetext() {
		//always add blank at end so string length is at least 1
		var e = typer_tool.msgs[0] + options.ch;
		var ch = e.substr(c++, 1);
		if (ch === '<' )
		    isTag = true;
		if (ch === '>' )
		    isTag = false;
		if (c <= e.length && isTag) {
                    typetext();
		} else if (c <= e.length) {
		    elem.html(e.substr(0, c));
		    //var nextDelay = (typer_tool.msgs.length > 2) ? options.duration / 2 : (c < e.length ? options.duration : options.newMsgDelay);
		    var nextDelay = (c < e.length ? options.duration : options.newMsgDelay);
                    typer_tool.typer_timer = setTimeout(typetext, nextDelay);
		} else {
		    c = 0;
		    isTag = false;
		    //console.log('typer::typetext: got to end of: ' + typer_tool.msgs[0]);
		    //blank in preparation for next string, or empty
		    elem.html("");
		    if (typer_tool.msgs.length > 1) {
			//console.log('typer::typetext: typer_msgs.length is ' + typer_msgs.length + '; shifting...');
			//console.log('typer::typetext: next msg is: ' + typer_msgs[1]);
			//another string is waiting...
			typer_tool.msgs.shift();
		    }
		    while (typer_tool.msgs.length > 0 && (!typer_tool.msgs[0] || typer_tool.msgs[0] == "")) {
			//console.log('typer::typetext: typer_msgs.length is ' + typer_msgs.length + '; new msg is blank, skipping...');
			//string is empty; skip it
			typer_tool.msgs.shift();
		    }
		    if (typer_tool.msgs.length > 0) {
			//console.log('typer::typetext: typer_msgs.length is ' + typer_tool.msgs.length + '; starting new msg: ' + typer_tool.msgs[0]);
			//if string exists (last string was not empty), then start it off
			typer_tool.timer = setTimeout(typetext, options.duration);
		    }
		}
	    })();
	}
    };
}
