package life.catalogue.doi.datacite.model;

import life.catalogue.api.model.EnumValue;

/**
 *
 */
public enum ContributorType implements EnumValue {

  CONTACT_PERSON("ContactPerson"),
  DATA_COLLECTOR("DataCollector"),
  DATA_CURATOR("DataCurator"),
  DATA_MANAGER("DataManager"),
  DISTRIBUTOR("Distributor"),
  EDITOR("Editor"),
  HOSTING_INSTITUTION("HostingInstitution"),
  OTHER("Other"),
  PRODUCER("Producer"),
  PROJECT_LEADER("ProjectLeader"),
  PROJECT_MANAGER("ProjectManager"),
  PROJECT_MEMBER("ProjectMember"),
  REGISTRATION_AGENCY("RegistrationAgency"),
  REGISTRATION_AUTHORITY("RegistrationAuthority"),
  RELATED_PERSON("RelatedPerson"),
  RESEARCH_GROUP("ResearchGroup"),
  RIGHTS_HOLDER("RightsHolder"),
  RESEARCHER("Researcher"),
  SPONSOR("Sponsor"),
  SUPERVISOR("Supervisor"),
  WORK_PACKAGE_LEADER("WorkPackageLeader");

  private final String value;

  ContributorType(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static ContributorType fromValue(String v) {
    for (ContributorType c : ContributorType.values()) {
      if (c.value.equalsIgnoreCase(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}
