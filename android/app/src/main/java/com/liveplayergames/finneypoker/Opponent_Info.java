package com.liveplayergames.finneypoker;

/**
 * Created by kaandoit on 12/11/16.
 */

class Opponent_Info {
    public String username;
    public String level;
    public String id;
    Opponent_Info(String id, String username, String level) {
        this.id = id;
        this.username = username;
        this.level = level;
    }
}
