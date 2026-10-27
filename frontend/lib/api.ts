import type {
  GatewayHealth,
  NotificationAccepted,
  NotificationStatus,
  NotificationSummary,
  PageResponse,
  Provider,
  Template,
  TemplateVersion,
} from "@/types/api";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type RequestOptions = Omit<RequestInit, "headers"> & {
  apiKey?: string;
  headers?: HeadersInit;
};

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { apiKey, headers, ...init } = options;
  const requestHeaders = new Headers(headers);
  requestHeaders.set("Accept", "application/json");
  if (apiKey) requestHeaders.set("X-API-Key", apiKey);
  if (init.body && !requestHeaders.has("Content-Type")) {
    requestHeaders.set("Content-Type", "application/json");
  }

  let response: Response;
  try {
    response = await fetch(`/api/backend${path}`, {
      ...init,
      headers: requestHeaders,
      cache: "no-store",
    });
  } catch {
    throw new ApiError("Could not reach NotifyFlow. Check the gateway connection and try again.", 0);
  }

  const text = await response.text();
  let payload: unknown;
  try {
    payload = text ? JSON.parse(text) : undefined;
  } catch {
    payload = undefined;
  }

  if (!response.ok) {
    const body = payload && typeof payload === "object" ? payload as Record<string, unknown> : {};
    const fieldErrors = safeFieldErrors(body.details);
    const message = responseMessage(response.status, body.message);
    throw new ApiError(message, response.status, fieldErrors);
  }

  return payload as T;
}

function responseMessage(status: number, backendMessage: unknown): string {
  if (status === 401) return "The API key was rejected. Check the key and try again.";
  if (status === 403) return "This API key is not authorized for that operation.";
  if (status === 404) return "The requested NotifyFlow resource was not found.";
  if (status === 409 || status === 400) {
    if (typeof backendMessage === "string") return backendMessage.slice(0, 240);
    return "The request could not be completed. Review the form and try again.";
  }
  if (status === 429) return "NotifyFlow is rate limiting requests. Wait a moment and try again.";
  if (status >= 500) return "NotifyFlow encountered a server error. Try again later.";
  return "The request could not be completed.";
}

function safeFieldErrors(details: unknown): Record<string, string> {
  if (!details || typeof details !== "object" || Array.isArray(details)) return {};
  return Object.fromEntries(
    Object.entries(details as Record<string, unknown>)
      .filter((entry): entry is [string, string] => typeof entry[1] === "string")
      .map(([field, message]) => [field, message.slice(0, 160)]),
  );
}

export function getNotifications(
  apiKey: string,
  params: { page?: number; size?: number; status?: NotificationStatus; channel?: string; recipient?: string } = {},
) {
  const query = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 20) });
  if (params.status) query.set("status", params.status);
  if (params.channel) query.set("channel", params.channel);
  if (params.recipient) query.set("recipient", params.recipient);
  return request<PageResponse<NotificationSummary>>(`/api/v1/notifications?${query}`, { apiKey });
}

export function getNotification(apiKey: string, id: string) {
  return request<NotificationSummary>(`/api/v1/notifications/${encodeURIComponent(id)}`, { apiKey });
}

export function getNotificationCount(apiKey: string, status?: NotificationStatus) {
  return getNotifications(apiKey, { page: 0, size: 1, status }).then((page) => page.totalElements);
}

export function sendNotification(apiKey: string, input: { templateKey: string; recipient: string; variables: Record<string, string> }) {
  return request<NotificationAccepted>("/api/v1/notifications/send", {
    apiKey,
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function retryNotification(apiKey: string, id: string) {
  return request<{ message: string }>(`/api/v1/notifications/${encodeURIComponent(id)}/retry`, {
    apiKey,
    method: "POST",
  });
}

export function getTemplates(apiKey: string, page = 0, channel?: string, tag?: string) {
  const query = new URLSearchParams({ page: String(page), size: "50", sortBy: "createdAt", sortDir: "desc" });
  if (channel) query.set("channel", channel);
  if (tag) query.set("tag", tag);
  return request<PageResponse<Template>>(`/api/v1/templates?${query}`, { apiKey });
}

export function createTemplate(apiKey: string, input: Pick<Template, "templateKey" | "name" | "channel" | "subject" | "body"> & { tags: string[] }) {
  return request<Template>("/api/v1/templates", { apiKey, method: "POST", body: JSON.stringify(input) });
}

export function getTemplateVariables(apiKey: string, id: string) {
  return request<string[]>(`/api/v1/templates/${encodeURIComponent(id)}/variables`, { apiKey });
}

export function updateTemplate(apiKey: string, id: string, input: Omit<Parameters<typeof createTemplate>[1], "templateKey">) {
  return request<Template>(`/api/v1/templates/${encodeURIComponent(id)}`, { apiKey, method: "PUT", body: JSON.stringify(input) });
}

export function deleteTemplate(apiKey: string, id: string) {
  return request<{ message: string }>(`/api/v1/templates/${encodeURIComponent(id)}`, { apiKey, method: "DELETE" });
}

export function getTemplateVersions(apiKey: string, id: string) {
  return request<TemplateVersion[]>(`/api/v1/templates/${encodeURIComponent(id)}/versions`, { apiKey });
}

export function createTemplateVersion(apiKey: string, id: string, input: { subject: string; body: string }) {
  return request<Template>(`/api/v1/templates/${encodeURIComponent(id)}/versions`, {
    apiKey,
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function getProviders(apiKey: string, filters: { channel?: string; enabled?: boolean } = {}) {
  const query = new URLSearchParams();
  if (filters.channel) query.set("channel", filters.channel);
  if (filters.enabled !== undefined) query.set("enabled", String(filters.enabled));
  const suffix = query.size ? `?${query}` : "";
  return request<Provider[]>(`/api/v1/providers${suffix}`, { apiKey });
}

export function createProvider(apiKey: string, input: {
  name: string;
  channel: "EMAIL";
  providerType: "SENDGRID";
  config: string;
  enabled: boolean;
  isDefault: boolean;
}) {
  return request<Provider>("/api/v1/providers", {
    apiKey,
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function setProviderEnabled(apiKey: string, id: string, enabled: boolean) {
  const action = enabled ? "enable" : "disable";
  return request<Provider>(`/api/v1/providers/${encodeURIComponent(id)}/${action}`, { apiKey, method: "PATCH" });
}

export function getGatewayHealth() {
  return request<GatewayHealth>("/actuator/health");
}

export function setProviderDefault(apiKey: string, id: string) {
  return request<Provider>(`/api/v1/providers/${encodeURIComponent(id)}/default`, { apiKey, method: "PATCH" });
}
