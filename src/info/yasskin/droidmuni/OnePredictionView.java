package info.yasskin.droidmuni;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
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
   * System.currentTimeMillis() at which the remaining time will visibly change.
   * We need to call update() at this time, or as soon as the view becomes after
   * this. update() will update this time.
   * 
   * Protected so the test's subclass can read this.
   */
  protected long m_next_visible_change;
  private final long UPDATE_NOW = -1;

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

  /**
   * Updates the view's contents according to the arrival time, routes, and
   * directions that have been set into this object. Schedules another update to
   * happen when the remaining time goes down by a minute.
   */
  public final void update() {
    // Never have multiple updates scheduled at once.
    m_handler.removeMessages(UPDATE_MSG);

    if (!"".equals(m_no_prediction_text)) {
      setText(m_no_prediction_text);
      return;
    }

    final long now = now();
    if (m_next_visible_change <= now()) {
      // This division causes the "0 minutes left" status to last for 2 minutes,
      // which is a little wrong, but other options, like rounding, might give
      // people a false sense of hope that they can make their bus.
      long delta_minutes = (m_expected_arrival - now) / 60000;

      // Update the time of the next update.
      if (delta_minutes > 0) {
        // The next change is when the delta_minutes division starts rounding
        // down
        // one more number, which is just after a minute change.
        m_next_visible_change = m_expected_arrival - delta_minutes * 60000 + 1;
      } else {
        // The next change is when the delta_minutes division _stops_ rounding
        // down, which is exactly at a minute boundary.
        m_next_visible_change =
            m_expected_arrival - (delta_minutes - 1) * 60000;
      }

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
      text.append(delta_minutes + "").append(" minute").append(plural).append(
          ago);

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

    // Arrange to call update() again the next time the text will change.
    long delay = m_next_visible_change - now();
    m_handler.sendEmptyMessageDelayed(UPDATE_MSG, delay);
  }

  /**
   * Tells the user there are no predictions. Overrides all the other fields.
   * Call this with an empty string before setting the other fields.
   */
  public final void setNoPredictionText(String no_prediction_text) {
    m_next_visible_change = UPDATE_NOW;
    m_no_prediction_text = no_prediction_text;
  }

  public final void setExpectedArrival(long expected_arrival) {
    m_next_visible_change = UPDATE_NOW;
    m_expected_arrival = expected_arrival;
  }

  public final void setQueryRouteTag(String query_route_tag) {
    m_next_visible_change = UPDATE_NOW;
    m_query_route_tag = query_route_tag;
  }

  public final void setQueryDirectionTag(String query_direction_tag) {
    m_next_visible_change = UPDATE_NOW;
    m_query_direction_tag = query_direction_tag;
  }

  public final void setPredictionRouteTag(String prediction_route_tag) {
    m_next_visible_change = UPDATE_NOW;
    m_prediction_route_tag = prediction_route_tag;
  }

  public final void setPredictionDirectionTag(String prediction_direction_tag) {
    m_next_visible_change = UPDATE_NOW;
    m_prediction_direction_tag = prediction_direction_tag;
  }

  public final void setPredictionDirectionTitle(
      String prediction_direction_title) {
    m_next_visible_change = UPDATE_NOW;
    m_prediction_direction_title = prediction_direction_title;
  }

  // Returns System.currentTimeMillis(), but as a non-final function so tests
  // can replace it.
  protected long now() {
    return System.currentTimeMillis();
  }

  // We schedule future update() calls on this Handler.
  private final int UPDATE_MSG = 1;
  private final Handler m_handler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
      case UPDATE_MSG:
        update();
        break;
      }
    }
  };

  /**
   * Stops updating the displayed text, since the user can't see it anymore.
   */
  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    m_handler.removeMessages(UPDATE_MSG);
  }

  /**
   * If the window became visible, tries an update. If the window became
   * invisible, stops updating.
   */
  @Override
  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (visibility == VISIBLE) {
      update();
    } else {
      m_handler.removeMessages(UPDATE_MSG);
    }
  }

}
