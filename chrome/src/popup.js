var welcome_page = null;

var strings = require('./strings');
var bg = require('./background');
var util = require('./util');
var ether = require('./ether');
var welcome = require('./welcome');

shtoggle = false;

document.addEventListener('DOMContentLoaded', function() {
    util.find_unique_id(function(id) {
        bg.bglog('content loaded... id = ' + id);
	util.typer_init();
	//
	var myfunc = function() {
	    shtoggle = shtoggle == true ? false: true;
	    $("#subHeading1").html(shtoggle == true ? strings.Alternating_Subtitle_1 : strings.Alternating_Subtitle_2 );
	    setTimeout(myfunc, 10000);
	};
	myfunc();
	//
	popup.do_welcome();
    });
}, false);


var popup = module.exports = {
    do_welcome: function() {
	bg.bglog('in popup.do_welcome');
	welcome.run(function(action) {
	    bg.bglog('popup.do_welcome: action = ' + action.text);
	    if (action == welcome.Welcome_Status.SHOW_AGAIN) {
		popup.do_welcome();
	    } else if (action == welcome.Welcome_Status.EXIT) {
		return;
	    }
	});
    }

};
