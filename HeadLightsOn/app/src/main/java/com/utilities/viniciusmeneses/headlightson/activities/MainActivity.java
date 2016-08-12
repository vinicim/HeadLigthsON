package com.utilities.viniciusmeneses.headlightson.activities;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.utilities.viniciusmeneses.headlightson.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int MIN_TIME_UPDATE_INTERVAL = 60000;

    private static final String locationProvider = LocationManager.NETWORK_PROVIDER;
    private LocationManager locationManager;
    private TextView tvRoadName, tvTurnOnOff;
    private double lastLatitude, lastLongitude;
    private MapFragment mapFragment;
    private GoogleMap myMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        tvRoadName = (TextView) findViewById(R.id.tvRoadName);
        tvTurnOnOff = (TextView) findViewById(R.id.tvTurnOnOff);

        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    @Override
    protected void onResume() {

        super.onResume();

        LocationListener locationListener = new LocationListener() {

            public void onLocationChanged(Location location) {

                lastLatitude = location.getLatitude();
                lastLongitude = location.getLongitude();
                myMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLatitude, lastLongitude), 12.0f));

                requestAddressData();

            }

            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            public void onProviderEnabled(String provider) {

            }

            public void onProviderDisabled(String provider) {

            }
        };


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(locationProvider, MIN_TIME_UPDATE_INTERVAL, 0, locationListener);
        Location lastKnownLocation = locationManager.getLastKnownLocation(locationProvider);

        lastLatitude = lastKnownLocation.getLatitude();
        lastLongitude = lastKnownLocation.getLongitude();

        requestAddressData();

    }

    private void requestAddressData() {
        if (thereIsInternetConn()) {
            String urlGeocodeApi = generateUrlGeocodeApi(lastLatitude, lastLongitude);
            new DownloadWebpageTask().execute(urlGeocodeApi);
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {

        myMap = map;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
           return;

        myMap.setMyLocationEnabled(true);

    }

    public String generateUrlGeocodeApi(double latitude, double longitude){

        StringBuilder urlReqGeocodeApi = new StringBuilder();

        urlReqGeocodeApi.append(getResources().getString(R.string.geocode_api_url_base));
        urlReqGeocodeApi.append(getResources().getString(R.string.geocode_api_path));

        urlReqGeocodeApi.append(getResources().getString(R.string.lat_long_params_label));
        urlReqGeocodeApi.append(latitude);
        urlReqGeocodeApi.append(",");
        urlReqGeocodeApi.append(longitude);

        urlReqGeocodeApi.append(getResources().getString(R.string.geocode_api_key_label));
        urlReqGeocodeApi.append(getResources().getString(R.string.geocode_api_key));

        return urlReqGeocodeApi.toString();
    }

    private class DownloadWebpageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {

            String url = params[0];

            try {
                return downloadUrl(url);
            } catch (IOException e) {
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            System.out.println("Result: "+result);
            try{
                String route = getRoute(result);
                turnOnHeadLights(route);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        private String downloadUrl(String myurl) throws IOException {

            InputStream is = null;

           try {
                URL url = new URL(myurl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);

                conn.connect();
                int response = conn.getResponseCode();
                is = conn.getInputStream();

                String contentAsString = readIt(is);
                return contentAsString;

            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }

        private String readIt(InputStream inputStream) throws IOException{

            String inputLine;
            StringBuilder conteudo = new StringBuilder();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            while ((inputLine = bufferedReader.readLine()) != null)
                conteudo.append(inputLine);

            return conteudo.toString();
        }

        private String getRoute(String result) throws JSONException {

            String roadName = null;
            String roadShortName = null;

            JSONObject jsonRoot = new JSONObject(result);
            JSONArray jsonResults = jsonRoot.getJSONArray("results");
            JSONObject firstObjectResult = jsonResults.getJSONObject(0);
            JSONArray addressComponents = firstObjectResult.getJSONArray("address_components");

            for(int i = 0 ; i < addressComponents.length(); i++){

                JSONObject jsonObject = addressComponents.getJSONObject(i);
                JSONArray addressTypes = jsonObject.getJSONArray("types");

                if(addressTypes.getString(0).equals("route")){
                    roadName = jsonObject.getString("long_name");
                    roadShortName = jsonObject.getString("short_name");
                }

            }
            String currentRoad =  roadName+" ("+roadShortName+")";
            tvRoadName.setText(currentRoad);
            return roadShortName;
        }

        private void turnOnHeadLights(String currentRoadShortName) {

            String[] brazilianHighways = getResources().getStringArray(R.array.highway_names);

            for(String brazilianHighway : brazilianHighways){
                if(brazilianHighway.contains(currentRoadShortName))
                    tvTurnOnOff.setText("TURN YOUR HEADLIGHTS ON!!");
            }
        }
    }

    public Boolean thereIsInternetConn(){

        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;

    }
}
