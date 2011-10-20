package info.yasskin.droidmuni;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Stores the cached database of NextBus route and stop information.
 * 
 * @author Jeffrey Yasskin <jyasskin@gmail.com>
 * 
 */
final class Db extends SQLiteOpenHelper {
  public Db(Context context) {
    super(context, "NextMUNIDb", null, 3);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.beginTransaction();
    try {
      db.execSQL("CREATE TABLE RoutesUpdated (last_update INTEGER)");

      db.execSQL("CREATE TABLE Routes ("
                 + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                 + "tag TEXT UNIQUE," + "upstream_index INTEGER,"
                 + "description TEXT,"
                 + "last_direction_update_ms INTEGER DEFAULT 0)");

      db.execSQL("CREATE TABLE Directions ("
                 + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                 + "route_id INTEGER REFERENCES Routes(_id)," + "tag TEXT,"
                 + "title TEXT," + "name TEXT,"
                 // use_for_ui is actually 0 or 1 for false or true.
                 + "use_for_ui INTEGER," + "UNIQUE(route_id, tag))");

      db.execSQL("CREATE TABLE Stops (" + "_id INTEGER PRIMARY KEY,"
                 + "tag INTEGER," + "title TEXT," + "latitude DOUBLE,"
                 + "longitude DOUBLE)");

      db.execSQL("CREATE TABLE DirectionStops ("
                 + "direction INTEGER REFERENCES Directions(_id),"
                 + "stop INTEGER REFERENCES Stops(_id),"
                 + "stop_order INTEGER," + "UNIQUE(direction, stop_order))");

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.beginTransaction();
    try {
      db.execSQL("DROP TABLE IF EXISTS RoutesUpdate");
      db.execSQL("DROP TABLE IF EXISTS Routes");
      db.execSQL("DROP TABLE IF EXISTS Directions");
      db.execSQL("DROP TABLE IF EXISTS Stops");
      db.execSQL("DROP TABLE IF EXISTS DirectionStops");
      db.execSQL("DROP TABLE IF EXISTS StopRoutes");

      onCreate(db);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Deletes the contents of all tables and sets things up as if the application
   * had just been installed.
   */
  public void eraseEverything() {
    onUpgrade(getWritableDatabase(), 0, 0);
  }

  public static class Route {
    public Route(long id, int upstream_index, String tag, String description,
        long directions_updated_ms) {
      this.id = id;
      this.upstream_index = upstream_index;
      this.tag = tag;
      this.description = description;
      this.directions_updated_ms = directions_updated_ms;
    }

    public final long id;
    public final int upstream_index;
    public final String tag;
    public final String description;
    /**
     * The result of System.currentTimeMillis() from the last time the
     * directions were updated.
     */
    public final long directions_updated_ms;
  }

  public static class Direction {
    public Direction(long id, String tag, String title, String name,
        boolean useForUI, List<Db.Stop> stops) {
      this.id = id;
      this.tag = tag;
      this.title = title;
      this.name = name;
      this.useForUI = useForUI;
      this.stops = Collections.unmodifiableList(stops);
    }

    public final long id;
    /**
     * For example, "71__OB6".
     */
    public final String tag;
    /**
     * For example, "Outbound to 48th Avenue".
     */
    public final String title;
    /**
     * For example, "Outbound".
     */
    public final String name;
    public final boolean useForUI;
    public final List<Db.Stop> stops;
  }

  public static class Stop {
    public Stop(int id, int tag, String title, double lat, double lon) {
      this.id = id;
      this.tag = tag;
      this.title = title;
      this.lat = lat;
      this.lon = lon;
    }

    public final int id;
    public final int tag;
    public final String title;
    public final double lat;
    public final double lon;
  }

  public static class Prediction implements Comparable<Prediction> {
    public Prediction(String route_tag, long predicted_time,
        boolean is_departure, String direction_tag, String block) {
      this.route_tag = route_tag;
      this.predicted_time = predicted_time;
      this.is_departure = is_departure;
      this.direction_tag = direction_tag;
      this.block = block;
    }

    /**
     * Number of milliseconds past the Unix epoch, GMT
     */
    public final long predicted_time;
    /**
     * ???
     */
    public final boolean is_departure;
    /**
     * The tag of a Route instance, a bus from which will arrive at the stop at
     * this Prediction's time.
     */
    public final String route_tag;
    /**
     * The tag of a Direction instance, to use to describe the endpoint of the
     * route. This can differ from the direction the user looked up when
     * different busses stop in different places.
     */
    public final String direction_tag;
    /**
     * ???
     */
    public final String block;

    /**
     * Compares based on the predicted time.
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Prediction another) {
      if (this.predicted_time < another.predicted_time)
        return -1;
      if (this.predicted_time > another.predicted_time)
        return 1;
      int cmp = this.route_tag.compareTo(another.route_tag);
      if (cmp != 0)
        return cmp;
      return this.direction_tag.compareTo(another.direction_tag);
    }
  }

  /**
   * Leaves new_routes in an undetermined state.
   */
  public void setRoutes(Map<String, Route> new_routes) {
    SQLiteDatabase tables = getWritableDatabase();
    tables.beginTransaction();
    try {
      String[] COLUMNS =
          new String[] { "_id", "tag", "upstream_index", "description" };
      Cursor old_routes =
          tables.query("Routes", COLUMNS, null, null, null, null, null);
      try {
        for (old_routes.moveToFirst(); !old_routes.isAfterLast(); old_routes.moveToNext()) {
          final long id = old_routes.getLong(0);
          final String tag = old_routes.getString(1);
          final int upstream_index = old_routes.getInt(2);
          final String description = old_routes.getString(3);

          final Route new_route = new_routes.remove(tag);
          if (new_route == null) {
            tables.delete("Routes", "_id == ?", new String[] { id + "" });
          } else if (upstream_index != new_route.upstream_index
                     || !description.equals(new_route.description)) {
            ContentValues new_values = new ContentValues(2);
            new_values.put("upstream_index", new_route.upstream_index);
            new_values.put("description", new_route.description);
            tables.update("Routes", new_values, "_id == ?",
                new String[] { id + "" });
          }
        }
        for (Route new_route : new_routes.values()) {
          ContentValues new_values = new ContentValues(3);
          new_values.put("tag", new_route.tag);
          new_values.put("upstream_index", new_route.upstream_index);
          new_values.put("description", new_route.description);
          tables.insertOrThrow("Routes", "tag", new_values);
        }
      } finally {
        old_routes.close();
      }

      // Update the "last updated" time to now.
      tables.delete("RoutesUpdated", null, null);
      ContentValues values = new ContentValues(1);
      values.put("last_update", System.currentTimeMillis());
      tables.insertOrThrow("RoutesUpdated", null, values);

      tables.setTransactionSuccessful();
    } finally {
      tables.endTransaction();
    }
  }

  public Route getRoute(String route_tag) {
    SQLiteDatabase tables = getReadableDatabase();
    final String[] COLUMNS =
        { "_id", "tag", "upstream_index", "description",
         "last_direction_update_ms" };
    Cursor routes =
        tables.query("Routes", COLUMNS, "tag == ?", new String[] { route_tag },
            null, null, null);
    try {
      routes.moveToFirst();
      Route result =
          new Route(routes.getLong(0), routes.getInt(2), routes.getString(1),
              routes.getString(3), routes.getLong(4));
      return result;
    } finally {
      routes.close();
    }
  }

  public boolean hasRoutes() {
    SQLiteDatabase tables = getReadableDatabase();
    return DatabaseUtils.queryNumEntries(tables, "Routes") > 0;
  }

  /**
   * Looks up the time the routes were last updated and returns whether that's
   * new enough.
   * 
   * @param time_millis
   *          The oldest time the routes can have been updated for this method
   *          to return true.
   */
  public boolean routesNewerThan(long time_millis) {
    SQLiteDatabase tables = getReadableDatabase();
    Cursor updated =
        tables.query("RoutesUpdated", null, null, null, null, null, null);
    try {
      if (!updated.moveToFirst()) {
        // Empty Cursor means routes were never updated.
        return false;
      }
      long update_time = updated.getLong(0);
      return update_time >= time_millis;
    } finally {
      updated.close();
    }
  }

  /**
   * Updates the route whose _id is route_id to have the directions in
   * 'directions'. After this call, directions has an undefined set of elements.
   */
  public void setDirections(long route_id, Map<String, Direction> new_directions) {
    SQLiteDatabase tables = getWritableDatabase();
    tables.beginTransaction();
    try {
      String[] COLUMNS =
          new String[] { "_id", "tag", "title", "name", "use_for_ui" };
      Cursor old_directions =
          tables.query("Directions", COLUMNS, "route_id == ?",
              new String[] { route_id + "" }, null, null, null);
      try {
        final ContentValues new_values = new ContentValues(5);
        for (old_directions.moveToFirst(); !old_directions.isAfterLast(); old_directions.moveToNext()) {
          final long id = old_directions.getLong(0);
          final String tag = old_directions.getString(1);
          final String title = old_directions.getString(2);
          final String name = old_directions.getString(3);
          final boolean use_for_ui = old_directions.getInt(4) != 0;

          final Direction new_direction = new_directions.remove(tag);
          if (new_direction == null) {
            tables.delete("Directions", "_id == ?", new String[] { id + "" });
            continue;
          } else if (!title.equals(new_direction.title)
                     || !name.equals(new_direction.name)
                     || use_for_ui != new_direction.useForUI) {
            new_values.clear();
            new_values.put("title", new_direction.title);
            new_values.put("name", new_direction.name);
            new_values.put("use_for_ui", new_direction.useForUI);
            tables.update("Directions", new_values, "_id == ?",
                new String[] { id + "" });
          }

          updateDirectionStops(tables, id, new_direction);
        }
        for (Direction new_direction : new_directions.values()) {
          new_values.clear();
          new_values.put("route_id", route_id);
          new_values.put("tag", new_direction.tag);
          new_values.put("title", new_direction.title);
          new_values.put("name", new_direction.name);
          new_values.put("use_for_ui", new_direction.useForUI);
          long id = tables.insertOrThrow("Directions", null, new_values);
          updateDirectionStops(tables, id, new_direction);
        }
      } finally {
        old_directions.close();
      }

      tables.setTransactionSuccessful();
    } finally {
      tables.endTransaction();
    }
  }

  private void updateDirectionStops(SQLiteDatabase tables, long direction_id,
      Direction new_direction) {
    tables.delete("DirectionStops", "direction == ?",
        new String[] { direction_id + "" });

    // Going to be inserting a bunch into DirectionStops...
    DatabaseUtils.InsertHelper stop_inserter =
        new DatabaseUtils.InsertHelper(tables, "DirectionStops");
    try {
      int direction_index = stop_inserter.getColumnIndex("direction");
      int stop_index = stop_inserter.getColumnIndex("stop");
      int stop_order_index = stop_inserter.getColumnIndex("stop_order");
      for (int i = 0; i < new_direction.stops.size(); ++i) {
        stop_inserter.prepareForInsert();
        stop_inserter.bind(direction_index, direction_id);
        stop_inserter.bind(stop_index, new_direction.stops.get(i).id);
        stop_inserter.bind(stop_order_index, i);
        stop_inserter.execute();
      }
    } finally {
      stop_inserter.close();
    }
  }

  /**
   * Adds 'stop' to the set of stops if it's not already present.
   */
  public synchronized void addStop(Stop stop, long route_id) {
    final SQLiteDatabase tables = getWritableDatabase();
    tables.beginTransaction();
    try {
      final ContentValues values = new ContentValues(4);
      final Cursor existing_stop =
          tables.query("Stops", new String[] { "tag", "title", "latitude",
                                              "longitude" }, "_id == ?",
              new String[] { stop.id + "" }, null, null, null);
      try {
        if (existing_stop.getCount() == 0) {
          values.clear();
          values.put("_id", stop.id);
          values.put("tag", stop.tag);
          values.put("title", stop.title);
          values.put("latitude", stop.lat);
          values.put("longitude", stop.lon);
          tables.insertOrThrow("Stops", null, values);
        } else {
          existing_stop.moveToFirst();
          if (existing_stop.getInt(0) != stop.tag
              || !existing_stop.getString(1).equals(stop.title)
              || existing_stop.getDouble(2) != stop.lat
              || existing_stop.getDouble(3) != stop.lon) {
            values.clear();
            values.put("tag", stop.tag);
            values.put("title", stop.title);
            values.put("latitude", stop.lat);
            values.put("longitude", stop.lon);
            tables.update("Stops", values, "_id == ?", new String[] { stop.id
                                                                      + "" });
          }
        }
      } finally {
        existing_stop.close();
      }

      tables.setTransactionSuccessful();
    } finally {
      tables.endTransaction();
    }
  }
}
