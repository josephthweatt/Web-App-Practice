package com.example.joseph.bigmap;

import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ChannelActivity extends AppCompatActivity{
    Boolean broadcasting;
    Button broadcastButton;

    int channelId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.channel_activity);

        if (broadcasting == null) {
            broadcasting = false;
        }

        channelId = getIntent().getIntExtra("channelId", 0);
        String header = "Channel " + channelId;
        ((TextView) findViewById(R.id.channel_header)).setText(header);

        broadcastButton = (Button) findViewById(R.id.channel_button);
        setButtonState(broadcasting);
        broadcastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // change current state of broadcasting
                setButtonState(!broadcasting);
            }
        });
    }

    public void setButtonState(Boolean selectedState) {
        if (selectedState) { // user is broadcasting
            broadcastButton.setBackgroundColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.broadcasting));
            broadcastButton.setText("Broadcasting");
        } else { // user not broadcasting
            broadcastButton.setBackgroundColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.notBroadcasting));
            broadcastButton.setText("Click to broadcast");
        }
        broadcasting = selectedState;
    }
}
