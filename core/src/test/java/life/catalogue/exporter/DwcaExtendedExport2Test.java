package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.util.RankUtils;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.coldp.ColdpTerm;

import org.gbif.dwc.terms.DwcTerm;

import org.junit.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DwcaExtendedExport2Test {

    @Test
    public void dwcRankTerms() {
      var exp = new DwcaExtendedExport(new ExportRequest(), 1, null, null, null);
      var terms = exp.define(EntityType.NAME_USAGE);
      var set = Arrays.stream(terms).collect(Collectors.toSet());
      for (var term : RankUtils.RANK2DWC.values()) {
        assertTrue(set.contains(term));
      }
    }
}