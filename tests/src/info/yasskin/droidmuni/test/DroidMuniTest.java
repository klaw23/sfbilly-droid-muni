package info.yasskin.droidmuni.test;

import info.yasskin.droidmuni.DroidMuni;
import info.yasskin.droidmuni.NextMuniProvider;
import info.yasskin.droidmuni.R;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.States;
import org.jmock.lib.concurrent.Synchroniser;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.test.ActivityUnitTestCase;
import android.test.IsolatedContext;
import android.test.MoreAsserts;
import android.test.mock.MockContentResolver;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

public class DroidMuniTest extends ActivityUnitTestCase<DroidMuni> {
  public DroidMuniTest() {
    super(DroidMuni.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mockery = new Mockery();
    synchroniser = new Synchroniser();
    mockery.setThreadingPolicy(synchroniser);

    content_resolver = new MockContentResolver();
    nextmuni_provider =
        mockery.mock(MockableContentProvider.Interface.class,
            "NextMuniProvider");
    context =
        new IsolatedContext(content_resolver,
            getInstrumentation().getTargetContext()) {
          @Override
          public Object getSystemService(String name) {
            if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
              return getInstrumentation().getTargetContext().getSystemService(
                  name);
            }
            return super.getSystemService(name);
          }
        };
    content_resolver.addProvider(NextMuniProvider.AUTHORITY,
        new MockableContentProvider(nextmuni_provider));

    setActivityContext(context);
  }

  public void testLifecycle() throws Throwable {
    final States queries = mockery.states("queries").startsAs("initial");

    mockery.checking(new Expectations() {
      {
        // Note that exceptions for unexpected calls get captured by the
        // Activity and logged rather than causing the test to fail quickly.
        final Sequence sequence = mockery.sequence("queries");
        final String URL_PREFIX =
            "content://info.yasskin.droidmuni.nextmuniprovider";

        oneOf(nextmuni_provider).query(Uri.parse(URL_PREFIX + "/routes"), null,
            null, null, null);
        will(returnValue(new CursorLiteral("_id", "tag", "description",
            "mock_result").row(0, "6", "6 Parnassus", "").row(1, "24",
            "24 Divisadero", "").row(2, "71", "71 Haight/Noriega", "")));
        inSequence(sequence);
        then(queries.is("routes"));

        oneOf(nextmuni_provider).query(
            Uri.parse(URL_PREFIX + "/directions/24"), null, null, null, null);
        will(returnValue(new CursorLiteral("_id", "route_tag", "tag", "title",
            "mock_result").row(0, "24", "24I", "Inbound to Downtown", "").row(
            0, "24", "24O", "Outbound to suburb", "")));
        inSequence(sequence);
        then(queries.is("directions"));

        oneOf(nextmuni_provider).query(Uri.parse(URL_PREFIX + "/stops/24/24I"),
            null, null, null, null);
        will(returnValue(new CursorLiteral("_id", "route_tag", "direction_tag",
            "stop_id", "title", "lat", "lon", "mock_result").row(0, "24",
            "24I", 1234, "Castro & 18th", 40, 124, "").row(0, "24", "24I",
            1235, "Castro & 17th", 40, 125, "").row(0, "24", "24I", 1236,
            "Castro & 16th", 40, 126, "")));
        inSequence(sequence);
        then(queries.is("stops"));

        oneOf(nextmuni_provider).query(
            Uri.parse(URL_PREFIX + "/predictions/1235"), null, null, null, null);
        will(returnValue(new CursorLiteral("_id", "route_tag", "direction_tag",
            "direction_title", "stop_id", "predicted_time", "mock_result").row(
            0, 24, "24I", "Inbound to Downtown", 1235, 3000, "").row(0, 24,
            "24IH", "Inbound to Halfway", 1235, 3200, "").row(0, 36, "36I",
            "Inbound to Downtown", 1235, 3400, "")));
        inSequence(sequence);
        then(queries.is("predictions"));
      }
    });

    FutureTask<DroidMuni> activity_future =
        new FutureTask<DroidMuni>(new Callable<DroidMuni>() {
          public DroidMuni call() throws Exception {
            return startActivity(new Intent(Intent.ACTION_MAIN), null, null);
          }
        });
    getInstrumentation().runOnMainSync(activity_future);
    DroidMuni activity = activity_future.get();

    final Spinner line_spinner = (Spinner) activity.findViewById(R.id.line);
    waitUntilHasColumnNamed(((CursorAdapter) line_spinner.getAdapter()),
        "mock_result");
    runTestOnUiThread(new Runnable() {
      public void run() {
        line_spinner.setSelection(1, false);
      }
    });

    final Spinner direction_spinner =
        (Spinner) activity.findViewById(R.id.direction);
    waitUntilHasColumnNamed(((CursorAdapter) direction_spinner.getAdapter()),
        "mock_result");

    runTestOnUiThread(new Runnable() {
      public void run() {
        direction_spinner.setSelection(0, false);
      }
    });

    final Spinner stop_spinner = (Spinner) activity.findViewById(R.id.stop);
    waitUntilHasColumnNamed(((CursorAdapter) stop_spinner.getAdapter()),
        "mock_result");

    runTestOnUiThread(new Runnable() {
      public void run() {
        stop_spinner.setSelection(1, false);
      }
    });

    final ListView prediction_list =
        (ListView) activity.findViewById(R.id.predictions);
    waitUntilHasColumnNamed(((CursorAdapter) prediction_list.getAdapter()),
        "mock_result");

    runTestOnUiThread(new Runnable() {
      public void run() {
        // Arrange to call setViewValue on the predictions list's
        // SimpleCursorAdapter.ViewBinder.
        int unlimited_measure_spec =
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        prediction_list.measure(unlimited_measure_spec, unlimited_measure_spec);
        prediction_list.layout(0, 0, prediction_list.getMeasuredWidth(),
        // For some reason, the measured height isn't enough to display all the
        // list elements, so double it.
            prediction_list.getMeasuredHeight() * 2);
      }
    });

    runTestOnUiThread(new Runnable() {
      public void run() {
        assertEquals(3, prediction_list.getChildCount());
        {
          TextView prediction = (TextView) prediction_list.getChildAt(0);
          Spanned text = (Spanned) prediction.getText();
          // TODO(jyasskin): Mock out time so this assertion can be exact.
          MoreAsserts.assertMatchesRegex("\\d+ minutes ago", text.toString());
          Object styles[] = text.getSpans(0, text.length(), Object.class);
          assertEquals(0, styles.length);
        }

        {
          TextView prediction = (TextView) prediction_list.getChildAt(1);
          Spanned text = (Spanned) prediction.getText();
          MoreAsserts.assertMatchesRegex(
              "\\d+ minutes ago  \\(Inbound to Halfway\\)", text.toString());
          Object styles[] = text.getSpans(0, text.length(), Object.class);
          assertEquals(1, styles.length);
          RelativeSizeSpan span = (RelativeSizeSpan) styles[0];
          assertEquals(.7f, span.getSizeChange());
          assertEquals(text.toString().indexOf('(') - 2, text.getSpanStart(span));
          assertEquals(text.length(), text.getSpanEnd(span));
        }

        {
          TextView prediction = (TextView) prediction_list.getChildAt(2);
          Spanned text = (Spanned) prediction.getText();
          MoreAsserts.assertMatchesRegex(
              "\\d+ minutes ago  \\(36 Inbound to Downtown\\)", text.toString());
          Object styles[] = text.getSpans(0, text.length(), Object.class);
          assertEquals(1, styles.length);
          RelativeSizeSpan span = (RelativeSizeSpan) styles[0];
          assertEquals(.7f, span.getSizeChange());
          assertEquals(text.toString().indexOf('(') - 2, text.getSpanStart(span));
          assertEquals(text.length(), text.getSpanEnd(span));
        }
      }
    });

    mockery.assertIsSatisfied();
  }

  void waitUntilHasColumnNamed(final CursorAdapter adapter,
      final String column_name) throws Throwable {
    final CountDownLatch has_index = new CountDownLatch(1);
    class CheckingObserver extends DataSetObserver {
      public void check() {
        Cursor cursor = adapter.getCursor();
        if (cursor != null && cursor.getColumnIndex(column_name) != -1) {
          has_index.countDown();
        }
      }

      @Override
      public void onChanged() {
        check();
      }

      @Override
      public void onInvalidated() {
        check();
      }
    }
    ;
    final CheckingObserver observer = new CheckingObserver();
    try {
      runTestOnUiThread(new Runnable() {
        public void run() {
          adapter.registerDataSetObserver(observer);
          // It's important to check after registering. If we checked before,
          // and the cursor was changed between the check and the registration,
          // we could wait forever.
          observer.check();
        }
      });
      if (!has_index.await(1000, TimeUnit.MILLISECONDS)) {
        throw new TimeoutException();
      }
    } finally {
      runTestOnUiThread(new Runnable() {
        public void run() {
          adapter.unregisterDataSetObserver(observer);
        }
      });
    }
  }

  private Synchroniser synchroniser;
  private Mockery mockery;
  private MockContentResolver content_resolver;
  private Context context;
  private MockableContentProvider.Interface nextmuni_provider;
}
