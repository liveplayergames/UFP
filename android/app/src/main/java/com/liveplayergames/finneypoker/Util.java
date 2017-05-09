    package com.liveplayergames.finneypoker;

    import android.app.ActivityManager;
    import android.content.Context;
    import android.content.DialogInterface;
    import android.os.AsyncTask;
    import android.os.CountDownTimer;
    import android.support.v7.app.AlertDialog;
    import android.support.v7.app.AppCompatActivity;
    import android.util.Base64;
    import android.widget.Toast;

    import org.apache.commons.codec.DecoderException;
    import org.apache.commons.codec.binary.Hex;
    import org.spongycastle.asn1.eac.UnsignedInteger;

    import java.io.BufferedInputStream;
    import java.io.BufferedReader;
    import java.io.IOException;
    import java.io.InputStream;
    import java.io.InputStreamReader;
    import java.net.HttpURLConnection;
    import java.net.MalformedURLException;
    import java.net.URISyntaxException;
    import java.net.URL;
    import java.nio.charset.Charset;
    import java.security.NoSuchAlgorithmException;
    import java.util.regex.Matcher;
    import java.util.regex.Pattern;

    import javax.crypto.Cipher;
    import javax.crypto.KeyGenerator;
    import javax.crypto.spec.IvParameterSpec;
    import javax.crypto.spec.SecretKeySpec;

    import io.socket.client.IO;
    import io.socket.client.Socket;

    import static android.content.Context.ACTIVITY_SERVICE;

    /**
     * Created by dbrosen on 11/25/16.
     */

    public class Util {


        public static final long WEI_PER_SZABO = 1000000000000L;
        public static final long WEI_PER_FINNEY = 1000000000000000L;
        public static final long WEI_PER_ETH = 1000000000000000000L;
        public static final long SZABO_PER_FINNEY = 1000L;
        public static final long SZABO_PER_ETH = 1000000L;
        public static final long DEFAULT_GAS_LIMIT = 35000;
        public static final long DEFAULT_GAS_PRICE = 21000000000L;

        private static Socket socket = null;
        private static Charset UTF8_CHARSET = Charset.forName("UTF-8");




        public static String aes_encrypt(String data, String initVector, String key) {
            try {
                byte iv_bytes[] = hex_string_to_byte_array(initVector);
                IvParameterSpec iv = new IvParameterSpec(iv_bytes);
                //Cipher c = Cipher.getInstance("AES/CBC/PKCS7PADDING");
                Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
                byte key_bytes[] = hex_string_to_byte_array(key);
                SecretKeySpec k = new SecretKeySpec(key_bytes, "AES");
                c.init(Cipher.ENCRYPT_MODE, k, iv);
                byte data_utf8[] = data.getBytes("UTF-8");
                byte data_bytes[] = c.doFinal(data_utf8);
                String hex_enc = new String(Hex.encodeHex(data_bytes));
                return hex_enc;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        //data, iv, key are all hex strings
        public static String aes_decrypt(String data, String initVector, String key) {
            try {
                byte iv_bytes[] =  hex_string_to_byte_array(initVector);
                //for (int i = 0; i < iv_bytes.length; ++i)
                //    System.out.println("iv[" + i + "]: " + String.format("%02X ", iv_bytes[i]));
                IvParameterSpec iv = new IvParameterSpec(iv_bytes);
                Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
                //SecretKeySpec k = new SecretKeySpec(Base64.decode(key, Base64.DEFAULT), "AES");
                byte key_bytes[] = hex_string_to_byte_array(key);
                SecretKeySpec k = new SecretKeySpec(key_bytes, "AES");
                c.init(Cipher.DECRYPT_MODE, k, iv);
                byte data_bytes[] = hex_string_to_byte_array(data);
                byte unenc_bytes[] = c.doFinal(data_bytes);
                String msg = new String(unenc_bytes, UTF8_CHARSET);
                return(msg);
            } catch (Exception e) {
                e.printStackTrace();
                return("");
            }
        }

        //key is returned as ascii hex
        public static String aes_key_generator() {
            try {
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(192);
                //String key = Base64.encodeToString(keyGenerator.generateKey().getEncoded(), Base64.DEFAULT);
                String key = new String(Hex.encodeHex(keyGenerator.generateKey().getEncoded()));
                return key;

            } catch (NoSuchAlgorithmException e) {
                System.out.println("aes_key_generator - NoSuchAlgorithmException Exception: " + e.toString());
                return("");
            }
        }

        static public byte[] hex_string_to_byte_array(String s) {
            /*
            try {
                return Hex.decodeHex(s.toCharArray());
            } catch (DecoderException e) {
                System.out.println("hex_string_to_byte_array - Decoder Exception: " + e.toString());
                return(new byte[0]);
            }
            */
            int len = s.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len - 1; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
            }
            return data;
        }


        static public void show_err(Context context, String msg, int seconds) {
            //toast LENGTH_LONG is 3.5 secs. (and LENGTH_SHORT is 2.5) we need to display this about 10 secs
            final Toast tag = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
            tag.show();
            new CountDownTimer(1000 * seconds, 1000) {
                public void onTick(long millisUntilFinished) { tag.show(); }
                public void onFinish()                       { tag.show(); }
            }.start();
        }

        //WARNING!!
        //this is a very cheesy, hacky parser!
        //you can't have several keywards with partial matches. for example:
        // { keyword: 7, key: 6 }
        // if you try to get the value for key, you'll get 7!!
        //BEWARE!!
      static public String json_parse(String json, String field) {
        String value = "";
        boolean done = false;
        int idx = 0;
        try {
            final Pattern pattern = Pattern.compile("[,}]");
            do {
                int quote_idx = json.indexOf('"', idx);
                int field_idx = json.indexOf(field, idx);
                if (quote_idx >= 0 && quote_idx < field_idx) {
                    int match_quote_idx = json.indexOf('"', quote_idx + 1);
                    if (match_quote_idx >= 0) {
                        String quoted_field = json.substring(quote_idx, match_quote_idx).replace("\"", "").trim();
                        if (!quoted_field.equals(field)) {
                            idx = match_quote_idx + 1;
                            continue;
                        }
                    } else {
                        System.out.println("unmatched quotes! json: " + json);
                        System.out.println("field: " + field);
                        System.out.println("field_idx = " + field_idx + ", quote_idx = " + quote_idx);
                    }
                }
                //System.out.println("field_idx = " + field_idx);
                done = true;
                if (field_idx >= 0) {
                    field_idx += field.length();
                    int beg_idx = json.indexOf(':', field_idx) + 1;
                    //System.out.println("beg = " + beg_idx);
                    Matcher matcher = pattern.matcher(json.substring(beg_idx));
                    if (matcher.find()) {
                        quote_idx = json.indexOf('"', beg_idx);
                        int end_idx = beg_idx + matcher.start();
                        if (quote_idx >= 0 && quote_idx < end_idx) {
                            //found a quote somewhere in the string. we stipulate that it must be at the beginning of the string
                            int match_quote_idx = json.indexOf('"', quote_idx + 1);
                            if (match_quote_idx >= 0) {
                                beg_idx = quote_idx + 1;
                                end_idx = match_quote_idx;
                                //System.out.println("quoted string: " + json.substring(beg_idx, end_idx));
                            } else {
                                System.out.println("unmatched quotes! json: " + json);
                                System.out.println("field: " + field);
                                System.out.println("field_idx = " + field_idx + ", beg_idx = " + beg_idx + ", quote_idx = " + quote_idx);
                            }
                        }
                        //System.out.println("end = " + end_idx);
                        value = json.substring(beg_idx, end_idx).replace("\"", "").trim();
                        //System.out.println("value = " + value);
                    } else {
                        System.out.println("malformed json:" + json);
                    }
                }
            } while (!done);
        } catch (Exception e) {
            System.out.println(e.toString());
            e.printStackTrace();
        }
        return(value);
      }

      static public long get_avail_memory_kb(Context context) {
          ActivityManager activity_manager = (ActivityManager)context.getSystemService(ACTIVITY_SERVICE);
          ActivityManager.MemoryInfo mem_info = new ActivityManager.MemoryInfo();
          activity_manager.getMemoryInfo(mem_info);
          long kb_avail = mem_info.availMem / 1000;
          System.out.println("memmory: " + kb_avail + " kB");
          return kb_avail;
      }


    /*
      static public long balance_wei_from_json(Context context, String rsp) {
        //typical response is:
            //{
            // "status": 1,
            // "data": [
            //  {
            //   "address": "0x7223efbf783eba259451a89e8e84c26611df8c4f",
            //   "balance": 40038159108626850000,
            //   "nonce": null,
            //   "code": "0x",
            //   "name": null,
            //   "storage": null,
            //   "firstSeen": null
            //  }
            // ]
            //}
        long balance = -1;
        boolean got_balance = false;
        String balance_str = Util.json_parse(rsp, "balance");
        if (!balance_str.isEmpty() && !balance_str.equals("null")) {
          //System.out.println(rsp);
          balance = Long.valueOf(balance_str);
          got_balance = true;
        } else if (rsp.contains("status")) {
          String status_str = Util.json_parse(rsp, "status");
          if (status_str.equals("1")) {
            //no error, but no balance data.... the account has never been used
            balance = 0;
            got_balance = true;
          }
        }
        if (!got_balance) {
          Util.show_err(context, "error retrieving balance!", 3);
          Util.show_err(context, rsp, 10);
        }
        //Toast.makeText(context, "balance = " + balance, Toast.LENGTH_LONG).show();
        return(balance);
      }
*/

        //functions to get the socket, which can be shared across activities
        static public Socket get_player_socket(AppCompatActivity context) {
            if (socket != null)
                return socket;
            String server = context.getResources().getString(R.string.player_server);
            try {
                socket = IO.socket(server);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            if (socket != null)
                socket.connect();
            return socket;
        }
        static public Socket release_player_socket() {
            if (socket != null) {
                socket.close();
                socket = null;
            }
            return socket;
        }
        static public Socket get_old_player_socket(AppCompatActivity context) {
            return socket;
        }
        static public Socket get_new_player_socket(AppCompatActivity context) {
            release_player_socket();
            return(get_player_socket(context));
        }

    }
