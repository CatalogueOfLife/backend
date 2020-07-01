package life.catalogue.es;

import java.io.IOException;
import java.io.InputStream;
import life.catalogue.api.search.NameUsageSearchResponse;

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

}
