package org.col.db.mapper.pgtest;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Integration tests of DataPackageMapper.
 */
public class DataPackageFileMapperTest  extends BaseMapperTest {


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
