package com.campus.common.constant;

/**
 * 系统角色常量定义
 * 统一管理 Sa-Token 权限注解与鉴权切面中的角色标识符，杜绝硬编码魔法值
 */
public final class UserRole {

    private UserRole() {
    }

    /** 超级管理员：拥有系统最高特权，可管理所有系统用户、活动、档案、礼品及知识库 */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** 普通管理员：可管理日常活动发布、学生档案、积分对账、礼品上下架与客服知识库 */
    public static final String ADMIN = "ADMIN";

    /** 辅导员：负责本院系学生档案维护、活动打卡审核、现场提货核销，无权管理系统用户、礼品库和知识库 */
    public static final String COUNSELOR = "COUNSELOR";

    /** 学生端用户：移动端学生身份，仅可参与报名、打卡、积分兑换，绝对禁止访问任何管理端接口 */
    public static final String STUDENT = "STUDENT";
}
