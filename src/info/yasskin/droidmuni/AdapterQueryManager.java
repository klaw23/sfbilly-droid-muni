package info.yasskin.droidmuni;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.CursorAdapter;

/**
 * Manages sending a single query at a time to a ContentProvider. If an earlier
 * query comes back after a later query is sent, the earlier query is closed and
 * ignored.
 * 
 * Must be constructed from the UI thread.
 */
class AdapterQueryManager extends Handler {
  private static final ExecutorService s_executor =
      Executors.newCachedThreadPool();

  // Each query gets a unique token that is higher than all previous tokens. We
  // use this to ignore outdated queries.
  private int m_next_token = 0;
  private Future<?> m_current_query = null;
  private CursorAdapter m_adapter;
  private final Cursor m_loading_cursor;
  private final Cursor m_failed_cursor;

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
  final public void startQuery(final ContentResolver cr, final Uri uri,
      final String[] projection, final String selection,
      final String[] selectionArgs, final String orderBy) {
    resetCursor(m_loading_cursor);

    final int token = ++m_next_token;
    if (m_current_query != null) {
      m_current_query.cancel(true);
    }
    m_current_query = s_executor.submit(new Runnable() {
      public void run() {
        final Message msg = Message.obtain();
        try {
          msg.obj =
              cr.query(uri, projection, selection, selectionArgs, orderBy);
          msg.what = CURSOR;
        } catch (Throwable e) {
          msg.what = EXCEPTION;
          msg.obj = e;
        }
        msg.arg1 = token;
        sendMessage(msg);
      }
    });
  }

  final static int CURSOR = 0;
  final static int EXCEPTION = 1;

  /**
   * Dispatches a result Cursor to onQueryComplete if it's a response to the
   * newest query.
   * 
   * @see android.os.Handler#handleMessage(android.os.Message)
   */
  @Override
  final public void handleMessage(Message msg) {
    switch (msg.what) {
    case CURSOR:
      Cursor cursor = (Cursor) msg.obj;
      if (msg.arg1 != m_next_token) {
        // This response is outdated.
        if (cursor != null) {
          cursor.close();
        }
        return;
      }
      onQueryComplete(cursor);
      break;
    case EXCEPTION:
      if (msg.arg1 != m_next_token) {
        // This response is outdated.
        return;
      }
      onException((Throwable) msg.obj);
      break;
    }
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
   * adapter and then onSuccessfulQuery is called. If the result is null, the
   * failed cursor is set into the adapter.
   * 
   * This will be called on the UI thread.
   */
  protected void onQueryComplete(Cursor cursor) {
    if (cursor == null) {
      resetCursor(m_failed_cursor);
    } else {
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
