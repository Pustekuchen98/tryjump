package de.bergwerklabs.tryjump.core;

import de.bergwerklabs.framework.bedrock.api.LabsGame;
import de.bergwerklabs.framework.bedrock.api.PlayerRegistry;
import de.bergwerklabs.framework.commons.spigot.general.timer.LabsTimer;
import de.bergwerklabs.framework.commons.spigot.item.ItemStackBuilder;
import de.bergwerklabs.framework.commons.spigot.scoreboard.LabsScoreboard;
import de.bergwerklabs.framework.commons.spigot.scoreboard.Row;
import de.bergwerklabs.tryjump.api.DeathmatchArena;
import de.bergwerklabs.tryjump.api.Unit;
import de.bergwerklabs.tryjump.core.listener.deathmatch.DeathmachtListener;
import de.bergwerklabs.tryjump.core.listener.jump.DropItemListener;
import de.bergwerklabs.tryjump.core.listener.jump.FoodLevelChangeListener;
import de.bergwerklabs.tryjump.core.listener.jump.JumpPhaseListener;
import de.bergwerklabs.tryjump.core.listener.jump.PlayerDamageListener;
import de.bergwerklabs.tryjump.core.listener.jump.PlayerInteractListener;
import de.bergwerklabs.tryjump.core.task.UpdatePlayerInfoTask;
import de.bergwerklabs.tryjump.core.unit.UnitPlacer;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Created by Yannic Rieger on 11.02.2018.
 *
 * <p>
 *
 * @author Yannic Rieger
 */
public class TryJump extends LabsGame<Jumper> {

  public TryJump() {
    super("TryJump");
  }

  public BukkitTask getUpdatePlayerInfoTask() {
    return updatePlayerInfoTask;
  }

  public DeathmatchArena getArena() {
    return arena;
  }

  private Collection<Jumper> players;
  private BukkitTask updatePlayerInfoTask;
  private DeathmatchArena arena;

  @Override
  public void start(PlayerRegistry<Jumper> registry) {
    this.playerRegistry = registry;
    this.players = registry.getPlayerCollection();

    final TryJumpSession session = TryJumpSession.getInstance();
    final Location spawn = new Location(Bukkit.getWorld("jump"), 0, 100, 0);
    final double[] count = {35};

    JumpPhaseListener.registerListeners(session);

    this.players.forEach(
        jumper -> {

          // TODO: remove this nonsense
          Queue<TryJumpUnit> units = new LinkedList<>();

          TryJumpSession.getInstance()
              .getPlacer()
              .getSelectedUnits()
              .forEach(
                  blub -> {
                    units.add(blub.clone());
                  });

          jumper.setUnits(units);
          final Location playerSpawn = spawn.clone().add(count[0] + 0.5, 0, 0.5);
          session.getPlacer().getStart().pasteAsync("jump", playerSpawn.toVector());
          jumper.setUnitSpawn(playerSpawn);
          jumper.setStartSpawn(playerSpawn);
          count[0] += 35;
        });

    this.players.forEach(
        jumper -> {
          final Player player = jumper.getPlayer();
          new PotionEffect(PotionEffectType.BLINDNESS, 35, 10, false, false)
              .apply(player.getPlayer());
          jumper.freeze();
          player.teleport(jumper.getUnitSpawn());
        });

    LabsTimer countdown =
        new LabsTimer(
            5,
            timeLeft -> {
              this.players.forEach(
                  jumper -> {
                    final Player spigotPlayer = jumper.getPlayer();
                    spigotPlayer.playSound(spigotPlayer.getEyeLocation(), Sound.CLICK, 100, 1);
                    this.messenger.message(
                        "Das Spiel startet in §b" + timeLeft + " Sekunden§7.", spigotPlayer);
                  });
            });

    LabsTimer jumpTimer =
        new LabsTimer(
            60 * 10,
            timeLeft -> {
              this.players.forEach(
                  player -> {
                    final Player spigotPlayer = player.getPlayer();
                    String timeString = String.format("§b%02d:%02d", timeLeft / 60, timeLeft % 60);
                    player.getScoreboard().setTitle("§6>> §eTryJump §6❘ " + timeString);
                    if (((float) timeLeft / 60F) % 10 == 0) {
                      this.getMessenger()
                          .message("Noch " + timeString + " §7Minuten.", spigotPlayer);
                      spigotPlayer.playSound(
                          spigotPlayer.getEyeLocation(), Sound.NOTE_BASS, 1.0F, 1.0F);
                    } else if (timeLeft <= 5) {
                      this.getMessenger().message("§b" + String.valueOf(timeLeft), spigotPlayer);
                      spigotPlayer.playSound(
                          spigotPlayer.getEyeLocation(), Sound.NOTE_BASS, 1.0F, 1.0F);
                    }
                  });
            });

    countdown.addStopListener(
        event -> {
          this.messenger.messageAll("LOS!");
          this.updatePlayerInfoTask =
              Bukkit.getScheduler()
                  .runTaskTimerAsynchronously(session, new UpdatePlayerInfoTask(session), 0, 10L);
          this.players.forEach(
              jumper -> {
                final Player spigotPlayer = jumper.getPlayer();
                final UnitPlacer placer = session.getPlacer();
                final Vector end = placer.getStart().getMetadata().getEndVector();
                // Since its the of the round, the optional will not be empty.
                final Unit unit = jumper.getNextUnit().get();
                final ItemStack instantDeath =
                    new ItemStackBuilder(Material.INK_SACK)
                        .setName("§c§lInstant-Tod(TM)")
                        .setData((byte) 1)
                        .create();

                jumper.setScoreboard(
                    this.createScoreboard(
                        spigotPlayer,
                        this.players,
                        session.getTryJumpConfig().getJumpPhaseDuration()));
                jumper.unfreeze();
                spigotPlayer.getInventory().setItem(4, instantDeath);
                Location to = jumper.getStartSpawn().clone().subtract(end);
                jumper.setCurrentUnit(unit);
                placer.placeUnit(to, (TryJumpUnit) unit, false);
                jumpTimer.start();

                // Register at this point so players cannot use instant death.
                Bukkit.getPluginManager().registerEvents(new PlayerInteractListener(this), session);
              });
        });

    jumpTimer.addStopListener(
        event -> {
          // ONLY FOR TEST PURPOSES [START]
          Bukkit.getOnlinePlayers().forEach(p -> p.kickPlayer("Restart..."));
          Bukkit.getServer().spigot().restart();
          // ONLY FOR TEST PURPOSES [END]
        });

    countdown.start();
  }

  @Override
  public void stop() {
    // TODO: save stats
    DeathmachtListener.unregisterListeners();
  }

  private LabsScoreboard createScoreboard(Player self, Collection<Jumper> players, int duration) {
    String timeString = String.format("§b%02d:%02d", duration / 60, duration % 60);
    LabsScoreboard scoreboard = new LabsScoreboard("§6>> §eTryJump §6❘ " + timeString, "distance");
    scoreboard.addRow(players.size() + 5, new Row(scoreboard, "§a§a§a§a"));
    scoreboard.addRow(players.size() + 4, new Row(scoreboard, "§eTokens: §b0"));
    scoreboard.addRow(players.size() + 3, new Row(scoreboard, "§a§a§a"));
    scoreboard.addRow(2, new Row(scoreboard, "§a§a"));
    scoreboard.addRow(1, new Row(scoreboard, "§6§m-------------"));
    scoreboard.addRow(0, new Row(scoreboard, "§ebergwerkLABS.de"));

    final int[] count = {2};

    players.forEach(
        jumper -> {
          final Player spigotPlayer = jumper.getPlayer();
          if (spigotPlayer.getUniqueId().equals(self.getUniqueId())) {
            scoreboard.addPlayerSpecificRow(
                count[0]++,
                spigotPlayer,
                new Row(scoreboard, "§7§n" + spigotPlayer.getDisplayName() + "§r" + " §b0%"));

          } else {
            scoreboard.addPlayerSpecificRow(
                count[0]++,
                spigotPlayer,
                new Row(scoreboard, "§7" + spigotPlayer.getDisplayName() + "§r" + " §b0%"));
          }
        });
    return scoreboard;
  }
}
