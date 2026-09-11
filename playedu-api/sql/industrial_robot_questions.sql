-- 工业机器人课程题库：25 个知识点，每个知识点生成 4 道客观题，共 100 道。
-- 每题 10 分；脚本可重复执行，固定编码 IR-001-S 至 IR-025-M 不会重复插入。

START TRANSACTION;

INSERT INTO `exam_question_banks`
  (`name`, `description`, `status`, `practice_enabled`, `owner_id`)
SELECT
  '机器人题库',
  '工业机器人基础、安全、操作、编程、集成与维护课程题库',
  'enabled',
  1,
  (SELECT `id` FROM `admin_users` ORDER BY `id` LIMIT 1)
WHERE NOT EXISTS (
  SELECT 1 FROM `exam_question_banks` WHERE `name` = '机器人题库'
);

SET @robot_bank_id = (
  SELECT `id` FROM `exam_question_banks`
  WHERE `name` = '机器人题库'
  ORDER BY `id` LIMIT 1
);

SET @robot_owner_id = (
  SELECT `owner_id` FROM `exam_question_banks` WHERE `id` = @robot_bank_id
);

UPDATE `exam_question_banks`
SET `status` = 'enabled', `practice_enabled` = 1
WHERE `id` = @robot_bank_id;

DROP TEMPORARY TABLE IF EXISTS `industrial_robot_concepts`;
CREATE TEMPORARY TABLE `industrial_robot_concepts` (
  `seq` int NOT NULL PRIMARY KEY,
  `topic` varchar(100) NOT NULL,
  `correct_1` varchar(500) NOT NULL,
  `correct_2` varchar(500) NOT NULL,
  `wrong_1` varchar(500) NOT NULL,
  `wrong_2` varchar(500) NOT NULL,
  `analysis` varchar(1000) NOT NULL,
  `difficulty` varchar(20) NOT NULL
);

INSERT INTO `industrial_robot_concepts`
  (`seq`, `topic`, `correct_1`, `correct_2`, `wrong_1`, `wrong_2`, `analysis`, `difficulty`)
VALUES
  (1, '安全启动检查', '启动前应确认机器人工作区域内无人且无遗留工具', '应按作业规范穿戴防护用品并确认安全装置有效', '可以短接安全门开关以提高调试效率', '急停按钮可以代替所有正常停机操作', '启动前必须清场并检查安全装置；严禁旁路联锁，急停仅用于紧急危险处置。', 'easy'),
  (2, '机器人轴与自由度', '关节型机器人的各运动轴通常构成串联运动链', '常见六轴机器人可控制末端的位置和姿态', '机器人轴数越多，其额定负载一定越大', '六轴工业机器人的所有关节都是直线移动关节', '轴数描述独立运动能力，不直接决定负载；常见六轴关节机器人多采用旋转关节。', 'easy'),
  (3, '机器人坐标系', '基坐标系通常固定在机器人底座参考位置', '工具坐标系以末端工具中心点及其姿态为参考', '世界坐标系会随机器人每个关节的运动而改变', '用户坐标系只能在视觉应用中使用', '基坐标系、工具坐标系和用户坐标系用于不同的定位与编程场景。', 'medium'),
  (4, '工具中心点 TCP', 'TCP 是机器人末端工具执行工作的参考点', '准确标定 TCP 有助于提高轨迹和定位精度', 'TCP 必须与机器人腕部法兰中心完全重合', '更换工具后无需检查 TCP 是否变化', 'TCP 可位于工具尖端等实际工作点；更换或碰撞工具后应检查并重新标定。', 'medium'),
  (5, '示教器操作', '示教器可用于点动、程序编辑和调试', '三位置使能开关通常只有在中间位置时允许手动运动', '示教模式下可以不受限制地使用生产最高速度', '按下急停后程序故障会自动全部清除', '示教操作应使用安全速度和使能装置，急停后仍需排查原因并按流程复位。', 'easy'),
  (6, '伺服系统', '伺服控制通常利用位置、速度或转矩反馈形成闭环', '垂直轴制动器主要用于断电或伺服关闭时保持负载', '伺服关闭后电机仍会持续输出正常控制转矩', '电机电流与输出转矩完全无关', '伺服系统依靠反馈控制运动，电流通常与转矩相关，制动器不替代正常动态控制。', 'medium'),
  (7, '编码器反馈', '编码器为控制器提供电机或关节的位置反馈', '绝对值编码器通常能保留绝对位置信息', '增量式编码器在任何情况下都不需要建立参考位置', '编码器主要用于测量末端夹持力', '编码器用于运动位置反馈；增量式系统通常需要回零，夹持力应由力或压力传感器测量。', 'medium'),
  (8, '机器人减速器', '减速器可降低电机输出速度并提高关节输出转矩', '减速器回差会影响机器人的定位和重复定位性能', '提高减速比可以无限提高机器人运行速度', '工业机器人减速器终身不需要检查润滑状态', '减速器用于速度和转矩匹配，其回差、磨损与润滑状态都会影响机器人性能。', 'medium'),
  (9, '负载与惯量', '机器人负载计算应包含工具和被搬运工件的质量', '负载重心和转动惯量会影响允许的速度与加速度', '选择机器人时只需比较工件质量，无需考虑工具', '超过额定负载运行能够提高节拍且不会影响寿命', '选型和参数设置必须综合质量、重心和惯量，超载会带来安全与寿命风险。', 'medium'),
  (10, '工作空间与臂展', '机器人工作空间受连杆尺寸和关节行程限制', '最大臂展表示 TCP 在特定条件下可达到的最远距离', '工作空间内的每个位置都能以任意姿态到达', '机器人臂展数值与额定负载数值必然相等', '位置可达不等于所有姿态可达，工作空间还会受到奇异点、工具和现场障碍物影响。', 'medium'),
  (11, '机器人奇异点', '接近奇异点时可能出现关节速度急剧增大的现象', '可通过轨迹规划和姿态调整降低奇异点风险', '奇异点表示机器人的碰撞传感器发生故障', '增加机器人负载可以消除所有奇异点', '奇异点源于机器人运动学结构，可能导致自由度退化或关节速度异常。', 'hard'),
  (12, '机器人标定', '工具坐标和用户坐标标定会影响程序点位解释', '机器人发生碰撞或工具变形后应检查相关标定', '标定操作的主要作用是更换机器人编程语言', '标定误差不会影响离线编程程序的落地精度', '准确标定是示教、离线编程和视觉引导正确转换坐标的基础。', 'medium'),
  (13, '数字量 I/O', '传感器状态通常通过输入信号传给机器人控制器', '机器人输出信号可用于与夹具或外围设备进行联锁', '机器人 24V 数字输出可直接驱动任意功率电机', '输入信号抖动永远不会影响程序逻辑', 'I/O 应注意电气容量、隔离、去抖和联锁逻辑，大功率负载需经继电器或驱动器控制。', 'easy'),
  (14, '机器人与 PLC 握手', '请求与应答信号可以确认双方动作已经接受和完成', '握手程序应设计状态校验、超时和异常复位', '机器人和 PLC 应同时驱动同一个物理输出点', '握手逻辑无需超时处理，等待时间越长越安全', '可靠握手应明确每个信号的唯一输出方，并处理超时、掉电和异常状态。', 'medium'),
  (15, '工业现场总线', 'PROFINET、EtherCAT 等网络可用于设备间周期数据交换', '现场总线诊断信息有助于定位通信和节点故障', '采用现场总线后可以取消所有机械安全防护', '同一网络内所有设备应配置完全相同的 IP 地址', '工业网络负责数据交换，不替代安全系统；网络地址必须按规划保持唯一。', 'medium'),
  (16, '机器视觉引导', '手眼标定用于建立相机坐标与机器人坐标之间的关系', '照明稳定性会显著影响视觉检测结果', '图像像素坐标可以不经标定直接作为机器人毫米坐标', '工业镜头在任何情况下都不存在畸变', '视觉引导需要完成成像、标定和坐标转换，并控制光照及镜头畸变影响。', 'hard'),
  (17, '末端夹具选型', '夹具选型应匹配工件尺寸、质量和允许夹持力', '可使用传感器确认夹具开闭或工件到位状态', '夹持力越大越好，不会损伤任何工件', '气动夹具在气源压力不足时仍能保持全部额定性能', '夹具必须兼顾安全系数、工件保护、失压状态和抓取确认。', 'medium'),
  (18, '机器人焊接', '焊接 TCP、焊枪姿态和运行速度会影响焊缝质量', '焊丝、保护气体和工艺参数应与焊接任务匹配', '焊接作业可以忽略烟尘治理和弧光防护', '空运行检查轨迹时应始终保持焊接电弧开启', '焊接程序需先安全空运行验证，并正确配置轨迹、姿态和焊接工艺参数。', 'medium'),
  (19, '码垛应用', '码垛程序应定义垛型、层数、偏移和安全接近点', '码垛节拍设置应考虑负载、惯量和机器人性能限制', '为缩短路径可从任意位置直接垂直插入垛位', '码垛单元无需与输送线和夹具进行信号联锁', '码垛轨迹应设置接近与离开路径，并与输送、抓取和安全设备协调。', 'medium'),
  (20, '预防性维护', '应按维护计划检查电缆、紧固件、润滑和异常噪声', '程序与系统参数备份有助于故障后的快速恢复', '维护控制柜时应保持设备带电并绕过上锁挂牌', '出现重复报警时只需无限次复位，无需分析原因', '预防性维护包括检查、润滑、备份和趋势分析，电气维护应执行断电和上锁挂牌。', 'easy'),
  (21, '急停与安全复位', '急停用于出现人员或设备紧急危险时快速停止', '急停释放后仍应确认现场安全并执行受控复位', '急停按钮适合作为每个生产循环的常规停止按钮', '急停释放后机器人应无需确认自动恢复运动', '急停属于紧急保护措施，复位不能直接导致危险运动，正常停机应使用正常控制流程。', 'easy'),
  (22, '安全围栏与联锁', '安全围栏用于限制人员进入机器人的危险区域', '安全门联锁与安全距离应依据风险评估进行设计', '安装安全围栏后可以取消急停和其他安全功能', '打开联锁门时机器人应保持原速度继续自动运行', '围栏、门锁、急停和安全控制共同构成防护体系，任何单一措施都不能替代完整风险控制。', 'easy'),
  (23, '重复定位精度与绝对精度', '重复定位精度描述机器人多次返回同一点时的离散程度', '绝对定位精度描述实际位置与指令位置之间的偏差', '重复定位精度与绝对定位精度是完全相同的指标', '重复定位精度高就必然不需要任何标定补偿', '重复定位性能好不代表绝对位置误差小；离线编程尤其关注绝对精度和标定。', 'hard'),
  (24, '轨迹类型与平滑', '关节运动通常按关节空间规划，直线运动约束 TCP 沿直线移动', '合理使用转角过渡可减少不必要的停顿并改善节拍', '直线运动能够保证所有关节始终保持相同速度', '加速度设置越高，机器人振动一定越小', '运动类型应根据工艺选择，速度、加速度和转角过渡需兼顾轨迹、节拍及机械振动。', 'medium'),
  (25, '机器人程序设计', '机器人程序通常包含运动指令、I/O 逻辑和异常处理', '新程序应先在低速、单步或空运行条件下验证', '未经验证的新程序可以直接以自动全速运行', '将所有点位和参数硬编码且不加说明最利于维护', '良好的机器人程序应结构清晰、参数化、可诊断，并经过安全的分阶段验证。', 'medium');

DROP TEMPORARY TABLE IF EXISTS `industrial_robot_questions_seed`;
CREATE TEMPORARY TABLE `industrial_robot_questions_seed` (
  `code` varchar(64) NOT NULL PRIMARY KEY,
  `type` varchar(30) NOT NULL,
  `difficulty` varchar(20) NOT NULL,
  `stem` varchar(10000) NOT NULL,
  `options_json` json NOT NULL,
  `answer_json` json NOT NULL,
  `analysis` varchar(1000) NOT NULL,
  `tags_json` json NOT NULL
);

INSERT INTO `industrial_robot_questions_seed`
SELECT
  CONCAT('IR-', LPAD(`seq`, 3, '0'), '-S') AS `code`,
  'single_choice' AS `type`,
  `difficulty`,
  CONCAT('关于“', `topic`, '”，下列说法正确的是？') AS `stem`,
  JSON_ARRAY(
    JSON_OBJECT('id', 'A', 'text', `correct_1`),
    JSON_OBJECT('id', 'B', 'text', `wrong_1`),
    JSON_OBJECT('id', 'C', 'text', `wrong_2`),
    JSON_OBJECT('id', 'D', 'text', '以上说法均不正确')
  ) AS `options_json`,
  JSON_OBJECT('optionIds', JSON_ARRAY('A')) AS `answer_json`,
  `analysis`,
  JSON_ARRAY('工业机器人', `topic`) AS `tags_json`
FROM `industrial_robot_concepts`;

INSERT INTO `industrial_robot_questions_seed`
SELECT
  CONCAT('IR-', LPAD(`seq`, 3, '0'), '-T1'),
  'true_false',
  `difficulty`,
  CONCAT('判断题：', `correct_1`, '。'),
  JSON_ARRAY(),
  JSON_OBJECT('value', TRUE),
  `analysis`,
  JSON_ARRAY('工业机器人', `topic`)
FROM `industrial_robot_concepts`;

INSERT INTO `industrial_robot_questions_seed`
SELECT
  CONCAT('IR-', LPAD(`seq`, 3, '0'), '-T2'),
  'true_false',
  `difficulty`,
  CONCAT('判断题：', `wrong_1`, '。'),
  JSON_ARRAY(),
  JSON_OBJECT('value', FALSE),
  `analysis`,
  JSON_ARRAY('工业机器人', `topic`)
FROM `industrial_robot_concepts`;

INSERT INTO `industrial_robot_questions_seed`
SELECT
  CONCAT('IR-', LPAD(`seq`, 3, '0'), '-M'),
  'multiple_choice',
  `difficulty`,
  CONCAT('关于“', `topic`, '”，下列说法正确的有哪两项？'),
  JSON_ARRAY(
    JSON_OBJECT('id', 'A', 'text', `correct_1`),
    JSON_OBJECT('id', 'B', 'text', `correct_2`),
    JSON_OBJECT('id', 'C', 'text', `wrong_1`),
    JSON_OBJECT('id', 'D', 'text', `wrong_2`)
  ),
  JSON_OBJECT('optionIds', JSON_ARRAY('A', 'B')),
  `analysis`,
  JSON_ARRAY('工业机器人', `topic`)
FROM `industrial_robot_concepts`;

INSERT INTO `exam_questions`
  (`bank_id`, `category_id`, `code`, `type`, `difficulty`, `stem`, `tags_json`, `status`, `current_version`)
SELECT
  @robot_bank_id,
  NULL,
  s.`code`,
  s.`type`,
  s.`difficulty`,
  s.`stem`,
  CAST(s.`tags_json` AS CHAR),
  'enabled',
  1
FROM `industrial_robot_questions_seed` s
WHERE NOT EXISTS (
  SELECT 1 FROM `exam_questions` q
  WHERE q.`bank_id` = @robot_bank_id AND q.`code` = s.`code`
);

INSERT INTO `exam_question_versions`
  (`question_id`, `version_no`, `content_json`, `created_by`)
SELECT
  q.`id`,
  1,
  JSON_OBJECT(
    'id', q.`id`,
    'expectedVersion', 1,
    'bankId', @robot_bank_id,
    'categoryId', NULL,
    'code', s.`code`,
    'type', s.`type`,
    'difficulty', s.`difficulty`,
    'stem', s.`stem`,
    'options', s.`options_json`,
    'standardAnswer', s.`answer_json`,
    'gradingRule', JSON_OBJECT('strategy', 'exact_match'),
    'suggestedScore', 10.00,
    'analysis', s.`analysis`,
    'tags', s.`tags_json`,
    'status', 'enabled'
  ),
  @robot_owner_id
FROM `industrial_robot_questions_seed` s
JOIN `exam_questions` q
  ON q.`bank_id` = @robot_bank_id AND q.`code` = s.`code`
WHERE NOT EXISTS (
  SELECT 1 FROM `exam_question_versions` v
  WHERE v.`question_id` = q.`id` AND v.`version_no` = 1
);

DROP TEMPORARY TABLE IF EXISTS `industrial_robot_questions_seed`;
DROP TEMPORARY TABLE IF EXISTS `industrial_robot_concepts`;

COMMIT;

SELECT
  b.`id` AS `bank_id`,
  b.`name` AS `bank_name`,
  COUNT(q.`id`) AS `question_count`,
  MIN(JSON_EXTRACT(v.`content_json`, '$.suggestedScore')) AS `min_score`,
  MAX(JSON_EXTRACT(v.`content_json`, '$.suggestedScore')) AS `max_score`
FROM `exam_question_banks` b
JOIN `exam_questions` q ON q.`bank_id` = b.`id`
JOIN `exam_question_versions` v
  ON v.`question_id` = q.`id` AND v.`version_no` = q.`current_version`
WHERE b.`id` = @robot_bank_id
GROUP BY b.`id`, b.`name`;
