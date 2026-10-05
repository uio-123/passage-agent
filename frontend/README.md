# 前端项目

基于 Vue 3 + Vite + Ant Design Vue 的前端项目模板。

## 环境要求

- Node.js 22+

## 技术栈

- Vue 3
- Vite
- TypeScript
- Ant Design Vue
- Axios
- Pinia
- Vue Router

## 开发

```bash
# 安装依赖
npm ci

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 代码格式化
npm run format

# 代码检查
npm run lint
```

`src/config/env.ts` 是受版本控制的公共配置，通过
`VITE_API_BASE_URL` 选择 API 地址。`.env.development` 默认使用后端地址，
`.env.production` 使用 `/api`，由 Nginx 反向代理到后端。

## 生成 API 代码

```bash
npm run openapi2ts
```

## 目录结构

```
src/
├── api/           # API 接口定义
├── assets/        # 静态资源
├── components/    # 公共组件
├── config/        # 配置文件
├── layouts/       # 布局组件
├── pages/         # 页面组件
├── router/        # 路由配置
├── stores/        # Pinia 状态管理
└── utils/         # 工具函数
```
