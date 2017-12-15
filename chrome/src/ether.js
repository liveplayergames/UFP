/* ------------------------------------------------------------------------------------------------------------------------------------------------------------------------
   couple of utility fcn to do things relating to eth
   - check balance
   - send eth
   ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ */
/*
var https = require('https');
var ethabi = require('ethereumjs-abi');
var WEI_PER_ETH    = 1000000000000000000;
var ETHERSCAN_APIKEY = "VRPDB8JW4CHSQV6A6AHBMGFWRA1E9PR6BC";
*/
var bg = require('./background');
var ethUtils = require('ethereumjs-util');
var ethtx = require('ethereumjs-tx');
var Buffer = require('buffer/').Buffer;
var BN = require("bn.js");
var WANT_RAW_TX = true;
var WEI_PER_FINNEY = 1000000000000000;
var NOMINAL_GAS_LIMIT = 40000;

var cached_block_count = "";
var block_count_refresh_sec = 0;
var DEFAULT_GAS_PRICE = 10000000000;
var history_min_price = DEFAULT_GAS_PRICE;
var price_refresh_sec = 0;

var ether = module.exports = {

    /* ------------------------------------------------------------------------------------------------------------------------------------------------------------------------
       get current block count
       ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ */
    get_block_count: function(callback) {
	bg.bglog('ether.get_block_count');
	var count = -1;
	var now_sec = Math.floor(Date.now() / 1000);
	if (now_sec - block_count_refresh_sec < 5) {
	    callback(cached_block_count);
	    return;
	}
	var url = 'https://api.etherscan.io/api?module=proxy&action=eth_blockNumber';
	bg.better_fetch(url, function(str, err) {
	    if (!str || !!err) {
		bg.bglog("get_block_count err: " + err);
		callback(cached_block_count);
	    } else {
		//typical response is:
		// {"jsonrpc":"2.0","result":"0x2f796a","id":83}
		var blockResp = JSON.parse(str);
		var blockHexStr = blockResp.result;
		if (!!blockHexStr) {
		    count = parseInt(blockHexStr, 16);
		    cached_block_count = count;
		}
		callback(count);
	    }
	});
    },

    private_key_to_addr: function(key, callback) {
	bg.bglog('in ether.private_key_to_addr(' + key + ')');
	key = '0x' + key;
	var err = null;
	var acct_addr = '';
	try {
	    acct_addr = ethUtils.privateToAddress(key).toString('hex')
	    bg.bglog('ether.private_key_to_addr: got ' + acct_addr + '; calling callback...');
	    acct_addr = '0x' + acct_addr;
	} catch (key_err) {
	    err = key_err;
	}
	callback(err, acct_addr);
    },


    get_gas_price: function(callback) {
	var now_sec = Math.floor(Date.now() / 1000);
	if (now_sec - price_refresh_sec < 10 * 60) {
	    callback(history_min_price);
	    return;
	}
	var priceURL = 'https://api.etherscan.io/api?module=proxy&action=eth_gasPrice'
	bg.bglog('ether.get_gas_price: url = ' + priceURL);
	bg.better_fetch(priceURL, function(str, err) {
	    var price = history_min_price;
	    if (!str || !!err) {
		bg.bglog("ether.get_gas_price: err = " + err);
	    } else {
		//typical response is:
		// {"jsonrpc":"2.0","result":"0x4e3b29200","id":73}
		var gasResp = JSON.parse(str);
		var priceHexStr = gasResp.result;
		if (!!priceHexStr) {
		    price = parseInt(priceHexStr, 16);
		    bg.bglog("ether.get_gas_price: price = " + price);
		    price_refresh_sec = Math.floor(Date.now() / 1000);
		    history_min_price = price;
		}
	    }
	    callback(price);
	});
    },


    //
    // refresh_balance
    //
    //  tx-nonce is periodically re-checked until last_nonce equals validated_nonce. then:
    // if the retrieved balance is greater than expected-balance, then we adopt the new balance as 'verified.'
    //   this would happen if there was an external deposit to the account
    // if the difference between the retrieved balance and expected-balance is less than BALANCE_ESTIMATE_SLOP, then we adopt the retrieved
    // balance as 'verified.'
    //   this is the normal mechanism for updating the balance
    // if the difference is greater than BALANCE_ESTIMATE_SLOP, then we re-check the balance every so often; but we give up and adopt the retrieved
    // balance as 'verified' if a long time has passed.
    //   this will happen if there's been an error in the expected balance calculation, or if there was a combination of a failed tx and a transaction
    //   from some other source.
    //
    // if the expected balance is not equal to the verified balance, then we call the expectedBalanceCallback and we continue to re-check the balance
    // until the expected balance is equal to the verified balance. when the expected balance equals the verified balance, then we call the
    // verifiedBalanceCallback.
    //
    // the calbacks are:
    // expectedBalanceCallback(expectedBalance)
    // verifiedBalanceCallback(verifiedBalance)
    //
    refresh_balance: function(acct, expectedBalanceCallback, verifiedBalanceCallback) {
	get_nonce(acct, function(last_nonce, validated_nonce) {
	    bg.bglog('refresh_balance: last_nonce = ' + last_nonce + ', validated_nonce = ' + validated_nonce);
	    if (last_nonce != validated_nonce) {
		bg.bglog('refresh_balance: check again in 30 secs');
		if (!!expectedBalanceCallback) {
		    var expectedBalance = bg.get_estimated_balance(acct);
		    expectedBalanceCallback(expectedBalance);
		}
		setTimeout(ether.refresh_balance, 30000, acct, null, verifiedBalanceCallback);
		return;
	    }
	    ether.get_balance(acct, true, function(balance) {
		var expectedBalance = bg.get_estimated_balance(acct)
		var expectedBalanceSec = bg.get_estimated_balance_sec(acct);
		var nowSec = Math.floor(Date.now() / 1000);
		bg.bglog('refresh_balance: expected balance = ' + expectedBalance + ', current = ' + balance);
		//if balance if close to expected balanace, or greater than expected balance, or too much time has elapsed,
		//then just adopt current balance as verified
		if (expectedBalance - balance <= 1 || nowSec - expectedBalanceSec > 900) {
		    bg.save_estimated_balance(acct, balance);
		    verifiedBalanceCallback(balance);
		    return;
		}
		if (!!expectedBalanceCallback)
		    expectedBalanceCallback(expectedBalance);
		setTimeout(ether.refresh_balance, 30000, acct, null, verifiedBalanceCallback);
		return;
	    });
	});
    },


    //
    // get balance of an acct (returned as string)
    // returns the current balance of the acct, as read from etherscan.io. no provisions are made for pending transactions.
    get_balance: function(acct, size_is_finney, callback) {
	bg.bglog('in ether.get_balance(' + acct + ', ' + size_is_finney + ')');
	var balance = -1;
	var url = 'https://api.etherscan.io/api?module=account&action=balance&address=' + acct + '&tag=latest';
	bg.better_fetch(url, function(str, err) {
	    if (!str || !!err) {
		bg.bglog("get_balance  err: " + err);
		callback(balance);
	    } else {
		bg.bglog('get_balance resp = ' + str);
		//typical response is:
		// {"status":"1","message":"OK","result":"740021584819750779479303"}
		var balanceResp = JSON.parse(str);
		var balance = balanceResp.result;
		bg.bglog('get_balance bal = ' + balance);
		if (size_is_finney) {
		    var big_wei_balance = new BN(balance);
		    var wei_per_finney = new BN(WEI_PER_FINNEY);
		    big_wei_balance = big_wei_balance.div(wei_per_finney);
		    balance = big_wei_balance.toString();
		}
		bg.bglog('ether.get_balance: calling callback...');
		callback(balance);
	    }
	});
    },

    //
    //send fcn that uses the default broadcast fcn
    //
    send: function(acct, key, to_addr, size, size_is_finney, gas_limit, data, callback) {
	var broadcast_fcn = ether.broadcast_tx;
	serial_send(acct, key, to_addr, size, size_is_finney, gas_limit, data, broadcast_fcn, callback);
    },


    //
    //broadcast_fcn should be eg. broadcast_tx(tx, function(err, txid))
    //
    raw_send: function(acct, key, to_addr, size, size_is_finney, gas_limit, data, broadcast_fcn, callback) {
	bg.bglog('ether.raw_send');
	serial_send(acct, key, to_addr, size, size_is_finney, gas_limit, data, broadcast_fcn, callback);
    },


    //
    //default broadcast fcn uses etherscan.io
    //callback args are err, txid
    //
    broadcast_tx: function(tx, callback) {
	bg.bglog('ether.broadcast_tx');
	var url = 'https://api.etherscan.io/api?module=proxy&action=eth_sendRawTransaction&hex=' + tx + '&apikey=' + ETHERSCAN_APIKEY;
	bg.better_fetch(url, function(str, err) {
	    if (!str || !!err) {
		bg.bglog('ether.broadcast_tx: err = ' + err);
		callback(err, '');
	    } else {
		//typical response is:
		//{ "jsonrpc": "2.0", "result": "0xd22456131597cff2297d1034f9e6f790e9678d85c041591949ab5a8de5f73f04", "id": 1 }
		// alternately:
		// { "jsonrpc":"2.0","error": {"code":-32010, "message": "Transaction nonce is too low. Try incrementing the nonce.","data": null},"id":1 }
		var txid = util.extract_json_field(str, 'result');
		if (!txid) {
		    bg.bglog("ether.broadcast_tx: failed! reponse is: " + str);
		    err = util.extract_json_field(str, "message");
		}
		callback(err, txid);
	    }
	});
    },
};



/* ------------------------------------------------------------------------------------------------------------------------------------------------------------------------
   we serialize calls to get-nonce, get-gas-price, create-signed-tx, and broadcast-tx
   the main point of the serialization is to increment the nonce, exactly once at the end of every successful send; and to always use the next nonce at the start of
   each new send.
   ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ */
function Send_Info(acct, key, to_addr, size, size_is_finney, gas_limit, data, broadcast_fcn, callback) {
    this.acct = acct;
    this.key = key;
    this.to_addr = to_addr;
    this.size = size;
    this.size_is_finney = size_is_finney;
    this.gas_limit = gas_limit;
    this.data = data;
    this.broadcast_fcn = broadcast_fcn;
    this.callback = callback;
}

var send_list = [];

function serial_send(acct, key, to_addr, size, size_is_finney, gas_limit, data, broadcast_fcn, callback) {
    bg.bglog('ether.serial_send');
    var send_info = new Send_Info(acct, key, to_addr, size, size_is_finney, gas_limit, data, broadcast_fcn, callback);
    send_list.push(send_info);
    if (send_list.length == 1)
	send_next();
}

function send_next() {
    bg.bglog('ether.send_next');
    if (send_list.length > 0) {
	var send_info = send_list[0];
	send_guts(send_info.acct, send_info.key, send_info.to_addr, send_info.size, send_info.size_is_finney, send_info.gas_limit, send_info.data, send_info.broadcast_fcn,
		  function(txid) {
		      //even thought the send is complete, we don't delete the head entry from the send_list, until we
		      //are ready to process the next entry. this is to prevent any intervening calls to serial send, which
		      //would have also called send_next
		      send_info.callback(txid);
		      send_list.splice(0, 1);
		      if (send_list.length > 0)
			  send_next();
		  });
    }
}




//
//do a transaction
//we do not call the callback fcn until after the broadcast fcn returns
//
function send_guts(acct, key, to_addr, size, size_is_finney, gas_limit, data, broadcast_fcn, callback) {
    bg.bglog('ether.send_guts');
    get_nonce(acct, function(last_nonce, validated_nonce) {
	ether.get_gas_price(function(gas_price) {
	    var next_nonce = last_nonce + 1;
	    bg.bglog("ether.send_guts: nonce = " + next_nonce + ", gas_price = " + gas_price);
	    var tx = create_signed_tx(key, to_addr, size, size_is_finney, data, next_nonce, gas_limit, gas_price);
	    broadcast_fcn(tx, function(err, txid) {
		if (!txid && !!err && err.indexOf("nonce is too low") >= 0) {
		    bg.save_tx_nonce(acct, next_nonce);
		    send_guts(acct, key, to_addr, size, size_is_finney, data, broadcast_fcn, callback);
		    return;
		}
		if (!!txid)
		    bg.save_tx_nonce(acct, next_nonce);
		callback(txid);
	    });
	});
    });
}


var nonce_counter = 0;
// callback(tx-nonce, validated-nonce)
function get_nonce(acct, callback) {
    if ((++nonce_counter) & 0x1) {
	get_nonce_from_tx_transactions(acct, function(nonce) {
	    update_validated_nonce(acct, nonce, callback);
	});
    } else {
	get_nonce_from_acct_info(acct, function(nonce) {
	    update_validated_nonce(acct, nonce, callback);
	});
    }
}


//
// update tx-nonce and validated-nonce
// updates and records the tx-nonce and validated-nonce and calls callback(tx-nonce, validated-nonce)
//
// if the retrieved tx-nonce is greater than the last-tx-none, then we adopt the retrieved tx-nonce
//   there must have been a transaction from some other source using the same account
// if the retrieved tx-nonce is less than the last-tx-none, then we wait for the tx-nonce to advance. we re-check the tx-nonce every so often;
// but we give up and adopt the retrieved tx-nonce if a long time has passed, and there's been no change to the tx-nonce.
//   this will happen when there's an error in a transaction.
//
function update_validated_nonce(acct, nonce, callback) {
    bg.bglog('ether.update_validated_nonce: nonce = ' + nonce);
    var curValidatedNonce = parseInt(nonce);
    var txNonce = bg.get_tx_nonce(acct);
    var validatedNonce = bg.get_validated_nonce(acct);
    if (isNaN(curValidatedNonce)) {
	bg.bglog('ether.update_validated_nonce: retrieved nonce cannot be parsed, "' + nonce + '"; historical nonce = ' + txNonce + ' / ' + validatedNonce);
    }
    if (curValidatedNonce > validatedNonce) {
	//validated nonce has advanced
	bg.bglog('ether.update_validated_nonce: advance validatedNonce, ' + validatedNonce + ' => ' + curValidatedNonce);
	validatedNonce = curValidatedNonce;
	bg.save_validated_nonce(acct, validatedNonce);
    }
    if (validatedNonce > txNonce) {
	//some tx's, which we previously had given up upon, have actually succeeded
	bg.bglog('ether.update_validated_nonce: advance txNonce, ' + txNonce + ' => ' + validatedNonce);
	txNonce = validatedNonce;
	bg.save_tx_nonce(acct, txNonce);
    }
    if (txNonce == validatedNonce) {
	//all outstanding tx's have completed
	bg.bglog('ether.update_validated_nonce: txNonce = validatedNonce = ' + txNonce);
	callback(txNonce, validatedNonce);
	return;
    }
    //last tx nonce is gt. validated nonce
    var now_sec = Math.floor(Date.now() / 1000);
    var validatedNonceSec = bg.get_validated_nonce_sec();
    var elapsed_sec = now_sec - validatedNonceSec;
    if (elapsed_sec > 900) {
	//more han 15 minutes have passed since validated nonce has changed.... it is clearly stuck. so reset
	//the last tx nonce to the validated none. all the invervening tx's are lost.
	//note is is also possible that some of the invervening tx's will succeed after we fill a single hole.
	//in that case when we refresh the nonce it will skip forward a bit...
	bg.bglog('ether.update_validated_nonce: uh oh! validated nonce has not advanced in ' + elapsed_sec + ' secs');
	bg.bglog('reseting txnonce (was ' + txNonce + ') to validated nonce, ' + validatedNonce);
	txNonce = validatedNonce;
	bg.save_tx_nonce(acct, txNonce);
	callback(txNonce, validatedNonce);
	return;
    }
    //here tx nonce is ahead of validated nonce. there are outstanding, unvalidated tx's, but the last validated nonce
    //was less than 15 minutes ago -- so as far as we know tx's are going out. just return last tx nonce.
    bg.bglog("ether.update_validated_nonce: retrieved nonce, " + nonce + " is less than historical nonce, " + txNonce + " -- use historical nonce...");
    callback(txNonce, validatedNonce);
}


function get_nonce_from_acct_info(acct, callback) {
    var nonce = -1;
    var nonce_URL = 'https://api.etherscan.io/api?module=proxy&action=eth_getTransactionCount&address=' + acct + '&tag=latest';
    bg.bglog('ether.get_nonce: url = ' + nonce_URL);
    bg.better_fetch(nonce_URL, function(str, err) {
	if (!str || !!err) {
	    bg.bglog("ether.get_nonce: err = " + err);
	} else {
	    //typical response is:
	    // {"jsonrpc":"2.0","result":"0xaf5d","id":1}
            //note that first payment nonce is 0; so if you've never made a payment then we have a convention that our "last-used-nonce" is -1.
            //the get-nonce api on etherchain.org used to provide the actual nonce value from the last transaction. etherscan.io however is actually
	    //giving the number of transactions sent from an address. that is, if an address has never been used, then the count is 0 -- so we
	    //subtract one to get the "last-used-nonce."
	    var txCntResp = JSON.parse(str);
	    var txCntHexStr = txCntResp.result;
	    if (!!txCntHexStr) {
		var txCnt = parseInt(txCntHexStr, 16);
		nonce = txCnt - 1;
	    }
	    bg.bglog("ether.get_nonce: nonce = " + nonce);
	}
	callback(nonce);
    });
}


function get_nonce_from_tx_transactions(acct, callback) {
    var nonce = -1;
    var offset = 50;
    var nonce_URL = 'https://api.etherscan.io/api?module=account&action=txlist&address=' + acct + '&startblock=0&endblock=99999999&page=1&offset=' + offset + '&sort=desc';
    bg.bglog('ether.get_nonce_from_tx_transactions: url = ' + nonce_URL);
    bg.better_fetch(nonce_URL, function(str, err) {
	if (!str || !!err) {
	    bg.bglog('ether.get_nonce_from_tx_transactions: err = ' + err);
	} else {
	    //typical response is:
	    //
	    // {"status":"1","message":"OK","result":
	    //  [
	    //   { "blockNumber":"65342", "timeStamp":"1439235315", "hash":"0x621de9a006b56c425d21ee0e04ab25866fff4cf606dd5d03cf677c5eb2172161",
	    //     "nonce":"1", "blockHash":"0x889d18b8791f43688d07e0b588e94de746a020d4337c61e5285cd97556a6416e", "transactionIndex":"0",
	    //     "from":"0x3fb1cd2cd96c6d5c0b5eb3322d807b34482481d4",
	    //     "to":"0xde0b295669a9fd93d5f28d9ec85e40f4cb697bae",
	    //     "value":"0",
	    //     "gas":"122269", "gasPrice":"50000000000", "isError":"0", "input":"...","contractAddress":"","cumulativeGasUsed":"122207",
	    //     "gasUsed":"122207","confirmations":"4215300"
	    //   },
	    //	   .....
	    //  ]
            // }
	    //or
	    //null
	    //bg.bglog("ether.get_nonce_from_tx_transactions: rsp = " + str);
	    var status = "";
	    var idx = str.indexOf('status');
	    if (idx >= 0) {
		status = bg.extract_json_field(str, 'status');
		//bg.bglog('ether.get_nonce_from_tx_transactions: status = ' + status);
		idx = str.substring(idx) + 6;
	    }
	    if (status !== "1") {
		bg.bglog(str + "; bad status = " + status);
                callback(nonce);
		return;
	    }
	    //bg.bglog("looking for " + acct);
	    for (var i = 0; i < 100; ++i) {
		idx = str.indexOf('{');
                if (idx >= 0)
		    str = str.substring(idx + 1);
                else
		    break;
		var sender = bg.extract_json_field(str, "from");
                if (sender !== acct) {
		    //bg.bglog('ether.get_nonce_from_tx_transactions: ignore sender ' + sender);
                    continue;
		}
		var nonce_str = bg.extract_json_field(str, 'nonce');
		//bg.bglog('ether.get_nonce_from_tx_transactions: nonce_str = ' + nonce_str);
		if (!!nonce_str) {
		    var this_nonce = parseInt(nonce_str, 10);
		    if (this_nonce > nonce)
			nonce = this_nonce;
		}
		idx = str.indexOf('}');
                if (idx >= 0)
		    str = str.substring(idx + 1);
                else
		    break;
            }
	    callback(nonce);
	}
    });
}


function create_signed_tx(key, to_addr, size, size_is_finney, data, nonce, gas_limit, gas_price) {
    bg.bglog('ether.create_signed_tx');
    var key_hex = new Buffer(key, 'hex');
    var big_wei_size = new BN(size);
    if (size_is_finney) {
	var wei_per_finney = new BN(WEI_PER_FINNEY);
	big_wei_size = big_wei_size.mul(wei_per_finney);
    }
    var big_gas_limit = new BN(gas_limit);
    var big_gas_price = new BN(gas_price);
    var big_nonce = new BN(nonce);
    var tx = new ethtx(null);
    //for browserfy compatibility use BN.toArrayLike(Buffer, 'be') instead of bignum.toBuffer()
    tx.nonce = big_nonce.toArrayLike(Buffer, 'be');
    tx.gasPrice = big_gas_price.toArrayLike(Buffer, 'be');
    tx.gasLimit = big_gas_limit.toArrayLike(Buffer, 'be');
    tx.value = big_wei_size.toArrayLike(Buffer, 'be');
    tx.to = to_addr,
    tx.data = data;
    tx.sign(key_hex);
    //bg.bglog("wei required = " + tx.getUpfrontCost().toString());
    var serialized_tx = tx.serialize();
    var hex_serialized_tx = serialized_tx.toString('hex');
    return(hex_serialized_tx);
}
