# Quy chuẩn lập trình

## 1. Java
- Java 17; gói `jakarta.*` (Spring Boot 3). Không dùng API chỉ có ở Java 21+.
- Viết Java rõ ràng, quen thuộc; tránh trừu tượng hóa "khéo".
- Dùng `final` khi làm rõ ý; ưu tiên `record` cho DTO bất biến.
- `Optional` chỉ làm kiểu trả về, không làm field hay tham số.
- Tránh `null` khi có kiểu có nghĩa hơn.
- **Không dùng wildcard import** (`import x.y.*`).

## 2. Dependency injection
Chỉ constructor injection.

Đúng:
```java
@Service
public class GoodsReceiptPostingService {
    private final InventoryPostingService inventoryPostingService;
    private final GoodsReceiptRepository goodsReceiptRepository;

    public GoodsReceiptPostingService(InventoryPostingService inventoryPostingService,
                                      GoodsReceiptRepository goodsReceiptRepository) {
        this.inventoryPostingService = inventoryPostingService;
        this.goodsReceiptRepository = goodsReceiptRepository;
    }
}
```
Có thể dùng `@RequiredArgsConstructor` của Lombok.

Sai:
```java
@Autowired
private GoodsReceiptRepository goodsReceiptRepository;
```

## 3. Lombok
- Được dùng khi giảm code lặp: `@Getter`, `@RequiredArgsConstructor`, `@Builder` cho DTO.
- **Không dùng `@Data`, `@EqualsAndHashCode`, `@ToString` mặc định trên entity JPA** (gây lỗi lazy loading, vòng lặp, so sánh sai).
- Hành vi nghiệp vụ của entity viết tường minh (ví dụ `receipt.post(...)`), không để setter tùy ý đổi trạng thái.

## 4. Cấu hình và hằng số
- **Không hard-code**: secret, URL, thông tin kết nối, cấu hình môi trường, quy tắc nghiệp vụ (ngưỡng duyệt, thuế suất, số ngày nợ, ngưỡng cảnh báo hạn dùng, định dạng số chứng từ).
- Cấu hình kỹ thuật → `application-*.yml` + biến môi trường, đọc bằng `@ConfigurationProperties`.
- Quy tắc nghiệp vụ thay đổi được → bảng tham số / danh mục trong DB.

## 5. Validation
- Tầng API: Bean Validation trên request DTO (`@NotNull`, `@Size`, `@Positive`, `@Valid` cho danh sách dòng).
- Tầng nghiệp vụ: quy tắc phụ thuộc dữ liệu (tồn khả dụng, kỳ khóa, trạng thái, hạn mức, số còn được giao) ở `application`/`domain`.
- Không dựa vào frontend để validate.

## 6. Exception
- Exception nghiệp vụ có ý nghĩa và mã lỗi: `InsufficientStockException`, `PeriodLockedException`, `InvalidDocumentStateException`, `ContractQuantityExceededException`, `DuplicateRequestException`...
- Xử lý tập trung bằng `@RestControllerAdvice`; không bắt exception rồi nuốt im lặng.
- Không trả stack trace, SQL, tên bảng ra client.
- Thông báo cho người dùng bằng tiếng Việt; mã lỗi ổn định để frontend xử lý.

## 7. Transaction
- `@Transactional` đặt ở phương thức public của tầng `application`; `readOnly = true` cho truy vấn.
- Không gọi dịch vụ ngoài (email, hóa đơn điện tử) bên trong transaction ghi sổ; dùng sự kiện sau commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`).
- Không dùng `@Transactional` trên Controller.

## 8. JPA
- Không trả entity ra API; map sang DTO.
- Mặc định `FetchType.LAZY`; không dùng `EAGER` khi chưa có lý do ghi rõ.
- Tránh quan hệ hai chiều không cần thiết; không quan hệ JPA xuyên module.
- Chống N+1: fetch join, `@EntityGraph`, projection; kiểm tra số câu SQL trong test với truy vấn danh sách.
- `@Version` cho chứng từ và danh mục có thể bị sửa đồng thời.
- Tiền, số lượng: `BigDecimal`; so sánh bằng `compareTo`, làm tròn tường minh với `RoundingMode`.

## 9. Logging
- SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`) hoặc `@Slf4j`.
- Log định danh nghiệp vụ (số chứng từ, ID, request ID), không log mật khẩu, token, header `Authorization`, toàn bộ request body nhạy cảm.
- Mức log: `ERROR` lỗi cần xử lý, `WARN` bất thường nghiệp vụ, `INFO` sự kiện nghiệp vụ chính (ghi sổ, đảo, khóa kỳ), `DEBUG` chi tiết kỹ thuật.
- Mỗi request có `requestId` trong MDC.

## 10. Đặt tên
- Tên tiếng Anh có nghĩa cho mã nguồn; thuật ngữ nghiệp vụ thống nhất với bảng thuật ngữ trong `docs/03-database-design.md`.
- Không viết tắt trừ thuật ngữ đã chuẩn (VAT, FEFO, FIFO, UoM).
- Lớp theo vai trò: `*Controller`, `*Service`, `*Repository`, `*Request`, `*Response`, `*Mapper`.

## 11. Chất lượng mã
- Phương thức ngắn, một trách nhiệm.
- Không code chết, không code cũ bị comment, không TODO thiếu lý do.
- Không lặp quy tắc nghiệp vụ ở nhiều nơi; đặt ở domain/application dùng chung.
- Comment giải thích **tại sao**, không giải thích **làm gì**.
- Ghi chú mã yêu cầu khi hữu ích: `// BR-INV-20: kiểm tra tồn khả dụng tại thời điểm ghi sổ`.
