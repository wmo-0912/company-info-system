package com.example.company.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.example.company.**.mapper")
public class MybatisPlusConfig {

    /** 单页最大条数，防止过大 pageSize 拖垮查询 */
    public static final long MAX_PAGE_SIZE = 100;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
