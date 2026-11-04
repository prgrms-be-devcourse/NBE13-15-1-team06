package com.kdt.nbe13151team06.statistics.service;

import com.kdt.nbe13151team06.error.VALIDATION_ERROR;
import com.kdt.nbe13151team06.orderItems.OrderItemRepository;
import com.kdt.nbe13151team06.orderItems.OrderItems;
import com.kdt.nbe13151team06.orders.Order;
import com.kdt.nbe13151team06.orders.OrderRepository;
import com.kdt.nbe13151team06.statistics.dto.CustomerStatisticsResponse;
import com.kdt.nbe13151team06.statistics.dto.DeliveryBatchResponse;
import com.kdt.nbe13151team06.statistics.dto.DeliveryItemResponse;
import com.kdt.nbe13151team06.statistics.dto.MenuStatisticsResponse;
import com.kdt.nbe13151team06.statistics.dto.OrderStatisticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 통계 관련 비즈니스 로직을 담당하는 서비스.
 *
 * <p>제공 기능 (API 번호 기준)</p>
 * <ul>
 *   <li>A17: 접수일별 주문 통계 ({@link #getOrderStatistics})</li>
 *   <li>A24: 접수일별 고객 통계 ({@link #getCustomerStatistics})</li>
 *   <li>A18: 접수일별 메뉴 통계 ({@link #getMenuStatistics})</li>
 *   <li>A14: 배송 묶음 ({@link #getDeliveryBatches})</li>
 * </ul>
 *
 * <p>공통 규칙</p>
 * <ul>
 *   <li>모든 메서드는 조회 전용이라 클래스에 {@code @Transactional(readOnly = true)}를 적용했다.</li>
 *   <li>날짜는 2000년 ~ 2099년 사이만 허용한다 (벗어나면 {@link VALIDATION_ERROR}).</li>
 *   <li>수량/금액 합계는 PLACED(접수) 상태 주문만 계산한다. 그 외(취소)는 건수만 센다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private final OrderRepository orderRepository;          // 주문 조회
    private final OrderItemRepository orderItemRepository;  // 주문 상품(주문에 담긴 메뉴) 조회

    // 생성자 주입 (Spring이 Repository를 자동으로 넣어줌)
    public StatisticsService(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    // ============================================================
    // A17. 접수일별 주문 통계
    //  - 기준: 접수일(createdAt)이 date인 주문 전체
    // ============================================================

    public OrderStatisticsResponse getOrderStatistics(LocalDate date) {
        validateDate(date);

        // 해당 날짜에 접수된 주문 전체 (PLACED + 취소 모두 포함)
        List<Order> orders = findOrdersOfDay(date);
        // 주문 ID -> 그 주문의 상품 목록 (쿼리 1번으로 한꺼번에 조회)
        Map<Long, List<OrderItems>> itemsByOrderId = groupItemsByOrderId(orders);

        int placedOrderCount = 0;     // 정상 접수 건수
        int cancelledOrderCount = 0;  // 취소 건수
        int totalQuantity = 0;        // 총 판매 수량 (PLACED만)
        long totalAmount = 0;         // 총 판매 금액 (PLACED만)

        for (Order order : orders) {
            if (order.getStatus() == Order.OrderStatus.PLACED) {
                // 정상 주문: 건수 + 수량 + 금액 합산
                placedOrderCount++;
                List<OrderItems> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
                totalQuantity += sumQuantity(items);
                totalAmount += sumLineAmount(items);
            } else {
                // 취소 주문: 건수만 센다 (금액/수량 제외)
                cancelledOrderCount++;
            }
        }

        return new OrderStatisticsResponse(date, orders.size(),
                placedOrderCount, cancelledOrderCount, totalQuantity, totalAmount);
    }

    // ============================================================
    // A24. 접수일별 고객 통계
    //  - 기준: 접수일(createdAt)이 date인 주문 전체
    //  - 결과: 고객(이메일)별 1건씩, 이메일 오름차순
    // ============================================================

    public List<CustomerStatisticsResponse> getCustomerStatistics(LocalDate date) {
        validateDate(date);

        List<Order> orders = findOrdersOfDay(date);
        Map<Long, List<OrderItems>> itemsByOrderId = groupItemsByOrderId(orders);
        // 이메일 -> 그 고객의 주문 목록 (TreeMap이라 이메일 오름차순)
        Map<String, List<Order>> ordersByEmail = groupByEmail(orders);

        List<CustomerStatisticsResponse> result = new ArrayList<>();

        // 고객 한 명씩 돌면서 A17과 같은 방식으로 집계
        for (Map.Entry<String, List<Order>> entry : ordersByEmail.entrySet()) {
            String email = entry.getKey();
            List<Order> customerOrders = entry.getValue();

            int placedOrderCount = 0;
            int cancelledOrderCount = 0;
            int totalQuantity = 0;
            long totalAmount = 0;

            for (Order order : customerOrders) {
                if (order.getStatus() == Order.OrderStatus.PLACED) {
                    placedOrderCount++;
                    List<OrderItems> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
                    totalQuantity += sumQuantity(items);
                    totalAmount += sumLineAmount(items);
                } else {
                    cancelledOrderCount++;
                }
            }

            result.add(new CustomerStatisticsResponse(date, email, customerOrders.size(),
                    placedOrderCount, cancelledOrderCount, totalQuantity, totalAmount));
        }

        return result;
    }

    // ============================================================
    // A18. 접수일별 메뉴 통계
    //  - 기준: 접수일(createdAt)이 date인 PLACED 주문만
    //  - 결과: 메뉴별 1건씩 (판매 수량, 판매 금액)
    //  - 정렬: 메뉴 ID → 단가 → 메뉴명
    // ============================================================

    public List<MenuStatisticsResponse> getMenuStatistics(LocalDate date) {
        validateDate(date);

        // 취소 주문은 메뉴 판매에서 빼야 하므로 PLACED 주문만 추린다
        List<Order> placedOrders = new ArrayList<>();
        for (Order order : findOrdersOfDay(date)) {
            if (order.getStatus() == Order.OrderStatus.PLACED) {
                placedOrders.add(order);
            }
        }

        // 주문이 없으면 DB 조회를 건너뛴다
        List<OrderItems> items = placedOrders.isEmpty()
                ? List.of()
                : orderItemRepository.findByOrderIdIn(placedOrders);

        List<MenuStatisticsResponse> result = new ArrayList<>();
        // (메뉴ID + 단가 + 메뉴명)이 같은 상품끼리 묶어서 수량/금액을 합산
        for (List<OrderItems> sameItems : groupByMenuPriceName(items).values()) {
            OrderItems first = sameItems.get(0);  // 대표 상품 (메뉴 정보를 꺼내기 위함)
            int totalQuantity = 0;
            long totalAmount = 0;
            for (OrderItems item : sameItems) {
                totalQuantity += item.getQuantity();
                totalAmount += item.getLine_amount();
            }
            result.add(new MenuStatisticsResponse(first.getMenuId().getId(), first.getMenuName(),
                    first.getUnit_price(), totalQuantity, totalAmount));
        }

        // 정렬: 메뉴 ID -> 단가 -> 메뉴명
        result.sort(Comparator.comparingLong(MenuStatisticsResponse::getMenuId)
                .thenComparingInt(MenuStatisticsResponse::getUnitPrice)
                .thenComparing(MenuStatisticsResponse::getMenuName));
        return result;
    }

    // ============================================================
    // A14. 배송 묶음
    //  - 기준: 접수일이 아니라 배송일(shippingDate)이 같은 PLACED 주문
    //  - 결과: 고객(이메일)별 배송 묶음 1건씩, 이메일 오름차순
    //  - 한 묶음 = 주소·우편번호 + orderIds(오름차순) + 합산된 items
    //  - items는 A18과 같은 규칙으로 합산 (ID → 단가 → 메뉴명 순)
    // ============================================================

    public List<DeliveryBatchResponse> getDeliveryBatches(LocalDate shippingDate) {
        validateDate(shippingDate);

        // 배송일이 같고 PLACED 상태인 주문만 조회 (접수일 기준이 아님에 주의)
        List<Order> orders = orderRepository
                .findByShippingDateAndStatusOrderByEmailAsc(shippingDate, Order.OrderStatus.PLACED);
        Map<Long, List<OrderItems>> itemsByOrderId = groupItemsByOrderId(orders);
        Map<String, List<Order>> ordersByEmail = groupByEmail(orders);

        List<DeliveryBatchResponse> result = new ArrayList<>();

        // 고객 한 명 = 배송 묶음 한 개
        // Entry = 맵에서 키와 밸류를 쌍이 아닌 하나씩 꺼내기 가능
        for (Map.Entry<String, List<Order>> entry : ordersByEmail.entrySet()) {
            String email = entry.getKey(); // -> map.entey<String> 꺼냄
            List<Order> customerOrders = entry.getValue(); // List<Order>꺼냄
            Order first = customerOrders.get(0); // 주소만

            List<Long> orderIds = new ArrayList<>();          // 묶음에 포함된 주문 번호들
            List<OrderItems> batchItems = new ArrayList<>();  // 묶음에 포함된 모든 상품

            for (Order order : customerOrders) {
                orderIds.add(order.getId());
                // getOrDefault 값이 없으면 정한 값 줌
                batchItems.addAll(itemsByOrderId.getOrDefault(order.getId(), List.of()));
            }
            orderIds.sort(null);  // 주문 번호 오름차순 (null = 자연 정렬)

            // 같은 메뉴는 하나로 합친 상품 목록
            List<DeliveryItemResponse> deliveryItems = mergeDeliveryItems(batchItems);
            int totalQuantity = 0;
            long totalAmount = 0;
            for (DeliveryItemResponse item : deliveryItems) {
                totalQuantity += item.getQuantity();
                totalAmount += item.getLineAmount();
            }

            result.add(new DeliveryBatchResponse(
                    shippingDate, email, first.getAddress(), first.getPostcode(),
                    orderIds, orderIds.size(), totalQuantity, totalAmount, deliveryItems));
        }

        return result;
    }


    // ============================================================
    // 내부에서만 쓰는 공통 메서드
    // ============================================================

    /** 날짜 검사: 연도가 2000 ~ 2099 범위를 벗어나면 VALIDATION_ERROR 발생 */
    private void validateDate(LocalDate date) {
        int year = date.getYear();
        if (year <= 2000 || year >= 2099) {
            throw new VALIDATION_ERROR();
        }
    }

    /**
     * 하루 동안 접수된 주문 전체 조회.
     * 하루 = 당일 00:00:00 이상 ~ 다음날 00:00:00 미만 (23:59:59.999...까지 포함)
     */
    private List<Order> findOrdersOfDay(LocalDate date) {
        return orderRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    /** 주문을 이메일별로 묶는다. TreeMap을 써서 이메일 오름차순으로 자동 정렬된다. */
    private Map<String, List<Order>> groupByEmail(List<Order> orders) {
        Map<String, List<Order>> map = new TreeMap<>();
        for (Order order : orders) {
            map.computeIfAbsent(order.getEmail(), k -> new ArrayList<>()).add(order);
        }
        return map;
    }

    /**
     * 주문 상품들을 "주문 ID -> 상품 목록"으로 묶는다.
     * 주문마다 쿼리를 날리지 않고 IN 쿼리 1번으로 가져온다 (N+1 문제 방지).
     */

    private Map<Long, List<OrderItems>> groupItemsByOrderId(List<Order> orders) {
        Map<Long, List<OrderItems>> map = new LinkedHashMap<>();
        if (orders.isEmpty()) {
            return map;
        }
        // IN 절로 한 번에 조회 후, 주문 ID별로 분류 -> n+1 방지
        for (OrderItems item : orderItemRepository.findByOrderIdIn(orders)) {
            map.computeIfAbsent(item.getOrderId().getId(), k -> new ArrayList<>()).add(item);
        }
        return map;
    }

    /**
     * 같은 메뉴끼리 묶는다. 기준(key) = "메뉴ID|단가|메뉴명".
     * 같은 메뉴라도 단가나 이름이 다르면 별개 항목으로 취급한다.
     * LinkedHashMap이라 입력된 순서가 유지된다.
     */
    private Map<String, List<OrderItems>> groupByMenuPriceName(List<OrderItems> items) {
        Map<String, List<OrderItems>> map = new LinkedHashMap<>();
        for (OrderItems item : items) {
            String key = item.getMenuId().getId() + "|" + item.getUnit_price() + "|" + item.getMenuName();
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        return map;
    }

    /** 상품 목록의 수량(quantity) 합계 */
    private int sumQuantity(List<OrderItems> items) {
        int sum = 0;
        for (OrderItems item : items) {
            sum += item.getQuantity();
        }
        return sum;
    }

    /** 상품 목록의 금액(line_amount = 단가 x 수량) 합계. 금액이 커질 수 있어 long 사용 */
    private long sumLineAmount(List<OrderItems> items) {
        long sum = 0;
        for (OrderItems item : items) {
            sum += item.getLine_amount();
        }
        return sum;
    }

    /**
     * 배송 묶음 안의 상품들을 같은 메뉴끼리 합쳐서 수량/금액을 합산한다.
     * (A18 메뉴 통계와 같은 규칙, 정렬: 메뉴 ID -> 단가 -> 메뉴명)
     */
    private List<DeliveryItemResponse> mergeDeliveryItems(List<OrderItems> items) {
        List<DeliveryItemResponse> result = new ArrayList<>();
        for (List<OrderItems> sameItems : groupByMenuPriceName(items).values()) {
            OrderItems first = sameItems.get(0);
            int quantity = 0;
            long lineAmount = 0;
            for (OrderItems item : sameItems) {
                quantity += item.getQuantity();
                lineAmount += item.getLine_amount();
            }
            result.add(new DeliveryItemResponse(first.getMenuId().getId(), first.getMenuName(),
                    first.getUnit_price(), quantity, lineAmount));
        }

        result.sort(Comparator.comparingLong(DeliveryItemResponse::getMenuId)
                .thenComparingInt(DeliveryItemResponse::getUnitPrice)
                .thenComparing(DeliveryItemResponse::getMenuName));
        return result;
    }
}