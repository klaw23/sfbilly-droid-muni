package info.yasskin.droidmuni;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

public class DroidMuni extends Activity {
	private int mLine;
	private int mDirection;
	private int mStop;

	private SimpleCursorAdapter mLineAdapter;
	private SimpleCursorAdapter mDirectionAdapter;
	private SimpleCursorAdapter mStopAdapter;
	private SimpleCursorAdapter mPredictionsAdapter;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SharedPreferences pref = getPreferences(MODE_PRIVATE);
		this.mLine = pref.getInt("line", Spinner.INVALID_POSITION);
		this.mDirection = pref.getInt("direction", Spinner.INVALID_POSITION);
		this.mStop = pref.getInt("stop", Spinner.INVALID_POSITION);

		this.setContentView(R.layout.main);

		Spinner line_spinner = (Spinner) findViewById(R.id.line);
		mLineAdapter = setupSpinner(line_spinner, "description",
				mLineClickedHandler);
		mLineAdapter.changeCursor(managedQuery(NextMuniProvider.ROUTES_URI,
				null, null, null, null));

		Spinner direction_spinner = (Spinner) findViewById(R.id.direction);
		mDirectionAdapter = setupSpinner(direction_spinner, "title",
				mDirectionClickedHandler);

		Spinner stop_spinner = (Spinner) findViewById(R.id.stop);
		mStopAdapter = setupSpinner(stop_spinner, "title", mStopClickedHandler);

		ListView prediction_list = (ListView) findViewById(R.id.predictions);
		mPredictionsAdapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_list_item_1,
				null,
				// TODO: Show the destination, but only when it's not totally
				// determined by the route (e.x. 31 Inbound & 38 Outbound).
				new String[] { "predicted_time" },
				new int[] { android.R.id.text1 });
		mPredictionsAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			public boolean setViewValue(View view, Cursor cursor,
					int columnIndex) {
				if (!(view instanceof TextView)) {
					return false;
				}
				TextView textview = (TextView) view;
				long expected_arrival = cursor.getLong(columnIndex);
				long now = System.currentTimeMillis();
				long delta_minutes = (expected_arrival - now) / 60000;
				String ago = "";
				String plural = "";
				if (delta_minutes < 0) {
					ago = " ago";
					delta_minutes = -delta_minutes;
				}
				if (delta_minutes != 1) {
					plural = "s";
				}
				textview.setText((expected_arrival - now) / 60000 + " minute"
						+ plural + ago);
				return true;
			}
		});
		prediction_list.setAdapter(mPredictionsAdapter);

		// if (mLine != Spinner.INVALID_POSITION) {
		// line_spinner.setSelection(mLine);
		// }
		// if (mDirection != Spinner.INVALID_POSITION) {
		// direction_spinner.setSelection(mDirection);
		// }
		// if (mStop != Spinner.INVALID_POSITION) {
		// stop_spinner.setSelection(mStop);
		// }
	}

	/**
	 * @param spinner
	 * @param display_column
	 *            TODO
	 * @param selection_handler
	 *            TODO
	 * @return
	 */
	private SimpleCursorAdapter setupSpinner(Spinner spinner,
			String display_column, OnItemSelectedListener selection_handler) {
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_spinner_item, null,
				new String[] { display_column },
				new int[] { android.R.id.text1 });
		adapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(selection_handler);
		return adapter;
	}

	private final OnItemSelectedListener mLineClickedHandler = new OnItemSelectedListener() {
		public void onItemSelected(AdapterView<?> parent, View v, int position,
				long id) {
			mLine = position;

			Cursor parent_item = (Cursor) parent.getItemAtPosition(position);
			Log.i("DroidMuni", "Line clicked: "
					+ DatabaseUtils.dumpCurrentRowToString(parent_item));
			String selected_route = parent_item.getString(1);
			Cursor direction_data = managedQuery(Uri.withAppendedPath(
					NextMuniProvider.DIRECTIONS_URI, selected_route), null,
					null, null, null);
			mDirectionAdapter.changeCursor(direction_data);
			Log.i("DroidMuni", "Directions cursor: "
					+ DatabaseUtils.dumpCursorToString(mDirectionAdapter
							.getCursor()));
		}

		public void onNothingSelected(AdapterView<?> parent) {
			Log.i("DroidMuni", "Nothing selected");
		}
	};

	private final OnItemSelectedListener mDirectionClickedHandler = new OnItemSelectedListener() {
		public void onItemSelected(AdapterView<?> parent, View v, int position,
				long id) {
			mDirection = position;

			Cursor parent_item = (Cursor) parent.getItemAtPosition(position);
			Log.i("DroidMuni", "Direction clicked: "
					+ DatabaseUtils.dumpCurrentRowToString(parent_item));
			String selected_route = parent_item.getString(1);
			String selected_direction = parent_item.getString(2);
			Cursor stop_data = managedQuery(Uri.withAppendedPath(Uri
					.withAppendedPath(NextMuniProvider.STOPS_URI,
							selected_route), selected_direction), null, null,
					null, null);
			mStopAdapter.changeCursor(stop_data);
		}

		public void onNothingSelected(AdapterView<?> parent) {
			Log.i("DroidMuni", "Nothing selected");
		}
	};

	private final OnItemSelectedListener mStopClickedHandler = new OnItemSelectedListener() {
		public void onItemSelected(AdapterView<?> parent, View v, int position,
				long id) {
			mStop = position;

			Cursor parent_item = (Cursor) parent.getItemAtPosition(position);
			Log.i("DroidMuni", "Stop clicked: "
					+ DatabaseUtils.dumpCurrentRowToString(parent_item));
			String selected_route = parent_item.getString(1);
			String selected_direction = parent_item.getString(2);
			String selected_stop = parent_item.getString(3);
			Cursor prediction_data = managedQuery(Uri.withAppendedPath(
					NextMuniProvider.PREDICTIONS_URI, selected_route + "/"
							+ selected_direction + "/" + selected_stop), null,
					null, null, null);
			mPredictionsAdapter.changeCursor(prediction_data);
			Log.i("DroidMuni", "Predictions: "
					+ DatabaseUtils.dumpCursorToString(prediction_data));
		}

		public void onNothingSelected(AdapterView<?> parent) {
			Log.i("DroidMuni", "Nothing selected");
		}
	};

	@Override
	public void onPause() {
		super.onPause();
		SharedPreferences.Editor editor = this.getPreferences(MODE_PRIVATE)
				.edit();
		editor.putInt("line", this.mLine);
		editor.putInt("direction", this.mDirection);
		editor.putInt("stop", this.mStop);
		editor.commit();
	}
}