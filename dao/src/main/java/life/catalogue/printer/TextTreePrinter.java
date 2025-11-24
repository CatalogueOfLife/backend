package life.catalogue.printer;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.terms.TxtTreeTerm;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.db.PgUtils;
import life.catalogue.db.SectorInfoCache;
import life.catalogue.db.mapper.DistributionMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonExtensionMapper;
import life.catalogue.db.mapper.VernacularNameMapper;

import org.gbif.nameparser.api.Rank;
import org.gbif.txtree.Tree;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Print an entire dataset in the indented text format used by TxtPrinter.
 * Synonyms are prefixed with an asterisk *,
 * Pro parte synoynms with a double asterisk **,
 * basionyms are prefixed by a $ and listed first in the synonymy.
 * <p>
 * Ranks are given in brackets after the scientific name
 * <p>
 * A basic example tree would look like this:
 * <pre>
 * Plantae [kingdom]
 * Compositae Giseke [family]
 * Asteraceae [family]
 * Artemisia L. [genus]
 * Artemisia elatior (Torr. & A. Gray) Rydb.
 * $Artemisia tilesii var. elatior Torr. & A. Gray
 * $Artemisia rupestre Schrank L. [species]
 * Absinthium rupestre (L.) Schrank [species]
 * Absinthium viridifolium var. rupestre (L.) Besser
 * </pre>
 */
public class TextTreePrinter extends AbstractTreePrinter {
  private static final int indentation = 2;
  private static final Pattern SPACE = Pattern.compile("[\n\r\t]+");
  private static final Pattern COMMA = Pattern.compile(",");
  private boolean showIDs;
  private boolean extended;
  private final DSID<String> key;
  private final SectorInfoCache sectorInfoCache;
  private final Set<String> basionyms = new HashSet<>();

  public TextTreePrinter(TreeTraversalParameter params, Set<Rank> ranks, @Nullable Boolean extinct,
                         @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
                         SqlSessionFactory factory, Writer writer) {
    super(params, ranks, extinct, countRank, taxonCounter, factory, writer);
    key = DSID.root(params.getDatasetKey());
    sectorInfoCache = new SectorInfoCache(factory, params.getDatasetKey());
  }


  public TextTreePrinter showIDs() {
    this.showIDs = true;
    return this;
  }

  public TextTreePrinter showExtendedInfos() {
    extended = true;
    return this;
  }

  public void addBasionyms(Collection<String> basionymIds) {
    basionyms.addAll(basionymIds);
  }

  /**
   * Prints the parent classification of the given root taxon and increases the indentation (level) accordingly.
   * Make sure to call this method only once and before printing any taxa!
   *
   * This method will do nothing if no taxonID (root) is set in the traversal parameters.
   */
  public void printParents() {
    if (params.getTaxonID() != null) {
      try (var sess = factory.openSession(true)){
        var num = sess.getMapper(NameUsageMapper.class);
        var cl = num.getClassificationSN(key.id(params.getTaxonID()));
        if (cl != null) {
          for (var sn : cl) {
            if (!sn.getId().equals(params.getTaxonID())) {
              accept(sn);
            }
          }
        }
      }
    }
  }

  protected void start(SimpleName u) throws IOException {
    writer.write(StringUtils.repeat(' ', level * indentation));
    if (u.getStatus() != null && u.getStatus().isSynonym()) {
      writer.write(Tree.SYNONYM_SYMBOL);
    }
    if (basionyms.contains(u.getId())) {
      writer.write(Tree.BASIONYM_SYMBOL);
    }
    if (u.isExtinct()) {
      writer.write(Tree.EXTINCT_SYMBOL);
    }
    if (u.getStatus() == TaxonomicStatus.PROVISIONALLY_ACCEPTED) {
      writer.write(Tree.PROVISIONAL_SYMBOL);
    }
    writer.write(u.getName());
    if (u.getAuthorship() != null) {
      writer.write(" ");
      writer.write(u.getAuthorship());
    }
    writer.write(" [");
    Rank r = ObjectUtils.coalesce(u.getRank(), Rank.UNRANKED);
    writer.write(r.name().toLowerCase());
    writer.write("]");

    var infos = infos(u);
    if (!infos.props.isEmpty()) {
      writer.write(" {");
      writer.write(String.join(" ", infos.props));
      writer.write("}");
    }
    if (infos.remarks != null) {
      writer.write(" # " + noLineBreak(infos.remarks) );
    }
    writer.write('\n');
  }

  protected void end(SimpleName u) {
    //nothing
  }

  private StringBuilder infoBuilder(TxtTreeTerm type) {
    StringBuilder sb = new StringBuilder();
    sb.append(type.name())
      .append("=");
    return sb;
  }

  private static class MappedInfos {
    final List<String> props = new ArrayList<>();
    String remarks;
  }

  /**
   * @return list of infos to be appended in brackets after the name
   * @param u
   */
  private MappedInfos infos(SimpleName u){
    var infos = new MappedInfos();
    if (showIDs) {
      infos.props.add("ID=" + u.getId());
    }
    if (extended) {
      key.id(u.getId());
      addInfos(TxtTreeTerm.CODE, u.getCode(), infos.props);
      var nu = session.getMapper(NameUsageMapper.class).get(key);
      if (nu != null) {
        nu.setSectorMode(sectorInfoCache.sector2mode(nu.getSectorKey()));
        if (Boolean.TRUE.equals(nu.isMerged())) {
          addInfos(TxtTreeTerm.MERGED, "true", infos.props);
        }
        addInfos(TxtTreeTerm.PUB, escape(nu.getName().getPublishedInId()), infos.props);
        if (nu.isTaxon()) {
          Taxon t = nu.asTaxon();
          addInfos(TxtTreeTerm.REF, joinStr(t.getReferenceIds()), infos.props);
          addInfos(TxtTreeTerm.ENV, joinEnum(t.getEnvironments()), infos.props);
          if (t.getTemporalRangeStart() != null || t.getTemporalRangeEnd() != null) {
            addInfos(TxtTreeTerm.CHRONO, str(t.getTemporalRangeStart()) + "-" + str(t.getTemporalRangeEnd()), infos.props);
          }
        }
        addInfos(TxtTreeTerm.LINK, nu.getLink(), infos.props);
        infos.remarks = nu.getRemarks();
      }
      addInfos(TxtTreeTerm.VERN, VernacularNameMapper.class, this::encode, infos.props);
      addInfos(TxtTreeTerm.DIST, DistributionMapper.class, this::encode, infos.props);
    }
    if (countRank != null) {
      infos.props.add("NUM_"+countRank.name() + "=" + taxonCount);
    }
    return infos;
  }

  private static String str(String x) {
    return x == null ? "" : escape(x);
  }

  /**
   * Escapes commas with double commas
   * @param x
   * @return
   */
  private static String escape(String x) {
    return x == null ? null : COMMA.matcher( noLineBreak(x) ).replaceAll(",,");
  }
  private static String noLineBreak(String x) {
    return x == null ? null : SPACE.matcher(x).replaceAll(" ");
  }

  private String encode(VernacularName vn) {
    if (vn != null && vn.getName() != null) {
      if (vn.getLanguage() != null) {
        return vn.getLanguage() + ":" + escape(vn.getName().trim());
      }
      return escape(vn.getName().trim());
    }
    return null;
  }

  /**
   * Serializes all structured distributions, but skips pure text ones
   */
  private String encode(Distribution d) {
    if (d != null && d.getArea() != null && d.getArea().getGazetteer() != null && d.getArea().getGazetteer() != Gazetteer.TEXT) {
      if (d.getArea().getId() != null) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.getArea().getGlobalId());
        if (d.getEstablishmentMeans() != null) {
          // iso:de:native
          sb.append(':').append(d.getEstablishmentMeans().name().toLowerCase());
        }
        return sb.toString();
      }
    }
    return null;
  }

  private void addInfos(TxtTreeTerm type, String value, List<String> infos) {
    if (value != null) {
      StringBuilder sb = infoBuilder(type);
      sb.append(value);
      infos.add(sb.toString());
    }
  }
  private void addInfos(TxtTreeTerm type, Enum value, List<String> infos) {
    addInfos(type, value == null ? null : value.name(), infos);
  }
  private void addInfos(TxtTreeTerm type, Object value, List<String> infos) {
    addInfos(type, value == null ? null : value.toString(), infos);
  }
  private static String joinStr(Collection<String> values) {
    if (values != null && !values.isEmpty()) {
      return String.join(",", values.stream().filter(Objects::nonNull).map(x -> escape(x)).collect(Collectors.toList()));
    }
    return null;
  }
  private static String joinEnum(Collection<? extends Enum<?>> values) {
    if (values != null && !values.isEmpty()) {
      return String.join(",", values.stream().filter(Objects::nonNull).map(Enum::name).collect(Collectors.toList()));
    }
    return null;
  }

  private <T extends ExtensionEntity> void addInfos(TxtTreeTerm type, Class<? extends TaxonExtensionMapper<T>> mapperClass, Function<T, String> encoder, List<String> infos) {
    StringBuilder sb = infoBuilder(type);
    var m = session.getMapper(mapperClass);
    boolean first = true;
    for (var obj : m.listByTaxon(key)) {
      String val = encoder.apply(obj);
      if (val != null) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        sb.append(val);
      }
    }
    if (!first){
      infos.add(sb.toString());
    }
  }

}
