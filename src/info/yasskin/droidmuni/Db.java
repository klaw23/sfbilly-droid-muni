package info.yasskin.droidmuni;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import android.util.SparseArray;

/**
 * Stores the cached database of NextBus route and stop information.
 * 
 * @author Jeffrey Yasskin <jyasskin@gmail.com>
 * 
 */
class Db {
  public static class Route implements Comparable<Route> {
    public Route(int upstream_index, String tag, String description) {
      this.upstream_index = upstream_index;
      this.tag = tag;
      this.description = description;
      this.directions =
          Collections.synchronizedMap(new HashMap<String, Direction>());
    }

    public final int upstream_index;
    public final String tag;
    public final String description;
    /**
     * The result of System.currentTimeMillis() from the last time the
     * directions were updated.
     */
    public final AtomicLong directions_updated_ms = new AtomicLong(0);
    /**
     * A synchronized map, so all accesses are thread-safe, and you can use
     * synchronized(directions) to do updates.
     */
    public final Map<String, Db.Direction> directions;

    public int compareTo(Route another) {
      if (this.upstream_index < another.upstream_index)
        return -1;
      if (this.upstream_index > another.upstream_index)
        return 1;
      int comparison = this.tag.compareTo(another.tag);
      if (comparison != 0)
        return comparison;
      return this.description.compareTo(another.description);
    }
  }

  public static class Direction implements Comparable<Direction> {
    public Direction(int upstream_index, String tag, String title, String name,
        boolean useForUI, List<Db.Stop> stops) {
      this.upstream_index = upstream_index;
      this.tag = tag;
      this.title = title;
      this.name = name;
      this.useForUI = useForUI;
      this.stops = Collections.unmodifiableList(stops);
    }

    public final int upstream_index;
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

    public int compareTo(Direction another) {
      if (this.upstream_index < another.upstream_index)
        return -1;
      if (this.upstream_index > another.upstream_index)
        return 1;
      return this.tag.compareTo(another.tag);
    }
  }

  public static class Stop {
    public Stop(int tag, String title, double lat, double lon) {
      this.tag = tag;
      this.title = title;
      this.lat = lat;
      this.lon = lon;
    }

    public final int tag;
    public final String title;
    public final double lat;
    public final double lon;

    /**
     * Returns a copy of the set of routes that stop at this stop.
     */
    public synchronized List<String> routesThatStopHere() {
      return new ArrayList<String>(routes_that_stop_here);
    }

    public synchronized void addRoute(String route_tag) {
      routes_that_stop_here.add(route_tag);
    }

    private final Set<String> routes_that_stop_here = new HashSet<String>();
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

  private final Map<String, Route> mRoutes = new HashMap<String, Route>();
  /**
   * Stop ID -> Stop
   */
  private final SparseArray<Stop> mStops = new SparseArray<Stop>();

  // TODO: Change this to setRoutes() for thread-safety.
  public synchronized void addRoute(Route newRoute) {
    this.mRoutes.put(newRoute.tag, newRoute);
  }

  public synchronized Route getRoute(String route_tag) {
    return mRoutes.get(route_tag);
  }

  // TODO: Not thread-safe...
  public Map<String, Route> getRoutes() {
    return Collections.unmodifiableMap(mRoutes);
  }

  public synchronized Stop getStop(int stop_tag) {
    return mStops.get(stop_tag);
  }

  /**
   * Adds 'stop' to the set of stops if it's not already present, and marks it
   * as serving route_tag.
   * 
   * @return the canonical Stop object for this stop.
   */
  public synchronized Stop addStop(Stop stop, String route_tag) {
    Stop current = mStops.get(stop.tag);
    if (current == null) {
      mStops.put(stop.tag, stop);
      current = stop;
    }
    current.addRoute(route_tag);
    return current;
  }
}
