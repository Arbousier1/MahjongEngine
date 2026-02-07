package top.ellan.mahjongengine;

import org.bukkit.plugin.java.JavaPlugin;
import mahjongutils.models.Tile;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;  
import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;  
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;  
import net.momirealms.craftengine.core.util.Key;

public class MahjongPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("=== MahjongEngine (Paper 1.21) 正在启动 ===");
        
        // 1. 检查 Java 环境
        getLogger().info("运行环境 Java 版本: " + System.getProperty("java.version"));

        // 2. 测试麻将算法库
        try {
            Tile tile = Tile.get("1m");
            getLogger().info("麻将算法库检查通过: " + tile.toString());
        } catch (Exception e) {
            getLogger().severe("麻将算法库加载失败！");
        }

        // 3. 检查 CraftEngine 钩子 (伪代码)
        if (getServer().getPluginManager().getPlugin("CraftEngine") != null) {
            getLogger().info("已连接到 CraftEngine!");
        }
    }

    @Override
    public void onDisable() {
    }
}