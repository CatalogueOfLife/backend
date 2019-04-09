package org.col.api.model;

import java.util.regex.Pattern;

import org.col.api.datapackage.ColTerm;
import org.col.api.jackson.ApiModule;
import org.col.api.jackson.SerdeTestBase;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.ImportState;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Language;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

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
    d.setDescriptionCount(231456);
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
    d.getVernacularsByLanguageCount().put(Language.GERMAN, 12);
    d.getVernacularsByLanguageCount().put(Language.FRENCH, 12);
    d.getVernacularsByLanguageCount().put(Language.HINDI, 12);
    d.getVerbatimByTypeCount().put(DwcTerm.Taxon, 12342);
    d.getVerbatimByTypeCount().put(AcefTerm.AcceptedSpecies, 78);
    d.getVerbatimByTypeCount().put(ColTerm.Name, 641723);
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
    assertNull(d.getDataAccess());
    assertNull(d.getLogo());
    assertNull(d.getLicense());
  }
  
}