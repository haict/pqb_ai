# Luồng nghiệp vụ

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.2 (DRAFT) |
| Ngày cập nhật | 2026-10-09 |
| Căn cứ | `docs/01-business-requirements.md` v0.6 (mục 1, 5, 8–14) |

## 1. Vòng đời chứng từ

### 1.1 Nhóm A - chứng từ ghi sổ
Phiếu nhận hàng, phiếu nhập/xuất/chuyển kho, điều chỉnh, kiểm kê, hóa đơn mua/bán, phiếu thu/chi, nghiệm thu, bút toán tổng hợp.
```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> SUBMITTED: Gửi duyệt
    SUBMITTED --> APPROVED: Duyệt
    SUBMITTED --> REJECTED: Từ chối (lý do)
    REJECTED --> DRAFT: Sửa lại
    APPROVED --> POSTED: Ghi sổ
    DRAFT --> POSTED: Ghi sổ (loại không cần duyệt)
    DRAFT --> CANCELLED: Hủy
    REJECTED --> CANCELLED: Hủy
    APPROVED --> CANCELLED: Hủy (lý do)
    POSTED --> REVERSED: Chứng từ đảo (lý do)
    CANCELLED --> [*]
    REVERSED --> [*]
```

### 1.2 Nhóm B - chứng từ điều phối
Đề nghị mua, yêu cầu báo giá, đơn mua, báo giá bán, đơn bán, giao hàng, gói thầu, hợp đồng.
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

## 2. Chuỗi mua hàng
```mermaid
flowchart LR
    PR[Đề nghị mua] --> RFQ[Yêu cầu báo giá NCC]
    RFQ --> CMP[So sánh báo giá]
    CMP --> PO[Đơn mua<br/>+ hàng đang về]
    PR -.không cần báo giá.-> PO
    PO --> GR[Phiếu nhận hàng<br/>kiểm đếm, hàng lỗi]
    GR --> IR[Phiếu nhập kho<br/>+ tồn, − đang về]
    GR -.hàng lỗi.-> RET[Trả hàng NCC / CÁCH LY]
    IR --> PI[Hóa đơn mua<br/>+ phải trả]
    PI --> PAY[Phiếu chi / UNC<br/>− phải trả, − quỹ]
    LC[Chi phí mua hàng] -.phân bổ.-> IR
```
| Bước | Kiểm tra chính | Mã BR |
|---|---|---|
| Đơn mua duyệt | Ngưỡng duyệt; NCC có vai trò SUPPLIER | BR-PUR-04 |
| Nhận hàng | Không vượt số đặt quá dung sai; ghi số lỗi | BR-PUR-07 |
| Nhập kho | Lô/hạn/serial bắt buộc theo hàng; chặn hàng hết hạn; kỳ chưa khóa | BR-PUR-08, BR-INV-01..02, BR-INV-11 |
| Hóa đơn mua | Chống trùng; đối chiếu ba chiều; hóa đơn về sau thì điều chỉnh giá nhập | BR-PUR-09..11 |
| Thanh toán | Duyệt theo ngưỡng; quỹ không âm; phân bổ không vượt còn lại | BR-AP-02, BR-CSH-03 |

## 3. Chuỗi bán hàng
```mermaid
flowchart LR
    C[Khách hàng] --> Q[Báo giá<br/>duyệt giá]
    Q --> SO[Đơn bán<br/>kiểm tra hạn mức, tồn khả dụng<br/>+ giữ chỗ]
    SO --> IS[Phiếu xuất kho<br/>FEFO, − tồn, − giữ chỗ]
    IS --> DL[Giao hàng<br/>biên bản giao nhận]
    DL --> INV[Hóa đơn bán<br/>+ phải thu, + doanh thu, + giá vốn]
    INV --> RC[Phiếu thu<br/>− phải thu, + quỹ]
    SO -.thiếu hàng.-> PR[Đề nghị mua]
    DL -.trả / đổi / lỗi.-> SR[Trả hàng bán<br/>hàng về CÁCH LY]
```
- Đơn bán, xuất kho, giao hàng, hóa đơn, thu tiền là các sự kiện riêng, có thể nhiều lần cho một đơn (BR-SAL-20).
- Doanh thu, phải thu, giá vốn ghi nhận khi hóa đơn bán ghi sổ (D-35; quy tắc cấu hình, kế toán trưởng rà soát trước UAT). Hàng đã xuất chưa có hóa đơn được theo dõi là hàng gửi đi bán.
- Thao tác nhanh (BR-SAL-42) sinh các chứng từ trên trong một lần, vẫn là chứng từ riêng và liên kết.

## 4. Chuỗi đấu thầu
```mermaid
flowchart LR
    TP[Gói thầu] --> BID[Hồ sơ dự thầu<br/>hàng dự thầu, giá]
    BID --> RES{Kết quả}
    RES -->|Trượt| LOST[Lưu lý do]
    RES -->|Trúng| TR[Kết quả trúng thầu<br/>SL, giá, hiệu lực]
    TR --> CT[Hợp đồng + phụ lục]
    CT --> AL[Phân bổ hàng<br/>giữ chỗ / đề nghị mua]
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

Đã làm cho chứng từ kho (Giai đoạn 3): thao tác "Đảo" lập chứng từ đảo PDK và gửi duyệt ngay; kiểm tra chứng từ phụ thuộc và kỳ khóa diễn ra **khi ghi sổ PDK** (lúc đó mới khóa số dư). Hàng của chứng từ gốc đã bị chứng từ khác xuất thì ghi sổ PDK báo `HAS_DEPENDENT_DOCUMENTS` kèm số chứng từ cần đảo trước. Hủy PDK thì chứng từ gốc đảo lại được.

### 6.1 Chứng từ kho (Giai đoạn 3)
```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> SUBMITTED: gửi duyệt
    SUBMITTED --> APPROVED: duyệt đủ bước / loại không cần duyệt
    SUBMITTED --> REJECTED: từ chối (lý do)
    REJECTED --> SUBMITTED: sửa, gửi lại
    APPROVED --> POSTED: ghi sổ
    APPROVED --> IN_TRANSIT: ghi sổ chuyển kho khác chi nhánh
    IN_TRANSIT --> POSTED: kho đích nhận hàng
    DRAFT --> CANCELLED
    SUBMITTED --> CANCELLED
    APPROVED --> CANCELLED
    POSTED --> REVERSED: chứng từ đảo PDK được ghi sổ
```
Chuyển kho khác chi nhánh: ghi sổ xuất khỏi kho nguồn và nhập vào kho "đang vận chuyển" của chi nhánh nguồn; người thuộc kho đích xác nhận nhận hàng thì xuất khỏi kho đang vận chuyển, nhập kho đích (đúng lô, serial, trạng thái).

## 7. Kiểm kê
```
DRAFT -> COUNTING (bắt đầu đếm: snapshot tồn theo vị trí/lô/serial/trạng thái; chặn ghi sổ nhập/xuất của kho)
      -> nhập số đếm (khi COUNTING; hàng tìm thấy ngoài sổ thêm dòng với tồn sổ sách 0)
      -> SUBMITTED (đủ số đếm) -> APPROVED -> POSTED (sinh phiếu điều chỉnh PDC loại COUNT theo chênh lệch, mở khóa kho)
SUBMITTED -> COUNTING khi bị từ chối (vẫn khóa kho); hủy ở bất kỳ bước chưa ghi sổ thì mở khóa kho
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

## 9. Phê duyệt
- Ma trận duyệt cấu hình theo loại chứng từ, chi nhánh, ngưỡng giá trị/chiết khấu, số cấp (BR-SYS-11, D-04).
- Duyệt bắt buộc: giá dưới giá sàn, vượt hạn mức tín dụng, hợp đồng, giá dự thầu dưới biên, phiếu chi trên ngưỡng, điều chỉnh tồn, hủy hàng, thu hồi, đảo chứng từ, xuất vượt tồn khả dụng.
- Người lập không tự duyệt khi bật tách biệt nhiệm vụ.
- Mỗi bước duyệt/từ chối ghi `approval_history` và audit.
