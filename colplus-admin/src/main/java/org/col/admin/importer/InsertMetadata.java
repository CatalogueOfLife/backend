package org.col.admin.importer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settings uses during the insert of the dwc archive into normalizer.
 */
public class InsertMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(InsertMetadata.class);

  private boolean taxonId;
  private boolean parsedNameMapped;
  private boolean denormedClassificationMapped;
  private boolean originalNameMapped;
  private boolean acceptedNameMapped;
  private boolean parentNameMapped;
  private Map<Term, Splitter> multiValueDelimiters = Maps.newHashMap();
  private int records;
  private Map<Rank, AtomicInteger> recordsByRank = Maps.newHashMap();

  /**
   * @return true if taxonID exists as a distinct property from the ID property
   * and should be used to resolve taxonomic relationships based on taxonID terms.
   */
  public boolean hasTaxonId() {
    return taxonId;
  }

  public void setTaxonId(boolean taxonId) {
    this.taxonId = taxonId;
  }

  /**
   * @return true if at least genus and specificEpithet are mapped
   */
  public boolean isParsedNameMapped() {
    return parsedNameMapped;
  }

  public void setParsedNameMapped(boolean parsedNameMapped) {
    this.parsedNameMapped = parsedNameMapped;
  }

  public boolean isDenormedClassificationMapped() {
    return denormedClassificationMapped;
  }

  public void setDenormedClassificationMapped(boolean denormedClassificationMapped) {
    this.denormedClassificationMapped = denormedClassificationMapped;
  }

  public boolean isOriginalNameMapped() {
    return originalNameMapped;
  }

  public void setOriginalNameMapped(boolean originalNameMapped) {
    this.originalNameMapped = originalNameMapped;
  }

  public boolean isAcceptedNameMapped() {
    return acceptedNameMapped;
  }

  public void setAcceptedNameMapped(boolean acceptedNameMapped) {
    this.acceptedNameMapped = acceptedNameMapped;
  }

  public boolean isParentNameMapped() {
    return parentNameMapped;
  }

  public void setParentNameMapped(boolean parentNameMapped) {
    this.parentNameMapped = parentNameMapped;
  }

  public Map<Term, Splitter> getMultiValueDelimiters() {
    return multiValueDelimiters;
  }

  public void incRecords(Rank rank) {
    records++;
    if (rank == null) {
      rank =Rank.UNRANKED;
    }
    if (!recordsByRank.containsKey(rank)) {
      recordsByRank.put(rank, new AtomicInteger(1));
    } else {
      recordsByRank.get(rank).getAndIncrement();
    }

    if (records % (10000) == 0) {
      LOG.info("Inserts done into neo4j: {}", records);
      if (Thread.interrupted()) {
        LOG.warn("NeoInserter interrupted, exit early with incomplete parsing");
        throw new NormalizationFailedException("NeoInserter interrupted");
      }
    }
  }

  public int getRecords() {
    return records;
  }

}
