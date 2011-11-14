package org.getspout.spout.inventory;

import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.server.Item;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.getspout.spout.Spout;
import org.getspout.spout.block.SpoutCraftBlock;
import org.getspout.spout.player.SpoutCraftPlayer;
import org.getspout.spoutapi.SpoutManager;
import org.getspout.spoutapi.block.SpoutBlock;
import org.getspout.spoutapi.block.design.BlockDesign;
import org.getspout.spoutapi.inventory.MaterialManager;
import org.getspout.spoutapi.inventory.SpoutShapedRecipe;
import org.getspout.spoutapi.inventory.SpoutShapelessRecipe;
import org.getspout.spoutapi.material.CustomBlock;
import org.getspout.spoutapi.material.CustomItem;
import org.getspout.spoutapi.material.Material;
import org.getspout.spoutapi.material.MaterialData;
import org.getspout.spoutapi.packet.PacketCustomBlockDesign;
import org.getspout.spoutapi.packet.PacketCustomBlockOverride;
import org.getspout.spoutapi.packet.PacketCustomItem;
import org.getspout.spoutapi.packet.PacketCustomMaterial;
import org.getspout.spoutapi.packet.PacketCustomMultiBlockOverride;
import org.getspout.spoutapi.packet.PacketItemTexture;
import org.getspout.spoutapi.packet.SpoutPacket;
import org.getspout.spoutapi.player.SpoutPlayer;
import org.getspout.spoutapi.util.UniqueItemStringMap;
import org.getspout.spoutapi.util.map.TIntPairHashSet;
import org.getspout.spoutapi.util.map.TIntPairObjectHashMap;

public class SimpleMaterialManager extends AbstractBlockManager implements MaterialManager {
	private final TIntIntHashMap itemBlock = new TIntIntHashMap();
	private final TIntObjectHashMap<String> itemPlugin = new TIntObjectHashMap<String>();
	private final TIntPairObjectHashMap<String> customTextures = new TIntPairObjectHashMap<String>(100);
	private final TIntPairObjectHashMap<String> customTexturesPlugin = new TIntPairObjectHashMap<String>(100);
	private final TIntObjectHashMap<ItemStack> customDrops = new TIntObjectHashMap<ItemStack>(100);
	private final TIntPairObjectHashMap<BlockDesign> customBlockDesigns = new TIntPairObjectHashMap<BlockDesign>(100);
	private final HashMap<World, TIntPairObjectHashMap<BlockOverrides>> queuedChunkBlockOverrides = new HashMap<World, TIntPairObjectHashMap<BlockOverrides>>(10);

	public static void disableFlintStackMix() {
		try {
			Method a = Item.class.getDeclaredMethod("a", new Class[] { boolean.class });
			a.setAccessible(true);
			a.invoke(Item.byId[318], Boolean.TRUE);

		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
	}
	
	@Override
	public void reset() {
		customTextures.clear();
		super.reset();
	}

	@Override
	public void onPlayerJoin(SpoutPlayer player) {
		if (((SpoutPlayer) player).isSpoutCraftEnabled()) {
			for (TLongObjectIterator<String> it = customTextures.iterator(); it.hasNext();) {
				it.advance();
				String pluginName = (String) customTexturesPlugin.get(TIntPairHashSet.longToKey1(it.key()), (short) TIntPairHashSet.longToKey2(it.key()));
				((SpoutPlayer) player).sendPacket(new PacketItemTexture(TIntPairHashSet.longToKey1(it.key()), (short) TIntPairHashSet.longToKey2(it.key()), pluginName, it.value()));
			}
			for (Integer id : UniqueItemStringMap.getIds()) {
				Material material = MaterialData.getMaterial(318, id.shortValue());
				if(material instanceof CustomBlock) {
					((SpoutPlayer) player).sendPacket(new PacketCustomMaterial((CustomBlock) material));
				}
				else if(material instanceof CustomItem) {
					((SpoutPlayer) player).sendPacket(new PacketCustomMaterial((CustomItem) material));
				}
			}
		}
		super.onPlayerJoin(player);
	}

	@Override
	public void setItemTexture(Material item, Plugin plugin, String texture) {
		int id = item.getRawId();
		int data = item.getRawData();
		String pluginName;
		if (plugin == null) {
			pluginName = null;
		} else {
			pluginName = plugin.getDescription().getName();
		}
		customTextures.put(id, data, texture);
		if (pluginName == null) {
			customTexturesPlugin.remove(id, data);
		} else {
			customTexturesPlugin.put(id, data, pluginName);
		}
		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
			if (player instanceof SpoutCraftPlayer) {
				if (((SpoutPlayer) player).isSpoutCraftEnabled()) {
					((SpoutPlayer) player).sendPacket(new PacketItemTexture(id, (short) data, pluginName, texture));
				}
			}
		}
	}

	@Override
	public String getCustomItemTexture(Material item) {
		int id = item.getRawId();
		int data = item.getRawData();
		if (customTextures.containsKey(id, data)) {
			return (String) customTextures.get(id, data);
		}
		return null;
	}

	public String getCustomItemTexturePlugin(Material item) {
		int id = item.getRawId();
		int data = item.getRawData();
		if (customTexturesPlugin.containsKey(id, data)) {
			return (String) customTexturesPlugin.get(id, data);
		}
		return null;
	}

	@Override
	public void resetTexture(Material item) {
		int id = item.getRawId();
		int data = item.getRawData();
		if (customTextures.containsKey(id, data)) {
			customTextures.remove(id, data);
			String pluginName = (String) customTexturesPlugin.remove(id, data);
			for (Player player : Bukkit.getServer().getOnlinePlayers()) {
				if (player instanceof SpoutCraftPlayer) {
					if (((SpoutPlayer) player).isSpoutCraftEnabled()) {
						((SpoutPlayer) player).sendPacket(new PacketItemTexture(id, (short) data, pluginName, "[reset]"));
					}
				}
			}
		}
	}

	@Override
	public int registerCustomItemName(Plugin plugin, String key) {
		int id = UniqueItemStringMap.getId(key);

		itemPlugin.put(id, plugin.getDescription().getName());

		return id;
	}

	@Override
	public void setCustomItemBlock(CustomItem item, CustomBlock block) {
		int itemId = item.getCustomId();
		itemBlock.put(itemId, block.getBlockId());
		updateCustomClientData(itemId);
	}

	public int getItemBlock(int damage) {
		return itemBlock.get(damage);
	}

	public void updateCustomClientData(Player player) {
		Set<Integer> ids = UniqueItemStringMap.getIds();
		Player[] players = new Player[1];
		players[0] = player;
		for (Integer id : ids) {
			updateCustomClientData(players, id);
		}
	}

	private void updateCustomClientData(int id) {
		Player[] players = Spout.getInstance().getServer().getOnlinePlayers();
		updateCustomClientData(players, id);
	}

	private void updateCustomClientData(Player[] players, int id) {

		int blockId = itemBlock.get(id);

		@SuppressWarnings("unused")
		String pluginName = (String) itemPlugin.get(id);

		PacketCustomItem p = new PacketCustomItem(id, blockId);

		for (Player player : players) {
			if (player instanceof SpoutCraftPlayer) {
				SpoutCraftPlayer sp = (SpoutCraftPlayer) player;
				if (sp.isSpoutCraftEnabled()) {
					sp.sendPacket(p);
				}
			}
		}
	}

	public ItemStack getCustomItemStack(CustomBlock block, int size) {
		return getCustomItemStack(block.getBlockItem(), size);
	}

	public ItemStack getCustomItemStack(CustomItem item, int size) {
		return new ItemStack(item.getRawId(), size, (short) item.getCustomId());
	}

	@Override
	public boolean overrideBlock(Block block, CustomBlock customBlock) {
		block.setTypeId(customBlock.getBlockId());
		int blockId = customBlock.getCustomId();
		if (!(block instanceof SpoutCraftBlock)) {
			return false;
		}

		SpoutCraftBlock scb = (SpoutCraftBlock) block;
		
		customBlock.onBlockPlace(scb.getWorld(), scb.getX(), scb.getY(), scb.getZ());

		scb.setCustomBlockId(blockId);
		queueBlockOverrides(scb, blockId);

		return true;
	}

	@Override
	public boolean removeBlockOverride(Block block) {
		if (!(block instanceof SpoutCraftBlock)) {
			return false;
		}

		SpoutCraftBlock scb = (SpoutCraftBlock) block;
		
		if (scb.isCustomBlock()) {
			scb.getCustomBlock().onBlockDestroyed(scb.getWorld(), scb.getX(), scb.getY(), scb.getZ());
		}

		scb.removeCustomBlockData();

		queueBlockOverrides(scb, null);
		return true;
	}

	@Override
	public boolean overrideBlock(World world, int x, int y, int z, CustomBlock customBlock) {
		int blockId = customBlock.getCustomId();

		SpoutManager.getChunkDataManager().setBlockData(blockIdString, world, x, y, z, blockId);

		queueBlockOverrides(world, x, y, z, blockId);

		return true;
	}

	public void queueBlockOverrides(SpoutCraftBlock block, Integer blockId) {
		if (block != null) {
			queueBlockOverrides(block.getWorld(), block.getX(), block.getY(), block.getZ(), blockId);
		}
	}
	
	public void queueBlockOverrides(World world, int x, int y, int z, Integer blockId) {
		if (world != null) {
			TIntPairObjectHashMap<BlockOverrides> chunkOverrides = queuedChunkBlockOverrides.get(world);
			if (chunkOverrides == null) {
				chunkOverrides = new TIntPairObjectHashMap<BlockOverrides>(100);
				queuedChunkBlockOverrides.put(world, chunkOverrides);
			}
			BlockOverrides overrides = chunkOverrides.get(x >> 4, z >> 4);
			if (overrides == null) {
				overrides = new BlockOverrides(world);
				chunkOverrides.put(x >> 4, z >> 4, overrides);
			}
			overrides.putOverride(x, y, z, blockId != null ? blockId.intValue() : -1);
		}
	}

	@Override
	public void setCustomBlockDesign(Material material, BlockDesign design) {
		int blockId = material.getRawId();
		int metaData = material.getRawData();
		if(material instanceof CustomBlock) {
			blockId = ((CustomBlock) material).getCustomId();
			metaData = 0;
		}
		Player[] players = Spout.getInstance().getServer().getOnlinePlayers();

		if (design != null) {
			customBlockDesigns.put(blockId, metaData, design);
		} else {
			customBlockDesigns.remove(blockId, metaData);
		}

		updateCustomBlockDesigns(players, blockId, metaData, design);

	}

	public void updateAllCustomBlockDesigns(Player player) {
		Player[] players = new Player[1];
		players[0] = player;
		updateAllCustomBlockDesigns(players);
	}

	public void updateAllCustomBlockDesigns(Player[] players) {
		for (TLongObjectIterator<BlockDesign> it = customBlockDesigns.iterator(); it.hasNext();) {
			it.advance();
			updateCustomBlockDesigns(players, TIntPairHashSet.longToKey1(it.key()), (short) TIntPairHashSet.longToKey2(it.key()), (BlockDesign) it.value());
		}
	}

	private void updateCustomBlockDesigns(Player[] players, int blockId, int metaData, BlockDesign design) {
		CustomBlock block = MaterialData.getCustomBlock(blockId);
		if (block == null) {
			block = MaterialData.getCustomBlock(metaData);
		}
		PacketCustomBlockDesign p = new PacketCustomBlockDesign(block.getName(), block.isOpaque(), blockId, metaData, design);

		for (Player player : players) {
			if (player instanceof SpoutCraftPlayer) {
				SpoutCraftPlayer sp = (SpoutCraftPlayer) player;
				if (sp.isSpoutCraftEnabled()) {
					sp.sendPacket(p);
				}
			}
		}
	}


	@Override
	public boolean isCustomBlock(Block block) {

		if (!(block instanceof SpoutCraftBlock)) {
			return false;
		}
		SpoutCraftBlock scb = (SpoutCraftBlock) block;

		return scb.isCustomBlock();
	}

	@Override
	public SpoutBlock getSpoutBlock(Block block) {
		if (block instanceof SpoutBlock) {
			return (SpoutBlock) block;
		} else {
			return null;
		}
	}

	@Override
	public boolean registerSpoutRecipe(Recipe recipe) {
		SpoutRecipe toAdd;
		if (recipe instanceof SpoutRecipe) {
			toAdd = (SpoutRecipe) recipe;
		} else {
			if (recipe instanceof SpoutShapedRecipe) {
				toAdd = SimpleSpoutShapedRecipe.fromSpoutRecipe((SpoutShapedRecipe) recipe);
			} else if (recipe instanceof SpoutShapelessRecipe) {
				toAdd = SimpleSpoutShapelessRecipe.fromSpoutRecipe((SpoutShapelessRecipe) recipe);
			} else {
				return false;
			}
		}
		toAdd.addToCraftingManager();
		return true;
	}

	@Override
	public boolean isCustomItem(ItemStack item) {
		if (item.getTypeId() == 318 && item.getDurability() != 0) {
			if (MaterialData.getCustomItem(item.getDurability()) != null) {
				return true;
			}
		}
		return false;
	}

	@Override
	public CustomItem getCustomItem(ItemStack item) {
		if (isCustomItem(item)) {
			return MaterialData.getCustomItem(item.getDurability());
		}
		return null;
	}

	@Override
	public CustomBlock registerItemDrop(CustomBlock block, ItemStack item) {
		if (item != null) {
			customDrops.put(block.getCustomId(), item);
		}
		else {
			customDrops.remove(block.getCustomId());
		}
		return block;
	}

	@Override
	public boolean hasItemDrop(CustomBlock block) {
		return customDrops.containsKey(block.getCustomId());
	}

	@Override
	public ItemStack getItemDrop(CustomBlock block) {
		return customDrops.get(block.getCustomId());
	}

	public void onTick() {
		for (World world : Bukkit.getServer().getWorlds()) {
			TIntPairObjectHashMap<BlockOverrides> chunkOverrides = queuedChunkBlockOverrides.get(world);
			if (chunkOverrides != null && chunkOverrides.size() > 0 && world.getPlayers().size() > 0) {
				// long time = System.nanoTime();
				for (BlockOverrides override : chunkOverrides.valueCollection()) {
					override.sendPacket();
				}
				chunkOverrides.clear();
				// System.out.println("Sending block overrides took " +
				// (System.nanoTime() - time) * 1E-6D + " ms");
			}
		}
	}
	
	private boolean glassUpdated = false;
	//Fired when MaterialData.addCustomItem or MaterialData.addCustomBlock is called
	public void onCustomMaterialRegistered(Material mat) {
		if (mat instanceof CustomBlock && !glassUpdated) {
			if (!((CustomBlock)mat).isOpaque()) {
				org.getspout.spout.block.mcblock.CustomBlock.updateGlass();
				glassUpdated = true;
			}
		}
	}

	private class BlockOverrides {
		private World world;
		private TIntArrayList xCoords = new TIntArrayList();
		private TIntArrayList yCoords = new TIntArrayList();
		private TIntArrayList zCoords = new TIntArrayList();
		private TIntArrayList typeIds = new TIntArrayList();
		BlockOverrides(World world) {
			this.world = world;
		}
		
		protected void putOverride(int x, int y, int z, int id) {
			xCoords.add(x);
			yCoords.add(y);
			zCoords.add(z);
			typeIds.add(id);
		}

		protected void sendPacket() {
			List<Player> players = world.getPlayers();
			if (xCoords.size() > 6) {
				SpoutPacket packet = new PacketCustomMultiBlockOverride(xCoords, yCoords, zCoords, typeIds);
				for (Player player : players) {
					if (player instanceof SpoutCraftPlayer) {
						SpoutCraftPlayer spc = (SpoutCraftPlayer) player;
						if (spc.isSpoutCraftEnabled()) {
							spc.sendPacket(packet);
						}
					}
				}
			} else {
				for (int i = 0; i < xCoords.size(); i++) {
					SpoutPacket packet = new PacketCustomBlockOverride(xCoords.get(i), yCoords.get(i), zCoords.get(i), typeIds.get(i));
					for (Player player : players) {
						if (player instanceof SpoutCraftPlayer) {
							SpoutCraftPlayer spc = (SpoutCraftPlayer) player;
							if (spc.isSpoutCraftEnabled()) {
								spc.sendPacket(packet);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public File getWorldDirectory(World world) {
		File worldContainer = new File(".");
		try {
			Method m = org.bukkit.craftbukkit.CraftServer.class.getDeclaredMethod("getWorldContainer", (Class<?>[])null); //Why isn't this public :(
			worldContainer = (File) m.invoke((org.bukkit.craftbukkit.CraftServer)Bukkit.getServer(), (Object[])null);
		}
		catch (Exception ignore) {}
		
		File dir = new File(worldContainer, world.getName());
		
		if (world.getEnvironment() == Environment.NETHER) {
			dir = new File(dir, "DIM-1");
		}
		
		if (dir.exists()) {
			return dir;
		} else {
			return null;
		}
	}

}
