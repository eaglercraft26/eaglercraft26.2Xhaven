package me.cominixo.betterf3;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public interface BetterF3Module {
   Identifier id();

   String displayName();

   default boolean defaultRight() {
      return false;
   }

   List<String> getLines(Minecraft minecraft);
}
