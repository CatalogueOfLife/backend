package life.catalogue.metadata.coldp;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetTest;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DatasetYamlWriterTest {

  @Test
  public void write() throws IOException {
    Dataset d = DatasetTest.generateTestDataset();
    String yaml = print(d);

    assertTrue(yaml.contains("containerTitle:"));
    assertTrue(yaml.contains("doi: 10.80631"));
    assertTrue(yaml.contains("issued: 2024-11"));
    assertFalse(yaml.contains("orcidAsUrl"));
    assertTrue(yaml.contains("col: 1001"));

    d.setIdentifier(new HashMap<>());
    // expect no empty object
    yaml = print(d);
    assertFalse(yaml.contains("col: 1001"));
    assertFalse(yaml.contains("identifier:"));
  }

  String print(Dataset d) throws IOException {
    StringWriter writer = new StringWriter();
    DatasetYamlWriter.write(d, writer);
    String yaml = writer.toString();
    System.out.println(yaml);
    return yaml;
  }
}