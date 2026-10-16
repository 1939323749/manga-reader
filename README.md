# manga-reader

一个自托管的网页漫画阅读器，直接使用 [Keiyoushi](https://github.com/keiyoushi/extensions) 等 Mihon/Tachiyomi 扩展仓库里的扩展。

后端从 [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) 精简而来：保留了它的 Android 兼容层（AndroidCompat）、`eu.kanade.tachiyomi` 扩展 API 实现和扩展加载代码；去掉了数据库、下载、书库、追踪、OPDS、GraphQL 和内置 WebUI，换成一个无状态的 REST API。前端是 Vue 3 + Vite。

## 运行

需要 Java 21 或更高版本。

```bash
java -jar manga-reader-0.1.0.jar
```

如果提示端口被占用（比如同时开着 Suwayomi），换个端口：`java "-Dmanga.reader.port=8080" -jar manga-reader-0.1.0.jar`。

然后打开 <http://localhost:4567>，先到「扩展」页安装扩展，再到「来源」页浏览。

构建好的 jar 可以在 GitHub Actions 每次构建的 Artifacts 里下载。

### 配置

全部通过环境变量（或同名的 `-D` 参数，如 `-Dmanga.reader.port=8080`）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MANGA_READER_PORT` | `4567` | 端口 |
| `MANGA_READER_HOST` | `0.0.0.0` | 监听地址，只想本机访问就设成 `127.0.0.1` |
| `MANGA_READER_DATA` | `~/.manga-reader` | 数据目录（已安装的扩展、扩展自己的设置） |
| `MANGA_READER_REPOS` | Keiyoushi 的 `repo.json` | 扩展仓库地址，逗号分隔 |
| `MANGA_READER_PROXY` | 空（使用系统代理） | 访问漫画站用的代理，如 `http://127.0.0.1:7890`、`socks5://127.0.0.1:7891`；填 `none` 强制直连 |
| `FLARESOLVERR_ENABLED` | `false` | 遇到 Cloudflare 验证时是否调用 FlareSolverr |
| `FLARESOLVERR_URL` | `http://localhost:8191` | FlareSolverr 地址 |
| `MANGA_READER_LOG_LEVEL` | `INFO` | 日志级别 |

## 开发

```bash
# 后端
./gradlew :server:run

# 前端（另开一个终端，/api 会代理到 4567 端口）
cd web && npm install && npm run dev
```

`npm run build` 会把前端输出到 `server/src/main/resources/web/`，之后打出来的 jar 会自带页面。

## API

服务端不保存漫画和章节数据：列表接口返回的漫画、章节对象，由前端在请求详情和图片时原样发回。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/repo/extensions?refresh=1` | 仓库中的扩展（带安装状态） |
| GET | `/api/extensions` | 已安装的扩展 |
| POST | `/api/extensions/{pkg}/install` | 安装或更新 |
| POST | `/api/extensions/{pkg}/uninstall` | 卸载 |
| GET | `/api/sources` | 已安装的来源 |
| GET | `/api/sources/{id}` | 来源信息（会加载扩展） |
| GET | `/api/sources/{id}/popular?page=` | 热门 |
| GET | `/api/sources/{id}/latest?page=` | 最新 |
| GET | `/api/sources/{id}/search?q=&page=` | 搜索（默认筛选器） |
| POST | `/api/sources/{id}/search` | 带筛选器搜索，请求体 `{"q", "page", "filters": [{"index", "childIndex"?, "state"}]}` |
| GET | `/api/sources/{id}/filters` | 来源的筛选器列表（`index` 为位置，分组的子项在 `filters` 里） |
| GET | `/api/sources/{id}/preferences` | 来源的偏好设置 |
| PUT | `/api/sources/{id}/preferences` | 修改一项设置，请求体 `{"key", "value"}`；之后扩展会重新加载 |
| POST | `/api/sources/{id}/manga` | 请求体为漫画对象，返回详情和章节列表 |
| POST | `/api/sources/{id}/pages` | 请求体为章节对象，返回页面列表 |
| GET | `/api/sources/{id}/image?url=&imageUrl=` | 页面图片 |
| GET | `/api/sources/{id}/cover?url=` | 封面（用来源自己的请求头抓取） |

来源 id 是 64 位整数，接口里一律用字符串表示。

## 已知限制

- 筛选器的 `state` 只改动和默认值不同的项；少数来源的筛选器要等它先加载过一次列表才完整，这时在筛选面板里点「重新载入」。
- 需要 WebView 登录或强 Cloudflare 防护的来源，可能要配合 FlareSolverr，部分仍然无法使用。
- 阅读记录只存在浏览器的 localStorage 里。
- 安装扩展时优先使用仓库提供的预编译 jar（Keiyoushi 的扩展都有）。只提供 APK 的仓库会走 dex2jar 转换，但 dex2jar 处理不了 R8 的构造函数内联，用 R8 优化过的扩展转换后可能在运行时报 `ClassCastException`，这和上游 Suwayomi 的情况一样。

## 关于版权

扩展抓取的多是第三方网站上的内容。自己用、或在内网自托管问题不大；如果公开部署、替别人抓取和转发受版权保护的图片，相应的法律责任由运营者承担。

## 许可证

本项目沿用 Suwayomi-Server 的 [Mozilla Public License 2.0](LICENSE)。来自 Suwayomi 的文件保留了原版权声明。
