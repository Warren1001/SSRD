package net.ranold;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.UUID;

public class SSRDCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            Class<?> subLevelArgClass = Class.forName("dev.ryanhcode.sable.api.command.SubLevelArgumentType");
            ArgumentType<?> subLevelArg = (ArgumentType<?>) subLevelArgClass.getMethod("subLevels").invoke(null);

            dispatcher.register(Commands.literal("ssrd")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("forceload")
                            .then(Commands.literal("add")
                                    .then(Commands.argument("sub_levels", subLevelArg)
                                            .executes(SSRDCommand::addForceload))
                                    .then(Commands.argument("uuid", UuidArgument.uuid())
                                            .executes(SSRDCommand::addForceloadUuid)))
                            .then(Commands.literal("remove")
                                    .then(Commands.argument("sub_levels", subLevelArg)
                                            .executes(SSRDCommand::removeForceload))
                                    .then(Commands.argument("uuid", UuidArgument.uuid())
                                            .executes(SSRDCommand::removeForceloadUuid)))
                            .then(Commands.literal("list")
                                    .executes(SSRDCommand::listForceload))
                    )
            );
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("SSRD: Failed to register commands", e);
        }
    }

    private static int addForceload(CommandContext<CommandSourceStack> context) {
        try {
            Class<?> subLevelArgClass = Class.forName("dev.ryanhcode.sable.api.command.SubLevelArgumentType");
            Collection<?> subLevels = (Collection<?>) subLevelArgClass.getMethod("getSubLevels", CommandContext.class, String.class).invoke(null, context, "sub_levels");
            if (subLevels == null) {
                context.getSource().sendFailure(Component.literal("No sub-levels matched."));
                return 0;
            }
            return addSubLevels(context.getSource(), subLevels);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("SSRD: Error in addForceload command", e);
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int addForceloadUuid(CommandContext<CommandSourceStack> context) {
        try {
            UUID uuid = UuidArgument.getUuid(context, "uuid");
            return addUuid(context.getSource(), uuid);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("SSRD: Error in addForceloadUuid command", e);
            return 0;
        }
    }

    private static int removeForceload(CommandContext<CommandSourceStack> context) {
        try {
            Class<?> subLevelArgClass = Class.forName("dev.ryanhcode.sable.api.command.SubLevelArgumentType");
            Collection<?> subLevels = (Collection<?>) subLevelArgClass.getMethod("getSubLevels", CommandContext.class, String.class).invoke(null, context, "sub_levels");
            if (subLevels == null) {
                context.getSource().sendFailure(Component.literal("No sub-levels matched."));
                return 0;
            }
            return removeSubLevels(context.getSource(), subLevels);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("SSRD: Error in removeForceload command", e);
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeForceloadUuid(CommandContext<CommandSourceStack> context) {
        try {
            UUID uuid = UuidArgument.getUuid(context, "uuid");
            return removeUuid(context.getSource(), uuid);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("SSRD: Error in removeForceloadUuid command", e);
            return 0;
        }
    }

    private static int addUuid(CommandSourceStack source, UUID uuid) {
        ServerLevel level = source.getLevel();
        SSRDForceloadData data = SSRDForceloadData.get(level);
        if (!data.getEntries().containsKey(uuid)) {
            data.getEntries().put(uuid, new SSRDForceloadData.ForceloadEntry(uuid, level.dimension().location(), null));
            data.setDirty();
            source.sendSuccess(() -> Component.literal("Added SubLevel " + uuid + " to forceload list."), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("SubLevel is already forceloaded."));
            return 0;
        }
    }

    private static int removeUuid(CommandSourceStack source, UUID uuid) {
        ServerLevel level = source.getLevel();
        SSRDForceloadData data = SSRDForceloadData.get(level);
        if (data.getEntries().remove(uuid) != null) {
            data.setDirty();
            source.sendSuccess(() -> Component.literal("Removed SubLevel " + uuid + " from forceload list."), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("SubLevel was not forceloaded."));
            return 0;
        }
    }

    private static int addSubLevels(CommandSourceStack source, Collection<?> subLevels) {
        ServerLevel level = source.getLevel();
        SSRDForceloadData data = SSRDForceloadData.get(level);
        int added = 0;
        for (Object sl : subLevels) {
            try {
                UUID uuid = (UUID) sl.getClass().getMethod("getUniqueId").invoke(sl);
                if (!data.getEntries().containsKey(uuid)) {
                    Object pose = sl.getClass().getMethod("logicalPose").invoke(sl);
                    
                    double x, z;
                    try {
                        // Try pose.position() first (Sable style)
                        Object posObj = pose.getClass().getMethod("position").invoke(pose);
                        try {
                            x = (double) posObj.getClass().getMethod("x").invoke(posObj);
                            z = (double) posObj.getClass().getMethod("z").invoke(posObj);
                        } catch (Exception e2) {
                            x = posObj.getClass().getField("x").getDouble(posObj);
                            z = posObj.getClass().getField("z").getDouble(posObj);
                        }
                    } catch (Exception e) {
                        try {
                            // Try methods directly on pose (JOML/Record style)
                            x = (double) pose.getClass().getMethod("x").invoke(pose);
                            z = (double) pose.getClass().getMethod("z").invoke(pose);
                        } catch (Exception e3) {
                            // Try fields directly on pose
                            x = pose.getClass().getField("x").getDouble(pose);
                            z = pose.getClass().getField("z").getDouble(pose);
                        }
                    }
                    
                    data.getEntries().put(uuid, new SSRDForceloadData.ForceloadEntry(uuid, level.dimension().location(), new ChunkPos((int)x >> 4, (int)z >> 4)));
                    added++;
                }
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().error("SSRD: Failed to process sub-level for forceload", e);
            }
        }
        if (added > 0) {
            data.setDirty();
            int finalAdded = added;
            source.sendSuccess(() -> Component.literal("Added " + finalAdded + " SubLevel(s) to forceload list."), true);
        } else if (subLevels.isEmpty()) {
            source.sendFailure(Component.literal("No sub-levels were selected."));
        } else {
            source.sendFailure(Component.literal("No new SubLevels were added."));
        }
        return added;
    }

    private static int removeSubLevels(CommandSourceStack source, Collection<?> subLevels) {
        ServerLevel level = source.getLevel();
        SSRDForceloadData data = SSRDForceloadData.get(level);
        int removed = 0;
        for (Object sl : subLevels) {
            try {
                UUID uuid = (UUID) sl.getClass().getMethod("getUniqueId").invoke(sl);
                if (data.getEntries().remove(uuid) != null) {
                    removed++;
                }
            } catch (Exception ignored) {}
        }
        if (removed > 0) {
            data.setDirty();
            int finalRemoved = removed;
            source.sendSuccess(() -> Component.literal("Removed " + finalRemoved + " SubLevel(s) from forceload list."), true);
        } else {
            source.sendFailure(Component.literal("No SubLevels were removed."));
        }
        return removed;
    }

    private static int listForceload(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        SSRDForceloadData data = SSRDForceloadData.get(level);

        if (data.getEntries().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No SubLevels are currently forceloaded."), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("Forceloaded SubLevels:"), false);
            for (SSRDForceloadData.ForceloadEntry entry : data.getEntries().values()) {
                String posStr = entry.pos != null ? entry.pos.x + ", " + entry.pos.z : "unknown";
                String dimStr = entry.dimension != null ? entry.dimension.toString() : "unknown";
                context.getSource().sendSuccess(() -> Component.literal("- " + entry.uuid + " [Dim: " + dimStr + ", Chunk: " + posStr + "]"), false);
            }
        }
        return 1;
    }
}
