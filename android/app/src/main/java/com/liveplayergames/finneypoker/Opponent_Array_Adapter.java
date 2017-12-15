package com.liveplayergames.finneypoker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;


/**
 * Created by kaandoit on 12/11/16.
 */

class Opponent_Array_Adapter extends ArrayAdapter<Opponent_Info> {
    private Context context;
    private Opponent_Info values[];

    public Opponent_Array_Adapter(Context context, Opponent_Info values[]) {
        super(context, R.layout.opponent_list_item, values);
        this.context = context;
        this.values = values;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        String username = "";
        String level = "";
        if (values != null) {
            Opponent_Info opponent_info = values[position];
            username = opponent_info.username;
            level = opponent_info.level;
        }
        View row_view;
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row_view = inflater.inflate(R.layout.opponent_list_item, parent, false);
        } else {
            row_view = convertView;
        }
        TextView username_view = (TextView) row_view.findViewById(R.id.username);
        username_view.setText(username);
        TextView level_view = (TextView) row_view.findViewById(R.id.level);
        level_view.setText(level);
        return (row_view);
    }
}
