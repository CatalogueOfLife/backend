package life.catalogue.api.model;

import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.*;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class DatasetImportTest extends SerdeTestBase<DatasetImport> {
  
  public DatasetImportTest() {
    super(DatasetImport.class);
  }
  
  @Override
  public DatasetImport genTestValue() throws Exception {
    DatasetImport d = new DatasetImport();
    d.setAttempt(12);
    d.setDatasetKey(Datasets.DRAFT_COL);
    d.setState(ImportState.FINISHED);
    d.setTreatmentCount(231456);
    d.setDistributionCount(232456);
    d.setNameCount(2314453);
    d.setMediaCount(2314);
    d.setVerbatimCount(231426);
    d.setTaxonCount(231451);
    d.getIssuesCount().put(Issue.ACCEPTED_NAME_MISSING, 432);
    d.getIssuesCount().put(Issue.BASIONYM_ID_INVALID, 2);
    d.getIssuesCount().put(Issue.CLASSIFICATION_RANK_ORDER_INVALID, 4312);
    d.getNamesByRankCount().put(Rank.FORMA_SPECIALIS, 432);
    d.getNamesByRankCount().put(Rank.SPECIES_AGGREGATE, 12);
    d.getVernacularsByLanguageCount().put("deu", 12);
    d.getVernacularsByLanguageCount().put("fra", 12);
    d.getVernacularsByLanguageCount().put("hin", 12);
    d.getVerbatimByTermCount().put(DwcTerm.Taxon, 12342);
    d.getVerbatimByTermCount().put(AcefTerm.AcceptedSpecies, 78);
    d.getVerbatimByTermCount().put(ColdpTerm.Name, 641723);
    d.getNameRelationsByTypeCount().put(NomRelType.BASIONYM, 12);
    d.getNameRelationsByTypeCount().put(NomRelType.LATER_HOMONYM, 3);
    d.getTaxonRelationsByTypeCount().put(TaxRelType.EQUALS, 121);
    d.getTaxonRelationsByTypeCount().put(TaxRelType.INCLUDES, 7);
    d.getTaxonRelationsByTypeCount().put(TaxRelType.POLLINATES, 34);
    return d;
  }
  
  @Test
  public void testIssueSerialisation() throws Exception {
    String json = serialize();
    // make sure issue (enum) names are lower cased
    assertFalse(Pattern.compile("[A-Z]{2,}").matcher(json).find());
  }
  
  @Test
  public void testEmptyString() throws Exception {
    String json = ApiModule.MAPPER.writeValueAsString(genTestValue());
    json = json.replaceAll("www\\.gbif\\.org", "");
    json = json.replaceAll("cc0", "");
    
    Dataset d = ApiModule.MAPPER.readValue(json, Dataset.class);
    assertNull(d.getWebsite());
    assertNull(d.getLogo());
    assertNull(d.getLicense());
  }
  
}