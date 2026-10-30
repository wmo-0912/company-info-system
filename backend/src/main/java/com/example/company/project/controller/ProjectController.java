package com.example.company.project.controller;

import com.example.company.common.api.ApiResponse;
import com.example.company.common.api.PageResponse;
import com.example.company.project.dto.ProjectMemberRequest;
import com.example.company.project.dto.ProjectRequest;
import com.example.company.project.service.ProjectService;
import com.example.company.project.vo.ProjectMemberVO;
import com.example.company.project.vo.ProjectVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "项目组管理")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "项目组分页列表")
    @GetMapping
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<PageResponse<ProjectVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(projectService.page(page, pageSize, keyword, status));
    }

    @Operation(summary = "我的项目组")
    @GetMapping("/my-teams")
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<List<ProjectVO>> myTeams() {
        return ApiResponse.ok(projectService.myTeams());
    }

    @Operation(summary = "项目组详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<ProjectVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(projectService.detail(id));
    }

    @Operation(summary = "创建项目组")
    @PostMapping
    @PreAuthorize("hasAuthority('project:create')")
    public ApiResponse<ProjectVO> create(@Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(projectService.create(request));
    }

    @Operation(summary = "更新项目组")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:update')")
    public ApiResponse<ProjectVO> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(projectService.update(id, request));
    }

    @Operation(summary = "成员列表")
    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<List<ProjectMemberVO>> members(@PathVariable Long id) {
        return ApiResponse.ok(projectService.members(id));
    }

    @Operation(summary = "新增成员")
    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('project:member:manage')")
    public ApiResponse<ProjectMemberVO> addMember(@PathVariable Long id,
                                                  @Valid @RequestBody ProjectMemberRequest request) {
        return ApiResponse.ok(projectService.addMember(id, request));
    }

    @Operation(summary = "移除成员（逻辑结束）")
    @DeleteMapping("/{id}/members/{employeeId}")
    @PreAuthorize("hasAuthority('project:member:manage')")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long employeeId) {
        projectService.removeMember(id, employeeId);
        return ApiResponse.ok();
    }
}
