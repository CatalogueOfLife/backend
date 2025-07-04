package life.catalogue.release;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.dao.DatasetSourceDao;

import java.util.ArrayList;
import java.util.List;

import life.catalogue.db.mapper.DatasetSourceMapper;

import org.junit.Test;

import static life.catalogue.api.model.Agent.organisation;
import static life.catalogue.api.model.Agent.person;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class AuthorlistGeneratorTest {

  @Test
  public void appendSourceAuthors() throws Exception {
    final int projectKey = 10;
    var dao = mock(DatasetSourceDao.class);

    final List<DatasetSourceMapper.SourceDataset> sources = new ArrayList<>();
    final var frankOrcid = "1234-5678-9999-0000";
    var s1 = new DatasetSourceMapper.SourceDataset();
    s1.setAlias("DS1");
    s1.setCreator(List.of(
      person("F", "Berril", null, frankOrcid),
      person("Arri", "Rønsen"), organisation("FAO")
    ));
    sources.add(s1);

    var s2 = new DatasetSourceMapper.SourceDataset();
    s2.setAlias("ALIAS");
    s2.setCreator(List.of(
      person("Gerry", "Newman"),
      person("Arri", "Rønsen"),
      organisation("GBIF")
    ));
    s2.setEditor(List.of(
      person("A.F.", "Beril", null, frankOrcid)
    ));
    sources.add(s2);

    doReturn(sources).when(dao).listSimple(anyInt(), anyBoolean(), anyBoolean());

    var gen = new AuthorlistGenerator(dao);

    final Dataset proj = new Dataset();
    proj.setKey(projectKey);
    final Agent markus = person("Markus", "Döring", "hit@me.com", "1111-2222-3333-4444", "Drummer");
    proj.setCreator(List.of(markus));
    proj.setContributor(List.of(person("Frank", "Berril"), person("Brant", "Spar")));

    Dataset d = new Dataset(proj);
    d.setKey(projectKey + 1);
    assertEquals(1, d.getCreator().size());
    assertEquals(2, d.getContributor().size());

    var cfg = new ProjectReleaseConfig.MetadataConfig();
    cfg.addSourceAuthors = true;
    cfg.additionalCreators = List.of(
      person("Berit", "Schneider"),
      person("Anna", "Bella")
    );
    gen.appendSourceAuthors(d, cfg);
    assertEquals(8, d.getCreator().size());
    assertEquals(markus, d.getCreator().get(0));
    assertEquals(2, d.getContributor().size());

    d = new Dataset(proj);
    d.setKey(projectKey + 2);
    gen.appendSourceAuthors(d, cfg);
    assertEquals(8, d.getCreator().size());
    assertEquals(proj.getContributor().size(), d.getContributor().size());

    var s3 = new DatasetSourceMapper.SourceDataset();
    s3.setKey(projectKey + 3);
    s3.setAlias("ALIAS");
    s3.setCreator(List.of(person("Markus", "Döring", "markus@vegan.pork", null, "Vegan")));
    sources.add(s3);


    var s4 = new DatasetSourceMapper.SourceDataset();
    s4.setKey(projectKey + 4);
    s4.setAlias("ALIs4");
    s4.setCreator(List.of(
      person("Henry D.", "Agudelo Zamora"),
      person("Henry D.", "Agudelo-Zamora")
    ));
    sources.add(s4);

    d = new Dataset(proj);
    d.setKey(projectKey + 10);
    gen.appendSourceAuthors(d, cfg);
    assertEquals(9, d.getCreator().size());

    assertEquals("Drummer; Vegan; ALIAS", d.getCreator().get(0).getNote());
    assertEquals("Henry D.", d.getCreator().get(1).getGiven());
    assertEquals("Agudelo Zamora", d.getCreator().get(1).getFamily());
    assertEquals("ALIAS, DS1", d.getCreator().get(5).getNote());
  }
}