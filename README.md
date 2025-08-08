# ExtendedAE Plus

ExtendedAE Plus 是一个针对 ExtendedAE 模组的增强模组，通过 Mixin 技术将扩展样板供应器（Extended Pattern Provider）的样板槽数从原来的 36 个增加到 108 个。

## 功能特性

- **增加样板槽数**: 将 ExtendedAE 的扩展样板供应器的样板槽数从 36 个增加到 108 个
- **完全兼容**: 与 ExtendedAE 1.4.2 版本完全兼容
- **无需配置**: 安装后自动生效，无需额外配置

## 安装要求

- Minecraft 1.20.1+
- Forge 47+
- ExtendedAE 1.4.2+
- Applied Energistics 2

## 安装方法

1. 确保已安装 ExtendedAE 模组
2. 将 ExtendedAE Plus 的 jar 文件放入 mods 文件夹
3. 启动游戏

## 技术实现

本模组使用 Mixin 技术来修改 ExtendedAE 中的 `PartExPatternProvider` 类，具体修改了 `createLogic()` 方法中传递给 `PatternProviderLogic` 构造函数的槽位数量参数。

### 修改的代码位置

```java
// 原始代码
protected PatternProviderLogic createLogic() {
    return new PatternProviderLogic(this.getMainNode(), this, 36);
}

// 修改后的效果
protected PatternProviderLogic createLogic() {
    return new PatternProviderLogic(this.getMainNode(), this, 108);
}
```

## 许可证

本项目采用开源许可证，具体许可证信息请查看 LICENSE 文件。

## 问题反馈

如果您在使用过程中遇到任何问题，请通过 GitHub Issues 进行反馈。 