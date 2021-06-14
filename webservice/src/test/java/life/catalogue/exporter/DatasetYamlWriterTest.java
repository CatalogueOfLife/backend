package life.catalogue.exporter;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetTest;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DatasetYamlWriterTest {

  @Test
  public void write() throws IOException {
    Dataset d = DatasetTest.generateTestDataset();
    StringWriter writer = new StringWriter();
    DatasetYamlWriter.write(d, writer);

    String yaml = writer.toString();
    System.out.println(yaml);

    assertTrue(yaml.contains("containerTitle:"));
    assertTrue(yaml.contains("doi: 10.80631"));
    assertTrue(yaml.contains("issued: 2024-11"));
  }
}