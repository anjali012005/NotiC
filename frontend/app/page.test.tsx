import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
    getGatewayHealth: vi.fn(),
    getNotificationCount: vi.fn(),
    getNotifications: vi.fn(),
}));

vi.mock("@/components/api-key-context", () => ({
    useApiKey: () => ({ apiKey: "test-tenant-key" }),
}));

vi.mock("@/lib/api", () => ({
    ApiError: class ApiError extends Error { },
    ...api,
}));

import OverviewPage from "@/app/page";

afterEach(() => cleanup());

beforeEach(() => {
    vi.resetAllMocks();
    api.getNotificationCount.mockImplementation((_key: string, status: string) => Promise.resolve({ QUEUED: 2, PROCESSING: 1, SENT: 8, FAILED: 2 }[status as "QUEUED" | "PROCESSING" | "SENT" | "FAILED"]));
    api.getGatewayHealth.mockResolvedValue({ status: "UP" });
    api.getNotifications.mockResolvedValue({ content: [], page: 0, size: 5, totalPages: 0, totalElements: 13, last: true });
});

describe("overview page", () => {
    it("shows a loading state while the API requests are pending", () => {
        api.getNotifications.mockReturnValue(new Promise(() => undefined));

        render(<OverviewPage />);

        expect(screen.getByText("Retrieving platform totals")).toBeInTheDocument();
    });

    it("shows server totals and a meaningful empty state", async () => {
        render(<OverviewPage />);

        expect(await screen.findByText("Nothing has moved yet")).toBeInTheDocument();
        expect(screen.getByLabelText("All notifications count")).toHaveTextContent("13");
        expect(screen.getByLabelText("Delivered count")).toHaveTextContent("8");
        expect(screen.getByText("Gateway availability")).toBeInTheDocument();
    });

    it("shows a helpful error instead of fabricated counts when the API fails", async () => {
        api.getNotifications.mockRejectedValue(new Error("offline"));

        render(<OverviewPage />);

        expect(await screen.findByRole("alert")).toHaveTextContent("Overview data could not be loaded.");
        expect(screen.getByLabelText("All notifications count")).toHaveTextContent("--");
    });
});