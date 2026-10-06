package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.SectorProfileMapper;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

public class SectorProfileDao extends DatasetEntityDao<Integer, SectorProfile, SectorProfileMapper> {

  public SectorProfileDao(SqlSessionFactory factory, Validator validator) {
    super(false, factory, SectorProfile.class, SectorProfileMapper.class, validator);
  }

  @Override
  protected void validate(SectorProfile p) throws ConstraintViolationException {
    super.validate(p);
    // a profile can select thousands of sectors, so a broken regex has to fail here and not in each of their syncs
    validatePatterns(p.getSettings());
  }

  /**
   * @throws IllegalArgumentException if the name filter or a blocked name pattern is no valid regular expression
   */
  public static void validatePatterns(SyncSettings s) {
    compile("nameFilter", s.getNameFilter(), 0);
    if (s.getBlockedNamePatterns() != null) {
      for (String p : s.getBlockedNamePatterns()) {
        // compiled the way NameBlocklist does
        compile("blockedNamePatterns", p == null ? null : p.trim(), Pattern.CASE_INSENSITIVE);
      }
    }
  }

  private static void compile(String setting, String regex, int flags) {
    if (regex != null && !regex.isBlank()) {
      try {
        Pattern.compile(regex, flags);
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException("Invalid " + setting + " regex '" + regex + "': " + e.getDescription(), e);
      }
    }
  }

  /**
   * The settings a sync of the sector uses, and the level each comes from.
   * A sector is resolved against the profiles of its own dataset, so a release sector against the profiles
   * copied into that release.
   */
  public EffectiveSectorSettings effectiveSettings(DSID<Integer> sectorKey) {
    try (SqlSession session = factory.openSession()) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      if (s == null) {
        throw NotFoundException.notFound(Sector.class, sectorKey);
      }
      return SectorSettingsResolver.resolve(s, session.getMapper(SectorProfileMapper.class).listMatching(sectorKey));
    }
  }
}
