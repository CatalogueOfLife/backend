package org.col.es;

import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;

/**
 * Converts NameUsageWrapper instances to EsNameUsage instances.
 */
public class NameUsageTransfer {

  // Writes out the entire EsNameUsage object to a JSON document
  private final ObjectWriter mainWriter;
  // Used to populate the "source" field within EsNameUsage
  private final ObjectWriter sourceWriter;

  public NameUsageTransfer(IndexConfig nameUsageConfig) {
    this.mainWriter = nameUsageConfig.getObjectWriter();
    this.sourceWriter = nameUsageConfig.getMapper()
        .writerFor(new TypeReference<NameUsageWrapper<? extends NameUsage>>() {});
  }

  public String toEsDocument(NameUsageWrapper<? extends NameUsage> wrapper)
      throws JsonProcessingException {
    EsNameUsage enu = new EsNameUsage();
    if (wrapper.getVernacularNames() != null) {
      enu.setVernacularNames(
          wrapper.getVernacularNames().stream().map(VernacularName::getName).collect(
              Collectors.toList()));
    }
    enu.setIssues(wrapper.getIssues());
    Name name = wrapper.getUsage().getName();
    enu.setAuthorship(name.authorshipComplete()); // TODO: Is this correct !!??
    enu.setDatasetKey(name.getDatasetKey());
    enu.setNameId(name.getId());
    enu.setNameIndexId(name.getIndexNameId());
    enu.setNomStatus(name.getNomStatus());
    enu.setPublishedInId(name.getPublishedInId());
    enu.setRank(name.getRank());
    enu.setScientificName(name.getScientificName());
    enu.setStatus(wrapper.getUsage().getStatus());
    if (wrapper.getUsage().getClass() == Taxon.class) {
      enu.setTaxonId(((Taxon) wrapper.getUsage()).getId());
    }
    enu.setType(name.getType());
    enu.setSource(sourceWriter.writeValueAsString(wrapper));
    return mainWriter.writeValueAsString(enu);
  }

}
