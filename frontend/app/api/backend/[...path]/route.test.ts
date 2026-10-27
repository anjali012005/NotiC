import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/backend/[...path]/route";

const routeContext = (path: string[]) => ({ params: Promise.resolve({ path }) });

afterEach(() => vi.unstubAllGlobals());

describe("gateway proxy", () => {
    it("rejects protected API calls without an API key", async () => {
        const upstream = vi.fn();
        vi.stubGlobal("fetch", upstream);
        const request = new NextRequest("http://localhost:3000/api/backend/api/v1/notifications");

        const response = await GET(request, routeContext(["api", "v1", "notifications"]));

        expect(response.status).toBe(401);
        expect(upstream).not.toHaveBeenCalled();
    });

    it("rejects path traversal before making an upstream request", async () => {
        const upstream = vi.fn();
        vi.stubGlobal("fetch", upstream);
        const request = new NextRequest("http://localhost:3000/api/backend/api/v1/notifications/../actuator/health");

        const response = await GET(request, routeContext(["api", "v1", "notifications", "..", "actuator", "health"]));

        expect(response.status).toBe(404);
        expect(upstream).not.toHaveBeenCalled();
    });

    it("redacts message content and raw failure details from notification responses", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
            id: "notification-1",
            subject: "private subject",
            body: "private content",
            failureReason: "provider response with sensitive details",
            status: "FAILED",
            retryEligible: false,
        }), { status: 200, headers: { "Content-Type": "application/json" } })));
        const request = new NextRequest("http://localhost:3000/api/backend/api/v1/notifications/notification-1", {
            headers: { "X-API-Key": "tenant-key" },
        });

        const response = await GET(request, routeContext(["api", "v1", "notifications", "notification-1"]));

        expect(response.status).toBe(200);
        expect(response.headers.get("Cache-Control")).toBe("no-store");
        expect(await response.json()).toEqual({ id: "notification-1", status: "FAILED", retryEligible: false });
    });
});