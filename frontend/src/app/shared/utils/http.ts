export function extractFilenameFromContentDisposition(cd: string): string | null {
  if (!cd) return null;
  // common patterns: attachment; filename="name.pdf" or inline; filename=name.pdf
  const m = /filename\*=UTF-8''([^;\n\r]+)|filename="([^"]+)"|filename=([^;\n\r]+)/i.exec(cd);
  if (!m) return null;
  return decodeURIComponent((m[1] || m[2] || m[3] || '').trim());
}
