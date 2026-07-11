package cn.guajichi.idlepool.integration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EconomyBridge {
    private final Logger logger;
    private final Object provider;
    private final Method depositMethod;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public EconomyBridge(Logger logger) {
        this.logger = logger;
        Object foundProvider = null;
        Method foundMethod = null;
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            try {
                Class economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(economyClass);
                if (registration != null) {
                    foundProvider = registration.getProvider();
                    foundMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                }
            } catch (ReflectiveOperationException exception) {
                logger.log(Level.WARNING, "Vault 已安装，但无法连接经济服务。", exception);
            }
        }
        this.provider = foundProvider;
        this.depositMethod = foundMethod;
    }

    public boolean available() {
        return provider != null && depositMethod != null;
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!available()) {
            return false;
        }
        try {
            Object response = depositMethod.invoke(provider, player, amount);
            Method successful = response.getClass().getMethod("transactionSuccess");
            return Boolean.TRUE.equals(successful.invoke(response));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
            logger.log(Level.SEVERE, "发放 Vault 货币失败。", exception);
            return false;
        }
    }
}
