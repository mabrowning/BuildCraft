package buildcraft.builders;

import buildcraft.api.API;
import buildcraft.api.APIProxy;
import buildcraft.api.IAreaProvider;
import buildcraft.api.IPowerReceptor;
import buildcraft.api.LaserKind;
import buildcraft.api.Orientations;
import buildcraft.api.PowerFramework;
import buildcraft.api.PowerProvider;
import buildcraft.api.TileNetworkData;
import buildcraft.core.BlockContents;
import buildcraft.core.BluePrint;
import buildcraft.core.BluePrintBuilder;
import buildcraft.core.Box;
import buildcraft.core.CoreProxy;
import buildcraft.core.TileBuildCraft;
import buildcraft.core.network.PacketUpdate;
import net.minecraft.server.Block;
import net.minecraft.server.BuildCraftBuilders;
import net.minecraft.server.BuildCraftCore;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.IInventory;
import net.minecraft.server.ItemBlock;
import net.minecraft.server.ItemStack;
import net.minecraft.server.NBTTagCompound;
import net.minecraft.server.NBTTagList;
import net.minecraft.server.mod_BuildCraftBuilders;

// MaeEdit start
import org.bukkit.event.block.BlockBreakEvent;
// MaeEdit end

public class TileBuilder extends TileBuildCraft implements IInventory, IPowerReceptor
{
    private ItemStack[] items = new ItemStack[28];
    private BluePrintBuilder bluePrintBuilder;
    private int currentBluePrintId = -1;
    @TileNetworkData
    public Box box = new Box();
    private PowerProvider powerProvider;

    public TileBuilder()
    {
        this.powerProvider = PowerFramework.currentFramework.createPowerProvider();
        this.powerProvider.configure(10, 25, 25, 25, 25);
        this.powerProvider.configurePowerPerdition(25, 1);
    }

    // CraftBukkit start
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = 
            new java.util.ArrayList<org.bukkit.entity.HumanEntity>();
    
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity who) {
        transaction.add(who);
    }

    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity who) {
        transaction.remove(who);
    }

    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return transaction;
    }

    public void setMaxStackSize(int size) {}

    public ItemStack[] getContents()
    {
        return items;
    }
    // CraftBukkit end

    public void initialize()
    {
        super.initialize();
        this.initalizeBluePrint();
    }

    public void initalizeBluePrint()
    {
        if (!APIProxy.isClient(this.world))
        {
            if (this.items[0] != null && this.items[0].getItem().id == BuildCraftBuilders.templateItem.id)
            {
                if (this.items[0].getData() != this.currentBluePrintId)
                {
                    this.bluePrintBuilder = null;

                    if (this.box.isInitialized())
                    {
                        this.box.deleteLasers();
                        this.box.reset();
                    }

                    BluePrint var1 = BuildCraftBuilders.bluePrints[this.items[0].getData()];

                    if (var1 == null)
                    {
                        if (APIProxy.isServerSide())
                        {
                            CoreProxy.sendToPlayers(this.getUpdatePacket(), this.x, this.y, this.z, 50, mod_BuildCraftBuilders.instance);
                        }
                    }
                    else
                    {
                        var1 = new BluePrint(var1);
                        Orientations var2 = Orientations.values()[this.world.getData(this.x, this.y, this.z)].reverse();

                        if (var2 != Orientations.XPos)
                        {
                            if (var2 == Orientations.ZPos)
                            {
                                var1.rotateLeft();
                            }
                            else if (var2 == Orientations.XNeg)
                            {
                                var1.rotateLeft();
                                var1.rotateLeft();
                            }
                            else if (var2 == Orientations.ZNeg)
                            {
                                var1.rotateLeft();
                                var1.rotateLeft();
                                var1.rotateLeft();
                            }
                        }

                        this.bluePrintBuilder = new BluePrintBuilder(var1, this.x, this.y, this.z);
                        this.box.initialize((IAreaProvider)this.bluePrintBuilder);
                        this.box.createLasers(this.world, LaserKind.Stripes);
                        this.currentBluePrintId = this.items[0].getData();

                        if (APIProxy.isServerSide())
                        {
                            this.sendNetworkUpdate();
                        }
                    }
                }
            }
            else
            {
                this.currentBluePrintId = -1;
                this.bluePrintBuilder = null;

                if (this.box.isInitialized())
                {
                    this.box.deleteLasers();
                    this.box.reset();
                }

                if (APIProxy.isServerSide())
                {
                    this.sendNetworkUpdate();
                }
            }
        }
    }

    public void doWork()
    {
        if (!APIProxy.isClient(this.world))
        {
            if (this.powerProvider.useEnergy(25, 25, true) >= 25)
            {
                this.initalizeBluePrint();

                if (this.bluePrintBuilder != null && !this.bluePrintBuilder.done)
                {
                    BlockContents var1 = this.bluePrintBuilder.findNextBlock(this.world, BluePrintBuilder.Mode.Template);

                    if (var1 == null && this.box.isInitialized())
                    {
                        this.box.deleteLasers();
                        this.box.reset();

                        if (APIProxy.isServerSide())
                        {
                            this.sendNetworkUpdate();
                        }

                        return;
                    }

                    if (!API.softBlock(var1.blockId))
                    {
                        // MaeEdit begin: Block break events for builders
                        org.bukkit.block.Block block = this.world.getWorld().getBlockAt(var1.x, var1.y, var1.z);
                        BlockBreakEvent event = new BlockBreakEvent(block, buildcraft.core.FakePlayer.getBukkitEntity(this.world));
                        this.world.getServer().getPluginManager().callEvent(event);
                        if (event.isCancelled())
                        {
                            return;
                        }
                        // MaeEdit end
                        Block.byId[var1.blockId].b(this.world, var1.x, var1.y, var1.z, this.world.getData(var1.x, var1.y, var1.z), 0);
                        this.world.setTypeId(var1.x, var1.y, var1.z, 0);
                    }
                    else
                    {
                        for (int var2 = 1; var2 < this.getSize(); ++var2)
                        {
                            if (this.getItem(var2) != null && this.getItem(var2).count > 0 && this.getItem(var2).getItem() instanceof ItemBlock)
                            {
                                ItemStack var3 = this.splitStack(var2, 1);
                                var3.getItem().interactWith(var3, (EntityHuman)buildcraft.core.FakePlayer.get(this.world), this.world, var1.x, var1.y + 1, var1.z, 0);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the number of slots in the inventory.
     */
    public int getSize()
    {
        return this.items.length;
    }

    /**
     * Returns the stack in slot i
     */
    public ItemStack getItem(int var1)
    {
        return this.items[var1];
    }

    /**
     * Decrease the size of the stack in slot (first int arg) by the amount of the second int arg. Returns the new
     * stack.
     */
    public ItemStack splitStack(int var1, int var2)
    {
        ItemStack var3;

        if (this.items[var1] == null)
        {
            var3 = null;
        }
        else if (this.items[var1].count > var2)
        {
            var3 = this.items[var1].a(var2);
        }
        else
        {
            ItemStack var4 = this.items[var1];
            this.items[var1] = null;
            var3 = var4;
        }

        if (var1 == 0)
        {
            this.initalizeBluePrint();
        }

        return var3;
    }

    /**
     * Sets the given item stack to the specified slot in the inventory (can be crafting or armor sections).
     */
    public void setItem(int var1, ItemStack var2)
    {
        this.items[var1] = var2;

        if (var1 == 0)
        {
            this.initalizeBluePrint();
        }
    }

    /**
     * Returns the name of the inventory.
     */
    public String getName()
    {
        return "Builder";
    }

    /**
     * Returns the maximum stack size for a inventory slot. Seems to always be 64, possibly will be extended. *Isn't
     * this more of a set than a get?*
     */
    public int getMaxStackSize()
    {
        return 64;
    }

    /**
     * Do not make give this method the name canInteractWith because it clashes with Container
     */
    public boolean a(EntityHuman var1)
    {
        return this.world.getTileEntity(this.x, this.y, this.z) == this;
    }

    /**
     * Reads a tile entity from NBT.
     */
    public void a(NBTTagCompound var1)
    {
        super.a(var1);
        NBTTagList var2 = var1.getList("Items");
        this.items = new ItemStack[this.getSize()];

        for (int var3 = 0; var3 < var2.size(); ++var3)
        {
            NBTTagCompound var4 = (NBTTagCompound)var2.get(var3);
            int var5 = var4.getByte("Slot") & 255;

            if (var5 >= 0 && var5 < this.items.length)
            {
                this.items[var5] = ItemStack.a(var4);
            }
        }

        if (var1.hasKey("box"))
        {
            this.box.initialize(var1.getCompound("box"));
        }
    }

    /**
     * Writes a tile entity to NBT.
     */
    public void b(NBTTagCompound var1)
    {
        super.b(var1);
        NBTTagList var2 = new NBTTagList();

        for (int var3 = 0; var3 < this.items.length; ++var3)
        {
            if (this.items[var3] != null)
            {
                NBTTagCompound var4 = new NBTTagCompound();
                var4.setByte("Slot", (byte)var3);
                this.items[var3].save(var4);
                var2.add(var4);
            }
        }

        var1.set("Items", var2);

        if (this.box.isInitialized())
        {
            NBTTagCompound var5 = new NBTTagCompound();
            this.box.writeToNBT(var5);
            var1.set("box", var5);
        }
    }

    /**
     * invalidates a tile entity
     */
    public void j()
    {
        this.destroy();
    }

    public void destroy()
    {
        if (this.box.isInitialized())
        {
            this.box.deleteLasers();
        }
    }

    public void setPowerProvider(PowerProvider var1)
    {
        this.powerProvider = var1;
    }

    public PowerProvider getPowerProvider()
    {
        return this.powerProvider;
    }

    public void handleDescriptionPacket(PacketUpdate var1)
    {
        boolean var2 = this.box.isInitialized();
        super.handleDescriptionPacket(var1);

        if (!var2 && this.box.isInitialized())
        {
            this.box.createLasers(this.world, LaserKind.Stripes);
        }
    }

    public void handleUpdatePacket(PacketUpdate var1)
    {
        boolean var2 = this.box.isInitialized();
        super.handleUpdatePacket(var1);

        if (!var2 && this.box.isInitialized())
        {
            this.box.createLasers(this.world, LaserKind.Stripes);
        }
    }

    public void f() {}

    public void g() {}

    public int powerRequest()
    {
        return this.powerProvider.maxEnergyReceived;
    }

    public ItemStack splitWithoutUpdate(int var1)
    {
        if (this.items[var1] == null)
        {
            return null;
        }
        else
        {
            ItemStack var2 = this.items[var1];
            this.items[var1] = null;
            return var2;
        }
    }
}
