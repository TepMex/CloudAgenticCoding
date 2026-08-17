const DIGITS = ["零", "一", "二", "三", "四", "五", "六", "七", "八", "九"] as const;

function belowTenThousand(value: number): string {
  const units = ["", "十", "百", "千"];
  let output = "";
  let zeroNeeded = false;

  for (let place = 3; place >= 0; place -= 1) {
    const divisor = 10 ** place;
    const digit = Math.floor(value / divisor) % 10;
    if (digit === 0) {
      if (output && value % divisor !== 0) zeroNeeded = true;
      continue;
    }
    if (zeroNeeded) {
      output += DIGITS[0];
      zeroNeeded = false;
    }
    if (!(place === 1 && digit === 1 && output === "")) output += DIGITS[digit];
    output += units[place];
  }
  return output;
}

export function toChineseNumber(value: number): string {
  if (!Number.isInteger(value) || value < 1 || value > 9999) {
    throw new RangeError("Chinese numbers are supported for integers from 1 to 9999");
  }
  return belowTenThousand(value);
}

export function toChineseMoney(value: number): string {
  return `${toChineseNumber(value)}元`;
}
