package com.spectral.net.packet.impl;

import java.util.Optional;

import com.spectral.Server;
import com.spectral.game.World;
import com.spectral.game.content.combat.CombatFactory;
import com.spectral.game.content.skill.skillable.impl.Cooking;
import com.spectral.game.content.skill.skillable.impl.Crafting;
import com.spectral.game.content.skill.skillable.impl.Firemaking;
import com.spectral.game.content.skill.skillable.impl.Fletching;
import com.spectral.game.content.skill.skillable.impl.Herblore;
import com.spectral.game.content.skill.skillable.impl.Cooking.Cookable;
import com.spectral.game.content.skill.skillable.impl.Firemaking.LightableLog;
import com.spectral.game.content.skill.skillable.impl.Prayer.AltarOffering;
import com.spectral.game.content.skill.skillable.impl.Prayer.BuriableBone;
import com.spectral.game.entity.impl.grounditem.ItemOnGround;
import com.spectral.game.entity.impl.grounditem.ItemOnGroundManager;
import com.spectral.game.entity.impl.npc.NPC;
import com.spectral.game.entity.impl.object.GameObject;
import com.spectral.game.entity.impl.object.MapObjects;
import com.spectral.game.entity.impl.player.Player;
import com.spectral.game.model.Action;
import com.spectral.game.model.Item;
import com.spectral.game.model.Position;
import com.spectral.game.model.menu.CreationMenu;
import com.spectral.game.model.menu.CreationMenu.CreationMenuAction;
import com.spectral.game.model.menu.impl.SingleItemCreationMenu;
import com.spectral.game.model.movement.WalkToAction;
import com.spectral.net.packet.Packet;
import com.spectral.net.packet.PacketConstants;
import com.spectral.net.packet.PacketListener;
import com.spectral.util.ItemIdentifiers;
import com.spectral.util.ObjectIdentifiers;


public class UseItemPacketListener extends ItemIdentifiers implements PacketListener {

	private static void itemOnItem(Player player, Packet packet) {
		int usedWithSlot = packet.readUnsignedShort();
		int itemUsedSlot = packet.readUnsignedShortA();
		if (usedWithSlot < 0 || itemUsedSlot < 0
				|| itemUsedSlot > player.getInventory().capacity()
				|| usedWithSlot > player.getInventory().capacity())
			return;
		Item used = player.getInventory().getItems()[itemUsedSlot];
		Item usedWith = player.getInventory().getItems()[usedWithSlot];
		
		player.getPacketSender().sendInterfaceRemoval();
		player.getSkillManager().stopSkillable();
		
		//Herblore
		if(Herblore.makeUnfinishedPotion(player, used.getId(), usedWith.getId())
				|| Herblore.finishPotion(player, used.getId(), usedWith.getId())
				|| Herblore.concatenate(player, used, usedWith)) {
			return;
		}
		
		//Fletching
		if(Fletching.fletchLog(player, used.getId(), usedWith.getId()) 
				|| Fletching.stringBow(player, used.getId(), usedWith.getId())
				|| Fletching.fletchAmmo(player, used.getId(), usedWith.getId())
				|| Fletching.fletchCrossbow(player, used.getId(), usedWith.getId())) {
			return;
		}
		
		//Crafting
		if(Crafting.craftGem(player, used.getId(), usedWith.getId())) {
			return;
		}
	
		//Firemaking
		if(Firemaking.init(player, used.getId(), usedWith.getId())) {
			return;
		}

		//Granite clamp on Granite maul
		if((used.getId() == GRANITE_CLAMP || usedWith.getId() == GRANITE_CLAMP)
				&& (used.getId() == GRANITE_MAUL || usedWith.getId() == GRANITE_MAUL)) {
			if(player.busy() || CombatFactory.inCombat(player)) {
				player.getPacketSender().sendMessage("You cannot do that right now.");
				return;
			}
			if(player.getInventory().contains(GRANITE_MAUL)) {
				player.getInventory().delete(GRANITE_MAUL, 1).delete(GRANITE_CLAMP, 1).add(GRANITE_MAUL_3, 1);
				player.getPacketSender().sendMessage("You attach your Granite clamp onto the maul..");
			}
			return;
		}
		
		//Blowpipe reload
		else if(used.getId() == TOXIC_BLOWPIPE || usedWith.getId() == TOXIC_BLOWPIPE) {
			int reload = used.getId() == TOXIC_BLOWPIPE ? usedWith.getId() : used.getId();
			if(reload == ZULRAHS_SCALES) {
				final int amount = player.getInventory().getAmount(12934);
				player.incrementBlowpipeScales(amount);
				player.getInventory().delete(ZULRAHS_SCALES, amount);
				player.getPacketSender().sendMessage("You now have "+player.getBlowpipeScales()+" Zulrah scales in your blowpipe.");
			} else {
				player.getPacketSender().sendMessage("You cannot load the blowpipe with that!");
			}
		}
	}

	private static void itemOnNpc(final Player player, Packet packet) {
		final int id = packet.readShortA();
		final int index = packet.readShortA();
		final int slot = packet.readLEShort();
		if(index < 0 || index > World.getNpcs().capacity()) {
			return;
		}
		if(slot < 0 || slot > player.getInventory().getItems().length) {
			return;
		}
		NPC npc = World.getNpcs().get(index);
		if(npc == null) {
			return;
		}
		if(player.getInventory().getItems()[slot].getId() != id) {
			return;
		}
		switch(id) {

		}
	}

	@SuppressWarnings("unused")
	private static void itemOnObject(Player player, Packet packet) {
		int interfaceType = packet.readShort();
		final int objectId = packet.readShort();
		final int objectY = packet.readLEShortA();
		final int itemSlot = packet.readLEShort();
		final int objectX = packet.readLEShortA();
		final int itemId = packet.readShort();

		if (itemSlot < 0 || itemSlot > player.getInventory().capacity())
			return;
		final Item item = player.getInventory().getItems()[itemSlot];
		if (item == null || item.getId() != itemId)
			return;
		final Position position = new Position(objectX, objectY, player.getPosition().getZ());
		final Optional<GameObject> object = MapObjects.get(objectId, position);
		
		//Make sure the object actually exists in the region...
		if(!object.isPresent()) {
			Server.getLogger().info("Object with id "+objectId+" does not exist!");
			return;
		}
		
		//Update facing..
		player.setPositionToFace(position);
		
		//Handle object..
		switch(object.get().getId()) {
		case ObjectIdentifiers.STOVE_4: //Edgeville Stove
		case ObjectIdentifiers.FIRE_5: //Player-made Fire
		case ObjectIdentifiers.FIRE_23: //Barb village fire
			//Handle cooking on objects..
			Optional<Cookable> cookable = Cookable.getForItem(item.getId());
			if(cookable.isPresent()) {
				CreationMenu cookMenu = new SingleItemCreationMenu(player, cookable.get().getCookedItem(), "How many would you like to cook?", new CreationMenuAction() {
					@Override
					public void execute(int item, int amount) {
						player.getSkillManager().startSkillable(new Cooking(object.get(), cookable.get(), amount));
					}
				}).open();
				player.setCreationMenu(Optional.of(cookMenu));
				return;
			}
			//Handle bonfires..
			if(object.get().getId() == ObjectIdentifiers.FIRE_5) {
				Optional<LightableLog> log = LightableLog.getForItem(item.getId());
				if(log.isPresent()) {
					CreationMenu fmMenu = new SingleItemCreationMenu(player, log.get().getLogId(), "How many would you like to burn?", new CreationMenuAction() {
						@Override
						public void execute(int item, int amount) {
							player.getSkillManager().startSkillable(new Firemaking(log.get(), object.get(), amount));
						}
					}).open();
					player.setCreationMenu(Optional.of(fmMenu));
					return;
				}
			}
			break;
		case 409: //Bone on Altar
			Optional<BuriableBone> b = BuriableBone.forId(item.getId());
			if(b.isPresent()) {
				CreationMenu menu = new SingleItemCreationMenu(player, itemId, "How many would you like to offer?", new CreationMenuAction() {
					@Override
					public void execute(int item, int amount) {
						player.getSkillManager().startSkillable(new AltarOffering(b.get(), object.get(), amount));
					}
				}).open();
				player.setCreationMenu(Optional.of(menu));
			}
			break;
		}
	}

	@SuppressWarnings("unused")
	private static void itemOnPlayer(Player player, Packet packet) {
		int interfaceId = packet.readUnsignedShortA();
		int targetIndex = packet.readUnsignedShort();
		int itemId = packet.readUnsignedShort();
		int slot = packet.readLEShort();
		if (slot < 0 || slot > player.getInventory().capacity() || targetIndex > World.getPlayers().capacity())
			return;
		Player target = World.getPlayers().get(targetIndex);
		if(target == null) {
			return;
		}
	}
	
	@SuppressWarnings("unused")
	private static void itemOnGroundItem(Player player, Packet packet) {
		int interfaceType = packet.readLEShort();
		int usedItemId = packet.readShortA();
		int groundItemId = packet.readShort();
		int y = packet.readShortA();
		int unknown = packet.readLEShortA();
		int x = packet.readShort();
		
		//Verify item..
		if(!player.getInventory().contains(usedItemId)) {
			return;
		}
		
		//Verify ground item..
		Optional<ItemOnGround> groundItem = ItemOnGroundManager.getGroundItem(Optional.of(player.getUsername()), groundItemId, new Position(x, y));
		if(!groundItem.isPresent()) {
			return;
		}
		
		player.setWalkToTask(new WalkToAction(player, groundItem.get().getPosition(), 1, new Action() {
			@Override
			public void execute() {
				//Face...
				player.setPositionToFace(groundItem.get().getPosition());
				
				//Handle used item..
				switch(usedItemId) {
				case TINDERBOX: //Lighting a fire..
					Optional<LightableLog> log = LightableLog.getForItem(groundItemId);
					if(log.isPresent()) {
						player.getSkillManager().startSkillable(new Firemaking(log.get(), groundItem.get()));
						return;
					}	
					break;
				}
			}
		}));
	}


	@Override
	public void handleMessage(Player player, Packet packet) {
		if (player.getHitpoints() <= 0)
			return;
		switch (packet.getOpcode()) {
		case PacketConstants.ITEM_ON_ITEM:
			itemOnItem(player, packet);
			break;
		case PacketConstants.ITEM_ON_OBJECT:
			itemOnObject(player, packet);
			break;
		case PacketConstants.ITEM_ON_GROUND_ITEM:
			itemOnGroundItem(player, packet);
			break;
		case PacketConstants.ITEM_ON_NPC:
			itemOnNpc(player, packet);
			break;
		case PacketConstants.ITEM_ON_PLAYER:
			itemOnPlayer(player, packet);
			break;
		}
	}
}