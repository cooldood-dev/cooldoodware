package com.github.cooldood.commands.impl;

import com.github.cooldood.commands.Command;
import com.github.cooldood.modules.Module;
import com.github.cooldood.utils.client.FuzzySearchUtil;
import com.github.cooldood.utils.client.KeybindHandler;
import com.github.cooldood.utils.minecraft.ChatUtil;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BindCommand extends Command {
    @Override
    public String name() {
        return "bind";
    }

    @Override
    public boolean execute(String[] args) {
        if (args[0].equals("list")) {
            for (Map.Entry<Integer, List<Module>> entry : KeybindHandler.keybindsMap.entrySet()) {
                ChatUtil.prefixMessage("§f" + entry.getValue().stream()
                        .map(e -> "§6" + e.getAnnotation().name()).collect(Collectors.joining(" §fand "))
                        + " §cis bound to §f" + KeybindHandler.getCharacter(entry.getKey())
                );
            }
        }
        else if (args[0].equals("remove")) {
            if (args.length != 2) return false;

            Module module = FuzzySearchUtil.findModule(args[1]);
            if (module == null) return false;

            KeybindHandler.removeKeybind(module);
            ChatUtil.prefixMessage("&cSuccessfully unbound &f" + module.getAnnotation().name() + "&c!");
        }
        else {
            Module module = FuzzySearchUtil.findModule(args[0]);

            if (module == null) return false;

            ChatUtil.prefixMessage("&cPress the keybind you wish to bind &f" + module.getAnnotation().name() + " &cto!");
            KeybindHandler.listeningModule = module;
        }

        return true;
    }

    @Override
    public String[] usage() {
        return new String[] {
                "list",
                "remove <module>",
                "<module> [enter] [key]",
        };
    }
}
