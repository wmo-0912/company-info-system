package com.example.company.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("project_team_member")
public class ProjectTeamMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectTeamId;

    private Long employeeId;

    private String memberRole;

    private LocalDate joinedAt;

    private LocalDate leftAt;
}
