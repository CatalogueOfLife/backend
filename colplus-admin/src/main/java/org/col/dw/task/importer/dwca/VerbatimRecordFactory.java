package org.col.dw.task.importer.dwca;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import org.col.dw.api.TermRecord;
import org.col.dw.api.VerbatimRecord;
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

  public static VerbatimRecord build (StarRecord star) {
    VerbatimRecord v = new VerbatimRecord();
    v.setId(star.core().id());

    // set core terms
    Record core = star.core();
    for (Term t : core.terms()) {
      String val = clean(core.value(t));
      if (val != null) {
        v.setCoreTerm(t, val);
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
    TermRecord tr = new TermRecord();
    for (Term t : eRec.terms()) {
      String val = clean(eRec.value(t));
      if (val != null) {
        tr.put(t, val);
      }
    }
    return tr;
  }
}
