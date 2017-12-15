//
// all strings go here
//

//
// glabal vars
//

var strings = module.exports = {

    socket_url: "https://poker.unclefinneys.com:",
    server_url: "https://poker.unclefinneys.com:",
    lava_contract_addr: "0xb56c725467c7eec851b1a4a4222d930932b04e89",
    local_key: "5ebbcef641cc02abaf6987fdb4991c20a81a124562bdc08a",

    //simple strings (_Str)-- no replacement keywords
    //more complex messages (_Msg) -- include replacement keywords


    Alternating_Subtitle_1:
    "Dont be scammed! Please make sure the URL is https://unclefinneys.com/poker-chrome",
    Alternating_Subtitle_2:
    "A verifiably fair poker game with bets impartially escrowed by an Ethereum Smart Contract",

    //
    Enter_Username_Str:
    "Enter a username, by which you will be known to other players.\
<p><p>Note: Your username must be unique. If you are running Uncle Finney's Poker \
on several devices, then on each device you must have a unique username and \
Ether account.",

    //
    Enter_Private_Key_Str:
    "Enter a private key for your Ether account.\
<p><p>Please use a dedicated Ether account for Uncle Finney's Poke, chrome. Even though \
take reasonable precautions to safeguard your price key, we recommend that you do NOT \
use an account in which you have lots of Ether -- instead make a new account, and only \
transfer into the account the amount of Ether that you want to use for playing. \
<p><p>To create a new Ether account you can visit\
<br/><a target=_blank href=https://www.myetherwallet.com >My Ether Wallet</a>.\
<p><p>Note: The javascript code that runs this poker client executes completely within \
your browser. We never store your private key on any backend server; nor is it ever \
sent anywhere. Your private key is only used to sign your bet transactions, which are \
then sent to an Ethereum smart contract, which acts as an escrow until the end of the \
game. \
<p><p>Note: Your Ether account must be unique. If you are running Uncle Finney's Poker \
on several devices, then on each device you must have a unique username and \
Ether account.",


    Balance_Too_Low_Title_Str:
    "Your Balance is Too Low",

    Balance_Too_Low_Msg:
    "Your balance must be at least BAL_MULTIPLE times the maximum raise (MIN_WAGER Finney).\
<br/><br/>Since your balance is less than this amount, WHAT_TO_DO.",

    Get_More_Finney_Str:
    "you will need to acquire more Finney before you can play",

    //
    //select
    //
    Declined_Your_Challenge_Str:       "has declined your challenge",
    Chickened_Out_Str:                 "chikkened out!",
    Fairness_Bug_Title_Str:            "Fairness Bug!!!",
    Fairness_Bug_Message_Str:          "This game appears to be RIGGED!",
    No_Opponents_Str:                  "There are no opponents online",
    Select_A_Player_Str:               "Select a player to send an invitation for a game",

    Wait_For_Challengee_Msg:           "waiting for OPPONENT to respond to your challenge",
    Wait_For_Opponent_Ante_Msg:        "waiting for OPPONENT to deposit his ante",
    Wait_For_Opponent_Bet_Msg:         "waiting for OPPONENT to bet",
    Opponent_Deposit_Difficulty_Msg:   "OPPONENT is experiencing technical or connectivity issues; we will give him an extra SECONDS seconds",
    Opponent_Backed_Out_Msg:           "OPPONENT has backed out!",
    Opponent_Deposited_Ante_Msg:       "OPPONENT has deposited his ante",
    Depositing_Ante_Msg:               "Depositing ante... (SIZE Finney)",
    Opponent_Cut_Deck_Msg:             "OPPONENT has cut the deck...",
    Cutting_the_deck_Msg:              "Cutting the deck...",

    //
    //bets
    //
    Place_Your_Bet_Str:                "Place Your Bet!",
    Opponent_Has_Raised_You_Msg:       "OPPONENT has raised you SIZE Finney",
    Opponent_Has_Bet_Msg:              "OPPONENT bet SIZE Finney; Your turn",
    Opponent_Checks_Msg:               "OPPONENT checks",
    Opponent_Calls_Msg:                "OPPONENT calls",
    Betting_Is_Complete_Str:           "Betting is complete",
    Bet_Limited_Str:                   " --- Note: Your bet is limited to ensure that you have sufficient funds to call any opponent bet",
    Placing_Bet_Msg:                   "Placing your bet... SIZE Finney",

    //
    //dealing
    //
    Wait_Opponent_Discard_Msg:         "waiting for OPPONENT to discard...",
    Select_Cards_To_Replace_Str:       "Select cards to replace",

    //
    //these messages relate to end of game, forfeits
    //
    You_Have_Str:                      "You have",
    Opponent_Has_Msg:                  "OPPONENT has",
    Abandoned_Game_Title_Str:          "Abandoned Game",
    Abandoned_Game_Msg:                "This game has been abandoned!",
    You_Win_Title_Str:                 "You Win!",
    Opponent_Wins_Title_Msg:           "OPPONENT Wins",
    You_Forfeited_Str:                 "You have forfeited the game!",
    Opponent_Forfeited_Msg:            "OPPONENT has forfeited the game!",
    Forfeited_Msg_Prompt_Str:          "Forfeit message:",
    You_Win_The_Pot_Str:               "You win the pot!",
    Opponent_Wins_The_Pot_Msg:         "OPPONENT wins the pot. Better luck next time.",
    Funds_Will_Be_Credited_Str:        "\n\nNote: the pot will be automatically credited to your account, usually in less than two minutes.",

    Play_Level_Label_Msgs: [
	"Starter Table (Max. Raise WAGER)",
	"Intermediate Table (Max. Raise WAGER)",
	"High Roller Table (Max. Raise WAGER)"
    ],


// for the big dialog - these strings are concatonates
    welcome_msg0:
    "<p>BET & WIN ETHER</p>\
<p>Uncle Finney's Poker is a head-to-head, two player poker game. Unlike many other poker apps, \
Uncle Finney's Poker doesn't deal in fake coins that have no real value. Instead, players bet, win \
and lose Ether (ETH), an exciting cryptocurrency that has real world value.<br/><br/> \
Uncle Finney's Poker is NOT an online casino! You don't need to buy chips, open an account, or \
entrust a 3rd party with your money. Uncle Finney's Poker does not hold your bets or take a 'house cut'. \
Instead, bets are deposited, held and paid to the winner by an independent Ethereum smart-contract \
that charges a tiny escrow fee. What the heck is a smart contract? What the heck is Ethereum? What \
on Earth is a cryptocurrency? Check out<br/> \
<a target=_blank href=https://blog.coinbase.com/a-beginners-guide-to-ethereum-46dd486ceecf>The Beginer's Guide to Ethereum</a>",

    welcome_msg1:
    "<p>Is this Website Secure?</p>\
<p>This website runs the poker client code as a javascript program entirely within your browser. The javascript code is open-source \
and available for anyone to check out on \
<a target=_blank href='https://github.com/liveplayergames/UFP'>github</a> \
In addition, anyone can use their browser's 'view-source' button to validate the code. The poker client communicates with a backend \
server that manages the flow of the game between players. However, the private key for your Ethereum account is <b>NEVER</b> sent \
to the backend server. Your Ethereum private key is only used to sign your Ether transactions -- and these signed transactions \
are then broadcast to the Ethereum network. Your bets are deposited to an Ethereum smart-contract, which is a program that lives on \
the blockchain, securely holds players' bets and then automatically distributes them to the winner. The Uncle Finney's Poker backend \
server never has access to players' bets, making this a far more secure and trustworthy poker game than anything currently available.</p>",

    welcome_msg2:
"<p>Where Can I Learn More About Uncle Finney's Poker?</p> \
<p>Uncle Finney's Poker is also available as an Android app. You can read more about Uncle Finney's Poker at:<br/> \
<a target=_blank href=http://unclefinneys.com/ufp/about-and-faq/#faq>The Uncle Finney's Website<a/>",

    welcome_msg_title:
    "Welcome To Uncle Finney's Poker -- Online Edition",

    payout_advisory_title:
    "Where's My Money?",

    payout_advisory_msg0:
"<table>\
<tr><td>Starting balance:<td/>          <td>&nbsp;START_BAL  Finney<td/><tr/>\
<tr><td>Your bet(s):<td/>               <td>(TX_TOTAL) Finney<td/><tr/>\
<tr><td>Fees for TX_CNT bet(s):<td/>    <td>(TX_FEES)  Finney<td/><tr/>\
<tr><td><td/>                           <td>-----------------<td/><tr/>\
<tr><td>Interim balance:<td/>           <td>&nbsp;DEDUCTED_BAL Finney<td/><tr/>\
<tr><td>Winnings:<td/>                  <td>&nbsp;POT Finney<td/><tr/>\
<tr><td>Escrow fee:<td/>                <td>(ESCROW_FEE) Finney<td/><tr/>\
<tr><td>Final (estimated) balance:<td/> <td>&nbsp;FINAL_BAL Finney<td/><tr/>\
<table/><br/><br/>",

    payout_advisory_msg1:
"When your account is up-to-date with respect to all of the deductions listed above you can expect to see a balance of approximately \
DEDUCTED_BAL Finney (plus or minus one, due to rounding).\
<br/><br/>\
Your winnings will be credited within about two minutes <b>After</b> your account is up-to-date vis-a-vis deductions. At that time \
you will be awarded POT Finney minus a 2% escrow fee. So in about two minutes expect to see an account balance of approximately FINAL_BAL \
Finney (plus or minus one, due to rounding).\
<br/><br/>\
Note:\
<br/>\
After each game it may take a minute or so for your account balance to reflect any bets that you placed during the game. \
<i>After</i> your account is up-to-date, vis-a-vis any bets tha you placed, it may take an <i>additional</i> minute or two \
for the blockchain contract to award the pot to the winner.\
<br/><br/>\
<b>Be patient!</b>\
<br/><br/>"

}


/*
Starting<br/> \
balance:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;START_BAL Finney<br/>\
Your bet(s):&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(TX_TOTAL) Finney<br/>\
Fees for TX_CNT bet(s):&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(TX_FEES) Finney<br/>\
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;_______________<br/>\
Interim balance:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;DEDUCTED_BAL Finney<br/>\
Winnings:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;POT Finney<br/>\
Escrow fee:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;(ESCROW_FEE) Finney<br/>\
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;_______________<br/>\
Final (estimated) balance:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;FINAL_BAL Finney<br/>\
    */
