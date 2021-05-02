package life.catalogue.es.ddl;

import life.catalogue.es.EsModule;
import life.catalogue.es.EsNameUsage;
import org.junit.Test;

/**
 * Only tests that we don't get exceptions while generating the document type mappings. Plus peek at what's being generated.
 */
public class MappingFactoryTest {

  @Test
  public void getMapping1a() {
    MappingsFactory mf = MappingsFactory.usingFields();
    mf.setMapEnumToInt(true);
    Mappings mapping = mf.getMapping(EsNameUsage.class);
    System.out.println(EsModule.writeDebug(mapping));
  }

  @Test
  public void getMapping1b() {
    MappingsFactory mf = MappingsFactory.usingFields();
    mf.setMapEnumToInt(false);
    Mappings mapping = mf.getMapping(EsNameUsage.class);
    System.out.println(EsModule.writeDebug(mapping));
  }

  @Test
  public void getMapping2a() {
    MappingsFactory mf = MappingsFactory.usingGetters();
    mf.setMapEnumToInt(true);
    Mappings mapping = mf.getMapping(EsNameUsage.class);
    System.out.println(EsModule.writeDebug(mapping));
  }

  @Test
  public void getMapping2b() {
    MappingsFactory mf = MappingsFactory.usingGetters();
    mf.setMapEnumToInt(true);
    Mappings mapping = mf.getMapping(EsNameUsage.class);
    System.out.println(EsModule.writeDebug(mapping));
  }

}
