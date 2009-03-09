package info.yasskin.droidmuni;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class ManageSpace extends Activity {
  // Set once in onCreate and never changed again.
  private Db db;

  private Cursor m_cached_routes;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    db = new Db(this);

    setContentView(R.layout.manage_space);

    final Button clear_cache = (Button) findViewById(R.id.clear_cache);
    clear_cache.setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        db.eraseEverything();
        m_cached_routes.requery();
      }
    });

    final Button ok = (Button) findViewById(R.id.ok);
    ok.setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) {
        finish();
      }
    });

    ListView cached_routes = (ListView) findViewById(R.id.cached_routes);
    m_cached_routes =
        db.getReadableDatabase().rawQuery(
            "SELECT _id, description FROM Routes"
                + " WHERE _id IN (SELECT route_id FROM Directions)"
                + " ORDER BY upstream_index ASC", null);
    SimpleCursorAdapter route_adapter =
        new SimpleCursorAdapter(this, android.R.layout.simple_list_item_1,
            m_cached_routes, new String[] { "description" },
            new int[] { android.R.id.text1 });
    cached_routes.setAdapter(route_adapter);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    m_cached_routes.close();
    db.close();
  }
}
