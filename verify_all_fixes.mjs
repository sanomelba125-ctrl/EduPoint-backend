// 全套修复端到端真实验证套件 (Node 22 原生 fetch + WebSocket)
import { execSync } from 'child_process';

const BASE_URL = 'http://localhost:8080/api';
const WS_BASE_URL = 'ws://localhost:8080/api';

function logSection(title) {
  console.log('\n' + '='.repeat(65));
  console.log(`  ${title}`);
  console.log('='.repeat(65));
}

function assert(condition, desc) {
  if (condition) {
    console.log(`  [PASS] ${desc}`);
  } else {
    console.error(`  [FAIL] ${desc}`);
    throw new Error(`Assertion failed: ${desc}`);
  }
}

function execSql(query) {
  const cmd = `docker exec campus-mysql mysql -uroot -prootpassword campus_activity -N -e "${query}"`;
  return execSync(cmd).toString().trim();
}

function getRedisVal(key) {
  const cmd = `docker exec campus-redis redis-cli -a redispassword GET "${key}"`;
  const out = execSync(cmd).toString();
  return out.split('\n').filter(l => !l.includes('Warning:')).join('').trim();
}

function generateUuid() {
  const hex = (len) => Array.from({ length: len }, () => Math.floor(Math.random() * 16).toString(16)).join('');
  return `${hex(8)}-${hex(4)}-4${hex(3)}-a${hex(3)}-${hex(12)}`;
}

// WebSocket 连接测试帮助函数
function testWsConnect(url, protocols, timeout = 3000) {
  return new Promise((resolve) => {
    let ws;
    let timer;
    try {
      ws = protocols ? new WebSocket(url, protocols) : new WebSocket(url);
    } catch (e) {
      return resolve({ success: false, error: e.message });
    }

    timer = setTimeout(() => {
      try { ws.close(); } catch (e) {}
      resolve({ success: false, error: 'TIMEOUT' });
    }, timeout);

    ws.onopen = () => {
      clearTimeout(timer);
      const subprotocol = ws.protocol;
      ws.close();
      resolve({ success: true, protocol: subprotocol });
    };

    ws.onerror = (err) => {
      clearTimeout(timer);
      resolve({ success: false, error: 'CONNECTION_REJECTED' });
    };

    ws.onclose = () => {
      clearTimeout(timer);
    };
  });
}

async function main() {
  console.log('开始执行三项核心修复全场景端到端真实验证...');

  // ==========================================
  // 1. 认证登录与上下文准备
  // ==========================================
  logSection('准备工作：获取管理员与学生会话凭据');

  // 1.1 管理员登录
  const adminRes = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123' })
  });
  const adminJson = await adminRes.json();
  assert(adminJson.code === 200, '管理员登录成功');
  const adminToken = adminJson.data.token;
  const adminUserId = adminJson.data.userInfo.id;
  console.log(`    管理员 ID: ${adminUserId}, Token: ${adminToken.slice(0, 8)}...`);

  // 1.2 学生登录
  const studentRes = await fetch(`${BASE_URL}/student/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ studentNo: '20220101', password: '123456' })
  });
  const studentJson = await studentRes.json();
  assert(studentJson.code === 200, '学生登录成功');
  const studentToken = studentJson.data.token;
  const studentId = studentJson.data.student.id;
  console.log(`    学生 ID: ${studentId}, 姓名: ${studentJson.data.student.name}, 初始积分: ${studentJson.data.student.currentPoints}`);
  // 为保证全套并发与压力测试积分充足，统一重置测试学生积分至 1000
  execSql(`UPDATE student SET current_points = 1000 WHERE id = ${studentId}`);
  let currentPoints = parseInt(execSql(`SELECT current_points FROM student WHERE id = ${studentId}`));
  console.log(`    已重置测试学生积分余额为: ${currentPoints}`);

  // ==========================================
  // 2. 验证修复①：WebSocket 握手鉴权全场景
  // ==========================================
  logSection('验证修复①：WebSocket 握手鉴权全场景');

  // 2.1 无凭据请求握手
  console.log('  -> 2.1 测试无任何凭据连接...');
  const resNoAuth = await testWsConnect(`${WS_BASE_URL}/ws/admin/1`);
  assert(!resNoAuth.success, '无凭据连接必须握手失败');

  // 2.2 伪造凭据握手
  console.log('  -> 2.2 测试伪造 token 连接...');
  const resFakeToken = await testWsConnect(`${WS_BASE_URL}/ws/admin/1`, ['satoken', 'fake-token-xyz-123456']);
  assert(!resFakeToken.success, '伪造 token 连接必须握手失败');

  // 2.3 越权测试 A：拿学生 token 试图连接管理员通道
  console.log('  -> 2.3 越权测试: 学生 token 试图连接管理员通道...');
  const resStudentToAdmin = await testWsConnect(`${WS_BASE_URL}/ws/admin/1`, ['satoken', studentToken]);
  assert(!resStudentToAdmin.success, '学生 token 连接管理员端点必须被拒');

  // 2.4 越权测试 B：拿学生 1 的 token 试图连接其他学生(99999)通道
  console.log('  -> 2.4 越权测试: 学生 token 试图连接他人通道...');
  const resStudentToOther = await testWsConnect(`${WS_BASE_URL}/ws/student/99999`, ['satoken', studentToken]);
  assert(!resStudentToOther.success, '学生 token 连接他人端点必须被拒');

  // 2.5 正常管理员 WebSocket 握手
  console.log('  -> 2.5 正常连接: 管理员带有效 token 连接本人通道...');
  const resAdminOk = await testWsConnect(`${WS_BASE_URL}/ws/admin/${adminUserId}`, ['satoken', adminToken]);
  assert(resAdminOk.success, '管理员携带合法 token 必须成功握手连接');
  assert(resAdminOk.protocol === 'satoken', '协商回选的子协议必须为 satoken (不泄漏 token)');

  // 2.6 正常学生 WebSocket 握手
  console.log('  -> 2.6 正常连接: 学生带有效 token 连接本人通道...');
  const resStudentOk = await testWsConnect(`${WS_BASE_URL}/ws/student/${studentId}`, ['satoken', studentToken]);
  assert(resStudentOk.success, '学生携带合法 token 必须成功握手连接');
  assert(resStudentOk.protocol === 'satoken', '协商回选的子协议必须为 satoken');

  // ==========================================
  // 3. 验证修复②：兑换幂等与最优解平滑兼容
  // ==========================================
  logSection('验证修复②：兑换请求幂等与最优解平滑兼容');

  // 3.1 正常兑换（带客户端 requestId）
  console.log('  -> 3.1 正常兑换测试: 携带合法 requestId...');
  const reqIdNormal = generateUuid();
  const pointsBefore31 = parseInt(execSql(`SELECT current_points FROM student WHERE id = ${studentId}`));
  const stockBefore31 = parseInt(execSql(`SELECT stock FROM gift WHERE id = 1`));

  const exchRes1 = await fetch(`${BASE_URL}/gift/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'satoken': studentToken },
    body: JSON.stringify({
      requestId: reqIdNormal,
      giftId: 1,
      quantity: 1,
      receiverName: '测试学生',
      receiverPhone: '13911112222',
      receiverAddress: '创客中心'
    })
  });
  const exchJson1 = await exchRes1.json();
  assert(exchJson1.code === 200, `首次兑换成功 (订单号: ${exchJson1.data?.orderNo})`);
  const firstOrderNo = exchJson1.data.orderNo;
  const firstPickupCode = exchJson1.data.pickupCode;

  const pointsAfter31 = parseInt(execSql(`SELECT current_points FROM student WHERE id = ${studentId}`));
  const stockAfter31 = parseInt(execSql(`SELECT stock FROM gift WHERE id = 1`));
  assert(pointsBefore31 - pointsAfter31 === 50, '首次兑换正确扣减 50 积分');
  assert(stockBefore31 - stockAfter31 === 1, '首次兑换正确扣减 1 件库存');

  // 3.2 重复提交相同 requestId（核心幂等断言）
  console.log('  -> 3.2 幂等重放测试: 再次发送相同 requestId...');
  const exchRes2 = await fetch(`${BASE_URL}/gift/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'satoken': studentToken },
    body: JSON.stringify({
      requestId: reqIdNormal,
      giftId: 1,
      quantity: 1,
      receiverName: '测试学生',
      receiverPhone: '13911112222',
      receiverAddress: '创客中心'
    })
  });
  const exchJson2 = await exchRes2.json();
  assert(exchJson2.code === 200, '相同 requestId 重试正常响应成功');
  assert(exchJson2.data.orderNo === firstOrderNo, '幂等返回的订单号必须与首次一致');
  assert(exchJson2.data.pickupCode === firstPickupCode, '幂等返回的提货码必须与首次一致');

  const pointsAfter32 = parseInt(execSql(`SELECT current_points FROM student WHERE id = ${studentId}`));
  const stockAfter32 = parseInt(execSql(`SELECT stock FROM gift WHERE id = 1`));
  assert(pointsAfter32 === pointsAfter31, '幂等重试严禁二次扣减学生积分');
  assert(stockAfter32 === stockAfter31, '幂等重试严禁二次扣减礼品库存');

  // 3.3 Redis 占位状态检查
  console.log('  -> 3.3 检查 Redis 幂等落定标记...');
  const redisDoneVal = getRedisVal(`gift:exchange:idem:${studentId}:${reqIdNormal}`);
  assert(redisDoneVal.startsWith('DONE:'), `Redis 幂等状态标记正确落定: ${redisDoneVal}`);

  // 3.4 同 requestId 并发提交
  console.log('  -> 3.4 并发重复提交测试 (同一 requestId)...');
  const reqIdConcurrent = generateUuid();
  const [cRes1, cRes2] = await Promise.all([
    fetch(`${BASE_URL}/gift/exchange`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'satoken': studentToken },
      body: JSON.stringify({ requestId: reqIdConcurrent, giftId: 1, quantity: 1 })
    }),
    fetch(`${BASE_URL}/gift/exchange`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'satoken': studentToken },
      body: JSON.stringify({ requestId: reqIdConcurrent, giftId: 1, quantity: 1 })
    })
  ]);
  const [cJson1, cJson2] = await Promise.all([cRes1.json(), cRes2.json()]);
  const successCount = (cJson1.code === 200 ? 1 : 0) + (cJson2.code === 200 ? 1 : 0);
  console.log(`    并发响应状态: c1=${cJson1.code}(${cJson1.message}), c2=${cJson2.code}(${cJson2.message})`);
  assert(successCount >= 1, '并发请求至少保证一笔成功落单');
  const orderCountForReq = parseInt(execSql(`SELECT COUNT(*) FROM student_gift WHERE request_id = '${reqIdConcurrent}'`));
  assert(orderCountForReq === 1, '并发提交在数据库中绝对只生成 1 笔订单');

  // 3.5 业务失败释放占位与重试
  console.log('  -> 3.5 业务失败(超额兑换)释放占位与纠错重试...');
  const reqIdFail = generateUuid();
  const failRes = await fetch(`${BASE_URL}/gift/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'satoken': studentToken },
    body: JSON.stringify({ requestId: reqIdFail, giftId: 5, quantity: 1 }) // 礼品5需要1000积分，余额不足
  });
  const failJson = await failRes.json();
  assert(failJson.code !== 200 && failJson.message.includes('可用积分不足'), '积分不足预期报错拦截');

  const redisFailVal = getRedisVal(`gift:exchange:idem:${studentId}:${reqIdFail}`);
  assert(!redisFailVal, '事务回滚后 Redis 占位键必须已自动删除释放');

  // 3.6 【核心验证：最优解兼容性】未传递 requestId 时的平滑降级
  console.log('  -> 3.6 【最优解兼容性】未传 requestId 的平滑降级兑换...');
  const exchResNoReqId = await fetch(`${BASE_URL}/gift/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'satoken': studentToken },
    body: JSON.stringify({
      giftId: 1,
      quantity: 1,
      receiverName: '老版兼容测试'
    }) // 故意不传 requestId 字段
  });
  const exchJsonNoReqId = await exchResNoReqId.json();
  assert(exchJsonNoReqId.code === 200, `未传 requestId 优雅降级成功兑换 (订单号: ${exchJsonNoReqId.data?.orderNo})`);
  const generatedReqId = execSql(`SELECT request_id FROM student_gift WHERE order_no = '${exchJsonNoReqId.data.orderNo}'`);
  assert(generatedReqId && generatedReqId.startsWith('GEN-'), `服务端自动生成了符合唯一索引的降级标识: ${generatedReqId}`);

  // ==========================================
  // 4. 验证修复③：订单状态并发竞态
  // ==========================================
  logSection('验证修复③：订单状态并发竞态');

  // 4.1 同一订单并发取消
  console.log('  -> 4.1 测试同一订单并发两次取消...');
  const exchOrderC = await (await fetch(`${BASE_URL}/gift/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'satoken': studentToken },
    body: JSON.stringify({ requestId: generateUuid(), giftId: 1, quantity: 1 })
  })).json();
  const orderCId = parseInt(execSql(`SELECT id FROM student_gift WHERE order_no = '${exchOrderC.data.orderNo}'`));
  const pointsBeforeCancel = parseInt(execSql(`SELECT current_points FROM student WHERE id = ${studentId}`));
  const stockBeforeCancel = parseInt(execSql(`SELECT stock FROM gift WHERE id = 1`));

  const [cancelRes1, cancelRes2] = await Promise.all([
    fetch(`${BASE_URL}/gift/orders/${orderCId}/cancel`, { method: 'POST', headers: { 'satoken': studentToken } }),
    fetch(`${BASE_URL}/gift/orders/${orderCId}/cancel`, { method: 'POST', headers: { 'satoken': studentToken } })
  ]);
  const [cancelJson1, cancelJson2] = await Promise.all([cancelRes1.json(), cancelRes2.json()]);
  console.log(`    取消并发响应: r1=${cancelJson1.code}(${cancelJson1.message}), r2=${cancelJson2.code}(${cancelJson2.message})`);

  const cancelSuccessCount = (cancelJson1.code === 200 ? 1 : 0) + (cancelJson2.code === 200 ? 1 : 0);
  assert(cancelSuccessCount === 1, '并发取消操作必须且仅有 1 次成功，另 1 次被状态守卫拦截');

  const pointsAfterCancel = parseInt(execSql(`SELECT current_points FROM student WHERE id = ${studentId}`));
  const stockAfterCancel = parseInt(execSql(`SELECT stock FROM gift WHERE id = 1`));
  assert(pointsAfterCancel - pointsBeforeCancel === 50, '并发取消只退还 1 次积分 (50 pts)，绝不超退');
  assert(stockAfterCancel - stockBeforeCancel === 1, '并发取消只恢复 1 次库存 (1 件)，绝不超退');

  const ledgerCount = parseInt(execSql(`SELECT COUNT(*) FROM student_point_record WHERE ref_id = ${orderCId} AND type = 4`));
  assert(ledgerCount === 1, '退还积分的台账流水记录严格只有 1 笔');

  // 4.2 管理员核销 vs 学生取消 并发竞态
  console.log('  -> 4.2 测试管理员核销与学生取消并发抢态...');
  const exchOrderD = await (await fetch(`${BASE_URL}/gift/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'satoken': studentToken },
    body: JSON.stringify({ requestId: generateUuid(), giftId: 1, quantity: 1 })
  })).json();
  const orderDId = parseInt(execSql(`SELECT id FROM student_gift WHERE order_no = '${exchOrderD.data.orderNo}'`));
  const pickupCodeD = exchOrderD.data.pickupCode;

  const pointsBeforeRace = parseInt(execSql(`SELECT current_points FROM student WHERE id = ${studentId}`));
  const stockBeforeRace = parseInt(execSql(`SELECT stock FROM gift WHERE id = 1`));

  const [raceVerifyRes, raceCancelRes] = await Promise.all([
    fetch(`${BASE_URL}/gift/deliver`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'satoken': adminToken },
      body: JSON.stringify({ orderId: orderDId, pickupCode: pickupCodeD, remark: '并发测试核销' })
    }),
    fetch(`${BASE_URL}/gift/orders/${orderDId}/cancel`, {
      method: 'POST',
      headers: { 'satoken': studentToken }
    })
  ]);
  const [raceVerifyJson, raceCancelJson] = await Promise.all([raceVerifyRes.json(), raceCancelRes.json()]);
  console.log(`    核销vs取消响应: 核销=${raceVerifyJson.code}(${raceVerifyJson.message}), 取消=${raceCancelJson.code}(${raceCancelJson.message})`);

  const raceWinCount = (raceVerifyJson.code === 200 ? 1 : 0) + (raceCancelJson.code === 200 ? 1 : 0);
  assert(raceWinCount === 1, '核销与取消同时发生时，必须且仅能有一方抢占成功');

  const finalOrderDStatus = parseInt(execSql(`SELECT status FROM student_gift WHERE id = ${orderDId}`));
  console.log(`    抢占终态: status=${finalOrderDStatus} (2=已核销, 3=已取消)`);
  assert(finalOrderDStatus === 2 || finalOrderDStatus === 3, '订单终态必须合法');

  if (finalOrderDStatus === 2) {
    const pts = parseInt(execSql(`SELECT current_points FROM student WHERE id = ${studentId}`));
    const stk = parseInt(execSql(`SELECT stock FROM gift WHERE id = 1`));
    assert(pts === pointsBeforeRace, '核销胜出时不退还积分');
    assert(stk === stockBeforeRace, '核销胜出时不恢复库存');
  } else {
    const pts = parseInt(execSql(`SELECT current_points FROM student WHERE id = ${studentId}`));
    const stk = parseInt(execSql(`SELECT stock FROM gift WHERE id = 1`));
    assert(pts - pointsBeforeRace === 50, '取消胜出时退还 1 次积分');
    assert(stk - stockBeforeRace === 1, '取消胜出时恢复 1 次库存');
  }

  // 4.3 已完结订单防重复操作
  console.log('  -> 4.3 终态订单不可重复操作测试...');
  const reOpRes = await fetch(`${BASE_URL}/gift/orders/${orderDId}/cancel`, {
    method: 'POST',
    headers: { 'satoken': studentToken }
  });
  const reOpJson = await reOpRes.json();
  assert(reOpJson.code !== 200, '对已处于终态的订单再次发起取消必须被拒绝');

  // 4.4 正常核销全链路与核销后防取消
  console.log('  -> 4.4 正常核销全链路与核销后防取消测试...');
  const exchOrderE = await (await fetch(`${BASE_URL}/gift/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'satoken': studentToken },
    body: JSON.stringify({ requestId: generateUuid(), giftId: 1, quantity: 1 })
  })).json();
  const orderEId = parseInt(execSql(`SELECT id FROM student_gift WHERE order_no = '${exchOrderE.data.orderNo}'`));
  const pickupCodeE = exchOrderE.data.pickupCode;

  const deliverRes = await fetch(`${BASE_URL}/gift/deliver`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'satoken': adminToken },
    body: JSON.stringify({ orderId: orderEId, pickupCode: pickupCodeE, remark: '正常全链路核销' })
  });
  const deliverJson = await deliverRes.json();
  assert(deliverJson.code === 200, '正常订单提货码核销成功');

  const orderEStatus = parseInt(execSql(`SELECT status FROM student_gift WHERE id = ${orderEId}`));
  assert(orderEStatus === 2, '核销后订单状态必须为 2 (已送达/已核销)');

  // 学生尝试取消已核销订单
  const cancelDeliveredRes = await fetch(`${BASE_URL}/gift/orders/${orderEId}/cancel`, {
    method: 'POST',
    headers: { 'satoken': studentToken }
  });
  const cancelDeliveredJson = await cancelDeliveredRes.json();
  assert(cancelDeliveredJson.code !== 200, '已核销的订单学生绝对不能取消');

  // 管理员再次重复核销
  const reDeliverRes = await fetch(`${BASE_URL}/gift/deliver`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'satoken': adminToken },
    body: JSON.stringify({ orderId: orderEId, pickupCode: pickupCodeE })
  });
  const reDeliverJson = await reDeliverRes.json();
  assert(reDeliverJson.code !== 200, '已核销订单禁止重复核销');

  logSection('全部验证完成！所有断言 100% PASS！');
}

main().catch(err => {
  console.error('\n[FATAL ERROR]', err);
  process.exit(1);
});
