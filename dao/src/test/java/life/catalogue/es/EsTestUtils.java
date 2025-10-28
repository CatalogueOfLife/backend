package life.catalogue.es;

import life.catalogue.api.search.NameUsageSearchResponse;

import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;

import java.io.IOException;
import java.io.InputStream;

public class EsTestUtils {

  public static void indexCrocodiles(EsReadTestBase testClass) throws IOException {
    testClass.index(getCrocodiles().getResult());
  }

  public static NameUsageSearchResponse getCrocodiles() throws IOException {
    InputStream is = EsTestUtils.class.getResourceAsStream("/elastic/Crocodylidae.json");
    return EsModule.readObject(is, NameUsageSearchResponse.class);
  }

  public static void indexCheilanthes(EsReadTestBase testClass) throws IOException {
    testClass.index(getCheilanthes().getResult());
  }

  public static NameUsageSearchResponse getCheilanthes() throws IOException {
    InputStream is = EsTestUtils.class.getResourceAsStream("/elastic/Cheilanthes.json");
    return EsModule.readObject(is, NameUsageSearchResponse.class);
  }

  public static void printDiff(Object o1, Object o2) {
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(o1, o2);
    System.out.println(diff);
  }

}
