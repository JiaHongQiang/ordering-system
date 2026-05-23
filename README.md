# 老李板面馆 - 点餐系统

一个适合小型餐饮店的点餐系统，支持堂食叫号、小料配置、小票打印。

## 功能特性

### 点餐端
- 分类浏览菜单，卡片式商品展示
- 点击商品弹出小料选择（支持单选/多选/多份）
- 购物车实时计算价格，支持数量调整和备注
- 下单后显示取餐号，支持叫号取餐

### 后台管理
- **订单管理**：查看订单详情、状态流转（待支付→已支付→制作中→待取餐→已完成）、打印小票
- **菜单管理**：分类增删改、商品增删改/上下架、关联小料组
- **小料配置**：小料组管理（做法/面条/辣度/卤味等）、小料项管理（名称/价格）、设置默认组
- **店铺设置**：店名/标语/电话/小票尾语，修改后全局生效
- **叫号大屏**：深色背景大字显示待取餐号码，自动刷新

### 小票打印
- 支持 ESC/POS 热敏打印机（网络/USB）
- 小票格式：店名→表头→商品明细（核心属性加粗、小料带组名和价格）→合计→尾语

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Kotlin 1.9 + Ktor 2.3 + Exposed 0.46 |
| 数据库 | SQLite |
| 前端 | Vue 3 + Vite + Element Plus + Pinia |
| 打印 | ESC/POS 指令集 |

## 项目结构

```
ordering-system-web/
├── server/                    # 后端 Ktor 服务
│   └── src/main/kotlin/com/ordering/system/
│       ├── Application.kt     # 入口
│       ├── db/                # 数据库表定义 + DAO
│       └── routes/            # API 路由
├── shared/                    # 共享模块（领域模型 + 定价引擎 + 打印）
│   └── src/main/kotlin/com/ordering/system/
│       ├── domain/model/      # 实体类
│       ├── domain/engine/     # 定价引擎、订单校验
│       └── print/             # 小票打印驱动和模板
├── web/                       # 前端 Vue 项目
│   └── src/
│       ├── views/             # 页面（点餐/订单/叫号/后台）
│       ├── components/        # 组件（购物车/小料选择）
│       ├── stores/            # Pinia 状态管理
│       └── api/               # API 封装
├── deploy.bat                 # Windows 一键部署脚本
└── deploy.sh                  # Linux/Mac 一键部署脚本
```

## 快速开始

### 环境要求
- JDK 17+
- Node.js 18+

### 开发模式

```bash
# 启动后端（端口 8080）
cd ordering-system-web
gradlew.bat :server:run

# 启动前端（端口 5173，自动代理到后端）
cd web
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`

### 生产部署

```bash
# Windows
deploy.bat

# Linux/Mac
bash deploy.sh
```

部署后启动：
```bash
# 方式一：直接运行
gradlew.bat :server:run

# 方式二：独立运行（不需要 Gradle）
server\build\install\server\bin\server.bat
```

局域网内其他设备访问 `http://服务器IP:8080`

## 页面说明

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` | 点餐 | 顾客点餐，选商品→选小料→下单→显示取餐号 |
| `/orders` | 订单 | 管理订单状态，查看详情，打印小票 |
| `/queue` | 叫号 | 大屏显示待取餐号码，供顾客查看 |
| `/admin` | 后台 | 菜单管理、小料配置、店铺设置 |

## 默认数据

系统首次启动会自动创建示例数据：

**分类**：板面、小菜、饮品、主食

**小料组**：
- 做法（必选/单选）：汤面、拌面
- 面条（必选/单选）：宽面、细面
- 辣度（必选/单选）：不辣、微辣、中辣、特辣
- 卤味（可选/不限量）：卤蛋(¥2)、卤肉(¥5)、豆皮(¥2)、海带结(¥2)、丸子(¥3)、豆腐(¥2)、鸡腿(¥6)、鸡翅(¥5)

## 数据备份

数据库文件为 `server/ordering.db`（SQLite），复制该文件即可备份全部数据。

## License

MIT
