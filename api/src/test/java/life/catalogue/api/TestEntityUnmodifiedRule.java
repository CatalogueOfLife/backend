package life.catalogue.api;

import org.junit.rules.ExternalResource;

public class TestEntityUnmodifiedRule extends ExternalResource {

  @Override
  protected void before() throws Throwable {
    TestEntityGenerator.throwIfObjectsChanged();
  }
}
