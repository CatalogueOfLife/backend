package org.col.admin.task.importer.neo.model;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.commons.text.StringEscapeUtils;
import org.col.api.model.ExtendedTermRecord;
import org.col.api.model.TermRecord;
import org.col.api.model.VerbatimRecord;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around a verbatim record that deals with unescaping character entities
 * on read methods without altering the underlying original verbatim data.
 *
 * When a value is altered during unescaping the record keeps track that it had been modified.
 *
 * The following escaped character formats are recognized:
 * 1) html entities
 *    named &amp;
 *    hex &#x0026;
 *    decimal &#38;
 * 2) Hexadecimal and octal escapes as used in Java, CSS & ECMA Javascript:
 *    Hexadecimal unicode escapes started by "\\u": \\u00A9
 *    TODO: support Unicode code point escapes indicated by "\\u{}": \\u{2F804}
 */
public class UnescapedVerbatimRecord extends VerbatimRecord {
  private static final Logger LOG = LoggerFactory.getLogger(UnescapedVerbatimRecord.class);

  private boolean modified;

  public static UnescapedVerbatimRecord create() {
    UnescapedVerbatimRecord v = new UnescapedVerbatimRecord();
    v.setTerms(new ExtendedTermRecord());
    return v;
  }

  public boolean isModified() {
    return modified;
  }

  private String unescape(String x){
    if (Strings.isNullOrEmpty(x)) {
      return null;
    }
    try {
      String unescaped = StringEscapeUtils.unescapeEcmaScript(
          StringEscapeUtils.unescapeHtml4(x)
      );
      if (!modified && !x.equals(unescaped)) {
        modified = true;
      }
      return unescaped;
    } catch (RuntimeException e) {
      LOG.debug("Failed to unescape: {}", x, e);
      return x;
    }
  }

  @Nullable
  public String getTermRaw(Term term) {
    return super.getTerm(term);
  }

  @Nullable
  @Override
  public String getTerm(Term term) {
    return unescape(super.getTerm(term));
  }

  @Nullable
  @Override
  public String getFirst(Term... terms) {
    return unescape(super.getFirst(terms));
  }

  @Override
  public List<TermRecord> getExtensionRecords(Term rowType) {
    List<TermRecord> unescaped = Lists.newArrayList();
    for (TermRecord tr : super.getExtensionRecords(rowType)) {
      unescaped.add(unescape(tr));
    }
    return unescaped;
  }

  private TermRecord unescape(TermRecord tr) {
    TermRecord tr2 = new TermRecord();
    tr2.setFile(tr.getFile());
    tr2.setLine(tr.getLine());
    tr2.setType(tr.getType());
    for (Map.Entry<Term, String> e : tr.termValues()) {
      tr2.put(e.getKey(), unescape(e.getValue()));
    }
    return tr2;
  }
}
