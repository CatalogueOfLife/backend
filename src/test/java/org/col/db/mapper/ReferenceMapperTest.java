package org.col.db.mapper;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.col.api.Reference;
import org.gbif.utils.text.StringUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class ReferenceMapperTest extends MapperTestBase<ReferenceMapper> {

  public ReferenceMapperTest() {
    super(ReferenceMapper.class);
  }

  private Reference create() throws Exception {
    Reference d = new Reference();
    d.setDataset(D1);
    d.setId(StringUtils.randomString(8));
    d.setYear(1988);
    d.setCsl(createCsl());
    return d;
  }

  private ObjectNode createCsl() {
    JsonNodeFactory factory = JsonNodeFactory.instance;
    ObjectNode csl = factory.objectNode();
    csl.put("title", StringUtils.randomString(80));
    csl.put("container-title", StringUtils.randomString(100));
    csl.put("publisher", "Springer");
    csl.put("year", "1988");
    csl.put("doi", "doi:10.1234/" + StringUtils.randomString(20));
    return csl;
  }

  @Test
  public void roundtrip() throws Exception {
    Reference r1 = create();
    mapper().create(r1);

    commit();

    Reference r2 = mapper().getByKey(r1.getKey());

    assertEquals(r1, r2);
  }

}