package dev.touhoulittlemaid.platform.impl;

import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.util.DataKey;

/**
 * 女仆 NPC 的 Citizens Trait：纯数据标记，只存 maidId（blueprint P1）。
 *
 * <p>放在 {@code platform/impl} 而不是 blueprint P1 表格里的 {@code maid/}：Trait 必须继承
 * Citizens 的类，而 0.4 三环纪律禁止环2 import Citizens。P0 的 spawn 链路需要它参与签名，
 * 因此与适配器同处唯一合法落点。
 *
 * <p><b>这里不驱动行为</b>：玩家型 NPC 的 {@link Trait#run()} 永远不会被调用
 * （api-facts 17.2），行为 tick 由插件自己的调度器驱动。
 */
public final class MaidTraitV2 extends Trait {

    private static final String KEY_MAID_ID = "maid";

    private String maidId;

    public MaidTraitV2() {
        super("tlm");
    }

    public String maidId() {
        return maidId;
    }

    public void maidId(String maidId) {
        this.maidId = maidId;
    }

    @Override
    public void load(DataKey key) {
        String value = key.getString(KEY_MAID_ID, "");
        this.maidId = value.isEmpty() ? null : value;
    }

    @Override
    public void save(DataKey key) {
        if (maidId != null) {
            key.setString(KEY_MAID_ID, maidId);
        }
    }
}
