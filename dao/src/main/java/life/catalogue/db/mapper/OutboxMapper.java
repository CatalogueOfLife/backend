package life.catalogue.db.mapper;

import life.catalogue.api.model.OutboxEntry;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.OutboxStatus;
import life.catalogue.api.vocab.OutboxType;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Mapper for the outbox table of durable commands, see life.catalogue.outbox.OutboxRunner.
 * Everything a runner does is a single autocommit statement, so no transaction ever sits idle while a command runs.
 */
public interface OutboxMapper {

  /**
   * Inserts a command, or merges it into the one waiting for the same type and key:
   * params are merged with jsonb ||, the earlier due time wins, the version is bumped so a run that is going on
   * does not complete the merged request, and a failed command is revived with its attempts reset.
   *
   * @param delaySeconds seconds from now until the command is due
   */
  void enqueue(@Param("type") OutboxType type,
               @Param("key") String key,
               @Param("params") JsonNode params,
               @Param("delaySeconds") int delaySeconds,
               @Param("user") int user);

  OutboxEntry get(@Param("id") long id);

  OutboxEntry getByKey(@Param("type") OutboxType type, @Param("key") String key);

  List<OutboxEntry> list(@Param("type") @Nullable OutboxType type,
                         @Param("status") @Nullable OutboxStatus status,
                         @Param("page") Page page);

  int count(@Param("type") @Nullable OutboxType type, @Param("status") @Nullable OutboxStatus status);

  /**
   * Counts pending commands that have been due for longer than the given minutes, a sign that nothing runs them.
   * @param exclude types not to count, e.g. those whose handler is offline on purpose
   */
  int countOverdue(@Param("minutes") int minutes, @Param("exclude") @Nullable Collection<OutboxType> exclude);

  /**
   * Counts pending commands requested longer than the given hours ago, which catches those put off again and again.
   * @param exclude types not to count, e.g. those whose handler is offline on purpose
   */
  int countStuck(@Param("hours") int hours, @Param("exclude") @Nullable Collection<OutboxType> exclude);

  /**
   * Claims up to limit due pending commands of one type for the given runner instance, earliest due first,
   * skipping rows another runner has locked.
   * @return the claimed commands, now RUNNING
   */
  List<OutboxEntry> claim(@Param("type") OutboxType type, @Param("limit") int limit, @Param("instance") String instance);

  /**
   * Deletes a command that ran successfully, but only if it is still the version that was run and still claimed by
   * this instance.
   * @return 1 if deleted, 0 if a request was merged in during the run - then requeue it.
   */
  int complete(@Param("id") long id, @Param("version") int version, @Param("instance") String instance);

  /**
   * Hands a command claimed by this instance back as pending, keeping its due time and without counting an attempt.
   */
  int requeue(@Param("id") long id, @Param("instance") String instance);

  /**
   * Hands a command claimed by this instance back as pending, due after the delay.
   * @param inc the attempts to add, 0 when the command was not at fault, e.g. the server was not ready to run it
   */
  int retry(@Param("id") long id,
            @Param("instance") String instance,
            @Param("inc") int inc,
            @Param("delaySeconds") int delaySeconds,
            @Param("error") @Nullable String error);

  /**
   * Marks a command claimed by this instance as failed for good.
   * If a request was merged in during the run it becomes pending again instead, the new request deserves its own try.
   * @return the status the command was left in, FAILED or PENDING, or null if this instance no longer held it
   */
  @Nullable
  OutboxStatus fail(@Param("id") long id, @Param("version") int version, @Param("instance") String instance,
                    @Param("error") String error);

  /**
   * Hands every command this instance still has running back as pending, used when a runner stops.
   */
  int releaseMine(@Param("instance") String instance);

  /**
   * Hands every running command back as pending, used when a runner starts. There is only ever one active runner,
   * so anything still running was left behind by an app that died or was stopped while running it.
   */
  int resetStale();

  /**
   * Makes a command pending and due now, resetting its attempts. The manual retry of a failed command.
   */
  int retryNow(@Param("id") long id);

  int delete(@Param("id") long id);

  /**
   * Deletes failed commands not modified for the given number of days.
   */
  int purgeFailed(@Param("days") int days);
}
