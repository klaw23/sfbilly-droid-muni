package info.yasskin.droidmuni.test;

import info.yasskin.droidmuni.OnePredictionView;
import android.content.Context;
import android.test.AndroidTestCase;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;

public class OnePredictionViewTest extends AndroidTestCase {
  private static class TestPredictionView extends OnePredictionView {
    public TestPredictionView(Context context) {
      super(context, null);
    }

    public long fake_now;

    protected long now() {
      return fake_now;
    }
  }

  private TestPredictionView m_view;

  public void setUp() {
    m_view = new TestPredictionView(getContext());
    // Set defaults that each test will override.
    m_view.fake_now = 10000 * 1000;
    m_view.setQueryRouteTag("71");
    m_view.setQueryDirectionTag("71I");
    m_view.setPredictionRouteTag("71");
    m_view.setPredictionDirectionTag("71I");
    m_view.setPredictionDirectionTitle("Inbound to Ferry");
  }

  public void testSameDirectionVariousTimes() {
    m_view.setExpectedArrival(10600 * 1000);
    m_view.update();

    Spanned text = (Spanned) m_view.getText();
    assertEquals("10 minutes", text.toString());
    Object styles[] = text.getSpans(0, text.length(), Object.class);
    assertEquals(0, styles.length);

    m_view.fake_now = 10539 * 1000;
    m_view.update();
    assertEquals("1 minute", m_view.getText().toString());

    m_view.fake_now = 10599 * 1000;
    m_view.update();
    assertEquals("0 minutes", m_view.getText().toString());

    m_view.fake_now = 10659 * 1000;
    m_view.update();
    assertEquals("0 minutes", m_view.getText().toString());

    m_view.fake_now = 10660 * 1000;
    m_view.update();
    assertEquals("1 minute ago", m_view.getText().toString());

    m_view.fake_now = 11140 * 1000;
    m_view.update();
    assertEquals("9 minutes ago", m_view.getText().toString());
  }

  public void testDifferentDirectionSameRoute() {
    m_view.setExpectedArrival(10600 * 1000);
    m_view.setPredictionDirectionTag("71IVN");
    m_view.setPredictionDirectionTitle("Inbound to Van Ness");
    m_view.update();

    Spanned text = (Spanned) m_view.getText();
    assertEquals("10 minutes  (Inbound to Van Ness)", text.toString());
    Object styles[] = text.getSpans(0, text.length(), Object.class);
    assertEquals(1, styles.length);
    RelativeSizeSpan span = (RelativeSizeSpan) styles[0];
    assertEquals(.7f, span.getSizeChange());
    assertEquals("10 minutes".length(), text.getSpanStart(span));
    assertEquals(text.length(), text.getSpanEnd(span));
  }

  public void testDifferentRoute() {
    m_view.setExpectedArrival(10600 * 1000);
    m_view.setPredictionRouteTag("6");
    m_view.setPredictionDirectionTag("6I");
    m_view.setPredictionDirectionTitle("Inbound to Ferry");
    m_view.update();

    Spanned text = (Spanned) m_view.getText();
    assertEquals("10 minutes  (6 Inbound to Ferry)", text.toString());
    Object styles[] = text.getSpans(0, text.length(), Object.class);
    assertEquals(1, styles.length);
    RelativeSizeSpan span = (RelativeSizeSpan) styles[0];
    assertEquals(.7f, span.getSizeChange());
    assertEquals("10 minutes".length(), text.getSpanStart(span));
    assertEquals(text.length(), text.getSpanEnd(span));
  }

  public void testNoPrediction() {
    m_view.setNoPredictionText("No predictions");
    m_view.update();

    assertTrue(m_view.getText() instanceof String);
    assertEquals("No predictions", m_view.getText());

    // Recovers when NoPredictionText is removed.
    m_view.setNoPredictionText("");
    m_view.setExpectedArrival(10600 * 1000);
    m_view.update();
    assertEquals("10 minutes", m_view.getText().toString());
  }
}
