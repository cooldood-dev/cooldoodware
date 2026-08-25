package com.github.cooldood.modules.impl.player;

import com.github.cooldood.events.SubscribeEvent;
import com.github.cooldood.events.impl.PacketEvent;
import com.github.cooldood.modules.Category;
import com.github.cooldood.modules.Module;
import com.github.cooldood.modules.RegisterModule;
import com.github.cooldood.utils.client.C;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.EnumChatFormatting;

@RegisterModule(
        name = "Killsults",
        description = "Automatically insults killed players.",
        category = Category.PLAYER
)
public class Killsults extends Module {

    private static final String MESSAGE = "Subscribe to @cooldood67420 on yt for more coolware updates";

    @SubscribeEvent
    public static void onChat(PacketEvent.Receive event) {
        if (!(event.packet instanceof S02PacketChat)) return;

        S02PacketChat packet = (S02PacketChat) event.packet;
        String message = EnumChatFormatting.getTextWithoutFormattingCodes(packet.getChatComponent().getUnformattedText());

        // Basic Hypixel kill messages regex check
        // Examples: 
        // "Player1 was killed by Player2."
        // "Player1 was thrown into the void by Player2."
        // "Player1 was struck down by Player2."
        
        String myName = C.p().getName();
        
        if (message.contains("by " + myName) || message.contains("to " + myName)) {
            // Very simple check to ensure it's a kill message. 
            // In a real client, you'd want a more robust regex list like AutoGG uses.
            C.p().sendChatMessage(MESSAGE);
        }
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }
}
