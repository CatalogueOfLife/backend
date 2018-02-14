package org.col.admin.task.importer;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Settings uses during the insert of the dwc archive into normalizer.
 */
public class InsertMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(InsertMetadata.class);

  private boolean coreIdUsed;
  private boolean parsedNameMapped;
  private boolean denormedClassificationMapped;
  private boolean originalNameMapped;
  private boolean acceptedNameMapped;
  private boolean parentNameMapped;
  private Map<Term, Splitter> multiValueDelimiters = Maps.newHashMap();
  private int records;
  private Map<Rank, AtomicInteger> recordsByRank = Maps.newHashMap();

  /**
   * @return true if the coreID of the core records is used instead of a column mapped to the taxonID term.
   */
  public boolean isCoreIdUsed() {
    return coreIdUsed;
  }

  public void setCoreIdUsed(boolean coreIdUsed) {
    this.coreIdUsed = coreIdUsed;
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
