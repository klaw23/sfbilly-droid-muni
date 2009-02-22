package info.yasskin.droidmuni;

import java.io.IOException;
import java.io.InputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Log;
import android.util.Xml;

abstract class Parser {
  public void parse(InputStream input) {
    if (this.result_state != ResultState.NOT_DONE) {
      return;
    }
    try {
      this.parser.setInput(input, null);
      parser.nextTag(); // Move to the first element.
      parser.require(XmlPullParser.START_TAG, null, null);
      if ("Error".equals(parser.getName())) {
        parseError();
      } else if ("body".equals(parser.getName())) {
        parseBody();
      } else {
        Log.w("DroidMuni", "Unexpected body element: " + parser.getName());
        result_state = ResultState.PARSE_ERROR;
      }
    } catch (XmlPullParserException e) {
      Log.d("DroidMuni", "Parse error with state " + dumpState(parser), e);
      result_state = ResultState.PARSE_ERROR;
    } catch (IOException e) {
      Log.d("DroidMuni", "IO error", e);
      result_state = ResultState.IO_ERROR;
    }
  }

  private static String dumpState(XmlPullParser parser) {
    String name = parser.getName();
    String text = parser.getText();
    String state;
    try {
      state = XmlPullParser.TYPES[parser.getEventType()];
    } catch (XmlPullParserException e) {
      state = "Unknown state";
    }
    return state + (name == null ? "" : " Name: " + name)
           + (text == null ? "" : " Text: " + text);
  }

  public enum ResultState {
    NOT_DONE, IO_ERROR, PARSE_ERROR, RETRY, MISSING_COOKIE, SUCCESS,
  }

  public ResultState getResult() {
    return result_state;
  }

  public String getCopyright() {
    return copyright;
  }

  protected final XmlPullParser parser = Xml.newPullParser();
  protected ResultState result_state = ResultState.NOT_DONE;
  protected String copyright;

  protected static String renderTag(XmlPullParser tag) {
    StringBuilder b = new StringBuilder();
    b.append("<").append(tag.getName());
    for (int i = 0; i < tag.getAttributeCount(); i++) {
      b.append(" ").append(tag.getAttributeName(i)).append("=\"");
      b.append(tag.getAttributeValue(i)).append("\"");
    }
    b.append(">");
    return b.toString();
  }

  protected abstract void parseBody() throws XmlPullParserException,
      IOException;

  /**
   * Parses a NextBus <Error> document. To see one of these, run 'curl
   * http://www .nextmuni.com/s/COM.NextBus.Servlets.XMLFeed?command=routeConfig
   * &a=sf-muni&r=71'
   */
  private void parseError() throws XmlPullParserException, IOException {
    parser.require(XmlPullParser.START_TAG, null, "Error");
    boolean shouldRetry = "true".equals(getAttr("shouldRetry"));
    if (shouldRetry) {
      Log.w("DroidMuni", "Unexpected error document with shouldRetry==true");
      result_state = ResultState.RETRY;
      return;
    }
    String content = parser.nextText();
    if (!content.contains("Feed can only be accessed by NextBus map page")) {
      Log.w("DroidMuni", "Unexpected content: " + content
                         + ". Trying again as if cookie was missing.");
    }
    result_state = ResultState.MISSING_COOKIE;
    return;
  }

  protected void skipToEndOfTag() throws XmlPullParserException, IOException {
    String tag_name = parser.getName();
    int depth = parser.getDepth();
    while (true) {
      parser.next();
      if (parser.getEventType() == XmlPullParser.END_TAG
          && parser.getDepth() == depth && parser.getName() == tag_name) {
        break;
      }
    }
  }

  /**
   * @return The value of the attribute named 'name' with any namespace on the
   *         current element.
   */
  protected String getAttr(String name) {
    return parser.getAttributeValue(null, name);
  }

}