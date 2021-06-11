package life.catalogue.exporter;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.model.Citation;

import life.catalogue.api.model.CitationTest;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetTest;
import life.catalogue.jackson.YamlMapper;

import org.apache.poi.ss.formula.functions.T;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.Assert.*;

public class DatasetYamlWriterTest {

  @Test
  public void write() throws IOException {
    Dataset d = DatasetTest.generateTestDataset();
    StringWriter writer = new StringWriter();
    DatasetYamlWriter.write(d, writer);

    String yaml = writer.toString();
    System.out.println(yaml);

    assertTrue(yaml.contains("collection-title:"));
    assertTrue(yaml.contains("doi: 10.80631"));
    assertTrue(yaml.contains("issued: 2024-11"));
  }
}