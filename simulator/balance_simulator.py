#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
古代天平模拟器 - MQTT数据发布脚本
模拟100件古代天平每小时上报传感器数据
"""

import json
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

MQTT_BROKER = "localhost"
MQTT_PORT = 1883
MQTT_USERNAME = "admin"
MQTT_PASSWORD = "public"
MQTT_TOPIC = "balance/sensor/data"
MQTT_CLIENT_ID = "balance-simulator"

TOTAL_BALANCES = 100
PUBLISH_INTERVAL = 3600

CST = timezone(timedelta(hours=8))

DYNASTIES = [
    "战国", "秦", "西汉", "东汉", "三国", "西晋", "东晋", "南北朝",
    "隋", "唐", "五代十国", "北宋", "南宋", "元", "明", "清"
]

BALANCE_TYPES = ["EQUAL_ARM", "UNEQUAL_ARM"]

BALANCE_DATA = {}


def init_balance_data():
    """初始化100件天平的基础数据"""
    for i in range(1, TOTAL_BALANCES + 1):
        balance_code = f"BAL-{i:04d}"
        balance_type = BALANCE_TYPES[i % 3 == 0 and 1 or 0]

        base_left_arm = 150.0 + (i % 10) * 5.0 + random.uniform(-1, 1)
        if balance_type == "UNEQUAL_ARM":
            base_right_arm = base_left_arm + random.uniform(10, 30)
        else:
            base_right_arm = base_left_arm + random.uniform(-0.5, 0.5)

        base_knife_edge = 1.5 + random.uniform(0, 1.0)
        base_error_std = 0.003 + (i % 5) * 0.002

        BALANCE_DATA[balance_code] = {
            "id": i,
            "code": balance_code,
            "type": balance_type,
            "dynasty_index": i % 16,
            "base_left_arm": base_left_arm,
            "base_right_arm": base_right_arm,
            "base_knife_edge": base_knife_edge,
            "base_error_std": base_error_std,
            "knife_wear_accum": 0.0,
            "measurement_count": 0
        }


def generate_measurement(balance_code):
    """生成单次测量数据"""
    data = BALANCE_DATA[balance_code]

    nominal_mass = random.choice([1.0, 2.0, 5.0, 10.0, 20.0, 50.0])

    left_arm = data["base_left_arm"] + random.uniform(-0.1, 0.1)
    right_arm = data["base_right_arm"] + random.uniform(-0.1, 0.1)

    data["knife_wear_accum"] += random.uniform(0, 0.0001)
    knife_wear = data["base_knife_edge"] * 0.01 + data["knife_wear_accum"]
    knife_friction = 0.001 + knife_wear * 0.5 + random.uniform(-0.0002, 0.0002)

    arm_ratio_error = (left_arm - right_arm) / left_arm
    arm_error = nominal_mass * arm_ratio_error

    friction_error = knife_friction * nominal_mass * random.uniform(0.8, 1.2)

    weight_error = random.gauss(0, data["base_error_std"])

    total_error = weight_error + arm_error + friction_error
    measured_mass = nominal_mass + total_error

    relative_error = total_error / nominal_mass if nominal_mass != 0 else 0

    temperature = 20.0 + random.uniform(-5, 10)
    humidity = 40.0 + random.uniform(0, 40)

    data["measurement_count"] += 1

    measurement = {
        "balanceCode": balance_code,
        "timestamp": datetime.now(CST).isoformat(),
        "nominalMass": round(nominal_mass, 4),
        "measuredMass": round(measured_mass, 6),
        "weighingError": round(total_error, 6),
        "relativeError": round(relative_error, 8),
        "leftArmLength": round(left_arm, 4),
        "rightArmLength": round(right_arm, 4),
        "knifeEdgeWearDepth": round(knife_wear, 6),
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
    import sys

    if len(sys.argv) > 1:
        mode = sys.argv[1]

        if mode == "single":
            run_single_publish()
        elif mode == "fast":
            run_simulation(fast_mode=True)
        elif mode == "balance" and len(sys.argv) > 2:
            balance_code = sys.argv[2]
            count = int(sys.argv[3]) if len(sys.argv) > 3 else 10
            interval = float(sys.argv[4]) if len(sys.argv) > 4 else 1
            run_specific_balance(balance_code, count, interval)
        else:
            print("用法:")
            print("  python balance_simulator.py           # 正常模式 (每小时一轮)")
            print("  python balance_simulator.py fast      # 快速模式 (每5秒一轮)")
            print("  python balance_simulator.py single    # 单轮发布")
            print("  python balance_simulator.py balance <code> [count] [interval]")
            print("     例: python balance_simulator.py balance BAL-0001 10 1")
    else:
        run_simulation()
