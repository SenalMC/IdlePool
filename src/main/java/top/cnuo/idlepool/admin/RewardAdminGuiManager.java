package top.cnuo.idlepool.admin;

import top.cnuo.idlepool.IdlePoolPlugin;
import top.cnuo.idlepool.reward.HeldItemIdentifier;
import top.cnuo.idlepool.reward.IdentifiedItem;
import top.cnuo.idlepool.reward.ItemRewardFactory;
import top.cnuo.idlepool.reward.RewardDefinition;
import top.cnuo.idlepool.reward.RewardPlan;
import top.cnuo.idlepool.reward.RewardPlanRepository;
import top.cnuo.idlepool.reward.RewardType;
import top.cnuo.idlepool.reward.RewardTrigger;
import top.cnuo.idlepool.reward.SnapshotRepository;
import top.cnuo.idlepool.util.DurationParser;
import top.cnuo.idlepool.util.Messages;
import top.cnuo.idlepool.util.TimeFormats;
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
        Inventory inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.reward.list.title"));
        holder.attach(inventory);
        List<RewardPlan> all = plans.all();
        for (int index = 0; index < all.size() && index < CONTENT_SLOTS.length; index++) {
            RewardPlan plan = all.get(index);
            inventory.setItem(CONTENT_SLOTS[index], namedKey(Material.WRITABLE_BOOK, "admin.reward.list.entry", Map.of(
                    "id", plan.id(), "count", Integer.toString(plan.rewards().size())
            )));
        }
        inventory.setItem(45, namedKey(Material.ANVIL, "admin.reward.list.create", Map.of()));
        inventory.setItem(49, namedKey(Material.ARROW, "admin.reward.list.back", Map.of()));
        player.openInventory(inventory);
    }

    public void openPlan(Player player, RewardPlan plan) {
        RewardAdminGuiHolder holder = new RewardAdminGuiHolder(RewardAdminGuiHolder.View.PLAN_DETAIL, plan.id(), -1);
        Inventory inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.reward.plan.title", Map.of("id", plan.id())));
        holder.attach(inventory);
        for (int index = 0; index < plan.rewards().size() && index < CONTENT_SLOTS.length; index++) {
            inventory.setItem(CONTENT_SLOTS[index], rewardIcon(plan.rewards().get(index), index));
        }
        inventory.setItem(45, namedKey(Material.HOPPER, "admin.reward.plan.add-held", Map.of()));
        inventory.setItem(46, namedKey(Material.COMMAND_BLOCK, "admin.reward.plan.add-command", Map.of()));
        inventory.setItem(47, namedKey(Material.GOLD_INGOT, "admin.reward.plan.add-money", Map.of()));
        inventory.setItem(48, namedKey(Material.NAME_TAG, "admin.reward.plan.add-id", Map.of()));
        inventory.setItem(49, namedKey(Material.ARROW, "admin.reward.plan.back", Map.of()));
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
        Inventory inventory = Bukkit.createInventory(holder, 54, Messages.get("admin.reward.detail.title", Map.of("index", Integer.toString(index + 1))));
        holder.attach(inventory);
        inventory.setItem(13, rewardIcon(reward, index));
        if (reward.type() == RewardType.ITEM) {
            inventory.setItem(29, namedKey(Material.CHEST, "admin.reward.detail.amount", Map.of("value", Integer.toString(reward.itemAmount()))));
        } else if (reward.type() == RewardType.MONEY) {
            inventory.setItem(29, namedKey(Material.GOLD_INGOT, "admin.reward.detail.money", Map.of("value", Double.toString(reward.moneyAmount()))));
        } else {
            inventory.setItem(29, namedKey(Material.COMMAND_BLOCK, "admin.reward.detail.command", Map.of("value", abbreviate(reward.command(), 32))));
        }
        inventory.setItem(31, namedKey(Material.PAPER, "admin.reward.detail.chance", Map.of("value", Double.toString(reward.chance()))));
        inventory.setItem(33, namedKey(
                reward.trigger() == RewardTrigger.CYCLE ? Material.REPEATER : Material.CLOCK,
                reward.trigger() == RewardTrigger.CYCLE ? "admin.reward.detail.trigger-cycle" : "admin.reward.detail.trigger-milestone",
                Map.of("value", triggerLabel(reward))
        ));
        inventory.setItem(35, namedKey(Material.CLOCK,
                reward.trigger() == RewardTrigger.CYCLE ? "admin.reward.detail.unlock-cycle" : "admin.reward.detail.unlock-milestone",
                Map.of("value", unlockLabel(reward))));
        inventory.setItem(45, namedKey(Material.LAVA_BUCKET, "admin.reward.detail.delete", Map.of()));
        inventory.setItem(49, namedKey(Material.ARROW, "admin.reward.detail.back", Map.of()));
        player.openInventory(inventory);
    }

    private void openDeleteConfirm(Player player, String planId, int index) {
        RewardAdminGuiHolder holder = new RewardAdminGuiHolder(RewardAdminGuiHolder.View.DELETE_CONFIRM, planId, index);
        Inventory inventory = Bukkit.createInventory(holder, 27, Messages.get("admin.reward.delete.title"));
        holder.attach(inventory);
        inventory.setItem(11, namedKey(Material.LIME_DYE, "admin.reward.delete.confirm", Map.of()));
        inventory.setItem(15, namedKey(Material.RED_DYE, "admin.reward.delete.cancel", Map.of()));
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
            request(player, new PendingInput(InputKind.CREATE_PLAN, "", -1), "admin.reward.prompt.create-plan");
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
            request(player, new PendingInput(InputKind.ADD_COMMAND, planId, -1), "admin.reward.prompt.add-command");
        } else if (slot == 47) {
            request(player, new PendingInput(InputKind.ADD_MONEY, planId, -1), "admin.reward.prompt.add-money");
        } else if (slot == 48) {
            request(player, new PendingInput(InputKind.ADD_ITEM_ID, planId, -1),
                    "admin.reward.prompt.add-item-id");
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
                case EDIT_ITEM_AMOUNT -> "admin.reward.prompt.item-amount";
                case EDIT_MONEY_AMOUNT -> "admin.reward.prompt.money-amount";
                default -> "admin.reward.prompt.edit-command";
            });
        } else if (slot == 31) {
            request(player, new PendingInput(InputKind.EDIT_CHANCE, planId, index), "admin.reward.prompt.chance");
        } else if (slot == 33) {
            String trigger = reward.trigger() == RewardTrigger.CYCLE ? "session-milestone" : "cycle";
            editAndOpenReward(player, planId, index, editor.setTrigger(planId, index, trigger));
        } else if (slot == 35) {
            request(player, new PendingInput(InputKind.EDIT_UNLOCK_AFTER, planId, index),
                    reward.trigger() == RewardTrigger.CYCLE
                            ? "admin.reward.prompt.unlock-cycle"
                            : "admin.reward.prompt.unlock-milestone");
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
            Messages.send(player, "admin.reward.no-held-item");
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
        Messages.send(player, "admin.reward.identified", Map.of("provider", shownProvider));
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
                        throw new IllegalArgumentException(Messages.raw("admin.error.plan-exists"));
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
            Messages.send(player, "admin.input.invalid", Map.of("error", exception.getMessage()));
            request(player, pending, "admin.input.retry");
        }
    }

    private void request(Player player, PendingInput pending, String promptKey) {
        pendingInputs.put(player.getUniqueId(), pending);
        player.closeInventory();
        Messages.send(player, "admin.input.prompt", Map.of("prompt", Messages.raw(promptKey)));
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
        Messages.send(player, "admin.reward.saving");
        edit.whenComplete((unused, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                Messages.send(player, "admin.save-failed", Map.of("error", String.valueOf(cause.getMessage())));
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
            meta.displayName(Messages.get(switch (reward.type()) {
                case ITEM -> "admin.reward.icon.item-name";
                case MONEY -> "admin.reward.icon.money-name";
                case COMMAND -> "admin.reward.icon.command-name";
            }, Map.of(
                    "provider", reward.provider(),
                    "id", reward.itemId(),
                    "amount", Double.toString(reward.moneyAmount())
            )));
        }
        Map<String, String> placeholders = Map.of(
                "index", Integer.toString(index + 1),
                "chance", Double.toString(reward.chance()),
                "trigger", triggerLabel(reward),
                "time", unlockLabel(reward)
        );
        List<Component> lore = new java.util.ArrayList<>(Messages.list("admin.reward.icon.lore", placeholders));
        if (reward.type() == RewardType.ITEM) {
            lore.add(Messages.get("admin.reward.icon.item-amount", Map.of("amount", Integer.toString(reward.itemAmount()))));
        } else if (reward.type() == RewardType.COMMAND) {
            lore.add(Messages.get("admin.reward.icon.command", Map.of("command", abbreviate(reward.command(), 36))));
        }
        lore.addAll(Messages.list("admin.reward.icon.footer"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        icon.setAmount(1);
        return icon;
    }

    private RewardDefinition reward(String planId, int index) {
        RewardPlan plan = plans.find(planId).orElse(null);
        return plan == null || index < 0 || index >= plan.rewards().size() ? null : plan.rewards().get(index);
    }

    private static ItemStack namedKey(Material material, String key, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.get(key + ".name", placeholders));
        meta.lore(Messages.list(key + ".lore", placeholders));
        item.setItemMeta(meta);
        return item;
    }

    private static int contentIndex(int slot) {
        return slot >= 0 && slot < 45 ? slot : -1;
    }

    private static void requireId(String input) {
        if (!input.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException(Messages.raw("admin.error.invalid-id"));
        }
    }

    private static String requireCommand(String input) {
        String command = input.startsWith("/") ? input.substring(1) : input;
        if (command.isBlank() || command.length() > 512) {
            throw new IllegalArgumentException(Messages.raw("admin.error.invalid-command"));
        }
        return command;
    }

    private static int positiveInt(String input) {
        int value = Integer.parseInt(input);
        if (value <= 0 || value > 1_000_000) {
            throw new IllegalArgumentException(Messages.raw("admin.error.invalid-amount"));
        }
        return value;
    }

    private static double positiveDouble(String input) {
        double value = Double.parseDouble(input);
        if (!Double.isFinite(value) || value <= 0 || value > 1_000_000_000) {
            throw new IllegalArgumentException(Messages.raw("admin.error.invalid-money"));
        }
        return value;
    }

    private static double chance(String input) {
        double value = Double.parseDouble(input);
        if (!Double.isFinite(value) || value < 0 || value > 100) {
            throw new IllegalArgumentException(Messages.raw("admin.error.invalid-chance"));
        }
        return value;
    }

    private static String unlockAfter(String input, RewardDefinition reward) {
        if (reward == null) {
            throw new IllegalArgumentException(Messages.raw("admin.error.reward-missing"));
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("0") || normalized.equals("0s") || normalized.equals("none")) {
            if (reward.trigger() == RewardTrigger.SESSION_MILESTONE) {
                throw new IllegalArgumentException(Messages.raw("admin.error.milestone-zero"));
            }
            return "0s";
        }
        try {
            DurationParser.parse(normalized);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(Messages.raw("admin.error.invalid-duration"));
        }
        return normalized;
    }

    private static String triggerLabel(RewardDefinition reward) {
        return Messages.raw(reward.trigger() == RewardTrigger.CYCLE
                ? "admin.reward.label.cycle"
                : "admin.reward.label.milestone");
    }

    private static String unlockLabel(RewardDefinition reward) {
        return reward.unlockAfter().isZero() ? Messages.raw("admin.reward.label.immediate") : TimeFormats.clock(reward.unlockAfter().toSeconds());
    }

    private static String[] providerItem(String input) {
        int separator = input.indexOf(':');
        if (separator <= 0 || separator == input.length() - 1) {
            throw new IllegalArgumentException(Messages.raw("admin.error.provider-format"));
        }
        String provider = input.substring(0, separator).toLowerCase(Locale.ROOT);
        if (!List.of("vanilla", "itemsadder", "mythicmobs", "mmoitems", "slimefun").contains(provider)) {
            throw new IllegalArgumentException(Messages.raw("admin.error.unsupported-provider").replace("{provider}", provider));
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
