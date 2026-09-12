# Tauri 原生客户端打包指南

用 [Tauri 2](https://tauri.app) 把前端打包为原生 App：**Windows 安装包（NSIS/MSI）、macOS dmg、Android APK**。数据与网页/PWA 版互通（同一套云端接口）。

## 一、架构要点（为什么有这几处改动）

| 改动 | 说明 |
|---|---|
| `src/api.ts` 的 `API_BASE` | Web 部署走同源相对路径；打包 App 后页面来源是 `tauri://localhost`，跨域请求需要绝对地址。构建时用 `VITE_API_BASE` 环境变量指向云端前端域名（nginx 统一反代接口） |
| 后端 `CorsConfig` | 放行 Tauri 壳来源（`tauri://localhost`、`http://tauri.localhost`）的跨域请求；CORS 过滤器排在 JWT 过滤器之前，预检请求（无 token）也能通过。额外来源用 `APP_CORS_ALLOWED_ORIGINS` 环境变量追加 |
| `main.ts` SW 守卫 | Tauri 壳内不注册 PWA Service Worker（离线外壳由原生安装包承担） |

## 二、CI 构建（推荐，无需本地装 Rust）

仓库已配好 [.github/workflows/release.yml](../.github/workflows/release.yml)：

```bash
git tag v1.0.0
git -c http.proxy=http://127.0.0.1:7897 push origin v1.0.0
```

Actions 会自动：
1. **desktop** job：Windows（NSIS + MSI）与 macOS（dmg）安装包，产物挂到 GitHub Release；
2. **android** job：Android APK（aarch64，调试签名），在 Actions 构件（Artifacts）里下载。

> macOS 未签名：首次打开需右键 → 打开（绕过 Gatekeeper）。Android 调试签名 APK 可直接侧载安装；正式上架应用市场需自行创建签名密钥并配置 `keystore.properties`。

## 三、本地构建（可选）

前置：[Rust](https://rustup.rs)（Windows 需 MSVC Build Tools）、Node 22；Android 需 Android Studio（SDK + NDK）。

```bash
cd frontend
npm install

# 桌面调试（热更新，加载本地 5173）
npx tauri dev

# 桌面构建（产物在 src-tauri/target/release/bundle/）
VITE_API_BASE=https://ai-lovefrontend-xmplmppqhf.cn-hangzhou.fcapp.run npx tauri build

# Android（首次先 init 生成 gen/android 工程）
npx tauri android init --ci
VITE_API_BASE=https://ai-lovefrontend-xmplmppqhf.cn-hangzhou.fcapp.run npx tauri android build --apk --debug --target aarch64
# 产物：src-tauri/gen/android/app/build/outputs/apk/aarch64/debug/app-aarch64-debug.apk
```

iOS 需要 macOS + Xcode + 苹果开发者账号（$99/年），仓库已含 iOS 图标与 Rust 移动端入口，有 Mac 时 `npx tauri ios init && npx tauri ios build` 即可。

## 四、版本与图标

- 版本号：`src-tauri/tauri.conf.json` 的 `version`（与 Cargo.toml 同步改）；
- 图标：改 `public/icons/icon-512.png` 后重新执行 `npx tauri icon public/icons/icon-512.png`（生成全套桌面/移动图标）；
- API 域名变更：同步修改 `release.yml` 里的 `VITE_API_BASE` 与 s.yaml 的前端域名。
