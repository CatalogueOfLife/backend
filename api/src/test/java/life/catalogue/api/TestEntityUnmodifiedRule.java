package life.catalogue.api;

import org.junit.rules.ExternalResource;

public class TestEntityUnmodifiedRule extends ExternalResource {

  @Override
  protected void before() throws Throwable {
    if (TestEntityGenerator.hasObjectsChanged()) {
      throw new IllegalStateException("static test instances have been changed before the test");
    }
  }

  @Override
  protected void after() {
    if (TestEntityGenerator.hasObjectsChanged()) {
      throw new IllegalStateException("static test instances have been changed after the test");
    }
  }
}
