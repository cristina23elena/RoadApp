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

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View.OnTouchListener;


import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.*;
import com.google.maps.android.PolyUtil;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import android.os.Handler;
import android.os.Looper;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap gMap;
    private FusedLocationProviderClient fusedClient;
    private static final int REQUEST_CODE = 101;
    private Location currentLocation;
    private boolean cameraAlreadyMoved = false;
    private Marker userMarker;
    private EditText editStart, editDestination;
    private Button btnRoute, btnStart;
    private TextView textDistance, textDuration;
    private CardView cardInfo;
    private FloatingActionButton fabReport;
    private final List<Marker> reportMarkers = new ArrayList<>();
    private Marker startMarker;
    private LinearLayout cardInput;
    private TextView textDestFinal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

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
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float deltaY = e2.getY() - e1.getY();
                if (deltaY > 100) { // glisare în jos
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
            // Șterge markerul locației curente (dacă există)
            if (userMarker != null) {
                userMarker.remove();
                userMarker = null;
            }

            // Mișcă camera pe punctul de start dacă există
            if (startMarker != null) {
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(startMarker.getPosition())
                        .zoom(18f)
                        .bearing(currentLocation != null ? currentLocation.getBearing() : 0)
                        .tilt(60f)
                        .build();
                gMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }

            // Ascunde cardul cu inputuri cu animație în sus
            cardInput.animate()
                    .translationY(-cardInput.getHeight())
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> cardInput.setVisibility(View.GONE))
                    .start();

            // Afișează doar destinația într-un singur rând
            String destinatie = editDestination.getText().toString();
            textDestFinal.setText(destinatie);
            textDestFinal.setVisibility(View.VISIBLE);

            Toast.makeText(this, "Navigarea a început!", Toast.LENGTH_SHORT).show();
            fabReport.setVisibility(View.VISIBLE); // Afișează butonul de raportare
        });


        fabReport.setOnClickListener(v -> showReportDialog());
    }

    private void showReportDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_report_grid, null);
        GridLayout gridLayout = view.findViewById(R.id.grid_layout);

        // Tipurile de raport și iconițele corespunzătoare
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

                //  Șterge markerul roșu default (dacă e prezent)
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
            return;
        }
        gMap.setMyLocationEnabled(true);
        startLocationUpdates();
        loadReportsFromFirebase();
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000).build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        fusedClient.requestLocationUpdates(request, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                if (result.getLastLocation() != null) {
                    currentLocation = result.getLastLocation();
                    LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

                    if (userMarker != null) userMarker.remove();
                    userMarker = gMap.addMarker(new MarkerOptions().position(currentLatLng).title("Tu ești aici"));

                    if (!cameraAlreadyMoved) {
                        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 14));
                        cameraAlreadyMoved = true;
                    }
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

                        List<LatLng> points = PolyUtil.decode(polyline);
                        DatabaseReference segRef = FirebaseDatabase.getInstance().getReference("segmente_drum");
                        segRef.removeValue(); // opțional: șterge segmentele vechi pentru traseu nou

                        for (int i = 0; i < points.size() - 1; i++) {
                            LatLng start = points.get(i);
                            LatLng end = points.get(i + 1);

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

                            // Adaugă din nou marker-ele de raport
                            loadReportsFromFirebase();

                            // Desenează ruta principală
                            gMap.addPolyline(new PolylineOptions().addAll(points).color(0xFF6200EE).width(12));

                            Bitmap arrowIcon = BitmapFactory.decodeResource(getResources(), R.drawable.marker_arrow);
                            Bitmap resizedArrow = Bitmap.createScaledBitmap(arrowIcon, 90, 90, false);

                            startMarker = gMap.addMarker(new MarkerOptions()
                                    .position(origin)
                                    .title("Start")
                                    .icon(BitmapDescriptorFactory.fromBitmap(resizedArrow)));

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
                            .width(10));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MapsActivity.this, "Eroare la afișarea stării drumului", Toast.LENGTH_SHORT).show();
            }
        });
    }

}
