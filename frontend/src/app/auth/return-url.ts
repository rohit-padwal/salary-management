export function safeReturnUrl(value: string | null): string {
  // Restrict redirects to known feature roots, retaining their search/filter query and fragment.
  return value &&
    !/[\\\u0000-\u001f\u007f]/.test(value) &&
    /^\/(?:employees(?:\/new)?|analytics)(?:\?[^#]*)?(?:#.*)?$/.test(value)
    ? value
    : '/employees';
}
