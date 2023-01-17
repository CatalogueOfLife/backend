package life.catalogue.metadata.eml;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Agent;
import life.catalogue.api.model.CitationTest;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetTest;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.License;
import life.catalogue.common.io.InputStreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import static org.junit.Assert.assertFalse;

public class EmlWriterTest {

  @Test
  public void eml() throws Exception {
    File f = File.createTempFile("col-eml", ".xml");
    try {
      Dataset d = DatasetTest.generateTestDataset();
      d.setCreator(List.of(
        Agent.person("Max", "Meier", "null@dev.null", "1234-5678-9012-3456"),
        Agent.person("Fax", "Feier")
      ));
      d.setPublisher(new Agent("Peter", "Publish"));
      d.setEditor(List.of(
        new Agent("Derek", "Dillinger's"),
        new Agent("Dan", "Dillinger's")
      ));
      d.setContributor(List.of(
        Agent.person("Max", "Meier", "null@dev.null", "1234-5678-9012-3456", "Collector"),
        Agent.person("Max", "Groningen", null, "0789-5678-9012-3455", "Programmer"),
        Agent.person("Morn", "Microfel", null, null, "Library research, UK"),
        Agent.person("Fax", "Feier")
      ));
      d.setLicense(License.CC0);
      d.setKeyword(List.of("Foo", "Bar Z"));
      d.setSource(List.of(
        CitationTest.create(),
        CitationTest.create()
      ));
      EmlWriter.write(d, f);

      String eml = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
      System.out.println(eml);
      assertFalse(eml.contains("COL backend services"));

      // try with empty agents
      d.setEditor(null);
      d.setContributor(new ArrayList<>());
      EmlWriter.write(d, f);

    } finally {
      f.delete();
    }
  }

  @Test
  @Ignore("Needs live prod API and is slow - for debugging EML writer only!")
  public void emlTestAllProdDatasets() throws Exception {
    IntSet keys = new IntOpenHashSet();
    keys.add(Datasets.COL);
    // old ACEF
    for (int i = 1000; i < 1210; i++) {
      keys.add(i);
    }
    // newly registered or released
    for (int i = 2000; i < 2280; i++) {
      keys.add(i);
    }

    File dir = new File("/tmp/eml");
    dir.mkdir();
    FileUtils.cleanDirectory(dir);
    for (int key : keys) {
      try {
        InputStream stream = new URL("http://api.catalogueoflife.org/dataset/" + key).openStream();
        String json = InputStreamUtils.readEntireStream(stream);
        Dataset d = ApiModule.MAPPER.readValue(json, Dataset.class);
        if (d == null) {
          System.out.println(String.format("\nNo dataset %s", key));
        } else {
          File f = new File(dir, "eml-" + key + ".xml");
          System.out.println(String.format("\n***** %s JSON *****\n", key));
          System.out.println(json);
          System.out.println(String.format("\n***** %s EML *****\n", key));
          EmlWriter.write(d, f);
          String eml = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
          System.out.println(eml);
        }
      } catch (IOException e) {
        System.out.println(e);
      }
    }
  }
}