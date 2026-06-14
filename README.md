# 古代天平衡器精度检定与误差分析系统

## 项目概述

本系统是一个用于研究古代天平（等臂天平、不等臂天平）精度的全栈应用，涵盖从战国至清代100件古代天平的精度测试数据分析。

## 技术栈

- **后端**: Java Spring Boot 2.7.x
- **数据库**: PostgreSQL 14+
- **消息队列**: MQTT (Eclipse Mosquitto)
- **前端**: HTML5 + Canvas + Three.js
- **实时通信**: WebSocket
- **模拟器**: Python

## 项目结构

```
balance-system/
├── backend/              # Java Spring Boot 后端
│   ├── src/
│   └── pom.xml
├── frontend/             # 前端应用
│   ├── index.html
│   ├── css/
│   └── js/
├── database/             # 数据库初始化脚本
│   └── init.sql
├── simulator/            # 天平模拟器
│   └── balance_simulator.py
└── README.md
```

## 核心功能

1. **天平数据采集**: 通过MQTT接收模拟传感器数据（砝码质量、刀口磨损深度、臂长、称量误差）
2. **三维可视化**: Three.js绘制天平三维模型，刀口和横梁高亮显示
3. **误差分析**: 基于刀口摩擦、臂长不等和砝码误差的蒙特卡洛模拟
4. **权衡制度分析**: 基于出土砝码质量的聚类分析，推断各朝代斤两标准
5. **告警系统**: 误差超过允差时通过WebSocket实时推送预警

## 快速开始

### 1. 数据库初始化

```bash
psql -U postgres -d balance_db -f database/init.sql
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

### 3. 启动MQTT broker (可选)

```bash
# 使用Docker
docker run -d -p 1883:1883 eclipse-mosquitto
```

### 4. 运行模拟器

```bash
cd simulator
pip install paho-mqtt
python balance_simulator.py
```

### 5. 访问前端

直接在浏览器中打开 `frontend/index.html`
