package org.col.pgtest;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Integration tests of DataPackageMapper.
 */
@Ignore
public class DataPackageFileTest extends BMTest {


  /**
   * Initializes the MyBatis module.
   */
  @BeforeClass
  public static void init() {
    System.out.println("Init my test class");
  }

  /**
   * Tests methods create and get.
   */
  @Test
  public void testCreate() {
    System.out.println("run testCreate");
  }

}
