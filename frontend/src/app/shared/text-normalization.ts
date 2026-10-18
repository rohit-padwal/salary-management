// Match the Java 21 API's Character.isWhitespace rule, rather than JavaScript trim().
// NBSP, figure space, narrow NBSP, and BOM remain literal characters.
const apiWhitespace =
  /[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]/;

export function stripOuterWhitespace(value: string): string {
  let start = 0;
  let end = value.length;
  while (start < end && apiWhitespace.test(value[start])) start++;
  while (end > start && apiWhitespace.test(value[end - 1])) end--;
  return value.slice(start, end);
}
