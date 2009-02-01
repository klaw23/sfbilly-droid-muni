package info.yasskin.droidmuni;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Log;

public class PredictionsParser extends Parser {
	public List<Db.Prediction> getPredictions() {
		return predictions;
	}
	
	public String getDirectionTitle() {
		return direction_title;
	}

	private String direction_title;
	private final List<Db.Prediction> predictions = new ArrayList<Db.Prediction>();

	@Override
	protected void parseBody() throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, null, "body");
		this.copyright = getAttr("copyright");

		parser.nextTag();
		parser.require(XmlPullParser.START_TAG, null, "predictions");
		String no_predictions_title = parser.getAttributeValue(null, "dirTitleBecauseNoPredictions");
		if (no_predictions_title != null) {
			direction_title = no_predictions_title;
			this.result_state = ResultState.SUCCESS;
			return;
		}
		parser.nextTag();
		Log.d("DroidMuni", parser.getName());
		parser.require(XmlPullParser.START_TAG, null, "direction");
		this.direction_title = parser.getAttributeValue(null, "routeTitle");
		while (parser.nextTag() != XmlPullParser.END_TAG) {
			parser.require(XmlPullParser.START_TAG, null, "prediction");
			long epochTime = Long.parseLong(getAttr("epochTime"), 10);
			boolean isDeparture = Boolean.parseBoolean(getAttr("isDeparture"));
			String dirTag = getAttr("dirTag");
			String block = getAttr("block");
			predictions.add(new Db.Prediction(epochTime, isDeparture, dirTag,
					block));
			parser.nextText();
		}
		this.result_state = ResultState.SUCCESS;
	}

}
