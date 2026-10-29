package com.xt9y.metal;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

/** /metalbench runs a complete default capture without configuration changes. */
public final class MetalBenchCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "metalbench";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/metalbench [start [10-120 seconds]|stop|status]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String result;
        if (args.length == 0 || "start".equalsIgnoreCase(args[0])) {
            int seconds = 60;
            if (args.length >= 2) {
                try {
                    seconds = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.addChatMessage(new ChatComponentText("[GTNH Metal] Invalid duration"));
                    return;
                }
            }
            result = MetalBenchmark.INSTANCE.start(seconds, ClientRuntime.INSTANCE.activeBackend());
        } else if ("stop".equalsIgnoreCase(args[0])) {
            result = MetalBenchmark.INSTANCE.stop();
        } else if ("status".equalsIgnoreCase(args[0])) {
            result = MetalBenchmark.INSTANCE.status();
        } else {
            result = getCommandUsage(sender);
        }
        sender.addChatMessage(new ChatComponentText("[GTNH Metal] " + result));
    }
}
