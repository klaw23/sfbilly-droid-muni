package info.yasskin.droidmuni;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.CursorAdapter;

/**
 * Manages sending a single query at a time to a ContentProvider. If an earlier
 * query comes back after a later query is sent, the earlier query is closed and
 * ignored.
 * 
 * Must be constructed from the UI thread.
 */
public class AdapterQueryManager {
  private CursorAdapter m_adapter;
  private final Cursor m_loading_cursor;
  private final Cursor m_failed_cursor;
  private ContentResolver m_content_resolver;
  private Uri m_current_query_uri;
  private boolean m_uri_has_succeeded;
  private QueryTask m_current_query;

  private class QueryTask extends AsyncTask<Void, Void, Void> {
    private final ContentResolver m_content_resolver;
    private final Uri m_query_uri;

    private Cursor m_cursor_result = null;
    private Throwable m_exception_result = null;

    public QueryTask(ContentResolver content_resolver, Uri uri) {
      m_content_resolver = content_resolver;
      m_query_uri = uri;
    }

    @Override
    protected Void doInBackground(Void... params) {
      try {
        Cursor result =
            m_content_resolver.query(m_query_uri, null, null, null, null);
        synchronized (this) {
          if (isCancelled()) {
            // If cancel() was called before the isCancelled() check,
            // onCancelled may run before or after this synchronized block.
            // Either way, it will find m_cursor_result==null and so not try to
            // double-close the cursor.
            result.close();
          } else {
            // Otherwise, if cancel() is called between the isCancelled() check
            // and when returning from doInBackground() fills in the Future
            // inside AsyncTask, the onCancelled() call will block until it can
            // acquire this's lock and then will find the cursor it needs to
            // close in m_cursor_result.
            m_cursor_result = result;
          }
        }
      } catch (Throwable e) {
        Log.v("DroidMuni", "Exception from ContentResolver", e);
        m_exception_result = e;
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void result) {
      if (m_exception_result != null) {
        onException(m_exception_result);
      } else {
        onQueryComplete(m_cursor_result);
      }
    }

    // Due to a bug in AsyncTask before Honeycomb, onCancelled() can run
    // concurrently with doInBackground().
    @Override
    protected synchronized void onCancelled() {
      if (m_cursor_result != null) {
        // doInBackground() has already returned.
        m_cursor_result.close();
      }
    }
  }

  public AdapterQueryManager(Cursor loading_cursor, Cursor failed_cursor) {
    m_loading_cursor = loading_cursor;
    m_failed_cursor = failed_cursor;
  }

  /**
   * Sets the adapter's cursor to the loading cursor and forwards to
   * ContentResolver.query() on a background thread. When the query finishes,
   * we'll arrange to call either onQueryComplete or onException in the UI
   * thread.
   * 
   * Must be called from the UI thread.
   */
  final public void startQuery(ContentResolver content_resolver, final Uri uri) {
    m_content_resolver = content_resolver;
    m_current_query_uri = uri;
    m_uri_has_succeeded = false;

    resetCursor(m_loading_cursor);

    if (m_current_query != null) {
      m_current_query.cancel(/* mayInterruptIfRunning= */true);
      m_current_query = null;
    }
    requery();
  }

  /**
   * Re-runs the previous query with the same parameters to see if its results
   * have changed, unless it's still running.
   * 
   * Arranges that if the requery fails, we don't replace the old result with
   * m_failed_cursor.
   */
  final public void requery() {
    if (m_current_query_uri == null) {
      throw new IllegalStateException(
          "Must call startQuery() before requery().");
    }
    if (m_current_query != null
        && m_current_query.getStatus() != AsyncTask.Status.FINISHED) {
      return;
    }
    m_current_query = new QueryTask(m_content_resolver, m_current_query_uri);
    m_current_query.execute();
  }

  final protected void resetCursor(Cursor new_cursor) {
    m_adapter.changeCursor(null);
    m_adapter.changeCursor(new_cursor);
  }

  /**
   * @return the CursorAdapter this query manager is using.
   */
  public final CursorAdapter getAdapter() {
    return m_adapter;
  }

  /**
   * Sets the CursorAdapter this query manager will use. You can only set this
   * once, and should probably do it in onCreate().
   */
  public final void setAdapter(CursorAdapter new_adapter) {
    if (m_adapter != null) {
      throw new IllegalStateException("Cannot set adapter twice");
    }
    if (new_adapter == null) {
      throw new IllegalArgumentException("Cannot set adapter to null");
    }
    m_adapter = new_adapter;
  }

  /**
   * Subclasses can override this method to define what happens with the result
   * of a query. By default, if the result is non-null it is set into the
   * adapter and then onSuccessfulQuery is called. If the result is null and
   * this wasn't a requery, the failed cursor is set into the adapter.
   * 
   * This will be called on the UI thread.
   */
  protected void onQueryComplete(Cursor cursor) {
    if (cursor == null) {
      if (!m_uri_has_succeeded) {
        resetCursor(m_failed_cursor);
      }
    } else {
      m_uri_has_succeeded = true;
      resetCursor(cursor);
      onSuccessfulQuery(cursor);
    }
  }

  /**
   * Called when a query returns a non-null cursor, as long as onQueryComplete
   * has its default behavior. Does nothing by default.
   */
  protected void onSuccessfulQuery(Cursor cursor) {
  }

  /**
   * Subclasses can override this method to do something when the query throws
   * an exception. The default is to log an error and set the failed cursor into
   * the adapter. This will be called on the UI thread.
   */
  protected void onException(Throwable e) {
    Log.e("DroidMuni", "query unexpectedly threw an exception", e);
    resetCursor(m_failed_cursor);
  }
}
