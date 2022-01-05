package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class PageTest {

  @Test
  public void testNormal() {
    var p = new Page();
    p = new Page(0, 100);
    p = new Page(99999, 999);
    p = new Page(100000, 1000);
  }

  @Test(expected = IllegalArgumentException.class)
  public void deepPaging() {
    var p = new Page(100001, 10);
  }
}