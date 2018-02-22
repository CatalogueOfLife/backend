package org.col.admin.task.importer.dwca;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.col.api.model.TermRecord;
import org.col.api.model.VerbatimRecord;
import org.gbif.dwc.terms.Term;
import org.gbif.dwca.record.Record;
import org.gbif.dwca.record.StarRecord;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 *
 */
public class VerbatimRecordFactory {
  private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL)\\s*$");

  public static String clean(String x) {
    if (Strings.isNullOrEmpty(x) || NULL_PATTERN.matcher(x).find()) {
      return null;
    }
    return Strings.emptyToNull(CharMatcher.javaIsoControl().trimAndCollapseFrom(x, ' ').trim());
  }

  public static VerbatimRecord build (String id, TermRecord core) {
    VerbatimRecord v = new VerbatimRecord();
    Preconditions.checkNotNull(id, "ID required");
    v.setId(id);

    // set core terms
    core.forEach((term, value) -> {
      String val = clean(value);
      if (val != null) {
        v.setTerm(term, val);
      }
    });

    return v;
  }

  public static VerbatimRecord build (StarRecord star) {
    VerbatimRecord v = new VerbatimRecord();
    v.setId(star.core().id());

    // set core terms
    Record core = star.core();
    v.getTerms().setType(core.rowType());
    for (Term t : core.terms()) {
      String val = clean(core.value(t));
      if (val != null) {
        v.setTerm(t, val);
      }
    }

    // read all extension data
    for (Map.Entry<Term, List<Record>> ext : star.extensions().entrySet()) {
      Term rowType = ext.getKey();
      for (Record eRec : ext.getValue()) {
        v.addExtensionRecord(rowType, buildTermRec(eRec));
      }
    }
    return v;
  }

  private static TermRecord buildTermRec(Record eRec) {
    TermRecord tr = new TermRecord(-1, null);
    tr.setType(eRec.rowType());
    for (Term t : eRec.terms()) {
      String val = clean(eRec.value(t));
      if (val != null) {
        tr.put(t, val);
      }
    }
    return tr;
  }
}
