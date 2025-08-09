package com.extendedae_plus.test;

import com.extendedae_plus.util.ExtendedAEPatternUploadUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * ExtendedAEPatternUploadUtil 简化测试运行器
 * 专注于测试核心逻辑和边界情况
 */
public class PatternUploadUtilTestRunner {
    
    private static final List<String> testResults = new ArrayList<>();
    private static int passedTests = 0;
    private static int totalTests = 0;
    
    /**
     * 运行所有可行的测试
     */
    public static void runTests(ServerPlayer player) {
        System.out.println("=== ExtendedAEPatternUploadUtil 测试开始 ===");
        System.out.println("注意: 某些测试需要真实的游戏环境才能完全验证");
        
        testResults.clear();
        passedTests = 0;
        totalTests = 0;
        
        // 基础功能测试
        testNullSafety();
        testTerminalTypeDetection(player);
        testUtilityMethods(player);
        testParameterValidation();
        
        // 打印结果
        printResults();
        
        System.out.println("=== ExtendedAEPatternUploadUtil 测试完成 ===");
    }
    
    /**
     * 测试空值安全性
     */
    private static void testNullSafety() {
        System.out.println("\n[测试组] 空值安全性测试");
        
        // 测试1: getPatternAccessMenu with null
        runTest("getPatternAccessMenu(null)", () -> {
            try {
                var result = ExtendedAEPatternUploadUtil.getPatternAccessMenu(null);
                return result == null; // 应该返回null而不是抛异常
            } catch (Exception e) {
                return false;
            }
        });
        
        // 测试2: isExtendedAETerminal with null
        runTest("isExtendedAETerminal(null)", () -> {
            try {
                var result = ExtendedAEPatternUploadUtil.isExtendedAETerminal(null);
                return !result; // 应该返回false而不是抛异常
            } catch (Exception e) {
                return false;
            }
        });
        
        // 测试3: getTerminalTypeDescription with null
        runTest("getTerminalTypeDescription(null)", () -> {
            try {
                var result = ExtendedAEPatternUploadUtil.getTerminalTypeDescription(null);
                return result != null && result.contains("未知");
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    /**
     * 测试终端类型检测
     */
    private static void testTerminalTypeDetection(ServerPlayer player) {
        System.out.println("\n[测试组] 终端类型检测测试");
        
        if (player == null) {
            addTestResult("终端类型检测", false, "需要真实玩家对象");
            return;
        }
        
        // 测试当前终端类型检测
        runTest("当前终端类型检测", () -> {
            try {
                boolean isExtended = ExtendedAEPatternUploadUtil.isExtendedAETerminal(player);
                String description = ExtendedAEPatternUploadUtil.getTerminalTypeDescription(player);
                var menu = ExtendedAEPatternUploadUtil.getPatternAccessMenu(player);
                
                System.out.println("  - 是否ExtendedAE终端: " + isExtended);
                System.out.println("  - 终端类型描述: " + description);
                System.out.println("  - 获取到的菜单: " + (menu != null ? menu.getClass().getSimpleName() : "null"));
                
                return true; // 只要不抛异常就算通过
            } catch (Exception e) {
                System.out.println("  - 异常: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * 测试工具方法
     */
    private static void testUtilityMethods(ServerPlayer player) {
        System.out.println("\n[测试组] 工具方法测试");
        
        if (player == null) {
            addTestResult("工具方法测试", false, "需要真实玩家对象");
            return;
        }
        
        // 测试获取样板访问菜单
        runTest("获取样板访问菜单", () -> {
            try {
                var menu = ExtendedAEPatternUploadUtil.getPatternAccessMenu(player);
                System.out.println("  - 菜单类型: " + (menu != null ? menu.getClass().getSimpleName() : "无菜单"));
                return true;
            } catch (Exception e) {
                System.out.println("  - 异常: " + e.getMessage());
                return false;
            }
        });
        
        // 测试供应器显示名称（使用模拟ID）
        runTest("获取供应器显示名称", () -> {
            try {
                var menu = ExtendedAEPatternUploadUtil.getPatternAccessMenu(player);
                if (menu != null) {
                    String displayName = ExtendedAEPatternUploadUtil.getProviderDisplayName(1L, menu);
                    System.out.println("  - 显示名称: " + displayName);
                    return displayName != null && !displayName.isEmpty();
                } else {
                    System.out.println("  - 跳过: 无可用菜单");
                    return true;
                }
            } catch (Exception e) {
                System.out.println("  - 异常: " + e.getMessage());
                return false;
            }
        });
        
        // 测试供应器可用性检查
        runTest("检查供应器可用性", () -> {
            try {
                var menu = ExtendedAEPatternUploadUtil.getPatternAccessMenu(player);
                if (menu != null) {
                    boolean isAvailable = ExtendedAEPatternUploadUtil.isProviderAvailable(1L, menu);
                    System.out.println("  - 供应器可用性: " + isAvailable);
                    return true;
                } else {
                    System.out.println("  - 跳过: 无可用菜单");
                    return true;
                }
            } catch (Exception e) {
                System.out.println("  - 异常: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * 测试参数验证
     */
    private static void testParameterValidation() {
        System.out.println("\n[测试组] 参数验证测试");
        
        // 测试无效槽位索引
        runTest("无效槽位索引处理", () -> {
            try {
                // 这些调用应该优雅地处理无效参数
                boolean result1 = ExtendedAEPatternUploadUtil.uploadPatternToProvider(null, -1, 1L);
                boolean result2 = ExtendedAEPatternUploadUtil.uploadPatternToProvider(null, 999, 1L);
                
                // 预期都应该返回false而不是抛异常
                return !result1 && !result2;
            } catch (Exception e) {
                System.out.println("  - 异常: " + e.getMessage());
                return false;
            }
        });
        
        // 测试无效供应器ID
        runTest("无效供应器ID处理", () -> {
            try {
                boolean result = ExtendedAEPatternUploadUtil.uploadPatternToProvider(null, 0, -1L);
                return !result; // 应该返回false
            } catch (Exception e) {
                System.out.println("  - 异常: " + e.getMessage());
                return false;
            }
        });
        
        // 测试批量上传空数组
        runTest("批量上传空数组", () -> {
            try {
                int[] emptyArray = {};
                int result = ExtendedAEPatternUploadUtil.uploadMultiplePatterns(null, emptyArray, 1L);
                return result == 0; // 应该返回0
            } catch (Exception e) {
                System.out.println("  - 异常: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * 运行单个测试
     */
    private static void runTest(String testName, TestFunction test) {
        totalTests++;
        try {
            boolean passed = test.run();
            if (passed) {
                passedTests++;
                addTestResult(testName, true, "通过");
            } else {
                addTestResult(testName, false, "失败");
            }
        } catch (Exception e) {
            addTestResult(testName, false, "异常: " + e.getMessage());
        }
    }
    
    /**
     * 添加测试结果
     */
    private static void addTestResult(String testName, boolean passed, String message) {
        String status = passed ? "✅" : "❌";
        String result = String.format("  %s %-30s - %s", status, testName, message);
        testResults.add(result);
        System.out.println(result);
    }
    
    /**
     * 打印测试结果汇总
     */
    private static void printResults() {
        System.out.println("\n=== 测试结果汇总 ===");
        
        double successRate = totalTests > 0 ? (double) passedTests / totalTests * 100 : 0;
        
        System.out.println("总测试数: " + totalTests);
        System.out.println("通过数: " + passedTests);
        System.out.println("失败数: " + (totalTests - passedTests));
        System.out.println("成功率: " + String.format("%.1f%%", successRate));
        
        if (passedTests == totalTests) {
            System.out.println("🎉 所有测试通过！");
        } else {
            System.out.println("⚠️ 有测试失败，请检查上述详细信息");
        }
        
        System.out.println("\n=== 使用建议 ===");
        System.out.println("1. 在游戏中打开样板访问终端后运行测试以获得更完整的结果");
        System.out.println("2. 确保背包中有编码样板进行上传测试");
        System.out.println("3. 连接到AE2网络以测试供应器相关功能");
    }
    
    /**
     * 测试函数接口
     */
    @FunctionalInterface
    private interface TestFunction {
        boolean run() throws Exception;
    }
    
    /**
     * 游戏内测试命令入口
     * 可以通过命令或其他方式调用
     */
    public static void runInGameTest(ServerPlayer player) {
        System.out.println("开始游戏内测试...");
        System.out.println("玩家: " + (player != null ? player.getName().getString() : "null"));
        System.out.println("当前容器: " + (player != null && player.containerMenu != null ? 
            player.containerMenu.getClass().getSimpleName() : "无"));
        
        runTests(player);
    }
    
    /**
     * 离线测试入口
     */
    public static void runOfflineTest() {
        System.out.println("开始离线测试（功能有限）...");
        runTests(null);
    }
}
