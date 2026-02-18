package life.catalogue.es;

import com.fasterxml.jackson.core.JsonProcessingException;

import life.catalogue.api.vocab.Issue;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class EsModuleTest {

  @Test
  void contentMapper() throws JsonProcessingException {
    EsNameUsage doc = newDocument();
    var json = EsModule.write(doc);
    System.out.println(json);
    assertTrue(json.contains("ACCEPTED"));
    assertTrue(json.contains(String.valueOf(Rank.FAMILY.ordinal())));
  }

  private static EsNameUsage newDocument() {
    EsNameUsage doc = new EsNameUsage();
    doc.setNameStrings(new NameStrings());
    doc.setRank(Rank.FAMILY);
    doc.setDatasetKey(1222);
    doc.setUsageId("123");
    doc.setScientificName("Abies alba");
    doc.setStatus(TaxonomicStatus.ACCEPTED);
    doc.setNomCode(NomCode.BOTANICAL);
    doc.setIssues(EnumSet.of(Issue.ACCEPTED_NAME_MISSING, Issue.ACCEPTED_ID_INVALID));
    return doc;
  }
}