import { afterEach, describe, expect, it, vi } from "vitest";
import { createProvider, getNotifications, getTemplates, sendNotification, setProviderEnabled } from "@/lib/api";

afterEach(() => vi.unstubAllGlobals());

describe("NotifyFlow API client", () => {
  it("requests the gateway proxy with filters and the tenant API key", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [], totalElements: 0 }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await getNotifications("tenant-key", { page: 2, size: 10, status: "FAILED", channel: "EMAIL" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/backend/api/v1/notifications?page=2&size=10&status=FAILED&channel=EMAIL",
      expect.objectContaining({ cache: "no-store" }),
    );
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("X-API-Key")).toBe("tenant-key");
  });

  it("sends only the supported notification request fields", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: "abc", status: "QUEUED" }), { status: 202 }));
    vi.stubGlobal("fetch", fetchMock);

    await sendNotification("tenant-key", { templateKey: "WELCOME", recipient: "a@example.test", variables: { name: "Ada" } });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/backend/api/v1/notifications/send");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      templateKey: "WELCOME",
      recipient: "a@example.test",
      variables: { name: "Ada" },
    });
  });

  it("sends recipient filtering using the backend's exact query parameter", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await getNotifications("tenant-key", { recipient: "a@example.test" });

    expect(fetchMock.mock.calls[0][0]).toContain("recipient=a%40example.test");
  });

  it("uses backend-supported template channel and tag filters", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await getTemplates("tenant-key", 1, "EMAIL", "onboarding");

    expect(fetchMock.mock.calls[0][0]).toBe("/api/backend/api/v1/templates?page=1&size=50&sortBy=createdAt&sortDir=desc&channel=EMAIL&tag=onboarding");
  });

  it("toggles providers through the supported PATCH route", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: "provider-1", enabled: false }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await setProviderEnabled("tenant-key", "provider-1", false);

    expect(fetchMock.mock.calls[0][0]).toBe("/api/backend/api/v1/providers/provider-1/disable");
    expect(fetchMock.mock.calls[0][1].method).toBe("PATCH");
  });

  it("creates a SendGrid provider without sending credential material", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: "provider-1" }), { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);
    const input = {
      name: "Primary SendGrid",
      channel: "EMAIL" as const,
      providerType: "SENDGRID" as const,
      config: JSON.stringify({ fromEmail: "notifications@example.com", fromName: "NotifyFlow" }),
      enabled: false,
      isDefault: false,
    };

    await createProvider("tenant-key", input);

    expect(fetchMock.mock.calls[0][0]).toBe("/api/backend/api/v1/providers");
    expect(fetchMock.mock.calls[0][1].method).toBe("POST");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(input);
    expect(JSON.stringify(input)).not.toContain("apiKey");
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("X-API-Key")).toBe("tenant-key");
  });

  it("maps unauthorized errors without exposing backend response details", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "Invalid API key", details: "secret internals" }), { status: 401 })));

    await expect(getNotifications("bad-key")).rejects.toMatchObject({
      status: 401,
      message: "The API key was rejected. Check the key and try again.",
    });
  });
});
