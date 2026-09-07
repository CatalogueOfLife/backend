package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Treatment;
import life.catalogue.api.vocab.TreatmentFormat;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class TreatmentMapperTest extends MapperTestBase<TreatmentMapper> {


	public TreatmentMapperTest() {
		super(TreatmentMapper.class);
	}

	@Test
  public void getEmpty() throws Exception {
    assertNull(mapper().get(DSID.of(11, "")));
  }

  @Test
  public void roundtrip() throws Exception {
    Treatment t1 = new Treatment();
    t1.setVerbatimKey(TestEntityGenerator.VERBATIM_KEY1);
    t1.setDatasetKey(TestEntityGenerator.TAXON1.getDatasetKey());
    t1.setId(TestEntityGenerator.TAXON1.getId());
    t1.setDocument("Oh mein lieber Augustin");
    t1.setFormat(TreatmentFormat.PLAIN_TEXT);
    TestEntityGenerator.setUser(t1);

    mapper().create(t1);
    commit();

    Treatment t2 = mapper().get(t1.getKey());
    //printDiff(t1, t2);
    assertEquals(t1, t2);

    // the batch form the archive export of a subtree uses. A treatment's own id IS the taxon id.
    var batch = mapper().listByTaxa(t1.getDatasetKey(), List.of(t1.getId()));
    assertEquals(1, batch.size());
    assertEquals(t1, batch.get(0));
    assertTrue(mapper().listByTaxa(t1.getDatasetKey(), List.of("no-such-taxon")).isEmpty());
    assertEquals(1, mapper().listByTaxa(t1.getDatasetKey(), List.of("nope", t1.getId())).size());
  }
}