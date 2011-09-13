package info.yasskin.droidmuni.test;

import android.database.MatrixCursor;

public class CursorLiteral extends MatrixCursor {
  public CursorLiteral(String... columnNames) {
    super(columnNames);
  }

  public CursorLiteral row(Object... columnValues) {
    addRow(columnValues);
    return this;
  }
}
