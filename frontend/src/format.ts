/**
 * One date/time format for the whole app, so a comment timestamp, a status move and an
 * archive date all read the same way: "21 Aug 2026, 2:32 PM".
 */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}
