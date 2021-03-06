package com.buuz135.industrial.tile.mob;

import com.buuz135.industrial.item.MobImprisonmentToolItem;
import com.buuz135.industrial.proxy.BlockRegistry;
import com.buuz135.industrial.proxy.FluidsRegistry;
import com.buuz135.industrial.proxy.ItemRegistry;
import com.buuz135.industrial.proxy.client.ClientProxy;
import com.buuz135.industrial.tile.CustomColoredItemHandler;
import com.buuz135.industrial.tile.WorkingAreaElectricMachine;
import com.buuz135.industrial.utils.BlockUtils;
import com.buuz135.industrial.utils.WorkUtils;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.ItemStackHandler;
import net.ndrei.teslacorelib.TeslaCoreLib;
import net.ndrei.teslacorelib.gui.BasicTeslaGuiContainer;
import net.ndrei.teslacorelib.gui.IGuiContainerPiece;
import net.ndrei.teslacorelib.gui.ToggleButtonPiece;
import net.ndrei.teslacorelib.inventory.BoundingRectangle;
import net.ndrei.teslacorelib.netsync.SimpleNBTMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MobDuplicatorTile extends WorkingAreaElectricMachine {

    private IFluidTank experienceTank;
    private ItemStackHandler mobTool;
    private boolean exactCopy;

    public MobDuplicatorTile() {
        super(MobDuplicatorTile.class.getName().hashCode());
    }

    @Override
    protected void initializeInventories() {
        super.initializeInventories();
        this.experienceTank = this.addFluidTank(FluidsRegistry.ESSENCE, 8000, EnumDyeColor.LIME, "Experience tank", new BoundingRectangle(50, 25, 18, 54));
        mobTool = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                MobDuplicatorTile.this.markDirty();
            }
        };
        this.addInventory(new CustomColoredItemHandler(mobTool, EnumDyeColor.ORANGE, "Mob imprisonment Tool", 18 * 5 + 3, 25, 1, 1) {
            @Override
            public boolean canInsertItem(int slot, ItemStack stack) {
                return stack.getItem().equals(ItemRegistry.mobImprisonmentToolItem) && ((MobImprisonmentToolItem) stack.getItem()).containsEntity(stack);
            }

            @Override
            public boolean canExtractItem(int slot) {
                return true;
            }

        });
        this.addInventoryToStorage(mobTool, "mob_replicator_tool");
        exactCopy = false;
    }

    @Override
    public AxisAlignedBB getWorkingArea() {
        return new AxisAlignedBB(this.pos.getX(), this.pos.getY(), this.pos.getZ(), this.pos.getX() + 1, this.pos.getY() + 1, this.pos.getZ() + 1).grow(getRadius(), getHeight(), getRadius());
    }

    @Override
    public float work() {
        if (WorkUtils.isDisabled(this.getBlockType())) return 0;
        if (!BlockRegistry.mobDuplicatorBlock.enableExactCopy) exactCopy = false;
        if (mobTool.getStackInSlot(0).isEmpty()) return 0;
        if (experienceTank.getFluid() == null) return 0;

        ItemStack stack = mobTool.getStackInSlot(0);
        EntityLiving entity = (EntityLiving) ((MobImprisonmentToolItem) stack.getItem()).getEntityFromStack(stack, this.world, BlockRegistry.mobDuplicatorBlock.enableExactCopy && exactCopy);
        if (BlockRegistry.mobDuplicatorBlock.blacklistedEntities.contains(EntityList.getKey(entity).toString()))
            return 0;

        int livingAround = world.getEntitiesWithinAABB(entity.getClass(), (new AxisAlignedBB((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), (double) (pos.getX() + 1), (double) (pos.getY() + 1), (double) (pos.getZ() + 1))).grow((double) 16)).size();
        if (livingAround > 32) return 0;

        int canSpawn = (int) ((experienceTank.getFluid() == null ? 0 : experienceTank.getFluid().amount) / (entity.getHealth() * 4));
        if (canSpawn == 0) return 0;

        int spawnAmount = 1 + this.world.rand.nextInt(Math.min(canSpawn, 4));
        List<BlockPos> blocks = BlockUtils.getBlockPosInAABB(getWorkingArea());
        int essenceNeeded = (int) (entity.getHealth() * BlockRegistry.mobDuplicatorBlock.essenceNeeded);
        while (spawnAmount > 0) {
            if (experienceTank.getFluid() != null && experienceTank.getFluid().amount > essenceNeeded) {
                entity = (EntityLiving) ((MobImprisonmentToolItem) stack.getItem()).getEntityFromStack(stack, this.world, BlockRegistry.mobDuplicatorBlock.enableExactCopy && exactCopy);
                int tries = 20;
                BlockPos random = blocks.get(this.world.rand.nextInt(blocks.size())).add(0.5, 0, 0.5);
                entity.setUniqueId(UUID.randomUUID());
                entity.setLocationAndAngles(random.getX(), random.getY(), random.getZ(), world.rand.nextFloat() * 360F, 0);
                while (tries > 0 && !canEntitySpawn(entity)) {
                    random = blocks.get(this.world.rand.nextInt(blocks.size()));
                    entity.setLocationAndAngles(random.getX(), random.getY(), random.getZ(), world.rand.nextFloat() * 360F, 0);
                    --tries;
                }

                if (tries <= 0) {
                    --spawnAmount;
                    continue;
                }
                entity.onInitialSpawn(world.getDifficultyForLocation(this.pos), null);

                this.world.spawnEntity(entity);

                if (entity != null) entity.spawnExplosionParticle();
                experienceTank.drain(essenceNeeded, true);
            }
            --spawnAmount;
        }

        return 1;
    }

    @Override
    public List<IGuiContainerPiece> getGuiContainerPieces(BasicTeslaGuiContainer container) {
        List<IGuiContainerPiece> pieces = super.getGuiContainerPieces(container);
        if (BlockRegistry.mobDuplicatorBlock.enableExactCopy) {
            pieces.add(new ToggleButtonPiece(120, 28, 13, 13, 0) {
                @Override
                protected void renderState(@NotNull BasicTeslaGuiContainer<?> basicTeslaGuiContainer, int i, @NotNull BoundingRectangle boundingRectangle) {

                }

                @Override
                public void drawBackgroundLayer(@NotNull BasicTeslaGuiContainer<?> container, int guiX, int guiY, float partialTicks, int mouseX, int mouseY) {
                    super.drawBackgroundLayer(container, guiX, guiY, partialTicks, mouseX, mouseY);
                    container.mc.getTextureManager().bindTexture(ClientProxy.GUI);
                    if (exactCopy) container.drawTexturedRect(this.getLeft() - 1, this.getTop() - 1, 1, 72, 16, 16);
                    else container.drawTexturedRect(this.getLeft() - 1, this.getTop() - 1, 1, 88, 16, 16);
                }

                @Override
                protected void clicked() {
                    exactCopy = !exactCopy;
                    sendStateToServer();
                }

                @Override
                public void drawForegroundTopLayer(@NotNull BasicTeslaGuiContainer<?> container, int guiX, int guiY, int mouseX, int mouseY) {
                    super.drawForegroundTopLayer(container, guiX, guiY, mouseX, mouseY);
                    if (isInside(container, mouseX, mouseY))
                        container.drawTooltip(Arrays.asList(exactCopy ? "Disable exact copy" : "Enable exact copy"), mouseX - guiX, mouseY - guiY);
                }
            });
        }
        return pieces;
    }

    public void sendStateToServer() {
        if (TeslaCoreLib.INSTANCE.isClientSide()) {
            NBTTagCompound compound = this.setupSpecialNBTMessage("EXACT_COPY");
            compound.setBoolean("EXACT", exactCopy);
            this.sendToServer(compound);
        }
    }

    @Nullable
    @Override
    protected SimpleNBTMessage processClientMessage(@Nullable String messageType, @NotNull NBTTagCompound compound) {
        super.processClientMessage(messageType, compound);
        if (messageType.equals("EXACT_COPY")) {
            exactCopy = compound.getBoolean("EXACT");
        }
        return null;
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound compound) {
        exactCopy = compound.getBoolean("ExactCopy");
        super.readFromNBT(compound);
    }

    @NotNull
    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        NBTTagCompound compound1 = super.writeToNBT(compound);
        compound1.setBoolean("ExactCopy", exactCopy);
        return compound1;
    }

    private boolean canEntitySpawn(EntityLiving living) {
        return /*getWorkingArea().contains(new Vec3d(living.getEntityBoundingBox().minX, living.getEntityBoundingBox().minY, living.getEntityBoundingBox().minZ)) &&
                getWorkingArea().contains(new Vec3d(living.getEntityBoundingBox().maxX, living.getEntityBoundingBox().maxY, living.getEntityBoundingBox().maxZ)
                &&*/ this.world.checkNoEntityCollision(living.getEntityBoundingBox()) && this.world.getCollisionBoxes(living, living.getEntityBoundingBox()).isEmpty() && (!this.world.containsAnyLiquid(living.getEntityBoundingBox()) || living.isCreatureType(EnumCreatureType.WATER_CREATURE, false));
    }
}
