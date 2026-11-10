package dev.nautilus.module.crystal;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.KeySetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.DamageUtil;
import dev.nautilus.util.InvUtil;
import dev.nautilus.util.WorldUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Anchor macro: for the respawn anchor under your crosshair it switches to glowstone, charges the
 * anchor, (optionally lets SafeAnchor put a shield block in front of you) and detonates it with
 * another item – then switches back. Outside the Nether only (there anchors set your spawn).
 */
public class AnchorMacro extends Module {
    private final ModeSetting trigger = add(new ModeSetting("Auslöser", "Halten = Anker in der Hand + auf einen Block schauen: setzt, lädt und sprengt selbst in Schleife. Neu = nur gerade platzierte Anker, Immer = jeder anvisierte Anker, Taste = nur mit Makro-Taste", "Halten", "Halten", "Neu", "Immer", "Taste"));
    private final KeySetting macroKey = add(new KeySetting("Makro-Taste", "Taste für den Auslöser 'Taste'", -1).visibleWhen(() -> trigger.is("Taste")));
    private final NumberSetting charges = add(new NumberSetting("Ladungen", "Mit wie viel Glowstone geladen wird (1 reicht zum Sprengen)", 1, 1, 4, 1));
    private final NumberSetting delay = add(new NumberSetting("Verzögerung", "Ticks zwischen den Schritten (0 = so schnell wie möglich)", 1, 0, 10, 1));
    private final NumberSetting randomDelay = add(new NumberSetting("Zufall", "Zufällige Extra-Ticks pro Schritt", 0, 0, 5, 1));
    private final ModeSetting explodeWith = add(new ModeSetting("Sprengen mit", "Welches Item den Anker zündet", "Auto", "Auto", "Totem", "Slot"));
    private final NumberSetting explodeSlot = add(new NumberSetting("Spreng-Slot", "Hotbar-Slot (1-9) für 'Slot'", 1, 1, 9, 1).visibleWhen(() -> explodeWith.is("Slot")));
    private final BoolSetting explode = add(new BoolSetting("Sprengen", "Aus = nur laden", true));
    private final BoolSetting useSafe = add(new BoolSetting("SafeAnchor nutzen", "Vor dem Sprengen einen Schutzblock setzen (wenn SafeAnchor an ist)", true));
    private final NumberSetting maxSelf = add(new NumberSetting("Max. Eigenschaden", "Nicht sprengen, wenn du mehr Schaden bekommen würdest (Herzen ×2)", 10, 0, 36, 1));
    private final BoolSetting antiSuicide = add(new BoolSetting("Anti-Selbstmord", "Nie sprengen, wenn dich die Explosion töten würde", true));
    private final BoolSetting switchBack = add(new BoolSetting("Zurückwechseln", "Am Ende wieder den vorherigen Slot halten", true));

    private enum Step { IDLE, CHARGE, SHIELD, EXPLODE }

    private Step step = Step.IDLE;
    private BlockPos anchor;
    private int wait, prevSlot = -1, clicks, timeout;
    private BlockPos lastLook;
    private boolean lastWasAnchor;

    public AnchorMacro() {
        super("AnchorMacro", "Anker halten + auf einen Block schauen = setzt, lädt und sprengt Anker in Schleife. Kombinierbar mit SafeAnchor.",
                Category.CRYSTAL, Items.RESPAWN_ANCHOR);
        preset("Legit", LOW, "Menschliches Tempo mit zufälligen Pausen",
                "Verzögerung", 3, "Zufall", 2);
        preset("Normal", MEDIUM, "Schnell mit etwas Zufall",
                "Verzögerung", 1, "Zufall", 1);
        preset("Rage", HIGH, "So schnell wie möglich",
                "Verzögerung", 0, "Zufall", 0);
    }

    boolean busy() {
        return step != Step.IDLE;
    }

    @Override
    protected void onDisable() {
        finish();
    }

    private void finish() {
        if ((switchBack.isOn() || trigger.is("Halten")) && prevSlot >= 0 && mc.player != null) InvUtil.select(prevSlot);
        step = Step.IDLE;
        anchor = null;
        prevSlot = -1;
        clicks = 0;
    }

    private void pause() {
        wait = delay.getInt() + (randomDelay.getInt() > 0 ? ThreadLocalRandom.current().nextInt(randomDelay.getInt() + 1) : 0);
    }

    private BlockPos lookedAnchor() {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        return mc.level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR) ? hit.getBlockPos() : null;
    }

    /** Pre-tick: interactions go out before the movement packet, like a real click (no "post" packets). */
    @Override
    public void onPreTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        // in the Nether an anchor sets the spawn point instead of exploding
        if (mc.level.dimension() == Level.NETHER) { if (busy()) finish(); return; }

        BlockPos looked = lookedAnchor();
        boolean fresh = looked != null && !(looked.equals(lastLook) && lastWasAnchor);
        lastLook = mc.hitResult instanceof BlockHitResult h ? h.getBlockPos() : null;
        lastWasAnchor = looked != null;

        if (wait > 0) { wait--; return; }
        if (mc.gui.screen() != null) return;

        if (step == Step.IDLE && trigger.is("Halten")) {
            if (!mc.player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) return;
            if (looked == null) {
                // place the anchor where the crosshair points
                if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
                BlockPos target = WorldUtil.isReplaceable(hit.getBlockPos()) ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
                if (!WorldUtil.isReplaceable(target) || mc.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(target))) return;
                if (InvUtil.hotbar(Items.GLOWSTONE) < 0) return;
                var r = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                if (!r.consumesAction()) return;
                mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getInteractAnimation(), false);
                looked = target;
            }
            anchor = looked;
            prevSlot = InvUtil.selected(); // the anchor slot: back to it after the blast for the next round
            clicks = 0;
            timeout = 40;
            step = Step.CHARGE;
            if (mc.level.getBlockState(anchor).is(Blocks.RESPAWN_ANCHOR)) {
                // the client already shows the anchor (placement prediction): charge right away
            } else {
                pause();
                return;
            }
        }

        if (step == Step.IDLE) {
            if (looked == null) return;
            boolean go = switch (trigger.get()) {
                case "Immer" -> true;
                case "Taste" -> macroKey.isBound() && InputConstants.isKeyDown(macroKey.get());
                default -> fresh;
            };
            if (!go) return;
            anchor = looked;
            prevSlot = InvUtil.selected();
            clicks = 0;
            timeout = 40;
            step = Step.CHARGE;
        }

        if (--timeout < 0 || !mc.level.getBlockState(anchor).is(Blocks.RESPAWN_ANCHOR)
                || mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(anchor)) > 5.5) {
            finish();
            return;
        }
        BlockState state = mc.level.getBlockState(anchor);
        int charge = state.getValue(RespawnAnchorBlock.CHARGE);

        switch (step) {
            case CHARGE -> {
                int want = charges.getInt();
                if (charge >= want || clicks >= want) {
                    step = Step.SHIELD;
                    // wait for the server to confirm the charge before going on
                    if (charge == 0) { wait = 1; return; }
                } else {
                    int slot = InvUtil.hotbar(Items.GLOWSTONE);
                    if (slot < 0) { finish(); return; }
                    InvUtil.select(slot);
                    WorldUtil.interact(anchor, Direction.UP, InteractionHand.MAIN_HAND);
                    clicks++;
                    pause();
                    return;
                }
            }
            case SHIELD -> {
                if (!explode.isOn()) { finish(); return; }
                if (charge == 0) return; // charge not confirmed yet
                SafeAnchor safe = ModuleManager.get().get(SafeAnchor.class);
                if (useSafe.isOn() && safe != null && safe.isEnabled() && safe.missingShield(anchor) != null) {
                    if (safe.protect(anchor)) { pause(); return; }
                }
                step = Step.EXPLODE;
            }
            default -> {}
        }

        if (step == Step.EXPLODE) {
            if (!safeToExplode()) { finish(); return; }
            int slot = explodeSlot();
            if (slot < 0) { finish(); return; }
            InvUtil.select(slot);
            WorldUtil.interact(anchor, Direction.UP, InteractionHand.MAIN_HAND);
            finish();
            pause();
        }
    }

    private boolean safeToExplode() {
        float self = DamageUtil.explosion(mc.player, Vec3.atCenterOf(anchor), DamageUtil.ANCHOR, anchor, null);
        if (self > maxSelf.get()) return false;
        return !antiSuicide.isOn() || self < mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1f;
    }

    /** Anything that is not glowstone detonates the anchor. */
    private int explodeSlot() {
        return switch (explodeWith.get()) {
            case "Slot" -> mc.player.getInventory().getItem(explodeSlot.getInt() - 1).is(Items.GLOWSTONE) ? -1 : explodeSlot.getInt() - 1;
            case "Totem" -> {
                int t = InvUtil.hotbar(Items.TOTEM_OF_UNDYING);
                yield t >= 0 ? t : InvUtil.hotbar(this::canDetonate);
            }
            default -> {
                // prefer an empty slot or a sword, never glowstone or an anchor
                int e = InvUtil.hotbar(ItemStack::isEmpty);
                yield e >= 0 ? e : InvUtil.hotbar(this::canDetonate);
            }
        };
    }

    private boolean canDetonate(ItemStack s) {
        return !s.is(Items.GLOWSTONE) && !s.is(Items.RESPAWN_ANCHOR) && !(s.getItem() instanceof net.minecraft.world.item.BlockItem);
    }

    @Override
    public String getInfo() {
        return busy() ? step.name().toLowerCase() : trigger.get();
    }
}
