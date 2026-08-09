package com.knowflow.organization;

import com.knowflow.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService service;

    public MemberController(
            MemberService service
    ) {
        this.service = service;
    }

    /**
     * 企业成员列表。
     */
    @GetMapping
    public ApiResponse<List<MemberDtos.View>> list() {

        return ApiResponse.ok(
                service.list()
        );
    }

    /**
     * 创建成员。
     */
    @PostMapping
    public ApiResponse<MemberDtos.View> create(
            @Valid
            @RequestBody
            MemberDtos.CreateRequest request
    ) {

        return ApiResponse.ok(
                service.create(
                        request
                )
        );
    }

    /**
     * 修改成员部门 / 角色。
     */
    @PutMapping("/{membershipId}")
    public ApiResponse<MemberDtos.View> update(
            @PathVariable
            Long membershipId,

            @Valid
            @RequestBody
            MemberDtos.UpdateRequest request
    ) {

        return ApiResponse.ok(
                service.update(
                        membershipId,
                        request
                )
        );
    }

    /**
     * 移除成员。
     */
    @DeleteMapping("/{membershipId}")
    public ApiResponse<Void> delete(
            @PathVariable
            Long membershipId
    ) {

        service.delete(
                membershipId
        );

        return ApiResponse.ok(
                null
        );
    }
}