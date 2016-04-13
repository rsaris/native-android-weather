package com.bobsaris.somervilleweather;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends FragmentActivity {
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
      List<WeatherData> weatherData = new ArrayList<>();

      try {
        JSONObject response = new JSONObject( result );
        JSONObject timeData = response.getJSONObject( "time" );
        JSONArray rawTimePeriods = timeData.getJSONArray( "startPeriodName" );

        JSONObject rawWeatherData = response.getJSONObject( "data" );
        JSONArray rawTemeratures = rawWeatherData.getJSONArray( "temperature" );
        JSONArray rawPercentOfPrecipitation = rawWeatherData.getJSONArray( "pop" );
        JSONArray rawWeathers = rawWeatherData.getJSONArray( "weather" );
        JSONArray rawWeatherTexts = rawWeatherData.getJSONArray( "text" );

        for( int i = 0; i < rawTimePeriods.length(); i++ ) {
          weatherData.add(
            new WeatherData(
              rawTimePeriods.getString( i ),
              rawTemeratures.getString( i ),
              rawPercentOfPrecipitation.getString( i ),
              rawWeathers.getString( i ),
              rawWeatherTexts.getString( i )
            )
          );
        }

      } catch( JSONException je ) {
        Log.e( getResources().getString( R.string.exception_tag ), "Caught JSONException while processing result.", je );
      }

      ViewPager pager = (ViewPager)findViewById(R.id.pager);
      pager.setAdapter( new WeatherPagerAdapter( getSupportFragmentManager(), weatherData ) );
    }
  }

  private class WeatherPagerAdapter extends FragmentPagerAdapter {
    List<WeatherData> _weatherData;

    public WeatherPagerAdapter( FragmentManager fm, List<WeatherData> weatherData ) {
      super( fm );

      _weatherData = weatherData;
    }

    @Override
    public int getCount() {
      return _weatherData.size();
    }

    @Override
    public Fragment getItem( int index ) {
      return WeatherFragment.newInstance( _weatherData.get( index ) );
    }
  }

  public static class WeatherFragment extends Fragment {
    private static String ARG_KEY_TITLE = "title";
    private static String ARG_KEY_WEATHER = "weather";

    static WeatherFragment newInstance( WeatherData weatherData ) {
      WeatherFragment fragment = new WeatherFragment();

      // Supply num input as an argument.
      Bundle args = new Bundle();
      args.putString( ARG_KEY_TITLE, weatherData.getTitle() );
      args.putString( ARG_KEY_WEATHER, weatherData.getWeatherText() );
      fragment.setArguments(args);

      return fragment;
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      View v = inflater.inflate( R.layout.weather_layout, container, false );
      TextView titleView = (TextView)v.findViewById( R.id.title );
      TextView weatherView = (TextView)v.findViewById( R.id.weather );

      Bundle arguments = getArguments();
      if( arguments == null ) {
        titleView.setText( "Error" );
        weatherView.setText( "There was an error loading information..." );
      } else {
        titleView.setText( arguments.getString( ARG_KEY_TITLE ) );
        weatherView.setText( arguments.getString( ARG_KEY_WEATHER ) );
      }

      return v;
    }
  }

  private class WeatherData {
    private String _title;
    private String _temperature;
    private String _percentOfPrecipitation;
    private String _weather;
    private String _weatherText;

    public WeatherData( String title, String temperature, String percentOfPrecipitation, String weather, String weatherText ) {
      _title = title;
      _temperature = temperature;
      _percentOfPrecipitation = percentOfPrecipitation;
      _weather = weather;
      _weatherText = weatherText;
    }

    public String getTitle() { return _title; }
    public String getTemperature() { return _temperature; }
    public String getPercentOfPrecipitation() { return _percentOfPrecipitation; }
    public String getWeather() { return _weather; }
    public String getWeatherText() { return _weatherText; }
  }
}
