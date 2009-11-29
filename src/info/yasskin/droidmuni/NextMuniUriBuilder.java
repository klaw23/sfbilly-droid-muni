package info.yasskin.droidmuni;

import android.net.Uri;

/**
 * @see http://www.sfmta.com/cms/asite/nextmunidata.htm for the description of
 *      the format.
 */
class NextMuniUriBuilder {
  // Should be http://webservices.nextbus.com/service/publicXMLFeed?command=routeList&a=sf-muni
  private static final Uri s_route_list_base =
      Uri.parse("http://www.nextmuni.com/googleMap/routeSelector.jsp");
  private static final Uri s_route_details_base =
      Uri.parse("http://webservices.nextbus.com/service/publicXMLFeed?command=routeConfig");
  private static final Uri s_multi_predictions_base =
      Uri.parse("http://webservices.nextbus.com/service/publicXMLFeed?command=predictionsForMultiStops");

  static Uri buildRouteListUri(String agency) {
    Uri.Builder builder = s_route_list_base.buildUpon();
    builder.appendQueryParameter("a", agency);
    return builder.build();
  }

  static Uri buildRouteDetailsUri(String agency, String route_tag) {
    Uri.Builder builder = s_route_details_base.buildUpon();
    builder.appendQueryParameter("a", agency);
    builder.appendQueryParameter("r", route_tag);
    return builder.build();
  }

  static Uri buildMultiPredictionUri(String agency, String stop_tag,
      String... route_tags) {
    Uri.Builder builder = s_multi_predictions_base.buildUpon();
    builder.appendQueryParameter("a", agency);
    for (String route_tag : route_tags) {
      builder.appendQueryParameter("stops", route_tag + "||" + stop_tag);
    }
    return builder.build();
  }
}
