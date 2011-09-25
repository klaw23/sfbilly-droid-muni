package info.yasskin.droidmuni;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
  private volatile SharedPreferences m_prefs;

  // This class's constructor will schedule these methods to get filled in the
  // background. If they don't get filled by the time the main Activity asks for
  // them, we'll return the defaults, and the user will lose their initial
  // settings.
  private String m_saved_line_selected = "";

  public PreferenceManager(final Activity prefs_activity) {
    Globals.EXECUTOR.execute(new Runnable() {
      public void run() {
        loadPreferences(prefs_activity);
      }
    });
  }

  public synchronized String getSavedLine() {
    return m_saved_line_selected;
  }

  public synchronized void setSelectedLine(String line) {
    m_saved_line_selected = line;
  }

  public void apply() {
    final SharedPreferences prefs = m_prefs;
    if (prefs == null) {
      // Something made the background preferences load take way too long. Just
      // abandon the current prefs change.
      return;
    }
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString("line", getSavedLine());
    try {
      // Technique borrowed from
      // http://code.google.com/p/zippy-android/source/browse/trunk/examples/SharedPreferencesCompat.java.
      Method apply_method = SharedPreferences.Editor.class.getMethod("apply");
      apply_method.invoke(editor);
      return;
    } catch (NoSuchMethodException e) {
      // Call commit() instead.
    } catch (IllegalAccessException e) {
      // Call commit() instead.
    } catch (InvocationTargetException e) {
      // Call commit() instead.
    }
    editor.commit();
  }

  // Called from a background thread.
  private void loadPreferences(Activity prefs_activity) {
    m_prefs = prefs_activity.getPreferences(Context.MODE_PRIVATE);
    String line_selected = safeGet(m_prefs, String.class, "line", "");
    synchronized (this) {
      // If the user has already selected another line, don't overwrite that.
      if ("".equals(m_saved_line_selected)) {
        m_saved_line_selected = line_selected;
      }
    }
  }

  /**
   * Retrieves the preference named 'name' of type 'T' from 'pref'. If the
   * preference is not present or has the wrong type, returns 'defalt'.
   */
  @SuppressWarnings("unchecked")
  private static <T> T safeGet(SharedPreferences pref, Class<T> type,
      String name, T defalt) {
    Object value = pref.getAll().get(name);
    if (value == null || !type.isInstance(value)) {
      return defalt;
    }
    return (T) value;
  }
}
