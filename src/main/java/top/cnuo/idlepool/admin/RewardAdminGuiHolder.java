package top.cnuo.idlepool.admin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class RewardAdminGuiHolder implements InventoryHolder {
    public enum View {
        PLAN_LIST,
        PLAN_DETAIL,
        REWARD_DETAIL,
        DELETE_CONFIRM
    }

    private final View view;
    private final String planId;
    private final int rewardIndex;
    private Inventory inventory;

    public RewardAdminGuiHolder(View view, String planId, int rewardIndex) {
        this.view = view;
        this.planId = planId;
        this.rewardIndex = rewardIndex;
    }

    public View view() {
        return view;
    }

    public String planId() {
        return planId;
    }

    public int rewardIndex() {
        return rewardIndex;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory);
    }
}
