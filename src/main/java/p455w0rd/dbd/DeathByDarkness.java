package p455w0rd.dbd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = DeathByDarkness.MODID, name = DeathByDarkness.NAME, version = DeathByDarkness.VERSION, acceptedMinecraftVersions = "1.12")
public class DeathByDarkness {

	public static final String NAME = "DeathByDarkness";
	public static final String MODID = "dbd";
	public static final String VERSION = "1.0.0";

	public static ModConfig config;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		ModConfig.loadConfig(new File("config/", NAME + ".cfg"));
		MinecraftForge.EVENT_BUS.register(DeathByDarkness.class);
	}

	@SubscribeEvent
	public static void onWorldTick(TickEvent.WorldTickEvent e) {
		if (e.side == Side.SERVER && e.phase == TickEvent.Phase.START) {
			ModConfig conf = ModConfig.getConfigOrDefault(e.world.provider.getDimension());
			for (Object playerObj : e.world.playerEntities) {
				EntityPlayer player = (EntityPlayer) playerObj;
				BlockPos blockpos = new BlockPos(player.posX, player.posY + player.getEyeHeight(), player.posZ);
				int light = player.world.isBlockLoaded(blockpos) ? player.world.getLightFromNeighbors(blockpos) : 0;
				if (light <= conf.deepLightLevel && conf.deepDamageMode != DamageMode.NONE) {
					if (player.ticksExisted % conf.deepCooldown != 0 || ShadowcloakEnchantment.hasDeepShadowcloak(player.inventory.armorInventory)) {
						continue;
					}
					player.hurtResistantTime = 0;
					if (!player.attackEntityFrom(ModConfig.deepDarkness, conf.getDeepDamage(light)) || !ModConfig.supressRedFlash) {
						continue;
					}
					player.hurtTime = 0;
					continue;
				}
				if (light > conf.lightLevel || player.ticksExisted % conf.cooldown != 0 || ShadowcloakEnchantment.hasShadowcloak(player.inventory.armorInventory)) {
					continue;
				}
				player.hurtResistantTime = 0;
				if (!player.attackEntityFrom(ModConfig.darkness, conf.getDamage(light)) || !ModConfig.supressRedFlash) {
					continue;
				}
				player.hurtTime = 0;
			}
		}
	}

	public static class ModConfig extends Configuration {

		public float damage;
		public float deepDamage;
		public float damageDelta;
		public float deepDamageDelta;
		public float[] damageTable;
		public float[] deepDamageTable;
		public DamageMode damageMode;
		public DamageMode deepDamageMode;
		public int lightLevel;
		public int deepLightLevel;
		public int cooldown;
		public int deepCooldown;
		public static final DamageSource darkness = new DamageSource("darkness");
		public static final DamageSource deepDarkness = new DamageSource("deepDarkness");
		public static boolean supressRedFlash;
		public static final ModConfig defaultConfig = new ModConfig();
		private static final Map<Integer, ModConfig> configs = new HashMap<Integer, ModConfig>();

		public static final String CATEGORY = "dbd";

		public ModConfig() {
			super(new File("config/", DeathByDarkness.MODID + ".cfg"));
		}

		public float getDamage(int light) {
			return damageMode.getDamage(damage, damageDelta, lightLevel, light, damageTable);
		}

		public float getDeepDamage(int light) {
			return deepDamageMode.getDamage(deepDamage, deepDamageDelta, deepLightLevel, light, deepDamageTable);
		}

		public static ModConfig getConfigOrDefault(int dimension) {
			return configs.getOrDefault(dimension, defaultConfig);
		}

		private static float[] convertDamageTable(double[] input) {
			float[] output = new float[input.length];
			for (int i = 0; i < input.length; ++i) {
				output[i] = (float) input[i];
			}
			return output;
		}

		private static double[] deconvertDamageTable(float[] input) {
			double[] output = new double[input.length];
			for (int i = 0; i < input.length; ++i) {
				output[i] = input[i];
			}
			return output;
		}

		public static void configBackwardsCompatibility(Configuration config, String catName) {
			config.renameProperty(catName, "DarknessDamage", "Damage");
			config.renameProperty(catName, "DeepDarknessDamage", "Damage");
			config.renameProperty(catName, "DeepLightLevel", "DeepLightThreshold");
			config.renameProperty(catName, "LightLevel", "LightThreshold");
		}

		public static void loadConfig(File file) {
			Configuration config = new Configuration(file);
			config.load();
			ModConfig.configBackwardsCompatibility(config, "default");
			config.setCategoryComment("default", "Default configuration" + Configuration.NEW_LINE + "Can be overriden per-dimension, by creating a category with dimension id as name and changing options in there");
			config.setCategoryPropertyOrder("default", new ArrayList<String>(Arrays.asList("LightThreshold", "Cooldown", "DamageMode", "Damage", "DamageDelta", "DamageTable", "DeepLightThreshold", "DeepCooldown", "DeepDamageMode", "DeepDamage", "DeepDamageDelta", "DeepDamageTable")));
			ModConfig.defaultConfig.damage = (float) config.get("default", "Damage", 2.0, "Amount of darkness damage to deal. Set to 0.0 with DamageMode CONSTANT to disable").getDouble();
			ModConfig.defaultConfig.damageDelta = (float) config.get("default", "DamageDelta", 2.0, "Used with DamageMode LINEARLIGHT and EXPLIGHT to modify damage based on light level, see DamageMode for details").getDouble();
			ModConfig.defaultConfig.damageTable = ModConfig.convertDamageTable(config.get("default", "DamageTable", new double[] {
					7.0,
					6.0,
					5.0,
					4.0,
					3.0,
					2.0,
					1.0
			}, "Used with DamageMode TABLELIGHT. List of damage values, each corresponding to a light level from threshold darker.\nElement to access is calculated: threshold - light, where 0 is the 1st element.\nIf element is out of range, the last (corresponding to darkest light level) element is returned. If list is empty, Damage is returned.\nDefault value has corresponding light levels (assuming default LightLevel) as values.").getDoubleList());
			ModConfig.defaultConfig.damageMode = DamageMode.valueOfOrDefault(config.get("default", "DamageMode", "CONSTANT", "Method of calculating damage:\nNONE - Damage disabled\nCONSTANT - Unmodified Damage value. (Damage)\nLINEARLIGHT - Damage increases by DamageDelta each light level darker. (Damage + DamageDelta * (Threshold - Light))\nEXPLIGHT - Damage is multiplied by DamageDelta each light level darker. (Damage * DamageDelta^(Threshold - Light))\nTABLELIGHT - Damage value is taken from DamageTable").getString(), DamageMode.CONSTANT);
			ModConfig.defaultConfig.cooldown = config.get("default", "Cooldown", 20, "Duration of cooldown (in ticks) after dealing darkness damage").setMinValue(1).getInt();
			ModConfig.defaultConfig.lightLevel = config.get("default", "LightThreshold", 7, "Light level threshold below or equal to which to deal darkness damage").getInt();
			ModConfig.defaultConfig.deepDamage = (float) config.get("default", "DeepDamage", 5.0, "Amount of deep darkness damage to deal. Set to 0.0 with DeepDamageMode CONSTANT to disable").getDouble();
			ModConfig.defaultConfig.deepDamageDelta = (float) config.get("default", "DeepDamageDelta", 2.0, "Used with DamageMode LINEARLIGHT and EXPLIGHT to modify damage based on light level, see DamageMode for details").getDouble();
			ModConfig.defaultConfig.deepDamageTable = ModConfig.convertDamageTable(config.get("default", "DeepDamageTable", new double[] {
					0.0
			}, "Used with DamageMode TABLELIGHT. List of damage values, each corresponding to a light level from threshold darker.\nElement to access is calculated: threshold - light, where 0 is the 1st element.\nIf element is out of range, the last (corresponding to darkest light level) element is returned. If list is empty, Damage is returned.\nDefault value has corresponding light levels (assuming default DeepLightLevel) as values.").getDoubleList());
			ModConfig.defaultConfig.deepDamageMode = DamageMode.valueOfOrDefault(config.get("default", "DeepDamageMode", "CONSTANT", "Method of calculating damage:\nNONE - Damage disabled\nCONSTANT - Unmodified Damage value. (Damage)\nLINEARLIGHT - Damage increases by DamageDelta each light level darker. (Damage + DamageDelta * (Threshold - Light))\nEXPLIGHT - Damage is multiplied by DamageDelta each light level darker. (Damage * DamageDelta^(Threshold - Light))\nTABLELIGHT - Damage value is taken from DamageTable").getString(), DamageMode.CONSTANT);
			ModConfig.defaultConfig.deepCooldown = config.get("default", "DeepCooldown", 20, "Duration of cooldown (in ticks) after dealing deep darkness damage").setMinValue(1).getInt();
			ModConfig.defaultConfig.deepLightLevel = config.get("default", "DeepLightThreshold", 0, "Light level threshold below or equal to which to deal deep darkness damage").getInt();
			if (ModConfig.defaultConfig.damageMode == DamageMode.TABLELIGHT && ModConfig.defaultConfig.damageTable.length == 0) {
				ModConfig.defaultConfig.damageMode = DamageMode.CONSTANT;
			}
			if (ModConfig.defaultConfig.deepDamageMode == DamageMode.TABLELIGHT && ModConfig.defaultConfig.deepDamageTable.length == 0) {
				ModConfig.defaultConfig.deepDamageMode = DamageMode.CONSTANT;
			}
			config.setCategoryComment("global", "Global configuration" + Configuration.NEW_LINE + "Can not be overriden per-dimension");
			config.setCategoryPropertyOrder("global", new ArrayList<String>(Arrays.asList("BypassArmor", "IsAbsolute", "DeepBypassArmor", "DeepIsAbsolute", "SupressRedFlash")));
			if (config.get("global", "BypassArmor", true, "Darkness damage bypasses armor protection. If false, also damages armor").getBoolean()) {
				darkness.setDamageBypassesArmor();
			}
			if (config.get("global", "IsAbsolute", true, "Darkness damage bypasses protecting potion effects and armor enchantments").getBoolean()) {
				darkness.setDamageIsAbsolute();
			}
			if (config.get("global", "DeepBypassArmor", true, "Deep darkness damage bypasses armor protection. If false, also damages armor").getBoolean()) {
				deepDarkness.setDamageBypassesArmor();
			}
			if (config.get("global", "DeepIsAbsolute", true, "Deep darkness damage bypasses protecting potion effects and armor enchantments").getBoolean()) {
				deepDarkness.setDamageIsAbsolute();
			}
			supressRedFlash = config.get("global", "SupressRedFlash", true, "Supress player turning red on darkness and deep darkness damage").getBoolean();
			config.setCategoryComment("features", "Miscellaneous features configuration");
			config.setCategoryPropertyOrder("features", new ArrayList<String>(Arrays.asList("EnableShadowcloak", "ShadowcloakID", "MultilevelShadowcloak")));
			int shadowcloakId = config.get("features", "ShadowcloakID", 231, "Shadowcloak enchantment ID. Only change this if it conflicts with another enchantment's ID").getInt();
			boolean multilevelShadowcloak = config.get("features", "MultilevelShadowcloak", true, "If enabled, Shadowcloak will have 2 levels, where Shadowcloak I will protect you from normal Darkness and Shadowcloak II will protect you from normal Darkness and Deep Darkness.\nIf disabled Shadowcloak will have 1 level which will protect you from both.").getBoolean();
			if (config.get("features", "EnableShadowcloak", true, "Enable or disable Shadowcloak armor enchantment, which protects against the Darkness").getBoolean()) {
				ShadowcloakEnchantment.self = new ShadowcloakEnchantment(multilevelShadowcloak ? 2 : 1);
				ShadowcloakEnchantment.self.setRegistryName(new ResourceLocation(MODID, "shadowcloak"));
				ForgeRegistries.ENCHANTMENTS.register(ShadowcloakEnchantment.self);
				//Enchantment.REGISTRY.register(shadowcloakId, new ResourceLocation("ftd", "shadowcloak"), ShadowcloakEnchantment.self);
			}
			if (config.hasChanged()) {
				config.save();
			}
			for (String catName : config.getCategoryNames()) {
				if (catName.equals("default") || catName.equals("global")) {
					continue;
				}
				try {
					int dimension = Integer.parseInt(catName);
					ModConfig conf = new ModConfig();
					ModConfig.configBackwardsCompatibility(config, catName);
					conf.damage = (float) config.get(catName, "Damage", ModConfig.defaultConfig.damage).getDouble();
					conf.damageDelta = (float) config.get(catName, "DamageDelta", ModConfig.defaultConfig.damageDelta).getDouble();
					conf.damageTable = ModConfig.convertDamageTable(config.get(catName, "DamageTable", ModConfig.deconvertDamageTable(ModConfig.defaultConfig.damageTable)).getDoubleList());
					conf.damageMode = DamageMode.valueOfOrDefault(config.get(catName, "DamageMode", ModConfig.defaultConfig.damageMode.toString()).getString(), ModConfig.defaultConfig.damageMode);
					conf.cooldown = config.get(catName, "Cooldown", ModConfig.defaultConfig.cooldown).setMinValue(1).getInt();
					conf.lightLevel = config.get(catName, "LightThreshold", ModConfig.defaultConfig.lightLevel).getInt();
					conf.deepDamage = (float) config.get(catName, "DeepDamage", ModConfig.defaultConfig.deepDamage).getDouble();
					conf.deepDamageDelta = (float) config.get(catName, "DeepDamageDelta", ModConfig.defaultConfig.deepDamageDelta).getDouble();
					conf.deepDamageTable = ModConfig.convertDamageTable(config.get(catName, "DeepDamageTable", ModConfig.deconvertDamageTable(ModConfig.defaultConfig.deepDamageTable)).getDoubleList());
					conf.deepDamageMode = DamageMode.valueOfOrDefault(config.get(catName, "DeepDamageMode", ModConfig.defaultConfig.deepDamageMode.toString()).getString(), ModConfig.defaultConfig.deepDamageMode);
					conf.deepCooldown = config.get(catName, "DeepCooldown", ModConfig.defaultConfig.deepCooldown).setMinValue(1).getInt();
					conf.deepLightLevel = config.get(catName, "DeepLightThreshold", ModConfig.defaultConfig.deepLightLevel).getInt();
					if (conf.damageMode == DamageMode.TABLELIGHT && conf.damageTable.length == 0) {
						conf.damageMode = DamageMode.CONSTANT;
					}
					if (conf.deepDamageMode == DamageMode.TABLELIGHT && conf.deepDamageTable.length == 0) {
						conf.deepDamageMode = DamageMode.CONSTANT;
					}
					configs.put(dimension, conf);
				}
				catch (NumberFormatException e) {
				}
			}
		}
	}

	public static class ShadowcloakEnchantment extends Enchantment {

		public static ShadowcloakEnchantment self;
		private final int maxLevel;

		protected ShadowcloakEnchantment(int maxLevel) {
			super(Enchantment.Rarity.RARE, EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[] {
					EntityEquipmentSlot.HEAD,
					EntityEquipmentSlot.CHEST,
					EntityEquipmentSlot.LEGS,
					EntityEquipmentSlot.FEET
			});
			name = "shadowcloak";
			this.maxLevel = maxLevel;
		}

		@Override
		public int getMaxLevel() {
			return maxLevel;
		}

		@Override
		public int getMinEnchantability(int level) {
			return 4 + level * 9;
		}

		@Override
		public int getMaxEnchantability(int level) {
			return 21 + level * 12;
		}

		public static boolean hasShadowcloak(NonNullList<ItemStack> armor) {
			return self != null && self.checkShadowcloak(armor);
		}

		public static boolean hasDeepShadowcloak(NonNullList<ItemStack> armor) {
			return self != null && self.checkDeepShadowcloak(armor);
		}

		private boolean checkShadowcloak(NonNullList<ItemStack> armor) {
			return EnchantmentHelper.getEnchantmentLevel(this, armor.get(0)) > 0 || EnchantmentHelper.getEnchantmentLevel(this, armor.get(1)) > 0 || EnchantmentHelper.getEnchantmentLevel(this, armor.get(2)) > 0 || EnchantmentHelper.getEnchantmentLevel(this, armor.get(3)) > 0;
		}

		private boolean checkDeepShadowcloak(NonNullList<ItemStack> armor) {
			return EnchantmentHelper.getEnchantmentLevel(this, armor.get(0)) >= maxLevel || EnchantmentHelper.getEnchantmentLevel(this, armor.get(1)) >= maxLevel || EnchantmentHelper.getEnchantmentLevel(this, armor.get(2)) >= maxLevel || EnchantmentHelper.getEnchantmentLevel(this, armor.get(3)) >= maxLevel;
		}

	}

	public static enum DamageMode {
			CONSTANT {

				@Override
				public float getDamage(float damage, float damageDelta, int threshold, int light, float[] damageTable) {
					return damage;
				}
			},
			LINEARLIGHT {

				@Override
				public float getDamage(float damage, float damageDelta, int threshold, int light, float[] damageTable) {
					return damage + damageDelta * (threshold - light);
				}
			},
			EXPLIGHT {

				@Override
				public float getDamage(float damage, float damageDelta, int threshold, int light, float[] damageTable) {
					return damage * (float) Math.pow(damageDelta, threshold - light);
				}
			},
			TABLELIGHT {

				@Override
				public float getDamage(float damage, float damageDelta, int threshold, int light, float[] damageTable) {
					return damageTable[Math.min(threshold - light, damageTable.length - 1)];
				}
			},
			NONE {

				@Override
				public float getDamage(float damage, float damageDelta, int threshold, int light, float[] damageTable) {
					return 0.0f;
				}
			};

		private DamageMode() {
		}

		public abstract float getDamage(float var1, float var2, int var3, int var4, float[] var5);

		public static DamageMode valueOfOrDefault(String value, DamageMode defaultValue) {
			try {
				return DamageMode.valueOf(value.toUpperCase());
			}
			catch (IllegalArgumentException e) {
				return defaultValue;
			}
		}

	}

}