package info.yasskin.droidmuni;

import android.net.Uri;

class NextMuniUriBuilder {
  private static final Uri s_route_list_base =
      Uri.parse("http://www.nextmuni.com/googleMap/routeSelector.jsp");
  private static final Uri s_route_details_base =
      Uri.parse("http://www.nextmuni.com/s/COM.NextBus.Servlets.XMLFeed?command=routeConfig");
  private static final Uri s_multi_predictions_base =
      Uri.parse("http://www.nextmuni.com/s/COM.NextBus.Servlets.XMLFeed?command=predictionsForMultiStops");

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
