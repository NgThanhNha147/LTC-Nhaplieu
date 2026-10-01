let memoryToken: string | null = null;

export const securityEnabled = String(import.meta.env.VITE_SECURITY_ENABLED ?? "true").toLowerCase() === "true";

export function getAccessToken(): string | null {
  if (!securityEnabled) return null;
  if (memoryToken) return memoryToken;
  return memoryToken;
}

export function setAccessToken(token: string): void {
  memoryToken = token;
}

export function clearAccessToken(): void {
  memoryToken = null;
}

export function notifyUnauthorized(): void {
  window.dispatchEvent(new CustomEvent("auth:unauthorized"));
}
