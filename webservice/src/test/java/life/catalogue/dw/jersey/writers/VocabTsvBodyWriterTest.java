package life.catalogue.dw.jersey.writers;

import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.resources.VocabResource;

import org.gbif.api.vocabulary.Rank;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

public class VocabTsvBodyWriterTest extends TestCase {

  public void testIsWriteable() throws Exception {
    var bw = new VocabTsvBodyWriter();
    List<Map<String, Object>> list = VocabResource.enumList(Rank.class);
    Method testMethod = VocabResource.class.getMethod("enumList", Rank.class.getClass());
    var t = testMethod.getGenericReturnType();
    assertTrue(bw.isWriteable(list.getClass(), t, null, MoreMediaTypes.TEXT_TSV_TYPE));
  }

}