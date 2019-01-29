package org.col.api.model;

import java.util.HashMap;
import java.util.Map;

import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.Rank;

public class StatusRankCounts extends HashMap<TaxonomicStatus, Map<Rank, Integer>> {
}
