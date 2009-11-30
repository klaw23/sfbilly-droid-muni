package info.yasskin.droidmuni;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.graphics.Color;
import android.util.Log;
import android.util.SparseArray;

class RouteConfigParser extends Parser {
	public String getTag() {
		return tag;
	}

	public String getRouteCode() {
		return routeCode;
	}

	public String getTitle() {
		return title;
	}

	public String getShortTitle() {
		return shortTitle;
	}

	public int getColor() {
		return color;
	}

	public int getOppositeColor() {
		return oppositeColor;
	}

	public SparseArray<Db.Stop> getStops() {
		return stops;
	}

	public Map<String, Db.Direction> getDirections() {
		return directions;
	}

	// Implementation

	private String tag;
	private String routeCode;
	private String title;
	private String shortTitle;
	private int color;
	private int oppositeColor;
	private final SparseArray<Db.Stop> stops = new SparseArray<Db.Stop>();
	private final Map<String, Db.Direction> directions = new HashMap<String, Db.Direction>();

	private int parseColorDefault(String color, int defalt) {
		if (color == null) {
			return defalt;
		}
		if (!color.startsWith("#")) {
			color = "#" + color;
		}
		try {
			return Color.parseColor(color);
		} catch (IllegalArgumentException e) {
			return defalt;
		}
	}

	/**
	 * Parses a NextBus <body> document. To see an example of one of these,
	 * visit 
	 * http://webservices.nextbus.com/service/publicXMLFeed?command=routeConfig&a=sf-muni&r=71
	 */
	@Override
	protected void parseBody() throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, null, "body");
		this.copyright = getAttr("copyright");

		parser.nextTag();
		parser.require(XmlPullParser.START_TAG, null, "route");
		this.tag = getAttr("tag");
		this.routeCode = getAttr("routeCode");
		this.title = getAttr("title");
		this.shortTitle = getAttr("shortTitle");
		this.color = parseColorDefault(getAttr("color"), Color.BLACK);
		this.oppositeColor = parseColorDefault(getAttr("oppositeColor"),
				Color.WHITE);

		while (true) {
			parser.nextTag();
			if (parser.getEventType() == XmlPullParser.END_TAG) {
				parser.require(XmlPullParser.END_TAG, null, "route");
				break;
			}
			parser.require(XmlPullParser.START_TAG, null, null);
			if (parser.getName().equals("stop")) {
				parseStop();
			} else if (parser.getName().equals("direction")) {
				parseDirection();
			} else if (parser.getName().equals("path")) {
				// I don't know what to do with paths yet, so ignore them.
				skipToEndOfTag();
			} else {
				Log.w("DroidMuni", "Unexpected element in route: "
						+ parser.getName());
				skipToEndOfTag();
			}
		}

		result_state = ResultState.SUCCESS;
	}

	private void parseStop() throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, null, "stop");

                int tag = Integer.parseInt(getAttr("tag"), 10);
                int id = Integer.parseInt(getAttr("stopId"), 10);
		String title = getAttr("title");
		double lat = Double.parseDouble(getAttr("lat"));
		double lon = Double.parseDouble(getAttr("lon"));
		stops.put(tag, new Db.Stop(id, tag, title, lat, lon));
		skipToEndOfTag();
	}

	private void parseDirection() throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, null, "direction");

		final String tag = getAttr("tag");
		final String title = getAttr("title");
		final String name = getAttr("name");
		final boolean useForUI = Boolean.parseBoolean(getAttr("useForUI"));
		final List<Db.Stop> stops = new ArrayList<Db.Stop>();

		final int original_depth = parser.getDepth();
		while (true) {
			try {
				final int event_type = parser.nextTag();
				if (parser.getDepth() == original_depth
						&& event_type == XmlPullParser.END_TAG) {
					break;
				}
				parser.require(XmlPullParser.START_TAG, null, "stop");
			} catch (XmlPullParserException e) {
				Log.d("DroidMuni", "Unexpected content in <direction> tag", e);
				continue;
			}

			Db.Stop stop;
			try {
				int stop_id = Integer.parseInt(getAttr("tag"), 10);
				stop = this.stops.get(stop_id);
			} catch (NumberFormatException e) {
				stop = null;
			}
			if (stop == null) {
				Log.w("DroidMuni", "Skipping unrecognized stop tag: "
						+ getAttr("tag"));
			} else {
				stops.add(stop);
			}
			skipToEndOfTag();
		}

		this.directions.put(tag, new Db.Direction(this.directions.size(), tag,
				title, name, useForUI, stops));
	}
}
