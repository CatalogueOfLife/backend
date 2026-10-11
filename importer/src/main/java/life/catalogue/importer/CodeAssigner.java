package life.catalogue.importer;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.dao.ParentStack;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.TreeWalker;
import life.catalogue.importer.store.model.NameUsageData;
import life.catalogue.matching.TaxGroupAnalyzer;

import org.gbif.nameparser.api.NomCode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides the nomenclatural code of names neither their record nor the dataset gave a code, using the taxon group of
 * their final classification. The parser's guess, if any, is only kept if the classification has no group with a
 * single code - and then only if it is one of the codes of the group.
 * A name with a decided code is interpreted again from its verbatim record, so that code dependent parsing like a
 * zoological subspecies or a subgenus instead of a basionym author comes out as if the code had been given.
 * See https://github.com/CatalogueOfLife/backend/issues/1624
 */
public class CodeAssigner implements TreeWalker.StartEndHandler {
  private static final Logger LOG = LoggerFactory.getLogger(CodeAssigner.class);
  private static final TaxGroupAnalyzer ANALYZER = new TaxGroupAnalyzer();
  private final ImportStore store;
  private final @Nullable InterpreterBase interpreter;
  private final ParentStack<SimpleName> parents = new ParentStack<>();
  private int reinterpreted;
  private int recoded;
  private int dropped;

  public CodeAssigner(ImportStore store, @Nullable InterpreterBase interpreter) {
    this.store = store;
    this.interpreter = interpreter;
  }

  @Override
  public void start(NameUsageData nu, TreeWalker.WalkerContext ctxt) {
    var sn = nu.toSimpleName();
    List<SimpleName> classification;
    if (nu.ud.isTaxon()) {
      parents.push(sn);
      var withSelf = parents.getParents();
      classification = withSelf.subList(0, withSelf.size() - 1);
    } else {
      // synonyms are visited right after their accepted name, which is the last parent then
      classification = parents.getParents();
    }
    if (!nu.nd.pnu.isCodeGiven()) {
      decide(nu, sn, classification);
    }
  }

  @Override
  public void end(NameUsageData data, TreeWalker.WalkerContext ctxt) {
  }

  private void decide(NameUsageData nu, SimpleName sn, List<SimpleName> classification) {
    // the parser's guess must not decide its own group
    var withoutCode = new SimpleName(sn);
    withoutCode.setCode(null);
    TaxGroup group = ANALYZER.analyze(withoutCode, classification);
    Set<NomCode> codes = group == null ? Set.of() : group.getCodes();
    final Name n = nu.nd.getName();
    if (codes.size() == 1) {
      NomCode code = codes.iterator().next();
      if (code != n.getCode()) {
        applyCode(nu, code);
      }
    } else if (!codes.isEmpty() && n.getCode() != null && !codes.contains(n.getCode())) {
      // the guess is not one of the codes of the group
      n.setCode(null);
      n.rebuildScientificName();
      flagInferred(n, null, false);
      nu.nd.pnu.setCodeGiven(true);
      store.names().update(nu.nd);
      dropped++;
    }
  }

  private void applyCode(NameUsageData nu, NomCode code) {
    final Name n = nu.nd.getName();
    Optional<ParsedNameUsage> pnu = Optional.empty();
    if (interpreter != null && n.getOrigin() == Origin.SOURCE && n.getVerbatimKey() != null) {
      VerbatimRecord v = store.getVerbatim(n.getVerbatimKey());
      pnu = interpreter.reinterpretName(n.getId(), v, code);
      if (pnu.isPresent()) {
        copyParsedName(pnu.get().getName(), n);
        // keep issues of the new interpretation
        flagInferred(n, v, true);
        reinterpreted++;
      }
    }
    if (pnu.isEmpty()) {
      n.setCode(code);
      n.rebuildScientificName();
      flagInferred(n, null, true);
      recoded++;
    }
    // decided once, also for a name of several usages
    nu.nd.pnu.setCodeGiven(true);
    store.names().update(nu.nd);
  }

  private void flagInferred(Name n, @Nullable VerbatimRecord v, boolean inferred) {
    if (v == null && n.getVerbatimKey() != null) {
      v = store.getVerbatim(n.getVerbatimKey());
    }
    if (v != null) {
      if (inferred) {
        v.add(Issue.NOMENCLATURAL_CODE_INFERRED);
      } else {
        v.remove(Issue.NOMENCLATURAL_CODE_INFERRED);
      }
      store.put(v);
    }
  }

  /**
   * Copies all pure name properties a parse produces, but not the ones interpreted from other terms or derived later.
   */
  private static void copyParsedName(Name src, Name n) {
    n.setScientificName(src.getScientificName());
    n.setAuthorship(src.getAuthorship());
    n.setCode(src.getCode());
    n.setType(src.getType());
    n.setRank(src.getRank());
    n.setUninomial(src.getUninomial());
    n.setGenus(src.getGenus());
    n.setInfragenericEpithet(src.getInfragenericEpithet());
    n.setSpecificEpithet(src.getSpecificEpithet());
    n.setInfraspecificEpithet(src.getInfraspecificEpithet());
    n.setCultivarEpithet(src.getCultivarEpithet());
    n.setCandidatus(src.isCandidatus());
    n.setNotho(src.getNotho());
    n.setCombinationAuthorship(src.getCombinationAuthorship());
    n.setBasionymAuthorship(src.getBasionymAuthorship());
    n.setUnparsed(src.getUnparsed());
    n.setNomenclaturalNote(src.getNomenclaturalNote());
    n.setOriginalSpelling(src.isOriginalSpelling());
  }

  public void logStats() {
    LOG.info("Codes decided by the classification: {} names interpreted again, {} recoded, {} inferred codes dropped",
      reinterpreted, recoded, dropped);
  }
}
