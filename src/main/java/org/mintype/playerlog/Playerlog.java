package org.mintype.playerlog;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Playerlog implements ModInitializer {

    public static final String MOD_ID = "playerlog";

    private static final Map<UUID, Long> damageCooldowns = new HashMap<>();
    private static final Map<UUID, Long> creeperThreatCooldowns = new HashMap<>();
    private static final Map<UUID, Long> drowningCooldowns = new HashMap<>();

    @Override
    public void onInitialize() {

        ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (entity, source, baseDamage, damageTaken, blocked) -> {

                    ModConfig config = ModConfig.INSTANCE;

                    if (!config.enabled) {
                        return;
                    }

                    if (!config.notifications.damage) {
                        return;
                    }

                    if (!(entity instanceof ServerPlayer damagedPlayer)) {
                        return;
                    }

                    boolean isDrowning =
                            source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING);

                    if (isDrowning) {
                        if (!config.notifications.drowning) {
                            return;
                        }

                        if (damagedPlayer.getHealth() > config.drowning.healthThreshold) {
                            return;
                        }

                        if (isOnCooldown(
                                drowningCooldowns,
                                damagedPlayer.getUUID(),
                                config.cooldowns.drowning
                        )) {
                            return;
                        }
                    }

                    if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                            && !config.notifications.fire) {
                        return;
                    }

                    if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                            && !config.notifications.fallDamage) {
                        return;
                    }

                    if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                            && !config.notifications.projectileDamage) {
                        return;
                    }

                    boolean lowHealth =
                            damagedPlayer.getHealth()
                                    < config.damage.lowHealthThreshold;

                    boolean enoughDamage =
                            damageTaken >= config.damage.minimumDamage;

                    if (!enoughDamage && !lowHealth) {
                        return;
                    }

                    MinecraftServer server = damagedPlayer.level().getServer();

                    if (server == null) {
                        return;
                    }

                    if (!isDrowning) {
                        if (isOnCooldown(
                                damageCooldowns,
                                damagedPlayer.getUUID(),
                                config.cooldowns.damage
                        )) {
                            return;
                        }
                    }

                    // Extract attacker/source details
                    String attackerStr;
                    if (source.getEntity() instanceof ServerPlayer attacker) {
                        attackerStr = attacker.getName().getString() + " (Player)";
                    } else if (source.getEntity() != null) {
                        attackerStr = source.getEntity().getDisplayName().getString();
                    } else {
                        attackerStr = source.getMsgId();
                    }

                    String posStr = String.format("%d, %d, %d (%s)",
                            damagedPlayer.blockPosition().getX(),
                            damagedPlayer.blockPosition().getY(),
                            damagedPlayer.blockPosition().getZ(),
                            damagedPlayer.level().dimension().identifier().getPath());

                    String logDetails = String.format("Took %.1f damage (Blocked: %b)", damageTaken, blocked);

                    // Log to daily file
                    EventLogger.log(
                            isDrowning ? "DROWNING" : "DAMAGE",
                            damagedPlayer.getName().getString(),
                            attackerStr,
                            damagedPlayer.getHealth(),
                            posStr,
                            logDetails
                    );

                    Component message = createDamageMessage(
                            damagedPlayer,
                            source,
                            damageTaken
                    );

                    notifyRecipients(server, message);
                }
        );

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        registerCommands(dispatcher)
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ModConfig config = ModConfig.INSTANCE;

            if (!config.enabled) {
                return;
            }

            if (!config.notifications.creeperThreat) {
                return;
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {

                // Don't alert about spectators
                if (player.gameMode.getGameModeForPlayer()
                        == GameType.SPECTATOR) {
                    continue;
                }

                Creeper creeper = findThreateningCreeper(player);

                if (creeper == null) {
                    continue;
                }

                if (isOnCooldown(
                        creeperThreatCooldowns,
                        player.getUUID(),
                        config.cooldowns.creeperThreat
                )) {
                    continue;
                }

                String posStr = String.format("%d, %d, %d (%s)",
                        player.blockPosition().getX(),
                        player.blockPosition().getY(),
                        player.blockPosition().getZ(),
                        player.level().dimension().identifier().getPath());

                // Log creeper event to daily file
                EventLogger.log(
                        "CREEPER_THREAT",
                        player.getName().getString(),
                        "Creeper",
                        player.getHealth(),
                        posStr,
                        "Targeted by creeper"
                );

                Component message = createCreeperThreatMessage(
                        player,
                        creeper
                );

                notifyRecipients(server, message);
            }
        });
    }

    private static Creeper findThreateningCreeper(
            ServerPlayer player
    ) {
        ModConfig.CreeperThreat config =
                ModConfig.INSTANCE.creeperThreat;

        double range = config.detectionRange;

        for (Creeper creeper : player.level().getEntitiesOfClass(
                Creeper.class,
                player.getBoundingBox().inflate(range)
        )) {

            if (creeper.getTarget() != player) {
                continue;
            }

            if (config.requireLineOfSight
                    && !creeper.hasLineOfSight(player)) {
                continue;
            }

            return creeper;
        }

        return null;
    }

    private static MutableComponent createCreeperThreatMessage(
            ServerPlayer player,
            Creeper creeper
    ) {
        ModConfig config = ModConfig.INSTANCE;

        MutableComponent message = Component.empty()
                .append(
                        Component.literal(config.chat.prefix + " ")
                                .withStyle(ChatFormatting.GRAY)
                )
                .append(
                        player.getName().copy()
                                .withStyle(ChatFormatting.YELLOW)
                )
                .append(
                        Component.literal(" is being targeted by a Creeper!")
                                .withStyle(ChatFormatting.RED)
                );

        if (config.chat.includeCoordinates) {
            message.append(
                    Component.literal(
                            String.format(
                                    " [%d, %d, %d]",
                                    player.blockPosition().getX(),
                                    player.blockPosition().getY(),
                                    player.blockPosition().getZ()
                            )
                    ).withStyle(ChatFormatting.DARK_AQUA)
            );
        }

        return message;
    }

    private static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
                Commands.literal("playerlog")

                        // /playerlog
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal(
                                            "PlayerLog is "
                                                    + (ModConfig.INSTANCE.enabled
                                                    ? "enabled"
                                                    : "disabled")
                                    ),
                                    false
                            );

                            return 1;
                        })

                        // /playerlog enable
                        .then(Commands.literal("enable")
                                .requires(source -> {
                                    if (source.getEntity() == null) return true;
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        return source.getServer()
                                                .getPlayerList()
                                                .isOp(new NameAndId(
                                                        player.getGameProfile()
                                                ));
                                    }
                                    return false;
                                })
                                .executes(context -> {
                                    ModConfig.INSTANCE.enabled = true;

                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "PlayerLog enabled."
                                            ),
                                            true
                                    );

                                    return 1;
                                }))

                        // /playerlog disable
                        .then(Commands.literal("disable")
                                .requires(source -> {
                                    if (source.getEntity() == null) return true;
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        return source.getServer()
                                                .getPlayerList()
                                                .isOp(new NameAndId(
                                                        player.getGameProfile()
                                                ));
                                    }
                                    return false;
                                })
                                .executes(context -> {
                                    ModConfig.INSTANCE.enabled = false;

                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "PlayerLog disabled."
                                            ),
                                            true
                                    );

                                    return 1;
                                }))

                        // /playerlog reload
                        .then(Commands.literal("reload")
                                .requires(source -> {
                                    if (source.getEntity() == null) return true;
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        return source.getServer()
                                                .getPlayerList()
                                                .isOp(new NameAndId(
                                                        player.getGameProfile()
                                                ));
                                    }
                                    return false;
                                })
                                .executes(context -> {
                                    ModConfig.INSTANCE = ModConfig.load();

                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "PlayerLog config reloaded."
                                            ),
                                            true
                                    );

                                    return 1;
                                }))

                        // /playerlog help
                        .then(Commands.literal("help")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();

                                    source.sendSuccess(
                                            () -> Component.empty()
                                                    .append(
                                                            Component.literal(
                                                                    "PlayerLog Commands\n"
                                                            ).withStyle(ChatFormatting.GOLD)
                                                    )
                                                    .append(
                                                            Component.literal(
                                                                    "/pl - Show PlayerLog status\n"
                                                            ).withStyle(ChatFormatting.GRAY)
                                                    )
                                                    .append(
                                                            Component.literal(
                                                                    "/pl enable - Enable PlayerLog\n"
                                                            ).withStyle(ChatFormatting.GRAY)
                                                    )
                                                    .append(
                                                            Component.literal(
                                                                    "/pl disable - Disable PlayerLog\n"
                                                            ).withStyle(ChatFormatting.GRAY)
                                                    )
                                                    .append(
                                                            Component.literal(
                                                                    "/pl reload - Reload the config\n"
                                                            ).withStyle(ChatFormatting.GRAY)
                                                    )
                                                    .append(
                                                            Component.literal(
                                                                    "/pl tp <player> - Teleport to a player\n"
                                                            ).withStyle(ChatFormatting.GRAY)
                                                    )
                                                    .append(
                                                            Component.literal(
                                                                    "/pl help - Show this help message"
                                                            ).withStyle(ChatFormatting.GRAY)
                                                    ),
                                            false
                                    );

                                    return 1;
                                }))

                        // /playerlog tp <player>
                        .then(Commands.literal("tp")
                                .requires(source -> {
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        return false;
                                    }
                                    if (source.getServer()
                                            .getPlayerList()
                                            .isOp(new NameAndId(
                                                    player.getGameProfile()
                                            ))) {
                                        return true;
                                    }
                                    return player.gameMode
                                            .getGameModeForPlayer()
                                            == GameType.SPECTATOR;
                                })
                                .then(Commands.argument(
                                                        "player",
                                                        EntityArgument.player()
                                                )
                                                .executes(context -> {
                                                    ServerPlayer target =
                                                            EntityArgument.getPlayer(
                                                                    context,
                                                                    "player"
                                                            );

                                                    ServerPlayer executor =
                                                            context.getSource()
                                                                    .getPlayerOrException();

                                                    executor.teleportTo(
                                                            target.getX(),
                                                            target.getY(),
                                                            target.getZ()
                                                    );

                                                    context.getSource().sendSuccess(
                                                            () -> Component.literal(
                                                                    "Teleported to "
                                                                            + target.getName()
                                                                            .getString()
                                                            ),
                                                            false
                                                    );

                                                    return 1;
                                                })
                                )
                        )
        );

        // /pl alias
        dispatcher.register(
                Commands.literal("pl")
                        .redirect(
                                dispatcher.getRoot()
                                        .getChild("playerlog")
                        )
        );
    }

    private static MutableComponent createDamageMessage(
            ServerPlayer player,
            DamageSource source,
            float damage
    ) {
        ModConfig config = ModConfig.INSTANCE;

        MutableComponent message = Component.empty()
                .append(Component.literal(config.chat.prefix + " ")
                        .withStyle(ChatFormatting.GRAY))
                .append(player.getName().copy()
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" took ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%.1f", damage))
                        .withStyle(ChatFormatting.RED))
                .append(Component.literal(" damage")
                        .withStyle(ChatFormatting.GRAY));

        if (config.damage.includeSource) {
            message.append(
                    Component.literal(" from ")
                            .withStyle(ChatFormatting.GRAY)
            );

            if (source.getEntity() instanceof ServerPlayer attacker) {
                message.append(
                        attacker.getName().copy()
                                .withStyle(ChatFormatting.RED)
                );
            } else if (source.getEntity() != null) {
                message.append(
                        source.getEntity().getDisplayName()
                                .copy()
                                .withStyle(ChatFormatting.RED)
                );
            } else {
                message.append(
                        Component.literal(source.getMsgId())
                                .withStyle(ChatFormatting.GOLD)
                );
            }
        }

        if (config.damage.includeHealth) {
            message.append(Component.literal(
                    String.format(
                            " (%.1f/%.1f HP)",
                            player.getHealth(),
                            player.getMaxHealth()
                    )
            ).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (config.chat.includeCoordinates) {
            message.append(Component.literal(
                    String.format(
                            " [%d, %d, %d]",
                            player.blockPosition().getX(),
                            player.blockPosition().getY(),
                            player.blockPosition().getZ()
                    )
            ).withStyle(ChatFormatting.DARK_AQUA));
        }

        if (config.chat.includeTeleport) {
            int x = player.blockPosition().getX();
            int y = player.blockPosition().getY();
            int z = player.blockPosition().getZ();

            message.append(
                    Component.literal(" ")
                            .withStyle(ChatFormatting.GRAY)
            );

            message.append(
                    Component.literal("[Teleport]")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(
                                            new ClickEvent.RunCommand(
                                                    "/pl tp " + player.getName().getString()
                                            )
                                    )
                                    .withHoverEvent(
                                            new HoverEvent.ShowText(
                                                    Component.literal(
                                                            String.format(
                                                                    "Teleport to %s\n(%d, %d, %d)",
                                                                    player.getName().getString(),
                                                                    x,
                                                                    y,
                                                                    z
                                                            )
                                                    )
                                            )
                                    )
                            )
            );
        }

        return message;
    }

    private static void notifyRecipients(
            MinecraftServer server,
            Component message
    ) {
        ModConfig.Recipients recipients = ModConfig.INSTANCE.recipients;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {

            boolean isOperator =
                    server.getPlayerList().isOp(player.nameAndId());

            boolean isSpectator =
                    player.gameMode.getGameModeForPlayer()
                            == GameType.SPECTATOR;

            boolean shouldNotify =
                    (recipients.operators && isOperator)
                            || (recipients.spectators && isSpectator);

            if (shouldNotify) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static boolean isOnCooldown(
            Map<UUID, Long> cooldowns,
            UUID playerId,
            double cooldownSeconds
    ) {
        if (cooldownSeconds <= 0) {
            return false;
        }

        long now = System.currentTimeMillis();

        Long lastNotification = cooldowns.get(playerId);

        if (lastNotification != null) {
            long cooldownMillis = (long) (cooldownSeconds * 1000);

            if (now - lastNotification < cooldownMillis) {
                return true;
            }
        }

        cooldowns.put(playerId, now);
        return false;
    }
}