package info.yasskin.droidmuni.test;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.IsolatedContext;

public class MockableContentProvider extends ContentProvider {
  interface Interface {
    public int delete(Uri uri, String selection, String[] selectionArgs);

    public String getType(Uri uri);

    public Uri insert(Uri uri, ContentValues values);

    public Cursor query(Uri uri, String[] projection, String selection,
        String[] selectionArgs, String sortOrder);

    public int update(Uri uri, ContentValues values, String selection,
        String[] selectionArgs);
  }

  private final Interface m_behavior;

  MockableContentProvider(Interface behavior) {
    m_behavior = behavior;
    attachInfo(new IsolatedContext(null, null), null);
  }

  @Override
  public boolean onCreate() {
    // Called from attachInfo(), which runs before expectations have a chance to
    // be set up.
    return true;
  }

  @Override
  public String getType(Uri uri) {
    return m_behavior.getType(uri);
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return m_behavior.delete(uri, selection, selectionArgs);
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection,
      String[] selectionArgs) {
    return m_behavior.update(uri, values, selection, selectionArgs);
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return m_behavior.insert(uri, values);
  }

  public Cursor query(Uri uri, String[] projection, String selection,
      String[] selectionArgs, String sortOrder) {
    return m_behavior.query(uri, projection, selection, selectionArgs,
        sortOrder);
  }
}
