package info.yasskin.droidmuni;

import java.util.TreeMap;
import java.util.Vector;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

class MultiStopAdapter extends BaseAdapter {

  private Vector<ExtendedStop> m_items;

  public MultiStopAdapter(Vector<ExtendedStop> items) {
    this.m_items = items;
  }

  public int getCount() {
    return m_items.size();
  }

  public Object getItem(int position) {
    return m_items.get(position);
  }

  public long getItemId(int position) {
    return position;
  }

  public TextView getFancyRouteImage(String route, Context ctx) {
    TextView view = new TextView(ctx);
    view.setPadding(4, 4, 4, 4);

    switch (route.substring(0, 1).toCharArray()[0]) {
    case 'J':
      view.setBackgroundResource(R.drawable.jline);
      break;
    case 'K':
      view.setBackgroundResource(R.drawable.kline);
      break;
    case 'L':
      view.setBackgroundResource(R.drawable.lline);
      break;
    case 'M':
      view.setBackgroundResource(R.drawable.mline);
      break;
    case 'N':
      view.setBackgroundResource(R.drawable.nline);
      break;
    case 'T':
      view.setBackgroundResource(R.drawable.tline);
      break;
    default:
      if (route.contains("L")) { // Note the L-Taraval will have already been
                                 // caught, so this "must" be an express bus..
        view.setBackgroundResource(R.drawable.exprbus);
      } else {
        view.setBackgroundResource(R.drawable.bus);
      }

      // Add text for bus route number since we don't have graphics for all of
      // them
      view.setText(route);
      view.setTextSize(26);
      view.setTextColor(Color.WHITE);
      view.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
      break;
    }

    return view;
  }

  public View getView(int position, View convertView, ViewGroup parent) {
    Context ctx = parent.getContext();
    ExtendedStop stop = m_items.elementAt(position);

    LinearLayout ll = (LinearLayout) View.inflate(ctx, R.layout.stop_row, null);
    ((TextView) ll.findViewById(R.id.textViewStopName)).setText(stop.stop_title);

    LinearLayout row_holder =
        (LinearLayout) ll.findViewById(R.id.predictionRow);
    
    for (String route_name : stop.keySet()) {      
      // Add a new major row for this route, with route icon
      LinearLayout route =
          (LinearLayout) View.inflate(ctx, R.layout.stop_route_row, null);
      row_holder.addView(route);

      TextView routeIcon = getFancyRouteImage(route_name, ctx);
      route.addView(routeIcon, 0); // insert image at left edge

      // Get layouts which hold subrows for all directions
      LinearLayout inbound =
          (LinearLayout) route.findViewById(R.id.layoutInbound);
      LinearLayout outbound =
          (LinearLayout) route.findViewById(R.id.layoutOutbound);

      TreeMap<String, Vector<Long>> directionMap = stop.get(route_name);

      // Add a light horiz line between routes at this stop, for readability
      TextView greyLine = new TextView(ctx);
      greyLine.setHeight(2);
      greyLine.setPadding(60, 0, 0, 0);
      greyLine.setBackgroundColor(0xff404040);
      row_holder.addView(greyLine);
    }
    // Add space at bottom, and remove final unneeded gray line
    row_holder.removeViewAt(row_holder.getChildCount() - 1);
    row_holder.addView(new TextView(ctx)); // blank space
    row_holder.addView(new TextView(ctx)); // blank space

    return ll;
  }
}
