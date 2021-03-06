package de.bergwerklabs.tryjump.core;

import com.google.common.base.Preconditions;
import de.bergwerklabs.framework.bedrock.api.LabsPlayer;
import de.bergwerklabs.framework.commons.spigot.scoreboard.LabsScoreboard;
import de.bergwerklabs.framework.commons.spigot.scoreboard.Row;
import de.bergwerklabs.framework.commons.spigot.title.Title;
import de.bergwerklabs.tryjump.api.TryJumpPlayer;
import de.bergwerklabs.tryjump.api.Unit;
import de.bergwerklabs.tryjump.api.event.UnitToggleLiteEvent;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Yannic Rieger on 11.02.2018.
 *
 * <p>
 *
 * @author Yannic Rieger
 */
public class Jumper extends LabsPlayer implements TryJumpPlayer {

  private Queue<TryJumpUnit> unitsAhead;
  private Set<TryJumpUnit> completed = new HashSet<>();
  private Location unitSpawn, startSpawn;
  private Unit current;
  private LabsScoreboard scoreboard;
  private int currentFails;
  private int totalFails;
  private int jumpProgress;
  private int totalKills;
  private int currentKills;
  private int failsInSession;
  private int livesLeft = 3;
  private int tokens;
  private long lastUse;
  private long lastRespawn;

  Jumper(Player player) {
    super(player.getUniqueId());
  }

  @Override
  public int getWins() {
    return 0;
  }

  @Override
  public int getTokens() {
    return this.tokens;
  }

  @Override
  public int getTotalKills() {
    return this.totalKills;
  }

  @Override
  public int getCurrentKills() {
    return this.currentKills;
  }

  @Override
  public int getLosses() {
    return 0;
  }

  @Override
  public int getLivesLeft() {
    return this.livesLeft;
  }

  @Override
  public Unit getCurrentUnit() {
    return this.current;
  }

  @Override
  public Set<Unit> getCompletedUnits() {
    return new HashSet<>(completed);
  }

  @Override
  public Queue<Unit> getUnitsAhead() {
    return new LinkedList<>(this.unitsAhead);
  }

  @Override
  public int getCurrentFails() {
    return this.currentFails;
  }

  /**
   * Sets the units for the player.
   *
   * @param units {@link Queue} containing the units.
   */
  public void setUnits(Queue<TryJumpUnit> units) {
    Preconditions.checkNotNull(units);
    this.unitsAhead = units;
  }

  /**
   * Gets the next unit contained in {@link TryJumpPlayer#getUnitsAhead()}.
   *
   * @return {@link Optional} containing the next unit. {@link Optional#empty()} when there is no
   *     unit left.
   */
  public Optional<TryJumpUnit> getNextUnit() {
    TryJumpUnit unit = this.unitsAhead.poll();
    if (unit == null) return Optional.empty();
    return Optional.of(unit);
  }

  /**
   * Sets the current unit for the player.
   *
   * @param unit unit to be set.
   */
  public void setCurrentUnit(@NotNull Unit unit) {
    Preconditions.checkNotNull(unit);
    this.current = unit;
  }

  /**
   * Sets the current fails for the module.
   *
   * @param currentFails number of fails for this module.
   */
  public void setCurrentFails(int currentFails) {
    this.currentFails = currentFails;
  }

  public void addCompletedUnit(@NotNull TryJumpUnit unit) {
    Preconditions.checkNotNull(unit);
    this.completed.add(unit);
  }

  public void updateScoreboardProgress(@NotNull List<Jumper> sorted) {
    final int[] min = {3};

    sorted.forEach(
        jumper -> {
          final Player spigotPlayer = jumper.getPlayer();
          Row row = this.scoreboard.getPlayerSpecificRows().get(spigotPlayer.getUniqueId());
          if (row != null) {
            if (spigotPlayer.getUniqueId().equals(this.getPlayer().getUniqueId())) {
              row.setText(
                  "§7§n" + spigotPlayer.getDisplayName() + "§r §b" + this.jumpProgress + "%");
            } else {
              row.setText(
                  "§7" + spigotPlayer.getDisplayName() + "§r §b" + jumper.getJumpProgress() + "%");
            }
            row.setScore(min[0]++);
          }
        });
  }

  public String buildActionbarText() {
    final StringBuilder builder = new StringBuilder();
    builder.append("§6§l>> ");
    builder.append("§7§lUnit ");
    builder.append(this.completed.size() + 1);

    if (this.isLite()) {
      builder.append(" Lite");
    }

    builder.append(": §b");
    builder.append(this.current.getName());
    builder.append(" §6§l❘ ");
    builder.append(this.current.getDifficulty().getDisplayName());
    builder.append(" §6§l<<");
    return builder.toString();
  }

  public void resetToSpawn() {
    final Player player = this.getPlayer();
    this.currentFails++;
    this.totalFails++;
    this.failsInSession++;

    if (this.currentFails <= 3) {
      String red = StringUtils.repeat("§c✖", this.currentFails);
      String grey = StringUtils.repeat("§7✖", 3 - this.currentFails);
      new Title("", red + grey, 20, 20, 20).display(player);
    }

    if (this.currentFails == 3) {
      player.playSound(player.getEyeLocation(), Sound.ITEM_PICKUP, 100, 1);
      TryJumpSession.getInstance()
          .getPlacer()
          .placeUnit(this.unitSpawn, (TryJumpUnit) this.current, true);
      Bukkit.getPluginManager().callEvent(new UnitToggleLiteEvent(this, this.current));
    }

    player.teleport(this.unitSpawn);
  }

  /**
   * Updates the Token display in the jump phase scoreboard.
   *
   * @param amount amount of tokens
   */
  public void updateJumpPhaseTokenDisplay(int amount) {
    final Row row = this.scoreboard.getRowsByContent().get("§eTokens: §b" + this.tokens);
    this.addTokens(amount);
    row.setText("§eTokens: §b" + this.getTokens());
  }

  public void updateKills() {
    final Row row = this.scoreboard.getRowsByContent().get("§eKills: §b" + this.currentKills);
    this.currentKills++;
    this.totalKills++;
    row.setText("§eKills: §b" + this.currentKills);
  }

  public int decrementLife() {
    return --this.livesLeft;
  }

  @Override
  public int getTotalFails() {
    return totalFails;
  }

  @Override
  public boolean isLite() {
    return this.currentFails >= 3;
  }

  public Location getUnitSpawn() {
    return unitSpawn;
  }

  public void setUnitSpawn(Location unitSpawn) {
    this.unitSpawn = unitSpawn;
    this.getPlayer().setBedSpawnLocation(unitSpawn, true);
  }

  public LabsScoreboard getScoreboard() {
    return scoreboard;
  }

  public void setScoreboard(LabsScoreboard scoreboard) {
    this.scoreboard = scoreboard;
    this.scoreboard.apply(this.getPlayer());
  }

  public Location getStartSpawn() {
    return startSpawn;
  }

  public void setStartSpawn(Location startSpawn) {
    this.startSpawn = startSpawn;
    this.getPlayer().setBedSpawnLocation(startSpawn, true);
  }

  public void addTokens(int amount) {
    this.tokens += amount;
  }

  public int getJumpProgress() {
    return jumpProgress;
  }

  public void setJumpProgress(int jumpProgress) {
    this.jumpProgress = jumpProgress;
  }

  public long getLastUse() {
    return lastUse;
  }

  public void setLastUse(long lastUse) {
    this.lastUse = lastUse;
  }

  public long getLastRespawn() {
    return lastRespawn;
  }

  public void setLastRespawn(long lastRespawn) {
    this.lastRespawn = lastRespawn;
  }

  public int getFailsInSession() {
    return failsInSession;
  }
}
