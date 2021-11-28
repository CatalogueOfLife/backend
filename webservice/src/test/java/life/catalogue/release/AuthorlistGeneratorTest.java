package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.Setting;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static life.catalogue.api.model.Agent.organisation;
import static life.catalogue.api.model.Agent.person;
import static org.junit.Assert.assertEquals;

public class AuthorlistGeneratorTest {

  @Test
  public void appendSourceAuthors() throws Exception {
    var gen = new AuthorlistGenerator();

    Dataset d = new Dataset();
    d.setCreator(List.of(person("Markus", "Döring")));
    d.setContributor(List.of(person("Frank", "Berril"), person("Brant", "Spar")));

    var frank = person("F", "Berril", null, "1234");

    List<Dataset> sources = new ArrayList<>();
    var s1 = new Dataset();
    s1.setCreator(List.of(frank, person("Arri", "Rønsen"), organisation("FAO")));
    sources.add(s1);

    var s2 = new Dataset();
    s2.setCreator(List.of(person("Gerry", "Newman"), person("Arri", "Rønsen"), organisation("GBIF")));
    s2.setEditor(List.of(person("A.F.", "Beril", null, frank.getOrcid())));
    sources.add(s2);

    var ds = new DatasetSettings();
    ds.enable(Setting.RELEASE_ADD_SOURCE_AUTHORS);
    ds.enable(Setting.RELEASE_ADD_CONTRIBUTORS);
    gen.appendSourceAuthors(d, sources, ds);
    assertEquals(7, d.getCreator().size());
    assertEquals(person("Markus", "Döring"), d.getCreator().get(0));

    d = new Dataset();
    ds.disable(Setting.RELEASE_ADD_CONTRIBUTORS);
    gen.appendSourceAuthors(d, sources, ds);
    assertEquals(6, d.getCreator().size());
  }

}