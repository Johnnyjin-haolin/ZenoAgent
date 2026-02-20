# ZenoAgent 全局视觉风格定义与重构方案

经过多轮迭代，我们已经确立了 ZenoAgent 独特且高质感的**“深渊科技风” (Abyss Tech)**。现在我们将这套设计语言标准化，并推广到所有功能页面。

## 1. 核心视觉风格总结 (Visual Identity System)

### A. 色彩体系 (The Abyss Palette)
*   **背景 (The Void)**: `#020408` (深渊黑) —— 所有页面的基底。
*   **表面 (Surface)**: `rgba(15, 23, 42, 0.6)` —— 用于侧边栏、卡片、对话气泡。
*   **强调色 (Accents)**:
    *   **Electric Blue**: `#3B82F6` (主操作、链接、高亮)。
    *   **Cyber Cyan**: `#06B6D4` (辅助信息、RAG)。
    *   **Neon Purple**: `#A855F7` (特殊状态、MCP)。
*   **文本 (Typography)**:
    *   主要: `#F8FAFC` (亮白)。
    *   次要: `#94A3B8` (蓝灰)。

### B. 纹理与材质 (Texture & Material)
*   **Tech Background**: 全息网格 + 浮动电路框线 + 径向光影。这是全局通用的背景组件。
*   **Glassmorphism**: 所有浮层（Navbar, Sidebar, Modals）均采用高斯模糊磨砂玻璃 (`backdrop-filter: blur(12px)`).
*   **Tech Border**: 四维流光边框，用于关键交互区域（如选中的会话、主按钮）。

### C. 动效 (Motion)
*   **微交互**: 鼠标悬停时的光晕扩散、边框流光。
*   **空间感**: 3D 视差（如首页立方体），页面切换时的平滑淡入淡出。

---