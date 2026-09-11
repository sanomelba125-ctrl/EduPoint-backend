package com.campus.modules.student.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PasswordUtil;
import com.campus.modules.student.dto.*;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.student.service.StudentService;
import com.campus.common.auth.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学生服务实现类
 */
@Service
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    private static final String STUDENT_LOGIN_PREFIX = AuthUtil.STUDENT_PREFIX;

    @Override
    public StudentLoginVO login(StudentLoginDTO loginDTO) {
        // 1. 显式查询包含密码列的记录(@TableField(select = false)默认排除了密码)
        Student student = lambdaQuery()
                .select(Student::getId, Student::getStudentNo, Student::getPassword,
                        Student::getName, Student::getGender, Student::getAvatar,
                        Student::getOpenid, Student::getCollege, Student::getMajor,
                        Student::getClassName, Student::getPhone, Student::getTotalPoints,
                        Student::getCurrentPoints, Student::getStatus, Student::getCreateTime)
                .eq(Student::getStudentNo, loginDTO.getStudentNo().trim())
                .one();

        // 2. 学号不存在 (统一提示“用户名或密码错误”防探测)
        if (student == null) {
            log.warn("学生端登录失败: 学号 [{}] 不存在", loginDTO.getStudentNo());
            throw new BusinessException(ResultCode.PASSWORD_ERROR.getCode(), "用户名或密码错误");
        }

        // 3. 校验账号状态是否已冻结/禁用
        if (student.getStatus() != null && student.getStatus() == 0) {
            log.warn("学生端登录失败: 账号 [{}] 已被禁用", loginDTO.getStudentNo());
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "账号已被冻结或禁用，请联系管理员");
        }

        // 4. BCrypt 密文比对
        if (!PasswordUtil.matches(loginDTO.getPassword(), student.getPassword())) {
            log.warn("学生端登录失败: 学号 [{}] 密码不匹配", loginDTO.getStudentNo());
            throw new BusinessException(ResultCode.PASSWORD_ERROR.getCode(), "用户名或密码错误");
        }

        // 5. 执行 Sa-Token 登录，前缀标识隔离 (student:{id})
        String loginId = STUDENT_LOGIN_PREFIX + student.getId();
        StpUtil.login(loginId);
        String token = StpUtil.getTokenValue();
        StpUtil.getSession().set("userType", "STUDENT");
        StpUtil.getSession().set("studentId", student.getId());
        StpUtil.getSession().set("studentNo", student.getStudentNo());
        log.info("学生 [{}] (学号: {}, ID: {}) 登录成功, 获得 Token: {}", student.getName(), student.getStudentNo(), student.getId(), token);

        // 6. 组装脱敏出参
        StudentVO vo = convertToVO(student);

        return StudentLoginVO.builder()
                .token(token)
                .student(vo)
                .studentInfo(vo)
                .userInfo(vo)
                .build();
    }

    @Override
    public void logout() {
        if (StpUtil.isLogin()) {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            StpUtil.logout();
            log.info("学生 (ID: {}) 退出登录成功", loginId);
        }
    }

    @Override
    public StudentVO getCurrentStudentInfo() {
        Long studentId = AuthUtil.requireStudentId();
        Student student = getById(studentId);
        if (student == null || (student.getStatus() != null && student.getStatus() == 0)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "学生账号不存在或已被禁用");
        }

        return convertToVO(student);
    }

    @Override
    public StudentVO updateProfile(StudentUpdateProfileDTO updateDTO) {
        Long studentId = AuthUtil.requireStudentId();
        Student student = getById(studentId);
        if (student == null || (student.getStatus() != null && student.getStatus() == 0)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "学生账号不存在或已被禁用");
        }

        boolean updated = false;
        if (updateDTO.getName() != null && !updateDTO.getName().isBlank()) {
            student.setName(updateDTO.getName().trim());
            updated = true;
        }
        if (updateDTO.getPhone() != null && !updateDTO.getPhone().isBlank()) {
            student.setPhone(updateDTO.getPhone().trim());
            updated = true;
        }
        if (updateDTO.getAvatar() != null && !updateDTO.getAvatar().isBlank()) {
            student.setAvatar(updateDTO.getAvatar().trim());
            updated = true;
        }
        if (updateDTO.getGender() != null) {
            student.setGender(updateDTO.getGender());
            updated = true;
        }

        if (updated) {
            updateById(student);
            log.info("学生 [{}] (ID: {}) 成功更新个人资料", student.getStudentNo(), student.getId());
        }

        return convertToVO(student);
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<StudentVO> getStudentPage(StudentQueryDTO queryDTO) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Student> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        queryDTO.getPage() != null ? queryDTO.getPage() : 1,
                        queryDTO.getSize() != null ? queryDTO.getSize() : 10
                );

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Student> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

        // 综合模糊匹配：学号或姓名
        if (org.springframework.util.StringUtils.hasText(queryDTO.getKeyword())) {
            String kw = queryDTO.getKeyword().trim();
            wrapper.and(w -> w.like(Student::getStudentNo, kw).or().like(Student::getName, kw));
        }

        // 精确/模糊条件过滤
        if (org.springframework.util.StringUtils.hasText(queryDTO.getClassName())) {
            wrapper.eq(Student::getClassName, queryDTO.getClassName().trim());
        }
        if (org.springframework.util.StringUtils.hasText(queryDTO.getCollege())) {
            wrapper.like(Student::getCollege, queryDTO.getCollege().trim());
        }
        if (org.springframework.util.StringUtils.hasText(queryDTO.getMajor())) {
            wrapper.like(Student::getMajor, queryDTO.getMajor().trim());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Student::getStatus, queryDTO.getStatus());
        }

        wrapper.orderByDesc(Student::getCreateTime);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Student> studentPage = page(page, wrapper);
        return studentPage.convert(this::convertToVO);
    }

    @Override
    public StudentVO createStudent(StudentCreateDTO createDTO) {
        String studentNo = createDTO.getStudentNo().trim();

        // 1. 学号唯一性校验
        boolean exists = lambdaQuery().eq(Student::getStudentNo, studentNo).exists();
        if (exists) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该学号学生档案已存在: " + studentNo);
        }

        // 2. 初始密码处理（默认 123456，BCrypt 密文哈希）
        String rawPassword = org.springframework.util.StringUtils.hasText(createDTO.getPassword())
                ? createDTO.getPassword().trim()
                : "123456";
        String encodedPassword = PasswordUtil.encode(rawPassword);

        // 3. 构建实体并持久化
        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName(createDTO.getName().trim());
        student.setPassword(encodedPassword);
        student.setGender(createDTO.getGender() != null ? createDTO.getGender() : 1);
        student.setClassName(createDTO.getClassName().trim());
        student.setCollege(org.springframework.util.StringUtils.hasText(createDTO.getCollege()) ? createDTO.getCollege().trim() : null);
        student.setMajor(org.springframework.util.StringUtils.hasText(createDTO.getMajor()) ? createDTO.getMajor().trim() : null);
        student.setPhone(org.springframework.util.StringUtils.hasText(createDTO.getPhone()) ? createDTO.getPhone().trim() : null);
        student.setAvatar(org.springframework.util.StringUtils.hasText(createDTO.getAvatar()) ? createDTO.getAvatar().trim() : null);
        student.setTotalPoints(0);
        student.setCurrentPoints(0);
        student.setStatus(1); // 默认正常启用
        student.setDeleted(0);

        save(student);
        log.info("管理端录入新学生档案成功: [{} - {}] (ID: {})", student.getStudentNo(), student.getName(), student.getId());

        return convertToVO(getById(student.getId()));
    }

    @Override
    public StudentVO updateStudentByAdmin(StudentAdminUpdateDTO updateDTO) {
        Student student = getById(updateDTO.getId());
        if (student == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "目标学生档案不存在");
        }

        if (org.springframework.util.StringUtils.hasText(updateDTO.getName())) {
            student.setName(updateDTO.getName().trim());
        }
        if (updateDTO.getGender() != null) {
            student.setGender(updateDTO.getGender());
        }
        if (org.springframework.util.StringUtils.hasText(updateDTO.getClassName())) {
            student.setClassName(updateDTO.getClassName().trim());
        }
        if (updateDTO.getCollege() != null) {
            student.setCollege(org.springframework.util.StringUtils.hasText(updateDTO.getCollege()) ? updateDTO.getCollege().trim() : null);
        }
        if (updateDTO.getMajor() != null) {
            student.setMajor(org.springframework.util.StringUtils.hasText(updateDTO.getMajor()) ? updateDTO.getMajor().trim() : null);
        }
        if (updateDTO.getPhone() != null) {
            student.setPhone(org.springframework.util.StringUtils.hasText(updateDTO.getPhone()) ? updateDTO.getPhone().trim() : null);
        }
        if (updateDTO.getAvatar() != null) {
            student.setAvatar(org.springframework.util.StringUtils.hasText(updateDTO.getAvatar()) ? updateDTO.getAvatar().trim() : null);
        }

        if (updateDTO.getStatus() != null) {
            student.setStatus(updateDTO.getStatus());
            // 若被管理员冻结，强踢学生下线
            if (updateDTO.getStatus() == 0) {
                StpUtil.kickout(STUDENT_LOGIN_PREFIX + student.getId());
            }
        }

        // 若传入新密码，则加密并更新，同时下线旧会话
        if (org.springframework.util.StringUtils.hasText(updateDTO.getPassword())) {
            student.setPassword(PasswordUtil.encode(updateDTO.getPassword().trim()));
            StpUtil.kickout(STUDENT_LOGIN_PREFIX + student.getId());
        }

        updateById(student);
        log.info("管理端修改学生档案成功: [{} - {}] (ID: {})", student.getStudentNo(), student.getName(), student.getId());

        return convertToVO(getById(student.getId()));
    }

    @Override
    public void updateStudentStatus(Long id, Integer status) {
        Student student = getById(id);
        if (student == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "目标学生档案不存在");
        }

        student.setStatus(status);
        updateById(student);

        // 冻结账号时立即踢掉对应在线会话
        if (status != null && status == 0) {
            StpUtil.kickout(STUDENT_LOGIN_PREFIX + id);
            log.info("学生账号已冻结并强制踢下线: (ID: {}, StudentNo: {})", id, student.getStudentNo());
        } else {
            log.info("学生账号已解冻恢复正常: (ID: {}, StudentNo: {})", id, student.getStudentNo());
        }
    }

    @Override
    public void resetStudentPassword(Long id, String newPassword) {
        Student student = getById(id);
        if (student == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "目标学生档案不存在");
        }

        String rawPassword = org.springframework.util.StringUtils.hasText(newPassword) ? newPassword.trim() : "123456";
        String encodedPassword = PasswordUtil.encode(rawPassword);

        lambdaUpdate()
                .eq(Student::getId, id)
                .set(Student::getPassword, encodedPassword)
                .update();

        // 重置密码后踢下线，确保以新密码重新认证
        StpUtil.kickout(STUDENT_LOGIN_PREFIX + id);
        log.info("学生密码重置成功并强制下线: (ID: {}, StudentNo: {})", id, student.getStudentNo());
    }

    @Override
    public void deleteStudent(Long id) {
        Student student = getById(id);
        if (student == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "目标学生档案不存在");
        }

        removeById(id);
        StpUtil.kickout(STUDENT_LOGIN_PREFIX + id);
        log.info("管理端注销/删除学生档案成功: (ID: {}, StudentNo: {})", id, student.getStudentNo());
    }

    @Override
    public java.util.List<String> getDistinctClasses() {
        return lambdaQuery()
                .select(Student::getClassName)
                .isNotNull(Student::getClassName)
                .ne(Student::getClassName, "")
                .list()
                .stream()
                .map(Student::getClassName)
                .filter(org.springframework.util.StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 实体脱敏转换为 VO
     */
    private StudentVO convertToVO(Student student) {
        if (student == null) {
            return null;
        }
        return StudentVO.builder()
                .id(student.getId())
                .studentNo(student.getStudentNo())
                .name(student.getName())
                .gender(student.getGender())
                .avatar(student.getAvatar())
                .openid(student.getOpenid())
                .college(student.getCollege())
                .major(student.getMajor())
                .className(student.getClassName())
                .phone(student.getPhone())
                .totalPoints(student.getTotalPoints() != null ? student.getTotalPoints() : 0)
                .currentPoints(student.getCurrentPoints() != null ? student.getCurrentPoints() : 0)
                .status(student.getStatus())
                .createTime(student.getCreateTime())
                .build();
    }
}
