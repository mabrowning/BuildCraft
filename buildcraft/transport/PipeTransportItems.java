package buildcraft.transport;

import buildcraft.api.APIProxy;
import buildcraft.api.EntityPassiveItem;
import buildcraft.api.IPipeEntry;
import buildcraft.api.Orientations;
import buildcraft.api.Position;
import buildcraft.core.CoreProxy;
import buildcraft.core.IMachine;
import buildcraft.core.PacketIds;
import buildcraft.core.StackUtil;
import buildcraft.core.Utils;
import buildcraft.transport.IPipeTransportItemsHook;
import buildcraft.transport.PipeTransport;
import buildcraft.transport.TileGenericPipe;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;
import net.minecraft.server.EntityItem;
import net.minecraft.server.IInventory;
import net.minecraft.server.ItemStack;
import net.minecraft.server.NBTTagCompound;
import net.minecraft.server.NBTTagList;
import net.minecraft.server.Packet230ModLoader;
import net.minecraft.server.TileEntity;
import net.minecraft.server.mod_BuildCraftTransport;

public class PipeTransportItems extends PipeTransport {

   public boolean allowBouncing = false;
   public TreeMap travelingEntities = new TreeMap();
   LinkedList entitiesToLoad = new LinkedList();
   HashSet toRemove = new HashSet();


   public void readjustSpeed(EntityPassiveItem var1) {
      if(this.container.pipe instanceof IPipeTransportItemsHook) {
         ((IPipeTransportItemsHook)this.container.pipe).readjustSpeed(var1);
      } else {
         this.defaultReajustSpeed(var1);
      }

   }

   public void defaultReajustSpeed(EntityPassiveItem var1) {
      if(var1.speed > Utils.pipeNormalSpeed) {
         var1.speed -= Utils.pipeNormalSpeed;
      }

      if(var1.speed < Utils.pipeNormalSpeed) {
         var1.speed = Utils.pipeNormalSpeed;
      }

   }

   public void entityEntering(EntityPassiveItem var1, Orientations var2) {
      this.readjustSpeed(var1);
      if(!this.travelingEntities.containsKey(new Integer(var1.entityId))) {
         this.travelingEntities.put(new Integer(var1.entityId), new PipeTransportItems.EntityData(var1, var2));
         if(var1.container != null && var1.container != this.container) {
            ((PipeTransportItems)((TileGenericPipe)var1.container).pipe.transport).scheduleRemoval(var1);
         }

         var1.container = this.container;
      }

      if(var2 != Orientations.YPos && var2 != Orientations.YNeg) {
         var1.setPosition(var1.posX, (double)((float)this.yCoord + Utils.getPipeFloorOf(var1.item)), var1.posZ);
      }

      if(this.container.pipe instanceof IPipeTransportItemsHook) {
         ((IPipeTransportItemsHook)this.container.pipe).entityEntered(var1, var2);
      }

      if(APIProxy.isServerSide() && var1.synchroTracker.markTimeIfDelay(this.worldObj, 20L)) {
         CoreProxy.sendToPlayers(this.createItemPacket(var1, var2), this.xCoord, this.yCoord, this.zCoord, 50, mod_BuildCraftTransport.instance);
      }

   }

   public LinkedList getPossibleMovements(Position var1, EntityPassiveItem var2) {
      LinkedList var3 = new LinkedList();

      for(int var4 = 0; var4 < 6; ++var4) {
         if(Orientations.values()[var4] != var1.orientation.reverse() && this.container.pipe.outputOpen(Orientations.values()[var4])) {
            Position var5 = new Position(var1);
            var5.orientation = Orientations.values()[var4];
            var5.moveForwards(1.0D);
            if(this.canReceivePipeObjects(var5, var2)) {
               var3.add(var5.orientation);
            }
         }
      }

      if(var3.size() == 0 && this.allowBouncing) {
         Position var6 = new Position(var1);
         var6.orientation = var6.orientation.reverse();
         if(this.canReceivePipeObjects(var6, var2)) {
            var3.add(var6.orientation);
         }
      }

      if(this.container.pipe instanceof IPipeTransportItemsHook) {
         var3 = ((IPipeTransportItemsHook)this.container.pipe).filterPossibleMovements(var3, var1, var2);
      }

      return var3;
   }

   public boolean canReceivePipeObjects(Position var1, EntityPassiveItem var2) {
      TileEntity var3 = this.worldObj.getTileEntity((int)var1.x, (int)var1.y, (int)var1.z);
      if(!Utils.checkPipesConnections(this.worldObj, (int)var1.x, (int)var1.y, (int)var1.z, this.xCoord, this.yCoord, this.zCoord)) {
         return false;
      } else if(var3 instanceof IPipeEntry) {
         return true;
      } else if(var3 instanceof TileGenericPipe) {
         TileGenericPipe var4 = (TileGenericPipe)var3;
         return var4.pipe.transport instanceof PipeTransportItems;
      } else {
         return var3 instanceof IInventory && (new StackUtil(var2.item)).checkAvailableSlot((IInventory)var3, false, var1.orientation.reverse());
      }
   }

   public void updateEntity() {
      this.moveSolids();
   }

   public void scheduleRemoval(EntityPassiveItem var1) {
      if(!this.toRemove.contains(Integer.valueOf(var1.entityId))) {
         this.toRemove.add(Integer.valueOf(var1.entityId));
      }

   }

   public void performRemoval() {
      LinkedList var1 = new LinkedList();
      Iterator var2 = this.travelingEntities.values().iterator();

      while(var2.hasNext()) {
         PipeTransportItems.EntityData var3 = (PipeTransportItems.EntityData)var2.next();
         if(this.toRemove.contains(Integer.valueOf(var3.item.entityId))) {
            var1.add(var3);
         }
      }

      this.travelingEntities.values().removeAll(var1);
      this.toRemove = new HashSet();
   }

   private void moveSolids() {
      Iterator var1 = this.entitiesToLoad.iterator();

      PipeTransportItems.EntityData var2;
      while(var1.hasNext()) {
         var2 = (PipeTransportItems.EntityData)var1.next();
         this.travelingEntities.put(new Integer(var2.item.entityId), var2);
      }

      this.entitiesToLoad.clear();
      this.performRemoval();
      var1 = this.travelingEntities.values().iterator();

      while(var1.hasNext()) {
         var2 = (PipeTransportItems.EntityData)var1.next();
         Position var3 = new Position(0.0D, 0.0D, 0.0D, var2.orientation);
         var3.moveForwards((double)var2.item.speed);
         var2.item.setPosition(var2.item.posX + var3.x, var2.item.posY + var3.y, var2.item.posZ + var3.z);
         if((!var2.toCenter || !this.middleReached(var2)) && !this.outOfBounds(var2)) {
            if(!var2.toCenter && this.endReached(var2)) {
               this.scheduleRemoval(var2.item);
               Position var8 = new Position((double)this.xCoord, (double)this.yCoord, (double)this.zCoord, var2.orientation);
               var8.moveForwards(1.0D);
               TileEntity var9 = this.worldObj.getTileEntity((int)var8.x, (int)var8.y, (int)var8.z);
               if(var9 instanceof IPipeEntry) {
                  ((IPipeEntry)var9).entityEntering(var2.item, var2.orientation);
               } else if(var9 instanceof TileGenericPipe && ((TileGenericPipe)var9).pipe.transport instanceof PipeTransportItems) {
                  TileGenericPipe var10 = (TileGenericPipe)var9;
                  ((PipeTransportItems)var10.pipe.transport).entityEntering(var2.item, var2.orientation);
               } else if(var9 instanceof IInventory) {
                  StackUtil var11 = new StackUtil(var2.item.item);
                  if(!APIProxy.isClient(this.worldObj)) {
                     if(var11.checkAvailableSlot((IInventory)var9, true, var8.orientation.reverse()) && var11.items.count == 0) {
                        var2.item.remove();
                     } else {
                        var2.item.item = var11.items;
                        EntityItem var7 = var2.item.toEntityItem(var2.orientation);
                        if(var7 != null) {
                           this.onDropped(var7);
                        }
                     }
                  }
               } else {
                  EntityItem var6 = var2.item.toEntityItem(var2.orientation);
                  if(var6 != null) {
                     this.onDropped(var6);
                  }
               }
            }
         } else {
            var2.toCenter = false;
            var2.item.setPosition((double)this.xCoord + 0.5D, (double)((float)this.yCoord + Utils.getPipeFloorOf(var2.item.item)), (double)this.zCoord + 0.5D);
            Orientations var4 = this.resolveDestination(var2);
            if(var4 == Orientations.Unknown) {
               this.scheduleRemoval(var2.item);
               EntityItem var5 = var2.item.toEntityItem(var2.orientation);
               if(var5 != null) {
                  this.onDropped(var5);
               }
            } else {
               var2.orientation = var4;
            }
         }
      }

      this.performRemoval();
   }

   public boolean middleReached(PipeTransportItems.EntityData var1) {
      float var2 = var1.item.speed * 1.01F;
      return Math.abs((double)this.xCoord + 0.5D - var1.item.posX) < (double)var2 && Math.abs((double)((float)this.yCoord + Utils.getPipeFloorOf(var1.item.item)) - var1.item.posY) < (double)var2 && Math.abs((double)this.zCoord + 0.5D - var1.item.posZ) < (double)var2;
   }

   public boolean endReached(PipeTransportItems.EntityData var1) {
      return var1.item.posX > (double)this.xCoord + 1.0D || var1.item.posX < (double)this.xCoord || var1.item.posY > (double)this.yCoord + 1.0D || var1.item.posY < (double)this.yCoord || var1.item.posZ > (double)this.zCoord + 1.0D || var1.item.posZ < (double)this.zCoord;
   }

   public boolean outOfBounds(PipeTransportItems.EntityData var1) {
      return var1.item.posX > (double)this.xCoord + 2.0D || var1.item.posX < (double)this.xCoord - 1.0D || var1.item.posY > (double)this.yCoord + 2.0D || var1.item.posY < (double)this.yCoord - 1.0D || var1.item.posZ > (double)this.zCoord + 2.0D || var1.item.posZ < (double)this.zCoord - 1.0D;
   }

   public Position getPosition() {
      return new Position((double)this.xCoord, (double)this.yCoord, (double)this.zCoord);
   }

   public void readFromNBT(NBTTagCompound var1) {
      super.readFromNBT(var1);
      NBTTagList var2 = var1.l("travelingEntities");

      for(int var3 = 0; var3 < var2.c(); ++var3) {
         try {
            NBTTagCompound var4 = (NBTTagCompound)var2.a(var3);
            EntityPassiveItem var5 = new EntityPassiveItem(APIProxy.getWorld());
            var5.readFromNBT(var4);
            var5.container = this.container;
            PipeTransportItems.EntityData var6 = new PipeTransportItems.EntityData(var5, Orientations.values()[var4.e("orientation")]);
            var6.toCenter = var4.m("toCenter");
            this.entitiesToLoad.add(var6);
         } catch (Throwable var7) {
            var7.printStackTrace();
         }
      }

   }

   public void writeToNBT(NBTTagCompound var1) {
      super.writeToNBT(var1);
      NBTTagList var2 = new NBTTagList();
      Iterator var3 = this.travelingEntities.values().iterator();

      while(var3.hasNext()) {
         PipeTransportItems.EntityData var4 = (PipeTransportItems.EntityData)var3.next();
         NBTTagCompound var5 = new NBTTagCompound();
         var2.a(var5);
         var4.item.writeToNBT(var5);
         var5.a("toCenter", var4.toCenter);
         var5.a("orientation", var4.orientation.ordinal());
      }

      var1.a("travelingEntities", var2);
   }

   public Orientations resolveDestination(PipeTransportItems.EntityData var1) {
      LinkedList var2 = this.getPossibleMovements(new Position((double)this.xCoord, (double)this.yCoord, (double)this.zCoord, var1.orientation), var1.item);
      if(var2.size() == 0) {
         return Orientations.Unknown;
      } else {
         int var3;
         if(!APIProxy.isClient(this.worldObj) && !APIProxy.isServerSide()) {
            var3 = this.worldObj.random.nextInt(var2.size());
         } else {
            var3 = Math.abs(var1.item.entityId + this.xCoord + this.yCoord + this.zCoord + var1.item.deterministicRandomization) % var2.size();
         }

         return (Orientations)var2.get(var3);
      }
   }

   public void destroy() {
      Iterator var1 = this.travelingEntities.values().iterator();

      while(var1.hasNext()) {
         PipeTransportItems.EntityData var2 = (PipeTransportItems.EntityData)var1.next();
         var2.item.toEntityItem(var2.orientation);
      }

      this.travelingEntities.clear();
   }

   protected void doWork() {}

   public void handleItemPacket(Packet230ModLoader var1) {
      if(var1.packetType == PacketIds.PipeItem.ordinal()) {
         int var2 = var1.dataInt[3];
         EntityPassiveItem var3 = EntityPassiveItem.getOrCreate(this.worldObj, var2);
         int var4 = var1.dataInt[5];
         int var5 = var1.dataInt[6];
         int var6 = var1.dataInt[7];
         var3.item = new ItemStack(var4, var5, var6);
         Orientations var7 = Orientations.values()[var1.dataInt[4]];
         var3.setPosition((double)var1.dataFloat[0], (double)var1.dataFloat[1], (double)var1.dataFloat[2]);
         var3.speed = var1.dataFloat[3];
         var3.deterministicRandomization = var1.dataInt[8];
         if(var3.container == this.container && this.travelingEntities.containsKey(Integer.valueOf(var3.entityId))) {
            ((PipeTransportItems.EntityData)this.travelingEntities.get(new Integer(var3.entityId))).orientation = var7;
         } else {
            if(var3.container != null) {
               ((PipeTransportItems)((TileGenericPipe)var3.container).pipe.transport).scheduleRemoval(var3);
            }

            this.travelingEntities.put(new Integer(var3.entityId), new PipeTransportItems.EntityData(var3, var7));
            var3.container = this.container;
         }

      }
   }

   public Packet230ModLoader createItemPacket(EntityPassiveItem var1, Orientations var2) {
      Packet230ModLoader var3 = new Packet230ModLoader();
      var1.deterministicRandomization += this.worldObj.random.nextInt(6);
      var3.modId = mod_BuildCraftTransport.instance.getId();
      var3.packetType = PacketIds.PipeItem.ordinal();
      var3.k = true;
      var3.dataInt = new int[9];
      var3.dataInt[0] = this.xCoord;
      var3.dataInt[1] = this.yCoord;
      var3.dataInt[2] = this.zCoord;
      var3.dataInt[3] = var1.entityId;
      var3.dataInt[4] = var2.ordinal();
      var3.dataInt[5] = var1.item.id;
      var3.dataInt[6] = var1.item.count;
      var3.dataInt[7] = var1.item.getData();
      var3.dataInt[8] = var1.deterministicRandomization;
      var3.dataFloat = new float[4];
      var3.dataFloat[0] = (float)var1.posX;
      var3.dataFloat[1] = (float)var1.posY;
      var3.dataFloat[2] = (float)var1.posZ;
      var3.dataFloat[3] = var1.speed;
      return var3;
   }

   public int getNumberOfItems() {
      return this.travelingEntities.size();
   }

   public void onDropped(EntityItem var1) {}

   protected void neighborChange() {}

   public boolean isPipeConnected(TileEntity var1) {
      return var1 instanceof TileGenericPipe || var1 instanceof IPipeEntry || var1 instanceof IInventory || var1 instanceof IMachine && ((IMachine)var1).manageSolids();
   }

   public boolean acceptItems() {
      return true;
   }

   public class EntityData {

      boolean toCenter = true;
      public EntityPassiveItem item;
      public Orientations orientation;


      public EntityData(EntityPassiveItem var2, Orientations var3) {
         this.item = var2;
         this.orientation = var3;
      }
   }
}
