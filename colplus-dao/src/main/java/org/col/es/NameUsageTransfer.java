package org.col.es;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.NameField;
import org.col.es.model.EsNameUsage;

import static org.col.common.util.CollectionUtils.notEmpty;

/**
 * Converts NameUsageWrapper instances to EsNameUsage documents.
 */
class NameUsageTransfer {

  // Writes out the entire EsNameUsage object to a JSON document
  private final ObjectWriter mainWriter;
  // Used to populate the "payload" field within EsNameUsage
  private final ObjectWriter payloadWriter;

  NameUsageTransfer(IndexConfig cfg) {
    this.mainWriter = cfg.getObjectWriter();
    this.payloadWriter =
        cfg.getMapper().writerFor(new TypeReference<NameUsageWrapper<? extends NameUsage>>() {});
  }

  String toEsDocument(NameUsageWrapper<? extends NameUsage> wrapper)
      throws JsonProcessingException {
    EsNameUsage enu = new EsNameUsage();
    if (notEmpty(wrapper.getVernacularNames())) {
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
    enu.setPayload(payloadWriter.writeValueAsString(wrapper));
    enu.setNameFields(getNonNullNameFields(wrapper.getUsage().getName()));
    return mainWriter.writeValueAsString(enu);
  }

  private static Set<NameField> getNonNullNameFields(Name name) {
    Set<NameField> fields = EnumSet.noneOf(NameField.class);
    for (NameField nf : NameField.values()) {
      switch (nf) {
        case BASIONYM_AUTHORS:
          if (name.getBasionymAuthorship() != null
              && notEmpty(name.getBasionymAuthorship().getAuthors())) {
            fields.add(nf);
          }
          break;
        case BASIONYM_EX_AUTHORS:
          if (name.getBasionymAuthorship() != null
              && notEmpty(name.getBasionymAuthorship().getExAuthors())) {
            fields.add(nf);
          }
          break;
        case BASIONYM_YEAR:
          if (name.getBasionymAuthorship() != null
              && name.getBasionymAuthorship().getYear() != null) {
            fields.add(nf);
          }
          break;
        case CANDIDATUS:
          if (name.isCandidatus()) {
            fields.add(nf);
          }
          break;
        case COMBINATION_AUTHORS:
          if (name.getCombinationAuthorship() != null
              && notEmpty(name.getCombinationAuthorship().getAuthors())) {
            fields.add(nf);
          }
          break;
        case COMBINATION_EX_AUTHORS:
          if (name.getCombinationAuthorship() != null
              && notEmpty(name.getCombinationAuthorship().getExAuthors())) {
            fields.add(nf);
          }
          break;
        case COMBINATION_YEAR:
          if (name.getCombinationAuthorship() != null
              && name.getCombinationAuthorship().getYear() != null) {
            fields.add(nf);
          }
          break;
        case CULTIVAR_EPITHET:
          addIfSet(fields, nf, name.getCultivarEpithet());
          break;
        case GENUS:
          addIfSet(fields, nf, name.getGenus());
          break;
        case INFRAGENERIC_EPITHET:
          addIfSet(fields, nf, name.getInfragenericEpithet());
          break;
        case INFRASPECIFIC_EPITHET:
          addIfSet(fields, nf, name.getInfraspecificEpithet());
          break;
        case NOM_STATUS:
          addIfSet(fields, nf, name.getNomStatus());
          break;
        case NOTHO:
          addIfSet(fields, nf, name.getNotho());
          break;
        case PUBLISHED_IN_ID:
          addIfSet(fields, nf, name.getPublishedInId());
          break;
        case PUBLISHED_IN_PAGE:
          addIfSet(fields, nf, name.getPublishedInPage());
          break;
        case REMARKS:
          addIfSet(fields, nf, name.getRemarks());
          break;
        case SANCTIONING_AUTHOR:
          addIfSet(fields, nf, name.getSanctioningAuthor());
          break;
        case SOURCE_URL:
          addIfSet(fields, nf, name.getSourceUrl());
          break;
        case SPECIFIC_EPITHET:
          addIfSet(fields, nf, name.getSpecificEpithet());
          break;
        case STRAIN:
          addIfSet(fields, nf, name.getStrain());
          break;
        case UNINOMIAL:
          addIfSet(fields, nf, name.getUninomial());
          break;
      }
    }
    return fields;
  }

  private static void addIfSet(Set<NameField> fields, NameField nf, Object val) {
    if (val != null) {
      fields.add(nf);
    }
  }

}
