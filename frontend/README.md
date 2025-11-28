# 电商订单管理系统 - 前端

基于 Vue 3 + TypeScript + Vite + Element Plus 构建的现代化前端应用。

## ✨ 技术栈

- **框架**: Vue 3.4+ (Composition API)
- **语言**: TypeScript 5.3+
- **构建工具**: Vite 5.0+
- **UI组件库**: Element Plus 2.5+
- **状态管理**: Pinia 2.1+
- **路由**: Vue Router 4.2+
- **HTTP请求**: Axios 1.6+
- **图标**: Element Plus Icons

## 🎨 功能特性

### 1. 订单管理
- ✅ 创建订单 - 表单验证、实时提交
- ✅ 订单列表 - 按用户ID查询、表格展示
- ✅ 订单详情 - 完整信息展示、状态追踪

### 2. 界面特点
- 🎯 现代简约设计风格
- 📱 响应式布局，适配各种屏幕
- 🌈 渐变色彩，视觉效果优雅
- ⚡ 页面切换动画流畅
- 🔔 友好的错误提示和加载状态

### 3. 技术亮点
- TypeScript 类型安全
- Axios 请求拦截和统一错误处理
- Vue 3 Composition API
- 组件化开发
- 路由懒加载

## 📦 项目结构

```
frontend/
├── public/                 # 静态资源
├── src/
│   ├── api/               # API接口
│   │   ├── request.ts     # Axios封装
│   │   └── order.ts       # 订单API
│   ├── router/            # 路由配置
│   │   └── index.ts
│   ├── types/             # TypeScript类型定义
│   │   └── order.ts
│   ├── views/             # 页面组件
│   │   ├── CreateOrder.vue     # 创建订单
│   │   ├── OrderList.vue       # 订单列表
│   │   └── OrderDetail.vue     # 订单详情
│   ├── App.vue            # 根组件
│   └── main.ts            # 应用入口
├── index.html             # HTML模板
├── vite.config.ts         # Vite配置
├── tsconfig.json          # TypeScript配置
└── package.json           # 依赖配置
```

## 🚀 快速开始

### 1. 安装依赖

使用 npm:
```bash
cd frontend
npm install
```

使用 pnpm (推荐):
```bash
cd frontend
pnpm install
```

使用 yarn:
```bash
cd frontend
yarn install
```

### 2. 启动开发服务器

```bash
npm run dev
# 或
pnpm dev
# 或
yarn dev
```

启动成功后，浏览器会自动打开 http://localhost:3000

### 3. 构建生产版本

```bash
npm run build
# 或
pnpm build
# 或
yarn build
```

构建产物会输出到 `dist` 目录。

### 4. 预览生产版本

```bash
npm run preview
# 或
pnpm preview
# 或
yarn preview
```

## ⚙️ 配置说明

### Vite 代理配置

前端通过 Vite 的代理功能转发请求到后端网关，避免跨域问题：

```typescript
// vite.config.ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8000', // 网关地址
      changeOrigin: true
    }
  }
}
```

### API 基础地址

```typescript
// src/api/request.ts
baseURL: '/api' // 会被代理到 http://localhost:8000/api
```

## 📖 使用说明

### 1. 创建订单
1. 点击顶部导航栏的"创建订单"
2. 填写订单信息（提供了示例数据）
3. 点击"提交订单"按钮
4. 成功后自动跳转到订单详情页

### 2. 查看订单列表
1. 在"订单列表"页面
2. 输入用户ID（默认1001）
3. 点击"查询"按钮
4. 查看该用户的所有订单

### 3. 查看订单详情
1. 在订单列表中点击"查看详情"
2. 或直接访问 `/orders/{订单号}`
3. 查看订单完整信息和处理流程

## 🎯 开发规范

### 1. 代码风格
- 使用 TypeScript 严格模式
- 组件使用 `<script setup>` 语法
- 遵循 Vue 3 官方推荐的代码风格

### 2. 命名规范
- 组件文件：PascalCase (如 `OrderList.vue`)
- 普通文件：camelCase (如 `order.ts`)
- 类型定义：PascalCase (如 `OrderResponse`)

### 3. 提交规范
- feat: 新功能
- fix: 修复bug
- style: 样式调整
- refactor: 重构代码

## 🔧 环境要求

- Node.js >= 16.0.0
- npm >= 7.0.0 或 pnpm >= 7.0.0

## 📝 注意事项

1. **后端服务**: 前端依赖后端微服务，请确保后端服务已启动
2. **端口配置**: 默认前端端口3000，网关端口8000
3. **数据初始化**: 使用前请确保数据库中有测试数据
4. **浏览器兼容**: 推荐使用 Chrome、Edge、Firefox 最新版本

## 🎨 界面预览

### 创建订单页面
- 简洁的表单设计
- 实时验证和错误提示
- 示例数据参考卡片

### 订单列表页面
- 清晰的表格展示
- 状态标签颜色区分
- 快速搜索和筛选

### 订单详情页面
- 渐变色状态卡片
- 详细的订单信息展示
- 可视化处理流程

## 🚀 性能优化

1. **路由懒加载**: 页面组件按需加载
2. **Vite 快速启动**: 开发环境秒启动
3. **Tree Shaking**: 自动移除未使用的代码
4. **代码分割**: 构建时自动分割代码块

## 📦 部署

### Nginx 部署示例

```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    root /path/to/dist;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api/ {
        proxy_pass http://localhost:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可

MIT License

---

**作者**: demo  
**版本**: 1.0.0  
**创建时间**: 2024-11-20

