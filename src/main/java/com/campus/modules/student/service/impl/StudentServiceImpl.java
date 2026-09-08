package com.campus.modules.student.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.student.service.StudentService;
import org.springframework.stereotype.Service;

/**
 * Student 服务实现类
 * 负责具体业务逻辑实现
 */
@Service
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {
    // 实现业务方法
}
