package org.col.db.mapper;

import org.col.api.TestEntityGenerator;
import org.col.api.model.ColSource;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TreeMapperTest extends MapperTestBase<TreeMapper> {
  
  private ColSource source;
  private final int dataset11 = TestEntityGenerator.DATASET11.getKey();
  
  public TreeMapperTest() {
    super(TreeMapper.class);
  }
  
  @Before
  public void initSource() {
    source = ColSourceMapperTest.create();
    mapper(ColSourceMapper.class).create(source);
  }
  
  @Test
  public void root() {
    assertEquals(2, mapper().root(dataset11).size());
  }
  
  @Test
  public void parents() {
    assertEquals(1, mapper().parents(dataset11, "root-1").size());
  }
  
  @Test
  public void children() {
    assertEquals(0, mapper().children(dataset11, "root-1").size());
  }
}