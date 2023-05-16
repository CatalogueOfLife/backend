package life.catalogue.metadata.eml;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.License;
import life.catalogue.common.io.InputStreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import static org.junit.Assert.*;

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
        Agent.person("Fax", "Feier"),
        Agent.organisation("Species 2000")
      ));
      d.setLicense(License.OTHER);
      d.setLogo(URI.create("http://huhu.me"));
      d.setTaxonomicScope("tax scope");
      d.setVersion("134.17");
      d.setKeyword(List.of("Foo", "Bar Z"));
      var c = createCitation();
      c.setTitle("Bad something & nothing <else>");
      d.setSource(List.of(
        c,
        createCitation(),
        Citation.create("Unparsed bad something & nothing <else>.")
      ));
      EmlWriter.write(d, f);

      String eml = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
      System.out.println(eml);
      assertFalse(eml.contains("COL backend services"));
      assertTrue(eml.contains("Species 2000"));
      assertFalse(eml.contains("intellectualRights"));

      d.setLicense(License.CC0);
      EmlWriter.write(d, f);
      eml = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
      System.out.println(eml);
      assertTrue(eml.contains("intellectualRights"));

      // roundtrip?
      Dataset d2 = EmlParser.parse(IOUtils.toInputStream(eml, StandardCharsets.UTF_8), StandardCharsets.UTF_8).get().getDataset();
      // copy properties not in EML
      d2.setKey(d.getKey());
      d2.setSourceKey(d.getSourceKey());
      d2.setType(d.getType());
      d2.setOrigin(d.getOrigin());
      d2.setNotes(d.getNotes());
      d2.getContributor().get(3).setNote(null); // we will get contributor othertwise
      d2.getContributor().get(4).setNote(null); // we will get contributor othertwise
      d.setUrl(URI.create("http://www.gbif.org")); // we normalise the URL

      assertEquals(d, d2);
      // try with empty agents
      d.setEditor(null);
      d.setContributor(new ArrayList<>());
      EmlWriter.write(d, f);

    } finally {
      f.delete();
    }
  }

  private Citation createCitation() {
    var c = CitationTest.create();
    c.setId(c.getDoi().getUrl().toString());
    return c;
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