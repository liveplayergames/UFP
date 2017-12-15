package com.liveplayergames.finneypoker;

import java.net.InetAddress;

/**
 * Created by kaandoit on 12/27/16.
 */

public class DNS_Entry {
    InetAddress addr;
    long timestamp;
    public DNS_Entry(InetAddress addr, long timestamp) {
            this.addr = addr;
            this.timestamp = timestamp;
    }
}
