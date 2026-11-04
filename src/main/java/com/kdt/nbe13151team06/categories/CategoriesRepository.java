package com.kdt.nbe13151team06.categories;


import com.kdt.nbe13151team06.categories.domain.Categories;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriesRepository extends JpaRepository<Categories, Long> {

    // 전체 조회후 Id를 기준으로 오름차순 정렬
    List<Categories> findAllByOrderByIdAsc();

    boolean existsByName(String name); // 카테고리 중복 검증(생성)

    // 수정할 때, 자기 자신을 제외한 다른 행이 있는지 검사.
    boolean existsByNameAndIdNot(String name, Long id); // 카테고리가 같고, id가 다른 행이 있는지 확인(업데이트)

    Categories findByName(String name);
}