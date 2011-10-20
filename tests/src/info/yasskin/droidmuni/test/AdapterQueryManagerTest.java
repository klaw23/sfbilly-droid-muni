package info.yasskin.droidmuni.test;

import info.yasskin.droidmuni.AdapterQueryManager;

import java.util.concurrent.TimeUnit;

import org.hamcrest.CustomMatcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.States;
import org.jmock.lib.concurrent.Synchroniser;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.test.InstrumentationTestCase;
import android.test.mock.MockContentResolver;

public class AdapterQueryManagerTest extends InstrumentationTestCase {
  protected void setUp() throws Exception {
    super.setUp();
    mockery = new Mockery();
    synchroniser = new Synchroniser();
    mockery.setThreadingPolicy(synchroniser);

    content_provider = mockery.mock(MockableContentProvider.Interface.class);
    content_resolver = new MockContentResolver();
    content_resolver.addProvider(AUTHORITY, new MockableContentProvider(
        content_provider));

    cursor_adapter =
        mockery.mock(MockableCursorAdapter.Interface.class, "CursorAdapter");
    adapter_query_manager =
        new AdapterQueryManager(loading_cursor, failed_cursor);
    adapter_query_manager.setAdapter(new MockableCursorAdapter(null,
        cursor_adapter));

    cursor1 = mockery.mock(Cursor.class, "Cursor1");
    cursor2 = mockery.mock(Cursor.class, "Cursor2");
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

  // Given a certain interleaving of queries, the AdapterQueryManager could fail
  // to close a cursor. This test arranges that interleaving, and asserts that
  // the cursor gets closed.
  public void testCancelledCursorIsClosed() throws Throwable {
    final States states = mockery.states("test").startsAs("initial");
    final States cursor1_state = mockery.states("cursor1").startsAs("open");
    mockery.checking(new Expectations() {
      {
        ignoring(cursor_adapter).changeCursor(with.<Cursor> is(anything()));
        when(states.isNot("in_second_query"));

        // An initial query comes in ...
        oneOf(content_provider).query(
            Uri.parse("content://" + AUTHORITY + "/query?1"), null, null, null,
            null);
        // This state change lets the main test know to fire off the second
        // query.
        then(states.is("in_first_query"));
        will(doAll(
        // ... and takes a while in the ContentProvider:
            new WaitUntilAction(synchroniser, states.is("in_second_query"),
                1000, TimeUnit.MILLISECONDS), returnValue(cursor1)));

        // A second query comes in and completes while the first one is blocked.
        oneOf(content_provider).query(
            Uri.parse("content://" + AUTHORITY + "/query?2"), null, null, null,
            null);
        will(returnValue(cursor2));
        then(states.is("in_second_query"));

        // The second query's result causes the adapter's cursor to get changed.
        Sequence cursor_change_sequence = mockery.sequence("cursor_changes");
        oneOf(cursor_adapter).changeCursor(null);
        inSequence(cursor_change_sequence);
        when(states.is("in_second_query"));

        oneOf(cursor_adapter).changeCursor(
            with(new CustomMatcher<Cursor>("wraps cursor2") {
              public boolean matches(Object item) {
                if (item instanceof Cursor) {
                  Cursor cursor = (Cursor) item;
                  return cursor.respond(Bundle.EMPTY).containsKey("cursor2");
                }
                return false;
              }
            }));
        inSequence(cursor_change_sequence);
        when(states.is("in_second_query"));
        then(states.is("cursor_changed"));

        // Cursors get wrapped as they exit ContentResolver.query(), so we have
        // to use a method instead of object identity to identify cursor2.
        oneOf(cursor2).respond(Bundle.EMPTY);
        Bundle cursor2_respond_result = new Bundle();
        cursor2_respond_result.putBoolean("cursor2", true);
        will(returnValue(cursor2_respond_result));

        // Several things happen to a cursor that we don't care about.
        ignoring(cursor1).getCount();
        ignoring(cursor2);

        // Except that the cursor must get closed.
        oneOf(cursor1).close();
        // We use a state so we can wait for the close.
        then(cursor1_state.is("closed"));
      }
    });

    runTestOnUiThread(new Runnable() {
      public void run() {
        adapter_query_manager.startQuery(content_resolver,
            Uri.parse("content://" + AUTHORITY + "/query?1"));
      }
    });
    synchroniser.waitUntil(states.is("in_first_query"), 1000);
    runTestOnUiThread(new Runnable() {
      public void run() {
        adapter_query_manager.startQuery(content_resolver,
            Uri.parse("content://" + AUTHORITY + "/query?2"));
      }
    });

    synchroniser.waitUntil(cursor1_state.is("closed"), 1000);
    synchroniser.waitUntil(states.is("cursor_changed"), 1000);

    mockery.assertIsSatisfied();
  }

  private Mockery mockery;
  private Synchroniser synchroniser;

  private MockableCursorAdapter.Interface cursor_adapter;
  private AdapterQueryManager adapter_query_manager;
  private MockableContentProvider.Interface content_provider;
  private MockContentResolver content_resolver;
  private final String AUTHORITY = "domain";

  private Cursor cursor1;
  private Cursor cursor2;

  // Used by identity, not value.
  private final Cursor loading_cursor = new CursorLiteral("_id");
  private final Cursor failed_cursor = new CursorLiteral("_id");
}
