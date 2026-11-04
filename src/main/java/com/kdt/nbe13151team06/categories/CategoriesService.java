package com.kdt.nbe13151team06.categories;

import com.kdt.nbe13151team06.categories.domain.Categories;
import com.kdt.nbe13151team06.categories.dto.CategoriesRequest;
import com.kdt.nbe13151team06.categories.dto.CategoriesResponse;
import com.kdt.nbe13151team06.error.CATEGORY_NAME_DUPLICATED;
import com.kdt.nbe13151team06.error.CATEGORY_NOT_FOUND;
import com.kdt.nbe13151team06.error.VALIDATION_ERROR;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoriesService {

    private final CategoriesRepository categoriesRepository;

    @Autowired
    public CategoriesService(CategoriesRepository categoriesRepository) {
        this.categoriesRepository = categoriesRepository;
    }

    // A01 - 카테고리 목록(GET) - /api/v1/categories
    // DB에서 엔티티 목록을 꺼낸 뒤, 하나씩 응답 DTO로 변환 후 다시 리스트로 모음
    @Transactional(readOnly = true)
    public List<CategoriesResponse> findAll() {
        return categoriesRepository.findAllByOrderByIdAsc() // id 오름차순 조회후 List<Categories>로 반환
                // 리스트 -> 스트림 변경. 스트림은 요소들을 하나씩 흘려보내면서 가공할 수 있게 해 주는 "처리 파이프라인"
                // 이라고 생각하기. 원본 리스트는 바뀌지 않음.
                .stream()
                // map 은 흘러가는 요소 하나하나에 같은 변환 적용. 여기서는 각 Categories 엔티티를 from에 넣어 DTO로 변환함.
                .map(CategoriesResponse::from)
                .toList();

        /*
        List<Categories> categories = categoriesRepository.findAllByOrderByIdAsc();
        List<CategoriesResponse> result = new ArrayList<>();

        for (Categories category : categories) {
            result.add(CategoriesResponse.from(category));
        }
        return result;
        */
    }

    // A02 - 카테고리 상세(GET) - /api/v1/categories/{categoriesId}
    @Transactional(readOnly = true)
    public CategoriesResponse findById(Long id) {
        return CategoriesResponse.from(getCategoryOrThrow(id));
    }

    // A03 - 카테고리 등록(POST) - /api/v1/categories
    @Transactional
    public CategoriesResponse save(CategoriesRequest request) {
        String name = validateName(request);

        if (categoriesRepository.existsByName(name)) {
            throw new CATEGORY_NAME_DUPLICATED("같은 이름의 카테고리가 이미 있습니다.");
        }

        Categories saved = categoriesRepository.save(new Categories(name));
        return CategoriesResponse.from(saved);
    }

    // A04 - 카테고리 수정(PUT) - /api/v1/categories/{categoriesId}
    @Transactional
    public CategoriesResponse update(Long id, CategoriesRequest request) {
        validateId(id);
        String name = validateName(request);

        Categories category = getCategoryOrThrow(id);

        if (categoriesRepository.existsByNameAndIdNot(name, id)) {
            throw new CATEGORY_NAME_DUPLICATED("같은 이름의 카테고리가 이미 있습니다.");
        }

        category.changeName(name);
        return CategoriesResponse.from(category);
    }

    // A05 - 카테고리 삭제(DELETE) - /api/v1/categories/{categoriesId}
    @Transactional
    public void delete(Long id) {
        validateId(id);

        Categories category = getCategoryOrThrow(id);

        // menuRepository에서 category 사용중인지 확인
        // menuRepository에 existsByCategoryId 추가
//        if(menuRepository.existsByCategoryId(id){
//            throw new CATEGORY_IN_USE("메뉴가 등록된 카테고리는 삭제할 수 없습니다.");
//        }

        categoriesRepository.delete(category);
    }

    // MenuRepository에 추가 -> List<Menus> findAllByCategoryIdOrderByIdAsc(Long id);
    // -> jpa category 인식 안되면, categories로 통합하기
    // A15 - 카테고리별 메뉴(GET) - /api/v1/categories/{categoriesId}/menus -> 200, Menu[]
    /*
    @Transactional(readOnly = true)
    public List<MenuResponse> findMenusByCategory(Long id) {
        validateId(id); // 카테고리 id 검증
        getCategoryOrThrow(id); // 카테고리 존재 여부 검증

        return this.menuRepository.findAllByCategoryIdOrderByIdAsc(id)
                .stream()
                .map(MenuResponse::from)
                .toList();
    }
     */

    // 공통 조회 메서드
    // id가 없는 경우 -> CATEGORY_NOT_FOUND 커스텀 예외 던짐.
    private Categories getCategoryOrThrow(Long id) {
        return categoriesRepository.findById(id).orElseThrow(() -> new CATEGORY_NOT_FOUND());
    }

    // 카테고리 이름 검증(생성, 수정)
    private String validateName(CategoriesRequest request) {
        if (request == null || request.name() == null) {
            throw new VALIDATION_ERROR();
        }
        String name = request.name();
        if (name.isEmpty() || name.length() > 20) {
            throw new VALIDATION_ERROR();
        }
        return name;
    }

    // 카테고리 아이디 검증(수정, 삭제)
    private void validateId(Long id) {
        if (id == null || id < 1) {
            throw new CATEGORY_NOT_FOUND();
        }
    }


}