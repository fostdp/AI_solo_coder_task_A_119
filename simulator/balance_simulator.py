#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
古代天平模拟器 - MQTT数据发布脚本
模拟100件古代天平每小时上报传感器数据
包含基于Archard磨损定律的动态磨损-摩擦模型
"""

import json
import os
import time
import random
import math
from datetime import datetime, timezone, timedelta
try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("请安装paho-mqtt: pip install paho-mqtt")
    import sys
    sys.exit(1)

MQTT_BROKER = os.environ.get("MQTT_BROKER", "localhost")
MQTT_PORT = int(os.environ.get("MQTT_PORT", "1883"))
MQTT_USERNAME = os.environ.get("MQTT_USERNAME", "admin")
MQTT_PASSWORD = os.environ.get("MQTT_PASSWORD", "public")
MQTT_TOPIC = "balance/sensor/data"
MQTT_CLIENT_ID = "balance-simulator"

TOTAL_BALANCES = int(os.environ.get("TOTAL_BALANCES", "100"))
PUBLISH_INTERVAL = int(os.environ.get("PUBLISH_INTERVAL", "3600"))
FAST_INTERVAL = int(os.environ.get("FAST_INTERVAL", "5"))

CST = timezone(timedelta(hours=8))

DYNASTIES = [
    "战国", "秦", "西汉", "东汉", "三国", "西晋", "东晋", "南北朝",
    "隋", "唐", "五代十国", "北宋", "南宋", "元", "明", "清"
]

MATERIAL_PROPERTIES = {
    "青铜": {"hardness": 150.0, "mu0": 0.0012, "alpha": 0.09, "delta": 0.00008},
    "铁":   {"hardness": 200.0, "mu0": 0.0015, "alpha": 0.07, "delta": 0.00012},
    "钢":   {"hardness": 350.0, "mu0": 0.0008, "alpha": 0.05, "delta": 0.00005},
    "玉石": {"hardness": 500.0, "mu0": 0.0005, "alpha": 0.03, "delta": 0.00003},
    "玛瑙": {"hardness": 650.0, "mu0": 0.0004, "alpha": 0.02, "delta": 0.00002},
    "木":   {"hardness": 80.0,  "mu0": 0.0020, "alpha": 0.12, "delta": 0.00020},
}
DEFAULT_MATERIAL = "青铜"

BALANCE_TYPES = ["EQUAL_ARM", "UNEQUAL_ARM"]
MATERIALS = list(MATERIAL_PROPERTIES.keys())

BALANCE_DATA = {}


class KnifeEdgeWearSimulator:
    """刀口磨损-摩擦动态模拟器 (与Java后端KnifeEdgeWearModel一致)

    摩擦公式:
      μ(h,T,RH) = [μ₀ + α·h^β + γ·h·f(T)] · [1 + δ·(RH/100)²]
    磨损增量 (修正Archard定律):
      Δh = k · P · S / (H · √A) · humidity_accel · aging_factor
    """

    FRACTURE_THRESHOLD = 0.15
    WEAR_COEFFICIENT = 1.5e-8
    BETA = 0.65
    GAMMA = 0.002

    def __init__(self, material=DEFAULT_MATERIAL):
        props = MATERIAL_PROPERTIES.get(material, MATERIAL_PROPERTIES[DEFAULT_MATERIAL])
        self.mu0 = props["mu0"]
        self.hardness = props["hardness"]
        self.alpha = props["alpha"]
        self.delta = props["delta"]
        self.material = material

        self.accumulated_wear = 0.0
        self.total_usage = 0
        self.first_usage_time = None

    def _temperature_effect(self, temperature):
        delta_t = temperature - 20.0
        return math.exp(abs(delta_t) * 0.015) - 1.0

    def calculate_friction(self, nominal_mass, temperature, humidity, arm_length):
        h = self.accumulated_wear

        mu_wear = self.mu0 + self.alpha * (h ** self.BETA) + self.GAMMA * h * self._temperature_effect(temperature)

        mu_final = mu_wear * (1.0 + self.delta * ((humidity / 100.0) ** 2))

        if h > self.FRACTURE_THRESHOLD:
            fracture_factor = 1.0 + 2.5 * ((h - self.FRACTURE_THRESHOLD) ** 1.5)
            mu_final *= fracture_factor

        jitter = (random.random() - 0.5) * 2.0 * mu_final * 0.05

        return max(0.0001, mu_final + jitter)

    def record_usage(self, nominal_mass, arm_length, swing_angle_deg, temperature, humidity):
        if self.first_usage_time is None:
            self.first_usage_time = datetime.now(CST)

        P = nominal_mass * 9.81 / 1000.0
        S = 2.0 * math.pi * arm_length * (swing_angle_deg / 360.0) * 3.0

        contact_area = math.pi * (max(0.5, self.accumulated_wear + 0.5) ** 2)

        hardness_factor = 1.0
        if temperature > 50.0:
            hardness_factor = max(0.5, 1.0 - (temperature - 50.0) * 0.01)

        delta_h = (self.WEAR_COEFFICIENT * P * S
                   / (self.hardness * hardness_factor * math.sqrt(contact_area)))

        humidity_accel = 1.0 + self.delta * ((humidity / 100.0) ** 2.5) * 100
        delta_h *= humidity_accel

        hours_elapsed = 0
        if self.first_usage_time:
            hours_elapsed = (datetime.now(CST) - self.first_usage_time).total_seconds() / 3600.0
        aging_factor = 1.0 + hours_elapsed * 0.0001
        delta_h *= aging_factor

        self.accumulated_wear += delta_h
        self.total_usage += 1

        return delta_h

    def get_wear_stage(self):
        h = self.accumulated_wear
        if h < 0.01:
            return "跑合阶段"
        elif h < self.FRACTURE_THRESHOLD:
            return "稳定磨损阶段"
        else:
            return "剧烈磨损阶段"


def init_balance_data():
    for i in range(1, TOTAL_BALANCES + 1):
        balance_code = f"BAL-{i:04d}"
        balance_type = BALANCE_TYPES[i % 3 == 0 and 1 or 0]

        material = MATERIALS[i % len(MATERIALS)]

        base_left_arm = 150.0 + (i % 10) * 5.0 + random.uniform(-1, 1)
        if balance_type == "UNEQUAL_ARM":
            base_right_arm = base_left_arm + random.uniform(10, 30)
        else:
            base_right_arm = base_left_arm + random.uniform(-0.5, 0.5)

        base_knife_edge = 1.5 + random.uniform(0, 1.0)
        base_error_std = 0.003 + (i % 5) * 0.002

        if i % 5 == 0:
            initial_wear = random.uniform(0.16, 0.30)
        elif i % 5 == 4:
            initial_wear = random.uniform(0.01, 0.08)
        else:
            initial_wear = random.uniform(0.0, 0.02)

        wear_sim = KnifeEdgeWearSimulator(material)
        wear_sim.accumulated_wear = initial_wear

        if initial_wear > 0.15:
            wear_sim.total_usage = random.randint(8000, 20000)
            wear_sim.first_usage_time = datetime.now(CST) - timedelta(days=random.randint(365, 730))
        elif initial_wear > 0.01:
            wear_sim.total_usage = random.randint(2000, 8000)
            wear_sim.first_usage_time = datetime.now(CST) - timedelta(days=random.randint(90, 365))
        else:
            wear_sim.total_usage = random.randint(0, 500)
            wear_sim.first_usage_time = datetime.now(CST) - timedelta(days=random.randint(0, 90))

        BALANCE_DATA[balance_code] = {
            "id": i,
            "code": balance_code,
            "type": balance_type,
            "material": material,
            "dynasty_index": i % 16,
            "base_left_arm": base_left_arm,
            "base_right_arm": base_right_arm,
            "base_knife_edge": base_knife_edge,
            "base_error_std": base_error_std,
            "wear_simulator": wear_sim,
            "initial_wear": initial_wear,
            "measurement_count": 0
        }


def generate_measurement(balance_code):
    """生成单次测量数据（使用动态磨损-摩擦模型）"""
    data = BALANCE_DATA[balance_code]
    wear_sim = data["wear_simulator"]

    nominal_mass = random.choice([
        1.0, 2.0, 5.0, 10.0, 15.625, 20.0, 25.0,
        31.25, 50.0, 62.5, 100.0, 125.0, 250.0, 500.0
    ])

    left_arm = data["base_left_arm"] + random.uniform(-0.1, 0.1)
    right_arm = data["base_right_arm"] + random.uniform(-0.1, 0.1)

    temperature = 20.0 + random.uniform(-5, 15)
    humidity = 40.0 + random.uniform(0, 50)
    swing_angle = 3.0 + random.uniform(0, 5.0)

    wear_sim.record_usage(nominal_mass, left_arm, swing_angle, temperature, humidity)

    knife_friction = wear_sim.calculate_friction(nominal_mass, temperature, humidity, left_arm)
    knife_wear_depth = wear_sim.accumulated_wear

    arm_ratio_error = (left_arm - right_arm) / left_arm
    arm_error = nominal_mass * arm_ratio_error

    friction_error = knife_friction * nominal_mass * random.uniform(0.9, 1.1)

    weight_error = random.gauss(0, data["base_error_std"])

    humidity_bias = (humidity - 50.0) * 0.00001 * nominal_mass
    temp_bias = (temperature - 20.0) * 0.000005 * nominal_mass

    total_error = weight_error + arm_error + friction_error + humidity_bias + temp_bias
    measured_mass = nominal_mass + total_error

    relative_error = total_error / nominal_mass if nominal_mass != 0 else 0

    data["measurement_count"] += 1

    if data["measurement_count"] % 100 == 0:
        print(f"  [{balance_code}] 磨损阶段: {wear_sim.get_wear_stage()}, "
              f"累计磨损={wear_sim.accumulated_wear:.6f}mm, "
              f"摩擦系数={knife_friction:.6f}, "
              f"使用次数={wear_sim.total_usage}")

    measurement = {
        "balanceCode": balance_code,
        "timestamp": datetime.now(CST).isoformat(),
        "nominalMass": round(nominal_mass, 4),
        "measuredMass": round(measured_mass, 6),
        "weighingError": round(total_error, 6),
        "relativeError": round(relative_error, 8),
        "leftArmLength": round(left_arm, 4),
        "rightArmLength": round(right_arm, 4),
        "knifeEdgeWearDepth": round(knife_wear_depth, 6),
        "knifeEdgeFriction": round(knife_friction, 6),
        "temperature": round(temperature, 2),
        "humidity": round(humidity, 2)
    }

    return measurement


def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[{datetime.now(CST).strftime('%H:%M:%S')}] MQTT连接成功")
    else:
        print(f"[{datetime.now(CST).strftime('%H:%M:%S')}] MQTT连接失败，错误码: {rc}")


def on_disconnect(client, userdata, rc):
    print(f"[{datetime.now(CST).strftime('%H:%M:%S')}] MQTT连接断开，错误码: {rc}")


def publish_all_balances(client):
    """发布所有天平的测量数据"""
    success_count = 0
    fail_count = 0

    for i, balance_code in enumerate(BALANCE_DATA.keys()):
        try:
            measurement = generate_measurement(balance_code)
            payload = json.dumps(measurement, ensure_ascii=False)

            topic = f"{MQTT_TOPIC}/{balance_code}"
            result = client.publish(topic, payload, qos=1)

            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                success_count += 1
            else:
                fail_count += 1

            if (i + 1) % 20 == 0:
                print(f"  已发布 {i + 1}/{TOTAL_BALANCES}...")

            time.sleep(0.05)

        except Exception as e:
            fail_count += 1
            print(f"  发布 {balance_code} 失败: {e}")

    return success_count, fail_count


def run_simulation(fast_mode=False):
    """运行模拟器"""
    client = mqtt.Client(client_id=MQTT_CLIENT_ID)
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect

    print("=" * 60)
    print("古代天平模拟器启动")
    print("=" * 60)
    print(f"MQTT Broker: {MQTT_BROKER}:{MQTT_PORT}")
    print(f"主题: {MQTT_TOPIC}")
    print(f"天平数量: {TOTAL_BALANCES}")
    if fast_mode:
        print("模式: 快速模式 (每5秒一轮)")
        interval = 5
    else:
        print(f"发布间隔: {PUBLISH_INTERVAL}秒 (1小时)")
        interval = PUBLISH_INTERVAL
    print("=" * 60)

    try:
        client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
        client.loop_start()
    except Exception as e:
        print(f"连接MQTT失败: {e}")
        print("请确保MQTT Broker已启动")
        return

    init_balance_data()
    print(f"\n已初始化 {TOTAL_BALANCES} 件天平数据")

    round_num = 0
    try:
        while True:
            round_num += 1
            print(f"\n[{datetime.now(CST).strftime('%Y-%m-%d %H:%M:%S')}] "
                  f"第 {round_num} 轮数据发布开始")

            start_time = time.time()
            success, fail = publish_all_balances(client)
            elapsed = time.time() - start_time

            print(f"第 {round_num} 轮完成: 成功 {success} 条, 失败 {fail} 条, "
                  f"耗时 {elapsed:.2f}秒")

            time.sleep(interval)

    except KeyboardInterrupt:
        print("\n\n模拟器已停止")
    finally:
        client.loop_stop()
        client.disconnect()


def run_single_publish():
    """单轮发布模式 - 用于测试"""
    client = mqtt.Client(client_id=MQTT_CLIENT_ID + "_single")
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)

    try:
        client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
    except Exception as e:
        print(f"连接MQTT失败: {e}")
        return

    init_balance_data()

    print(f"发布 {TOTAL_BALANCES} 条测量数据...")
    success, fail = publish_all_balances(client)
    print(f"完成: 成功 {success} 条, 失败 {fail} 条")

    client.disconnect()


def run_specific_balance(balance_code, count=10, interval=1):
    """发布特定天平的数据 - 用于测试"""
    client = mqtt.Client(client_id=MQTT_CLIENT_ID + "_specific")
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)

    try:
        client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
    except Exception as e:
        print(f"连接MQTT失败: {e}")
        return

    init_balance_data()

    if balance_code not in BALANCE_DATA:
        print(f"天平 {balance_code} 不存在")
        return

    print(f"发布天平 {balance_code} 的 {count} 条数据...")

    for i in range(count):
        measurement = generate_measurement(balance_code)
        payload = json.dumps(measurement, ensure_ascii=False)
        topic = f"{MQTT_TOPIC}/{balance_code}"
        client.publish(topic, payload, qos=1)
        print(f"  [{i+1}] 误差: {measurement['weighingError']:.6f}g, "
              f"相对误差: {measurement['relativeError']*100:.4f}%")
        time.sleep(interval)

    print("完成")
    client.disconnect()


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="古代天平模拟器")
    parser.add_argument("--mode", choices=["normal", "fast", "single", "balance"],
                        default=os.environ.get("SIM_MODE", "normal"),
                        help="运行模式: normal/fast/single/balance")
    parser.add_argument("--balance-code", help="特定天平编码(balance模式)")
    parser.add_argument("--count", type=int, default=10, help="发布条数")
    parser.add_argument("--interval", type=float, default=1.0, help="发布间隔(秒)")
    args = parser.parse_args()

    if args.mode == "single":
        run_single_publish()
    elif args.mode == "fast":
        run_simulation(fast_mode=True)
    elif args.mode == "balance" and args.balance_code:
        run_specific_balance(args.balance_code, args.count, args.interval)
    else:
        run_simulation(fast_mode=(args.mode == "fast"))
