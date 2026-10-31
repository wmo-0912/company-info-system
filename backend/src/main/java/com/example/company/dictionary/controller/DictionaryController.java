package com.example.company.dictionary.controller;

import com.example.company.common.api.ApiResponse;
import com.example.company.dictionary.dto.DictionaryItemRequest;
import com.example.company.dictionary.dto.DictionaryTypeRequest;
import com.example.company.dictionary.entity.DictionaryItem;
import com.example.company.dictionary.entity.DictionaryType;
import com.example.company.dictionary.service.DictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "数据字典")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @Operation(summary = "字典类型列表")
    @GetMapping("/dictionaries")
    @PreAuthorize("hasAuthority('dictionary:read')")
    public ApiResponse<List<DictionaryType>> listTypes() {
        return ApiResponse.ok(dictionaryService.listTypes());
    }

    @Operation(summary = "按编码查询字典项")
    @GetMapping("/dictionaries/{dictCode}/items")
    @PreAuthorize("hasAuthority('dictionary:read')")
    public ApiResponse<List<DictionaryItem>> listItems(@PathVariable String dictCode) {
        return ApiResponse.ok(dictionaryService.listItems(dictCode));
    }

    @Operation(summary = "创建字典类型")
    @PostMapping("/dictionaries")
    @PreAuthorize("hasAuthority('dictionary:manage')")
    public ApiResponse<DictionaryType> createType(@Valid @RequestBody DictionaryTypeRequest request) {
        return ApiResponse.ok(dictionaryService.createType(request));
    }

    @Operation(summary = "修改字典类型（编码不可改）")
    @PutMapping("/dictionaries/{id}")
    @PreAuthorize("hasAuthority('dictionary:manage')")
    public ApiResponse<DictionaryType> updateType(@PathVariable Long id,
                                                  @Valid @RequestBody DictionaryTypeRequest request) {
        return ApiResponse.ok(dictionaryService.updateType(id, request));
    }

    @Operation(summary = "启用/停用字典类型")
    @PatchMapping("/dictionaries/{id}/status")
    @PreAuthorize("hasAuthority('dictionary:manage')")
    public ApiResponse<Void> changeTypeStatus(@PathVariable Long id,
                                              @Valid @RequestBody EnabledRequest request) {
        dictionaryService.changeTypeStatus(id, request.getEnabled());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除字典类型（有字典项或属于内置字典时拒绝）")
    @DeleteMapping("/dictionaries/{id}")
    @PreAuthorize("hasAuthority('dictionary:manage')")
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        dictionaryService.deleteType(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "创建字典项")
    @PostMapping("/dictionaries/{dictCode}/items")
    @PreAuthorize("hasAuthority('dictionary:manage')")
    public ApiResponse<DictionaryItem> createItem(@PathVariable String dictCode,
                                                  @Valid @RequestBody DictionaryItemRequest request) {
        return ApiResponse.ok(dictionaryService.createItem(dictCode, request));
    }

    @Operation(summary = "更新字典项")
    @PutMapping("/dictionary-items/{id}")
    @PreAuthorize("hasAuthority('dictionary:manage')")
    public ApiResponse<DictionaryItem> updateItem(@PathVariable Long id,
                                                  @Valid @RequestBody DictionaryItemRequest request) {
        return ApiResponse.ok(dictionaryService.updateItem(id, request));
    }

    @Operation(summary = "启用/停用字典项")
    @PatchMapping("/dictionary-items/{id}/status")
    @PreAuthorize("hasAuthority('dictionary:manage')")
    public ApiResponse<Void> changeItemStatus(@PathVariable Long id,
                                              @Valid @RequestBody EnabledRequest request) {
        dictionaryService.changeItemStatus(id, request.getEnabled());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除字典项（已被员工档案引用时拒绝）")
    @DeleteMapping("/dictionary-items/{id}")
    @PreAuthorize("hasAuthority('dictionary:manage')")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        dictionaryService.deleteItem(id);
        return ApiResponse.ok();
    }

    @Data
    public static class EnabledRequest {
        @NotNull(message = "enabled 不能为空")
        private Boolean enabled;
    }
}
