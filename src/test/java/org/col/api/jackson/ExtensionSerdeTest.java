package org.col.api.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.col.api.vocab.Extension;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class ExtensionSerdeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void testRoundtrip() throws IOException {

    for (Extension ext : Extension.values()) {
      Wrapper e = new Wrapper(ext);
      String json = MAPPER.writeValueAsString(e);
      System.out.println(json);
      assertEquals(e.extension, MAPPER.readValue(json, Wrapper.class).extension);
    }
  }

  public static class Wrapper {
    public Extension extension;

    public Wrapper(){}

    public Wrapper(Extension extension){
      this.extension = extension;
    }
  }
}
