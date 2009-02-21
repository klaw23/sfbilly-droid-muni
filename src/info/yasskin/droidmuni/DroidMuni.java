package info.yasskin.droidmuni;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

public class DroidMuni extends Activity {
  private final Handler m_handler = new Handler();
  /**
   * Set once in onCreate() and never modified again.
   */
  private LocationManager m_location_manager;

  private static final ExecutorService s_executor =
      Executors.newCachedThreadPool();

  static final int REDRAW_INTERVAL_MS = 30000;
  static final int REPREDICT_INTERVAL_MS = 2 * 60000;

  private String m_saved_line_selected;
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

  private static final Cursor m_loading_lines =
      makeConstantCursor("description", "Loading lines...");
  private static final Cursor m_line_request_failed =
      makeConstantCursor("description", "Line request failed", "Retry");
  private static final Cursor m_loading_directions =
      makeConstantCursor("title", "Loading directions...");
  private static final Cursor m_directions_request_failed =
      makeConstantCursor("title", "Direction request failed", "Retry");
  private static final Cursor m_loading_stops =
      makeConstantCursor("title", "Loading stops...");
  private static final Cursor m_stop_request_failed =
      makeConstantCursor("title", "Stop request failed", "Retry");
  private static final Cursor m_loading_predictions =
      makeConstantCursor("predicted_time", "Loading predictions...");
  private static final Cursor m_prediction_request_failed =
      makeConstantCursor("predicted_time", "Prediction request failed");
  private static final Cursor m_no_predictions =
      makeConstantCursor("predicted_time", "No predictions");

  private static Cursor makeConstantCursor(String column_name, String... rows) {
    MatrixCursor result =
        new MatrixCursor(new String[] { "_id", column_name }, rows.length);
    int id = 0;
    for (String row : rows) {
      result.addRow(new Object[] { id++, row });
    }
    return result;
  }

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    m_location_manager = (LocationManager) getSystemService(LOCATION_SERVICE);

    SharedPreferences pref = getPreferences(MODE_PRIVATE);
    this.m_saved_line_selected = safeGet(pref, String.class, "line", "");

    this.setContentView(R.layout.main);

    m_line_spinner = (Spinner) findViewById(R.id.line);
    m_line_adapter =
        setupSpinner(m_line_spinner, "description", mLineClickedHandler);
    queryRoutes();

    m_direction_spinner = (Spinner) findViewById(R.id.direction);
    m_direction_adapter =
        setupSpinner(m_direction_spinner, "title", mDirectionClickedHandler);

    m_stop_spinner = (Spinner) findViewById(R.id.stop);
    m_stop_adapter = setupSpinner(m_stop_spinner, "title", mStopClickedHandler);

    m_prediction_list = (ListView) findViewById(R.id.predictions);
    m_predictions_adapter =
        new SimpleCursorAdapter(this, android.R.layout.simple_list_item_1,
            null,
            // TODO: Show the destination, but only when it's not
            // totally
            // determined by the route (e.x. 31 Inbound & 38
            // Outbound).
            new String[] { "predicted_time" }, new int[] { android.R.id.text1 });
    m_predictions_adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
      public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
        if (!(view instanceof TextView)) {
          return false;
        }
        TextView textview = (TextView) view;
        if (cursor == m_loading_predictions
            || cursor == m_prediction_request_failed
            || cursor == m_no_predictions) {
          textview.setText(cursor.getString(columnIndex));
          return true;
        }
        long expected_arrival = cursor.getLong(columnIndex);
        long now = System.currentTimeMillis();
        long delta_minutes = (expected_arrival - now) / 60000;
        String ago = "";
        String plural = "";
        if (delta_minutes < 0) {
          ago = " ago";
          delta_minutes = -delta_minutes;
        }
        if (delta_minutes != 1) {
          plural = "s";
        }
        textview.setText(delta_minutes + " minute" + plural + ago);
        return true;
      }
    });
    m_prediction_list.setAdapter(m_predictions_adapter);
  }

  /**
   * 
   */
  private void queryRoutes() {
    resetCursor(m_line_adapter, m_loading_lines);
    Future<Cursor> line_cursor =
        managedBackgroundQuery(NextMuniProvider.ROUTES_URI, null, null, null,
            null);
    changeCursorWhenReadyAndThen(m_line_adapter, line_cursor,
        m_line_request_failed, new Runnable() {
          public void run() {
            if (m_saved_line_selected != "") {
              final Cursor cursor = m_line_adapter.getCursor();
              final int tag_index = cursor.getColumnIndexOrThrow("tag");
              for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                if (cursor.getString(tag_index).equals(m_saved_line_selected)) {
                  m_line_spinner.setSelection(cursor.getPosition());
                  break;
                }
              }
            }
          }
        });
  }

  /**
   * Retrieves the preference named 'name' of type 'T' from 'pref'. If the
   * preference is not present or has the wrong type, returns 'defalt'.
   */
  @SuppressWarnings("unchecked")
  private static <T> T safeGet(SharedPreferences pref, Class<T> type,
      String name, T defalt) {
    Object value = pref.getAll().get(name);
    if (value == null || !type.isInstance(value)) {
      return defalt;
    }
    return (T) value;
  }

  @Override
  protected void onStart() {
    super.onStart();
    // Start the re-drawing loop for the prediction list.
    m_handler.postDelayed(mRedrawPredictionList, REDRAW_INTERVAL_MS);
  }

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

    SharedPreferences.Editor editor = this.getPreferences(MODE_PRIVATE).edit();
    editor.putString("line", this.m_saved_line_selected);
    editor.commit();
  }

  @Override
  protected void onStop() {
    super.onStop();
    // No need to re-draw when we're invisible.
    m_handler.removeCallbacks(mRedrawPredictionList);
  }

  /**
   * @param spinner
   * @param display_column
   *          TODO
   * @param selection_handler
   *          TODO
   * @return
   */
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

  /**
   * Runs a query in the background and returns a Future representing its
   * result.
   * 
   * @see Activity.managedQuery
   */
  private Future<Cursor> managedBackgroundQuery(final Uri uri,
      final String[] projection, final String selection,
      final String[] selectionArgs, final String sortOrder) {
    return s_executor.submit(new Callable<Cursor>() {
      public Cursor call() {
        return managedQuery(uri, projection, selection, selectionArgs,
            sortOrder);
      }
    });
  }

  /**
   * Runs cursor.get() and handles exceptions appropriately for the result of
   * Activity.managedQuery().
   */
  private Cursor getManagedQueryFuture(final Future<Cursor> cursor) {
    try {
      return cursor.get();
    } catch (CancellationException e) {
      // If the Future was cancelled, don't fill in
      // the cursor with its result.
      return null;
    } catch (InterruptedException e) {
      // And if the thread was interrupted, just
      // return.
      return null;
    } catch (ExecutionException e) {
      Log.e("DroidMuni", "managedQuery unexpectedly threw" + " an exception", e);
      return null;
    }
  }

  /**
   * Waits for 'cursor' to be ready in a separate thread, then transfers back
   * onto the UI threads, sets the received cursor into 'adapter', and calls
   * and_then.run().
   */
  private void changeCursorWhenReadyAndThen(final CursorAdapter adapter,
      final Future<Cursor> cursor, final Cursor on_failure,
      final Runnable and_then) {
    s_executor.execute(new Runnable() {
      public void run() {
        final Cursor result = getManagedQueryFuture(cursor);
        if (result == null) {
          m_handler.post(new Runnable() {
            public void run() {
              resetCursor(adapter, on_failure);
            }
          });
        } else {
          m_handler.post(new Runnable() {
            public void run() {
              resetCursor(adapter, result);
              if (and_then != null) {
                and_then.run();
              }
            }
          });
        }
      }
    });
  }

  /**
   * Sets adapter's Cursor to new_cursor in a way that will make the
   * AdapterView's ItemSelectedListener get called.
   */
  private void resetCursor(final CursorAdapter adapter, final Cursor new_cursor) {
    adapter.changeCursor(null);
    adapter.changeCursor(new_cursor);
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
          m_saved_line_selected = selected_route;
          resetCursor(m_direction_adapter, m_loading_directions);
          Future<Cursor> direction_data =
              managedBackgroundQuery(Uri.withAppendedPath(
                  NextMuniProvider.DIRECTIONS_URI, selected_route), null, null,
                  null, null);
          changeCursorWhenReadyAndThen(m_direction_adapter, direction_data,
              m_directions_request_failed, new Runnable() {
                public void run() {
                  final String prev_direction =
                      m_prev_directions.get(selected_route);
                  if (prev_direction == null) {
                    // If we've never seen this route before, stay on the first
                    // direction choice.
                    return;
                  }
                  final Cursor directions = m_direction_adapter.getCursor();
                  final int tag_index = directions.getColumnIndexOrThrow("tag");
                  for (directions.moveToFirst(); !directions.isAfterLast(); directions.moveToNext()) {
                    if (prev_direction.equals(directions.getString(tag_index))) {
                      m_direction_spinner.setSelection(directions.getPosition());
                    }
                  }
                }
              });
        }

        public void onNothingSelected(AdapterView<?> parent) {
          m_direction_adapter.changeCursor(null);
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

          resetCursor(m_stop_adapter, m_loading_stops);
          Future<Cursor> stop_data =
              managedBackgroundQuery(Uri.withAppendedPath(
                  NextMuniProvider.STOPS_URI, selected_route + "/"
                                              + selected_direction), null,
                  null, null, null);
          changeCursorWhenReadyAndThen(m_stop_adapter, stop_data,
              m_stop_request_failed, mSetStopToNearest);
        }

        public void onNothingSelected(AdapterView<?> parent) {
          m_stop_adapter.changeCursor(null);
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
      final Cursor stop_cursor = m_stop_adapter.getCursor();
      final int lat_index = stop_cursor.getColumnIndexOrThrow("lat");
      final int lon_index = stop_cursor.getColumnIndexOrThrow("lon");
      final float[] results = new float[1];

      double best_distance = Double.POSITIVE_INFINITY;
      int best_position = Spinner.INVALID_POSITION;
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
      m_stop_spinner.setSelection(best_position);
    }
  };

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
          String selected_route = parent_item.getString(1);
          String selected_direction = parent_item.getString(2);
          String selected_stop = parent_item.getString(3);
          resetCursor(m_predictions_adapter, m_loading_predictions);
          m_predictions_shown = false;
          m_handler.removeCallbacks(mRequeryPredictions);
          Future<Cursor> prediction_data =
              managedBackgroundQuery(Uri.withAppendedPath(
                  NextMuniProvider.PREDICTIONS_URI, selected_route + "/"
                                                    + selected_direction + "/"
                                                    + selected_stop), null,
                  null, null, null);
          changeCursorWhenReadyAndThen(m_predictions_adapter, prediction_data,
              m_prediction_request_failed, new Runnable() {
                public void run() {
                  Cursor predictions = m_predictions_adapter.getCursor();
                  if (predictions.getCount() == 0) {
                    m_predictions_adapter.changeCursor(m_no_predictions);
                  } else {
                    m_predictions_shown = true;
                    m_handler.postDelayed(mRequeryPredictions,
                        REPREDICT_INTERVAL_MS);
                  }
                }
              });
        }

        public void onNothingSelected(AdapterView<?> parent) {
          m_predictions_shown = false;
          m_handler.removeCallbacks(mRequeryPredictions);
          m_predictions_adapter.changeCursor(null);
        }
      };

  private final Runnable mRedrawPredictionList = new Runnable() {
    public void run() {
      m_prediction_list.invalidate();

      m_handler.postDelayed(this, REDRAW_INTERVAL_MS);
    }
  };

  private boolean m_predictions_shown = false;
  private final Runnable mRequeryPredictions = new Runnable() {
    public void run() {
      m_predictions_adapter.getCursor().requery();

      m_handler.postDelayed(this, REPREDICT_INTERVAL_MS);
    }
  };
}