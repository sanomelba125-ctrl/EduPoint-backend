package com.campus.modules.student.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PasswordUtil;
import com.campus.modules.student.dto.StudentLoginDTO;
import com.campus.modules.student.dto.StudentLoginVO;
import com.campus.modules.student.dto.StudentUpdateProfileDTO;
import com.campus.modules.student.dto.StudentVO;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.student.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 学生服务实现类
 */
@Service
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    private static final String STUDENT_LOGIN_PREFIX = "student:";

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
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();

        // 严格校验用户类型，防止管理员 Token 越权访问学生接口
        if (!loginId.startsWith(STUDENT_LOGIN_PREFIX)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权访问学生端接口");
        }

        long studentId = Long.parseLong(loginId.substring(STUDENT_LOGIN_PREFIX.length()));
        Student student = getById(studentId);
        if (student == null || (student.getStatus() != null && student.getStatus() == 0)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "学生账号不存在或已被禁用");
        }

        return convertToVO(student);
    }

    @Override
    public StudentVO updateProfile(StudentUpdateProfileDTO updateDTO) {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();

        if (!loginId.startsWith(STUDENT_LOGIN_PREFIX)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权访问学生端接口");
        }

        long studentId = Long.parseLong(loginId.substring(STUDENT_LOGIN_PREFIX.length()));
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
