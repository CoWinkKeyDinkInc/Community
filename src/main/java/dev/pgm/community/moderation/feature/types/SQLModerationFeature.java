package dev.pgm.community.moderation.feature.types;

import com.google.common.collect.Lists;
import dev.pgm.community.database.DatabaseConnection;
import dev.pgm.community.moderation.ModerationConfig;
import dev.pgm.community.moderation.feature.ModerationFeatureBase;
import dev.pgm.community.moderation.feature.SQLModerationService;
import dev.pgm.community.moderation.punishments.Punishment;
import dev.pgm.community.moderation.punishments.PunishmentType;
import dev.pgm.community.usernames.UsernameService;
import dev.pgm.community.utils.CommandAudience;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import org.bukkit.configuration.Configuration;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;

public class SQLModerationFeature extends ModerationFeatureBase {

  private SQLModerationService service;

  public SQLModerationFeature(
      Configuration config,
      Logger logger,
      DatabaseConnection connection,
      UsernameService usernames) {
    super(new ModerationConfig(config), logger, usernames);
    this.service = new SQLModerationService(connection, getModerationConfig(), usernames);
    logger.info("Punishments (SQL) have been enabled");
  }

  @Override
  public Punishment punish(
      PunishmentType type,
      UUID target,
      CommandAudience issuer,
      String reason,
      Duration duration,
      boolean active,
      boolean silent) {
    Punishment punishment = super.punish(type, target, issuer, reason, duration, active, silent);

    if (getModerationConfig().isPersistent()) {
      service.save(punishment);
    }
    return punishment;
  }

  @Override
  public CompletableFuture<List<Punishment>> query(String target) {
    if (UsernameService.USERNAME_REGEX.matcher(target).matches()) {
      // CONVERT TO UUID if username
      return getUsernames()
          .getStoredId(target)
          .thenApplyAsync(
              uuid ->
                  uuid.isPresent()
                      ? service.query(uuid.get().toString()).join()
                      : Lists.newArrayList());
    }
    return service.query(target);
  }

  @Override
  public CompletableFuture<Boolean> pardon(String target, Optional<UUID> issuer) {
    if (UsernameService.USERNAME_REGEX.matcher(target).matches()) {
      return getUsernames()
          .getStoredId(target)
          .thenApplyAsync(
              uuid -> uuid.isPresent() ? service.pardon(uuid.get(), issuer).join() : false);
    }
    return service.pardon(UUID.fromString(target), issuer);
  }

  @Override
  public CompletableFuture<Boolean> isBanned(String target) {
    if (UsernameService.USERNAME_REGEX.matcher(target).matches()) {
      return getUsernames()
          .getStoredId(target)
          .thenApplyAsync(
              uuid -> uuid.isPresent() ? service.isBanned(uuid.get().toString()).join() : false);
    }
    return service.isBanned(target);
  }

  @Override
  public void onPreLogin(AsyncPlayerPreLoginEvent event) {
    List<Punishment> punishments;
    try {
      punishments = service.query(event.getUniqueId().toString()).get();

      Optional<Punishment> ban = hasActiveBan(punishments);
      if (ban.isPresent()) {
        Punishment punishment = ban.get();

        event.setKickMessage(
            punishment.formatPunishmentScreen(getModerationConfig(), getUsernames()));
        event.setLoginResult(Result.KICK_BANNED);
      }

      logger.info(
          punishments.size()
              + " Punishments have been fetched for "
              + event.getUniqueId().toString());
    } catch (InterruptedException | ExecutionException e) {
      event.setLoginResult(Result.KICK_OTHER);
      event.setKickMessage("Error, please try again."); // TODO: Pretty this up
      e.printStackTrace();
    }
  }

  private Optional<Punishment> hasActiveBan(List<Punishment> punishments) {
    return punishments.stream()
        .filter(p -> p.isActive() && PunishmentType.isBan(p.getType()))
        .findAny();
  }

  @Override
  public CompletableFuture<PunishmentType> getNextPunishment(UUID target) {
    return service.getNextPunishment(target);
  }
}