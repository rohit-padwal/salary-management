export function safeReturnUrl(value: string | null): string {
  // Restrict redirects to known feature routes, retaining their query and fragment.
  return value &&
    !/[\\\u0000-\u001f\u007f]/.test(value) &&
    /^\/(?:employees(?:\/new|\/[0-9]+\/edit)?|analytics)(?:\?[^#]*)?(?:#.*)?$/.test(value)
    ? value
    : '/employees';
}
