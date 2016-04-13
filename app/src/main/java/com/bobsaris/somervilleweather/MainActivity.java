package com.bobsaris.somervilleweather;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Xml;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
  @Override
  protected void onCreate( Bundle savedInstanceState ) {
    super.onCreate( savedInstanceState );
    setContentView( R.layout.activity_main );

    WeatherTask _weatherTask = new WeatherTask();
    _weatherTask.execute();
  }

  private class WeatherTask extends AsyncTask<Void, Integer, String> {
    private String SOMERVILLE_ZIP = "02143";
    private String SOMERVILLE_LAT = "42.39";
    private String SOMERVILLE_LON = "-71.15";

    // private String NOAA_BASE_URL = "http://graphical.weather.gov/xml/sample_products/browser_interface/ndfdBrowserClientByDay.php";
    private String NOAA_BASE_URL = "http://forecast.weather.gov/MapClick.php";

    protected String doInBackground( Void... activity ) {
      try {
        URL url = new URL(
          NOAA_BASE_URL +
            "?lat=" + SOMERVILLE_LAT +
            "&lon=" + SOMERVILLE_LON +
            "&FcstType=json"
        );

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
          BufferedReader reader = new BufferedReader( new InputStreamReader( urlConnection.getInputStream() ) );
          StringBuilder sb = new StringBuilder();
          String line = null;
          while( (line = reader.readLine()) != null ) {
            sb.append( line );
            sb.append( "\n" );
          }
          return sb.toString();
        } finally {
          urlConnection.disconnect();
        }
      } catch( MalformedURLException mue ) {
        Log.e( getResources().getString( R.string.exception_tag ), "Malformed URL found when grabbing weather.", mue );
      } catch( IOException ioe ) {
        Log.e( getResources().getString( R.string.exception_tag ), "Caught IOException when reading weather response.", ioe );
      }

      return "There was an error.";
    }

    protected void onPostExecute( String result ) {
      StringBuilder sb = new StringBuilder();

      try {
        JSONObject response = new JSONObject( result );
        JSONObject timeData = response.getJSONObject( "time" );
        JSONArray timePeriods = timeData.getJSONArray( "startPeriodName" );

        JSONObject weatherData = response.getJSONObject( "data" );
        JSONArray temeratures = weatherData.getJSONArray( "temperature" );
        JSONArray precipitationChances = weatherData.getJSONArray( "pop" );
        JSONArray weathers = weatherData.getJSONArray( "weather" );
        JSONArray weatherText = weatherData.getJSONArray( "text" );

        for( int i = 0; i < timePeriods.length(); i++ ) {
          sb.append( timePeriods.get( i ) );
          sb.append( " -> " );
          sb.append( weatherText.get( i ) );
          sb.append( "\n\n" );
        }

      } catch( JSONException je ) {
        Log.e( getResources().getString( R.string.exception_tag ), "Caught JSONException while processing result.", je );
      }

      TextView weatherView = (TextView) findViewById( R.id.weather_view );
      weatherView.setText( sb.toString() );
    }
  }
}
