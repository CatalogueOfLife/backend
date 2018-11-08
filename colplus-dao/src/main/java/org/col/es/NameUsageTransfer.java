package org.col.es;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.col.api.model.Name;
import org.col.api.model.NameUsage;
import org.col.api.model.Taxon;
import org.col.api.model.VernacularName;
import org.col.api.search.NameUsageWrapper;
import org.col.api.vocab.NameField;
import org.col.es.model.EsNameUsage;

import static org.col.api.vocab.NameField.*;
import static org.col.common.util.CollectionUtils.notEmpty;

/**
 * Converts NameUsageWrapper instances to EsNameUsage documents.
 */
class NameUsageTransfer {
  
  EsNameUsage toEsDocument(NameUsageWrapper<? extends NameUsage> wrapper) throws JsonProcessingException {
    
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
    enu.setPayload(EsModule.NAME_USAGE_WRITER.writeValueAsString(wrapper));
    enu.setNameFields(getNonNullNameFields(wrapper.getUsage().getName()));
    return enu;
  }
  
  private static Set<NameField> getNonNullNameFields(Name name) {
    Set<NameField> fields = EnumSet.noneOf(NameField.class);
    if (name.getBasionymAuthorship() != null
        && notEmpty(name.getBasionymAuthorship().getAuthors())) {
      fields.add(BASIONYM_AUTHORS);
    }
    if (name.getBasionymAuthorship() != null
        && notEmpty(name.getBasionymAuthorship().getExAuthors())) {
      fields.add(BASIONYM_EX_AUTHORS);
    }
    if (name.getBasionymAuthorship() != null && name.getBasionymAuthorship().getYear() != null) {
      fields.add(BASIONYM_YEAR);
    }
    if (name.isCandidatus()) {
      fields.add(CANDIDATUS);
    }
    if (name.getCombinationAuthorship() != null
        && notEmpty(name.getCombinationAuthorship().getAuthors())) {
      fields.add(COMBINATION_AUTHORS);
    }
    if (name.getCombinationAuthorship() != null
        && notEmpty(name.getCombinationAuthorship().getExAuthors())) {
      fields.add(COMBINATION_EX_AUTHORS);
    }
    if (name.getCombinationAuthorship() != null
        && name.getCombinationAuthorship().getYear() != null) {
      fields.add(COMBINATION_YEAR);
    }
    addIfSet(fields, CULTIVAR_EPITHET, name.getCultivarEpithet());
    addIfSet(fields, GENUS, name.getGenus());
    addIfSet(fields, INFRAGENERIC_EPITHET, name.getInfragenericEpithet());
    addIfSet(fields, INFRASPECIFIC_EPITHET, name.getInfraspecificEpithet());
    addIfSet(fields, NOM_STATUS, name.getNomStatus());
    addIfSet(fields, NOTHO, name.getNotho());
    addIfSet(fields, PUBLISHED_IN_ID, name.getPublishedInId());
    addIfSet(fields, PUBLISHED_IN_PAGE, name.getPublishedInPage());
    addIfSet(fields, REMARKS, name.getRemarks());
    addIfSet(fields, SANCTIONING_AUTHOR, name.getSanctioningAuthor());
    addIfSet(fields, SOURCE_URL, name.getSourceUrl());
    addIfSet(fields, SPECIFIC_EPITHET, name.getSpecificEpithet());
    addIfSet(fields, STRAIN, name.getStrain());
    addIfSet(fields, UNINOMIAL, name.getUninomial());
    return fields;
  }
  
  private static void addIfSet(Set<NameField> fields, NameField nf, Object val) {
    if (val != null) {
      fields.add(nf);
    }
  }
  
}
