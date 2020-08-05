package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.db.SectorProcessable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class ProcessableSectorTest extends MapperTestBase<NameUsageMapper> {

  DSID<Integer> secKey = DSID.of(3,1);

  final Class<? extends SectorProcessable<?>> mapperClass;

  public ProcessableSectorTest(Class<? extends SectorProcessable<?>> mapperClass) {
    this.mapperClass = mapperClass;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    List<Class<? extends SectorProcessable<?>>> mapper = List.of(
      NameUsageMapper.class,
      NameMapper.class,
      ReferenceMapper.class,
      TypeMaterialMapper.class
    );
    return mapper.stream()
      .map(mc -> new Object[]{mc})
      .collect(Collectors.toList());
  }

  @Test
  public void deleteBySector() {
    System.out.println(mapperClass);
    mapper(mapperClass).deleteBySector(secKey);
  }

  @Test
  public void removeSectorKey() {
    mapper(mapperClass).removeSectorKey(secKey);
  }

  @Test
  public void processSector() {
    mapper(mapperClass).processSector(secKey).forEach(System.out::println);
  }
}
