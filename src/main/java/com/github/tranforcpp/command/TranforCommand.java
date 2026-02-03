package com.github.tranforcpp.command;

import com.github.tranforcpp.TranforCPlusPlus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class TranforCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MiniMessage mm = MiniMessage.miniMessage();
        
        if (args.length == 0) {
            sender.sendMessage(mm.deserialize("<red>用法: /tranforcpp <reload|version> 或 /cpp <reload|version>"));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("tranforcpp.reload")) {
                sender.sendMessage(mm.deserialize("<red>你没有权限执行此命令"));
                return true;
            }
            sender.sendMessage(mm.deserialize("<yellow>正在重载 C++ 插件中..."));
            TranforCPlusPlus.getInstance().reload();
            sender.sendMessage(mm.deserialize("<green>C++ 插件重载完成!"));
            return true;
        }
        if (args[0].equalsIgnoreCase("version")) {
            if (!sender.hasPermission("tranforcpp.version")) {
                sender.sendMessage(mm.deserialize("<red>你没有权限执行此命令"));
                return true;
            }
            String version = TranforCPlusPlus.getInstance().getPluginMeta().getVersion();
            sender.sendMessage(mm.deserialize("<white>[<aqua>TFCPP<white>]<green>当前版本版本: <aqua>" + version));
            return true;
        }

        sender.sendMessage(mm.deserialize("<red>未知指令! 用法: /tranforcpp <reload|version> 或 /cpp <reload|version>"));
        return true;
    }
    

}