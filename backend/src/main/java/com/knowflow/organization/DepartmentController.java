package com.knowflow.organization;

import com.knowflow.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(
            DepartmentService service
    ) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DepartmentDtos.View>> tree() {

        return ApiResponse.ok(
                service.tree()
        );
    }

    @PostMapping
    public ApiResponse<DepartmentDtos.View> create(
            @Valid
            @RequestBody
            DepartmentDtos.CreateRequest request
    ) {

        return ApiResponse.ok(
                service.create(
                        request
                )
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<DepartmentDtos.View> update(
            @PathVariable
            Long id,

            @Valid
            @RequestBody
            DepartmentDtos.UpdateRequest request
    ) {

        return ApiResponse.ok(
                service.update(
                        id,
                        request
                )
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable
            Long id
    ) {

        service.delete(id);

        return ApiResponse.ok();
    }
}