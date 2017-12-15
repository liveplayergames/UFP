

//
var main_div = null;


var poker_page = module.exports = {

    hide: function(callback) {
	document.body.removeChild(main_div);
    },
    
    show: function(callback) {
	util.bglog('in poker_page.show');
	main_div = document.createElement("div");	
	//
	//make the exit button
	//
	var p;
	var text;
	p = document.createElement("p");
	main_div.appendChild(p);    
	var exit_button = document.createElement('button');
	text = document.createTextNode("Exit");
	exit_button.appendChild(text);
	main_div.appendChild(exit_button);
	exit_button.addEventListener('click', function() {
	    callback(Poker_Page_Action.EXIT);
	    return;
	});
	p = document.createElement("p");
	main_div.appendChild(p)   

	document.body.appendChild(main_div);	
	//
	//
    }
};

