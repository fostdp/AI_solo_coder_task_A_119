-- ============================================================
-- 古代天平衡器精度检定与误差分析系统 - 数据库初始化脚本
-- ============================================================

-- 创建数据库
-- CREATE DATABASE balance_db WITH ENCODING 'UTF8';

-- 连接到数据库
-- \c balance_db;

-- ============================================================
-- 扩展
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- 朝代字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS dynasties (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    start_year INTEGER,
    end_year INTEGER,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 天平表
-- ============================================================
CREATE TABLE IF NOT EXISTS balances (
    id SERIAL PRIMARY KEY,
    balance_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    dynasty_id INTEGER REFERENCES dynasties(id),
    balance_type VARCHAR(20) NOT NULL CHECK (balance_type IN ('EQUAL_ARM', 'UNEQUAL_ARM')),
    max_capacity NUMERIC(10, 2),
    left_arm_length NUMERIC(10, 4),
    right_arm_length NUMERIC(10, 4),
    knife_edge_radius NUMERIC(10, 6),
    discovery_location VARCHAR(200),
    discovery_year INTEGER,
    material VARCHAR(50),
    description TEXT,
    accuracy_grade VARCHAR(20),
    allowable_error NUMERIC(10, 4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 砝码表
-- ============================================================
CREATE TABLE IF NOT EXISTS weights (
    id SERIAL PRIMARY KEY,
    weight_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100),
    dynasty_id INTEGER REFERENCES dynasties(id),
    nominal_mass NUMERIC(10, 4),
    actual_mass NUMERIC(10, 6),
    material VARCHAR(50),
    discovery_location VARCHAR(200),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 天平测量记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS balance_measurements (
    id BIGSERIAL PRIMARY KEY,
    balance_id INTEGER NOT NULL REFERENCES balances(id),
    measurement_time TIMESTAMP NOT NULL,
    weight_id INTEGER REFERENCES weights(id),
    nominal_mass NUMERIC(10, 4),
    measured_mass NUMERIC(10, 6),
    weighing_error NUMERIC(10, 6),
    relative_error NUMERIC(12, 8),
    left_arm_length NUMERIC(10, 4),
    right_arm_length NUMERIC(10, 4),
    knife_edge_wear_depth NUMERIC(10, 6),
    knife_edge_friction NUMERIC(10, 6),
    temperature NUMERIC(6, 2),
    humidity NUMERIC(6, 2),
    is_alert BOOLEAN DEFAULT FALSE,
    alert_level VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_balance_measurements_balance_id ON balance_measurements(balance_id);
CREATE INDEX IF NOT EXISTS idx_balance_measurements_time ON balance_measurements(measurement_time);
CREATE INDEX IF NOT EXISTS idx_balance_measurements_alert ON balance_measurements(is_alert) WHERE is_alert = TRUE;
CREATE INDEX IF NOT EXISTS idx_balance_measurements_balance_time ON balance_measurements(balance_id, measurement_time DESC);
CREATE INDEX IF NOT EXISTS idx_balance_measurements_wear ON balance_measurements(balance_id, knife_edge_wear_depth DESC);
CREATE INDEX IF NOT EXISTS idx_balance_measurements_friction ON balance_measurements(balance_id, knife_edge_friction);

-- ============================================================
-- 误差分析结果表
-- ============================================================
CREATE TABLE IF NOT EXISTS error_analyses (
    id SERIAL PRIMARY KEY,
    balance_id INTEGER NOT NULL REFERENCES balances(id),
    analysis_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    simulation_count INTEGER NOT NULL,
    mean_error NUMERIC(12, 8),
    std_deviation NUMERIC(12, 8),
    combined_uncertainty NUMERIC(12, 8),
    expanded_uncertainty NUMERIC(12, 8),
    coverage_factor NUMERIC(4, 2) DEFAULT 2.0,
    friction_contribution NUMERIC(8, 4),
    arm_length_contribution NUMERIC(8, 4),
    weight_contribution NUMERIC(8, 4),
    accuracy_grade VARCHAR(20),
    raw_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_error_analyses_balance_id ON error_analyses(balance_id);
CREATE INDEX IF NOT EXISTS idx_error_analyses_time ON error_analyses(analysis_time DESC);

-- ============================================================
-- 权衡制度分析表
-- ============================================================
CREATE TABLE IF NOT EXISTS weight_system_analyses (
    id SERIAL PRIMARY KEY,
    dynasty_id INTEGER REFERENCES dynasties(id),
    analysis_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sample_count INTEGER,
    jin_standard NUMERIC(10, 4),
    liang_standard NUMERIC(10, 6),
    cluster_count INTEGER,
    silhouette_score NUMERIC(8, 6),
    clusters JSONB,
    method VARCHAR(50) DEFAULT 'K_MEANS',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 告警记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    balance_id INTEGER REFERENCES balances(id),
    measurement_id BIGINT REFERENCES balance_measurements(id),
    alert_type VARCHAR(50) NOT NULL,
    alert_level VARCHAR(20) NOT NULL CHECK (alert_level IN ('INFO', 'WARNING', 'CRITICAL')),
    message TEXT NOT NULL,
    threshold_value NUMERIC(12, 6),
    actual_value NUMERIC(12, 6),
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alerts_balance_id ON alerts(balance_id);
CREATE INDEX IF NOT EXISTS idx_alerts_resolved ON alerts(is_resolved) WHERE is_resolved = FALSE;
CREATE INDEX IF NOT EXISTS idx_alerts_level_time ON alerts(alert_level, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_created ON alerts(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_weights_dynasty ON weights(dynasty_id);
CREATE INDEX IF NOT EXISTS idx_weights_actual_mass ON weights(actual_mass);

CREATE INDEX IF NOT EXISTS idx_balances_dynasty ON balances(dynasty_id);
CREATE INDEX IF NOT EXISTS idx_balances_type ON balances(balance_type);
CREATE INDEX IF NOT EXISTS idx_balances_material ON balances(material);

CREATE INDEX IF NOT EXISTS idx_weight_system_dynasty ON weight_system_analyses(dynasty_id);

-- ============================================================
-- 初始化数据 - 朝代
-- ============================================================
INSERT INTO dynasties (name, start_year, end_year, description) VALUES
('战国', -475, -221, '战国时期，各国度量衡不统一'),
('秦', -221, -206, '秦朝统一度量衡'),
('西汉', -202, 8, '西汉度量衡制度'),
('东汉', 25, 220, '东汉度量衡制度'),
('三国', 220, 280, '三国时期'),
('西晋', 265, 316, '西晋时期'),
('东晋', 317, 420, '东晋时期'),
('南北朝', 420, 589, '南北朝时期'),
('隋', 581, 618, '隋朝统一度量衡'),
('唐', 618, 907, '唐朝度量衡制度完备'),
('五代十国', 907, 979, '五代十国时期'),
('北宋', 960, 1127, '北宋时期'),
('南宋', 1127, 1279, '南宋时期'),
('元', 1271, 1368, '元朝时期'),
('明', 1368, 1644, '明朝度量衡制度'),
('清', 1644, 1912, '清朝度量衡制度')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 初始化数据 - 古代天平（100件模拟数据）
-- ============================================================
INSERT INTO balances (balance_code, name, dynasty_id, balance_type, max_capacity, 
    left_arm_length, right_arm_length, knife_edge_radius, discovery_location, 
    material, description, accuracy_grade, allowable_error)
SELECT 
    'BAL-' || LPAD(seq::text, 4, '0'),
    CASE (seq % 6)
        WHEN 0 THEN '青铜等臂天平'
        WHEN 1 THEN '铁制不等臂天平'
        WHEN 2 THEN '玉石等臂天平'
        WHEN 3 THEN '木梁铜权天平'
        WHEN 4 THEN '象牙杆等臂天平'
        ELSE '竹制不等臂天平'
    END || ' 第' || seq || '号',
    (seq % 16) + 1 as dynasty_id,
    CASE WHEN seq % 3 = 0 THEN 'UNEQUAL_ARM' ELSE 'EQUAL_ARM' END,
    ROUND(50 + (seq % 20) * 25.0, 2),
    ROUND(150.0 + (seq % 10) * 5.0 + random() * 2, 4),
    ROUND(150.0 + (seq % 10) * 5.0 + (CASE WHEN seq % 3 = 0 THEN random() * 20 - 10 ELSE random() * 1 - 0.5 END), 4),
    ROUND(1.5 + random() * 1.0, 6),
    CASE (seq % 8)
        WHEN 0 THEN '河南安阳'
        WHEN 1 THEN '陕西西安'
        WHEN 2 THEN '湖南长沙'
        WHEN 3 THEN '湖北江陵'
        WHEN 4 THEN '山东临沂'
        WHEN 5 THEN '江苏徐州'
        WHEN 6 THEN '四川成都'
        ELSE '山西太原'
    END,
    CASE (seq % 4)
        WHEN 0 THEN '青铜'
        WHEN 1 THEN '铁'
        WHEN 2 THEN '玉石'
        ELSE '木'
    END,
    '出土于' || CASE (seq % 8)
        WHEN 0 THEN '河南安阳'
        WHEN 1 THEN '陕西西安'
        WHEN 2 THEN '湖南长沙'
        WHEN 3 THEN '湖北江陵'
        WHEN 4 THEN '山东临沂'
        WHEN 5 THEN '江苏徐州'
        WHEN 6 THEN '四川成都'
        ELSE '山西太原'
    END || '的古代天平，保存' || CASE WHEN seq % 3 = 0 THEN '完好' WHEN seq % 3 = 1 THEN '一般' ELSE '残损' END,
    CASE 
        WHEN seq % 5 = 0 THEN '一级'
        WHEN seq % 5 = 1 THEN '二级'
        WHEN seq % 5 = 2 THEN '三级'
        ELSE '等外'
    END,
    ROUND(CASE 
        WHEN seq % 5 = 0 THEN 0.001
        WHEN seq % 5 = 1 THEN 0.005
        WHEN seq % 5 = 2 THEN 0.01
        ELSE 0.05
    END, 6)
FROM generate_series(1, 100) seq
ON CONFLICT (balance_code) DO NOTHING;

-- ============================================================
-- 初始化数据 - 古代砝码（模拟出土砝码）
-- ============================================================
INSERT INTO weights (weight_code, name, dynasty_id, nominal_mass, actual_mass, 
    material, discovery_location, description)
SELECT 
    'WGT-' || LPAD(seq::text, 4, '0'),
    CASE (seq % 5)
        WHEN 0 THEN '铜权'
        WHEN 1 THEN '铁权'
        WHEN 2 THEN '石权'
        WHEN 3 THEN '金权'
        ELSE '银权'
    END || ' 第' || seq || '号',
    (seq % 16) + 1 as dynasty_id,
    ROUND(1.0 * (seq % 10 + 1), 4),
    ROUND(1.0 * (seq % 10 + 1) + (random() * 0.1 - 0.05), 6),
    CASE (seq % 4)
        WHEN 0 THEN '青铜'
        WHEN 1 THEN '铁'
        WHEN 2 THEN '石头'
        ELSE '金'
    END,
    CASE (seq % 8)
        WHEN 0 THEN '河南安阳殷墟'
        WHEN 1 THEN '陕西西安兵马俑'
        WHEN 2 THEN '湖南长沙马王堆'
        WHEN 3 THEN '湖北江陵望山'
        WHEN 4 THEN '山东临沂银雀山'
        WHEN 5 THEN '江苏徐州楚王墓'
        WHEN 6 THEN '四川成都金沙'
        ELSE '山西太原晋侯墓'
    END,
    '出土砝码，标称质量' || (seq % 10 + 1) || '两'
FROM generate_series(1, 80) seq
ON CONFLICT (weight_code) DO NOTHING;

-- ============================================================
-- 初始化数据 - 历史测量记录（模拟数据）
-- ============================================================
INSERT INTO balance_measurements (balance_id, measurement_time, weight_id, 
    nominal_mass, measured_mass, weighing_error, relative_error,
    left_arm_length, right_arm_length, knife_edge_wear_depth, knife_edge_friction,
    temperature, humidity, is_alert, alert_level)
SELECT 
    (seq % 100) + 1 as balance_id,
    NOW() - (random() * interval '7 days') as measurement_time,
    (seq % 80) + 1 as weight_id,
    ROUND(1.0 * ((seq % 10) + 1), 4) as nominal_mass,
    ROUND(1.0 * ((seq % 10) + 1) + (random() * 0.02 - 0.01), 6) as measured_mass,
    ROUND(random() * 0.02 - 0.01, 6) as weighing_error,
    ROUND((random() * 0.002 - 0.001), 8) as relative_error,
    ROUND(150.0 + random() * 10, 4) as left_arm_length,
    ROUND(150.0 + random() * 10, 4) as right_arm_length,
    ROUND(random() * 0.05, 6) as knife_edge_wear_depth,
    ROUND(random() * 0.002, 6) as knife_edge_friction,
    ROUND(20.0 + random() * 10, 2) as temperature,
    ROUND(40.0 + random() * 30, 2) as humidity,
    CASE WHEN random() > 0.9 THEN TRUE ELSE FALSE END as is_alert,
    CASE 
        WHEN random() > 0.95 THEN 'CRITICAL'
        WHEN random() > 0.9 THEN 'WARNING'
        ELSE NULL 
    END as alert_level
FROM generate_series(1, 5000) seq;

-- ============================================================
-- 视图: 天平最新状态视图
-- ============================================================
CREATE OR REPLACE VIEW v_balance_latest_status AS
SELECT DISTINCT ON (b.id)
    b.id,
    b.balance_code,
    b.name,
    d.name as dynasty_name,
    b.balance_type,
    b.max_capacity,
    b.accuracy_grade,
    b.allowable_error,
    m.measurement_time as last_measurement_time,
    m.weighing_error as last_error,
    m.relative_error as last_relative_error,
    m.is_alert,
    m.alert_level
FROM balances b
LEFT JOIN dynasties d ON b.dynasty_id = d.id
LEFT JOIN balance_measurements m ON b.id = m.balance_id
ORDER BY b.id, m.measurement_time DESC;

-- ============================================================
-- 函数: 计算精度等级
-- ============================================================
CREATE OR REPLACE FUNCTION calculate_accuracy_grade(p_relative_error NUMERIC)
RETURNS VARCHAR(20) AS $$
BEGIN
    IF p_relative_error IS NULL THEN
        RETURN '未知';
    END IF;
    
    IF ABS(p_relative_error) <= 0.00001 THEN
        RETURN '特级';
    ELSIF ABS(p_relative_error) <= 0.0001 THEN
        RETURN '一级';
    ELSIF ABS(p_relative_error) <= 0.001 THEN
        RETURN '二级';
    ELSIF ABS(p_relative_error) <= 0.01 THEN
        RETURN '三级';
    ELSE
        RETURN '等外';
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 函数: 误差分析 - 简单版本
-- ============================================================
CREATE OR REPLACE FUNCTION analyze_balance_error(p_balance_id INTEGER)
RETURNS JSONB AS $$
DECLARE
    v_mean_error NUMERIC;
    v_std_dev NUMERIC;
    v_count INTEGER;
    v_result JSONB;
BEGIN
    SELECT 
        AVG(weighing_error),
        STDDEV(weighing_error),
        COUNT(*)
    INTO v_mean_error, v_std_dev, v_count
    FROM balance_measurements
    WHERE balance_id = p_balance_id;
    
    v_result := jsonb_build_object(
        'balance_id', p_balance_id,
        'sample_count', v_count,
        'mean_error', v_mean_error,
        'std_deviation', v_std_dev,
        'combined_uncertainty', v_std_dev,
        'expanded_uncertainty', v_std_dev * 2.0,
        'accuracy_grade', calculate_accuracy_grade(
            CASE WHEN v_mean_error IS NOT NULL THEN v_mean_error / 10.0 ELSE NULL END
        )
    );
    
    RETURN v_result;
END;
$$ LANGUAGE plpgsql;
