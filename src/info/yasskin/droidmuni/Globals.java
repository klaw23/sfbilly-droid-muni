package info.yasskin.droidmuni;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Globals {
  // Remember to change this to false for releases.
  public static final boolean DEVELOPER_MODE = true;

  public static ExecutorService EXECUTOR = Executors.newCachedThreadPool();
}
