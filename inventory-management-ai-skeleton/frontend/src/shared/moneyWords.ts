const DIGITS = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
const GROUP_UNITS = ['', 'nghìn', 'triệu', 'tỷ', 'nghìn tỷ', 'triệu tỷ'];

function digit(value: number): string {
  return DIGITS[value] ?? '';
}

/** Đọc một nhóm ba chữ số; {@code full}: nhóm không đứng đầu thì đọc cả "không trăm", "linh". */
function readGroup(group: number, full: boolean): string[] {
  const hundreds = Math.floor(group / 100);
  const tens = Math.floor((group % 100) / 10);
  const units = group % 10;
  const words: string[] = [];
  if (hundreds > 0 || full) {
    words.push(digit(hundreds), 'trăm');
  }
  if (tens === 0) {
    if (units > 0 && (hundreds > 0 || full)) {
      words.push('linh');
    }
  } else if (tens === 1) {
    words.push('mười');
  } else {
    words.push(digit(tens), 'mươi');
  }
  if (units === 1 && tens > 1) {
    words.push('mốt');
  } else if (units === 5 && tens > 0) {
    words.push('lăm');
  } else if (units > 0) {
    words.push(digit(units));
  }
  return words;
}

/** Số tiền bằng chữ trên chứng từ, làm tròn đến đồng: 65946000 → "Sáu mươi lăm triệu chín trăm bốn mươi sáu nghìn đồng chẵn." */
export function moneyInWords(value: number): string {
  const amount = Math.round(Math.abs(value));
  if (amount === 0) {
    return 'Không đồng.';
  }
  const groups: number[] = [];
  for (let rest = amount; rest > 0; rest = Math.floor(rest / 1000)) {
    groups.push(rest % 1000);
  }
  const words: string[] = [];
  groups.forEach((group, index) => {
    if (group === 0) {
      return;
    }
    const unit = GROUP_UNITS[index] ?? '';
    words.unshift(...readGroup(group, index < groups.length - 1), ...(unit ? [unit] : []));
  });
  const text = `${value < 0 ? 'âm ' : ''}${words.join(' ')} đồng chẵn.`;
  return text.charAt(0).toUpperCase() + text.slice(1);
}
