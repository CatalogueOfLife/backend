package life.catalogue.dao;

import com.google.common.annotations.VisibleForTesting;

import freemarker.template.TemplateException;

import life.catalogue.api.event.UserChanged;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.config.MailConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.UserMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import life.catalogue.metadata.FmUtil;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import jakarta.validation.Validator;

public class UserDao extends EntityDao<Integer, User, UserMapper> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(UserDao.class);
  private static final String emailTemplate = "email/editor-request.ftl";

  private final EventBus bus;
  @Nullable
  private Mailer mailer;
  private final MailConfig mailCfg;

  public UserDao(SqlSessionFactory factory, MailConfig cfg, @Nullable Mailer emailer, EventBus bus, Validator validator) {
    super(true, factory, User.class, UserMapper.class, validator);
    this.bus = bus;
    this.mailer = emailer;
    this.mailCfg = cfg;
  }

  public ResultPage<User> search(@Nullable final String q, @Nullable final User.Role role, @Nullable Page page) {
    page = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()){
      UserMapper um = session.getMapper(mapperClass);
      List<User> result = um.search(q, role, defaultPage(page));
      return new ResultPage<>(page, result, () -> um.searchCount(q, role));
    }
  }

  public void updateSettings(Map<String, String> settings, User user) {
    if (user != null && settings != null) {
      user.setSettings(settings);
      try (SqlSession session = factory.openSession()){
        session.getMapper(UserMapper.class).update(user);
      }
    }
  }

  /**
   * Updates a users set of roles. Dataset specific access control lists are unchanged.
   */
  public void changeRoles(int key, User admin, List<User.Role> roles) {
    Preconditions.checkArgument(admin.isAdmin());
    User user = getOr404(key);
    final var newRoles = new HashSet<User.Role>(ObjectUtils.coalesce(roles, Collections.EMPTY_SET));
    // update user
    user.setRoles(newRoles);
    update(user, admin.getKey());
  }

  /**
   * Removes the specified user and role from all datasets held currently by the user.
   * It does not change the role of the user in general, but modifies the dataset ACLs.
   * @param key
   * @param admin
   * @param role
   */
  public void revokeRoleOnAllDatasets(int key, User admin, User.Role role) {
    Preconditions.checkArgument(admin.isAdmin());
    User user = getOr404(key);
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      if (role == User.Role.EDITOR) {
        dm.removeEditorEverywhere(user.getKey(), admin.getKey());
      } else if (role == User.Role.REVIEWER) {
        dm.removeReviewerEverywhere(user.getKey(), admin.getKey());
      } else {
        throw new IllegalArgumentException("Role " + role + " does not exist on datasets");
      }
    }
  }

  public void block(int key, User admin) {
    block(key, LocalDateTime.now(), admin);
  }

  public void unblock(int key, User admin) {
    block(key, null, admin);
  }

  private void block(int key, @Nullable LocalDateTime datetime, User admin) {
    User u;
    try (SqlSession session = factory.openSession(true)){
      var um = session.getMapper(UserMapper.class);
      um.block(key, datetime);
      u = um.get(key);
    }
    bus.post(UserChanged.created(u, admin.getKey()));
  }

  private static Page defaultPage(Page page){
    return page == null ? new Page(0, 10) : page;
  }

  @Override
  protected boolean createAfter(User obj, int user, UserMapper mapper, SqlSession session) {
    session.close();
    bus.post(UserChanged.created(obj, user));
    return false;
  }

  @Override
  protected boolean updateAfter(User obj, User old, int user, UserMapper mapper, SqlSession session, boolean keepSessionOpen) {
    if (!keepSessionOpen) {
      session.close();
    }
    bus.post(UserChanged.changed(obj, user));
    return keepSessionOpen;
  }

  @Override
  protected boolean deleteAfter(Integer key, User old, int user, UserMapper mapper, SqlSession session) {
    bus.post(UserChanged.deleted(old, user));
    return false;
  }

  public void addReleaseKeys(User user) {
    var keys = releaseKeys(user.getKey(), user.getEditor());
    user.getEditor().addAll(keys);

    keys = releaseKeys(user.getKey(), user.getReviewer());
    user.getReviewer().addAll(keys);
  }

  /**
   * Returns all (x)release dataset keys that belong to a project included in the given projectKeys.
   * @param projectKeys dataset keys of projects. Other dataset keys, e.g. external, are ignored
   */
  private IntSet releaseKeys(int userKey, IntSet projectKeys){
    IntSet keys = new IntOpenHashSet();
    if (projectKeys != null) {
      try (SqlSession session = factory.openSession()) {
        var dm = session.getMapper(DatasetMapper.class);
        for (int projKey : projectKeys) {
          if (DatasetInfoCache.CACHE.info(projKey, true).origin == DatasetOrigin.PROJECT) {
            var res = dm.listReleaseKeys(projKey);
            if (res != null) {
              keys.addAll(res);
            }
          }
        }
      } catch (Throwable e) {
        LOG.warn("Failed to list release keys for user {}", e, userKey);
      }
    }
    return keys;
  }

  /**
   * Files a request to become an editor and sends an email to admins to handle it.
   * @param user
   */
  public void requestEditorPermission(User user, String request) {
    Preconditions.checkArgument(user.getFirstname() != null, "Your firstname is required in your GBIF profile to become an editor");
    Preconditions.checkArgument(user.getLastname() != null, "Your lastname is required in your GBIF profile to become an editor");
    Preconditions.checkArgument(user.getOrcid() != null, "You must have an ORCID configured in your GBIF profile to become an editor");
    Preconditions.checkArgument(!user.isAdmin(), "You must have an ORCID configured in your GBIF profile to become an editor");
    if (user.getRoles().contains(User.Role.EDITOR) || user.isEditor() || user.isAdmin()) {
      throw new IllegalArgumentException("You already have editor permissions");
    }
    sendRequestEmail(user, request);
  }

  private void sendRequestEmail(User user, String request) {
    try {
      if (mailer != null) {
        Email mail = EmailBuilder.startingBlank()
          .to(mailCfg.replyTo)
          .ccAddresses(List.of(user.getEmail()))
          .from(mailCfg.fromName, mailCfg.from)
          .withReplyTo(user.getEmail())
          .withSubject(String.format("%s editor request from %s", mailCfg.subjectPrefix, user.getUsername()))
          .withPlainText(buildEmailText(user, request))
          .buildEmail();

        var asyncResp = mailer.sendMail(mail, true).thenAccept((resp) -> {
          LOG.info("Successfully sent editor request mail for {}", user.getUsername());
        }).exceptionally((e) -> {
          LOG.error("Error sending editor request mail for {}", user.getUsername(), e);
          return null;
        });
        if (mailCfg.block && asyncResp != null) {
          asyncResp.get(); // blocks
        }
        LOG.info("Sent editor request mail for user {} [{}] to {}", user.getName(), user.getKey(), user.getEmail());

      } else {
        LOG.warn("No mailer configured to sent editor request mails for user {} [{}] to {}", user.getName(), user.getKey(), user.getEmail());
      }

    } catch (IOException | TemplateException | ExecutionException | InterruptedException | RuntimeException e) {
      LOG.error("Error sending editor request mail for {}", user.getUsername(), e);
      if (mailCfg != null && mailCfg.block) {
        throw Exceptions.asRuntimeException(e);
      }
    }
  }

  @VisibleForTesting
  static String buildEmailText(User user, String request) throws TemplateException, IOException {
    var data = new HashMap<>();
    data.put("user", user);
    data.put("request", request);
    return FmUtil.render(data, emailTemplate);
  }
}
