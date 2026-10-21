# Luồng nghiệp vụ

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.2 (DRAFT) |
| Ngày cập nhật | 2026-10-09 |
| Căn cứ | `docs/01-business-requirements.md` v0.6 (mục 1, 5, 8–14) |

## 1. Vòng đời chứng từ

### 1.1 Nhóm A - chứng từ ghi sổ
Đơn mua, đơn bán (D-54), trả hàng / giảm giá mua bán, phân bổ chi phí mua, phiếu nhập/xuất/chuyển kho, điều chỉnh, kiểm kê, phiếu thu/chi, nghiệm thu, bút toán tổng hợp.
```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> POSTED: Ghi sổ (D-61 không qua duyệt)
    DRAFT --> CANCELLED: Hủy (lý do)
    POSTED --> REVERSED: Chứng từ đảo (lý do)
    POSTED --> DRAFT: Bỏ ghi sổ (lý do; chỉ đơn mua, đơn bán, D-62)
    CANCELLED --> [*]
    REVERSED --> [*]
```
Chứng từ lập trước D-61: APPROVED vẫn ghi sổ / hủy được; SUBMITTED, REJECTED chỉ còn hủy (hủy đóng yêu cầu duyệt còn treo); lịch sử duyệt giữ chỉ đọc.

Bỏ ghi sổ (D-62, BR-PUR-20, BR-SAL-25) chỉ cho đơn mua, đơn bán: trong một transaction đảo phiếu kho tự sinh (PDK), công nợ, bút toán theo ngày hạch toán gốc (sổ phụ vẫn chỉ INSERT), đơn về DRAFT giữ số để sửa; ghi sổ lại sinh phiếu kho, khoản công nợ, bút toán mới. Điều kiện chặn như đảo; đơn bán thêm: chưa chuyển đi giao, chưa có số hóa đơn điện tử, không có khiếu nại.

### 1.2 Nhóm B - chứng từ điều phối
Gói thầu, hợp đồng (từ D-54 đơn mua, đơn bán là chứng từ ghi sổ nhóm A). `[Suy luận]` Theo D-61 nhóm B cũng không qua duyệt (bỏ SUBMITTED / APPROVED / REJECTED) khi triển khai Giai đoạn 7; sơ đồ dưới là thiết kế trước D-61, cần cập nhật khi làm.
```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> SUBMITTED
    SUBMITTED --> APPROVED
    SUBMITTED --> REJECTED
    REJECTED --> DRAFT
    APPROVED --> IN_PROGRESS: Phát sinh chứng từ sau
    IN_PROGRESS --> COMPLETED: Thực hiện đủ
    IN_PROGRESS --> CLOSED: Đóng phần còn lại (lý do)
    APPROVED --> CANCELLED: Chưa có chứng từ sau
    DRAFT --> CANCELLED
```
Quy tắc: chỉ POSTED ảnh hưởng tồn thực tế và số liệu tài chính; nhóm B chỉ ảnh hưởng giữ chỗ, hàng đang về, số còn được giao (BR-GEN-02).

## 2. Chuỗi mua hàng (D-54)
```mermaid
flowchart LR
    PO[Đơn mua nháp<br/>kèm hóa đơn NCC, lô / hạn / serial] -->|ghi sổ D-61| POST[Phiếu nhập kho tự sinh<br/>+ tồn<br/>+ phải trả, bút toán]
    POST --> PAY[Phiếu chi / UNC<br/>− phải trả, − quỹ]
    POST -.hàng lỗi / NCC giảm giá.-> RET[Trả hàng NCC / giảm giá<br/>− tồn, − phải trả]
    SVC[Đơn mua dịch vụ<br/>vận chuyển, bảo hiểm] -.phân bổ chi phí.-> POST
```
| Bước | Kiểm tra chính | Mã BR |
|---|---|---|
| Lập đơn mua | NCC có vai trò SUPPLIER; kho vật lý cùng chi nhánh; chỉ VND; lô / hạn dùng theo cấu hình hàng, số serial bằng số lượng cơ sở; hóa đơn NCC đủ ký hiệu, số, ngày hoặc để trống, không trùng (MST, ký hiệu, số). Không qua duyệt (D-61); đơn nháp không cộng hàng đang về; hủy đơn đã duyệt trước D-61: − hàng đang về | BR-PUR-04, 09 |
| Ghi sổ đơn mua | Đơn nháp (hoặc đã duyệt trước D-61); kỳ chưa khóa; tổng tiền từ ngưỡng `purchasing.order-approval-threshold` cần quyền `purchase-order:post-over-threshold` (422 `AUTHORITY_LIMIT_EXCEEDED`). Lập phiếu nhập kho PNK nguồn `DMH` cho dòng hàng hóa (đơn giá sau chiết khấu), ghi sổ ngay; − hàng đang về (đơn đã duyệt trước D-61); khoản công nợ phải trả (hạn = ngày hóa đơn / ngày hạch toán + số ngày nợ); bút toán theo quy tắc DMH | BR-PUR-08, 09, BR-INV-01..02, 11, D-34 |
| Đảo đơn mua | Chặn khi còn trả hàng / giảm giá / phân bổ chưa đảo, hàng đã xuất (đảo phiếu nhập bị chặn), đã thanh toán; đảo phiếu nhập, công nợ, bút toán; giải phóng số hóa đơn | BR-GEN-04, 21 |
| Trả hàng / giảm giá (5D) | Theo dòng đơn mua đã ghi sổ; trả không vượt số còn trả, đúng serial của dòng; giảm giá không vượt giá trị còn lại | BR-PUR-14 |
| Phân bổ chi phí (5D) | Nguồn: dòng dịch vụ của đơn mua đã ghi sổ; đích: dòng hàng hóa của đơn mua đã ghi sổ; theo giá trị hoặc số lượng | BR-PUR-12, 16 |
| Thanh toán | Từ ngưỡng chi cần quyền ghi sổ vượt ngưỡng (D-61); quỹ không âm; phân bổ không vượt còn lại | BR-AP-02, BR-CSH-03 |

## 3. Chuỗi bán hàng (D-54)
```mermaid
flowchart LR
    C[Khách hàng] --> SO[Đơn bán nháp<br/>giá theo bảng giá<br/>+ giữ chỗ nếu chọn]
    SO -->|ghi sổ D-61: kiểm tra quyền<br/>giá dưới sàn / vượt hạn mức| POST[Phiếu xuất kho tự sinh FEFO<br/>− tồn, − giữ chỗ<br/>+ phải thu, + doanh thu, bút toán]
    POST --> EI[Số hóa đơn điện tử MISA]
    POST --> DL[Giao hàng<br/>đang giao → khách nhận<br/>biên bản giao nhận]
    POST --> RC[Phiếu thu<br/>− phải thu, + quỹ]
    DL -.giao thất bại.-> REV[Đảo đơn bán<br/>hàng về kho]
    POST -.trả / giảm giá / lỗi.-> SR[Trả hàng bán<br/>hàng về CÁCH LY]
    SR -.đổi hàng.-> SO
```
- Ghi sổ đơn bán làm cùng lúc xuất kho, doanh thu, công nợ phải thu, bút toán (D-54, D-35; quy tắc cấu hình, kế toán trưởng rà soát trước UAT). Mỗi lần giao là một đơn bán; giá vốn ở Giai đoạn 8.
- Lô xuất: FEFO hoặc lô chỉ định (khác FEFO phải có lý do); chặn lô còn hạn dùng ngắn hơn mức tối thiểu khách yêu cầu (D-07); dùng phần hàng đã giữ cho đơn, nhả phần giữ còn lại.
- Giữ hàng: ngay trên đơn nháp khi lưu (nếu chọn) hoặc theo lệnh; sửa đơn thì nhả rồi giữ lại theo dòng mới; hết hạn sau `sales.reservation-days`, nhả khi ghi sổ / hủy (D-61).
- Thẩm quyền (D-61, thay duyệt): giá dưới sàn / chiết khấu vượt `sales.discount-approval-percent` cần `sales-order:override-price` (422 `PRICE_BELOW_FLOOR`); vượt hạn mức tín dụng / nợ quá hạn cần `sales-order:override-credit` (422 `CREDIT_LIMIT_EXCEEDED`), tính lại lúc ghi sổ; lý do hiện trên đơn nháp (`approvalReasons`).
- Số hóa đơn điện tử ghi trên đơn trước hoặc sau khi ghi sổ, chống trùng; nhãn khoản công nợ lấy số hóa đơn nếu đã có lúc ghi sổ.
- Trả hàng bán theo dòng và lô đã xuất của đơn, nhập lại kho cách ly; đổi hàng lập đơn bán mới cùng giá.
```mermaid
stateDiagram-v2
    [*] --> DRAFT: lập đơn
    DRAFT --> PENDING: ghi sổ (xuất kho, doanh thu, công nợ)
    PENDING --> IN_TRANSIT: đang giao
    PENDING --> DELIVERED: khách nhận
    IN_TRANSIT --> DELIVERED: khách nhận
    PENDING --> REVERSED: giao thất bại / lập sai (đảo đơn)
    IN_TRANSIT --> REVERSED: giao thất bại (đảo đơn)
```

## 4. Chuỗi đấu thầu
```mermaid
flowchart LR
    TP[Gói thầu] --> BID[Hồ sơ dự thầu<br/>hàng dự thầu, giá]
    BID --> RES{Kết quả}
    RES -->|Trượt| LOST[Lưu lý do]
    RES -->|Trúng| TR[Kết quả trúng thầu<br/>SL, giá, hiệu lực]
    TR --> CT[Hợp đồng + phụ lục]
    CT --> AL[Phân bổ hàng<br/>giữ chỗ / đơn mua nháp]
    CT --> PLAN[Kế hoạch giao theo đợt]
    PLAN --> SO[Đơn bán theo hợp đồng]
    SO --> IS[Xuất kho] --> DL[Giao hàng]
    DL --> ACC[Nghiệm thu]
    ACC --> INV[Hóa đơn] --> RC[Đối soát và thu tiền]
```
Kiểm soát: mỗi lần đặt, xuất, giao theo hợp đồng khóa dòng hợp đồng và kiểm tra `đã giao + lần này ≤ số lượng tối đa` (BR-TND-22, AT-07).

## 5. Ghi sổ (trong một transaction)
```mermaid
flowchart TB
    A[Nhận lệnh ghi sổ + Idempotency-Key] --> B{Key đã xử lý?}
    B -->|Có| Z[Trả kết quả cũ]
    B -->|Không| C[BEGIN]
    C --> D[Khóa chứng từ; kiểm tra trạng thái, quyền]
    D --> E[Kiểm tra kỳ chưa khóa]
    E --> F[Khóa summary + balance theo thứ tự;<br/>kiểm tra tồn khả dụng / số còn lại]
    F --> G[Ghi inventory_transactions,<br/>debt/cash ledger]
    G --> H[Sinh bút toán Nợ = Có]
    H --> I[Cập nhật số dư, chỉ tiêu chứng từ nguồn,<br/>trạng thái POSTED]
    I --> J[Ghi audit + lưu kết quả key]
    J --> K[COMMIT]
    F -->|Không đủ| X[ROLLBACK + lỗi 422]
    E -->|Kỳ khóa| X
```

### 5.1 Sinh bút toán (Giai đoạn 4A, ADR-11)
```mermaid
flowchart LR
    S[Chứng từ nguồn ghi sổ] -->|dòng: mã nghiệp vụ, số tiền,<br/>loại hàng, vai trò đối tượng,<br/>TK do chứng từ quyết định| J[JournalPostingService]
    J --> P{Kỳ kế toán mở?}
    P -->|Khóa| X[PERIOD_LOCKED, rollback cả chứng từ]
    P -->|Mở| R[Tra posting_rules: loại CT + mã nghiệp vụ,<br/>còn hiệu lực, quy tắc cụ thể nhất]
    R -->|Không có| X2[POSTING_RULE_NOT_FOUND]
    R --> A[Xác định TK Nợ / Có; vế trống lấy TK chứng từ truyền]
    A --> K[Kiểm tra TK: hoạt động, chi tiết, có đối tượng nếu bắt buộc]
    K --> B[Ghi journal_entries + lines, Nợ = Có]
```
- Đảo chứng từ nguồn gọi `JournalPostingService.reverse`: khóa bút toán gốc, sinh bút toán đổi vế từng dòng vào ngày đảo, bút toán gốc chuyển `REVERSED`.
- Bút toán phiếu kho làm ở Giai đoạn 8 cùng tính giá vốn (D-51); Giai đoạn 4 sinh bút toán cho chứng từ tiền (4C). Công nợ đầu kỳ không sinh bút toán `[Giả định]`.

### 5.2 Thu chi, ngân hàng (Giai đoạn 4C, BR-CSH-02, 03, BR-AR-03)
```mermaid
flowchart TB
    L[Lập PT / PC / BC / UNC / CTN nháp] --> T{Ghi sổ D-61: chứng từ chi PC, UNC, CTN<br/>và số tiền ≥ ngưỡng chi?}
    T -->|Có, thiếu quyền vượt ngưỡng| X[422 AUTHORITY_LIMIT_EXCEEDED]
    T -->|Không / có quyền / chứng từ thu| P[Ghi sổ: khóa chứng từ → quỹ theo id → khoản công nợ theo id]
    P --> D{Nội dung}
    D -->|Thu tiền khách / trả NCC| S[Phân bổ: AUTO hóa đơn đến hạn sớm nhất trước<br/>hoặc MANUAL theo dòng; phần dư = trả trước]
    D -->|Thu / chi khác, chuyển tiền| C
    S --> C[Sổ quỹ + số dư quỹ<br/>quỹ tiền mặt không âm]
    C --> J[Bút toán theo quy tắc hạch toán<br/>TK tiền lấy từ quỹ]
    J --> K[POSTED + audit, COMMIT]
```
- Ngưỡng chi: tham số `finance.payment-approval-threshold` (mặc định 20.000.000 `[Giả định]`); từ D-61 chỉ người có quyền `cash-document:post-over-threshold` ghi sổ được chứng từ chi từ ngưỡng (thay luồng duyệt chi).
- Phân bổ trong cùng chi nhánh, cùng đối tượng, cùng loại công nợ; số còn lại của hóa đơn kiểm tra lại sau khi khóa (BR-GEN-52).
- Trả trước ghi sổ chi tiết công nợ loại `PREPAYMENT`, không gắn hóa đơn; đối trừ trả trước với hóa đơn làm ở 4D.
- Đảo (quyền `cash-document:reverse`, bắt buộc lý do): hoàn phân bổ vào hóa đơn, sổ quỹ ngược, bút toán đảo. Đảo phiếu thu mà tiền đã chi làm quỹ tiền mặt âm thì bị từ chối (`CASH_BALANCE_NEGATIVE`). Công nợ đầu kỳ đã được thu một phần không đảo được cho tới khi đảo phiếu thu (`HAS_DEPENDENT_DOCUMENTS`).

### 5.3 Bù trừ, điều chỉnh, tạm ứng, đối chiếu, kiểm kê quỹ (Giai đoạn 4D)
```mermaid
flowchart LR
    PT[Phiếu thu thừa] -->|trả trước| PP[(debt_prepayments)]
    PP -->|DTCN trả trước| HD[Hóa đơn còn nợ]
    AR[Phải thu] <-->|DTCN phải thu - phải trả<br/>Nợ 331 / Có 131| AP[Phải trả]
    PC[Phiếu chi tạm ứng] -->|công nợ ADVANCE| TU[Khoản tạm ứng]
    TU -->|TTTU: chi phí thực tế| CP[TK chi phí]
    TU -->|Phiếu thu hoàn ứng| Q[Quỹ]
    SL[(Sổ chi tiết công nợ)] --> BB[Biên bản đối chiếu<br/>chốt số dư]
    BB -->|lệch| DC[DCCN]
    KK[Kiểm kê quỹ] -->|chênh lệch| SQ[(Sổ quỹ)]
```
- Bù trừ, điều chỉnh, thanh toán tạm ứng, kiểm kê quỹ dùng vòng đời nhóm A chung; đảo bằng chứng từ đảo, không xóa (bỏ bù trừ = đảo DTCN).
- Đảo phiếu thu có trả trước đã bù trừ: `HAS_DEPENDENT_DOCUMENTS` (đảo chứng từ bù trừ trước). Đảo phiếu chi tạm ứng đã hoàn ứng / thanh toán: `HAS_DEPENDENT_DOCUMENTS`.
- Kiểm kê quỹ: số dư sổ quỹ tính đến hết ngày kiểm kê tại lúc ghi sổ (sau khi khóa quỹ); không chênh lệch thì không sinh sổ quỹ, bút toán.

## 6. Đảo chứng từ
```mermaid
flowchart LR
    S[Chứng từ POSTED] --> D{Có chứng từ phụ thuộc<br/>chưa đảo?}
    D -->|Có| E[Từ chối, trả danh sách<br/>chứng từ phụ thuộc]
    D -->|Không| K{Ngày đảo thuộc kỳ khóa?}
    K -->|Có| E2[Từ chối PERIOD_LOCKED]
    K -->|Không| R[Tạo chứng từ đảo<br/>dòng sổ phụ ngược từng dòng]
    R --> U[Cập nhật số dư,<br/>giảm chỉ tiêu chứng từ nguồn]
    U --> M[Chứng từ gốc → REVERSED<br/>giữ nguyên lịch sử]
```

Đã làm cho chứng từ kho (Giai đoạn 3): thao tác "Đảo" lập chứng từ đảo PDK ở trạng thái nháp (D-61; trước đây gửi duyệt ngay), người có quyền ghi sổ đảo ghi sổ PDK; kiểm tra chứng từ phụ thuộc và kỳ khóa diễn ra **khi ghi sổ PDK** (lúc đó mới khóa số dư). Hàng của chứng từ gốc đã bị chứng từ khác xuất thì ghi sổ PDK báo `HAS_DEPENDENT_DOCUMENTS` kèm số chứng từ cần đảo trước. Hủy PDK thì chứng từ gốc đảo lại được.

### 6.1 Chứng từ kho (Giai đoạn 3)
```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> POSTED: ghi sổ (D-61 không qua duyệt)
    DRAFT --> IN_TRANSIT: ghi sổ chuyển kho khác chi nhánh
    IN_TRANSIT --> POSTED: kho đích nhận hàng
    DRAFT --> CANCELLED
    POSTED --> REVERSED: chứng từ đảo PDK được ghi sổ
```
Chuyển kho khác chi nhánh: ghi sổ xuất khỏi kho nguồn và nhập vào kho "đang vận chuyển" của chi nhánh nguồn; người thuộc kho đích xác nhận nhận hàng thì xuất khỏi kho đang vận chuyển, nhập kho đích (đúng lô, serial, trạng thái).

## 7. Kiểm kê
```
DRAFT -> COUNTING (bắt đầu đếm: snapshot tồn theo vị trí/lô/serial/trạng thái; chặn ghi sổ nhập/xuất của kho)
      -> nhập số đếm (khi COUNTING; hàng tìm thấy ngoài sổ thêm dòng với tồn sổ sách 0)
      -> POSTED (đủ số đếm, D-61 không qua duyệt; sinh phiếu điều chỉnh PDC loại COUNT theo chênh lệch, mở khóa kho)
Hủy ở bất kỳ bước chưa ghi sổ thì mở khóa kho; phiếu chờ duyệt trước D-61 chỉ còn hủy, đã duyệt vẫn ghi sổ được
```

## 8. Khóa kỳ cuối tháng
```mermaid
flowchart LR
    A[Hoàn tất chứng từ trong tháng] --> B[Tính giá vốn kỳ<br/>cost_calculation_run]
    B --> C[Khóa kỳ kho]
    C --> D[Đối chiếu kho, công nợ, quỹ<br/>với tài khoản]
    D --> E[Khóa kỳ công nợ, quỹ]
    E --> F[Khóa kỳ kế toán]
```
Mở lại kỳ: Kế toán trưởng hoặc Giám đốc duyệt, có lý do, ghi audit (BR-SYS-32).

## 9. Phê duyệt (bỏ theo D-61, 2026-10-21)
- Không còn bước duyệt, luồng duyệt, ủy quyền duyệt, việc chờ duyệt, tách biệt nhiệm vụ người lập / người duyệt (thay BR-SYS-11, BR-SYS-14, D-04, D-52).
- Kiểm soát còn lại bằng quyền khi ghi sổ, ghi audit khi dùng quyền vượt: chi từ ngưỡng (`cash-document:post-over-threshold`), đơn mua từ ngưỡng (`purchase-order:post-over-threshold`), giá dưới sàn / chiết khấu vượt ngưỡng (`sales-order:override-price`), vượt hạn mức tín dụng / nợ quá hạn (`sales-order:override-credit`).
- Chính sách xuất vượt tồn khả dụng kích hoạt bằng `stock-policy:activate` (mặc định Giám đốc); thu hồi kích hoạt / đóng bằng `recall:manage`.
- Lịch sử duyệt của chứng từ lập trước D-61 giữ chỉ đọc. `[Suy luận]` Giá dự thầu dưới biên, hợp đồng (Giai đoạn 7) chưa làm; cách kiểm soát cần chốt khi triển khai.
