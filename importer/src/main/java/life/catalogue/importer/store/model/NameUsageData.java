package life.catalogue.importer.store.model;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.VerbatimEntity;
import life.catalogue.dao.TxtTreeDao;

public class NameUsageData implements VerbatimEntity {
  public final NameData nd;
  public final UsageData ud;

  public NameUsageData(NameData nd, UsageData ud) {
    this.nd = nd;
    this.ud = ud;
    if (ud != null && ud.usage.getName()==null) {
      ud.usage.setName(nd.getName());
    }
  }

  public NameUsageData(TxtTreeDao.TxtUsage tu) {
    this.ud = new UsageData();
    ud.usage = tu.usage;
    ud.distributions.addAll(tu.distributions);
    ud.media.addAll(tu.media);
    ud.vernacularNames.addAll(tu.vernacularNames);
    ud.estimates.addAll(tu.estimates);
    ud.properties.addAll(tu.properties);

    nd = new NameData(tu.usage.getName());
    ud.nameID = nd.getId();
  }

  @Override
  public Integer getVerbatimKey() {
    return ud.getVerbatimKey();
  }

  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    ud.setVerbatimKey(verbatimKey);
  }

  @Override
  public Integer getDatasetKey() {
    return ud.getDatasetKey();
  }

  @Override
  public void setDatasetKey(Integer key) {
    ud.setDatasetKey(key);
  }

  public String getLabel() {
    return getLabel(false);
  }

  public String getLabel(boolean hideExtinct) {
    return NameUsageBase.labelBuilder(nd.getName(),
      hideExtinct || !ud.usage.isTaxon() ? null : ud.usage.asTaxon().isExtinct(),
      ud.usage.getStatus(),
      ud.usage.getNamePhrase(),
      ud.usage.getAccordingTo(),
      false
    ).toString();
  }

  public SimpleName toSimpleName() {
    var sn = new SimpleName(nd.getName());
    sn.setId(ud.usage.getId()); // otherwise we get the name id !
    // we place both the phrase and accordingTo into phrase here, so accordingTo is not lost
    StringBuilder phrase = new StringBuilder();
    NameUsageBase.appendNamePhraseAndAccordingTo(phrase, ud.usage.getNamePhrase(), ud.usage.getAccordingTo(), false);
    if (!phrase.isEmpty()) {
      sn.setPhrase(phrase.toString().trim());
    }
    sn.setStatus(ud.usage.getStatus());
    sn.setParent(ud.usage.getParentId());
    if (ud.isTaxon()) {
      sn.setExtinct(Boolean.TRUE.equals(ud.asTaxon().isExtinct()));
    }
    return sn;
  }

  public NameUsageBase toNameUsageBase() {
    var nub = ud.asNameUsageBase();
    nub.setName(nd.getName());
    return nub;
  }

  public boolean hasParentID() {
    var nub = ud.asNameUsageBase();
    return nub != null && nub.getParentId() != null;
  }

  @Override
  public String toString() {
    return String.format("%s -> %s [%s] %s", ud.getId(), ud.usage.getParentId(), nd.getRank(), getLabel());
  }
}
