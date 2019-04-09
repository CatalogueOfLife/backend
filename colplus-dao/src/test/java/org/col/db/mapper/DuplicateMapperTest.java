package org.col.db.mapper;

import org.col.api.model.Page;
import org.col.api.vocab.EqualityMode;
import org.junit.Test;

public class DuplicateMapperTest extends MapperTestBase<DuplicateMapper> {
  
  public DuplicateMapperTest() {
    super(DuplicateMapper.class);
  }
  
  
  @Test
  public void find() throws Exception {
    Page p = new Page();
    mapper().find(11, EqualityMode.CANONICAL, null, null, null, null, p);
    
  }
  
}