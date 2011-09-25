package info.yasskin.droidmuni;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * A TextView that knows how to render a single NextMuni prediction. This object
 * has setters for the arrival time, prediction line and destination, and
 * requested line and destination, and sets the TextView's content accordingly.
 * 
 * Call update() after a series of setter calls to update the text.
 */
public class OnePredictionView extends TextView {
  public OnePredictionView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  /**
   * Text explaining that there's no prediction. If this is non-empty, it
   * overrides the other settings.
   */
  private String m_no_prediction_text = "";

  /**
   * System.currentTimeMillis() at which the bus is expected to arrive.
   */
  private long m_expected_arrival;

  /**
   * The route and direction tag of the user's query.
   */
  private String m_query_route_tag;
  private String m_query_direction_tag;

  /**
   * Information about this prediction, which may not exactly match the query.
   */
  private String m_prediction_route_tag;
  private String m_prediction_direction_tag;
  private String m_prediction_direction_title;

  private static final RelativeSizeSpan s_small_span = new RelativeSizeSpan(
      0.7f);

  public final void update() {
    if (!"".equals(m_no_prediction_text)) {
      setText(m_no_prediction_text);
      return;
    }

    final long now = now();
    long delta_minutes = (m_expected_arrival - now) / 60000;
    String ago = "";
    String plural = "";
    if (delta_minutes < 0) {
      ago = " ago";
      delta_minutes = -delta_minutes;
    }
    if (delta_minutes != 1) {
      plural = "s";
    }
    SpannableStringBuilder text = new SpannableStringBuilder();
    // TODO: Replace this with
    // android.text.format.DateUtils.getRelativeTimeSpanString.
    text.append(delta_minutes + "").append(" minute").append(plural).append(ago);

    // TODO: Always show the destination if it's not totally determined by the
    // route (e.x. 31 Inbound & 38 Outbound).
    if (!m_prediction_direction_tag.equals(m_query_direction_tag)) {
      final int small_start = text.length();
      text.append("  (");
      if (!m_prediction_route_tag.equals(m_query_route_tag)) {
        text.append(m_prediction_route_tag).append(" ");
      }
      text.append(m_prediction_direction_title).append(")");
      text.setSpan(s_small_span, small_start, text.length(), 0);
    }

    setText(text);
  }

  /**
   * Tells the user there are no predictions. Overrides all the other fields.
   * Call this with an empty string before setting the other fields.
   */
  public final void setNoPredictionText(String no_prediction_text) {
    m_no_prediction_text = no_prediction_text;
  }

  public final void setExpectedArrival(long expected_arrival) {
    m_expected_arrival = expected_arrival;
  }

  public final void setQueryRouteTag(String query_route_tag) {
    m_query_route_tag = query_route_tag;
  }

  public final void setQueryDirectionTag(String query_direction_tag) {
    m_query_direction_tag = query_direction_tag;
  }

  public final void setPredictionRouteTag(String prediction_route_tag) {
    m_prediction_route_tag = prediction_route_tag;
  }

  public final void setPredictionDirectionTag(String prediction_direction_tag) {
    m_prediction_direction_tag = prediction_direction_tag;
  }

  public final void setPredictionDirectionTitle(
      String prediction_direction_title) {
    m_prediction_direction_title = prediction_direction_title;
  }

  // Returns System.currentTimeMillis(), but as a non-final function so tests
  // can replace it.
  protected long now() {
    return System.currentTimeMillis();
  }
}
