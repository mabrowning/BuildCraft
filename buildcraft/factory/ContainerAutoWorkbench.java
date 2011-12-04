package buildcraft.factory;

import buildcraft.factory.TileAutoWorkbench;
import net.minecraft.server.ContainerWorkbench;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.InventoryPlayer;
import net.minecraft.server.ItemStack;
import net.minecraft.server.World;

import java.util.*;
import net.minecraft.server.*;

public class ContainerAutoWorkbench extends ContainerWorkbench {

   TileAutoWorkbench tile;


   public ContainerAutoWorkbench(InventoryPlayer var1, World var2, TileAutoWorkbench var3) {
      super(var1, var2, 0, 0, 0);
      this.tile = var3;

      for(int var4 = 0; var4 < this.craftInventory.getSize(); ++var4) {
         this.craftInventory.setItem(var4, var3.getItem(var4));
      }

      this.a(this.craftInventory);
   }

   public void a(EntityHuman var1) {
      InventoryPlayer var2 = var1.inventory;
      if(var2.l() != null) {
         var1.b(var2.l());
         var2.b((ItemStack)null);
      }

      for(int var3 = 0; var3 < this.craftInventory.getSize(); ++var3) {
         this.tile.setItem(var3, this.craftInventory.getItem(var3));
      }

   }

   public boolean isUsableByPlayer(EntityHuman var1) {
      return true;
   }

   public boolean b(EntityHuman var1) {
      return true;
   }

    // MaeEdit: Try to prevent infinite items bug.
    public ItemStack a(int i, int j, boolean flag, EntityHuman entityhuman) {
		ItemStack ret = super.a(i, j, flag, entityhuman);
		if (ret != null && ret.count <= 0) {
			ret = null;
			entityhuman.inventory.b((ItemStack)null);
		}
		return ret;
	}
}
