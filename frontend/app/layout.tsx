import type { Metadata } from "next";
import { DashboardShell } from "@/components/dashboard-shell";
import "./globals.css";

export const metadata: Metadata = {
    title: "NotifyFlow | Messaging Operations",
    description: "Tenant-scoped notification operations dashboard.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
    return <html lang="en"><body><DashboardShell>{children}</DashboardShell></body></html>;
}