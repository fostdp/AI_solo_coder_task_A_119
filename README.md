# 古代天平衡器精度检定与误差分析系统

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Docker Compose 编排                            │
│                                                                         │
│  ┌──────────┐   MQTT    ┌──────────────────┐   STOMP    ┌───────────┐  │
│  │ Simulator │──────────▶│  Spring Boot API  │──────────▶│  Frontend  │  │
│  │ (Python)  │  :1883   │  (Java 11/JRE)    │  WebSocket│  (Nginx)   │  │
│  └──────────┘          └────────┬─────────┘           └─────┬─────┘  │
│      ▲                          │                           │         │
│      │                          │ JPA/JDBC                  │ proxy   │
│      │                          ▼                           │ :80→:8080│
│      │                  ┌──────────────┐                    │         │
│      │                  │  PostgreSQL   │                    │         │
│      │                  │  (:5432)      │                    │         │
│      │                  └──────────────┘                    │         │
│      │                                                      │         │
│      │  MQTT Broker                                         │         │
│      │  ┌──────────────┐                                    │         │
│      └──│  Mosquitto   │◀──────────────────────────────────┘         │
│         │  (:1883)      │                                            │
│         └──────────────┘                                             │
│                                                                         │
│  ┌──────────────┐                                                     │
│  │  Prometheus   │  scrape /api/actuator/prometheus                   │
│  │  (:9090)      │◀──────────────────────────────────────             │
│  └──────────────┘                                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

## 数据流

```
Simulator ──MQTT──▶ MqttReceiver ──Event──▶ AlarmWebSocket ──STOMP──▶ Frontend
                        │                                        │
                   save to DB                              3D + Charts
                        │
              ┌─────────┴──────────┐
              │                    │
     ErrorSimulator      MetrologyAnalyzer
     (Monte Carlo)       (K-Means + Bayes)
              │                    │
         Event:EventAnalysis   Event:MetrologyAnalysis
```

## 模块说明

### 后端模块 (Spring Boot 2.7)

| 模块 | 包路径 | 职责 |
|------|--------|------|
| **mqtt_receiver** | `modules.mqtt_receiver` | MQTT数据接收、磨损模型集成、融合校准、告警判定 |
| **error_simulator** | `modules.error_simulator` | 蒙特卡洛误差仿真(10万次)、动态摩擦、三源合成 |
| **metrology_analyzer** | `modules.metrology_analyzer` | K-Means++聚类、先验过滤、贝叶斯后验校正 |
| **alarm_ws** | `modules.alarm_ws` | Spring Events监听、@Async STOMP推送 |

模块间通过 Spring ApplicationEvent 解耦：
- `MeasurementSavedEvent` → 测量保存后广播
- `AlertTriggeredEvent` → 告警触发后异步推送WebSocket
- `ErrorAnalysisCompletedEvent` → 蒙特卡洛完成后广播
- `MetrologyAnalysisCompletedEvent` → 聚类分析完成后广播

### 前端文件

| 文件 | 职责 |
|------|------|
| `config.js` | 全局配置(API_BASE/WS_URL/物理参数/性能阈值) |
| `balance3d.js` | Three.js天平三维渲染 + LOD + 物理模拟 + 触控 |
| `charts.js` | Canvas误差曲线图 + 直方图 |
| `metrology_panel.js` | API客户端 + 面板控制 + WebSocket告警订阅 |
| `app.js` | Bootstrap入口 |

### 核心算法

- **磨损模型**: Archard定律 Δh = k·P·S/(H·√A) + 摩擦学公式 μ(h,T,RH)
- **蒙特卡洛**: 10万次采样，动态摩擦随磨损推进，温湿度偏置
- **聚类分析**: K-Means++ + 16朝代先验知识库 + 贝叶斯后验校正

## 快速部署

### 前提条件

- Docker 20.10+
- Docker Compose v2.0+

### 一键启动

```bash
# 构建并启动所有服务
docker-compose up --build -d

# 查看服务状态
docker-compose ps

# 查看后端日志
docker-compose logs -f backend

# 查看模拟器日志
docker-compose logs -f simulator
```

### 访问地址

| 服务 | URL |
|------|-----|
| 前端界面 | http://localhost |
| 后端API | http://localhost:8080/api |
| Actuator健康检查 | http://localhost:8080/api/actuator/health |
| Prometheus指标 | http://localhost:8080/api/actuator/prometheus |
| Prometheus UI | http://localhost:9090 |
| MQTT Broker | localhost:1883 |
| PostgreSQL | localhost:5432 |

### 环境变量

模拟器支持以下环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MQTT_BROKER` | localhost | MQTT Broker地址 |
| `MQTT_PORT` | 1883 | MQTT端口 |
| `SIM_MODE` | normal | 运行模式: normal/fast/single |
| `TOTAL_BALANCES` | 100 | 模拟天平数量 |
| `PUBLISH_INTERVAL` | 3600 | 正常模式发布间隔(秒) |
| `FAST_INTERVAL` | 5 | 快速模式发布间隔(秒) |

## 监控

### Actuator端点

- `/api/actuator/health` - 健康检查
- `/api/actuator/prometheus` - Prometheus指标
- `/api/actuator/metrics` - JVM/HTTP指标
- `/api/actuator/env` - 环境变量
- `/api/actuator/loggers` - 日志级别管理

### 关键Prometheus指标

- `http_server_requests_seconds` - API请求延迟
- `jvm_memory_used_bytes` - JVM内存使用
- `hikaricp_connections_active` - 数据库连接池
- `mqtt_messages_received_total` - MQTT消息接收数

## 数据库索引

已在 `init.sql` 中创建以下索引：

- `idx_balance_measurements_balance_time` - 按天平+时间联合查询(最频繁)
- `idx_balance_measurements_wear` - 按天平+磨损深度查询
- `idx_balance_measurements_friction` - 按天平+摩擦系数查询
- `idx_balance_measurements_alert` - 部分索引(仅告警记录)
- `idx_weights_actual_mass` - 砝码质量聚类查询
- `idx_alerts_level_time` - 按告警级别+时间查询
- `idx_balances_dynasty/type/material` - 天平筛选查询

## 停止服务

```bash
# 停止所有容器
docker-compose down

# 停止并清除数据卷
docker-compose down -v
```
