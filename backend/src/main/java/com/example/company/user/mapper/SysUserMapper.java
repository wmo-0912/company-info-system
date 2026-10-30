package com.example.company.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.company.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 失败计数自增（原子更新，避免并发丢失），达到阈值时在同一语句内置为 LOCKED。
     * 使用独立语句而非"查出实体再 updateById"，因此不依赖外层事务，失败登录的计数一定会落库。
     */
    @Update("UPDATE sys_user SET failed_login_count = failed_login_count + 1, " +
            "status = CASE WHEN status = 'ACTIVE' AND failed_login_count + 1 >= #{maxAttempts} " +
            "THEN 'LOCKED' ELSE status END, " +
            "updated_at = now() " +
            "WHERE id = #{userId} AND deleted = FALSE")
    int incrementFailedLogin(@Param("userId") Long userId, @Param("maxAttempts") int maxAttempts);

    /** 登录成功后清零失败计数 */
    @Update("UPDATE sys_user SET failed_login_count = 0, last_login_at = now(), updated_at = now() " +
            "WHERE id = #{userId} AND deleted = FALSE")
    int markLoginSuccess(@Param("userId") Long userId);

    /** 管理员解锁 / 启用账号时清零失败计数 */
    @Update("UPDATE sys_user SET failed_login_count = 0, updated_at = now() " +
            "WHERE id = #{userId} AND deleted = FALSE")
    int resetFailedLogin(@Param("userId") Long userId);
}
