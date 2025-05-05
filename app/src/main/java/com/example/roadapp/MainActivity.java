package com.example.roadapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // DIRECT trimitem spre MapsActivity
        Intent intent = new Intent(MainActivity.this, MapsActivity.class);
        startActivity(intent);
        finish(); // ca să nu mai rămână MainActivity în memorie
    }
}
