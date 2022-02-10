package life.catalogue.dw.jersey.provider;

import life.catalogue.api.vocab.Issue;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 *
 */
public class EnumParamConverterTest {

  EnumParamConverterProvider.EnumParamConverter<Issue> converter = new EnumParamConverterProvider.EnumParamConverter<Issue>(Issue.class);

  @Test
  public void rountrip() throws Exception {
    for (Issue value : Issue.values()) {
      String param = converter.toString(value);
      System.out.println(param);
      assertFalse(param.contains("\""));
      assertEquals(value, converter.fromString(param));
    }
  }

}