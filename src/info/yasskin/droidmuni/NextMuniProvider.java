package info.yasskin.droidmuni;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

public class NextMuniProvider extends ContentProvider {
  private static final ExecutorService s_executor =
      Executors.newCachedThreadPool();

  private static final long ONE_DAY = 24 * 3600 * 1000;
  private static final long ONE_MONTH = 30 * ONE_DAY;

  private static final String AUTHORITY =
      "info.yasskin.droidmuni.nextmuniprovider";
  public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

  public static final Uri ROUTES_URI =
      Uri.withAppendedPath(CONTENT_URI, "routes");
  public static final Uri DIRECTIONS_URI =
      Uri.withAppendedPath(CONTENT_URI, "directions");
  public static final Uri STOPS_URI =
      Uri.withAppendedPath(CONTENT_URI, "stops");
  public static final Uri PREDICTIONS_URI =
      Uri.withAppendedPath(CONTENT_URI, "predictions");

  private static final int NEXT_MUNI_ROUTES = 0;
  private static final int NEXT_MUNI_ROUTE_ID = 1;
  private static final int NEXT_MUNI_DIRECTIONS = 2;
  private static final int NEXT_MUNI_STOPS = 4;
  private static final int NEXT_MUNI_PREDICTIONS = 5;

  private static final UriMatcher sURLMatcher =
      new UriMatcher(UriMatcher.NO_MATCH);

  static {
    sURLMatcher.addURI(AUTHORITY, "routes", NEXT_MUNI_ROUTES);
    sURLMatcher.addURI(AUTHORITY, "routes/#", NEXT_MUNI_ROUTE_ID);
    sURLMatcher.addURI(AUTHORITY, "directions/*", NEXT_MUNI_DIRECTIONS);
    sURLMatcher.addURI(AUTHORITY, "stops/*/*", NEXT_MUNI_STOPS);
    sURLMatcher.addURI(AUTHORITY, "predictions/*/*/#", NEXT_MUNI_PREDICTIONS);
  }

  @Override
  public String getType(Uri uri) {
    switch (sURLMatcher.match(uri)) {
    case NEXT_MUNI_ROUTES:
      return "vnd.android.cursor.dir/vnd.yasskin.route";
    case NEXT_MUNI_ROUTE_ID:
      return "vnd.android.cursor.item/vnd.yasskin.route";
    case NEXT_MUNI_DIRECTIONS:
      return "vnd.android.cursor.dir/vnd.yasskin.direction";
    case NEXT_MUNI_STOPS:
      return "vnd.android.cursor.dir/vnd.yasskin.stop";
    case NEXT_MUNI_PREDICTIONS:
      return "vnd.android.cursor.dir/vnd.yasskin.prediction";
    default:
      throw new IllegalArgumentException("Unknown URI " + uri);
    }
  }

  private final DefaultHttpClient mClient = new DefaultHttpClient();
  private final Db db = new Db();
  private boolean m_someone_fetching_routes = false; // Guarded by db.

  @Override
  public boolean onCreate() {
    tryFetchRoutes(ForceCookieRequest.DO);
    return true;
  }

  enum ForceCookieRequest {
    DO, DONT
  };

  /**
   * If the database doesn't already have the list of routes, requests the list
   * from NextMUNI. Uses m_someone_fetching_routes to make sure we only send one
   * request at a time. If we start a call while another thread is fetching, and
   * they fail, we fail too in order to bound the maximum blocking time to a
   * single request timeout.
   * 
   * @param force
   *          If this is "FORCE...", always makes a request to get the cookies.
   *          Otherwise, only makes a request when we actually need to retrieve
   *          the route information.
   * @return The routes, or null if this or a concurrent request failed.
   */
  private Map<String, Db.Route> tryFetchRoutes(final ForceCookieRequest force) {
    final Map<String, Db.Route> routes = db.getRoutes();
    if (routes != null && force == ForceCookieRequest.DONT) {
      return routes;
    }
    final boolean routes_empty;
    final boolean someone_was_fetching_routes;
    synchronized (db) {
      routes_empty = db.getRoutes() == null;
      if (routes_empty) {
        someone_was_fetching_routes = m_someone_fetching_routes;
        while (m_someone_fetching_routes) {
          try {
            db.wait();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
          }
        }
        if (someone_was_fetching_routes) {
          if (force == ForceCookieRequest.DO) {
            getCookieAndRoutes(mIgnoreResponseHandler);
          }
          return db.getRoutes();
        } else {
          m_someone_fetching_routes = true;
        }
      }
    }
    try {
      if (routes_empty) {
        getCookieAndRoutes(new ResponseHandler<Boolean>() {
          public Boolean handleResponse(HttpResponse response)
              throws ClientProtocolException, IOException {
            synchronized (db) {
              if (db.getRoutes() == null) {
                String route_string =
                    new BasicResponseHandler().handleResponse(response);
                db.setRoutes(parseRoutes(route_string));
              }
              return true;
            }
          }
        });
      } else if (force == ForceCookieRequest.DO) {
        getCookieAndRoutes(mIgnoreResponseHandler);
      }
    } finally {
      synchronized (db) {
        m_someone_fetching_routes = false;
        db.notifyAll();
      }
    }
    return db.getRoutes();
  }

  /**
   * Downloads the list of routes to get its cookies. If the caller also needs
   * the actual routes, they can pass a ResponseHandler to process them.
   * 
   * @param handler
   *          What to do with the HTTP result after we've pulled cookies out of
   *          it.
   * @return true if the HTTP call succeeded.
   */
  private Boolean getCookieAndRoutes(ResponseHandler<Boolean> handler) {
    // Pull the cookies that let us request route information. We can
    // pick up the route names and IDs along with them
    HttpGet init_request =
        new HttpGet(NextMuniUriBuilder.buildRouteListUri("sf-muni").toString());
    try {
      Log.d("DroidMuni", "Requesting " + init_request.getURI());
      return mClient.execute(init_request, handler);
    } catch (ClientProtocolException e) {
      Toast.makeText(getContext(), "Cookie/route request failed." + e,
          Toast.LENGTH_SHORT);
      Log.e("DroidMuni", "Cookie/route request failed.", e);
      init_request.abort();
      return false;
    } catch (IOException e) {
      Toast.makeText(getContext(), "Cookie/route request failed." + e,
          Toast.LENGTH_SHORT);
      Log.e("DroidMuni", "Cookie/route request failed.", e);
      init_request.abort();
      return false;
    }
  }

  private static final ResponseHandler<Boolean> mIgnoreResponseHandler =
      new ResponseHandler<Boolean>() {
        public Boolean handleResponse(HttpResponse response)
            throws ClientProtocolException, IOException {
          return Boolean.TRUE;
        }
      };

  /**
   * Match one route name block. Blocks look like:
   * <tr>
   * <td>
   * 
   * <input type="checkbox" id="J" value="checkbox"
   * onClick="routeSelected('J')"> <script> if
   * (window.opener.isRouteVisible('J')) { document.getElementById('J').checked
   * = true; } </script></td>
   * <td>J-Church</td>
   * </tr>
   */
  private static final Pattern sRoutePattern =
      Pattern.compile(
          "<input type=\"checkbox\" id=\"([^\"]*)\".*?<td> ([^<]*) </td>",
          Pattern.DOTALL);

  private static Map<String, Db.Route> parseRoutes(String route_string) {
    Map<String, Db.Route> result = new HashMap<String, Db.Route>();
    Matcher m = sRoutePattern.matcher(route_string);
    for (int upstream_index = 0; m.find(); upstream_index++) {
      Db.Route route = new Db.Route(upstream_index, m.group(1), m.group(2));
      result.put(route.tag, route);
    }
    return result;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection,
      String[] selectionArgs, String sortOrder) {
    switch (sURLMatcher.match(uri)) {
    case NEXT_MUNI_ROUTES:
      Map<String, Db.Route> routes = tryFetchRoutes(ForceCookieRequest.DONT);
      if (routes == null) {
        return null;
      }
      List<Db.Route> route_list = new ArrayList<Db.Route>(routes.values());
      Collections.sort(route_list);
      String[] columns = { "_id", "tag", "description" };
      MatrixCursor result = new MatrixCursor(columns, routes.size());
      int id = 0;
      for (Db.Route route : route_list) {
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(id++);
        row.add(route.tag);
        row.add(route.description);
      }
      return result;
    case NEXT_MUNI_DIRECTIONS:
      return queryDirections("sf-muni", uri.getPathSegments().get(1));
    case NEXT_MUNI_STOPS:
      return queryStops("sf-muni", uri.getPathSegments().get(1),
          uri.getPathSegments().get(2));
    case NEXT_MUNI_PREDICTIONS:
      return queryPredictions("sf-muni", uri.getPathSegments().get(1),
          uri.getPathSegments().get(2), uri.getPathSegments().get(3));
    default:
      throw new IllegalArgumentException("Unknown URI " + uri);
    }
  }

  /**
   * Requests route data from NextBus. If the route data could be parsed,
   * returns the successful Parser. Otherwise, returns null.
   * 
   * @param agency_tag
   * @param route_tag
   * @return
   * @throws IllegalStateException
   */
  private RouteConfigParser getAndParseRoute(String agency_tag, String route_tag) {
    Uri request_uri =
        NextMuniUriBuilder.buildRouteDetailsUri(agency_tag, route_tag);
    return getAndParse(request_uri.toString(), RouteConfigParser.class);
  }

  /**
   * Requests a URI from NextBus, parses it with the specified parser, and
   * returns the parser if it succeeded.
   * 
   * @param request_uri
   * @return
   * @throws IllegalStateException
   */
  private <ParserT extends Parser> ParserT getAndParse(String request_uri,
      Class<ParserT> parserT) {
    return getAndParse(request_uri, parserT, false);
  }

  /**
   * Requests a URI from NextBus, parses it with the specified parser, and
   * returns the parser if it succeeded.
   * 
   * @param request_uri
   * @param already_retried_cookie
   *          TODO
   * @return
   * @throws IllegalStateException
   */
  private <ParserT extends Parser> ParserT getAndParse(String request_uri,
      Class<ParserT> parserT, boolean already_retried_cookie) {
    Log.i("DroidMuni", "Requesting " + request_uri);
    HttpGet dir_request = new HttpGet(request_uri);
    InputStream get_response;
    try {
      HttpResponse response = mClient.execute(dir_request);
      // TODO(jyasskin): Figure out how best to guarantee that the
      // response gets closed.
      get_response = response.getEntity().getContent();
    } catch (ClientProtocolException e) {
      Log.e("DroidMuni", "Cannot get directions: " + e);
      dir_request.abort();
      return null;
    } catch (IOException e) {
      Log.e("DroidMuni", "Cannot get directions: " + e);
      dir_request.abort();
      return null;
    }

    ParserT parser;
    try {
      parser = (ParserT) parserT.newInstance();
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException(
          "Passed " + parserT.getName()
              + " to getAndParse(), without an accessible constructor", e);
    } catch (InstantiationException e) {
      throw new IllegalArgumentException(
          "Passed " + parserT.getName()
              + " to getAndParse(), which cannot be constructed", e);
    }
    parser.parse(get_response);
    switch (parser.getResult()) {
    case SUCCESS:
      return parser;
    case NOT_DONE:
      Log.e("DroidMuni", "Parser didn't finish?!?");
      break;
    case MISSING_COOKIE:
      // If the cookie has expired, retry once by recursing. If the cookie
      // request fails or the parse fails a second time, complain to the user.
      if (already_retried_cookie || !getCookieAndRoutes(mIgnoreResponseHandler)) {
        Toast.makeText(getContext(), "Failed to get cookie", Toast.LENGTH_SHORT).show();
        break;
      } else {
        return getAndParse(request_uri, parserT, true);
      }
    case IO_ERROR:
    case PARSE_ERROR:
      Log.e("DroidMuni", "Failed to parse response");
      break;
    }
    return null;
  }

  /**
   * Fills in the database with details for the specified route.
   * 
   * @param agency_tag
   *          "sf-muni" (Eventually, maybe, the agency whose route we're
   *          querying for.)
   * @param route
   *          The route to query directions and stops for.
   * @param update_time_on_failure
   *          When getting the route fails, we set its "last update" time back
   *          to this value so the next query will try again.
   */
  private void fillDbForRoute(String agency_tag, Db.Route route) {
    // We use the directions_updated_ms field to make sure there's only one
    // outstanding request to fill the directions and stop data for a route at
    // a time.
    synchronized (route.directions_updated_ms) {
      if (route.directions_updated_ms.get() >= System.currentTimeMillis()
                                               - ONE_DAY) {
        // Someone else updated it first. Skip the work.
        return;
      }
      RouteConfigParser parser = getAndParseRoute(agency_tag, route.tag);
      if (parser == null) {
        return;
      }
      synchronized (db) {
        for (int i = 0; i < parser.getStops().size(); i++) {
          db.addStop(parser.getStops().valueAt(i), route.tag);
        }
      }

      Map<String, Db.Direction> dir_map = parser.getDirections();
      synchronized (route.directions) {
        route.directions.clear();
        route.directions.putAll(dir_map);
      }

      // Record that the directions and stops are now up to date.
      route.directions_updated_ms.set(System.currentTimeMillis());
    }
  }

  /**
   * If our cache is out of date, requeries NextMuni's website for direction and
   * stop data on the_route.
   */
  private void maybeUpdateRouteData(final String agency_tag,
      final Db.Route the_route) {
    final long now = System.currentTimeMillis();
    final long last_directions_update = the_route.directions_updated_ms.get();
    if (last_directions_update < now - ONE_MONTH) {
      // The data is too old, so block until we can update it.
      fillDbForRoute(agency_tag, the_route);
    } else if (last_directions_update < now - ONE_DAY) {
      // The data is a little stale, so update it in the background, but
      // return quickly with the cached data.
      s_executor.execute(new Runnable() {
        public void run() {
          fillDbForRoute(agency_tag, the_route);
        }
      });
    }
  }

  Cursor queryDirections(final String agency_tag, final String route_tag) {
    final Db.Route the_route = db.getRoute(route_tag);
    maybeUpdateRouteData(agency_tag, the_route);

    // Now use the local cache to return the directions list.
    ArrayList<Db.Direction> directions = new ArrayList<Db.Direction>();
    synchronized (the_route.directions) {
      directions.ensureCapacity(the_route.directions.size());
      for (Db.Direction direction : the_route.directions.values()) {
        if (direction.useForUI) {
          directions.add(direction);
        }
      }
    }
    Collections.sort(directions);
    String[] columns = { "_id", "route_tag", "tag", "title" };
    MatrixCursor result = new MatrixCursor(columns, directions.size());
    int id = 0;
    for (Db.Direction direction : directions) {
      MatrixCursor.RowBuilder row = result.newRow();
      row.add(id++);
      row.add(route_tag);
      row.add(direction.tag);
      row.add(direction.title);
    }
    return result;
  }

  private Cursor queryStops(String agency_tag, String route_tag,
      String direction_tag) {
    final Db.Route the_route = db.getRoute(route_tag);
    maybeUpdateRouteData(agency_tag, the_route);

    List<Db.Stop> stops = the_route.directions.get(direction_tag).stops;
    String[] columns =
        { "_id", "route_tag", "direction_tag", "stop_tag", "title", "lat",
         "lon" };
    MatrixCursor result = new MatrixCursor(columns);
    int id = 0;
    for (Db.Stop stop : stops) {
      MatrixCursor.RowBuilder row = result.newRow();
      row.add(id++);
      row.add(route_tag);
      row.add(direction_tag);
      row.add(stop.tag);
      row.add(stop.title);
      row.add(stop.lat);
      row.add(stop.lon);
    }
    return result;
  }

  private Cursor queryPredictions(String agency_tag, String route_tag,
      String direction_tag, String stop_tag) {
    Uri prediction_uri =
        NextMuniUriBuilder.buildMultiPredictionUri(agency_tag, stop_tag,
            route_tag);

    PredictionsParser parser =
        getAndParse(prediction_uri.toString(), PredictionsParser.class);
    if (parser == null) {
      return null;
    }

    List<Db.Prediction> predictions = parser.getPredictions();
    String[] columns =
        { "_id", "route_tag", "direction_tag", "stop_tag", "predicted_time",
         "endpoint" };
    MatrixCursor result = new MatrixCursor(columns);
    int id = 0;
    for (Db.Prediction prediction : predictions) {
      MatrixCursor.RowBuilder row = result.newRow();
      row.add(id++);
      row.add(route_tag);
      row.add(direction_tag);
      row.add(stop_tag);
      row.add(prediction.predicted_time);
      row.add(prediction.direction_tag);
    }
    return result;
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException("Cannot insert into NextMUNI");
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection,
      String[] selectionArgs) {
    throw new UnsupportedOperationException("Cannot update NextMUNI");
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException("Cannot delete from NextMUNI");
  }
}
