package info.yasskin.droidmuni.test;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

public class MockableCursorAdapter extends CursorAdapter {
  public static interface Interface {
    public void bindView(View view, Context context, Cursor cursor);
    public View newView(Context context, Cursor cursor, ViewGroup parent);
    public void changeCursor(Cursor cursor);
  }

  private final Interface m_behavior;

  public MockableCursorAdapter(Context context, Interface behavior) {
    super(context, null);
    m_behavior = behavior;
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    m_behavior.bindView(view, context, cursor);
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    return m_behavior.newView(context, cursor, parent);
  }

  @Override
  public void changeCursor(Cursor cursor) {
    super.changeCursor(cursor);
    m_behavior.changeCursor(cursor);
  }

}
