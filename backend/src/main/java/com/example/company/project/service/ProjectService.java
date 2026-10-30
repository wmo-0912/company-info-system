package com.example.company.project.service;

import com.example.company.common.api.PageResponse;
import com.example.company.project.dto.ProjectMemberRequest;
import com.example.company.project.dto.ProjectRequest;
import com.example.company.project.vo.ProjectMemberVO;
import com.example.company.project.vo.ProjectVO;

import java.util.List;

public interface ProjectService {

    PageResponse<ProjectVO> page(int page, int pageSize, String keyword, String status);

    ProjectVO detail(Long id);

    ProjectVO create(ProjectRequest request);

    ProjectVO update(Long id, ProjectRequest request);

    List<ProjectMemberVO> members(Long id);

    ProjectMemberVO addMember(Long id, ProjectMemberRequest request);

    void removeMember(Long id, Long employeeId);

    List<ProjectVO> myTeams();
}
