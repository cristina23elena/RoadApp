package com.example.roadapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;


import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.view.GestureDetector;
import android.view.MotionEvent;

import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.google.firebase.database.*;
import com.google.maps.android.PolyUtil;

// Places API
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AutoCompleteTextView;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap gMap;
    private FusedLocationProviderClient fusedClient;
    private static final int REQUEST_CODE = 101;
    private Location currentLocation;
    private boolean cameraAlreadyMoved = false;
    private Marker userMarker;
    private AutoCompleteTextView editStart, editDestination;
    private Button btnRoute, btnStart;
    private TextView textDistance, textDuration;
    private CardView cardInfo;
    private FloatingActionButton fabReport;
    private final List<Marker> reportMarkers = new ArrayList<>();
    private Marker startMarker;
    private LinearLayout cardInput;
    private TextView textDestFinal;
    private List<LatLng> currentRoutePoints = new ArrayList<>();
    private TextView textSpeed;
   // private FloatingActionButton fabSpeed;

    private View speedCard;
    private TextView speedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        speedCard = findViewById(R.id.speed_card);
        speedText = speedCard.findViewById(R.id.speed_text);
        TextView speedUnit = speedCard.findViewById(R.id.speed_unit);
        speedCard.setVisibility(View.GONE);
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), "AIzaSyCw3d4KYLtAUYRF1NOpfoF6fa6hksclw2s");
        }
        AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();

        editStart = findViewById(R.id.edit_start);
        editDestination = findViewById(R.id.edit_destination);
        btnRoute = findViewById(R.id.btn_route);
        btnStart = findViewById(R.id.btn_start);
        textDistance = findViewById(R.id.text_distance);
        textDuration = findViewById(R.id.text_duration);
        cardInfo = findViewById(R.id.card_info);
        fabReport = findViewById(R.id.fab_report);



        cardInput = findViewById(R.id.card_input);
        textDestFinal = findViewById(R.id.text_dest_final);



        editStart.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().isEmpty()) {
                    findPlacePredictions(s.toString(), token, editStart);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        editDestination.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().isEmpty()) {
                    findPlacePredictions(s.toString(), token, editDestination);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float deltaY = e2.getY() - e1.getY();
                if (deltaY > 100) {
                    cardInput.setVisibility(View.VISIBLE);
                    cardInput.setTranslationY(-cardInput.getHeight());
                    cardInput.animate().translationY(0f).alpha(1f).setDuration(500).start();
                    textDestFinal.setVisibility(View.GONE);
                    return true;
                }
                return false;
            }
        });

        textDestFinal.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        fabReport.setVisibility(View.GONE);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        btnRoute.setOnClickListener(v -> {
            String start = editStart.getText().toString();
            String dest = editDestination.getText().toString();



            if (dest.isEmpty()) {
                Toast.makeText(this, "Completează destinația", Toast.LENGTH_SHORT).show();
                return;
            }

            if (start.isEmpty()) {
                if (currentLocation != null) {
                    LatLng origin = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                    drawRouteFromCoordinates(origin, dest);
                } else {
                    Toast.makeText(this, "Locația curentă nu este disponibilă", Toast.LENGTH_SHORT).show();
                }
            } else {
                drawRoute(start, dest);
            }
        });

        btnStart.setOnClickListener(v -> {
            if (userMarker != null) {
                userMarker.remove();
                userMarker = null;
            }

            // Zoom pe întreaga rută
            if (currentRoutePoints != null && !currentRoutePoints.isEmpty()) {
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (LatLng point : currentRoutePoints) {
                    builder.include(point);
                }
                LatLngBounds bounds = builder.build();
                gMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            }

            cardInput.animate()
                    .translationY(-cardInput.getHeight())
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> cardInput.setVisibility(View.GONE))
                    .start();

            textDestFinal.setText(editDestination.getText().toString());
            textDestFinal.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Navigarea a început!", Toast.LENGTH_SHORT).show();
            fabReport.setVisibility(View.VISIBLE);
           // fabSpeed.setVisibility(View.VISIBLE);
            speedCard .setVisibility(View.VISIBLE);
        });

        fabReport.setOnClickListener(v -> showReportDialog());

    }



    //  Metoda pentru sugestii autocomplete Places API
    private void findPlacePredictions(String query, AutocompleteSessionToken token, AutoCompleteTextView targetEditText) {
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(token)
                .setQuery(query)
                .build();

        PlacesClient placesClient = Places.createClient(this);
        placesClient.findAutocompletePredictions(request).addOnSuccessListener(response -> {
            List<String> suggestions = new ArrayList<>();
            for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                suggestions.add(prediction.getFullText(null).toString());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(MapsActivity.this,
                    android.R.layout.simple_dropdown_item_1line, suggestions);

            targetEditText.setAdapter(adapter);
            targetEditText.setThreshold(1);
            targetEditText.showDropDown();
        }).addOnFailureListener(e -> Log.e("PLACES_API", "Eroare sugestii: ", e));
    }

    // restul metodelor tale rămân neschimbate...
    // (onMapReady, startLocationUpdates, showReportDialog, sendReport, drawRoute, etc.)



private void showReportDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_report_grid, null);
        GridLayout gridLayout = view.findViewById(R.id.grid_layout);

        // Tipurile de raport si iconitele corespunzatoare
        String[] tipuri = {
                "Groapă",
                "Drum în lucru",
                "Poliție",
                "Accident",
                "Blocaj",
                "Denivelare",
                "Inundație",
                "Capac Lipsa"

        };

        int[] icons = {
                R.drawable.ic_pit,           // Groapă
                R.drawable.road_block,       // Drum în lucru
                R.drawable.police_car,       // Poliție
                R.drawable.car_crash,        // Accident
                R.drawable.warning_icon,     // Blocaj
                R.drawable.ic_bump,          // Denivelare
                R.drawable.ic_flood,         // Inundație
                R.drawable.ic_missing_lid    // Capac lipsă
        };

        for (int i = 0; i < tipuri.length; i++) {
            View item = getLayoutInflater().inflate(R.layout.report_grid_item, null);
            ImageView icon = item.findViewById(R.id.icon);
            TextView label = item.findViewById(R.id.label);

            icon.setImageResource(icons[i]);
            label.setText(tipuri[i]);

            int finalI = i;
            item.setOnClickListener(v -> {
                dialog.dismiss();
                sendReport(tipuri[finalI]);
            });

            gridLayout.addView(item);
        }

        dialog.setContentView(view);
        dialog.show();
    }

    private void sendReport(String tipRaport) {
        if (currentLocation == null) {
            Toast.makeText(this, "Locația nu este disponibilă", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("raportari");
        String id = dbRef.push().getKey();
        if (id != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("tip", tipRaport);
            data.put("lat", currentLocation.getLatitude());
            data.put("lng", currentLocation.getLongitude());
            data.put("timestamp", System.currentTimeMillis());

            dbRef.child(id).setValue(data).addOnSuccessListener(unused -> {
                LatLng locatie = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                int iconResId = getIconForReport(tipRaport);
                if (iconResId != 0) {
                    Bitmap icon = BitmapFactory.decodeResource(getResources(), iconResId);
                    Bitmap resizedIcon = Bitmap.createScaledBitmap(icon, 150, 150, false);

                    Marker reportMarker = gMap.addMarker(new MarkerOptions()
                            .position(locatie)
                            .title(tipRaport)
                            .icon(BitmapDescriptorFactory.fromBitmap(resizedIcon)));

                    reportMarkers.add(reportMarker);
                }

                //  sterge markerul rosu default (daca e prezent)
                if (userMarker != null) {
                    userMarker.remove();
                    userMarker = null;
                }

                Toast.makeText(this, "Raport trimis: " + tipRaport, Toast.LENGTH_SHORT).show();
            });
        }
    }


    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        gMap = googleMap;
        gMap.setBuildingsEnabled(false);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
            return;
        }
        gMap.setMyLocationEnabled(false);
        gMap.setBuildingsEnabled(false);
        startLocationUpdates();
        loadReportsFromFirebase();
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Bitmap arrowBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.marker_arrow);
        Bitmap resizedArrow = Bitmap.createScaledBitmap(arrowBitmap, 90, 90, false);

        fusedClient.requestLocationUpdates(request, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                if (result.getLastLocation() != null) {
                    currentLocation = result.getLastLocation();
                    float speedMps = currentLocation.getSpeed(); // în m/s
                    float speedKph = speedMps * 3.6f; // conversie în km/h

// rotunjim viteza
                    int roundedSpeed = Math.round(speedKph);

                    if (speedCard.getVisibility() == View.VISIBLE) {
                        speedText.setText(String.valueOf(roundedSpeed));
                    }



                    LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

                    runOnUiThread(() -> {
                        if (userMarker == null) {
                            userMarker = gMap.addMarker(new MarkerOptions()
                                    .position(currentLatLng)
                                    .anchor(0.5f, 0.5f)
                                    .flat(true)
                                    .icon(BitmapDescriptorFactory.fromBitmap(resizedArrow))
                                    .rotation(currentLocation.getBearing()));
                        } else {
                            userMarker.setPosition(currentLatLng);
                            userMarker.setRotation(currentLocation.getBearing());
                        }

                        CameraPosition cameraPosition = new CameraPosition.Builder()
                                .target(currentLatLng)
                                .zoom(18f)
                                .bearing(currentLocation.getBearing())
                                .tilt(0f) // setare 2D
                                .build();
                        gMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                    });
                }
            }
        }, getMainLooper());
    }



    private void loadReportsFromFirebase() {
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("raportari");

        for (Marker marker : reportMarkers) {
            marker.remove();
        }
        reportMarkers.clear();

        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                    try {
                        Double lat = reportSnapshot.child("lat").getValue(Double.class);
                        Double lng = reportSnapshot.child("lng").getValue(Double.class);
                        String tip = reportSnapshot.child("tip").getValue(String.class);

                        if (lat == null || lng == null || tip == null) continue;

                        LatLng locatie = new LatLng(lat, lng);
                        int iconResId = getIconForReport(tip);
                        if (iconResId == 0) continue;

                        Bitmap icon = BitmapFactory.decodeResource(getResources(), iconResId);
                        Bitmap resizedIcon = Bitmap.createScaledBitmap(icon, 150, 150, false);

                        Marker marker = gMap.addMarker(new MarkerOptions()
                                .position(locatie)
                                .title(tip)
                                .icon(BitmapDescriptorFactory.fromBitmap(resizedIcon)));

                        reportMarkers.add(marker);

                    } catch (Exception e) {
                        Log.e("FirebaseMarker", "Eroare: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MapsActivity.this, "Eroare Firebase", Toast.LENGTH_SHORT).show();
            }
        });
    }




    private int getIconForReport(String tip) {
        if (tip == null) return R.drawable.marker_arrow;

        tip = tip.toLowerCase(Locale.ROOT)
                .replace("ă", "a")
                .replace("â", "a")
                .replace("î", "i")
                .replace("ș", "s")
                .replace("ş", "s")
                .replace("ț", "t")
                .replace("ţ", "t");

        if (tip.contains("groapa")) return R.drawable.ic_pit;
        if (tip.contains("drum") && tip.contains("lucru")) return R.drawable.road_block;
        if (tip.contains("politie")) return R.drawable.police_car;
        if (tip.contains("accident")) return R.drawable.car_crash;
        if (tip.contains("blocaj")) return R.drawable.warning_icon;
        if (tip.contains("denivelare")) return R.drawable.ic_bump;
        if (tip.contains("inundatie")) return R.drawable.ic_flood;
        if (tip.contains("capac")) return R.drawable.ic_missing_lid;

        return R.drawable.marker_arrow;
    }

    private void drawRoute(String originName, String destinationName) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> originList = geocoder.getFromLocationName(originName, 1);
            if (originList.isEmpty()) {
                Toast.makeText(this, "Locație de plecare invalidă", Toast.LENGTH_SHORT).show();
                return;
            }
            LatLng origin = new LatLng(originList.get(0).getLatitude(), originList.get(0).getLongitude());
            drawRouteFromCoordinates(origin, destinationName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawRouteFromCoordinates(LatLng origin, String destinationName) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> destList = geocoder.getFromLocationName(destinationName, 1);
            if (destList.isEmpty()) {
                Toast.makeText(this, "Destinație invalidă", Toast.LENGTH_SHORT).show();
                return;
            }

            LatLng destination = new LatLng(destList.get(0).getLatitude(), destList.get(0).getLongitude());

            String url = "https://maps.googleapis.com/maps/api/directions/json?origin="
                    + origin.latitude + "," + origin.longitude +
                    "&destination=" + destination.latitude + "," + destination.longitude +
                    "&key=AIzaSyCw3d4KYLtAUYRF1NOpfoF6fa6hksclw2s";

            new Thread(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("GET");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) json.append(line);
                    reader.close();

                    JSONObject response = new JSONObject(json.toString());
                    JSONArray routes = response.getJSONArray("routes");
                    if (routes.length() > 0) {
                        JSONObject route = routes.getJSONObject(0);
                        JSONObject leg = route.getJSONArray("legs").getJSONObject(0);

                        String distance = leg.getJSONObject("distance").getString("text");
                        String duration = leg.getJSONObject("duration").getString("text");
                        String polyline = route.getJSONObject("overview_polyline").getString("points");

                        currentRoutePoints = PolyUtil.decode(polyline);

                        DatabaseReference segRef = FirebaseDatabase.getInstance().getReference("segmente_drum");
                        segRef.removeValue(); // opțional: șterge segmentele vechi pentru traseu nou

                        for (int i = 0; i < currentRoutePoints.size() - 1; i++) {
                            LatLng start = currentRoutePoints.get(i);
                            LatLng end = currentRoutePoints.get(i + 1);


                            Map<String, Object> segment = new HashMap<>();
                            segment.put("startLat", start.latitude);
                            segment.put("startLng", start.longitude);
                            segment.put("endLat", end.latitude);
                            segment.put("endLng", end.longitude);
                            segment.put("scor", 100); // scor inițial

                            segRef.push().setValue(segment);
                        }



                        runOnUiThread(() -> {
                            gMap.clear();

                            // Adauga din nou marker-ele de raport
                            loadReportsFromFirebase();

                            // Deseneaza ruta principala
                            gMap.addPolyline(new PolylineOptions().addAll(currentRoutePoints).color(0xFF6200EE).width(12));


                            Bitmap arrowIcon = BitmapFactory.decodeResource(getResources(), R.drawable.marker_arrow);
                            Bitmap resizedArrow = Bitmap.createScaledBitmap(arrowIcon, 90, 90, false);


                            gMap.addMarker(new MarkerOptions().position(destination).title("Destinație"));

                            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 13));

                            textDistance.setText(distance);
                            textDuration.setText(duration);
                            cardInfo.setVisibility(View.VISIBLE);
                            updateScoruriSegment();
                            new Handler(Looper.getMainLooper()).postDelayed(() -> drawSegmentsOnMap(), 2000);
                        });

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() ->
                            Toast.makeText(MapsActivity.this, "Eroare la încărcarea traseului", Toast.LENGTH_SHORT).show()
                    );
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Eroare la geocodare", Toast.LENGTH_SHORT).show();
        }
    }
    private String normalizeTip(String tip) {
        if (tip == null) return "";
        return tip.toLowerCase(Locale.ROOT)
                .replace("ă", "a")
                .replace("â", "a")
                .replace("î", "i")
                .replace("ș", "s")
                .replace("ş", "s")
                .replace("ț", "t")
                .replace("ţ", "t");
    }

    private void updateScoruriSegment() {
        DatabaseReference raportariRef = FirebaseDatabase.getInstance().getReference("raportari");
        DatabaseReference segmenteRef = FirebaseDatabase.getInstance().getReference("segmente_drum");

        raportariRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot raportSnapshot) {
                List<DataSnapshot> raportari = new ArrayList<>();
                for (DataSnapshot r : raportSnapshot.getChildren()) {
                    raportari.add(r);
                }

                segmenteRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot segmentSnapshot) {
                        int total = (int) segmentSnapshot.getChildrenCount();
                        final int[] processed = {0};

                        for (DataSnapshot segment : segmentSnapshot.getChildren()) {
                            double startLat = segment.child("startLat").getValue(Double.class);
                            double startLng = segment.child("startLng").getValue(Double.class);
                            double endLat = segment.child("endLat").getValue(Double.class);
                            double endLng = segment.child("endLng").getValue(Double.class);

                            LatLng start = new LatLng(startLat, startLng);
                            LatLng end = new LatLng(endLat, endLng);

                            int scor = 100;
                            long timpActual = System.currentTimeMillis();
                            long sapteZile = 7 * 24 * 60 * 60 * 1000L;

                            for (DataSnapshot r : raportari) {
                                String tipRaw = r.child("tip").getValue(String.class);
                                if (tipRaw == null) continue;

                                String tip = normalizeTip(tipRaw);
                                Double lat = r.child("lat").getValue(Double.class);
                                Double lng = r.child("lng").getValue(Double.class);
                                Long timestamp = r.child("timestamp").getValue(Long.class);

                                if (lat == null || lng == null || timestamp == null) continue;
                                if ((timpActual - timestamp) > sapteZile) continue;

                                if (tip.contains("groapa") || tip.contains("lucru") || tip.contains("denivelare")
                                        || tip.contains("inundatie") || tip.contains("capac")) {

                                    double dist = distanceToSegment(start, end, new LatLng(lat, lng));
                                    if (dist < 100) scor -= 10;
                                }
                            }

                            segment.getRef().child("scor").setValue(Math.max(scor, 0)).addOnCompleteListener(task -> {
                                processed[0]++;
                                if (processed[0] == total) {
                                    runOnUiThread(() -> drawSegmentsOnMap());
                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public double distanceToSegment(LatLng A, LatLng B, LatLng P) {
        double ax = A.latitude;
        double ay = A.longitude;
        double bx = B.latitude;
        double by = B.longitude;
        double px = P.latitude;
        double py = P.longitude;

        double dx = bx - ax;
        double dy = by - ay;
        if (dx == 0 && dy == 0) {
            dx = px - ax;
            dy = py - ay;
            return Math.sqrt(dx * dx + dy * dy) * 111000;
        }

        double t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));

        double closestX = ax + t * dx;
        double closestY = ay + t * dy;

        dx = px - closestX;
        dy = py - closestY;

        return Math.sqrt(dx * dx + dy * dy) * 111000;
    }
    private void drawSegmentsOnMap() {
        DatabaseReference segRef = FirebaseDatabase.getInstance().getReference("segmente_drum");

        segRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot segment : snapshot.getChildren()) {
                    double startLat = segment.child("startLat").getValue(Double.class);
                    double startLng = segment.child("startLng").getValue(Double.class);
                    double endLat = segment.child("endLat").getValue(Double.class);
                    double endLng = segment.child("endLng").getValue(Double.class);
                    int scor = segment.child("scor").getValue(Integer.class);

                    LatLng start = new LatLng(startLat, startLng);
                    LatLng end = new LatLng(endLat, endLng);

                    int color;
                    if (scor >= 80) {
                        color = 0xFF4CAF50; // verde
                    } else if (scor >= 50) {
                        color = 0xFFFFC107; // galben
                    } else {
                        color = 0xFFF44336; // roșu
                    }

                    gMap.addPolyline(new PolylineOptions()
                            .add(start, end)
                            .color(color)
                            .width(38)
                            .startCap(new RoundCap())
                            .endCap(new RoundCap())
                            .zIndex(20));

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MapsActivity.this, "Eroare la afișarea stării drumului", Toast.LENGTH_SHORT).show();
            }
        });

    }





}