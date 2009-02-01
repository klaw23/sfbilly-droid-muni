package info.yasskin.droidmuni;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
	private static final String AUTHORITY = "info.yasskin.droidmuni.nextmuniprovider";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

	public static final Uri ROUTES_URI = Uri.withAppendedPath(CONTENT_URI,
			"routes");
	public static final Uri DIRECTIONS_URI = Uri.withAppendedPath(CONTENT_URI,
			"directions");
	public static final Uri STOPS_URI = Uri.withAppendedPath(CONTENT_URI,
			"stops");
	public static final Uri PREDICTIONS_URI = Uri.withAppendedPath(CONTENT_URI,
			"predictions");

	private static final int NEXT_MUNI_ROUTES = 0;
	private static final int NEXT_MUNI_ROUTE_ID = 1;
	private static final int NEXT_MUNI_DIRECTIONS = 2;
	private static final int NEXT_MUNI_STOPS = 4;
	private static final int NEXT_MUNI_PREDICTIONS = 5;

	private static final UriMatcher sURLMatcher = new UriMatcher(
			UriMatcher.NO_MATCH);

	static {
		sURLMatcher.addURI(AUTHORITY, "routes", NEXT_MUNI_ROUTES);
		sURLMatcher.addURI(AUTHORITY, "routes/#", NEXT_MUNI_ROUTE_ID);
		sURLMatcher.addURI(AUTHORITY, "directions/*", NEXT_MUNI_DIRECTIONS);
		sURLMatcher.addURI(AUTHORITY, "stops/*/*", NEXT_MUNI_STOPS);
		sURLMatcher.addURI(AUTHORITY, "predictions/*/*/*",
				NEXT_MUNI_PREDICTIONS);
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
	private boolean mHaveCookie = false;
	private final Db db = new Db();

	@Override
	public synchronized boolean onCreate() {
		final Map<String, Db.Route> routes = db.getRoutes();
		if (!mHaveCookie || routes.isEmpty()) {
			return getCookieAndRoutes(new ResponseHandler<Boolean>() {
				public Boolean handleResponse(HttpResponse response)
						throws ClientProtocolException, IOException {
					if (routes.isEmpty()) {
						String route_string = new BasicResponseHandler()
								.handleResponse(response);
						parseRoutes(db, route_string);
					}
					return true;
				}
			});
		}

		return true;
	}

	/**
	 * Downloads the list of routes to get its cookies. If the caller also needs
	 * the actual routes, they can pass a ResponseHandler to process them.
	 * 
	 * @param handler
	 *            What to do with the HTTP result after we've pulled cookies out
	 *            of it.
	 * @return true if the HTTP call succeeded.
	 */
	private Boolean getCookieAndRoutes(ResponseHandler<Boolean> handler) {
		// Pull the cookies that let us request route information. We can
		// pick up the route names and IDs along with them
		HttpGet init_request = new HttpGet(getContext().getString(
				R.string.route_list_uri));
		try {
			Log.d("DroidMuni", "Requesting " + init_request.getURI() + "\n"
					+ Arrays.toString(init_request.getAllHeaders()));
			return mClient.execute(init_request, handler);
		} catch (ClientProtocolException e) {
			Log.e("DroidMuni", "Cookie/route request failed.", e);
			init_request.abort();
			return false;
		} catch (IOException e) {
			Log.e("DroidMuni", "Cookie/route request failed.", e);
			init_request.abort();
			return false;
		}
	}

	private static class IgnoreResponseHandler implements
			ResponseHandler<Boolean> {
		public Boolean handleResponse(HttpResponse response)
				throws ClientProtocolException, IOException {
			return Boolean.TRUE;
		}
	}

	/**
	 * Match one route name block. Blocks look like:
	 * <tr>
	 * <td>
	 * 
	 * <input type="checkbox" id="J" value="checkbox"
	 * onClick="routeSelected('J')"> <script> if
	 * (window.opener.isRouteVisible('J')) {
	 * document.getElementById('J').checked = true; } </script></td>
	 * <td>J-Church</td>
	 * </tr>
	 */
	private static final Pattern sRoutePattern = Pattern.compile(
			"<input type=\"checkbox\" id=\"([^\"]*)\".*?<td> ([^<]*) </td>",
			Pattern.DOTALL);

	private static void parseRoutes(Db db, String route_string) {
		Matcher m = sRoutePattern.matcher(route_string);
		for (int upstream_index = 0; m.find(); upstream_index++) {
			db.addRoute(new Db.Route(upstream_index, m.group(1), m.group(2)));
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		switch (sURLMatcher.match(uri)) {
		case NEXT_MUNI_ROUTES:
			Map<String, Db.Route> routes = db.getRoutes();
			List<Db.Route> route_list = new ArrayList<Db.Route>(routes.values());
			Collections.sort(route_list);
			String[] columns = { "_id", "name", "description" };
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
			return queryStops("sf-muni", uri.getPathSegments().get(1), uri
					.getPathSegments().get(2));
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
	private RouteConfigParser getAndParseRoute(String agency_tag,
			String route_tag) {
		String request_uri = getContext().getString(R.string.route_config_uri,
				agency_tag, route_tag);
		return getAndParse(request_uri, RouteConfigParser.class);
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
		Log.i(AUTHORITY, "Requesting " + request_uri);
		HttpGet dir_request = new HttpGet(request_uri);
		InputStream get_response;
		try {
			HttpResponse response = mClient.execute(dir_request);
			// TODO(jyasskin): Figure out how best to guarantee that the
			// response gets closed.
			get_response = response.getEntity().getContent();
		} catch (ClientProtocolException e) {
			Log.e(AUTHORITY, "Cannot get directions: " + e);
			dir_request.abort();
			return null;
		} catch (IOException e) {
			Log.e(AUTHORITY, "Cannot get directions: " + e);
			dir_request.abort();
			return null;
		}

		ParserT parser;
		try {
			parser = (ParserT) parserT.newInstance();
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Passed " + parserT.getName()
					+ " to getAndParse(), without an accessible constructor", e);
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Passed " + parserT.getName()
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
			if (!getCookieAndRoutes(new IgnoreResponseHandler())) {
				Log.e("DroidMuni", "Failed to get cookie");
				break;
			} else {
				Toast
						.makeText(getContext(),
								"Cookie expired. Please try again.",
								Toast.LENGTH_SHORT).show();
				break;
			}
		case IO_ERROR:
		case PARSE_ERROR:
			Log.e("DroidMuni", "Failed to parse response");
			break;
		}
		return null;
	}

	Cursor queryDirections(String agency_tag, String route_tag) {
		RouteConfigParser parser = getAndParseRoute(agency_tag, route_tag);
		if (parser == null) {
			return null;
		}

		List<Db.Direction> directions = new ArrayList<Db.Direction>(parser
				.getDirections().values());
		Collections.sort(directions);
		String[] columns = { "_id", "route_tag", "tag", "title" };
		MatrixCursor result = new MatrixCursor(columns);
		int id = 0;
		for (Db.Direction direction : directions) {
			if (!direction.useForUI) {
				continue;
			}
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
		RouteConfigParser parser = getAndParseRoute(agency_tag, route_tag);
		if (parser == null) {
			return null;
		}

		List<Db.Stop> stops = parser.getDirections().get(direction_tag).stops;
		String[] columns = { "_id", "route_tag", "direction_tag", "stop_tag",
				"title" };
		MatrixCursor result = new MatrixCursor(columns);
		int id = 0;
		for (Db.Stop stop : stops) {
			MatrixCursor.RowBuilder row = result.newRow();
			row.add(id++);
			row.add(route_tag);
			row.add(direction_tag);
			row.add(stop.tag);
			row.add(stop.title);
		}
		return result;
	}

	private Cursor queryPredictions(String agency_tag, String route_tag,
			String direction_tag, String stop_tag) {
		String request_uri = getContext().getString(
				R.string.one_prediction_uri, agency_tag, route_tag, stop_tag);

		PredictionsParser parser = getAndParse(request_uri,
				PredictionsParser.class);
		if (parser == null) {
			return null;
		}

		List<Db.Prediction> predictions = parser.getPredictions();
		String[] columns = { "_id", "route_tag", "direction_tag", "stop_tag",
				"predicted_time", "endpoint" };
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
