import { describe, expect, it } from 'vitest';
import type { TaxCodeLookup } from '../types';
import { taxCodeFill } from './partnerFields';

const result = (partial: Partial<TaxCodeLookup> = {}): TaxCodeLookup => ({
  taxCode: '0311315789',
  name: 'CÔNG TY CỔ PHẦN TẬP ĐOÀN BAMBOO CAPITAL',
  internationalName: null,
  shortName: null,
  address: '22A đường số 7, Phường Bình Trưng, TP Hồ Chí Minh',
  phone: null,
  status: 'NNT đang hoạt động',
  source: 'Xinvoice',
  ...partial,
});

describe('taxCodeFill (D-66)', () => {
  it('điền ô còn trống, bỏ trường nguồn không có', () => {
    const fill = taxCodeFill({ name: '  ', phone: '0909' }, result());
    expect(fill.values).toEqual({
      name: 'CÔNG TY CỔ PHẦN TẬP ĐOÀN BAMBOO CAPITAL',
      address: '22A đường số 7, Phường Bình Trưng, TP Hồ Chí Minh',
    });
    expect(fill.notes).toEqual([]);
  });

  it('không ghi đè ô đã nhập; giá trị khác thì ghi chú; tình trạng không hoạt động thì cảnh báo', () => {
    const fill = taxCodeFill(
      { name: 'Bamboo', address: '22A đường số 7, Phường Bình Trưng, TP Hồ Chí Minh' },
      result({ phone: '02862680680', status: 'NNT ngừng hoạt động và đã đóng MST' }),
    );
    expect(fill.values).toEqual({ phone: '02862680680' });
    expect(fill.notes).toEqual([
      'Tên theo mã số thuế: CÔNG TY CỔ PHẦN TẬP ĐOÀN BAMBOO CAPITAL',
      'Tình trạng theo mã số thuế: NNT ngừng hoạt động và đã đóng MST',
    ]);
  });
});
