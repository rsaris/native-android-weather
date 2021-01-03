package com.bobsaris.somervilleweather;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationServices;

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
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements ConnectionCallbacks, OnConnectionFailedListener {
  private static final long SOMERVILLE_LAT = Double.doubleToLongBits( 42.39 );
  private static final long SOMERVILLE_LNG = Double.doubleToLongBits( -71.15 );

  private static final int PERMISSION_REQUESTED_COARSE_LOCATION = 1;

  private static final String PREFS_KEY = "WEATHERPREFS";
  private static final String LOCATION_LAT_KEY = "LOCATIONLAT";
  private static final String LOCATION_LNG_KEY = "LOCATIONLNG";

  private SwipeRefreshLayout _refreshView;
  private List<WeatherData> _weatherData;
  private WeatherListAdapter _listAdapter;
  private WeatherViewPagerAdapter _pagerAdapter;
  private GoogleApiClient _googleApiClient;

  @Override
  protected void onCreate( Bundle savedInstanceState ) {
    super.onCreate( savedInstanceState );
    setContentView( R.layout.weather_loading_layout );

    _weatherData = new ArrayList<>();
    _pagerAdapter = new WeatherViewPagerAdapter( this, _weatherData );
    _listAdapter = new WeatherListAdapter( this );

    // Create an instance of GoogleAPIClient.
    if (_googleApiClient == null) {
      _googleApiClient = new GoogleApiClient.Builder(this)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .addApi( LocationServices.API )
        .build();
    }

    WeatherTask weatherTask = new WeatherTask();
    weatherTask.execute();
  }

  @Override
  public void onStart() {
    _googleApiClient.connect();
    super.onStart();
  }

  @Override
  public void onStop() {
    _googleApiClient.disconnect();
    super.onStop();
  }

  public void onSaveButtonClick( View view ) {
    updateLatLng();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    switch (requestCode) {
      case PERMISSION_REQUESTED_COARSE_LOCATION: {
        if( grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          updateLatLng();
        }
      }
    }
  }

  @Override
  public void onConnected( Bundle connectionHunt ) {
  }

  @Override
  public void onConnectionSuspended( int param ) {
  }

  @Override
  public void onConnectionFailed( ConnectionResult result ) {
  }

  private void updateLatLng() {
    int permissionCheck = ContextCompat.checkSelfPermission( this, Manifest.permission.ACCESS_COARSE_LOCATION );
    if( permissionCheck != PackageManager.PERMISSION_GRANTED ) {
      ActivityCompat.requestPermissions( this,  new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUESTED_COARSE_LOCATION );
    } else {
      Location lastLocation = LocationServices.FusedLocationApi.getLastLocation( _googleApiClient );
      if( lastLocation != null ) {
        Log.d( getResources().getString( R.string.log_tag ), "Got new location: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude() );
        saveLatLng( getBaseContext(), lastLocation.getLatitude(), lastLocation.getLongitude() );

        WeatherTask weatherTask = new WeatherTask();
        weatherTask.execute();
      } else {
        new AlertDialog.Builder( new ContextThemeWrapper( this, R.style.Theme_Dialog_Alert_Weather ) )
          .setTitle( "Error loading location" )
          .setMessage( "There was an error loading the location, please try again." )
          .setPositiveButton( "OK", null )
          .show();
      }
    }
  }

  private static Double[] loadLatLng( Context context ) {
    SharedPreferences prefs = context.getSharedPreferences( PREFS_KEY, Context.MODE_PRIVATE );
    return new Double[]{
      Double.longBitsToDouble( prefs.getLong( LOCATION_LAT_KEY, SOMERVILLE_LAT ) ),
      Double.longBitsToDouble( prefs.getLong( LOCATION_LNG_KEY, SOMERVILLE_LNG ) )
    };
  }

  private static void saveLatLng( Context context, double lat, double lng ) {
    SharedPreferences prefs = context.getSharedPreferences( PREFS_KEY, Context.MODE_PRIVATE );
    SharedPreferences.Editor prefsEditor = prefs.edit();

    prefsEditor.putLong( LOCATION_LAT_KEY, Double.doubleToLongBits( lat ) );
    prefsEditor.putLong( LOCATION_LNG_KEY, Double.doubleToLongBits( lng ) );
    prefsEditor.commit();
  }

  private class WeatherTask extends AsyncTask<Void, Integer, String> {
    // private String NOAA_BASE_URL = "http://graphical.weather.gov/xml/sample_products/browser_interface/ndfdBrowserClientByDay.php";
    private String NOAA_BASE_URL = "http://forecast.weather.gov/MapClick.php";

    protected String doInBackground( Void... placeholder ) {
      try {
        Double[] latLng = loadLatLng( getBaseContext() );
        URL url = new URL(
          NOAA_BASE_URL +
            "?lat=" + latLng[0] +
            "&lon=" + latLng[1] +
            "&FcstType=json"
        );

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
          BufferedReader reader = new BufferedReader( new InputStreamReader( urlConnection.getInputStream() ) );
          StringBuilder sb = new StringBuilder();
          String line = null;
          while( ( line = reader.readLine() ) != null ) {
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

      return null;
    }

    protected void onPostExecute( String result ) {
      _weatherData.clear();
      if( result != null ) {
        try {
          JSONObject response = new JSONObject( result );
          JSONObject timeData = response.getJSONObject( "time" );
          JSONArray rawTimePeriods = timeData.getJSONArray( "startPeriodName" );

          JSONObject currentObservationData = response.getJSONObject( "currentobservation" );
          _weatherData.add(
            new WeatherData(
              "Right Now",
              currentObservationData.getString( "Temp" ),
              null,
              currentObservationData.getString( "Weather" ),
              "It is currently " + currentObservationData.getString( "Temp" ) + " degrees outside.",
              "/" + currentObservationData.getString( "Weatherimage" )
            )
          );

          JSONObject rawWeatherData = response.getJSONObject( "data" );
          JSONArray rawTemperatures = rawWeatherData.getJSONArray( "temperature" );
          JSONArray rawPercentOfPrecipitation = rawWeatherData.getJSONArray( "pop" );
          JSONArray rawWeathers = rawWeatherData.getJSONArray( "weather" );
          JSONArray rawWeatherTexts = rawWeatherData.getJSONArray( "text" );
          JSONArray rawIconURLs = rawWeatherData.getJSONArray( "iconLink" );

          for( int i = 0; i < rawTimePeriods.length(); i++ ) {
            _weatherData.add(
              new WeatherData(
                rawTimePeriods.getString( i ),
                rawTemperatures.getString( i ),
                rawPercentOfPrecipitation.getString( i ),
                rawWeathers.getString( i ),
                rawWeatherTexts.getString( i ),
                rawIconURLs.getString( i )
              )
            );
          }
        } catch( JSONException je ) {
          Log.e( getResources().getString( R.string.exception_tag ), "Caught JSONException while processing result.", je );
          _weatherData.clear();
        }
      }

      if( _refreshView == null ) {
        setContentView( R.layout.refresh_list_layout );
        _refreshView = (SwipeRefreshLayout) findViewById( R.id.refresh );
        _refreshView.setOnRefreshListener( new SwipeRefreshLayout.OnRefreshListener() {
          @Override
          public void onRefresh() {
            WeatherTask weatherTask = new WeatherTask();
            weatherTask.execute();
          }
        });

        ListView listView = (ListView) _refreshView.findViewById( R.id.list );
        listView.setAdapter( _listAdapter );
      }

      _listAdapter.notifyDataSetChanged();
      _refreshView.setRefreshing( false );
    }
  }

  private class WeatherListAdapter extends ArrayAdapter {
    private Context _context;

    // YES! This is silly, but it's easier to just extend an ArrayAdapter with a dummy array
    // than it is to extend Adapter and figure all that noise out...
    public WeatherListAdapter( Context context ) {
      super( context, R.layout.pager_layout, new Integer[]{ Integer.valueOf( 0 ) } );
      _context = context;
    }

    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
      if( convertView == null ) {
        convertView = LayoutInflater.from( _context ).inflate( R.layout.list_item_layout, parent, false );
      }

      LinearLayout parentView = (LinearLayout)convertView;
      View childView = parentView.getChildAt( 0 );

      ViewPager pager;
      if( childView == null || !(childView instanceof ViewPager) ) {
        if( childView != null ) {
          parentView.removeView( childView );
        }

        LayoutInflater.from( _context ).inflate( R.layout.pager_layout, parentView );
        pager = (ViewPager)parentView.findViewById( R.id.pager );
        pager.setAdapter( _pagerAdapter );
        pager.addOnPageChangeListener( new ViewPager.OnPageChangeListener() {
          @Override
          public void onPageScrolled( int position, float v, int i1 ) {
          }

          @Override
          public void onPageSelected( int position ) {
          }

          @Override
          public void onPageScrollStateChanged( int state ) {
            _refreshView.setEnabled( state == ViewPager.SCROLL_STATE_IDLE );
          }
        });
      } else {
        pager = (ViewPager)parentView.findViewById( R.id.pager );
      }

      pager.getAdapter().notifyDataSetChanged();
      pager.setCurrentItem( 1 );

      convertView.setMinimumHeight( parent.getHeight() );
      return convertView;
    }
  }

  private static class WeatherViewPagerAdapter extends PagerAdapter {
    private List<WeatherData> _weatherData;
    private Context _context;

    public WeatherViewPagerAdapter( Context context, List<WeatherData> weatherData ) {
      _weatherData = weatherData;
      _context = context;
    }

    @Override
    public int getCount() {
      // Always make sure to include two views, one for settings and one for an error
      return Math.max( _weatherData.size() + 1, 2 );
    }

    @Override
    public Object instantiateItem( ViewGroup parent, int position ) {
      if( position == 0 ) {
        View settingsView = LayoutInflater.from( _context ).inflate( R.layout.settings_layout, parent, false );

        Double[] latLng = loadLatLng( _context );
        StringBuilder statusBuilder = new StringBuilder();
        statusBuilder.append( "Current location is (" );
        statusBuilder.append( String.format( "%.2f", latLng[0] ) );
        statusBuilder.append( ", " );
        statusBuilder.append( String.format( "%.2f", latLng[1] ) );
        statusBuilder.append( ")" );

        ((TextView)settingsView.findViewById( R.id.current_location )).setText( statusBuilder.toString() );

        parent.addView( settingsView );
        return settingsView;
      } else if ( _weatherData.size() == 0 ) {
        View errorView = LayoutInflater.from( _context ).inflate( R.layout.weather_error_layout, parent, false );

        ((TextView)errorView.findViewById( R.id.message )).setText( "Please check your connection." );

        parent.addView( errorView );

        return errorView;
      } else {
        final WeatherData data = _weatherData.get( position - 1 );
        Calendar cal = Calendar.getInstance();
        boolean isNight = data.getTitle().toLowerCase().contains( "night" ) ||
          ( data.getTitle().toLowerCase().contains( "now" ) &&
            ( cal.get( Calendar.HOUR_OF_DAY ) > 18 || cal.get( Calendar.HOUR_OF_DAY ) < 5 ) );

        View parentView = LayoutInflater.from( _context ).inflate( R.layout.weather_layout, parent, false );
        ImageView iconView = (ImageView) parentView.findViewById( R.id.weather_icon );
        TextView dayView = (TextView) parentView.findViewById( R.id.weather_day );
        TextView weatherTitleView = (TextView) parentView.findViewById( R.id.weather_title );
        TextView weatherDescriptionView = (TextView) parentView.findViewById( R.id.weather_description );

        iconView.setImageDrawable( data.getIconDrawable( _context ) );
        iconView.setOnClickListener( new View.OnClickListener() {
          public void onClick( View v ) {
            Intent intent = new Intent();
            intent.setAction( Intent.ACTION_VIEW );
            intent.addCategory( Intent.CATEGORY_BROWSABLE );
            intent.setData( Uri.parse( data.getIconURL() ) );
            _context.startActivity( intent );
          }
        } );
        dayView.setText( data.getTitle() );
        weatherTitleView.setText( data.getWeather() );
        weatherDescriptionView.setText( data.getWeatherText() );

        if( isNight ) {
          parentView.setBackgroundColor( ContextCompat.getColor( _context, R.color.nightBackground ) );
          dayView.setTextColor( ContextCompat.getColor( _context, R.color.nightText ) );
          weatherTitleView.setTextColor( ContextCompat.getColor( _context, R.color.nightText ) );
          weatherDescriptionView.setTextColor( ContextCompat.getColor( _context, R.color.nightText ) );
        } else {
          parentView.setBackgroundColor( ContextCompat.getColor( _context, R.color.dayBackground ) );
          dayView.setTextColor( ContextCompat.getColor( _context, R.color.dayText ) );
          weatherTitleView.setTextColor( ContextCompat.getColor( _context, R.color.dayText ) );
          weatherDescriptionView.setTextColor( ContextCompat.getColor( _context, R.color.dayText ) );
        }

        parent.addView( parentView );
        return parentView;
      }
    }

    @Override
    public void destroyItem( ViewGroup parent, int position, Object view ) {
      parent.removeView( (View) view );
    }

    @Override
    public boolean isViewFromObject( View view, Object object ) {
      return view == object;
    }

    @Override
    public int getItemPosition( Object object ) {
      // TODO: This is probably a bad idea, should probably figure out how to update the views we have
      return POSITION_NONE;
    }
  }

  private static class WeatherData {
    private String _title;
    private String _temperature;
    private String _percentOfPrecipitation;
    private String _weather;
    private String _weatherText;
    private String _iconURL;

    public WeatherData( String title, String temperature, String percentOfPrecipitation, String weather, String weatherText, String iconURL ) {
      _title = title;
      _temperature = temperature;
      _percentOfPrecipitation = percentOfPrecipitation;
      _weather = weather;
      _weatherText = weatherText;
      _iconURL = iconURL;
    }

    public Drawable getIconDrawable( Context context ) {
      int iconID = R.drawable.weather_unknown;

      Pattern simplePattern = Pattern.compile( ".*\\/(.*).png\\z" );
      Pattern doublePattern = Pattern.compile( ".*DualImage.php\\?i=(.*)&j=(.*)&?.*" );

      Matcher simpleMatcher = simplePattern.matcher( _iconURL );
      Matcher doubleMatcher = doublePattern.matcher( _iconURL );
      if( simpleMatcher.matches() || doubleMatcher.matches() ) {
        String matchFile = (simpleMatcher.matches() ? simpleMatcher.group( 1 ) : doubleMatcher.group( 1 ));
        switch( matchFile ) {
          case "nskc":
          case "nfew":
            iconID = R.drawable.weather_night_clear;
            break;
          case "bkn":
          case "skc":
          case "few":
            iconID = R.drawable.weather_day_clear;
            break;
          case "nsct":
          case "nbkn":
            iconID = R.drawable.weather_night_cloudy;
            break;
          case "sct":
            iconID = R.drawable.weather_day_cloudy;
            break;
          case "shra":
          case "shra10":
          case "shra20":
          case "shra30":
          case "shra40":
          case "ra":
          case "ra10":
          case "ra20":
          case "ra30":
          case "ra40":
          case "hi_shwrs":
          case "hi_shwrs10":
          case "hi_shwrs20":
          case "hi_shwrs30":
          case "hi_shwrs40":
            iconID = R.drawable.weather_sunny_rain;
            break;
          case "nshra":
          case "nshra10":
          case "nshra20":
          case "nshra30":
          case "nshra40":
          case "nra":
          case "nra10":
          case "nra20":
          case "nra30":
          case "nra40":
            iconID = R.drawable.weather_moon_rain;
            break;
          case "ra50":
          case "ra60":
          case "ra70":
          case "ra80":
          case "ra90":
          case "ra100":
          case "shra50":
          case "shra60":
          case "shra70":
          case "shra80":
          case "shra90":
          case "shra100":
          case "nshra50":
          case "nshra60":
          case "nshra70":
          case "nshra80":
          case "nshra90":
          case "nshra100":
          case "nra50":
          case "nra60":
          case "nra70":
          case "nra80":
          case "nra90":
          case "nra100":
          case "hi_shwrs50":
          case "hi_shwrs60":
          case "hi_shwrs70":
          case "hi_shwrs80":
          case "hi_shwrs90":
          case "hi_shwrs100":
            iconID = R.drawable.weather_rain;
            break;
          case "ntsra":
          case "ntsra10":
          case "ntsra20":
          case "ntsra30":
          case "ntsra40":
          case "hi_ntsra":
          case "hi_ntsra10":
          case "hi_ntsra20":
          case "hi_ntsra30":
          case "hi_ntsra40":
            iconID = R.drawable.weather_night_thunderstorm;
            break;
          case "tsra":
          case "tsra10":
          case "tsra20":
          case "tsra30":
          case "tsra40":
          case "tsra50":
          case "tsra60":
          case "tsra70":
          case "tsra80":
          case "tsra90":
          case "tsra100":
          case "ntsra50":
          case "ntsra60":
          case "ntsra70":
          case "ntsra80":
          case "ntsra90":
          case "ntsra100":
          case "hi_tsra":
          case "hi_tsra10":
          case "hi_tsra20":
          case "hi_tsra30":
          case "hi_tsra40":
          case "hi_tsra50":
          case "hi_tsra60":
          case "hi_tsra70":
          case "hi_tsra80":
          case "hi_tsra90":
          case "hi_tsra100":
          case "hi_ntsra50":
          case "hi_ntsra60":
          case "hi_ntsra70":
          case "hi_ntsra80":
          case "hi_ntsra90":
          case "hi_ntsra100":
          case "scttsra":
            iconID = R.drawable.weather_thunderstorm;
            break;
          case "nvc":
          case "vc":
            iconID = R.drawable.weather_cloudy;
            break;
        }
      }

      return ContextCompat.getDrawable( context, iconID );
    }

    public String getTitle() { return _title; }
    public String getTemperature() { return _temperature; }
    public String getPercentOfPrecipitation() { return _percentOfPrecipitation; }
    public String getWeather() { return _weather; }
    public String getWeatherText() { return _weatherText; }
    public String getIconURL() { return _iconURL; }
  }
}
