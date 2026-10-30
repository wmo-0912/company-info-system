package com.example.company.project.service;

import com.example.company.common.api.PageResponse;
import com.example.company.project.dto.ProjectMemberRequest;
import com.example.company.project.dto.ProjectRequest;
import com.example.company.project.vo.ProjectMemberVO;
import com.example.company.project.vo.ProjectVO;

import java.time.LocalDate;
import java.util.List;

public interface ProjectService {

    PageResponse<ProjectVO> page(int page, int pageSize, String keyword, String status);

    ProjectVO detail(Long id);

    ProjectVO create(ProjectRequest request);

    ProjectVO update(Long id, ProjectRequest request);

    List<ProjectMemberVO> members(Long id);

    ProjectMemberVO addMember(Long id, ProjectMemberRequest request);

    /** 成员离开；leftAt 为空取当天，且不能早于加入日期 */
    void removeMember(Long id, Long employeeId, LocalDate leftAt);

    /** 关闭 / 重新开启项目组（ACTIVE / CLOSED） */
    ProjectVO updateStatus(Long id, String status);

    List<ProjectVO> myTeams();
}
