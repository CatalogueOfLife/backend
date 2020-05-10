package life.catalogue.db.mapper;

import life.catalogue.db.SectorProcessable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class ProcessableSectorTest extends MapperTestBase<NameUsageMapper> {

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
    mapper(mapperClass).deleteBySector(3,1);
  }

  @Test
  public void removeSectorKey() {
    mapper(mapperClass).removeSectorKey(3,1);
  }

  @Test
  public void processSector() {
    mapper(mapperClass).processSector(3,1).forEach(System.out::println);
  }
}
