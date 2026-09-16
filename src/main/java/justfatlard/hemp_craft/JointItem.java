package justfatlard.hemp_craft;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A joint: rolled from a strain's flower and paper, lit by the first puff, and burning down from
 * then on, faster while it is being smoked.
 *
 * <p>Lit means damaged: a fresh joint is whole, and the first puff takes its first point, so the
 * durability bar is the joint burning down and the item's model shortens with it. It burns
 * anywhere in the inventory once lit, trailing smoke from its lit end while it is held.
 *
 * <p>A puff is eaten, as far as any client can tell: the joint carries an always-edible food of no
 * nutrition and a long eating time, because food is what a client's stand-in item learns from
 * Pandorical, and eating is the animation a puff should have. The server ends each puff before the
 * client's own eating sounds and crumbs would start, so nothing is ever eaten.
 *
 * <p>The smoke leaves from the lit end wherever each viewer sees it: the smoker sees the joint in
 * first person, so theirs is placed there, in front of their eyes; everyone else sees it in the
 * hand of the smoker's model.
 */
public class JointItem extends Item {

	/** Ticks one puff lasts before the smoke comes back out. */
	static final int PUFF_TICKS = 40;
	/**
	 * The eating time a client is told. Its eating pose rises into place over the first tenth of
	 * it, so shorter is snappier; its chewing bob starts a fifth of the way in and its eating sounds
	 * past a fifth, so it has to be over five puffs long for a puff never to reach either.
	 */
	static final float EAT_SECONDS = 12.0F;
	/** Ticks into a puff before the eating pose has the joint at the lips. */
	private static final int PUFF_SETTLED_TICKS = 16;
	private static final int IDLE_BURN_TICKS = 20;
	private static final int PUFF_BURN_TICKS = 8;
	/** Ticks of luck a whole puff adds, and the most it can stack to. */
	private static final int LUCK_PER_PUFF = 30 * 20;
	private static final int LUCK_MOST = 5 * 60 * 20;
	/** Ticks of hunger a whole puff adds, and the most it can stack to: the munchies. */
	private static final int HUNGER_PER_PUFF = 10 * 20;
	private static final int HUNGER_MOST = 60 * 20;

	/**
	 * Where the joint is, for a right hand, in the smoker's own view: right, up, and toward the eye,
	 * in blocks, so ahead is negative. Held, as vanilla's handheld items sit; puffing, as the joints'
	 * puff models lay it at the lips.
	 */
	private static final Hold HELD = new Hold(new Vec3(0.63, -0.58, -0.74), new Vec3(0.63, -0.21, -0.61));
	private static final Hold PUFF = new Hold(new Vec3(0.03, -0.24, -0.38), new Vec3(0.21, -0.20, -0.64));
	/** Where the joint is in a right hand of the player model: right, up from the feet, and ahead. */
	private static final Hold MODEL = new Hold(new Vec3(0.35, 0.82, 0.19), new Vec3(0.35, 0.89, 0.65));

	/**
	 * The smoke the smoker sees off their own joint. Smoke's own particle this close to the eye draws
	 * as a few big blotches; a pale dust rises off the tip as a finer thread. Dust picks its own slow
	 * speed, so what it is sent is only a direction.
	 */
	private static final DustParticleOptions WISP = new DustParticleOptions(0xDCDCDC, 1.0F);

	public JointItem(Properties properties) {
		super(properties);
	}

	static boolean isLit(ItemStack stack) {
		return stack.getDamageValue() > 0;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server && !isLit(stack)) {
			stack.setDamageValue(1);
			server.playSound(null, player.blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.6F, 1.2F);
		}
		return super.use(level, player, hand);
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (!(level instanceof ServerLevel server)) return;
		int used = getUseDuration(stack, entity) - remaining;
		if (used == 1) {
			server.playSound(null, entity.blockPosition(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.25F, 1.6F);
		}
		if (used >= PUFF_SETTLED_TICKS && used % 2 == 0) {
			boolean rightHand = (entity.getUsedItemHand() == InteractionHand.MAIN_HAND) == (entity.getMainArm() == HumanoidArm.RIGHT);
			smoke(server, entity, stack, rightHand, PUFF, used % 4 == 0, 0.025);
		}
		if (used % PUFF_BURN_TICKS == 0 && burn(server, entity, stack)) return;
		if (used >= PUFF_TICKS) {
			exhale(server, entity, 1.0F);
			dose(entity, 1.0F);
			entity.stopUsingItem();
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
		if (level instanceof ServerLevel server) {
			int used = getUseDuration(stack, entity) - remaining;
			if (used > 10) {
				exhale(server, entity, used / (float) PUFF_TICKS);
				dose(entity, used / (float) PUFF_TICKS);
			}
		}
		return true;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (!isLit(stack)) return;
		long time = level.getGameTime();
		if (time % IDLE_BURN_TICKS == 0 && burn(level, entity, stack)) return;
		boolean held = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
		if (held && entity instanceof LivingEntity living && time % 4 == 0 && living.getUseItem() != stack) {
			boolean rightHand = (slot == EquipmentSlot.MAINHAND) == (living.getMainArm() == HumanoidArm.RIGHT);
			smoke(level, living, stack, rightHand, HELD, time % 8 == 0, 0.035);
		}
	}

	/** Burn one point off; a joint burnt to nothing is gone. Returns whether it went out. */
	private static boolean burn(ServerLevel level, Entity entity, ItemStack stack) {
		int damage = stack.getDamageValue() + 1;
		if (damage < stack.getMaxDamage()) {
			stack.setDamageValue(damage);
			return false;
		}
		stack.setCount(0);
		level.playSound(null, entity.blockPosition(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.0F);
		if (entity instanceof LivingEntity living && living.isUsingItem()) living.stopUsingItem();
		return true;
	}

	/**
	 * A wisp rising off the lit end, sent to each player where they see that end: to the smoker
	 * every time, as a thread of dust, and to everyone else as smoke when {@code others} says.
	 */
	private static void smoke(ServerLevel level, LivingEntity holder, ItemStack stack, boolean rightHand, Hold ownView,
			boolean others, double rise) {
		float left = left(stack);
		for (ServerPlayer viewer : level.players()) {
			if (viewer == holder) {
				Vec3 at = inView(holder, ownView.tip(left, rightHand));
				level.sendParticles(viewer, WISP, false, false, at.x, at.y, at.z, 0, 0.0, 1.0, 0.0, 1.0);
			} else if (others) {
				Vec3 at = onModel(holder, MODEL.tip(left, rightHand));
				level.sendParticles(viewer, ParticleTypes.WHITE_SMOKE, false, false, at.x, at.y, at.z, 0, 0.0, rise, 0.0, 1.0);
			}
		}
	}

	/**
	 * How much of a whole joint's length is left in the look its item model shows, crutch to lit
	 * end: the damage shares here are the item model's thresholds for its lit, half and stub looks.
	 */
	private static float left(ItemStack stack) {
		float burnt = stack.getDamageValue() / (float) stack.getMaxDamage();
		return burnt < 0.5F ? 1.0F : burnt < 0.8F ? 0.68F : 0.36F;
	}

	/**
	 * What a puff does to the smoker: luck, and the munchies. Each puff adds to what is left of
	 * both, a cut-short one in proportion, up to a cap, so smoking the whole joint stacks them.
	 */
	private static void dose(LivingEntity entity, float strength) {
		float share = Math.min(1.0F, strength);
		prolong(entity, MobEffects.LUCK, Math.round(LUCK_PER_PUFF * share), LUCK_MOST);
		prolong(entity, MobEffects.HUNGER, Math.round(HUNGER_PER_PUFF * share), HUNGER_MOST);
	}

	private static void prolong(LivingEntity entity, Holder<MobEffect> effect, int ticks, int most) {
		MobEffectInstance now = entity.getEffect(effect);
		int left = now == null ? 0 : now.getDuration();
		entity.addEffect(new MobEffectInstance(effect, Math.min(most, left + ticks)));
	}

	/** The smoke coming back out: a drifting stream from the mouth along the smoker's look. */
	private static void exhale(ServerLevel level, LivingEntity entity, float strength) {
		Vec3 mouth = mouth(entity);
		Vec3 look = entity.getLookAngle();
		int puffs = Math.max(3, Math.round(12 * Math.min(1.0F, strength)));
		for (int i = 0; i < puffs; i++) {
			SimpleParticleType type = i % 5 == 4 ? ParticleTypes.SMOKE : ParticleTypes.WHITE_SMOKE;
			Vec3 drift = look.scale(0.07 + 0.05 * entity.getRandom().nextFloat())
				.add((entity.getRandom().nextFloat() - 0.5) * 0.03, 0.012, (entity.getRandom().nextFloat() - 0.5) * 0.03);
			level.sendParticles(type, mouth.x, mouth.y, mouth.z, 0, drift.x, drift.y, drift.z, 1.0);
		}
		level.playSound(null, entity.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.12F, 1.9F);
	}

	/**
	 * Just out in front of the lips. Particles this close to the eye draw large, so the smoke
	 * starts half a block out: in first person it drifts off from the edge of the view, not over
	 * it, and from outside it still comes from the face.
	 */
	private static Vec3 mouth(LivingEntity entity) {
		return entity.getEyePosition().add(entity.getLookAngle().scale(0.55)).add(0.0, -0.18, 0.0);
	}

	/** A point given right, up and back from the eye, turned with the head into the world. */
	private static Vec3 inView(LivingEntity entity, Vec3 view) {
		float yaw = entity.getYRot() * Mth.DEG_TO_RAD;
		float pitch = entity.getXRot() * Mth.DEG_TO_RAD;
		Vec3 look = entity.getLookAngle();
		Vec3 up = new Vec3(-Mth.sin(yaw) * Mth.sin(pitch), Mth.cos(pitch), Mth.cos(yaw) * Mth.sin(pitch));
		Vec3 right = look.cross(up);
		return entity.getEyePosition().add(right.scale(view.x)).add(up.scale(view.y)).add(look.scale(-view.z));
	}

	/** A point given right, up and ahead from the feet, turned with the body into the world. */
	private static Vec3 onModel(LivingEntity entity, Vec3 body) {
		float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 offset = right.scale(body.x).add(0.0, body.y, 0.0).add(ahead.scale(body.z));
		return entity.position().add(offset.scale(entity.getScale()));
	}

	/** A way of holding the joint, for a right hand: where its crutch is, and a whole one's lit end. */
	private record Hold(Vec3 crutch, Vec3 lit) {
		/** The lit end of a joint with this share of its length left; a left hand mirrors it. */
		Vec3 tip(float left, boolean rightHand) {
			Vec3 at = crutch.lerp(lit, left);
			return rightHand ? at : new Vec3(-at.x, at.y, at.z);
		}
	}
}
