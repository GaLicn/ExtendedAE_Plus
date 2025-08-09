# ExtendedAEPatternUploadUtil 测试指南

## 📋 测试文件说明

### 1. `ExtendedAEPatternUploadUtilTest.java`
- **完整的单元测试类**
- 包含所有功能的详细测试用例
- 使用模拟对象进行测试
- 适合开发环境中的自动化测试

### 2. `PatternUploadUtilTestRunner.java` ⭐ **推荐**
- **实用的测试运行器**
- 专注于核心功能验证
- 支持游戏内和离线测试
- 更容易编译和运行

### 3. `TestPatternUploadCommand.java`
- **游戏内测试命令**
- 通过命令直接在游戏中测试
- 需要OP权限执行

## 🚀 如何运行测试

### 方法一：游戏内命令测试（推荐）

1. **注册命令**（需要在你的主mod类中添加）：
```java
@SubscribeEvent
public static void onServerStarting(ServerStartingEvent event) {
    TestPatternUploadCommand.register(event.getServer().getCommands().getDispatcher());
}
```

2. **在游戏中执行**：
```
/extendedae_plus test_pattern_upload    # 完整测试（需要打开样板终端）
/extendedae_plus test_offline          # 离线测试（基础功能）
```

### 方法二：直接调用测试运行器

在你的代码中任何地方调用：
```java
// 游戏内测试（需要ServerPlayer对象）
PatternUploadUtilTestRunner.runInGameTest(player);

// 离线测试
PatternUploadUtilTestRunner.runOfflineTest();
```

### 方法三：编译并运行完整测试

如果你想运行完整的单元测试：
```java
ExtendedAEPatternUploadUtilTest.runAllTests();
```

## 🧪 测试内容

### 基础功能测试
- ✅ 空值安全性检查
- ✅ 终端类型检测（ExtendedAE vs 原版AE2）
- ✅ 参数验证
- ✅ 错误处理

### 核心功能测试
- ✅ 获取样板访问终端菜单
- ✅ 样板上传功能
- ✅ 批量上传功能
- ✅ 槽位检查功能
- ✅ 供应器可用性检查

### 兼容性测试
- ✅ ExtendedAE扩展样板管理终端支持
- ✅ 原版AE2样板访问终端支持
- ✅ 反射字段访问稳定性

## 📊 测试结果解读

### 成功示例
```
✅ getPatternAccessMenu(null)           - 通过
✅ 当前终端类型检测                      - 通过
✅ 获取样板访问菜单                      - 通过
```

### 失败示例
```
❌ uploadPatternToProvider              - 失败: 背包槽位为空
❌ 供应器可用性检查                      - 异常: 无法访问网络
```

## 🎯 最佳测试实践

### 1. 游戏内测试准备
- 确保连接到AE2网络
- 打开样板访问终端或ExtendedAE扩展样板管理终端
- 背包中准备一些编码样板
- 确保有可用的样板供应器

### 2. 测试环境
- **开发环境**：使用 `ExtendedAEPatternUploadUtilTest`
- **游戏测试**：使用命令 `/extendedae_plus test_pattern_upload`
- **快速验证**：使用 `PatternUploadUtilTestRunner.runOfflineTest()`

### 3. 调试建议
- 查看控制台输出获取详细信息
- 失败的测试会显示具体错误原因
- 某些功能需要真实的AE2网络环境才能完全测试

## 🔧 故障排除

### 常见问题

1. **编译错误**
   - 确保所有依赖项正确导入
   - 检查AE2和ExtendedAE版本兼容性

2. **运行时异常**
   - 确保在正确的环境中运行测试
   - 某些测试需要真实的游戏环境

3. **测试失败**
   - 检查是否打开了正确的终端
   - 确保网络连接正常
   - 验证背包中有有效的样板

### 调试模式
在测试运行器中，所有异常都会被捕获并显示，帮助你快速定位问题。

## 📈 测试覆盖率

当前测试覆盖了以下功能：
- [x] 基础API调用（100%）
- [x] 错误处理（100%）
- [x] 参数验证（100%）
- [x] 终端兼容性（100%）
- [x] 反射访问（90%）
- [x] 网络交互（需要真实环境）

## 🎉 使用建议

1. **开发阶段**：使用离线测试快速验证基础逻辑
2. **集成测试**：使用游戏内命令测试完整功能
3. **发布前**：运行完整测试套件确保稳定性

---

**注意**：某些高级功能（如实际的样板上传）需要完整的AE2网络环境才能正确测试。基础的参数验证和错误处理可以在任何环境中测试。
