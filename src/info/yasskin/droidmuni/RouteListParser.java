package info.yasskin.droidmuni;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Parses the result of a
 * http://webservices.nextbus.com/service/publicXMLFeed?command
 * =routeList&a=sf-muni request.
 */
class RouteListParser extends Parser {
  /**
   * @return the result
   */
  public final Map<String, Db.Route> getRoutes() {
    return routes;
  }

  private Map<String, Db.Route> routes = new HashMap<String, Db.Route>();

  @Override
  protected void parseBody() throws XmlPullParserException, IOException {
    parser.require(XmlPullParser.START_TAG, null, "body");
    this.copyright = getAttr("copyright");

    for (int upstream_index = 0; true; ++upstream_index) {
      if (parser.nextTag() == XmlPullParser.END_TAG) {
        parser.require(XmlPullParser.END_TAG, null, "body");
        // We're done with parsing routes.
        break;
      }
      if (!"route".equals(parser.getName())) {
        // Found a tag we didn't expect.
        skipToEndOfTag();
        continue;
      }
      parser.require(XmlPullParser.START_TAG, null, "route");
      String tag = getAttr("tag");
      String title = getAttr("title");

      Db.Route route = new Db.Route(-1, upstream_index, tag, title, 0);
      routes.put(route.tag, route);

      // Go to the </route> tag.
      skipToEndOfTag();
    }

    result_state = ResultState.SUCCESS;
  }
}
