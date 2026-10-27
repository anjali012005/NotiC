import { NextRequest, NextResponse } from "next/server";

const allowedApiRoots = ["/api/v1/notifications", "/api/v1/templates", "/api/v1/providers"];

async function proxy(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
    const { path: segments } = await context.params;
    if (segments.some((segment) => segment === "." || segment === ".." || segment.includes("/") || segment.includes("\\"))) {
        return NextResponse.json({ message: "This gateway path is not available through the dashboard." }, { status: 404 });
    }
    const path = `/${segments.join("/")}`;
    const isHealth = path === "/actuator/health";
    const isApiPath = allowedApiRoots.some((root) => path === root || path.startsWith(`${root}/`));

    if (!isHealth && !isApiPath) {
        return NextResponse.json({ message: "This gateway path is not available through the dashboard." }, { status: 404 });
    }
    if (!isHealth && !request.headers.get("x-api-key")) {
        return NextResponse.json({ message: "An API key is required." }, { status: 401 });
    }

    const baseUrl = process.env.NOTIFYFLOW_API_BASE_URL ?? "http://localhost:8080";
    let destination: URL;
    try {
        destination = new URL(`${path}${request.nextUrl.search}`, baseUrl);
        if (!new Set(["http:", "https:"]).has(destination.protocol)) throw new Error("Unsupported protocol");
    } catch {
        return NextResponse.json({ message: "The gateway URL is not configured correctly." }, { status: 500 });
    }

    const headers = new Headers({ Accept: "application/json" });
    const apiKey = request.headers.get("x-api-key");
    if (apiKey) headers.set("X-API-Key", apiKey);
    const contentType = request.headers.get("content-type");
    if (contentType) headers.set("Content-Type", contentType);

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
        const upstream = await fetch(destination, {
            method: request.method,
            headers,
            body: ["GET", "HEAD"].includes(request.method) ? undefined : await request.text(),
            cache: "no-store",
            signal: controller.signal,
        });
        let body = await upstream.text();
        if (upstream.ok && request.method === "GET" && path.startsWith("/api/v1/notifications")) {
            try {
                const payload: unknown = JSON.parse(body);
                const safeNotification = (value: unknown) => {
                    if (!value || typeof value !== "object" || Array.isArray(value)) return value;
                    const { body: _body, subject: _subject, failureReason: _failureReason, ...safe } = value as Record<string, unknown>;
                    return safe;
                };
                if (payload && typeof payload === "object" && "content" in payload && Array.isArray(payload.content)) {
                    body = JSON.stringify({ ...payload, content: payload.content.map(safeNotification) });
                } else {
                    body = JSON.stringify(safeNotification(payload));
                }
            } catch {
                return NextResponse.json({ message: "The gateway returned an unreadable notification response." }, { status: 502 });
            }
        }
        return new NextResponse(body, {
            status: upstream.status,
            headers: {
                "Content-Type": upstream.headers.get("content-type") ?? "application/json",
                "Cache-Control": "no-store",
            },
        });
    } catch {
        return NextResponse.json({ message: "The NotifyFlow gateway is unavailable." }, { status: 502 });
    } finally {
        clearTimeout(timeout);
    }
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;