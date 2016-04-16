package com.bobsaris.somervilleweather;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
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

public class MainActivity extends Activity {
  private SwipeRefreshLayout _refreshView;
  private List<WeatherData> _weatherData;
  private WeatherListAdapter _listAdapter;
  private WeatherViewPagerAdapter _pagerAdapter;

  @Override
  protected void onCreate( Bundle savedInstanceState ) {
    super.onCreate( savedInstanceState );
    setContentView( R.layout.refresh_list_layout );

    _weatherData = new ArrayList<>();
    List<List<WeatherData>> listViewList = new ArrayList<>();
    listViewList.add( _weatherData );

    _pagerAdapter = new WeatherViewPagerAdapter( this, _weatherData );

    _refreshView = (SwipeRefreshLayout) findViewById( R.id.refresh );
    _refreshView.setOnRefreshListener( new SwipeRefreshLayout.OnRefreshListener() {
      @Override
      public void onRefresh() {
        WeatherTask weatherTask = new WeatherTask();
        weatherTask.execute();
      }
    });

    _listAdapter = new WeatherListAdapter( this );

    ListView listView = (ListView) _refreshView.findViewById( R.id.list );
    listView.setAdapter( _listAdapter );

    WeatherTask weatherTask = new WeatherTask();
    weatherTask.execute();
  }

  private class WeatherTask extends AsyncTask<Void, Integer, String> {
    private String SOMERVILLE_ZIP = "02143";
    private String SOMERVILLE_LAT = "42.39";
    private String SOMERVILLE_LON = "-71.15";

    // private String NOAA_BASE_URL = "http://graphical.weather.gov/xml/sample_products/browser_interface/ndfdBrowserClientByDay.php";
    private String NOAA_BASE_URL = "http://forecast.weather.gov/MapClick.php";

    protected String doInBackground( Void... placeholder ) {
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
      if( result != null ) {
        try {
          JSONObject response = new JSONObject( result );
          JSONObject timeData = response.getJSONObject( "time" );
          JSONArray rawTimePeriods = timeData.getJSONArray( "startPeriodName" );

          JSONObject rawWeatherData = response.getJSONObject( "data" );
          JSONArray rawTemperatures = rawWeatherData.getJSONArray( "temperature" );
          JSONArray rawPercentOfPrecipitation = rawWeatherData.getJSONArray( "pop" );
          JSONArray rawWeathers = rawWeatherData.getJSONArray( "weather" );
          JSONArray rawWeatherTexts = rawWeatherData.getJSONArray( "text" );

          _weatherData.clear();
          for( int i = 0; i < rawTimePeriods.length(); i++ ) {
            _weatherData.add(
              new WeatherData(
                rawTimePeriods.getString( i ),
                rawTemperatures.getString( i ),
                rawPercentOfPrecipitation.getString( i ),
                rawWeathers.getString( i ),
                rawWeatherTexts.getString( i )
              )
            );
          }
        } catch( JSONException je ) {
          Log.e( getResources().getString( R.string.exception_tag ), "Caught JSONException while processing result.", je );
        }
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
      ViewPager pager;
      if( convertView == null ) {
        convertView = LayoutInflater.from( _context ).inflate( R.layout.pager_layout, parent, false );
        pager = (ViewPager) convertView.findViewById( R.id.pager );
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
        } );
      } else {
        pager = (ViewPager) convertView.findViewById( R.id.pager );
        pager.getAdapter().notifyDataSetChanged();
        pager.setCurrentItem( 0 );
      }

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
      return _weatherData.size();
    }

    @Override
    public Object instantiateItem( ViewGroup parent, int position ) {
      WeatherData data = _weatherData.get( position );

      View parentView = LayoutInflater.from( _context ).inflate( R.layout.weather_layout, parent, false );
      TextView titleView = (TextView) parentView.findViewById( R.id.title );
      titleView.setText( data.getTitle() );
      TextView weatherView = (TextView) parentView.findViewById( R.id.weather );
      weatherView.setText( data.getWeatherText() );
      parent.addView( parentView );

      return parentView;
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
