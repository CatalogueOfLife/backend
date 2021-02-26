package life.catalogue.api.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.FacetValue;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.vocab.Environment;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.gbif.dwc.terms.TermFactory;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class ApiModuleTest {
  
  @Test
  public void testInit() throws IOException {
    assertNotNull( ApiModule.MAPPER );
  }

  @Test
  public void testTermFactory() throws IOException {
    ObjectMapper map = ApiModule.MAPPER;

    for( String t : List.of(
      "acef:AcceptedInfraSpecificTaxa",
      "acef:AcceptedSpecies",
      "acef:CommonNames",
      "acef:Distribution",
      "acef:NameReferencesLinks",
      "acef:Reference",
      "acef:Synonyms",
      "col:Description",
      "col:Distribution",
      "col:Media",
      "col:Name",
      "col:NameRel",
      "col:NameRelation",
      "col:NameUsage",
      "col:Reference",
      "col:SpeciesEstimate",
      "col:SpeciesInteraction",
      "col:Synonym",
      "col:Taxon",
      "col:TypeMaterial",
      "col:VernacularName",
      "dwc:Taxon",
      "gbif:Description",
      "gbif:Distribution",
      "gbif:Multimedia",
      "gbif:Reference",
      "gbif:VernacularName",
      "http://bibtex.org/BibTeX",
      "bib:BibTeX",
      "http://unknown.org/col/Description",
      "tt:Tree"
    )) {
      System.out.println(t);
      System.out.println( TermFactory.instance().findTerm(t, true) );
    }
  }
  
  @Test
  public void testJsonCreator() throws IOException {
    DSIDValue did1 = new DSIDValue(123, "peter");
    String json = ApiModule.MAPPER.writeValueAsString(did1);
    DSIDValue did2 = ApiModule.MAPPER.readValue(json, DSIDValue.class);
    assertEquals(did1, did2);
  }

  @Test
  public void localDate() throws IOException {
    LocalDate date = LocalDate.now();
    String json = ApiModule.MAPPER.writeValueAsString(date);
    System.out.println(json);
    LocalDate date2 = ApiModule.MAPPER.readValue(json, LocalDate.class);
    assertEquals(date2, date);
  }
  
  @Test
  public void testEnum() throws IOException {
    EditorialDecision ed = new EditorialDecision();
    ed.setDatasetKey(4321);
    ed.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    ed.setEnvironments(Set.of(Environment.MARINE, Environment.FRESHWATER));
    ed.setMode(EditorialDecision.Mode.UPDATE);
    
    String json = ApiModule.MAPPER.writeValueAsString(ed);
    assertTrue(json.contains("provisionally accepted"));
    EditorialDecision ed2 = ApiModule.MAPPER.readValue(json, EditorialDecision.class);
    assertEquals(ed, ed2);
  }

  @Test
  public void testSearchParameters() throws IOException {
    NameUsageSearchResponse resp = new NameUsageSearchResponse(
      new Page(1,17), 69,
      Collections.EMPTY_LIST,
      Map.of(
        NameUsageSearchParameter.NOM_STATUS, Set.of(
          new FacetValue(NomStatus.NOT_ESTABLISHED, 13),
          new FacetValue(NomStatus.CHRESONYM, 3)
        ),
        NameUsageSearchParameter.DATASET_KEY, Set.of(
          new FacetValue(3, 13),
          new FacetValue(13, 43),
          new FacetValue(23, 52)
        )
      )
    );

    String json = ApiModule.MAPPER.writeValueAsString(resp);
    System.out.println(json);
    assertTrue(json.contains("\"nomStatus\""));
    assertFalse(json.contains("\"NOM_STATUS\""));
    assertFalse(json.contains("\"nom status\""));
    assertTrue(json.contains("\"datasetKey\""));
    assertTrue(json.contains("\"not established\""));
    assertTrue(json.contains("\"chresonym\""));
    assertFalse(json.contains("\"CHRESONYM\""));
    // we do not read back in json as FacetValue has not appropriate constructor
  }
}