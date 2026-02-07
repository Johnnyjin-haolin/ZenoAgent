# 贡献指南

感谢您对 ZenoAgent 项目的关注！我们欢迎所有形式的贡献。

## 🤝 如何贡献

### 报告问题

如果您发现了 bug 或有功能建议，请通过以下方式提交：

1. **检查现有 Issue**：在提交新 Issue 之前，请先搜索是否已有相关问题
2. **创建 Issue**：使用清晰的标题和详细描述
   - Bug 报告：请包含复现步骤、预期行为和实际行为
   - 功能建议：请说明使用场景和预期效果

### 提交代码

1. **Fork 项目**
   ```bash
   git clone https://github.com/your-username/ZenoAgent.git
   cd ZenoAgent
   ```

2. **创建分支**
   ```bash
   git checkout -b feature/your-feature-name
   # 或
   git checkout -b fix/your-bug-fix
   ```

3. **开发规范**
   - 遵循现有代码风格
   - 添加必要的注释和文档
   - 确保代码通过所有测试
   - 提交前运行代码格式化工具

4. **提交代码**
   ```bash
   git add .
   git commit -m "feat: 添加新功能描述"
   git push origin feature/your-feature-name
   ```

5. **创建 Pull Request**
   - 在 GitHub 上创建 Pull Request
   - 填写清晰的 PR 描述
   - 关联相关 Issue（如果有）

## 📝 代码规范

### Java 代码规范

- 遵循 Google Java Style Guide
- 使用 4 个空格缩进
- 类名使用大驼峰（PascalCase）
- 方法名和变量名使用小驼峰（camelCase）
- 常量使用全大写下划线分隔（UPPER_SNAKE_CASE）

### TypeScript/Vue 代码规范

- 遵循 Vue 3 官方风格指南
- 使用 2 个空格缩进
- 组件名使用大驼峰（PascalCase）
- 使用 TypeScript 严格模式
- 优先使用 Composition API

### 提交信息规范

我们使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `style`: 代码格式调整（不影响功能）
- `refactor`: 代码重构
- `perf`: 性能优化
- `test`: 测试相关
- `chore`: 构建/工具链相关

示例：
```
feat: 添加 Redis 连接池配置
fix: 修复 SSE 流式响应中断问题
docs: 更新部署文档
```

## 🧪 测试

在提交 PR 之前，请确保：

- [ ] 所有现有测试通过
- [ ] 为新功能添加了测试用例
- [ ] 代码覆盖率不低于 80%

运行测试：

```bash
# 后端测试
cd backend
mvn test

# 前端测试（如果配置了）
cd frontend
npm test
```

## 📚 文档

- 更新相关文档（README、API 文档等）
- 为新功能添加使用示例
- 更新 CHANGELOG（如果适用）

## 🔍 代码审查

所有 PR 都需要经过代码审查：

- 至少需要一位维护者批准
- 解决所有审查意见
- 确保 CI 检查通过

## ❓ 需要帮助？

如果您有任何问题，可以：

- 在 GitHub Discussions 中提问
- 创建 Issue 并标记为 `question`
- 联系维护者

再次感谢您的贡献！🎉
