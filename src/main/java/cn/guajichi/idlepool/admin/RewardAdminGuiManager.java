package cn.guajichi.idlepool.admin;

import cn.guajichi.idlepool.IdlePoolPlugin;
import cn.guajichi.idlepool.reward.HeldItemIdentifier;
import cn.guajichi.idlepool.reward.IdentifiedItem;
import cn.guajichi.idlepool.reward.ItemRewardFactory;
import cn.guajichi.idlepool.reward.RewardDefinition;
import cn.guajichi.idlepool.reward.RewardPlan;
import cn.guajichi.idlepool.reward.RewardPlanRepository;
import cn.guajichi.idlepool.reward.RewardType;
import cn.guajichi.idlepool.reward.RewardTrigger;
import cn.guajichi.idlepool.reward.SnapshotRepository;
import cn.guajichi.idlepool.util.DurationParser;
import cn.guajichi.idlepool.util.Messages;
import cn.guajichi.idlepool.util.TimeFormats;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class RewardAdminGuiManager implements Listener, AutoCloseable {
    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private final IdlePoolPlugin plugin;
    private final RewardPlanRepository plans;
    private final ItemRewardFactory items;
    private final HeldItemIdentifier identifier;
    private final RewardConfigEditor editor;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public RewardAdminGuiManager(
            IdlePoolPlugin plugin,
            RewardPlanRepository plans,
            ItemRewardFactory items,
            HeldItemIdentifier identifier
    ) {
        this.plugin = plugin;
        this.plans = plans;
        this.items = items;
        this.identifier = identifier;
        this.editor = new RewardConfigEditor(plugin);
    }

    public void openList(Player player) {
        RewardAdminGuiHolder holder = new RewardAdminGuiHolder(RewardAdminGuiHolder.View.PLAN_LIST, "", -1);
        Inventory inventory = Bukkit.createInventory(holder, 54, title("<gold>奖励方案管理"));
        holder.attach(inventory);
        List<RewardPlan> all = plans.all();
        for (int index = 0; index < all.size() && index < CONTENT_SLOTS.length; index++) {
            RewardPlan plan = all.get(index);
            inventory.setItem(CONTENT_SLOTS[index], named(Material.WRITABLE_BOOK, "<yellow>" + plan.id(), List.of(
                    "<gray>奖励数量：<white>" + plan.rewards().size(),
                    "<gray>抽取模式：<white>独立概率",
                    "",
                    "<yellow>点击编辑"
            )));
        }
        inventory.setItem(45, named(Material.ANVIL, "<green>创建奖励方案", List.of("<gray>创建一个空的独立概率奖池。")));
        inventory.setItem(49, named(Material.ARROW, "<yellow>返回挂机池管理", List.of()));
        player.openInventory(inventory);
    }

    public void openPlan(Player player, RewardPlan plan) {
        RewardAdminGuiHolder holder = new RewardAdminGuiHolder(RewardAdminGuiHolder.View.PLAN_DETAIL, plan.id(), -1);
        Inventory inventory = Bukkit.createInventory(holder, 54, title("<gold>奖励方案：" + plan.id()));
        holder.attach(inventory);
        for (int index = 0; index < plan.rewards().size() && index < CONTENT_SLOTS.length; index++) {
            inventory.setItem(CONTENT_SLOTS[index], rewardIcon(plan.rewards().get(index), index));
        }
        inventory.setItem(45, named(Material.HOPPER, "<green>添加手持物品", List.of(
                "<gray>自动识别原版、IA、MMOItems、Slimefun。",
                "<gray>无法稳定识别时自动保存完整快照。"
        )));
        inventory.setItem(46, named(Material.COMMAND_BLOCK, "<green>添加命令奖励", List.of("<gray>点击后在聊天输入控制台命令。")));
        inventory.setItem(47, named(Material.GOLD_INGOT, "<green>添加 Vault 货币", List.of("<gray>点击后输入货币数量。")));
        inventory.setItem(48, named(Material.NAME_TAG, "<green>通过物品 ID 添加", List.of(
                "<gray>适合 MythicMobs 等需要指定模板 ID 的物品。",
                "<gray>格式：provider:item_id"
        )));
        inventory.setItem(49, named(Material.ARROW, "<yellow>返回方案列表", List.of()));
        player.openInventory(inventory);
    }

    public void openReward(Player player, String planId, int index) {
        RewardPlan plan = plans.find(planId).orElse(null);
        if (plan == null || index < 0 || index >= plan.rewards().size()) {
            openList(player);
            return;
        }
        RewardDefinition reward = plan.rewards().get(index);
        RewardAdminGuiHolder holder = new RewardAdminGuiHolder(RewardAdminGuiHolder.View.REWARD_DETAIL, planId, index);
        Inventory inventory = Bukkit.createInventory(holder, 54, title("<gold>编辑奖励 #" + (index + 1)));
        holder.attach(inventory);
        inventory.setItem(13, rewardIcon(reward, index));
        if (reward.type() == RewardType.ITEM) {
            inventory.setItem(29, named(Material.CHEST, "<yellow>数量：" + reward.itemAmount(), List.of("<gray>点击修改。")));
        } else if (reward.type() == RewardType.MONEY) {
            inventory.setItem(29, named(Material.GOLD_INGOT, "<yellow>金额：" + reward.moneyAmount(), List.of("<gray>点击修改。")));
        } else {
            inventory.setItem(29, named(Material.COMMAND_BLOCK, "<yellow>编辑命令", List.of(
                    "<gray>当前：<white>" + abbreviate(reward.command(), 32), "<gray>点击修改。"
            )));
        }
        inventory.setItem(31, named(Material.PAPER, "<yellow>概率：" + reward.chance() + "%", List.of("<gray>点击输入 0～100。")));
        inventory.setItem(33, named(
                reward.trigger() == RewardTrigger.CYCLE ? Material.REPEATER : Material.CLOCK,
                "<yellow>触发方式：" + triggerLabel(reward),
                reward.trigger() == RewardTrigger.CYCLE
                        ? List.of("<gray>满足解锁时间后，参与每个普通周期抽取。", "<yellow>点击切换为单次挂机里程碑。")
                        : List.of("<gray>本次连续挂机到达时间时，仅结算一次。", "<yellow>点击切换为普通周期抽取。")
        ));
        inventory.setItem(35, named(Material.CLOCK,
                "<yellow>" + (reward.trigger() == RewardTrigger.CYCLE ? "概率解锁时间：" : "里程碑时间：")
                        + unlockLabel(reward),
                List.of(
                        reward.trigger() == RewardTrigger.CYCLE
                                ? "<gray>在此之前，本奖励不会进入周期抽取。"
                                : "<gray>本次连续挂机跨过此时间时额外结算。",
                        "<yellow>点击输入，例如 30m、2h" + (reward.trigger() == RewardTrigger.CYCLE ? " 或 0" : "")
                )));
        inventory.setItem(45, named(Material.LAVA_BUCKET, "<red>删除奖励", List.of("<gray>点击后需要再次确认。")));
        inventory.setItem(49, named(Material.ARROW, "<yellow>返回奖励方案", List.of()));
        player.openInventory(inventory);
    }

    private void openDeleteConfirm(Player player, String planId, int index) {
        RewardAdminGuiHolder holder = new RewardAdminGuiHolder(RewardAdminGuiHolder.View.DELETE_CONFIRM, planId, index);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("<red>确认删除奖励？"));
        holder.attach(inventory);
        inventory.setItem(11, named(Material.LIME_DYE, "<green>确认删除", List.of("<red>此操作不可撤销。")));
        inventory.setItem(15, named(Material.RED_DYE, "<yellow>取消", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof RewardAdminGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("idlepool.admin")) {
            return;
        }
        switch (holder.view()) {
            case PLAN_LIST -> handlePlanList(player, event.getRawSlot());
            case PLAN_DETAIL -> handlePlanDetail(player, holder.planId(), event.getRawSlot());
            case REWARD_DETAIL -> handleRewardDetail(player, holder.planId(), holder.rewardIndex(), event.getRawSlot());
            case DELETE_CONFIRM -> handleDeleteConfirm(player, holder.planId(), holder.rewardIndex(), event.getRawSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof RewardAdminGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        PendingInput pending = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyInput(event.getPlayer(), pending, input));
    }

    private void handlePlanList(Player player, int slot) {
        if (slot == 45) {
            request(player, new PendingInput(InputKind.CREATE_PLAN, "", -1), "请输入新奖励方案 ID");
            return;
        }
        if (slot == 49) {
            player.performCommand("afkpool admin");
            return;
        }
        int index = contentIndex(slot);
        List<RewardPlan> all = plans.all();
        if (index >= 0 && index < all.size()) {
            openPlan(player, all.get(index));
        }
    }

    private void handlePlanDetail(Player player, String planId, int slot) {
        RewardPlan plan = plans.find(planId).orElse(null);
        if (plan == null) {
            openList(player);
            return;
        }
        if (slot == 45) {
            addHeldItem(player, planId);
        } else if (slot == 46) {
            request(player, new PendingInput(InputKind.ADD_COMMAND, planId, -1), "请输入控制台命令，不需要开头的 / ");
        } else if (slot == 47) {
            request(player, new PendingInput(InputKind.ADD_MONEY, planId, -1), "请输入 Vault 货币数量");
        } else if (slot == 48) {
            request(player, new PendingInput(InputKind.ADD_ITEM_ID, planId, -1),
                    "请输入 provider:item_id，例如 mythicmobs:SKELETON_SWORD 或 mmoitems:SWORD:CUTLASS");
        } else if (slot == 49) {
            openList(player);
        } else {
            int index = contentIndex(slot);
            if (index >= 0 && index < plan.rewards().size()) {
                openReward(player, planId, index);
            }
        }
    }

    private void handleRewardDetail(Player player, String planId, int index, int slot) {
        RewardDefinition reward = reward(planId, index);
        if (reward == null) {
            openList(player);
            return;
        }
        if (slot == 29) {
            InputKind kind = switch (reward.type()) {
                case ITEM -> InputKind.EDIT_ITEM_AMOUNT;
                case MONEY -> InputKind.EDIT_MONEY_AMOUNT;
                case COMMAND -> InputKind.EDIT_COMMAND;
            };
            request(player, new PendingInput(kind, planId, index), switch (kind) {
                case EDIT_ITEM_AMOUNT -> "请输入物品数量，必须大于 0";
                case EDIT_MONEY_AMOUNT -> "请输入货币数量，必须大于 0";
                default -> "请输入新的控制台命令";
            });
        } else if (slot == 31) {
            request(player, new PendingInput(InputKind.EDIT_CHANCE, planId, index), "请输入触发概率 0～100");
        } else if (slot == 33) {
            String trigger = reward.trigger() == RewardTrigger.CYCLE ? "session-milestone" : "cycle";
            editAndOpenReward(player, planId, index, editor.setTrigger(planId, index, trigger));
        } else if (slot == 35) {
            request(player, new PendingInput(InputKind.EDIT_UNLOCK_AFTER, planId, index),
                    reward.trigger() == RewardTrigger.CYCLE
                            ? "请输入概率解锁时间，例如 30m；输入 0 表示立即参与"
                            : "请输入单次挂机里程碑时间，例如 30m 或 2h");
        } else if (slot == 45) {
            openDeleteConfirm(player, planId, index);
        } else if (slot == 49) {
            plans.find(planId).ifPresent(plan -> openPlan(player, plan));
        }
    }

    private void handleDeleteConfirm(Player player, String planId, int index, int slot) {
        if (slot == 11) {
            editAndOpenPlan(player, planId, editor.remove(planId, index));
        } else if (slot == 15) {
            openReward(player, planId, index);
        }
    }

    private void addHeldItem(Player player, String planId) {
        ItemStack held = player.getInventory().getItemInMainHand();
        IdentifiedItem identified = identifier.identify(held).orElse(null);
        if (identified == null) {
            player.sendMessage(Messages.parse("<red>请先在主手拿着要添加的物品。"));
            return;
        }

        CompletableFuture<Void> edit;
        String shownProvider;
        if (identified.requiresSnapshot()) {
            String snapshotId = "snapshot_" + UUID.randomUUID().toString().replace("-", "");
            String encoded = SnapshotRepository.encode(identified.snapshot());
            edit = editor.addSnapshot(planId, snapshotId, encoded, identified.amount());
            shownProvider = "snapshot";
        } else {
            edit = editor.addItem(planId, identified.provider(), identified.itemId(), identified.amount());
            shownProvider = identified.provider() + ":" + identified.itemId();
        }
        player.sendMessage(Messages.parse("<gray>已识别物品来源：<white>" + shownProvider));
        editAndOpenPlan(player, planId, edit);
    }

    private void applyInput(Player player, PendingInput pending, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            if (pending.planId().isBlank()) {
                openList(player);
            } else if (pending.rewardIndex() >= 0) {
                openReward(player, pending.planId(), pending.rewardIndex());
            } else {
                plans.find(pending.planId()).ifPresent(plan -> openPlan(player, plan));
            }
            return;
        }
        try {
            CompletableFuture<Void> edit = switch (pending.kind()) {
                case CREATE_PLAN -> {
                    requireId(input);
                    if (plans.find(input).isPresent()) {
                        throw new IllegalArgumentException("奖励方案 ID 已存在");
                    }
                    yield editor.createPlan(input);
                }
                case ADD_COMMAND -> editor.addCommand(pending.planId(), requireCommand(input));
                case ADD_MONEY -> editor.addMoney(pending.planId(), positiveDouble(input));
                case ADD_ITEM_ID -> {
                    String[] item = providerItem(input);
                    yield editor.addItem(pending.planId(), item[0], item[1], 1);
                }
                case EDIT_ITEM_AMOUNT -> editor.update(pending.planId(), pending.rewardIndex(), "amount", positiveInt(input));
                case EDIT_MONEY_AMOUNT -> editor.update(pending.planId(), pending.rewardIndex(), "amount", positiveDouble(input));
                case EDIT_COMMAND -> editor.update(pending.planId(), pending.rewardIndex(), "command", requireCommand(input));
                case EDIT_CHANCE -> editor.update(pending.planId(), pending.rewardIndex(), "chance", chance(input));
                case EDIT_UNLOCK_AFTER -> editor.update(
                        pending.planId(), pending.rewardIndex(), "unlock-after",
                        unlockAfter(input, reward(pending.planId(), pending.rewardIndex()))
                );
            };
            if (pending.kind() == InputKind.CREATE_PLAN) {
                editAndOpenPlan(player, input, edit);
            } else if (pending.rewardIndex() >= 0) {
                editAndOpenReward(player, pending.planId(), pending.rewardIndex(), edit);
            } else {
                editAndOpenPlan(player, pending.planId(), edit);
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Messages.parse("<red>输入无效：</red>" + exception.getMessage()));
            request(player, pending, "请重新输入");
        }
    }

    private void request(Player player, PendingInput pending, String prompt) {
        pendingInputs.put(player.getUniqueId(), pending);
        player.closeInventory();
        player.sendMessage(Messages.parse("<gold>[挂机池]</gold> " + prompt + "；输入 <red>cancel</red> 取消。"));
    }

    private void editAndOpenPlan(Player player, String planId, CompletableFuture<Void> edit) {
        completeEdit(player, edit, () -> plans.find(planId).ifPresentOrElse(
                plan -> openPlan(player, plan),
                () -> openList(player)
        ));
    }

    private void editAndOpenReward(Player player, String planId, int index, CompletableFuture<Void> edit) {
        completeEdit(player, edit, () -> openReward(player, planId, index));
    }

    private void completeEdit(Player player, CompletableFuture<Void> edit, Runnable afterReload) {
        player.closeInventory();
        player.sendMessage(Messages.parse("<gray>正在保存奖励配置……"));
        edit.whenComplete((unused, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                player.sendMessage(Messages.parse("<red>保存失败：</red>" + cause.getMessage()));
                return;
            }
            plugin.reloadIdlePool();
            afterReload.run();
        }));
    }

    private ItemStack rewardIcon(RewardDefinition reward, int index) {
        ItemStack icon = switch (reward.type()) {
            case ITEM -> items.preview(reward.provider(), reward.itemId()).orElseGet(() -> new ItemStack(Material.BARRIER));
            case MONEY -> new ItemStack(Material.GOLD_INGOT);
            case COMMAND -> new ItemStack(Material.COMMAND_BLOCK);
        };
        ItemMeta meta = icon.getItemMeta();
        if (!meta.hasDisplayName()) {
            meta.displayName(Messages.parse(switch (reward.type()) {
                case ITEM -> "<yellow>" + reward.provider() + ":" + reward.itemId();
                case MONEY -> "<gold>Vault 货币 ×" + reward.moneyAmount();
                case COMMAND -> "<aqua>命令奖励";
            }));
        }
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(Messages.parse("<gray>序号：<white>" + (index + 1)));
        lore.add(Messages.parse("<gray>概率：<white>" + reward.chance() + "%"));
        lore.add(Messages.parse("<gray>触发：<white>" + triggerLabel(reward)));
        lore.add(Messages.parse("<gray>时间：<white>" + unlockLabel(reward)));
        if (reward.type() == RewardType.ITEM) {
            lore.add(Messages.parse("<gray>数量：<white>" + reward.itemAmount()));
        } else if (reward.type() == RewardType.COMMAND) {
            lore.add(Messages.parse("<gray>命令：<white>" + abbreviate(reward.command(), 36)));
        }
        lore.add(Messages.parse(""));
        lore.add(Messages.parse("<yellow>点击编辑"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        icon.setAmount(1);
        return icon;
    }

    private RewardDefinition reward(String planId, int index) {
        RewardPlan plan = plans.find(planId).orElse(null);
        return plan == null || index < 0 || index >= plan.rewards().size() ? null : plan.rewards().get(index);
    }

    private Component title(String text) {
        return Messages.parse(text);
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.parse(name));
        meta.lore(lore.stream().map(Messages::parse).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static int contentIndex(int slot) {
        return slot >= 0 && slot < 45 ? slot : -1;
    }

    private static void requireId(String input) {
        if (!input.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("ID 只能包含小写字母、数字、_ 和 -，最长 32 位");
        }
    }

    private static String requireCommand(String input) {
        String command = input.startsWith("/") ? input.substring(1) : input;
        if (command.isBlank() || command.length() > 512) {
            throw new IllegalArgumentException("命令不能为空且不能超过 512 个字符");
        }
        return command;
    }

    private static int positiveInt(String input) {
        int value = Integer.parseInt(input);
        if (value <= 0 || value > 1_000_000) {
            throw new IllegalArgumentException("数量必须在 1～1000000 之间");
        }
        return value;
    }

    private static double positiveDouble(String input) {
        double value = Double.parseDouble(input);
        if (!Double.isFinite(value) || value <= 0 || value > 1_000_000_000) {
            throw new IllegalArgumentException("金额必须大于 0 且不超过 10 亿");
        }
        return value;
    }

    private static double chance(String input) {
        double value = Double.parseDouble(input);
        if (!Double.isFinite(value) || value < 0 || value > 100) {
            throw new IllegalArgumentException("概率必须在 0～100 之间");
        }
        return value;
    }

    private static String unlockAfter(String input, RewardDefinition reward) {
        if (reward == null) {
            throw new IllegalArgumentException("奖励已经不存在");
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("0") || normalized.equals("0s") || normalized.equals("none")) {
            if (reward.trigger() == RewardTrigger.SESSION_MILESTONE) {
                throw new IllegalArgumentException("单次挂机里程碑时间必须大于 0");
            }
            return "0s";
        }
        DurationParser.parse(normalized);
        return normalized;
    }

    private static String triggerLabel(RewardDefinition reward) {
        return reward.trigger() == RewardTrigger.CYCLE ? "周期抽取" : "单次挂机里程碑";
    }

    private static String unlockLabel(RewardDefinition reward) {
        return reward.unlockAfter().isZero() ? "立即" : TimeFormats.clock(reward.unlockAfter().toSeconds());
    }

    private static String[] providerItem(String input) {
        int separator = input.indexOf(':');
        if (separator <= 0 || separator == input.length() - 1) {
            throw new IllegalArgumentException("格式应为 provider:item_id");
        }
        String provider = input.substring(0, separator).toLowerCase(Locale.ROOT);
        if (!List.of("vanilla", "itemsadder", "mythicmobs", "mmoitems", "slimefun").contains(provider)) {
            throw new IllegalArgumentException("不支持的物品来源：" + provider);
        }
        return new String[]{provider, input.substring(separator + 1)};
    }

    private static String abbreviate(String text, int maximum) {
        return text.length() <= maximum ? text : text.substring(0, maximum - 1) + "…";
    }

    @Override
    public void close() {
        pendingInputs.clear();
        editor.close();
    }

    private record PendingInput(InputKind kind, String planId, int rewardIndex) {
    }

    private enum InputKind {
        CREATE_PLAN,
        ADD_COMMAND,
        ADD_MONEY,
        ADD_ITEM_ID,
        EDIT_ITEM_AMOUNT,
        EDIT_MONEY_AMOUNT,
        EDIT_COMMAND,
        EDIT_CHANCE,
        EDIT_UNLOCK_AFTER
    }
}
