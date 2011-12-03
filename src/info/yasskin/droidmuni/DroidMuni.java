package info.yasskin.droidmuni;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.viewpagerindicator.TitlePageIndicator;
import com.viewpagerindicator.TitlePageIndicator.IndicatorStyle;
import com.viewpagerindicator.TitleProvider;

public class DroidMuni extends Activity {
  private final Handler m_handler = new Handler();

  private ViewPager muniPager;
  private Context cxt;
  
  /**
   * Set once in onCreate() and never modified again.
   */
  private LocationManager m_location_manager;
  private PreferenceManager m_preferences_manager;

  static final int REDRAW_INTERVAL_MS = 30000;
  static final int REPREDICT_INTERVAL_MS = 2 * 60000;
  static final int NUM_CLOSEST_STOPS = 6;

  /**
   * Maps a route tag to the direction tag that was last selected for it.
   */
  private final Map<String, String> m_prev_directions =
      new HashMap<String, String>();

  // These are all set in onCreate() and then never changed again.
  private Spinner m_line_spinner;
  private Spinner m_direction_spinner;
  private Spinner m_stop_spinner;
  private ListView m_prediction_list;
  private SimpleCursorAdapter m_line_adapter;
  private SimpleCursorAdapter m_direction_adapter;
  private SimpleCursorAdapter m_stop_adapter;
  private SimpleCursorAdapter m_predictions_adapter;

  private MultiStopAdapter m_multistop_adapter;
  private ListView m_multistop_list;

  private static final Cursor m_loading_lines = makeConstantCursor(
      "description", "Loading lines...");
  private static final Cursor m_line_request_failed = makeConstantCursor(
      "description", "Line request failed", "Retry");
  private static final Cursor m_loading_directions = makeConstantCursor(
      "title", "Loading directions...");
  private static final Cursor m_directions_request_failed = makeConstantCursor(
      "title", "Direction request failed", "Retry");
  private static final Cursor m_loading_stops = makeConstantCursor("title",
      "Loading stops...");
  private static final Cursor m_stop_request_failed = makeConstantCursor(
      "title", "Stop request failed", "Retry");
  private static final Cursor m_loading_predictions = makeConstantCursor(
      "predicted_time", "Loading predictions...");
  private static final Cursor m_prediction_request_failed = makeConstantCursor(
      "predicted_time", "Prediction request failed");
  private static final Cursor m_no_predictions = makeConstantCursor(
      "predicted_time", "No predictions");

  private static Cursor makeConstantCursor(String column_name, String... rows) {
    MatrixCursor result =
        new MatrixCursor(new String[] { "_id", column_name }, rows.length);
    int id = 0;
    for (String row : rows) {
      result.addRow(new Object[] { id++, row });
    }
    return result;
  }

  /** Called to link up the original DroidMuni widgets to the pager view */
  private void hookupAllLineWidgets(View v) {
    m_line_spinner = (Spinner) v.findViewById(R.id.line);
    m_line_adapter =
        setupSpinner(m_line_spinner, "description", mLineClickedHandler);
    m_route_query_manager.setAdapter(m_line_adapter);
    queryRoutes();

    m_direction_spinner = (Spinner) v.findViewById(R.id.direction);
    m_direction_adapter =
        setupSpinner(m_direction_spinner, "title", mDirectionClickedHandler);
    m_directions_query_manager.setAdapter(m_direction_adapter);

    m_stop_spinner = (Spinner) v.findViewById(R.id.stop);
    m_stop_adapter = setupSpinner(m_stop_spinner, "title", mStopClickedHandler);
    m_stop_query_manager.setAdapter(m_stop_adapter);

    m_prediction_list = (ListView) v.findViewById(R.id.predictions);
    m_predictions_adapter =
        new SimpleCursorAdapter(this, R.layout.prediction_list_item, null,
            new String[] { "predicted_time" }, new int[] { android.R.id.text1 });
    m_predictions_adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
      public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
        if (!(view instanceof OnePredictionView)) {
          return false;
        }
        OnePredictionView prediction_view = (OnePredictionView) view;
        if (cursor == m_loading_predictions
            || cursor == m_prediction_request_failed
            || cursor == m_no_predictions) {
          prediction_view.setNoPredictionText(cursor.getString(columnIndex));
          return true;
        }
        prediction_view.setNoPredictionText("");

        prediction_view.setExpectedArrival(cursor.getLong(columnIndex));
        prediction_view.setQueryRouteTag(getSelectedRoute());
        prediction_view.setQueryDirectionTag(getSelectedDirection());
        prediction_view.setPredictionRouteTag(cursor.getString(cursor.getColumnIndexOrThrow("route_tag")));
        prediction_view.setPredictionDirectionTag(cursor.getString(cursor.getColumnIndexOrThrow("direction_tag")));
        prediction_view.setPredictionDirectionTitle(cursor.getString(cursor.getColumnIndexOrThrow("direction_title")));

        prediction_view.update();
        return true;
      }
    });
    m_prediction_list.setAdapter(m_predictions_adapter);
    m_prediction_query_manager.setAdapter(m_predictions_adapter);
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    Compatibility.enableStrictMode();

    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    cxt = this;

    int MINUTES_BETWEEN_LOCATION_UPDATES = 2;

    // Try to get location service; test for null since user may block location
    // tracking
    m_location_manager = (LocationManager) getSystemService(LOCATION_SERVICE);
    if (m_location_manager != null) {
      String provider =
          m_location_manager.getBestProvider(new Criteria(),/* enabled_only= */
              true);
      if (provider != null) {
        m_location_manager.requestLocationUpdates(provider,
            // Request loc updates every X minutes or Y meters
            (long) 1000.0 * 60 * MINUTES_BETWEEN_LOCATION_UPDATES,
            (float) 100.0, new LocationListener() {
              public void onLocationChanged(Location location) {
                if (location != null)
                  Log.i("DroidMuni", "Accuracy: " + location.getAccuracy());
              }

              public void onProviderDisabled(String provider) {
              }

              public void onProviderEnabled(String provider) {
              }

              public void onStatusChanged(String provider, int status,
                  Bundle extras) {
              }
            });
      }
    }
    
    m_preferences_manager = new PreferenceManager(this);

    this.setContentView(R.layout.main);

    // Note the helpful title indicator below comes from
    // http://blog.stylingandroid.com/archives/537
    // or see https://github.com/JakeWharton/Android-ViewPagerIndicator

    muniPager = (ViewPager) findViewById(R.id.munipager);
    muniPager.setAdapter(new DroidMuniPagerAdapter());

    TitlePageIndicator indicator =
        (TitlePageIndicator) findViewById(R.id.indicator);
    indicator.setViewPager(muniPager);
    indicator.setFooterColor(0xffa00000);
    indicator.setFooterIndicatorStyle(IndicatorStyle.Underline);
    indicator.setFooterLineHeight(3);

    muniPager.setCurrentItem(1);

  }

  private void queryRoutes() {
    m_route_query_manager.startQuery(getContentResolver(),
        NextMuniProvider.ROUTES_URI);
  }

  private final AdapterQueryManager m_route_query_manager =
      new AdapterQueryManager(m_loading_lines, m_line_request_failed) {
        @Override
        protected void onSuccessfulQuery(Cursor cursor) {
          final String saved_line = m_preferences_manager.getSavedLine();
          if (saved_line != "") {
            final int tag_index = cursor.getColumnIndexOrThrow("tag");
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
              if (cursor.getString(tag_index).equals(saved_line)) {
                m_line_spinner.setSelection(cursor.getPosition());
                break;
              }
            }
          }
        }
      };

  @Override
  protected void onResume() {
    super.onResume();
    if (this.m_predictions_shown) {
      m_handler.postDelayed(mRequeryPredictions, 0);
    }
  }

  @Override
  public void onPause() {
    super.onPause();

    m_handler.removeCallbacks(mRequeryPredictions);

    m_preferences_manager.apply();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    // Clear out all the adapters so their cursors get closed.
    m_direction_adapter.changeCursor(null);
    m_stop_adapter.changeCursor(null);
    m_predictions_adapter.changeCursor(null);
    m_line_adapter.changeCursor(null);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    super.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.about:
      showDialog(DIALOG_ABOUT_ID);
      return true;
    }
    return false;
  }

  private static final int DIALOG_ABOUT_ID = 0;

  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
    case DIALOG_ABOUT_ID:
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      TextView view = new TextView(this);
      // This MovementMethod follows links when clicked.
      view.setMovementMethod(LinkMovementMethod.getInstance());
      view.setText(Html.fromHtml(getString(R.string.about_dialog_contents)));
      builder.setView(view);
      builder.setNeutralButton(android.R.string.ok,
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.dismiss();
            }
          });
      return builder.create();
    }
    return null;
  }

  private SimpleCursorAdapter setupSpinner(Spinner spinner,
      String display_column, OnItemSelectedListener selection_handler) {
    SimpleCursorAdapter adapter =
        new SimpleCursorAdapter(this, android.R.layout.simple_spinner_item,
            null, new String[] { display_column },
            new int[] { android.R.id.text1 });
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(adapter);
    spinner.setOnItemSelectedListener(selection_handler);
    return adapter;
  }

  private String getSelectedRoute() {
    final int position = m_line_spinner.getSelectedItemPosition();
    if (position == AdapterView.INVALID_POSITION) {
      return null;
    }
    final Cursor cursor = m_line_adapter.getCursor();
    if (cursor == null || cursor == m_loading_lines
        || cursor == m_line_request_failed) {
      return null;
    }
    if (!cursor.moveToPosition(position)) {
      return null;
    }
    int tag_index = cursor.getColumnIndexOrThrow("tag");
    return cursor.getString(tag_index);
  }

  private String getSelectedDirection() {
    final int position = m_direction_spinner.getSelectedItemPosition();
    if (position == AdapterView.INVALID_POSITION) {
      return null;
    }
    final Cursor cursor = m_direction_adapter.getCursor();
    if (cursor == null || cursor == m_loading_directions
        || cursor == m_directions_request_failed) {
      return null;
    }
    if (!cursor.moveToPosition(position)) {
      return null;
    }
    int tag_index = cursor.getColumnIndexOrThrow("tag");
    return cursor.getString(tag_index);
  }

  private final OnItemSelectedListener mLineClickedHandler =
      new OnItemSelectedListener() {
        public void onItemSelected(AdapterView<?> parent, View v, int position,
            long id) {
          final Cursor parent_item =
              (Cursor) parent.getItemAtPosition(position);
          if (parent_item == m_loading_lines
              || parent_item == m_line_request_failed) {
            onNothingSelected(parent);
            if (parent_item == m_line_request_failed && position == 1) {
              queryRoutes();
            }
            return;
          }

          final String selected_route = parent_item.getString(1);
          m_preferences_manager.setSelectedLine(selected_route);

          m_directions_query_manager.startQuery(getContentResolver(),
              Uri.withAppendedPath(NextMuniProvider.DIRECTIONS_URI,
                  selected_route));
        }

        public void onNothingSelected(AdapterView<?> parent) {
          m_direction_adapter.changeCursor(null);
        }
      };

  private final AdapterQueryManager m_directions_query_manager =
      new AdapterQueryManager(m_loading_directions, m_directions_request_failed) {
        @Override
        protected void onSuccessfulQuery(final Cursor directions) {
          final String prev_direction =
              m_prev_directions.get(getSelectedRoute());
          if (prev_direction == null) {
            // If we've never seen this route before, stay on the first
            // direction choice.
            return;
          }
          final int tag_index = directions.getColumnIndexOrThrow("tag");
          for (directions.moveToFirst(); !directions.isAfterLast(); directions.moveToNext()) {
            if (prev_direction.equals(directions.getString(tag_index))) {
              m_direction_spinner.setSelection(directions.getPosition());
            }
          }
        }
      };

  private final OnItemSelectedListener mDirectionClickedHandler =
      new OnItemSelectedListener() {
        public void onItemSelected(AdapterView<?> parent, View v, int position,
            long id) {
          Cursor parent_item = (Cursor) parent.getItemAtPosition(position);
          if (parent_item == m_loading_directions
              || parent_item == m_directions_request_failed) {
            onNothingSelected(parent);
            // TODO: Handle retries.
            return;
          }
          String selected_route = parent_item.getString(1);
          String selected_direction = parent_item.getString(2);

          m_prev_directions.put(selected_route, selected_direction);

          m_stop_query_manager.startQuery(getContentResolver(),
              Uri.withAppendedPath(NextMuniProvider.STOPS_URI,
                  selected_route + "/" + selected_direction));
        }

        public void onNothingSelected(AdapterView<?> parent) {
          m_stop_adapter.changeCursor(null);
        }
      };

  private final AdapterQueryManager m_stop_query_manager =
      new AdapterQueryManager(m_loading_stops, m_stop_request_failed) {
        @Override
        protected void onSuccessfulQuery(Cursor cursor) {
          mSetStopToNearest.run();
        }
      };

  private final Runnable mSetStopToNearest = new Runnable() {
    public void run() {
      if (m_location_manager == null) {
        return;
      }
      final String provider_name =
          m_location_manager.getBestProvider(new Criteria(), true);
      if (provider_name == null) {
        return;
      }
      final Location last_location =
          m_location_manager.getLastKnownLocation(provider_name);
      if (last_location == null) {
        // Can't get the location, so leave the default stop at the
        // first one.
        return;
      }
      
      int best_position = getClosestStop(last_location, m_stop_adapter.getCursor());
      m_stop_spinner.setSelection(best_position);
    }
  };
  
  /** Return the list-position of the stop closest to a location, given a list of stops 
   * @param Location location to test
   * @param Cursor list of stops to compare to current location
   * @return integer position of the stop closest to the location 
   * */
  private int getClosestStop(Location last_location, Cursor stop_cursor) {    
      final int lat_index = stop_cursor.getColumnIndexOrThrow("lat");
      final int lon_index = stop_cursor.getColumnIndexOrThrow("lon");
      final float[] results = new float[1];

      double best_distance = Double.POSITIVE_INFINITY;
      int best_position = AdapterView.INVALID_POSITION;
      for (stop_cursor.moveToFirst(); !stop_cursor.isAfterLast(); stop_cursor.moveToNext()) {
        double stop_lat = stop_cursor.getDouble(lat_index);
        double stop_lon = stop_cursor.getDouble(lon_index);
        Location.distanceBetween(last_location.getLatitude(),
            last_location.getLongitude(), stop_lat, stop_lon, results);
        double distance = results[0];
        if (distance < best_distance) {
          best_distance = distance;
          best_position = stop_cursor.getPosition();
        }
      }
      return best_position;
  }
 
  /** Build list of the closest stops to our location, one for each route.
   * @param v View object of Nearby UI pane
   */
  private void buildListOfClosestStops(View v) {
    // Temporary list view while stops are loading
    String no_location =
        "\nCan't determine location;\nCheck Settings > Location";
    m_multistop_list =
        (ListView) DroidMuni.this.findViewById(R.id.multistop_predictions);

    final String provider_name =
        m_location_manager.getBestProvider(new Criteria(), true);

    if (provider_name == null) {
      m_multistop_list.setAdapter(new ArrayAdapter<String>(this,
          android.R.layout.simple_list_item_1, new String[] { no_location }));
      return;
    }

    if (m_location_manager.isProviderEnabled(provider_name))
      Log.i("DroidMuni", "Provider is disabled: " + provider_name);

    if (my_location == null) {
      m_multistop_list.setAdapter(new ArrayAdapter<String>(this,
          android.R.layout.simple_list_item_1, new String[] { no_location }));
      return;
    }

    m_multistop_list.setAdapter(new ArrayAdapter<String>(this,
        android.R.layout.simple_list_item_1,
        new String[] { "\nScanning for closest stops..." }));
  }

  private final OnItemSelectedListener mStopClickedHandler =
      new OnItemSelectedListener() {
        public void onItemSelected(AdapterView<?> parent, View v, int position,
            long id) {
          Cursor parent_item = (Cursor) parent.getItemAtPosition(position);
          if (parent_item == m_loading_stops
              || parent_item == m_stop_request_failed) {
            onNothingSelected(parent);
            // TODO: Handle retries.
            return;
          }
          String selected_stop = parent_item.getString(3);
          m_predictions_shown = false;
          m_handler.removeCallbacks(mRequeryPredictions);
          m_prediction_query_manager.startQuery(getContentResolver(),
              Uri.withAppendedPath(NextMuniProvider.PREDICTIONS_URI,
                  selected_stop));
        }

        public void onNothingSelected(AdapterView<?> parent) {
          m_predictions_shown = false;
          m_handler.removeCallbacks(mRequeryPredictions);
          m_predictions_adapter.changeCursor(null);
        }
      };

  private final AdapterQueryManager m_prediction_query_manager =
      new AdapterQueryManager(m_loading_predictions,
          m_prediction_request_failed) {
        @Override
        protected void onSuccessfulQuery(Cursor predictions) {
          if (predictions.getCount() == 0) {
            m_predictions_adapter.changeCursor(m_no_predictions);
          } else {
            m_predictions_shown = true;
            m_handler.postDelayed(mRequeryPredictions, REPREDICT_INTERVAL_MS);
          }
        }
      };

  private boolean m_predictions_shown = false;
  private final Runnable mRequeryPredictions = new Runnable() {
    public void run() {
      m_prediction_query_manager.requery();

      m_handler.postDelayed(this, REPREDICT_INTERVAL_MS);
    }
  };


  private class DroidMuniPagerAdapter extends PagerAdapter implements 
  TitleProvider {

      private String[] titles = new String[]
      {   "Nearby",
          "All Muni Lines",
          "Favorites",
      };
    
      public String getTitle( int position )
      {
          return titles[ position ];
      }

      @Override
      public int getCount() {
              return titles.length;
      }
      
      /**
      * Create the page for the given position.  The adapter is responsible
      * for adding the view to the container given here, although it only
      * must ensure this is done by the time it returns from
      * {@link #finishUpdate()}.
      *
      * @param collection The containing View in which the page will be shown.
      * @param position The page position to be instantiated.
      * @return Returns an Object representing the new page.  This does not
      * need to be a View, but can be some other container of the page.
      */
      @Override
      public Object instantiateItem(View collection, int position) {
        View v;
        switch (position) {
      case 0: // Nearby
        v = View.inflate(DroidMuni.this.cxt, R.layout.nearby, null);
        ((ViewPager) collection).addView(v, 0);
        buildListOfClosestStops(v);
        break;

      case 1: // All Lines (original UI)
        v = View.inflate(DroidMuni.this.cxt, R.layout.original, null);
        ((ViewPager) collection).addView(v, 0);
        hookupAllLineWidgets(v); // connect the widgets
        break;

      case 2: // Favorites-Alarms
      default:
            v = View.inflate(DroidMuni.this.cxt, R.layout.fake, null);          
            ((ViewPager) collection).addView(v,0);
        }
        
        return v;
      }
      
      /**
      * Remove a page for the given position.  The adapter is responsible
      * for removing the view from its container, although it only must ensure
      * this is done by the time it returns from {@link #finishUpdate()}.
      *
      * @param container The containing View from which the page will be removed.
      * @param position The page position to be removed.
      * @param object The same object that was returned by
      * {@link #instantiateItem(View, int)}.
      */
      @Override
      public void destroyItem(View collection, int position, Object view) {
              ((ViewPager) collection).removeView((View) view);
      }
      
      
      
      @Override
      public boolean isViewFromObject(View view, Object object) {
              return view.equals(object);
      }
      
      
      /**
      * Called when the a change in the shown pages has been completed.  At this
      * point you must ensure that all of the pages have actually been added or
      * removed from the container as appropriate.
      * @param container The containing View which is displaying this adapter's
      * page views.
      */
      @Override
    public void finishUpdate(View arg0) {
    }

    @Override
    public void restoreState(Parcelable arg0, ClassLoader arg1) {
    }

    @Override
    public Parcelable saveState() {
      return null;
    }

    @Override
    public void startUpdate(View arg0) {
    }
      
  }
}

class ExtendedStop extends TreeMap<String, TreeMap<String, Vector<Long>>> {
  private static final long serialVersionUID = -7109419099951901052L;
  String stop_title;

  public ExtendedStop(String t) {
    this.stop_title = t;
  }

  public String toString() {
    return stop_title;
  }
}

class LocationAwareStop implements Comparable<LocationAwareStop> {
  String stop_id;
  String title;
  double lat;
  double lon;
  float[] dist = new float[1];
  Vector<String> predictions = new Vector<String>();

  public LocationAwareStop(String id, String title, Location cl, double lat,
      double lon) {
    this.stop_id = id;
    this.lat = lat;
    this.lon = lon;
    this.title = title;
    Location.distanceBetween(lat, lon, cl.getLatitude(), cl.getLongitude(),
        dist);
  }

  public int compareTo(LocationAwareStop o) {
    if (o.dist[0] == this.dist[0])
      return 0;
    return (this.dist[0] < o.dist[0] ? -1 : 1);
  }

  public String toString() {
    return stop_id + ":" + title;
  }
}

