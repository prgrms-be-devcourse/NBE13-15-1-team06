package com.kdt.nbe13151team06.categories;

import com.kdt.nbe13151team06.categories.dto.CategoriesRequest;
import com.kdt.nbe13151team06.categories.dto.CategoriesResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoriesController {

    private final CategoriesService categoriesService;

    @Autowired
    public CategoriesController(CategoriesService categoriesService) {
        this.categoriesService = categoriesService;
    }

    // A01 - 카테고리 목록(GET) - /api/v1/categories -> 200, Category[]
    @GetMapping
    public ResponseEntity<List<CategoriesResponse>> categoryList() {
        return ResponseEntity.ok(this.categoriesService.findAll());
    }

    // A02 - 카테고리 상세(GET) - /api/v1/categories/{categoriesId} -> 200, Category
    @GetMapping("/{categoriesId}")
    public ResponseEntity<CategoriesResponse> categoryOne(@PathVariable("categoriesId") Long id) {
        return ResponseEntity.ok(this.categoriesService.findById(id));
    }

    // A03 - 카테고리 등록(POST) - /api/v1/categories -> 201, Category
    @PostMapping
    public ResponseEntity<CategoriesResponse> categoryCreate(@RequestBody CategoriesRequest request) {
        CategoriesResponse saved = this.categoriesService.save(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // A04 - 카테고리 수정(PUT) - /api/v1/categories/{categoriesId} -> 200, Category
    @PutMapping("/{categoriesId}")
    public ResponseEntity<CategoriesResponse> categoryUpdate(
            @PathVariable("categoriesId") Long id,
            @RequestBody CategoriesRequest request) {
        return ResponseEntity.ok(this.categoriesService.update(id, request));
    }

    // A05 - 카테고리 삭제(DELETE) - /api/v1/categories/{categoriesId} -> 204, 빈 본문
    @DeleteMapping("/{categoriesId}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // 응답 -> 204, 빈 본문
    public void categoryDelete(@PathVariable("categoriesId") Long id) {
        this.categoriesService.delete(id);
    }

    /*
    // A15 - 카테고리별 메뉴(GET) - /api/v1/categories/{categoriesId}/menus -> 200, Menu[]
    @GetMapping("/{categoriesId}/menus")
    public ResponseEntity<List<MenuResponse>> findMenusByCategory(@PathVariable("categoriesId") Long id) {
        return ResponseEntity.ok(this.categoriesService.findMenusByCategory(id));
    }
    */

}