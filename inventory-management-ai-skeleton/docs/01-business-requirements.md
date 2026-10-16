# Yêu cầu nghiệp vụ - ERP kinh doanh máy móc, thiết bị và vật tư y tế

| Thuộc tính | Giá trị |
|---|---|
| Phiên bản | 0.8 (DRAFT - đã chốt toàn bộ quyết định ở mục 20) |
| Ngày cập nhật | 2026-10-12 |
| Doanh nghiệp | Công ty TNHH Công nghệ B2K (Hà Nội) |
| Loại hình | Thương mại / phân phối: máy móc, thiết bị y tế, vật tư y tế, hóa chất - thuốc thử xét nghiệm; tham gia đấu thầu cung cấp cho cơ sở y tế |
| Nguồn | (1) Đặc tả yêu cầu dự án do chủ dự án cung cấp ngày 2026-10-09; (2) ảnh hiện trạng MISA SME 2026 R5 Enterprise (`docs/HienTrang/01..19.jpg`); (3) tài liệu tham khảo `docs/HienTrang/để lập 1 phần mềm theo dõi xuất nhập tồn.docx` |
| Thay đổi so với 0.7 (2026-10-12) | **D-54 gộp chứng từ mua / bán**: đơn mua hàng là chứng từ ghi sổ duy nhất của nghiệp vụ mua (mang hóa đơn NCC, lô / hạn dùng / serial; ghi sổ = nhập kho + công nợ + bút toán); đơn bán hàng là chứng từ ghi sổ duy nhất của nghiệp vụ bán (ghi sổ = xuất kho + doanh thu + công nợ + bút toán; số hóa đơn điện tử; theo dõi giao hàng). Bỏ đề nghị mua, yêu cầu / so sánh báo giá NCC, phiếu nhận hàng, hóa đơn mua riêng, báo giá bán, phiếu giao hàng, hóa đơn bán riêng, bán nhanh. Sửa mục 1, 5.1, 5.3, 8, 10, 18, 20 |
| Thay đổi bổ sung 2026-10-10 | Chốt D-45..D-48 (chi tiết khóa kỳ, cấu hình số chứng từ) khi làm Giai đoạn 1C; D-49..D-53 khi làm 1D, Giai đoạn 2, 3 |
| Thay đổi so với 0.6 | Chốt toàn bộ quyết định còn "Mở" và "Chốt tạm" theo khuyến nghị; quy tắc kế toán/pháp lý cấu hình được, KTT/pháp chế rà soát trước UAT |
| Thay đổi so với 0.5 | Chủ dự án chốt quyết định theo đề xuất (mục 20, cột Trạng thái); thêm D-42 (thư viện bổ sung), D-43 (gói gốc Java) |
| Thay đổi so với 0.4 | Tái cấu trúc theo **10 phân hệ** của đặc tả dự án (M01..M10). Tách **Đấu thầu** thành phân hệ riêng. Tách **phiếu nhận hàng** và **phiếu nhập kho**; tách **đơn bán, xuất kho, giao hàng, ghi nhận doanh thu, hóa đơn**. Thêm: ma trận ảnh hưởng chứng từ (5.3), bốn loại tồn (5.4), chống xử lý trùng (5.6), đa chi nhánh và phạm vi dữ liệu ở P1, yêu cầu báo giá NCC, so sánh báo giá, đổi hàng, đại lý, kỳ kho, bút toán tự động ở P1, dashboard truy ngược, yêu cầu giao diện, kịch bản nghiệm thu (mục 18), tài liệu bàn giao (mục 19), quyết định D-33..D-41 |

### Quy ước
- `BR-<PHÂN HỆ>-nn`: mã yêu cầu, dùng để truy vết sang thiết kế, API và test. `BR-GEN` là quy tắc chung, `BR-UX` là tiện ích người dùng, `BR-NF` là yêu cầu phi chức năng, `AT-nn` là kịch bản nghiệm thu.
- `[ĐT]`: căn cứ từ đặc tả yêu cầu dự án.
- `[HT-nn]`: căn cứ từ ảnh hiện trạng số `nn`.
- `[TK]`: căn cứ từ file .docx tham khảo (**hội thoại tư vấn với ChatGPT, chưa được doanh nghiệp duyệt**).
- `[Suy luận]`: nhận định của người phân tích, cần xác nhận.
- `[Chưa kiểm chứng]`: nội dung pháp lý, kế toán hoặc thuế chưa được đối chiếu văn bản hiện hành, cần pháp chế hoặc kế toán trưởng xác nhận.
- Giai đoạn: **P1** = MVP, **P2** = giai đoạn 2, **P3** = giai đoạn 3 (mục 3.2).
- Yêu cầu kỹ thuật, kiến trúc và quy tắc lập trình nằm ở `CLAUDE.md` và `.claude/rules/`. Tài liệu này chỉ nêu ràng buộc kỹ thuật khi ảnh hưởng nghiệp vụ.

---

## 1. Mục đích
Xây dựng hệ thống ERP thay thế dần MISA SME, quản lý xuyên suốt ba chuỗi nghiệp vụ [ĐT]:

| Chuỗi | Luồng |
|---|---|
| **Mua hàng** | Đơn mua hàng (kèm hóa đơn nhà cung cấp; ghi sổ = nhập kho + công nợ nhà cung cấp) → Thanh toán (D-54) |
| **Bán hàng** | Khách hàng → Đơn bán hàng (kiểm tra tồn, giữ hàng; ghi sổ = xuất kho + doanh thu + công nợ khách hàng; số hóa đơn điện tử) → Giao hàng (theo dõi) → Thu tiền (D-54) |
| **Đấu thầu** | Gói thầu → Hồ sơ dự thầu → Kết quả trúng thầu → Hợp đồng → Phân bổ hàng hóa → Giao hàng theo tiến độ → Nghiệm thu → Đối soát và thu tiền |

Ngoài ba chuỗi trên, hệ thống quản lý dịch vụ kỹ thuật (bảo hành, sửa chữa thiết bị), kiểm soát chất lượng theo lô/hạn dùng/serial, kế toán và giá vốn.

### Nguyên tắc cốt lõi
    Chứng từ nghiệp vụ -> Bút toán sổ phụ (kho / công nợ / quỹ / kế toán) -> Số dư -> Báo cáo

| Mã | Nguyên tắc |
|---|---|
| BR-GEN-P1 | Mọi nghiệp vụ liên kết với nhau. Một giao dịch được ghi nhận phải cập nhật đúng mọi dữ liệu liên quan theo ma trận ảnh hưởng (mục 5.3). Không để tồn kho, công nợ, doanh thu hoặc giá vốn bị lệch [ĐT]. |
| BR-GEN-P2 | Không số dư nào (tồn kho, công nợ, quỹ, giá vốn) được sửa trực tiếp mà không có chứng từ tương ứng. |
| BR-GEN-P3 | Mọi số liệu tổng hợp (báo cáo, dashboard) truy ngược được về chứng từ nguồn [ĐT]. |

---

## 2. Hiện trạng (AS-IS)

### 2.1 Hệ thống và tổ chức
| # | Quan sát | Nguồn |
|---|---|---|
| H-01 | Dùng **MISA SME 2026 R5 Enterprise** với các phân hệ: Quỹ, Ngân hàng, Mua hàng, Bán hàng, Hóa đơn điện tử, Kho, Công cụ dụng cụ, Tài sản cố định, Tiền lương, Thuế, Giá thành, Tổng hợp, Hợp đồng | [HT-01], [HT-05] |
| H-02 | Kế toán là người lập chứng từ mua, bán, kho; không thấy bước phê duyệt trong hệ thống | [HT-02], [HT-11] |
| H-03 | Chỉ có 1 kho `KH-01` "HANG HOA", không có vị trí | [HT-11], [HT-12] |
| H-04 | Chế độ kế toán theo Thông tư 133/2016/TT-BTC (mẫu S16-DNN) | [HT-09] |
| H-05 | Menu MISA còn có: Bàn làm việc, Khế ước vay, Kết nối vay vốn, Ngân sách, nhóm báo cáo Thủ quỹ, Thủ kho, Báo cáo phân tích, Báo cáo đối chiếu; tiện ích "Tìm chứng từ", "Ngày hạch toán", "Tùy chỉnh báo cáo", "Báo cáo yêu thích", ô tìm tính năng (Ctrl+Q) | [HT-05], [HT-13] |
| H-06 | Bán hàng MISA có: hóa đơn chiết khấu thương mại, thu tiền khách hàng hàng loạt, đối trừ chứng từ nhiều đối tượng, báo cáo "Chính sách giá", "Sổ chi tiết bán hàng theo mã quy cách", "Thống kê số lượng tồn kho và số lượng đặt hàng chưa giao", "Giấy đề nghị thanh toán công nợ", "Thông báo công nợ ngoại tệ" | [HT-05] |
| H-07 | Mua hàng MISA có: chứng từ mua hàng nhiều hóa đơn, bỏ đối trừ, bù trừ công nợ, "Tình hình thực hiện hợp đồng mua", "Chi tiết công nợ phải trả theo mặt hàng" | [HT-13] |
### 2.2 Khách hàng, nhà cung cấp, hàng hóa
| # | Quan sát | Nguồn |
|---|---|---|
| H-10 | Khách hàng chủ yếu là **cơ sở y tế công**: bệnh viện đa khoa, trung tâm y tế khu vực, nhà thuốc bệnh viện. Ngoài ra có công ty thiết bị y tế, cửa hàng vật tư, cá nhân | [HT-01], [HT-08], [HT-16] |
| H-11 | Danh mục nhà cung cấp lẫn các đối tượng khác (Sở Y tế Hà Nội, khách sạn) | [HT-14] |
| H-12 | Mặt hàng: thuốc thử sinh hóa (Glenbio/Anh, Horiba ABX/Pháp), test nhanh, linh kiện máy (Electrode Housing), cột sắc ký (TSKgel), bộ dụng cụ phẫu thuật (Pakistan) | [HT-07], [HT-14] |
| H-13 | Quy cách, hãng, xuất xứ bị ghép vào tên hàng: `Cholesterol (12x65ml)(Glenbio/Anh)` | [HT-10], [HT-12] |
| H-14 | Mã hàng không thống nhất: `GL8S105`, `GL8S107_T`, `01_P1001_B15`, `000885003` | [HT-07], [HT-15] |
| H-15 | Cùng mã hàng dùng nhiều đơn vị tính: mua theo **Hộp**, bán theo **ml** hoặc **Hộp**. Ví dụ 4 hộp Cholesterol 12x65ml = 3.120 ml | [HT-02], [HT-09], [HT-11] |
| H-16 | Đơn giá theo ml có phần thập phân (8.380,95 đ/ml) | [HT-03] |
| H-17 | Giá mua cùng mã biến động mạnh: AST giảm từ 2.900.000 xuống 1.050.000 đ/hộp | [HT-15] |

### 2.3 Mua hàng
| # | Quan sát | Nguồn |
|---|---|---|
| H-20 | Có các chức năng: đơn mua hàng, hợp đồng mua, chứng từ mua hàng hóa, mua dịch vụ, trả lại hàng mua, giảm giá hàng mua, nhận hóa đơn, trả tiền nhà cung cấp, đối trừ chứng từ | [HT-10] |
| H-21 | Chứng từ mua có: nhà cung cấp, người giao hàng, điều khoản thanh toán, số ngày được nợ, hạn thanh toán, chi phí mua hàng, tiền chiết khấu, thuế GTGT, giá trị nhập kho. Hạch toán Nợ 156 / Có 331 | [HT-11], [HT-12] |
| H-22 | Ký hiệu chứng từ: `NK` nhập kho, `MH` mua hàng (giá trị nhập kho = 0), `MDV` mua dịch vụ | [HT-10] |
| H-23 | `[Suy luận]` Chứng từ `MH` có thể là hàng mua giao thẳng cho khách, không qua kho | [HT-10] |
| H-24 | Khối lượng: 1.408 dòng chứng từ mua (01/01/2025–31/12/2026), tổng tiền thanh toán khoảng 19,7 tỷ đồng | [HT-10] |

### 2.4 Bán hàng và hóa đơn
| # | Quan sát | Nguồn |
|---|---|---|
| H-30 | Có các chức năng: báo giá, đơn đặt hàng, chứng từ bán hàng, hóa đơn, hóa đơn điều chỉnh/thay thế, giảm giá hàng bán, trả lại hàng bán, doanh thu nhận trước, thu tiền khách hàng, đối trừ chứng từ | [HT-01], [HT-05] |
| H-31 | Một chứng từ bán **đồng thời là phiếu xuất kho và hóa đơn**; có lựa chọn "Chưa thu tiền" hoặc "Thu tiền ngay". Hạch toán Nợ 131 / Có 5111 | [HT-02], [HT-03] |
| H-32 | Hóa đơn điện tử có ký hiệu `1C26TBK`, trạng thái "Đã cấp mã" (hóa đơn có mã của cơ quan thuế) | [HT-01], [HT-02] |
| H-33 | Thuế GTGT 5% trên thuốc thử xét nghiệm | [HT-03] |
| H-34 | 419 chứng từ bán từ 01/01 đến 07/10/2026, tổng khoảng 10,17 tỷ đồng | [HT-01] |
| H-35 | `[Suy luận]` Có dấu hiệu **mua theo đơn và bán ngay**: NK01125/24 và BH01196/24 cùng ngày 07/10/2026, cùng mặt hàng, cùng số lượng | [HT-02], [HT-11] |
| H-36 | MISA có báo cáo "Sổ chi tiết theo dõi tình trạng bảo hành", tức là doanh nghiệp có nhu cầu theo dõi bảo hành | [HT-06] |

### 2.5 Công nợ
| # | Quan sát | Nguồn |
|---|---|---|
| H-40 | Báo cáo công nợ đang dùng: phân tích nợ quá hạn và theo tuổi nợ, biên bản đối chiếu xác nhận công nợ, thông báo công nợ, tổng hợp theo nhóm khách, chi nhánh, phòng ban | [HT-05], [HT-13] |
| H-41 | Ví dụ Trung tâm Y tế khu vực Hàm Yên (01/01–09/10/2026): dư nợ đầu kỳ 111,6 triệu, phát sinh nợ 1,28 tỷ, đã thu 963 triệu, **dư cuối kỳ 428,6 triệu**. Khách thuộc nhóm `VIENB2K` | [HT-18] |

### 2.6 Vấn đề hiện trạng
| # | Vấn đề | Hệ quả `[Suy luận]` |
|---|---|---|
| P-01 | Cột Số lô và Hạn sử dụng có trên mẫu nhưng để trống [HT-12], [HT-19] | Không truy xuất được lô, không thu hồi được hàng |
| P-02 | Không cảnh báo cận hạn và hết hạn | Có rủi ro giao hàng hết hạn cho bệnh viện |
| P-03 | Quy cách, hãng, xuất xứ nằm trong tên hàng | Không báo cáo được theo hãng hay xuất xứ; dễ tạo mã trùng |
| P-04 | Đơn vị tính hỗn hợp, không có quy đổi chính thức | Tồn kho theo hộp và theo ml dễ lệch nhau |
| P-05 | Không có hồ sơ chất lượng, số lưu hành gắn với hàng và lô | Khó đáp ứng hồ sơ thầu và yêu cầu giao hàng của bệnh viện |
| P-06 | Không có phê duyệt trong hệ thống | Khó truy cứu trách nhiệm; giá bán và chiết khấu không được kiểm soát |
| P-07 | Danh mục đối tác lẫn lộn | Báo cáo mua hàng và công nợ sai lệch |
| P-08 | Không theo dõi serial, lắp đặt, bảo hành, bảo trì máy trong hệ thống | Không biết máy nào đang ở đâu, còn bảo hành không, đến hạn bảo trì chưa |
| P-09 | Không theo dõi hợp đồng hoặc kết quả trúng thầu với bệnh viện | Không kiểm soát được giá và số lượng còn được giao theo hợp đồng |
| P-10 | Kinh doanh do kế toán ghi nhận sau | Không có báo cáo bán hàng, lợi nhuận theo nhân viên kinh doanh hoặc hợp đồng theo thời gian thực |

---

## 3. Phạm vi và lộ trình

### 3.1 Danh sách phân hệ
| Phân hệ | Tên | Mã yêu cầu | Mục |
|---|---|---|---|
| M01 | Quản trị hệ thống | SYS | 6 |
| M02 | Danh mục dùng chung | MD | 7 |
| M03 | Mua hàng và nhà cung cấp | PUR | 8 |
| M04 | Quản lý kho | INV | 9 |
| M05 | Bán hàng và phân phối | SAL, EINV | 10 |
| M06 | Quản lý đấu thầu | TND | 11 |
| M07 | Công nợ, thu chi và ngân hàng | AR, AP, CSH | 12 |
| M08 | Kế toán và giá vốn | CST, ACC, TAX | 13 |
| M09 | Thiết bị, bảo hành và hồ sơ sản phẩm | EQP, SVC, MED | 14 |
| M10 | Báo cáo và dashboard | RPT | 15 |

```
                 ┌──────────────── M01 QUẢN TRỊ HỆ THỐNG ────────────────┐
                 │ Tổ chức · Người dùng · Phân quyền · Phê duyệt · Kỳ · Nhật ký │
                 └────────────────────────────────────────────────────────┘
                                  M02 DANH MỤC DÙNG CHUNG
     M03 MUA HÀNG ──► M04 KHO (lô/hạn/serial/QC) ◄── M05 BÁN HÀNG ◄── M06 ĐẤU THẦU
          │                    │                        │                │
          └────────► M07 CÔNG NỢ · THU CHI · NGÂN HÀNG ◄─┘                │
                               │                                         │
                     M08 KẾ TOÁN & GIÁ VỐN        M09 THIẾT BỊ · BẢO HÀNH ◄┘
                               │
                     M10 BÁO CÁO · DASHBOARD (truy ngược chứng từ nguồn)
```

### 3.2 Lộ trình đề xuất
| Giai đoạn | Nội dung | Điều kiện kết thúc |
|---|---|---|
| **P1** | M01, M02, M03, M04, M05 (trừ phát hành hóa đơn điện tử), M06 (gói thầu, kết quả, hợp đồng, kiểm soát giao), M07, M08 (giá vốn, quy tắc hạch toán, bút toán tự động, khóa kỳ), M09 (serial, bảo hành cơ bản, hồ sơ sản phẩm), M10 (báo cáo cơ bản, dashboard điều hành). Hóa đơn điện tử và báo cáo thuế vẫn làm tại MISA | Đạt toàn bộ kịch bản AT-01..AT-12 (mục 18). Chạy song song với MISA 1–2 tháng; tồn kho và công nợ khớp nhau |
| **P2** | Phát hành hóa đơn điện tử, nhập khẩu, sửa chữa và bảo trì, tuân thủ y tế nâng cao, bảng kê thuế, sổ chi tiết và sổ tổng hợp, dashboard nâng cao | Ngừng lập hóa đơn trên MISA |
| **P3** | Báo cáo tài chính, tờ khai thuế, ngân sách; khế ước vay, TSCĐ, CCDC nếu thay MISA hoàn toàn | Ngừng dùng MISA (nếu D-01 chọn thay thế hoàn toàn) |

`[Suy luận]` Không thay MISA hoàn toàn ngay từ P1 vì báo cáo tài chính và báo cáo thuế có rủi ro tuân thủ cao. Bút toán tự động vẫn làm từ P1 để đối chiếu kho với kế toán và khóa kỳ (BR-ACC-02, AT-11).

### 3.3 Đối chiếu chức năng với MISA SME (fit-gap)
Cột "MISA" chỉ liệt kê chức năng **thấy được trên ảnh hiện trạng**.

| Phân hệ MISA | Chức năng MISA thấy được | Phân hệ ERP | Khác biệt của ERP | GĐ |
|---|---|---|---|---|
| Bàn làm việc | Bàn làm việc, tìm tính năng, tìm chứng từ [HT-05] | M01, M10 | Bàn làm việc theo vai trò (5.8) | P1 |
| Quỹ, Ngân hàng | Thu, chi, báo cáo quỹ, ngân hàng [HT-01], [HT-05] | M07 | Phê duyệt chi, tạm ứng, kiểm kê quỹ, dự kiến dòng tiền | P1 |
| Mua hàng | Đơn mua, hợp đồng mua, mua hàng hóa, mua nhiều hóa đơn, mua dịch vụ, trả lại, giảm giá, nhận hóa đơn, trả tiền, đối trừ, bù trừ [HT-10], [HT-13] | M03, M07 | Đề nghị mua, yêu cầu báo giá NCC, so sánh báo giá, nhận hàng tách khỏi nhập kho, hàng lỗi | P1 |
| Bán hàng | Báo giá, đơn đặt hàng, chứng từ bán, hóa đơn, điều chỉnh, thay thế, chiết khấu thương mại, giảm giá, trả lại, doanh thu nhận trước, thu tiền, thu hàng loạt, đối trừ [HT-05] | M05, M07 | Tách đơn, xuất kho, giao hàng, doanh thu, hóa đơn; duyệt giá; hạn mức tín dụng; giữ chỗ; FEFO | P1 |
| Hợp đồng | Hợp đồng mua, bán [HT-05], [HT-13] | M06 | Gói thầu, hồ sơ dự thầu, kết quả, phân bổ, tiến độ giao, nghiệm thu | P1 |
| Hóa đơn điện tử | Hóa đơn đã cấp mã [HT-01], [HT-02] | M05 | P1 vẫn phát hành trên MISA | P2 |
| Kho | Nhập, xuất, báo cáo kho [HT-11], [HT-12] | M04 | **Khác biệt lớn nhất**: lô, hạn dùng, serial, trạng thái chất lượng, thu hồi, bốn loại tồn | P1 |
| Giá thành, Tổng hợp, Thuế | Có trên menu [HT-05] | M08 | Giá vốn hàng bán, bút toán tự động, khóa kỳ ở P1; BCTC, thuế ở P2–P3 | P1–P3 |
| CCDC, TSCĐ, Khế ước vay, Ngân sách | Có trên menu [HT-05] | M08, M07 | Chỉ làm khi D-01 chọn thay MISA hoàn toàn | P3 |
| Tiền lương, Kết nối vay vốn | Có trên menu [HT-05] | — | Ngoài phạm vi | — |
| (Không có) | — | M09 | Bảo hành, sửa chữa, hồ sơ sản phẩm `[Suy luận]` | P1/P2 |

### 3.4 Ngoài phạm vi
Tiền lương, CRM đầy đủ, mobile app riêng (giao diện web responsive vẫn trong phạm vi), kết nối vay vốn, sản xuất / giá thành sản xuất, microservices.

---

## 4. Vai trò người dùng
| Vai trò | Trách nhiệm chính |
|---|---|
| Quản trị hệ thống | Tổ chức, người dùng, vai trò, tham số, cấu hình số chứng từ và luồng duyệt |
| Giám đốc | Xem toàn bộ; duyệt hợp đồng, giá dưới giá sàn, chiết khấu vượt ngưỡng, vượt hạn mức tín dụng, hủy hàng, thu hồi lô, chính sách xuất vượt tồn khả dụng |
| Giám đốc chi nhánh | Như Giám đốc nhưng trong phạm vi chi nhánh được giao |
| Trưởng phòng kinh doanh | Duyệt đơn bán (giá, hạn mức) trong ngưỡng; quản lý nhân viên kinh doanh |
| Nhân viên kinh doanh | Lập đơn bán; theo dõi giao hàng và công nợ khách mình phụ trách |
| Nhân viên đấu thầu / pháp chế | Gói thầu, hồ sơ dự thầu, hồ sơ pháp lý doanh nghiệp và sản phẩm, công khai giá |
| Nhân viên mua hàng | Lập đơn mua (kèm hóa đơn NCC), theo dõi hàng về (D-54) |
| Quản lý kho | Duyệt chứng từ kho, kiểm kê, chuyển kho, khóa kỳ kho |
| Thủ kho / Nhân viên kho | Nhận hàng, kiểm đếm, nhập, xuất, ghi nhận lô/hạn/serial |
| Phụ trách chất lượng (QA) | Hồ sơ chất lượng, chuyển trạng thái lô, thu hồi |
| Kỹ thuật viên | Bàn giao, lắp đặt, bảo hành, sửa chữa |
| Nhân viên giao hàng | Cập nhật trạng thái giao, biên bản giao nhận |
| Kế toán công nợ | Hóa đơn, công nợ, đối chiếu, đối trừ |
| Thủ quỹ / Kế toán thanh toán | Phiếu thu, phiếu chi, ngân hàng |
| Kế toán trưởng | Giá vốn, quy tắc hạch toán, khóa sổ, duyệt chứng từ tài chính |
| Kế toán thuế / tổng hợp | Bảng kê, tờ khai, bút toán tổng hợp, kết chuyển cuối kỳ |
| Người dùng chỉ xem | Xem báo cáo được cấp quyền |

`[Suy luận]` Doanh nghiệp nhỏ thường cho một người kiêm nhiều vai trò. Hệ thống cho gán nhiều vai trò cho một người dùng (D-08), nhưng vẫn áp dụng tách biệt nhiệm vụ (BR-SYS-14).

---

## 5. Quy tắc nghiệp vụ chung (bắt buộc)

### 5.1 Trạng thái chứng từ [ĐT]
**Nhóm A - chứng từ ghi sổ** (phiếu nhập, phiếu xuất, chuyển kho, điều chỉnh, kiểm kê, đơn mua, đơn bán, trả hàng / giảm giá mua bán, phân bổ chi phí mua, phiếu thu, phiếu chi, bút toán tổng hợp; D-54):
```
DRAFT (Nháp) -> SUBMITTED (Chờ duyệt) -> APPROVED (Đã duyệt) -> POSTED (Đã ghi sổ)
SUBMITTED -> REJECTED (Bị từ chối) -> DRAFT
DRAFT / REJECTED / APPROVED (chưa ghi sổ) -> CANCELLED (Đã hủy)
POSTED -> REVERSED (Đã đảo) - chỉ qua chứng từ đảo
```

**Nhóm B - chứng từ điều phối** (gói thầu, hợp đồng; sau D-54 đơn mua, đơn bán là chứng từ ghi sổ nhóm A):
```
DRAFT -> SUBMITTED -> APPROVED -> IN_PROGRESS (Đang thực hiện) -> COMPLETED (Hoàn tất) / CLOSED (Đóng phần còn lại)
SUBMITTED -> REJECTED -> DRAFT
DRAFT / REJECTED / APPROVED (chưa phát sinh chứng từ sau) -> CANCELLED
```
Trạng thái riêng của từng loại (ví dụ trạng thái giao hàng của đơn bán, gói thầu "Trúng / Trượt") mô tả trong phân hệ.

| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-GEN-01 | DRAFT và REJECTED được sửa; SUBMITTED, APPROVED không được sửa (muốn sửa phải trả về DRAFT, có lý do). | P1 |
| BR-GEN-02 | **Chỉ chứng từ nhóm A ở trạng thái POSTED** mới ảnh hưởng tồn thực tế và số liệu tài chính. Chứng từ nhóm B chỉ ảnh hưởng chỉ tiêu điều phối (giữ chỗ, hàng đang về, số còn được giao) [ĐT]. | P1 |
| BR-GEN-03 | **Không xóa cứng** chứng từ đã phát sinh giao dịch. Chứng từ POSTED muốn hủy thì tạo **chứng từ đảo** tham chiếu chứng từ gốc; bút toán gốc giữ nguyên [ĐT]. Chỉ chứng từ DRAFT chưa từng gửi duyệt mới được xóa. | P1 |
| BR-GEN-04 | Không đảo chứng từ khi đã có chứng từ sau phụ thuộc (phiếu nhập có lô đã xuất, hóa đơn đã thu tiền). Hệ thống liệt kê chứng từ phụ thuộc; phải đảo chúng trước. | P1 |
| BR-GEN-05 | Ma trận phê duyệt cấu hình theo loại chứng từ, chi nhánh, giá trị, chiết khấu (D-04). Loại chứng từ không cần duyệt chuyển thẳng DRAFT → POSTED nhưng vẫn ghi người, thời điểm. | P1 |
| BR-GEN-06 | Duyệt, ghi sổ, hủy, đảo, điều chỉnh đều **kiểm tra quyền phía server** và ghi nhật ký [ĐT]. | P1 |
| BR-GEN-07 | Mọi thay đổi quan trọng lưu người thực hiện, thời điểm, **lý do**, dữ liệu trước/sau [ĐT]. | P1 |
| BR-GEN-08 | Đính kèm file cho chứng từ và danh mục (giới hạn loại, kích thước: BR-SYS-22). | P1 |
| BR-GEN-09 | **Truy vết hai chiều** từ chứng từ nguồn đến chứng từ đích: đơn mua → phiếu nhập kho → trả hàng / phân bổ chi phí → phiếu chi; gói thầu → hợp đồng → đơn bán → phiếu xuất kho → nghiệm thu → trả hàng → phiếu thu (D-54) [ĐT]. | P1 |
| BR-GEN-10 | Không ghi sổ hoặc đảo chứng từ có ngày hạch toán thuộc **kỳ đã khóa** (BR-SYS-30). | P1 |
| BR-GEN-11 | In chứng từ theo mẫu cấu hình, có logo, thông tin công ty, chữ ký như mẫu hiện tại [HT-04], [HT-12]; số tiền bằng chữ tiếng Việt. | P1 |
| BR-GEN-12 | Mỗi chứng từ có ngày chứng từ và ngày hạch toán; ngày hạch toán quyết định kỳ. | P1 |

### 5.2 Ghi sổ và đảo
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-GEN-20 | Ghi sổ là **một giao dịch nguyên tử**: chứng từ, sổ phụ kho, sổ phụ công nợ/quỹ, bút toán kế toán, số dư, nhật ký cùng thành công hoặc cùng rollback [ĐT]. | P1 |
| BR-GEN-21 | Chứng từ đảo sinh bút toán ngược đúng từng dòng của chứng từ gốc (cùng lô, serial, giá trị), ngày hạch toán là ngày đảo (không được thuộc kỳ đã khóa). | P1 |
| BR-GEN-22 | Đảo một chứng từ cập nhật lại chỉ tiêu của chứng từ nguồn (ví dụ đảo phiếu xuất thì số "đã giao" của đơn bán và hợp đồng giảm tương ứng). | P1 |

### 5.3 Ma trận ảnh hưởng chứng từ
Ký hiệu: `+` tăng, `−` giảm, `·` không ảnh hưởng. Giá trị kế toán theo quy tắc hạch toán cấu hình (BR-ACC-02). Các ô có dấu `*` phụ thuộc quyết định D-34, D-35.

| Chứng từ (khi) | Tồn thực tế | Giữ chỗ | Hàng đang về | Công nợ | Doanh thu | Giá vốn | Quỹ / NH | Chỉ tiêu nguồn |
|---|---|---|---|---|---|---|---|---|
| Đơn mua (APPROVED) | · | · | + | · | · | · | · | · |
| Đơn mua (POSTED, D-54) | + (phiếu nhập kho liên kết) | · | − | Phải trả + `*` | · | · | · | · |
| Phiếu chi trả NCC (POSTED) | · | · | · | Phải trả − | · | · | − | Đơn mua: + đã trả |
| Trả hàng NCC (POSTED) | − (phiếu xuất trả) | · | · | Phải trả − | · | · | · | Đơn mua: + đã trả lại, + đã giảm |
| Giảm giá hàng mua (POSTED) | · | · | · | Phải trả − | · | Điều chỉnh giá trị nhập | · | Đơn mua: + đã giảm |
| Phân bổ chi phí mua (POSTED) | · | · | · | · | · | Điều chỉnh giá trị nhập | · | Đơn mua dịch vụ: + đã phân bổ |
| Đơn bán (APPROVED) | · | + (nếu chọn giữ hàng) | · | · | · | · | · | Hợp đồng: + đã đặt |
| Đơn bán (POSTED, D-54) | − (phiếu xuất kho liên kết) | − | · | Phải thu + | + `*` | + `*` | · | Hợp đồng: + đã giao |
| Khách nhận hàng (trên đơn bán) | · | · | · | · | · | · | · | Đơn bán: trạng thái giao |
| Phiếu thu (POSTED) | · | · | · | Phải thu − | · | · | + | Đơn bán: + đã thu |
| Trả hàng bán (POSTED) | + (CÁCH LY) | · | · | Phải thu − | − | − | · | Đơn bán: + đã trả lại; hợp đồng: − đã giao |
| Giảm giá hàng bán (POSTED) | · | · | · | Phải thu − | − | · | · | Đơn bán: + đã giảm |
| Chuyển kho (POSTED) | − nguồn / + đích | · | · | · | · | · | · | · |
| Kiểm kê, điều chỉnh (POSTED) | ± | · | · | · | · | Chênh lệch giá trị | · | · |
| Chứng từ đảo | Ngược chứng từ gốc | | | | | | | Ngược chứng từ gốc |

Theo D-54 (2026-10-12), giống cách làm trên MISA (H-31): ghi sổ đơn bán làm đồng thời xuất kho, ghi doanh thu, công nợ; ghi sổ đơn mua làm đồng thời nhập kho, công nợ. Phiếu kho vẫn là chứng từ riêng của phân hệ kho, sinh tự động và liên kết hai chiều với đơn.

### 5.4 Các loại tồn [ĐT]
| Chỉ tiêu | Định nghĩa |
|---|---|
| **Tồn thực tế** | Σ nhập − Σ xuất từ chứng từ POSTED, theo (chi nhánh, kho, vị trí, hàng, lô, serial, trạng thái chất lượng) |
| **Tồn giữ chỗ** | Σ số lượng giữ chỗ còn hiệu lực từ đơn bán, hợp đồng, phiếu xuất chưa ghi sổ |
| **Tồn khả dụng** | Tồn thực tế ở trạng thái ĐẠT, chưa hết hạn − Tồn giữ chỗ |
| **Hàng đang về** | Σ số lượng trên đơn mua APPROVED/IN_PROGRESS − Σ đã nhập kho (trừ phần đã đóng) + hàng chuyển kho đang vận chuyển |
| **Hàng đã xuất chưa ghi nhận doanh thu** | Hàng đã xuất kho bán nhưng chưa đủ điều kiện ghi nhận doanh thu (D-35) |

| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-GEN-30 | Màn hình lập đơn bán, đơn mua, phiếu xuất hiển thị đồng thời bốn chỉ tiêu tồn theo kho và lô. | P1 |
| BR-GEN-31 | **Tồn thực tế không bao giờ âm** ở mọi cấp. **Xuất vượt tồn khả dụng** (lấy phần đang giữ chỗ cho đơn khác) chỉ được phép khi có chính sách đặc biệt được phê duyệt và ghi nhật ký (D-10) [ĐT]. | P1 |

### 5.5 Tiền, thuế, số liệu
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-GEN-40 | Tiền tệ hạch toán VND. Tiền và số lượng lưu kiểu DECIMAL; không dùng số thực dấu phẩy động. Làm tròn theo quy tắc cấu hình (D-05). | P1 |
| BR-GEN-41 | Chứng từ mua, bán hỗ trợ ngoại tệ kèm tỷ giá; quy đổi ra VND khi ghi sổ. | P2 |
| BR-GEN-42 | Thuế suất GTGT cấu hình theo hàng, ghi đè được theo dòng. `[Chưa kiểm chứng]` Danh sách thuế suất và thời hạn áp dụng do kế toán trưởng xác nhận. | P1 |
| BR-GEN-43 | Thời gian lưu UTC, hiển thị UTC+7. Ngày chứng từ, ngày hạch toán là kiểu ngày (không giờ). Định dạng số và ngày theo chuẩn Việt Nam. | P1 |

### 5.6 Chống xử lý trùng và đồng thời [ĐT]
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-GEN-50 | Gửi lại cùng một yêu cầu (bấm hai lần, mạng chập chờn, client tự thử lại) **không được tạo hai chứng từ hoặc ghi sổ hai lần**. Lệnh tạo, gửi duyệt, duyệt, ghi sổ, đảo, thu, chi đều chống xử lý trùng. | P1 |
| BR-GEN-51 | Ghi sổ một chứng từ đã POSTED trả lỗi nghiệp vụ, không ghi lần hai. | P1 |
| BR-GEN-52 | Hai người cùng xuất một lô, cùng phân bổ tiền cho một hóa đơn, cùng giao trên một dòng hợp đồng, cùng chi một quỹ: không được làm số dư âm hoặc vượt phần còn lại [ĐT]. | P1 |
| BR-GEN-53 | Hai người cùng sửa một chứng từ hoặc danh mục: người lưu sau nhận thông báo dữ liệu đã thay đổi, không ghi đè im lặng. | P1 |
| BR-GEN-54 | Tác vụ nền (tính giá vốn, cảnh báo, gửi email) chạy lại không tạo kết quả trùng. | P1 |

### 5.7 Tổ chức, chiều phân tích và phạm vi dữ liệu
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-GEN-60 | Mọi chứng từ thuộc một **công ty** và một **chi nhánh**; kho thuộc chi nhánh [ĐT]. P1 vận hành một công ty, nhiều chi nhánh; mô hình dữ liệu sẵn sàng nhiều công ty (D-23). | P1 |
| BR-GEN-61 | Chứng từ và dòng chứng từ gắn được các chiều: phòng ban, nhân viên kinh doanh, hợp đồng, gói thầu, khoản mục chi phí, mã thống kê. Chiều phân tích kế thừa từ chứng từ nguồn sang chứng từ sinh ra. | P1 |
| BR-GEN-62 | Báo cáo lọc và nhóm theo mọi chiều phân tích và theo công ty, chi nhánh, kho. | P1 |

### 5.8 Bàn làm việc và tiện ích người dùng
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-UX-01 | Bàn làm việc theo vai trò: chứng từ chờ mình duyệt, chứng từ bị từ chối, cảnh báo hạn dùng, hợp đồng sắp hết, nợ đến hạn (H-05). | P1 |
| BR-UX-02 | Tìm tính năng theo tên; phím tắt cho thao tác thường dùng. | P1 |
| BR-UX-03 | Tìm chứng từ trên mọi loại theo số, ngày, đối tượng, hàng, lô, serial, số tiền, trạng thái. | P1 |
| BR-UX-04 | Nhân bản chứng từ thành DRAFT mới; chứng từ mẫu cho giao dịch lặp lại. | P1 |
| BR-UX-05 | Nhập từ Excel theo mẫu: danh mục, đơn mua, đơn bán, phiếu nhập/xuất, số dư đầu kỳ. Kiểm tra toàn bộ file trước; báo lỗi theo dòng; file có lỗi thì không ghi gì. | P1 |
| BR-UX-06 | Danh sách: lọc, sắp xếp, phân trang **thực hiện ở server**; chọn và ẩn cột; lưu bố cục; xuất Excel [ĐT]. | P1 |
| BR-UX-07 | Tra cứu mã số thuế tự điền thông tin đối tác. `[Chưa kiểm chứng]` Nguồn tra cứu (D-25). | P2 |
| BR-UX-08 | Kiểm tra và tính lại số dư tồn kho, công nợ, quỹ từ sổ phụ; báo chênh lệch. Chỉ tái tạo bản đọc nhanh, không sửa sổ phụ. | P1 |

---

## 6. M01 - Quản trị hệ thống (SYS)

### 6.1 Tổ chức, người dùng, phân quyền
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-SYS-01 | Danh mục **công ty, chi nhánh, phòng ban, kho** và quan hệ giữa chúng [ĐT]. | P1 |
| BR-SYS-02 | Người dùng, vai trò, quyền hạn; một người nhiều vai trò. | P1 |
| BR-SYS-03 | Quyền theo **chức năng × hành động**: xem / tạo / sửa / gửi duyệt / duyệt / ghi sổ / hủy / đảo / in / xuất file. | P1 |
| BR-SYS-04 | Quyền theo **phạm vi dữ liệu**: công ty, chi nhánh, kho được phép; nhân viên kinh doanh chỉ thấy khách hàng, đơn, công nợ của mình hoặc nhóm mình [ĐT]. | P1 |
| BR-SYS-05 | Ẩn giá vốn, giá mua, lợi nhuận với vai trò không có quyền. Thủ kho không sửa được giá bán [TK]. | P1 |
| BR-SYS-06 | Người dùng không có quyền **không truy cập được** dữ liệu của chi nhánh/kho khác, kể cả khi gọi API trực tiếp với ID của dữ liệu đó [ĐT]. | P1 |

### 6.2 Cấu hình nghiệp vụ
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-SYS-10 | **Cấu hình số chứng từ**: tiền tố, năm, chi nhánh, độ dài số thứ tự, chu kỳ đặt lại; số duy nhất theo loại chứng từ, công ty và chu kỳ; không nhảy số trùng khi nhiều người tạo cùng lúc (D-03); sửa cấu hình theo D-48 [ĐT]. | P1 |
| BR-SYS-11 | **Cấu hình luồng phê duyệt**: theo loại chứng từ, chi nhánh, ngưỡng giá trị, chiết khấu; một hoặc nhiều cấp; người duyệt theo vai trò hoặc người cụ thể; ủy quyền duyệt có thời hạn [ĐT]. | P1 |
| BR-SYS-12 | Cấu hình trạng thái nghiệp vụ hiển thị và các bước bắt buộc/tùy chọn theo loại chứng từ, trong khuôn khổ vòng đời mục 5.1 [ĐT]. | P1 |
| BR-SYS-13 | Tham số hệ thống: số chữ số thập phân, phương pháp giá vốn, ngưỡng cảnh báo, ngưỡng duyệt, mẫu in. Thay đổi tham số ghi nhật ký; tham số ảnh hưởng số liệu đã ghi sổ bị khóa sau khi phát sinh. | P1 |
| BR-SYS-14 | Tách biệt nhiệm vụ: người lập không tự duyệt chứng từ của mình (bật/tắt theo loại chứng từ). | P1 |

### 6.3 Kỳ kế toán và kỳ kho
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-SYS-30 | **Kỳ kế toán** và **kỳ kho** theo tháng, khóa riêng theo công ty/chi nhánh và theo phân hệ (kho, công nợ, quỹ, kế toán); chi tiết D-46, D-47 [ĐT]. | P1 |
| BR-SYS-31 | Khóa kỳ kho phải sau khi đã tính giá vốn kỳ đó; khóa kỳ kế toán phải sau khi khóa kỳ kho. Hệ thống kiểm tra điều kiện trước khi khóa. | P1 |
| BR-SYS-32 | Mở lại kỳ đã khóa phải được Kế toán trưởng hoặc Giám đốc duyệt, có lý do, ghi nhật ký (cách làm: D-45). | P1 |

### 6.4 Nhật ký và bảo mật
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-SYS-20 | Nhật ký **đăng nhập** (thành công, thất bại, đăng xuất), **thao tác** và **thay đổi dữ liệu** (giá trị trước/sau): người thực hiện, thời điểm, thực thể, ID, số chứng từ, request ID, IP. Không sửa/xóa được qua ứng dụng (D-14) [ĐT]. | P1 |
| BR-SYS-21 | Ghi nhật ký bắt buộc: đổi trạng thái chứng từ, đổi giá và chiết khấu, vượt hạn mức, chọn lô khác FEFO, đổi trạng thái chất lượng, khóa/mở kỳ, phân quyền, xuất vượt tồn khả dụng. | P1 |
| BR-SYS-22 | **File tải lên**: giới hạn kích thước và danh sách loại file cho phép (cấu hình); kiểm tra loại thật của file, không chỉ phần mở rộng [ĐT]. | P1 |
| BR-SYS-23 | Phiên hết hạn khi không thao tác; khóa tạm tài khoản khi sai mật khẩu nhiều lần; chính sách độ mạnh mật khẩu. | P1 |
| BR-SYS-24 | Xác thực hai lớp cho Giám đốc, Kế toán trưởng, Quản trị hệ thống. | P2 |
| BR-SYS-25 | Thông báo trong ứng dụng và email: chờ duyệt, cảnh báo hạn dùng, hợp đồng sắp hết, nợ quá hạn, giấy tờ sắp hết hạn. | P1 |
| BR-SYS-26 | Ký số chứng từ điện tử. `[Chưa kiểm chứng]` Nhà cung cấp chữ ký số và giá trị pháp lý. | P3 |

---

## 7. M02 - Danh mục dùng chung (MD)

### 7.1 Hàng hóa
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-MD-01 | Mỗi hàng hóa có **mã duy nhất, tên, nhóm, đơn vị tính, trạng thái hoạt động** và thuộc tính theo loại hàng [ĐT]. Mã không đổi sau khi phát sinh giao dịch. Mã cũ MISA (H-14) không nhập, không hiển thị trên màn hình và mẫu Excel (D-55); giá trị đã lưu được giữ. | P1 |
| BR-MD-02 | **Loại hàng**: Máy móc - thiết bị / Vật tư tiêu hao / Thuốc thử - hóa chất / Linh kiện - phụ tùng / Dụng cụ / Dịch vụ / Khác. Loại hàng quyết định trường bắt buộc và quy tắc quản lý. | P1 |
| BR-MD-03 | Nhóm hàng dạng cây. Thông tin: tên thương mại, model, quy cách, **nhãn hiệu, nhà sản xuất, xuất xứ** (danh mục riêng, P-03), mô tả kỹ thuật, ảnh [ĐT]. | P1 |
| BR-MD-04 | Cờ quản lý: theo lô, hạn dùng, serial, hàng dịch vụ (không quản lý tồn). Không tắt cờ khi đã phát sinh giao dịch. Máy móc mặc định bắt buộc serial; thuốc thử, hóa chất, vật tư tiêu hao mặc định bắt buộc lô và hạn dùng. | P1 |
| BR-MD-05 | Pháp lý y tế: số lưu hành / giấy phép nhập khẩu, hiệu lực, chủ sở hữu, phân loại rủi ro TTBYT (A/B/C/D). `[Chưa kiểm chứng]` Căn cứ Nghị định 98/2021/NĐ-CP và văn bản sửa đổi. | P1 |
| BR-MD-06 | Thông tin hóa chất: tên hóa học, số CAS, nồng độ, phân loại nguy hiểm, SDS [TK]. | P2 |
| BR-MD-07 | Điều kiện bảo quản: nhiệt độ min/max, hàng lạnh, hàng nguy hiểm. | P1 |
| BR-MD-08 | Máy móc: thời hạn bảo hành mặc định, chu kỳ bảo trì, yêu cầu lắp đặt, yêu cầu hiệu chuẩn/kiểm định. | P1 |
| BR-MD-09 | **Định mức tồn** tối thiểu/tối đa theo hàng và kho; ngưỡng ngày tồn lâu. | P1 |
| BR-MD-10 | Hàng thay thế, hàng tương đương, phụ kiện đi kèm; hàng bộ (kit) với thành phần (D-20). | P2 |
| BR-MD-11 | Mã vạch, GTIN, UDI: một hàng nhiều mã. | P2 |
| BR-MD-12 | Ngừng kinh doanh thay vì xóa; không xóa vật lý hàng đã phát sinh giao dịch. | P1 |

### 7.2 Đơn vị tính
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-MD-20 | Danh mục đơn vị tính. Mỗi hàng có **đơn vị tồn kho cơ sở** và nhiều đơn vị giao dịch kèm hệ số quy đổi (ví dụ `1 Hộp = 780 ml`, H-15) [ĐT]. | P1 |
| BR-MD-21 | Chứng từ lưu số lượng giao dịch, đơn vị, hệ số tại thời điểm lập và số lượng quy đổi về đơn vị cơ sở. Không sửa hệ số đã dùng; thay đổi thì tạo hệ số mới có hiệu lực. | P1 |

### 7.3 Đối tác
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-MD-30 | Đối tượng có một hoặc nhiều vai trò: **Khách hàng, Nhà cung cấp, Đại lý**, Nhân viên, Khác [ĐT]. Chỉ đối tượng có vai trò phù hợp được chọn trên chứng từ (P-07). | P1 |
| BR-MD-31 | Khách hàng: mã, tên, loại khách (Bệnh viện công / Trung tâm y tế / Phòng khám - BV tư / Nhà thuốc / Doanh nghiệp / Đại lý / Cá nhân), nhóm, MST, địa chỉ hóa đơn, nhiều địa chỉ giao, nhiều người liên hệ, nhân viên kinh doanh, chi nhánh phụ trách. | P1 |
| BR-MD-32 | Điều khoản khách: số ngày nợ, **hạn mức tín dụng**, bảng giá, hạn dùng còn lại tối thiểu khi giao. | P1 |
| BR-MD-33 | **Đại lý**: chính sách chiết khấu đại lý, khu vực phân phối, hạn mức. | P1 |
| BR-MD-34 | Nhà cung cấp: mã, tên, quốc gia, **MST, địa chỉ (bắt buộc)**, liên hệ, điều khoản thanh toán, tiền tệ, hãng phân phối. Đơn mua hiển thị MST, địa chỉ người bán theo danh mục nhà cung cấp, không sửa trên đơn (2026-10-13). | P1 |
| BR-MD-35 | Bên mời thầu / chủ đầu tư: dùng chung danh mục khách hàng, có cờ "Bên mời thầu" (BR-TND-01). | P1 |

### 7.4 Kho, giá, thuế, tiền tệ và danh mục khác
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-MD-40 | Kho thuộc chi nhánh; vị trí kho tùy chọn, có điều kiện bảo quản và loại khu (bình thường / cách ly / hỏng - hết hạn) [ĐT]. | P1 |
| BR-MD-41 | **Kho ảo**: hàng đang vận chuyển, hàng gửi tại khách (máy đặt, cho mượn, ký gửi), hàng đang sửa chữa, hàng demo (D-19). | P1 |
| BR-MD-42 | **Thuế suất** và **cấu hình giá**: giá mua tham khảo, giá niêm yết, **giá sàn**, bảng giá theo nhóm khách/khách/đại lý, thời gian hiệu lực, đơn vị tính, bậc số lượng [ĐT]. | P1 |
| BR-MD-43 | **Tiền tệ và tỷ giá** theo ngày [ĐT]. | P2 |
| BR-MD-44 | **Danh mục hồ sơ, giấy phép, chứng từ sản phẩm**: loại hồ sơ (số lưu hành, CO, CQ, catalogue, ủy quyền hãng, giấy phép nhập khẩu...), bắt buộc theo loại hàng, có ngày hết hạn hay không [ĐT]. | P1 |
| BR-MD-45 | Danh mục **loại hợp đồng, loại/phương thức thanh toán, điều khoản thanh toán, phương thức giao hàng** [ĐT]. | P1 |
| BR-MD-46 | Tài khoản ngân hàng, quỹ tiền mặt, khoản mục thu chi [TK], danh mục lý do (điều chỉnh, hủy, trả hàng, giảm giá, từ chối). | P1 |
| BR-MD-47 | Hồ sơ năng lực và ủy quyền phân phối của nhà cung cấp; cảnh báo hết hạn. | P2 |

---

## 8. M03 - Mua hàng và nhà cung cấp (PUR)

### 8.1 Luồng và phân biệt chứng từ [ĐT]
```
Đơn mua hàng (duyệt theo ngưỡng; kèm hóa đơn NCC, lô / hạn dùng / serial)
   -> Ghi sổ: phiếu nhập kho (tự sinh, liên kết) + công nợ phải trả + bút toán -> Phiếu chi / UNC
   -> (khi cần) Trả hàng / giảm giá hàng mua, phân bổ chi phí mua
```
D-54 (2026-10-12): đơn mua là chứng từ ghi sổ duy nhất của nghiệp vụ mua. Mỗi lần nhận hàng (kèm hóa đơn) là một đơn mua; không theo dõi nhận nhiều đợt cho một đơn.

| Chứng từ | Ý nghĩa | Ảnh hưởng |
|---|---|---|
| Đơn mua hàng (đã duyệt) | Cam kết mua với NCC | Hàng đang về |
| Đơn mua hàng (đã ghi sổ) | Hàng về kho, hóa đơn NCC | Tồn thực tế (phiếu nhập kho liên kết), công nợ phải trả, giá trị nhập, bút toán |
| Trả hàng / giảm giá hàng mua | Trả lại NCC đúng lô / serial, hoặc NCC giảm giá | Tồn, công nợ, giá trị nhập |
| Phiếu chi / UNC | Thanh toán | Quỹ / ngân hàng, công nợ |

### 8.2 Yêu cầu
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-PUR-01 | ~~Đề nghị mua~~: **bỏ theo D-54**. Nhu cầu mua từ đơn bán thiếu hàng / hợp đồng trúng thầu / cảnh báo dưới định mức chuyển thành đơn mua nháp (Giai đoạn 7). | Bỏ |
| BR-PUR-02 | ~~Yêu cầu báo giá NCC~~: **bỏ theo D-54** (xuất phát từ đề nghị mua). | Bỏ |
| BR-PUR-03 | ~~So sánh báo giá~~: **bỏ theo D-54**. | Bỏ |
| BR-PUR-04 | **Đơn mua hàng** (D-54): NCC, kho nhập, ngày hạch toán, hàng, số lượng, đơn giá, chiết khấu, thuế, lô / hạn dùng / serial / vị trí theo dòng (hàng nhiều lô: mỗi lô một dòng), dòng dịch vụ, ngày giao dự kiến, hạn thanh toán, tiền tệ, hóa đơn NCC (không bắt buộc); có duyệt theo ngưỡng. | P1 |
| BR-PUR-05 | Theo dõi theo dòng đơn mua đã ghi sổ: đã trả NCC, giá trị đã giảm, chi phí đã phân bổ, còn trả được. Không còn nhận nhiều đợt / đóng phần còn lại (D-54). | P1 |
| BR-PUR-06 | **Đơn mua theo đơn bán / hợp đồng**: khi hàng về thì giữ chỗ cho đơn bán hoặc hợp đồng đó (H-35). | P1 |
| BR-PUR-07 | ~~Phiếu nhận hàng~~: **bỏ theo D-54**. Hàng lỗi khi nhận: không ghi vào đơn mua, hoặc nhập rồi trả NCC (BR-PUR-14). | Bỏ |
| BR-PUR-08 | **Phiếu nhập kho** sinh khi ghi sổ đơn mua (liên kết hai chiều), hoặc theo nghiệp vụ được phép (mục 9.3); ghi lô, hạn dùng, serial, vị trí, đơn giá sau chiết khấu (D-54). | P1 |
| BR-PUR-09 | **Hóa đơn mua** ghi trên đơn mua: số, ký hiệu, ngày, MST người bán (D-54). Chống trùng theo (MST người bán, ký hiệu, số hóa đơn); hủy / đảo đơn thì giải phóng số hóa đơn. | P1 |
| BR-PUR-10 | ~~Hàng về trước, hóa đơn về sau~~: **bỏ theo D-54** (hóa đơn ghi cùng đơn mua; có thể ghi sổ đơn không có hóa đơn). | Bỏ |
| BR-PUR-11 | ~~Đối chiếu ba chiều~~: **bỏ theo D-54** (đơn mua, phiếu nhập, hóa đơn cùng một chứng từ). | Bỏ |
| BR-PUR-12 | **Chi phí mua hàng** (vận chuyển, bảo hiểm, bốc xếp, thuế nhập khẩu) là dòng dịch vụ của đơn mua đã ghi sổ; phân bổ vào giá trị hàng hóa của các đơn mua đã ghi sổ theo giá trị hoặc số lượng; phân bổ sau khi hàng đã xuất thì điều chỉnh giá vốn [ĐT]. | P1 |
| BR-PUR-13 | **Nhập khẩu**: ngoại tệ, tỷ giá, tờ khai hải quan, thuế nhập khẩu, GTGT hàng nhập khẩu (D-24). | P2 |
| BR-PUR-14 | **Trả hàng NCC**: tham chiếu đơn mua đã ghi sổ, đúng lô/serial của dòng đơn; giảm tồn và công nợ phải trả. **Giảm giá hàng mua** chỉ giảm công nợ và giá trị nhập. | P1 |
| BR-PUR-15 | **Mua giao thẳng** cho khách qua kho ảo, vẫn giữ lô/serial (H-23, D-17). | P1 |
| BR-PUR-16 | Mua dịch vụ không qua kho; có thể phân bổ vào giá nhập. | P1 |
| BR-PUR-17 | Hàng khuyến mại từ NCC. `[Chưa kiểm chứng]` Cách ghi nhận giá trị và thuế (D-26). | P1 |
| BR-PUR-18 | **Lịch sử giao dịch NCC**: giá mua theo hàng (H-17), đơn mua, nhận hàng, hàng lỗi, trả hàng, hóa đơn, thanh toán; đánh giá NCC (giao đúng hạn, tỷ lệ lỗi). | P1 |
| BR-PUR-19 | Hợp đồng mua / phân phối với NCC: giá, thời hạn, sản lượng cam kết. | P2 |

---

## 9. M04 - Quản lý kho (INV)

### 9.1 Nguyên tắc
- `inventory_transactions` là sổ nhật ký biến động: bất biến, là nguồn kiểm toán. `inventory_balances` là bản đọc nhanh, tái tạo được từ sổ nhật ký.
- Tồn theo dõi ở mức **(công ty, chi nhánh, kho, vị trí, hàng, lô, serial, trạng thái chất lượng)** [ĐT].
- `Tồn cuối kỳ = Tồn đầu kỳ + Σ Nhập − Σ Xuất` ở mọi cấp.
- Tồn chỉ thay đổi qua chứng từ nhóm A POSTED, trong giao dịch có khóa dữ liệu phù hợp, để hai người xuất đồng thời không làm tồn âm [ĐT].

### 9.2 Lô, hạn dùng, serial, chất lượng
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-INV-01 | Hàng quản lý lô bắt buộc số lô; quản lý hạn bắt buộc hạn dùng; lưu ngày sản xuất. Một (mã hàng, số lô) chỉ có một hạn dùng [ĐT]. | P1 |
| BR-INV-02 | Serial duy nhất theo mã hàng; nhập/xuất phải chỉ rõ serial; một serial không tồn ở hai nơi; lưu vòng đời serial [ĐT]. | P1 |
| BR-INV-03 | Trạng thái chất lượng: CHỜ KIỂM TRA / ĐẠT / CÁCH LY / KHÔNG ĐẠT / HẾT HẠN / THU HỒI; đổi trạng thái qua chứng từ có lý do và duyệt. Nhập kho mặc định ĐẠT (D-56, thay D-09). | P1 |
| BR-INV-04 | Lô quá hạn bị chặn bán. Hạn dùng theo tháng quy ước ngày cuối tháng (D-06). | P1 |
| BR-INV-05 | Hồ sơ chất lượng (CO, CQ, phiếu kiểm nghiệm, SDS) đính kèm theo lô hoặc theo hàng. | P1 |
| BR-INV-06 | **Truy xuất lịch sử nhập xuất** của từng lô hoặc serial: NCC, phiếu nhập, hóa đơn mua, phiếu xuất, khách, hợp đồng, bảo hành [ĐT]. | P1 |

### 9.3 Nhập kho
| Loại nhập | Nguồn | GĐ |
|---|---|---|
| Nhập mua | Ghi sổ đơn mua (D-54) | P1 |
| Nhập hàng bán bị trả lại, hàng đổi | Trả hàng bán | P1 |
| Nhập chuyển kho | Chuyển kho | P1 |
| Nhập thu hồi từ khách (máy đặt, mượn, ký gửi, máy sửa xong) | Kho ảo / dịch vụ | P1 |
| Nhập thừa kiểm kê, điều chỉnh tăng | Kiểm kê / điều chỉnh | P1 |
| Nhập tồn đầu kỳ | Chuyển đổi dữ liệu | P1 |

| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-INV-10 | Hàng, kho, vị trí tồn tại và đang hoạt động; người dùng có quyền trên kho; số lượng > 0. | P1 |
| BR-INV-11 | Chặn nhập hàng đã hết hạn; cảnh báo hoặc yêu cầu duyệt khi hạn còn lại dưới ngưỡng (D-07); cảnh báo số lưu hành hết hiệu lực. | P1 |
| BR-INV-12 | Nhập serial hàng loạt (dán danh sách, quét mã vạch). | P1 |

### 9.4 Xuất kho
| Loại xuất | Nguồn | GĐ |
|---|---|---|
| Xuất bán | Đơn bán / hợp đồng | P1 |
| Xuất trả NCC | Trả hàng mua | P1 |
| Xuất chuyển kho | Chuyển kho | P1 |
| Xuất cho mượn / đặt máy / ký gửi (sang kho ảo) | Phiếu mượn / hợp đồng đặt máy | P1 |
| Xuất sử dụng nội bộ, demo | Đề nghị nội bộ có duyệt [ĐT] | P1 |
| Xuất linh kiện bảo hành, sửa chữa | Phiếu dịch vụ | P2 |
| Xuất hủy | Biên bản hủy | P1 |
| Xuất thiếu kiểm kê, điều chỉnh giảm | Kiểm kê / điều chỉnh | P1 |

| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-INV-20 | Kiểm tra tồn khả dụng theo lô/serial **tại thời điểm ghi sổ**, không chỉ lúc lập. Không xuất vượt tồn khả dụng trừ khi có chính sách được duyệt (BR-GEN-31) [ĐT]. | P1 |
| BR-INV-21 | **FEFO** cho hàng có hạn dùng: đề xuất lô hết hạn sớm nhất. **FIFO** cho hàng không có hạn dùng khi cấu hình. Chọn lô khác phải ghi lý do [ĐT]. | P1 |
| BR-INV-22 | Kiểm tra hạn dùng còn lại tối thiểu theo khách. | P1 |
| BR-INV-23 | Chỉ xuất bán lô ĐẠT. Xuất trả NCC, xuất hủy được lấy hàng CÁCH LY, KHÔNG ĐẠT, HẾT HẠN, THU HỒI. | P1 |
| BR-INV-24 | Xuất theo đơn vị khác đơn vị nhập theo quy đổi (BR-MD-20). | P1 |

### 9.5 Chuyển kho, kiểm kê, điều chỉnh, hủy
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-INV-30 | Chuyển kho: nguồn ≠ đích; giữ nguyên lô/serial/trạng thái/giá trị; trừ nguồn và cộng đích trong một giao dịch. Chuyển khác chi nhánh qua trạng thái ĐANG VẬN CHUYỂN. | P1 |
| BR-INV-31 | Kiểm kê: `DRAFT → COUNTING → REVIEW → APPROVED → POSTED`. Snapshot theo lô/serial khi bắt đầu; chặn ghi sổ nhập/xuất trong phạm vi kiểm kê (D-11); giữ số đếm gốc; POSTED sinh chứng từ điều chỉnh chênh lệch [ĐT]. | P1 |
| BR-INV-32 | Điều chỉnh tồn **chỉ theo quyền được cấp**: lý do từ danh mục, hướng tăng/giảm, luôn phải duyệt; vượt ngưỡng giá trị thì Giám đốc duyệt [ĐT]. | P1 |
| BR-INV-33 | Hủy hàng: chỉ lô HẾT HẠN, KHÔNG ĐẠT, THU HỒI hoặc hỏng; biên bản hủy; Giám đốc duyệt. `[Chưa kiểm chứng]` Hóa chất nguy hại có thể phải hủy qua đơn vị được cấp phép. | P1 |

### 9.6 Cảnh báo, cách ly, thu hồi
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-INV-40 | Cảnh báo **tồn dưới định mức, tồn lâu, hàng sắp hết hạn** (30/60/90 ngày, cấu hình) [ĐT]. | P1 |
| BR-INV-41 | Khóa một lô hoặc serial trên toàn bộ kho bằng một thao tác. | P1 |
| BR-INV-42 | Phiếu thu hồi: liệt kê mọi khách đã nhận lô/serial; theo dõi tiến độ thu hồi theo khách. | P1 |

---

## 10. M05 - Bán hàng và phân phối (SAL, EINV)

### 10.1 Luồng và phân biệt chứng từ [ĐT]
```
Khách hàng -> Đơn bán (giá theo bảng giá, duyệt giá / hạn mức, kiểm tra tồn, giữ hàng)
   -> Ghi sổ: phiếu xuất kho (tự sinh, FEFO, liên kết) + doanh thu + công nợ phải thu + bút toán
   -> Số hóa đơn điện tử (MISA) -> Giao hàng (đang giao, khách nhận; biên bản giao nhận) -> Phiếu thu
   -> (khi cần) Trả hàng / giảm giá hàng bán, đổi hàng, hàng lỗi
```
D-54 (2026-10-12): đơn bán là chứng từ ghi sổ duy nhất của nghiệp vụ bán: xuất kho, doanh thu, công nợ, hóa đơn ghi nhận cùng lúc khi ghi sổ đơn. Mỗi lần giao (kèm hóa đơn) là một đơn bán; không theo dõi giao nhiều đợt / một hóa đơn cho nhiều lần giao. Giao thất bại thì đảo đơn.

| Chứng từ / sự kiện | Ý nghĩa | Ảnh hưởng |
|---|---|---|
| Đơn bán (đã duyệt) | Cam kết bán | Giữ chỗ (nếu chọn) |
| Đơn bán (đã ghi sổ) | Hàng rời kho, hóa đơn | Tồn thực tế (phiếu xuất kho liên kết), doanh thu, công nợ phải thu, giá vốn `*` |
| Giao hàng (trên đơn bán) | Đang giao, khách nhận, ký biên bản | Không đổi sổ; mốc lịch thu tiền |
| Trả hàng / giảm giá hàng bán | Khách trả đúng lô / serial, hoặc giảm giá | Tồn (cách ly), doanh thu, công nợ |
| Phiếu thu | Thu tiền | Quỹ / ngân hàng, công nợ |

### 10.2 Bảng giá (báo giá bỏ theo D-54)
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-SAL-01 | ~~Báo giá~~: **bỏ theo D-54**; giá, chiết khấu, điều kiện ghi trực tiếp trên đơn bán. | Bỏ |
| BR-SAL-02 | Lấy giá theo thứ tự: giá hợp đồng/trúng thầu → chính sách giá theo khách → theo nhóm khách/đại lý → giá niêm yết. | P1 |
| BR-SAL-03 | **Phê duyệt giá bán**: giá dưới giá sàn hoặc chiết khấu vượt ngưỡng thì đơn bán phải duyệt trước khi ghi sổ [ĐT]. | P1 |
| BR-SAL-04 | ~~In báo giá~~: **bỏ theo D-54**; in biên bản giao nhận từ đơn bán (BR-SAL-22). | Bỏ |
| BR-SAL-05 | ~~Trạng thái báo giá~~: **bỏ theo D-54**. | Bỏ |

### 10.3 Đơn bán, tồn và giữ hàng
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-SAL-10 | Đơn bán (D-54): khách, hợp đồng/gói thầu (nếu có), kho xuất, ngày hạch toán, địa chỉ giao, người nhận, nhân viên kinh doanh, chi nhánh, hàng, số lượng, đơn vị, giá, chiết khấu, thuế, lô / serial chỉ định (trống: FEFO), ngày giao dự kiến, điều khoản thanh toán. | P1 |
| BR-SAL-11 | Đơn phát sinh từ đấu thầu **bắt buộc liên kết hợp đồng/gói thầu**; giá lấy theo hợp đồng; không vượt số lượng còn được giao (BR-TND-22) [ĐT]. | P1 |
| BR-SAL-12 | **Kiểm tra tồn khả dụng** khi lập và khi duyệt đơn (BR-GEN-30) [ĐT]. | P1 |
| BR-SAL-13 | **Giữ hàng theo đơn** nếu cấu hình (theo loại khách, hợp đồng): theo hàng hoặc chỉ định lô/serial; hết hạn giữ hoặc hủy đơn thì nhả [ĐT]. | P1 |
| BR-SAL-14 | Kiểm tra hạn mức tín dụng: công nợ hiện tại + giá trị đơn > hạn mức, hoặc có nợ quá hạn, thì phải duyệt (D-22). | P1 |
| BR-SAL-15 | ~~Đề nghị mua từ đơn bán thiếu hàng~~: **bỏ theo D-54** (đề nghị mua bỏ); cảnh báo thiếu tồn vẫn hiện trên đơn. | Bỏ |

### 10.4 Xuất kho và giao hàng
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-SAL-20 | Mỗi đơn bán ghi sổ là một lần xuất / giao / hóa đơn (D-54); giao nhiều đợt thì lập nhiều đơn. Theo dõi theo dòng: đã trả lại, giá trị đã giảm, còn trả được; theo đơn: đã thu theo lịch thu tiền. | P1 |
| BR-SAL-21 | Phiếu xuất kho sinh khi ghi sổ đơn bán; lô theo FEFO hoặc lô chỉ định (khác FEFO phải có lý do), serial chỉ định hoặc kho chọn; dùng phần hàng đã giữ cho đơn. | P1 |
| BR-SAL-22 | **Chứng từ giao nhận** (biên bản giao nhận) có lô, hạn dùng, serial, số lưu hành, danh mục CO/CQ; ghi nhận ngày khách nhận, người nhận, file biên bản ký [ĐT]. | P1 |
| BR-SAL-23 | Trạng thái giao của đơn đã ghi sổ: Chờ giao / Đang giao / Đã giao. Giao thất bại: đảo đơn bán (hàng về kho bằng chứng từ đảo phiếu xuất, đảo doanh thu và công nợ). | P1 |
| BR-SAL-24 | Đơn có yêu cầu lắp đặt sinh phiếu bàn giao/lắp đặt cho kỹ thuật (BR-SVC-01). | P1 |

### 10.5 Hóa đơn, trả hàng, đổi hàng, chiết khấu
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-SAL-30 | **Hóa đơn bán** là đơn bán đã ghi sổ (D-54): công nợ phải thu, doanh thu, thuế GTGT đầu ra ghi nhận khi ghi sổ đơn. Không còn một hóa đơn cho nhiều lần giao / hóa đơn trước khi giao. | P1 |
| BR-SAL-31 | P1: hóa đơn điện tử phát hành trên MISA, ERP lưu số, ký hiệu, ngày, mã cơ quan thuế trên đơn bán, trước hoặc sau khi ghi sổ; chống trùng số (H-32, D-54). | P1 |
| BR-EINV-01 | P2: tích hợp nhà cung cấp hóa đơn điện tử: phát hành, ký, lấy mã, gửi khách (D-12). Hóa đơn đã phát hành không sửa; điều chỉnh/thay thế theo quy định. `[Chưa kiểm chứng]` | P2 |
| BR-SAL-32 | **Trả hàng**: tham chiếu đơn bán đã ghi sổ, đúng lô/serial đã xuất, không vượt số đã bán trừ số đã trả; hàng về trạng thái CÁCH LY; giảm doanh thu, giá vốn, công nợ; cập nhật số đã trả của đơn và số đã giao của hợp đồng [ĐT]. | P1 |
| BR-SAL-33 | **Đổi hàng**: phiếu trả hàng cũ và đơn bán mới (lập từ phiếu trả, cùng giá) liên kết; xử lý chênh lệch giá trên đơn mới [ĐT]. | P1 |
| BR-SAL-34 | **Hàng lỗi** khách báo: tiếp nhận, kiểm tra, quyết định đổi / sửa / trả / bảo hành (liên kết M09) [ĐT]. | P1 |
| BR-SAL-35 | **Chiết khấu và khuyến mại theo chính sách**: chiết khấu dòng, chiết khấu đơn, chiết khấu đại lý, hàng tặng kèm, chiết khấu thương mại theo doanh số kỳ, chiết khấu thanh toán [ĐT]. `[Chưa kiểm chứng]` Hóa đơn và thuế cho hàng khuyến mại (D-26). | P1 (P2: theo doanh số kỳ) |
| BR-SAL-36 | Giảm giá hàng bán sau bán: giảm doanh thu, công nợ, không đổi tồn. | P1 |
| BR-SAL-37 | Doanh thu nhận trước cho hợp đồng dịch vụ nhiều kỳ (H-30). | P2 |
| BR-SAL-42 | **Thao tác nhanh**: ghi sổ đơn bán là xuất kho + doanh thu + công nợ + hóa đơn trong một lần (D-54), giữ cách làm quen thuộc (H-31); phiếu xuất kho vẫn sinh riêng và liên kết. Phiếu thu lập ở Thu chi. | P1 |

### 10.6 Công nợ và doanh số
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-SAL-50 | **Lịch thu tiền** theo đơn/hợp đồng: các đợt, tỷ lệ, hạn thanh toán, điều kiện (sau giao, sau nghiệm thu) [ĐT]. | P1 |
| BR-SAL-51 | Doanh số, lợi nhuận gộp, công nợ theo **nhân viên, khách hàng, sản phẩm, chi nhánh** [ĐT]. | P1 |
| BR-SAL-52 | Chỉ tiêu doanh số, hoa hồng. | P3 |

---

## 11. M06 - Quản lý đấu thầu (TND)

`[Suy luận]` Khách hàng chủ yếu là cơ sở y tế công (H-10), thường mua qua đấu thầu. Đặc tả dự án xác nhận đây là một chuỗi nghiệp vụ chính [ĐT].

### 11.1 Luồng
```
Gói thầu -> Hồ sơ dự thầu -> Kết quả (Trúng / Trượt) -> Hợp đồng (+ phụ lục)
   -> Phân bổ hàng hóa (kho, lô, đơn mua) -> Giao hàng theo tiến độ -> Nghiệm thu -> Hóa đơn -> Đối soát và thu tiền
```

### 11.2 Gói thầu và hồ sơ dự thầu
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-TND-01 | **Gói thầu**: mã, tên, bên mời thầu/chủ đầu tư, hình thức, nguồn vốn, giá gói thầu, thời điểm phát hành, hạn nộp, hạn hiệu lực hồ sơ, nhân viên phụ trách [ĐT]. | P1 |
| BR-TND-02 | Trạng thái gói thầu: Đang theo dõi / Đang chuẩn bị / Đã nộp / Trúng / Trượt / Hủy; lý do trượt [ĐT]. | P1 |
| BR-TND-03 | **Hồ sơ dự thầu**: danh sách tài liệu, người phụ trách, hạn từng phần, trạng thái; file đính kèm [ĐT]. | P1 |
| BR-TND-04 | **Danh mục hàng dự thầu**: mặt hàng, nhà sản xuất, model, xuất xứ, quy cách, số lượng, **giá dự thầu**, hồ sơ kỹ thuật [ĐT]. | P1 |
| BR-TND-05 | **Cấu hình giá dự thầu**: tham chiếu giá vốn dự kiến, giá mua gần nhất, biên lợi nhuận mục tiêu; giá dự thầu dưới ngưỡng biên lợi nhuận phải duyệt. | P1 |
| BR-TND-06 | Tự gom hồ sơ kỹ thuật theo mặt hàng (số lưu hành, CO/CQ mẫu, catalogue, ủy quyền hãng) và cảnh báo hồ sơ thiếu hoặc hết hạn trước hạn nộp. | P1 |
| BR-TND-07 | Bảo lãnh dự thầu và bảo lãnh thực hiện hợp đồng: số tiền, ngân hàng, hạn. | P2 |

### 11.3 Kết quả trúng thầu và hợp đồng
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-TND-10 | **Kết quả trúng thầu**: số, ngày quyết định, danh mục trúng với **số lượng trúng, giá trúng, thời hạn hiệu lực** theo sản phẩm [ĐT]. | P1 |
| BR-TND-11 | **Hợp đồng**: số, loại, khách, gói thầu (nếu có), thời hạn, giá trị, danh mục hàng với giá cố định và số lượng tối đa, điều khoản giao, thanh toán, bảo hành [ĐT]. Hợp đồng không qua thầu dùng cùng chức năng. | P1 |
| BR-TND-12 | **Phụ lục hợp đồng** điều chỉnh giá, số lượng, thời hạn; lưu phiên bản; có hiệu lực từ ngày ký [ĐT]. | P1 |
| BR-TND-13 | Một kết quả trúng thầu có thể sinh nhiều hợp đồng (nhiều đơn vị sử dụng); tổng số lượng hợp đồng không vượt số lượng trúng. | P1 |

### 11.4 Phân bổ, tiến độ giao, nghiệm thu, đối soát
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-TND-20 | **Phân bổ hàng hóa** cho hợp đồng: giữ chỗ tồn theo lô, hoặc sinh đơn mua nháp cho phần thiếu (D-54) [ĐT]. | P1 |
| BR-TND-21 | **Kế hoạch giao hàng** theo đợt: ngày, địa điểm, số lượng từng mặt hàng. | P1 |
| BR-TND-22 | Theo dõi theo từng dòng hợp đồng: **trúng / đã đặt / đã giao / còn lại / đã nghiệm thu / đã xuất hóa đơn / đã thu**. **Chặn giao vượt phần còn lại**; kiểm soát cả khi nhiều người giao đồng thời [ĐT]. | P1 |
| BR-TND-23 | **Nghiệm thu**: biên bản theo đợt, số lượng nghiệm thu, ngày, người ký, file; nghiệm thu là điều kiện lập hóa đơn hoặc thu tiền khi hợp đồng quy định [ĐT]. | P1 |
| BR-TND-24 | **Hồ sơ giao hàng, nghiệm thu, thanh toán** theo đợt: danh mục giấy tờ bắt buộc, trạng thái đủ/thiếu [ĐT]. | P1 |
| BR-TND-25 | **Đối soát** với khách theo hợp đồng: đã giao, đã nghiệm thu, đã xuất hóa đơn, đã thu, còn phải thu [ĐT]. | P1 |
| BR-TND-26 | Cảnh báo **hợp đồng sắp hết hạn**, sắp hết số lượng, **giấy tờ sắp hết hạn**, đợt giao trễ tiến độ [ĐT]. | P1 |
| BR-TND-27 | Báo cáo **doanh thu, lợi nhuận, tiến độ theo gói thầu và hợp đồng**; tỷ lệ trúng thầu [ĐT]. | P1 |

---

## 12. M07 - Công nợ, thu chi và ngân hàng (AR, AP, CSH)

### 12.1 Nguyên tắc
- Sổ chi tiết công nợ là sổ phụ bất biến; số dư tính từ sổ chi tiết; không sửa số dư trực tiếp (trừ số dư đầu kỳ khi chuyển đổi).
- Công nợ theo dõi **theo từng hóa đơn** để tính hạn và tuổi nợ.

| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-AR-01 | Phải thu tăng khi ghi sổ đơn bán (hóa đơn bán, D-54); giảm bởi phiếu thu, trả hàng, giảm giá, đối trừ. | P1 |
| BR-AR-02 | Hạn thanh toán = ngày hóa đơn + số ngày nợ (từ lịch thu tiền, hợp đồng, rồi khách hàng) [ĐT]. | P1 |
| BR-AR-03 | **Phân bổ thanh toán** cho từng hóa đơn; **thu một phần** hoặc **một lần cho nhiều hóa đơn**; mặc định hóa đơn cũ nhất trước; phần chưa phân bổ là trả trước [ĐT]. | P1 |
| BR-AR-04 | Đối trừ trả trước với hóa đơn; phải thu với phải trả cùng đối tượng; bỏ đối trừ bằng bút toán đảo (H-06, H-07). | P1 |
| BR-AR-05 | **Tuổi nợ** theo mốc cấu hình; nợ quá hạn theo khách, nhân viên, chi nhánh, hợp đồng [ĐT]. | P1 |
| BR-AR-06 | **Đối chiếu công nợ**: biên bản đối chiếu xác nhận, thông báo công nợ, giấy đề nghị thanh toán; in và gửi email [ĐT]. | P1 |
| BR-AR-07 | **Xử lý chênh lệch đối chiếu** bằng chứng từ điều chỉnh công nợ có lý do, **theo quyền hạn** và phê duyệt [ĐT]. | P1 |
| BR-AR-08 | Thu tiền hàng loạt nhiều khách, nhiều hóa đơn (H-06). | P1 |
| BR-AP-01 | Phải trả tăng từ hóa đơn mua; giảm bởi phiếu chi/UNC, trả hàng, giảm giá, đối trừ. | P1 |
| BR-AP-02 | Theo dõi phải trả theo hóa đơn, hạn, tuổi nợ; danh sách đến hạn trả; trả một phần hoặc nhiều hóa đơn [ĐT]. | P1 |
| BR-AP-03 | Công nợ ngoại tệ theo nguyên tệ và VND. `[Chưa kiểm chứng]` Đánh giá lại tỷ giá cuối kỳ. | P2 |
| BR-CSH-01 | Quỹ tiền mặt và nhiều tài khoản ngân hàng; số dư tính từ sổ quỹ và sổ tiền gửi. | P1 |
| BR-CSH-02 | **Phiếu thu, phiếu chi, giao dịch ngân hàng** (giấy báo có, UNC, chuyển nội bộ) [ĐT]. | P1 |
| BR-CSH-03 | Chi phải duyệt theo ngưỡng; quỹ tiền mặt không âm. | P1 |
| BR-CSH-04 | Tạm ứng và hoàn ứng nhân viên; giấy đề nghị thanh toán nội bộ. | P1 |
| BR-CSH-05 | Kiểm kê quỹ; chênh lệch xử lý bằng chứng từ có duyệt. | P1 |
| BR-CSH-06 | **Báo cáo dòng tiền** thực tế và **dự kiến dòng tiền**: dự thu theo hạn hóa đơn bán và lịch thu hợp đồng, dự chi theo hạn hóa đơn mua và đơn mua đã duyệt [ĐT]. | P1 |
| BR-CSH-07 | Nhập sao kê ngân hàng từ file và khớp với chứng từ. | P2 |
| BR-CSH-08 | Ngân hàng điện tử (D-27); khế ước vay (nếu D-01 thay MISA hoàn toàn). | P3 |

---

## 13. M08 - Kế toán và giá vốn (CST, ACC, TAX)

> **Giới hạn trách nhiệm** [ĐT]: Hệ thống **không tự khẳng định** đáp ứng đầy đủ quy định pháp luật về kế toán, thuế hoặc thay thế phần mềm kế toán đã được kiểm tra/chứng nhận. Mọi quy tắc kế toán và thuế trong mục này phải được kế toán trưởng hoặc chuyên gia nghiệp vụ xác nhận trước khi triển khai. Chế độ kế toán áp dụng là chế độ doanh nghiệp Việt Nam đang hiệu lực tại thời điểm triển khai; hiện trạng dùng TT133/2016/TT-BTC (H-04) `[Chưa kiểm chứng]` cần kiểm tra văn bản thay thế.

| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-ACC-01 | **Hệ thống tài khoản** cấu hình được theo chế độ kế toán áp dụng; tài khoản chi tiết [ĐT]. | P1 |
| BR-ACC-02 | **Quy tắc hạch toán** cấu hình theo loại chứng từ, loại hàng, loại đối tượng; chứng từ nhóm A khi POSTED **tự sinh bút toán** (ví dụ hiện trạng: mua Nợ 156/Có 331; bán Nợ 131/Có 5111 - H-21, H-31) [ĐT]. | P1 |
| BR-ACC-03 | Bút toán cân Nợ = Có; bút toán tự sinh không sửa tay, chỉ đảo theo chứng từ gốc. | P1 |
| BR-CST-01 | **Tính giá xuất kho** theo phương pháp doanh nghiệp chọn (D-02): bình quân gia quyền (tức thời / cuối kỳ), FIFO, đích danh theo serial. Không đổi phương pháp giữa kỳ [ĐT]. | P1 |
| BR-CST-02 | Giá nhập gồm chi phí mua phân bổ (BR-PUR-12); điều chỉnh giá khi hóa đơn về sau (BR-PUR-10). | P1 |
| BR-CST-03 | Giá vốn ghi nhận đúng kỳ với doanh thu (D-35); tính lại khi có chứng từ hồi tố trong **kỳ chưa khóa**. **Không thay đổi giá vốn của kỳ đã khóa**; chênh lệch phát sinh sau khóa ghi vào kỳ hiện hành [ĐT]. | P1 |
| BR-CST-04 | Lợi nhuận gộp theo hóa đơn, đơn bán, hợp đồng, gói thầu, khách, hàng, nhân viên, chi nhánh. | P1 |
| BR-ACC-04 | **Đối chiếu số liệu kho với kế toán**: sổ phụ kho với tài khoản hàng tồn kho, công nợ chi tiết với tài khoản công nợ, sổ quỹ với tài khoản tiền; báo chênh lệch [ĐT]. | P1 |
| BR-ACC-05 | **Đóng/mở kỳ** (BR-SYS-30..32) và chặn chỉnh sửa dữ liệu kỳ đã khóa [ĐT]. | P1 |
| BR-ACC-06 | Xuất dữ liệu chứng từ đã ghi sổ sang file nhập được vào MISA, có ánh xạ mã (trong thời gian chạy song song). | P1 |
| BR-ACC-07 | **Báo cáo doanh thu, chi phí, lợi nhuận, kết quả kinh doanh** quản trị [ĐT]. | P1 |
| BR-TAX-01 | **Theo dõi thuế**: thuế GTGT đầu vào, đầu ra theo hóa đơn và thuế suất; bảng kê hóa đơn mua vào, bán ra [ĐT]. `[Chưa kiểm chứng]` Mẫu biểu hiện hành. | P2 |
| BR-TAX-02 | Hóa đơn đầu vào không đủ điều kiện khấu trừ: đánh dấu, có lý do. | P2 |
| BR-TAX-03 | Tải và đối chiếu hóa đơn đầu vào với dữ liệu cơ quan thuế (D-12). `[Chưa kiểm chứng]` | P2 |
| BR-ACC-08 | **Sổ chi tiết, sổ tổng hợp** (nhật ký chung, sổ cái, cân đối phát sinh) theo phạm vi được phê duyệt [ĐT]. | P2 |
| BR-ACC-09 | Nghiệp vụ cuối kỳ: phân bổ chi phí trả trước, đánh giá lại ngoại tệ, kết chuyển lãi lỗ; bút toán tổng hợp có duyệt. | P3 |
| BR-ACC-10 | **Báo cáo tài chính**, tờ khai thuế, số liệu quyết toán theo phạm vi được phê duyệt (D-29) [ĐT]. | P3 |
| BR-ACC-11 | TSCĐ, CCDC, ngân sách: chỉ khi D-01 chọn thay MISA hoàn toàn. | P3 |

---

## 14. M09 - Thiết bị, bảo hành và hồ sơ sản phẩm (EQP, SVC, MED)

| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-EQP-01 | **Hồ sơ thiết bị** theo serial: model, nhà sản xuất, xuất xứ, thông tin nhận diện (serial, mã UDI nếu có), lô nhập, NCC, ngày nhập [ĐT]. | P1 |
| BR-EQP-02 | **Bảo hành**: thời hạn và điều kiện bảo hành theo hàng/hợp đồng; ngày bắt đầu bảo hành mặc định là ngày bàn giao/nghiệm thu (cấu hình); tra cứu theo serial còn/hết bảo hành [ĐT]. | P1 |
| BR-EQP-03 | **Lịch sử bàn giao**: khách, khoa phòng, địa điểm, ngày, biên bản; hình thức sở hữu (khách mua / đặt máy / cho mượn) [ĐT]. | P1 |
| BR-EQP-04 | **Truy xuất thiết bị** theo khách hàng, đơn hàng, hợp đồng, gói thầu và ngược lại [ĐT]. | P1 |
| BR-EQP-05 | **Thiết bị đổi trả**: liên kết serial cũ và serial mới, lý do, chứng từ trả/đổi [ĐT]. | P1 |
| BR-EQP-06 | **Hồ sơ kỹ thuật, chứng từ nguồn gốc** (CO, CQ, tờ khai, catalogue, hướng dẫn sử dụng) theo hàng, lô, serial [ĐT]. | P1 |
| BR-EQP-07 | **Cảnh báo giấy tờ hết hạn**: số lưu hành, ủy quyền hãng, hồ sơ pháp lý doanh nghiệp, giấy tờ hợp đồng [ĐT]. | P1 |
| BR-SVC-01 | Phiếu bàn giao / lắp đặt sinh từ đơn bán hoặc hợp đồng; kỹ thuật viên, lịch hẹn, kết quả. | P1 |
| BR-SVC-02 | **Lịch sử sửa chữa**: tiếp nhận yêu cầu, chẩn đoán, linh kiện thay (xuất kho), chi phí, kết quả; bảo hành không tính tiền, ngoài bảo hành có báo giá và hóa đơn dịch vụ [ĐT]. | P2 |
| BR-SVC-03 | Bảo trì định kỳ theo chu kỳ; nhắc trước N ngày. | P2 |
| BR-SVC-04 | Gửi máy về hãng bảo hành: theo dõi máy ở hãng. | P2 |
| BR-SVC-05 | Máy đặt / cho mượn: theo dõi ở kho ảo; sản lượng thuốc thử khách mua so với cam kết (D-19). | P2 |
| BR-SVC-06 | Hiệu chuẩn, kiểm định. `[Chưa kiểm chứng]` | P3 |
| BR-MED-01 | **Hồ sơ pháp lý doanh nghiệp**: đăng ký kinh doanh, công bố đủ điều kiện mua bán TTBYT, giấy phép hóa chất; hạn và cảnh báo. `[Chưa kiểm chứng]` (D-28) | P1 |
| BR-MED-02 | Kê khai / công khai giá TTBYT: lịch sử giá đã kê khai; cảnh báo giá bán cao hơn. `[Chưa kiểm chứng]` | P2 |
| BR-MED-03 | Hàng lạnh: ghi nhiệt độ khi nhận và giao; cảnh báo ngoài khoảng bảo quản. | P2 |
| BR-MED-04 | Phản hồi chất lượng, sự cố gắn lô/serial; liên kết thu hồi. | P2 |

---

## 15. M10 - Báo cáo và dashboard (RPT)

### 15.1 Dashboard điều hành [ĐT]
| Mã | Chỉ tiêu | GĐ |
|---|---|---|
| BR-RPT-01 | Tổng doanh thu, giá vốn, lợi nhuận theo kỳ | P1 |
| BR-RPT-02 | Công nợ phải thu, phải trả; nợ quá hạn | P1 |
| BR-RPT-03 | Giá trị tồn kho và hàng tồn lâu | P1 |
| BR-RPT-04 | Hàng sắp hết hạn | P1 |
| BR-RPT-05 | Đơn mua chưa giao đủ | P1 |
| BR-RPT-06 | Đơn bán chưa giao đủ | P1 |
| BR-RPT-07 | Gói thầu và hợp đồng sắp hết hạn | P1 |
| BR-RPT-08 | Tiến độ giao hàng trúng thầu | P1 |
| BR-RPT-09 | Dòng tiền dự kiến | P1 |
| BR-RPT-10 | Top sản phẩm, khách hàng, nhà cung cấp | P1 |

| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-RPT-11 | Mọi chỉ tiêu dashboard **bấm được để xem danh sách chi tiết và chứng từ nguồn**; tổng chi tiết bằng số trên dashboard [ĐT]. | P1 |
| BR-RPT-12 | Dashboard và báo cáo tôn trọng phạm vi dữ liệu của người xem (BR-SYS-04). | P1 |
| BR-RPT-13 | Ghi rõ thời điểm số liệu (thời gian thực hay dữ liệu tổng hợp đến thời điểm nào). | P1 |

### 15.2 Báo cáo
Mọi báo cáo lọc theo **thời gian, công ty, chi nhánh, kho, khách hàng, nhà cung cấp, sản phẩm** tùy loại; xuất Excel/PDF [ĐT].

| Mã | Báo cáo | GĐ |
|---|---|---|
| BR-RPT-20 | Tồn kho tức thời (bốn loại tồn) theo hàng / kho / vị trí / lô / hạn / trạng thái [ĐT] | P1 |
| BR-RPT-21 | **Thẻ kho** theo hàng, theo lô; lịch sử serial [ĐT] | P1 |
| BR-RPT-22 | **Nhập - xuất - tồn** theo kỳ (số lượng, giá trị) [ĐT] | P1 |
| BR-RPT-23 | Hàng sắp hết hạn, hết hạn, cách ly, thu hồi, thiếu hồ sơ; tồn dưới định mức, tồn lâu | P1 |
| BR-RPT-24 | Truy xuất lô/serial: lô → khách; khách → lô, serial | P1 |
| BR-RPT-25 | Tình hình thực hiện đơn mua; sổ chi tiết mua hàng; lịch sử giá mua; hàng lỗi theo NCC | P1 |
| BR-RPT-26 | Tổng hợp, chi tiết bán hàng; sổ nhật ký bán hàng; tình hình thực hiện đơn bán; tồn so với đặt hàng chưa giao (H-06) | P1 |
| BR-RPT-27 | Lợi nhuận gộp theo hóa đơn, đơn hàng, khách, hàng, nhân viên, chi nhánh, hợp đồng, gói thầu | P1 |
| BR-RPT-28 | Đấu thầu: danh sách gói, tỷ lệ trúng, tiến độ giao và nghiệm thu, doanh thu - lợi nhuận theo gói thầu | P1 |
| BR-RPT-29 | Công nợ: tổng hợp, chi tiết theo hóa đơn, tuổi nợ, quá hạn, biên bản đối chiếu (H-40) | P1 |
| BR-RPT-30 | Sổ quỹ, sổ tiền gửi, dòng tiền thực tế và dự kiến | P1 |
| BR-RPT-31 | Doanh thu, chi phí, lợi nhuận, kết quả kinh doanh quản trị | P1 |
| BR-RPT-32 | Đối chiếu kho - kế toán, công nợ - kế toán, quỹ - kế toán; đối chiếu ERP - MISA khi chạy song song | P1 |
| BR-RPT-33 | Thiết bị tại khách, bảo hành sắp hết, lịch sử sửa chữa | P1 (P2: bảo trì) |
| BR-RPT-34 | Báo cáo yêu thích, tùy chỉnh báo cáo; gửi báo cáo định kỳ qua email (H-05) | P2 |
| BR-RPT-35 | Sổ sách kế toán, báo cáo thuế, báo cáo tài chính | P2–P3 |

---

## 16. Chuyển đổi dữ liệu từ MISA
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-MIG-01 | Nhập danh mục hàng, đối tượng, kho từ Excel MISA; tách quy cách / hãng / xuất xứ khỏi tên hàng; phân loại lại vai trò đối tượng (P-03, P-07). | P1 |
| BR-MIG-02 | Tồn đầu kỳ **theo lô, hạn dùng, serial thực tế** qua kiểm kê trước ngày vận hành; hàng không xác định lô dùng lô tạm CÁCH LY (D-13). | P1 |
| BR-MIG-03 | Công nợ đầu kỳ **theo từng hóa đơn còn nợ**. | P1 |
| BR-MIG-04 | Số dư quỹ, ngân hàng đầu kỳ; số dư tài khoản kế toán đầu kỳ. | P1 |
| BR-MIG-05 | Hợp đồng, kết quả trúng thầu đang hiệu lực kèm số lượng đã giao; thiết bị đã bán còn bảo hành. | P1 |
| BR-MIG-06 | Biên bản đối chiếu số dư đầu kỳ được Kế toán trưởng và Giám đốc ký. | P1 |

---

## 17. Yêu cầu phi chức năng

### 17.1 Kỹ thuật (tóm tắt; chi tiết ở `CLAUDE.md`)
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-NF-01 | Backend Java 17, Spring Boot 3.x; frontend ReactJS + TypeScript + Ant Design; MySQL 8.x; Flyway; REST JSON có OpenAPI. Không nâng lên Java 21 hoặc Spring Boot 4 [ĐT]. | P1 |
| BR-NF-02 | Triển khai được trên **Tomcat 10.1** (D-39). Môi trường phát triển và kiểm thử dùng MySQL local, không dùng Docker (D-44). | P1 |
| BR-NF-03 | Kiến trúc **Modular Monolith** chia theo 10 phân hệ [ĐT]. | P1 |
| BR-NF-04 | Có API REST `/api/v1` cho tích hợp hóa đơn điện tử, ngân hàng, hệ thống khác. | P1 |

### 17.2 Hiệu năng, sẵn sàng, dữ liệu
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-NF-10 | `[Suy luận]` Quy mô ban đầu: 1–3 kho, vài nghìn mã hàng, dưới 50 người dùng, 2.000–5.000 chứng từ/năm (ước từ H-24, H-34). Biên thiết kế theo D-16. | P1 |
| BR-NF-11 | Mục tiêu: mở danh sách, lưu chứng từ dưới 2 giây; báo cáo một năm dưới 10 giây với quy mô trên. | P1 |
| BR-NF-12 | Sẵn sàng 99% trong giờ làm việc; RPO ≤ 24 giờ, RTO ≤ 4 giờ (D-30). | P1 |
| BR-NF-13 | **Sao lưu hằng ngày**, **quy trình khôi phục đã kiểm thử** và có tài liệu [ĐT]. | P1 |
| BR-NF-14 | Dữ liệu mẫu chỉ dùng cho môi trường phát triển/kiểm thử; **không bao giờ nạp vào production** [ĐT]. | P1 |

### 17.3 Giao diện [ĐT]
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-UI-01 | Giao diện tiếng Việt, nhất quán, phong cách ERP doanh nghiệp. Ưu tiên desktop (từ 1366×768); dùng được trên tablet và mobile cho màn hình phù hợp (tra cứu tồn, duyệt chứng từ, giao hàng, bảo hành). | P1 |
| BR-UI-02 | Bố cục: sidebar phân hệ; thanh tiêu đề có thông tin người dùng, chi nhánh đang làm việc, thông báo; dashboard tổng quan. | P1 |
| BR-UI-03 | Trang danh sách: tìm kiếm, bộ lọc, phân trang (server-side). | P1 |
| BR-UI-04 | Form nhập liệu có validation rõ ràng tại trường, thông báo lỗi tiếng Việt dễ hiểu. | P1 |
| BR-UI-05 | Trang chi tiết chứng từ gồm: **thông tin chung, danh sách hàng hóa, chứng từ liên quan, lịch sử xử lý** (duyệt, ghi sổ, đảo, người, thời điểm, lý do). | P1 |
| BR-UI-06 | Nút thao tác hiển thị **theo trạng thái chứng từ và quyền** người dùng (giao diện chỉ hỗ trợ; server vẫn kiểm tra). | P1 |
| BR-UI-07 | Hộp thoại xác nhận cho thao tác nhạy cảm (duyệt, ghi sổ, hủy, đảo, xóa, khóa kỳ); thao tác đảo/hủy bắt buộc nhập lý do. | P1 |
| BR-UI-08 | Thông báo thành công/thất bại dễ hiểu, không hiển thị lỗi kỹ thuật. | P1 |
| BR-UI-09 | **Không có nút hoặc màn hình giả**: chức năng đã công bố phải kết nối API và database thật; chức năng chưa làm thì ẩn [ĐT]. | P1 |

### 17.4 Bảo mật [ĐT]
| Mã | Yêu cầu | GĐ |
|---|---|---|
| BR-SEC-01 | Xác thực an toàn (D-38); mật khẩu băm một chiều. | P1 |
| BR-SEC-02 | Phân quyền server-side cho **từng API**; không tin dữ liệu hoặc quyền do frontend gửi lên. | P1 |
| BR-SEC-03 | Chặn truy cập chéo chi nhánh/kho (BR-SYS-06). | P1 |
| BR-SEC-04 | Validate mọi dữ liệu đầu vào; giới hạn file tải lên (BR-SYS-22). | P1 |
| BR-SEC-05 | Không ghi token, mật khẩu, dữ liệu nhạy cảm vào log; secret quản lý theo môi trường, không đưa vào mã nguồn. | P1 |
| BR-SEC-06 | Có kiểm thử truy cập trái quyền và các tình huống bảo mật cơ bản (AT-10). | P1 |

---

## 18. Kịch bản nghiệm thu tối thiểu [ĐT]
Mỗi kịch bản phải có test tự động (unit và/hoặc integration) và được chạy trên dữ liệu mẫu.

| Mã | Kịch bản | Yêu cầu liên quan |
|---|---|---|
| AT-01 | Tạo đơn mua, duyệt (hàng đang về tăng), ghi sổ (nhập kho, hàng đang về giảm, công nợ, bút toán); mỗi lần nhận là một đơn mua (D-54) | BR-PUR-04, 08, 09, BR-GEN-30 |
| AT-02 | Nhận hàng theo lô, hạn dùng và serial; chặn lô hết hạn, serial trùng | BR-INV-01, 02, 11 |
| AT-03 | Tạo đơn bán và xuất kho theo lô FEFO; chọn lô khác phải có lý do | BR-SAL-21, BR-INV-21 |
| AT-04 | Hai người đồng thời xuất cùng lô vượt tồn: một thành công, một bị từ chối; tồn không âm | BR-GEN-52, BR-INV-20 |
| AT-05 | Ghi sổ đơn bán, theo dõi giao hàng (đang giao, khách nhận), giao thất bại đảo đơn trả hàng về kho; giao nhiều đợt là nhiều đơn (D-54) | BR-SAL-20..23 |
| AT-06 | Trả hàng và điều chỉnh tồn có truy vết về chứng từ gốc; doanh thu, giá vốn, công nợ giảm tương ứng | BR-SAL-32, BR-INV-32 |
| AT-07 | Tạo hợp đồng trúng thầu; giao vượt số còn lại bị chặn (kể cả khi đồng thời) | BR-TND-10..22 |
| AT-08 | Thu tiền một phần, phân bổ nhiều hóa đơn; biên bản đối chiếu công nợ khớp | BR-AR-03, 06 |
| AT-09 | Hủy (đảo) chứng từ đã ghi sổ: tồn, công nợ, giá vốn, bút toán, chỉ tiêu nguồn được đảo đúng; chặn đảo khi có chứng từ phụ thuộc | BR-GEN-03, 04, 21, 22 |
| AT-10 | Người không có quyền không duyệt được; không đọc được dữ liệu chi nhánh/kho khác qua API | BR-SYS-03..06, BR-SEC-02 |
| AT-11 | Khóa kỳ; ghi sổ, đảo, sửa chứng từ thuộc kỳ khóa bị chặn; giá vốn kỳ khóa không đổi | BR-SYS-30..32, BR-CST-03 |
| AT-12 | Số liệu dashboard và báo cáo bằng tổng chứng từ nguồn | BR-RPT-11, BR-GEN-P3 |
| AT-13 | Gửi lại cùng một yêu cầu tạo/ghi sổ không sinh chứng từ hoặc bút toán trùng | BR-GEN-50, 51 |

Không tuyên bố hoàn thành khi chỉ biên dịch thành công. Báo cáo nghiệm thu phải nêu **số lượng test, kết quả, lỗi còn tồn tại, chức năng chưa triển khai** [ĐT].

---

## 19. Tài liệu bàn giao bắt buộc [ĐT]
| # | Tài liệu | Vị trí đề xuất |
|---|---|---|
| 1 | README hướng dẫn cài đặt và chạy dự án | `README.md` |
| 2 | Kiến trúc tổng thể | `docs/02-system-architecture.md` |
| 3 | Sơ đồ module và luồng nghiệp vụ | `docs/05-workflow.md` |
| 4 | ERD và data dictionary | `docs/03-database-design.md` |
| 5 | API specification (kèm OpenAPI sinh tự động) | `docs/04-api-specification.md` |
| 6 | Tài liệu phân quyền (ma trận vai trò × quyền) | `docs/07-authorization.md` |
| 7 | Cấu hình môi trường | `docs/08-environment.md` |
| 8 | Hướng dẫn backup/restore | `docs/09-backup-restore.md` |
| 9 | Hướng dẫn triển khai (Tomcat) | `docs/10-deployment.md` |
| 10 | Kế hoạch kiểm thử và báo cáo nghiệm thu | `docs/11-test-plan.md` |
| 11 | Danh sách tính năng hoàn thành / chưa hoàn thành / giả định nghiệp vụ | `docs/12-feature-status.md` |

---

## 20. Quyết định cần doanh nghiệp phê duyệt
Ngày 2026-10-09 chủ dự án chốt các quyết định theo đề xuất của người phân tích.

Trạng thái:
- **Đã chốt**: dùng làm căn cứ thiết kế và lập trình.
- **Đã chốt (KTT / pháp chế rà soát trước UAT)**: quy tắc kế toán, thuế, pháp lý. Lập trình theo kết quả đã chốt dưới dạng **tham số / quy tắc cấu hình** (không hard-code) để đổi được mà không sửa mã. Kế toán trưởng / pháp chế ký xác nhận trước UAT (GĐ 11); hệ thống không tự khẳng định tuân thủ pháp luật (mục 13).
- **Đã chốt (giả định thiết kế)**: số liệu ước lượng, cập nhật khi doanh nghiệp cung cấp số thực.

Ngày 2026-10-09 (lần 2) chủ dự án chốt toàn bộ quyết định còn mở theo khuyến nghị của người phân tích.

| Mã | Câu hỏi | Kết quả | Trạng thái |
|---|---|---|---|
| D-01 | Thay MISA hoàn toàn hay giữ MISA cho kế toán? | Giữ MISA cho hóa đơn điện tử, thuế, BCTC đến hết P2; quyết định thay hẳn tại điểm rà soát cuối P2 | Đã chốt (điểm quyết định cuối P2) |
| D-02 | Phương pháp tính giá vốn; phạm vi tiền lương, TSCĐ, CRM | Bình quân gia quyền cuối kỳ cho vật tư/thuốc thử, đích danh cho máy có serial; tiền lương, CRM ngoài phạm vi; TSCĐ/CCDC/khế ước vay ở P3 nếu thay MISA hoàn toàn | Đã chốt (KTT rà soát trước UAT) |
| D-03 | Định dạng số chứng từ | `<LOẠI>-<CHI NHÁNH>-<YYYY>-<6 số>` | Đã chốt |
| D-04 | Ma trận phê duyệt | Duyệt bắt buộc: giá dưới giá sàn, vượt hạn mức, hợp đồng, giá dự thầu dưới biên, phiếu chi trên ngưỡng, điều chỉnh, hủy, thu hồi, đảo. Ngưỡng cụ thể là tham số | Đã chốt |
| D-05 | Chữ số thập phân, làm tròn | Số lượng 3, đơn giá 4, tiền làm tròn đến đồng | Đã chốt |
| D-06 | Hạn dùng theo tháng | Ngày cuối tháng | Đã chốt |
| D-07 | Hạn dùng còn lại tối thiểu | Cấu hình theo nhóm hàng và khách | Đã chốt |
| D-08 | Danh sách người dùng, vai trò kiêm nhiệm | Bộ vai trò mặc định theo mục 4; cho phép kiêm nhiệm, vẫn áp dụng tách biệt nhiệm vụ; danh sách người dùng thật nhập khi triển khai (GĐ 11) | Đã chốt |
| D-09 | Nhóm hàng phải qua "chờ kiểm tra" | ~~Thuốc thử và TTBYT loại C/D~~ Thay bằng D-56 (2026-10-15): nhập kho mặc định ĐẠT | Thay thế |
| D-10 | Tồn âm, xuất vượt tồn khả dụng | Cấm tồn thực tế âm tuyệt đối; xuất vượt khả dụng chỉ với chính sách Giám đốc duyệt | Đã chốt |
| D-11 | Chặn nhập/xuất khi kiểm kê | Chặn trong phạm vi kiểm kê | Đã chốt |
| D-12 | Nhà cung cấp hóa đơn điện tử, API | P2; thiết kế lớp tích hợp qua interface để thay nhà cung cấp; ưu tiên tiếp tục nhà cung cấp hiện tại (MISA meInvoice) để giữ ký hiệu, dữ liệu | Đã chốt (P2) |
| D-13 | Tồn đầu kỳ không xác định lô | Kiểm kê; phần còn lại lô tạm CÁCH LY | Đã chốt |
| D-14 | Thời gian lưu chứng từ, nhật ký `[Chưa kiểm chứng]` | Ứng dụng không xóa chứng từ, sổ phụ, nhật ký; lưu tối thiểu 10 năm, nhật ký đăng nhập tối thiểu 2 năm; dữ liệu cũ chuyển lưu trữ (archive) chứ không xóa | Đã chốt (KTT/pháp chế rà soát trước UAT) |
| D-15 | Múi giờ | Lưu UTC, hiển thị UTC+7 | Đã chốt |
| D-16 | Quy mô thực tế và 3 năm tới | Thiết kế theo BR-NF-10 nhân 5 lần làm biên an toàn: ≤ 5 chi nhánh, ≤ 10 kho, ≤ 20.000 mã hàng, ≤ 100 người dùng (≤ 50 đồng thời), ≤ 50.000 chứng từ/năm | Đã chốt (giả định thiết kế) |
| D-17 | Chứng từ `MH` có phải hàng giao thẳng? | Hỗ trợ mua giao thẳng qua kho ảo (BR-PUR-15); khi chuyển đổi dữ liệu, chứng từ `MH` được đối chiếu từng trường hợp với kế toán | Đã chốt |
| D-18 | Phạm vi dịch vụ kỹ thuật | Bảo hành, bàn giao P1; sửa chữa, bảo trì P2 | Đã chốt |
| D-19 | Đặt máy, cho mượn, ký gửi | P1: kho ảo, phiếu xuất/thu hồi, theo dõi máy tại khách; P2: theo dõi sản lượng thuốc thử theo cam kết (BR-SVC-05) | Đã chốt |
| D-20 | Bán theo bộ | P1 bán từng thành phần (có thể dùng chứng từ mẫu); P2 hàng bộ (kit) có thành phần (BR-MD-10) | Đã chốt |
| D-21 | Kinh doanh qua đấu thầu | Có; M06 thuộc P1 | Đã chốt |
| D-22 | Hạn mức tín dụng | Cảnh báo và yêu cầu duyệt | Đã chốt |
| D-23 | Số công ty, chi nhánh | 1 công ty, nhiều chi nhánh ở P1; mô hình sẵn sàng nhiều công ty | Đã chốt (danh sách chi nhánh: doanh nghiệp cung cấp) |
| D-24 | Nhập khẩu trực tiếp | P2 (BR-PUR-13); P1 nhập qua nhà phân phối trong nước, chi phí nhập khẩu (nếu có) nhập như chi phí mua hàng | Đã chốt |
| D-25 | Nguồn tra cứu MST | Xác định ở P2 | Đã chốt (P2) |
| D-26 | Hóa đơn, thuế cho hàng khuyến mại `[Chưa kiểm chứng]` | Dòng chứng từ có cờ "hàng khuyến mại"; giá trị, thuế và tài khoản hạch toán do quy tắc hạch toán cấu hình (không hard-code); mặc định không ghi doanh thu, giá vốn ghi vào chi phí bán hàng | Đã chốt (KTT rà soát trước UAT) |
| D-27 | Ngân hàng điện tử | P3 | Đã chốt |
| D-28 | Hồ sơ pháp lý bắt buộc, kê khai giá TTBYT `[Chưa kiểm chứng]` | Danh mục loại hồ sơ cấu hình được (BR-MD-44); bộ mặc định: ĐKKD, công bố đủ điều kiện mua bán TTBYT, số lưu hành/giấy phép nhập khẩu, giấy phép hóa chất, ủy quyền hãng, CO/CQ; kê khai giá ở P2 | Đã chốt (pháp chế rà soát trước UAT) |
| D-29 | Tờ khai thuế ở ERP hay MISA | MISA đến hết P2 | Đã chốt |
| D-30 | RPO, RTO | RPO 24 giờ, RTO 4 giờ | Đã chốt |
| D-31 | Ngân sách, chỉ tiêu, hoa hồng | P3 | Đã chốt |
| D-32 | Máy chủ tại công ty hay cloud | Thiết kế không phụ thuộc hạ tầng; mặc định một máy chủ Linux (Tomcat 10.1 + MySQL 8) tại công ty hoặc VPS trong nước, HTTPS qua reverse proxy, backup ra ngoài máy chủ; chọn cụ thể trước GĐ 11 | Đã chốt |
| D-33 | Cấu trúc repository | Một repo: `backend/`, `frontend/`, `docs/` (bỏ `docker/` theo D-44) | Đã chốt |
| D-34 | Thời điểm ghi nhận công nợ phải trả `[Chưa kiểm chứng]` | Khi hóa đơn mua ghi sổ; hàng về chưa có hóa đơn theo dõi riêng với giá tạm tính | Đã chốt (KTT rà soát trước UAT) |
| D-35 | Thời điểm ghi nhận doanh thu, giá vốn `[Chưa kiểm chứng]` | Doanh thu và phải thu theo hóa đơn bán ghi sổ; giá vốn ghi nhận cùng thời điểm; hàng đã xuất chưa có hóa đơn theo dõi là hàng gửi đi bán | Đã chốt (KTT rà soát trước UAT) |
| D-36 | Tách phiếu nhận hàng và phiếu nhập kho | Hai chứng từ riêng, cho phép lập trong một thao tác. **Thay bằng D-54 (2026-10-12)** | Thay thế |
| D-37 | Giữ hàng theo đơn | Bắt buộc với hợp đồng thầu; tùy chọn với đơn thường; thời hạn mặc định 7 ngày (tham số) | Đã chốt |
| D-38 | Cơ chế xác thực | JWT access token ngắn hạn + refresh token xoay vòng lưu phía server | Đã chốt |
| D-39 | Đóng gói | WAR triển khai trên Tomcat 10.1; chạy `java -jar` khi phát triển | Đã chốt |
| D-40 | Chính sách xuất vượt tồn khả dụng | Giám đốc duyệt theo từng chứng từ | Đã chốt |
| D-41 | File tải lên | PDF, ảnh (JPG, PNG), Office (DOCX, XLSX); tối đa 20 MB/file (tham số) | Đã chốt |
| D-42 | Thư viện bổ sung | ArchUnit (kiểm tra ranh giới module), TanStack Query (cache dữ liệu server ở frontend), springdoc-openapi | Đã chốt |
| D-43 | Gói gốc Java | `com.company.erp`; đổi khi có tên chính thức | Đã chốt |
| D-44 | Docker | Không dùng Docker / Docker Compose / Testcontainers. Dev và test dùng MySQL 8 local; backend chạy Eclipse + Tomcat 10.1. Test tích hợp vẫn chạy trên MySQL thật (schema `_test`). Chốt 2026-10-09 theo yêu cầu chủ dự án | Đã chốt |
| D-45 | Cách mở lại kỳ đã khóa (BR-SYS-32) | Người có quyền `period:unlock` (mặc định Kế toán trưởng, Giám đốc) mở trực tiếp; bắt buộc lý do, ghi audit; hành động mở là sự phê duyệt. Có thể chuyển sang luồng đề nghị → duyệt khi làm ma trận duyệt (1D). Chốt 2026-10-10 | Đã chốt |
| D-46 | Thứ tự khóa/mở kỳ | Tháng N chỉ khóa khi tháng N-1 (nếu có) đã khóa; mở lại theo thứ tự ngược. Ngày hạch toán rơi vào kỳ chưa tạo bị chặn. Chốt 2026-10-10 | Đã chốt |
| D-47 | Phạm vi khóa kỳ (BR-SYS-30) | Khóa theo chi nhánh × phân hệ; giao diện hỗ trợ khóa nhiều/tất cả chi nhánh một lần. Kỳ kế toán của chi nhánh chỉ khóa khi kỳ kho cùng tháng của chi nhánh đó đã khóa. Chốt 2026-10-10 | Đã chốt |
| D-49 | Kênh thông báo P1 (BR-SYS-25) | Chỉ thông báo trong ứng dụng (chuông trên header, danh sách thông báo); email làm sau. Chốt 2026-10-10 theo lựa chọn của chủ dự án | Đã chốt |
| D-50 | Nhập danh mục từ Excel (BR-UX-05, BR-MIG-01) | Làm ngay ở Giai đoạn 2 bằng Apache POI (`poi-ooxml`); nhập "tất cả hoặc không", lỗi trả theo dòng. Chốt 2026-10-10 | Đã chốt |
| D-51 | Giá trị nhập / xuất kho trước khi có giá vốn | Phiếu nhập kho bắt buộc đơn giá (lưu vào sổ kho); giá xuất để trống, do tính giá vốn (Giai đoạn 4/8) điền. Chốt 2026-10-10 | Đã chốt |
| D-52 | Chế độ duyệt theo loại chứng từ (BR-SYS-11, D-04) | Mỗi loại chứng từ có chế độ `NONE` (không duyệt), `CONFIGURED` (duyệt khi có luồng khớp), `REQUIRED` (bắt buộc có luồng, thiếu thì báo lỗi `APPROVAL_NOT_CONFIGURED`). Mặc định kho: PNK, PCK `CONFIGURED`; PXK, PXH, PDC, PCL, PDK, PKK, PTH, CSX `REQUIRED`; doanh nghiệp đổi ở màn hình Loại chứng từ. Cảnh báo hạn dùng mặc định 90 ngày (tham số `inventory.expiry-warning-days`). Chốt 2026-10-10 làm giá trị mặc định | Đã chốt |
| D-53 | Chính sách xuất vượt tồn khả dụng: cách áp dụng (BR-GEN-31) | Chính sách là chứng từ riêng (CSX) theo kho (và hàng, nếu chọn), có hạn mức số lượng và thời hạn, Giám đốc duyệt. Theo D-40, **mỗi chính sách chỉ dùng cho một chứng từ xuất**: chứng từ thứ hai không tham chiếu được; chính sách đã dùng không hủy được; lưu chứng từ đã dùng để truy vết. Chốt 2026-10-10 | Đã chốt |
| D-54 | Gộp chứng từ mua / bán (thay D-36; sửa BR-PUR-01..11, BR-SAL-01..05, 15, 20..23, 30..33, 42, AT-01, AT-05) | **Đơn mua** là chứng từ ghi sổ duy nhất của nghiệp vụ mua: mang hóa đơn NCC (không bắt buộc), lô / hạn dùng / serial; ghi sổ = phiếu nhập kho liên kết + công nợ phải trả + bút toán. **Đơn bán** là chứng từ ghi sổ duy nhất của nghiệp vụ bán: ghi sổ = phiếu xuất kho liên kết (FEFO) + doanh thu + công nợ phải thu + bút toán; số hóa đơn điện tử nhập trước / sau ghi sổ; theo dõi giao hàng trên đơn; giao thất bại = đảo đơn. Bỏ đề nghị mua, yêu cầu / so sánh báo giá NCC (5E), phiếu nhận hàng, hóa đơn mua riêng, dung sai nhận vượt, báo giá bán, phiếu giao hàng, hóa đơn bán riêng, bán nhanh. Mất: nhận / giao nhiều đợt cho một đơn, một hóa đơn cho nhiều lần giao, hàng về trước hóa đơn về sau, kiểm nhận hàng lỗi khi nhận. Chủ dự án chốt 2026-10-12 | Đã chốt |
| D-55 | Mã cũ MISA của hàng hóa, đối tác (sửa BR-MD-01, H-14) | Bỏ ô "Mã cũ (MISA)" khỏi mọi màn hình và cột tương ứng khỏi mẫu nhập Excel hàng hóa, đối tác. Giữ cột `legacy_code` và trường `legacyCode` trong API (tương thích ngược, giữ dữ liệu đã nhập); sửa hàng hóa / đối tác giữ nguyên giá trị đang lưu; tìm kiếm ở server vẫn khớp mã cũ. Chủ dự án chốt 2026-10-15 | Đã chốt |
| D-56 | Trạng thái chất lượng khi nhập kho (thay D-09, sửa BR-INV-03) | Mọi nhập kho (ghi sổ đơn mua, phiếu nhập kho, tồn đầu kỳ) mặc định **ĐẠT**, bán được ngay; bỏ quy tắc thuốc thử và TTBYT loại C/D phải qua "chờ kiểm tra". Vẫn chọn tay được trạng thái khác trên dòng phiếu nhập (ví dụ CÁCH LY cho lô tạm D-13); nhập thu hồi vẫn là THU HỒI; phiếu chuyển trạng thái chất lượng giữ nguyên. API hàng hóa bỏ trường `initialQualityStatus`. Chủ dự án chốt 2026-10-15 | Đã chốt |
| D-57 | Định dạng số hiển thị, nhập - xuất - tồn có giá trị (sửa phần hiển thị D-05; BR-RPT-22) | Toàn hệ thống hiển thị số kiểu Việt Nam: **số lượng luôn 2 số lẻ** (114.300,00), **giá trị tiền làm tròn đến đồng** (373.400.000), **số âm màu đỏ trong ngoặc** ((5.955,00)); DB và tính toán vẫn giữ 3 số lẻ số lượng, 4 số lẻ đơn giá (D-05), đơn giá / phần trăm hiển thị như cũ. Báo cáo nhập - xuất - tồn nhóm theo kho (dòng tổng mỗi kho cộng cả số lượng và giá trị), mỗi nhóm Đầu kỳ / Nhập / Xuất / Cuối kỳ có Số lượng + Giá trị; giá trị **tạm tính** = số lượng × đơn giá nhập bình quân (phiếu nhập, đơn mua, tồn đầu kỳ, điều chỉnh tăng, kiểm kê thừa) toàn công ty đến cuối kỳ, chưa phải giá vốn (D-51 giữ nguyên), thay bằng giá vốn ở Giai đoạn 8. Chủ dự án chốt 2026-10-16 | Đã chốt |
| D-58 | Công ty in trên phiếu nhập kho, phiếu xuất kho (BR-PUR-08, BR-SAL-21) | Trước nút "In phiếu nhập kho" của đơn mua và "In phiếu xuất kho" của đơn bán có ô chọn công ty, mặc định công ty của người dùng; chỉ liệt kê công ty đang hoạt động trong phạm vi người dùng. Đầu phiếu in tên, địa chỉ công ty đã chọn; tiêu đề hộp xem trước là tên thao tác (không lặp lại tên công ty). Dữ liệu chứng từ không đổi (đơn vẫn thuộc công ty của đơn); in theo công ty khác công ty của đơn ghi nhật ký audit. Chủ dự án chốt 2026-10-16 | Đã chốt |
| D-48 | Sửa cấu hình số chứng từ đã cấp số (BR-SYS-10) | Thay đổi chỉ có hiệu lực từ chu kỳ sau (đầu tháng sau nếu đặt lại theo tháng; đầu năm sau nếu theo năm hoặc đổi chu kỳ); chưa cấp số thì hiệu lực ngay. Chi nhánh và kỳ luôn in trong số. Chốt 2026-10-10 | Đã chốt |

---

## 21. Ma trận truy vết

### 21.1 Vấn đề hiện trạng → yêu cầu
| Vấn đề | Yêu cầu giải quyết |
|---|---|
| P-01 Không có lô, hạn dùng | BR-INV-01..06, BR-INV-20..23, BR-INV-41..42, BR-RPT-24 |
| P-02 Không cảnh báo hạn dùng | BR-INV-04, BR-INV-11, BR-INV-40, BR-RPT-04, BR-RPT-23 |
| P-03 Thuộc tính ghép trong tên hàng | BR-MD-03, BR-MIG-01 |
| P-04 Đơn vị tính hỗn hợp | BR-MD-20..21, BR-INV-24 |
| P-05 Không có hồ sơ chất lượng, số lưu hành | BR-MD-05, BR-MD-44, BR-INV-05, BR-EQP-06, BR-SAL-22 |
| P-06 Không có phê duyệt | BR-GEN-05, BR-SYS-11, BR-SAL-03, BR-SAL-14, BR-SYS-14 |
| P-07 Danh mục đối tác lẫn lộn | BR-MD-30, BR-MIG-01 |
| P-08 Không theo dõi serial, bảo hành | BR-INV-02, BR-EQP-01..07, BR-SVC-01..06 |
| P-09 Không theo dõi hợp đồng, thầu | BR-TND-01..27 |
| P-10 Không có báo cáo kinh doanh tức thời | BR-SAL-51, BR-CST-04, BR-RPT-01..11, BR-RPT-27 |

### 21.2 Đặc tả dự án → yêu cầu
| Mục đặc tả | Yêu cầu |
|---|---|
| Mục tiêu nghiệp vụ (3 chuỗi, liên kết, không lệch số liệu) | Mục 1, BR-GEN-P1..P3, mục 5.3 |
| 3. Công nghệ bắt buộc | BR-NF-01..02, `CLAUDE.md` mục 2–3 |
| 4. Kiến trúc | BR-NF-03, BR-GEN-50..54, `CLAUDE.md` mục 4–5, `.claude/rules/architecture.md` |
| Module 01..10 | Mục 6..15 |
| 6. Quy tắc nghiệp vụ bắt buộc | Mục 5.1..5.6 |
| 7. Thiết kế database | `.claude/rules/database.md`, `docs/03-database-design.md` |
| 8. Thiết kế giao diện | BR-UI-01..09, `.claude/rules/frontend.md` |
| 9. Bảo mật | BR-SEC-01..06, BR-SYS-20..24, `.claude/rules/security.md` |
| 10. Kiểm thử và nghiệm thu | Mục 18, `.claude/rules/testing.md` |
| 11. Tài liệu bắt buộc | Mục 19, `.claude/rules/documentation.md` |

### 21.3 Chức năng MISA đang dùng → yêu cầu
| Chức năng MISA | Yêu cầu |
|---|---|
| Bàn làm việc, tìm tính năng, tìm chứng từ (H-05) | BR-UX-01..03 |
| Chính sách giá, chiết khấu thương mại (H-06) | BR-MD-42, BR-SAL-02, BR-SAL-35 |
| Doanh thu nhận trước (H-30) | BR-SAL-37 |
| Thu hàng loạt, đối trừ, bỏ đối trừ, bù trừ (H-06, H-07) | BR-AR-03, BR-AR-04, BR-AR-08 |
| Mua hàng nhiều hóa đơn (H-07) | BR-PUR-09 |
| Tình hình thực hiện đơn đặt hàng, hợp đồng, lãi lỗ theo đơn (H-05, H-07) | BR-RPT-25..28 |
| Sổ theo dõi tình trạng bảo hành (H-36) | BR-EQP-02, BR-RPT-33 |
| Thuế, Tổng hợp, Báo cáo đối chiếu (H-01, H-05) | BR-TAX-01..03, BR-ACC-01..10, BR-RPT-32 |
