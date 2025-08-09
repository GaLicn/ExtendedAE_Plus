package com.extendedae_plus.command;

import com.extendedae_plus.test.PatternUploadUtilTestRunner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 测试样板上传工具的游戏内命令
 * 使用方法: /extendedae_plus test_pattern_upload
 */
public class TestPatternUploadCommand {
    
    /**
     * 注册命令
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("extendedae_plus")
                .then(Commands.literal("test_pattern_upload")
                    .requires(source -> source.hasPermission(2)) // 需要OP权限
                    .executes(TestPatternUploadCommand::executeTest)
                )
                .then(Commands.literal("test_offline")
                    .requires(source -> source.hasPermission(2))
                    .executes(TestPatternUploadCommand::executeOfflineTest)
                )
        );
    }
    
    /**
     * 执行游戏内测试
     */
    private static int executeTest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            if (source.getEntity() instanceof ServerPlayer player) {
                source.sendSuccess(() -> Component.literal("开始测试 ExtendedAEPatternUploadUtil..."), true);
                
                // 在单独的线程中运行测试，避免阻塞游戏
                new Thread(() -> {
                    try {
                        PatternUploadUtilTestRunner.runInGameTest(player);
                        
                        // 测试完成后发送消息
                        player.getServer().execute(() -> {
                            source.sendSuccess(() -> Component.literal("ExtendedAEPatternUploadUtil 测试完成！查看控制台获取详细结果。"), true);
                        });
                        
                    } catch (Exception e) {
                        player.getServer().execute(() -> {
                            source.sendFailure(Component.literal("测试过程中发生异常: " + e.getMessage()));
                        });
                    }
                }).start();
                
                return 1;
            } else {
                source.sendFailure(Component.literal("此命令只能由玩家执行"));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("命令执行失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 执行离线测试
     */
    private static int executeOfflineTest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            source.sendSuccess(() -> Component.literal("开始离线测试 ExtendedAEPatternUploadUtil..."), true);
            
            // 在单独的线程中运行离线测试
            new Thread(() -> {
                try {
                    PatternUploadUtilTestRunner.runOfflineTest();
                    
                    // 测试完成后发送消息
                    source.getServer().execute(() -> {
                        source.sendSuccess(() -> Component.literal("ExtendedAEPatternUploadUtil 离线测试完成！查看控制台获取详细结果。"), true);
                    });
                    
                } catch (Exception e) {
                    source.getServer().execute(() -> {
                        source.sendFailure(Component.literal("离线测试过程中发生异常: " + e.getMessage()));
                    });
                }
            }).start();
            
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("命令执行失败: " + e.getMessage()));
            return 0;
        }
    }
}
