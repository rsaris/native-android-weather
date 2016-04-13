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

  private class WeatherXMLParser {

    private Map<String, List<String>> _timeLayoutMap;
    private Map<String, String> _timeNameMap;
    private Map<String, Integer> _temperatureMap;
    private Map<String, Integer> _precipitationMap;

    public WeatherXMLParser( InputStream in ) throws XmlPullParserException, IOException {
      _timeLayoutMap = new HashMap<>();
      _timeNameMap = new HashMap<>();
      _temperatureMap = new HashMap<>();
      _precipitationMap = new HashMap<>();

      parse( in );
    }

    public String getData() {
      StringBuilder sb = new StringBuilder();
      List<String> sortedTimeKeys = new ArrayList<>( _temperatureMap.keySet() );
      java.util.Collections.sort( sortedTimeKeys );

      for( String timeKey : sortedTimeKeys ) {
        sb.append( _timeNameMap.get( timeKey ) );
        sb.append( " -> " );
        sb.append( _temperatureMap.get( timeKey ) );
        sb.append( " degrees, " );
        sb.append( _precipitationMap.get( timeKey ) );
        sb.append( "% rain\n" );
      }

      return sb.toString();
    }


    private void parse( InputStream in ) throws XmlPullParserException, IOException {
      try {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature( XmlPullParser.FEATURE_PROCESS_NAMESPACES, false );
        parser.setInput( in, null );
        parser.nextTag();
        readFeed( parser );
      } finally {
        in.close();
      }
    }

    private void readFeed( XmlPullParser parser ) throws XmlPullParserException, IOException {
      parser.require( XmlPullParser.START_TAG, null, "dwml" );
      while( parser.next() != XmlPullParser.END_TAG ) {
        if( parser.getEventType() != XmlPullParser.START_TAG ) {
          continue;
        }
        String name = parser.getName();
        // Starts by looking for the entry tag
        if( name.equals( "data" ) ) {
          readData( parser );
          return;
        } else {
          skip( parser );
        }
      }
    }

    private void readData( XmlPullParser parser ) throws XmlPullParserException, IOException {
      parser.require( XmlPullParser.START_TAG, null, "data" );

      while( parser.next() != XmlPullParser.END_TAG ) {
        if( parser.getEventType() != XmlPullParser.START_TAG ) {
          continue;
        }
        String name = parser.getName();
        if( name.equals( "time-layout" ) ) {
          readTimeLayout( parser );
        } else if( name.equals( "parameters" ) ) {
          readParameters( parser );
        } else {
          skip( parser );
        }
      }
    }

    private void readTimeLayout( XmlPullParser parser ) throws XmlPullParserException, IOException {

      String newKey = null;
      List<String> newTimeLayouts = new ArrayList<>();

      parser.require( XmlPullParser.START_TAG, null, "time-layout" );
      while( parser.next() != XmlPullParser.END_TAG ) {
        if( parser.getEventType() != XmlPullParser.START_TAG ) {
          continue;
        }
        String name = parser.getName();
        if( name.equals( "layout-key" ) ) {
          parser.next();
          newKey = parser.getText();
          while( parser.next() != XmlPullParser.END_TAG ) {}
        } else if( name.equals( "start-valid-time" ) ) {
          String value = parser.getAttributeValue( null, "period-name" );
          parser.next();
          String key = parser.getText();
          newTimeLayouts.add( key );
          if( value != null ) {
            _timeNameMap.put( key, value );
          }
          while( parser.next() != XmlPullParser.END_TAG ) {}
        } else {
          skip( parser );
        }
      }

      if( newKey != null ) {
        _timeLayoutMap.put( newKey, newTimeLayouts );
      }
    }

    private void readParameters( XmlPullParser parser ) throws XmlPullParserException, IOException {
      while( parser.next() != XmlPullParser.END_TAG ) {
        if( parser.getEventType() != XmlPullParser.START_TAG ) {
          continue;
        }

        String name = parser.getName();
        if( name.equals( "temperature" ) ) {
          readTemperature( parser );
        } else if( name.equals( "probability-of-precipitation" ) ) {
          readPrecipitation( parser );
        } else {
          skip( parser );
        }
      }
    }

    private void readTemperature( XmlPullParser parser ) throws XmlPullParserException, IOException {
      String timeLayoutKey = parser.getAttributeValue( null, "time-layout" );
      List<String> timeLayoutList = _timeLayoutMap.get( timeLayoutKey );
      int currentIndex = 0;

      while( parser.next() != XmlPullParser.END_TAG ) {
        if( parser.getEventType() != XmlPullParser.START_TAG ) {
          continue;
        }

        String name = parser.getName();
        if( name.equals( "value" ) ) {
          parser.next();
          _temperatureMap.put( timeLayoutList.get( currentIndex ), Integer.valueOf( parser.getText() ) );
          while( parser.next() != XmlPullParser.END_TAG ) {}
          currentIndex++;
        } else {
          skip( parser );
        }
      }
    }

    private void readPrecipitation( XmlPullParser parser ) throws XmlPullParserException, IOException {
      String timeLayoutKey = parser.getAttributeValue( null, "time-layout" );
      List<String> timeLayoutList = _timeLayoutMap.get( timeLayoutKey );
      int currentIndex = 0;

      while( parser.next() != XmlPullParser.END_TAG ) {
        if( parser.getEventType() != XmlPullParser.START_TAG ) {
          continue;
        }

        String name = parser.getName();
        if( name.equals( "value" ) ) {
          parser.next();
          _precipitationMap.put( timeLayoutList.get( currentIndex ), Integer.valueOf( parser.getText() ) );
          while( parser.next() != XmlPullParser.END_TAG ) {}
          currentIndex++;
        } else {
          skip( parser );
        }
      }
    }


    private void skip( XmlPullParser parser ) throws XmlPullParserException, IOException {
      if( parser.getEventType() != XmlPullParser.START_TAG ) {
        throw new IllegalStateException();
      }
      int depth = 1;
      while( depth != 0 ) {
        switch( parser.next() ) {
          case XmlPullParser.END_TAG:
            depth--;
            break;
          case XmlPullParser.START_TAG:
            depth++;
            break;
        }
      }
    }
  }
}
