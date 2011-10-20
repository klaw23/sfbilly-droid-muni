package info.yasskin.droidmuni.test;

import java.util.concurrent.TimeUnit;

import org.hamcrest.Description;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.internal.StatePredicate;
import org.jmock.lib.concurrent.Synchroniser;

public class WaitUntilAction implements Action {
  private final Synchroniser m_synchroniser;
  private final StatePredicate m_state;
  private final long m_timeout_ms;

  public WaitUntilAction(Synchroniser synchroniser, StatePredicate p,
      long timeout, TimeUnit timeunit) {
    m_synchroniser = synchroniser;
    m_state = p;
    m_timeout_ms = timeunit.toMillis(timeout);
  }

  public void describeTo(Description description) {
    description.appendText("waits until ");
    m_state.describeTo(description);
  }

  public Object invoke(Invocation invocation) {
    try {
      m_synchroniser.waitUntil(m_state, m_timeout_ms);
    } catch (InterruptedException e) {
      // Just return early.
    }
    return null;
  }

}
