package life.catalogue.exporter;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.License;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DwcaExporterTest extends ExporterTest {

  @Test
  public void dataset() {
    DwcaExporter exp = DwcaExporter.dataset(TestDataRule.APPLE.key, Users.TESTER, PgSetupRule.getSqlSessionFactory(), dir);
    exp.run();

    assertTrue(exp.getArchive().exists());
  }

  @Test
  public void eml() throws Exception {
    File f = File.createTempFile("col-eml", ".xml");
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);

      Dataset d = dm.get(TestDataRule.APPLE.key);
      d.getAuthors().add(new Person("Max", "Meier", "null@dev.null", "1234-5678-9012-3456"));
      d.getAuthors().add(new Person("Fax", "Feier", null, null));
      d.getEditors().add(new Person("Derek", "Dillinger"));
      d.setLicense(License.CC0);
      DwcaExporter.writeEml(d, f);

      String eml = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
      System.out.println(eml);
      assertFalse(eml.contains("COL backend services"));

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
        InputStream stream = new URL("http://api.catalogueoflife.org/dataset/"+key).openStream();
        String json = InputStreamUtils.readEntireStream(stream);
        Dataset d = ApiModule.MAPPER.readValue(json, Dataset.class);
        if (d == null) {
          System.out.println(String.format("\nNo dataset %s", key));
        } else {
          File f = new File(dir,"eml-"+key+".xml");
          System.out.println(String.format("\n***** %s JSON *****\n", key));
          System.out.println(json);
          System.out.println(String.format("\n***** %s EML *****\n", key));
          DwcaExporter.writeEml(d, f);
          String eml = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
          System.out.println(eml);
        }
      } catch (IOException e){
        System.out.println(e);
      }
    }
  }
}