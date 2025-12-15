package life.catalogue.metadata.coldp;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DatasetJsonWriterTest {

  // same as in WsMatchingServer
  private abstract class DatasetSizeMixin {
    @JsonProperty(access = JsonProperty.Access.READ_WRITE)
    private Integer size;
  }

  @Test
  public void write() throws IOException {
    ApiModule.MAPPER.addMixIn(Dataset.class, DatasetSizeMixin.class);

    Dataset d = DatasetTest.generateTestDataset();
    d.setSize(1234567);
    String json = print(d);

    assertTrue(json.contains("size"));

    InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    var d2 = ColdpMetadataParser.readJSON(stream)
      .get()
      .getDataset();

    assertEquals(d.getSize(), d2.getSize());
  }

  String print(Dataset d) throws IOException {
    StringWriter writer = new StringWriter();
    DatasetJsonWriter.write(d, writer);
    String json = writer.toString();
    System.out.println(json);
    return json;
  }
}